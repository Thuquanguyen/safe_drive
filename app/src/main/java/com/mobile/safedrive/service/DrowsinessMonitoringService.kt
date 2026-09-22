package com.mobile.safedrive.service

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.mobile.safedrive.SafeDriveApp
import com.mobile.safedrive.alert.AlertManager
import com.mobile.safedrive.camera.CameraController
import com.mobile.safedrive.camera.FrameAnalyzer
import com.mobile.safedrive.drowsiness.CalibrationManager
import com.mobile.safedrive.drowsiness.CalibrationProfile
import com.mobile.safedrive.drowsiness.DrowsinessConfig
import com.mobile.safedrive.drowsiness.DrowsinessEngine
import com.mobile.safedrive.drowsiness.DrowsinessState
import com.mobile.safedrive.drowsiness.FrameObservation
import com.mobile.safedrive.drowsiness.MonitorCondition
import com.mobile.safedrive.drowsiness.MotionActivity
import com.mobile.safedrive.motion.AutoDrivingController
import com.mobile.safedrive.motion.SpeedTracker
import com.mobile.safedrive.power.PowerAdaptiveManager
import com.mobile.safedrive.power.WakeLockManager
import com.mobile.safedrive.session.DrivingSession
import com.mobile.safedrive.settings.AppSettings
import com.mobile.safedrive.vision.FaceLandmarkAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Independent foreground service that owns the whole monitoring pipeline (spec §36):
 * sticky notification, partial wake lock, headless CameraX analysis with adaptive FPS,
 * screen on/off handling, drowsiness engine, alerts, speed tracking and auto start/stop.
 */
class DrowsinessMonitoringService : LifecycleService() {

    private val app get() = application as SafeDriveApp
    private lateinit var notifications: MonitoringNotificationManager
    private lateinit var wakeLock: WakeLockManager
    private lateinit var speedTracker: SpeedTracker
    private lateinit var alerts: AlertManager
    private lateinit var calibration: CalibrationManager

    private var settings = AppSettings()
    private var config = DrowsinessConfig()
    private val engine = DrowsinessEngine(config, CalibrationProfile.DEFAULT)
    private val power = PowerAdaptiveManager(config)
    private val autoDriving = AutoDrivingController(config)

    private var camera: CameraController? = null
    private var face: FaceLandmarkAnalyzer? = null

    @Volatile private var screenOn = true
    @Volatile private var calibrateOnly = false
    private var calibrationElapsedMs = 0L
    private var lastCalibrationFrameMs = 0L

    private var attentionAlerts = 0
    private var drowsyAlerts = 0
    private var criticalAlerts = 0
    private var maxLevel = DrowsinessState.NORMAL

    private var issueCandidate: MonitorIssue? = null
    private var issueCandidateSince = 0L
    private val issueSnoozedUntil = LongArray(MonitorIssue.entries.size)

    private var lastNotifiedState = DrowsinessState.NORMAL
    private var lastAlertNotified = DrowsinessState.NORMAL
    private var lastNotificationMs = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenChanged(on = false)
                Intent.ACTION_SCREEN_ON -> onScreenChanged(on = true)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notifications = MonitoringNotificationManager(this).also { it.ensureChannels() }
        wakeLock = WakeLockManager(this)
        alerts = AlertManager(this)
        calibration = CalibrationManager(this)
        speedTracker = SpeedTracker(this).apply { onUpdate = ::onSpeed }
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        screenOn = (getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        power.screenOn = screenOn
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startMonitoring(
                calibrate = intent.getBooleanExtra(EXTRA_CALIBRATE, true),
                calibrationOnly = intent.getBooleanExtra(EXTRA_CALIBRATION_ONLY, false),
                autoStarted = false,
            )
            ACTION_RECALIBRATE -> restartCalibration()
            ACTION_WATCH_MOTION -> startWatching()
            ACTION_STOP -> finishTrip()
            ACTION_ACKNOWLEDGE -> acknowledge()
            ACTION_DISMISS_ISSUE -> dismissIssue(
                MonitorIssue.valueOf(intent.getStringExtra(EXTRA_ISSUE) ?: return START_NOT_STICKY),
                intent.getLongExtra(EXTRA_SNOOZE_MS, DEFAULT_SNOOZE_MS),
            )
            ACTION_MOTION -> onMotion(
                MotionActivity.valueOf(intent.getStringExtra(EXTRA_ACTIVITY) ?: MotionActivity.UNKNOWN.name)
            )
            else -> if (MonitoringStore.state.value.phase == MonitorPhase.IDLE) stopSelf()
        }
        return START_NOT_STICKY
    }

    // region Start / stop

    private fun startMonitoring(calibrate: Boolean, calibrationOnly: Boolean, autoStarted: Boolean) {
        val phase = MonitoringStore.state.value.phase
        if (phase == MonitorPhase.MONITORING || phase == MonitorPhase.CALIBRATING) return
        lifecycleScope.launch {
            loadSettings()
            val profile = calibration.load()
            if (!startForegroundWith(cameraAndLocationTypes())) {
                // Android blocks camera use from a service started in the background:
                // let the driver start it with one tap instead.
                if (autoStarted) notifications.showTapToStart()
                shutdown()
                return@launch
            }
            engine.update(config, profile)
            engine.reset()
            alerts.reset()
            autoDriving.reset()
            attentionAlerts = 0; drowsyAlerts = 0; criticalAlerts = 0
            maxLevel = DrowsinessState.NORMAL
            issueCandidate = null
            issueSnoozedUntil.fill(0L)
            lastNotifiedState = DrowsinessState.NORMAL
            lastAlertNotified = DrowsinessState.NORMAL
            power.stoppedInGrace = false
            calibrateOnly = calibrationOnly

            val analyzer = withContext(Dispatchers.Default) { FaceLandmarkAnalyzer(this@DrowsinessMonitoringService) }
            face = analyzer
            val runCalibration = calibrate && analyzer.isAvailable
            beginCalibration()
            MonitoringStore.update {
                MonitorUiState(
                    phase = if (runCalibration) MonitorPhase.CALIBRATING else MonitorPhase.MONITORING,
                    sessionId = UUID.randomUUID().toString(),
                    startTimeMs = System.currentTimeMillis(),
                    autoStarted = autoStarted,
                    calibrationStatus = if (runCalibration) CalibrationStatus.RUNNING else CalibrationStatus.NONE,
                    modelAvailable = analyzer.isAvailable,
                )
            }
            if (calibrationOnly && !runCalibration) {
                shutdown()
                return@launch
            }
            if (autoStarted) alerts.greet()

            if (!calibrationOnly) {
                speedTracker.resetTrip()
                speedTracker.start(LOCATION_INTERVAL_MS)
            }
            val controller = CameraController(this@DrowsinessMonitoringService)
            camera = controller
            val frameAnalyzer = FrameAnalyzer(
                face = analyzer,
                minIntervalMs = {
                    // Screen-off monitoring disabled: drop every frame before any AI work.
                    if (!screenOn && !settings.screenOffMonitoring) Long.MAX_VALUE
                    else power.minIntervalMs(SystemClock.uptimeMillis())
                },
                onFrame = ::onFrame,
            )
            runCatching { controller.bind(this@DrowsinessMonitoringService, frameAnalyzer) }
                .onFailure { MonitoringStore.update { s -> s.copy(cameraError = true) } }
            if (!screenOn) onScreenChanged(on = false)
            refreshOngoingNotification(force = true)
        }
    }

    private fun startWatching() {
        if (MonitoringStore.state.value.phase != MonitorPhase.IDLE) return
        lifecycleScope.launch {
            loadSettings()
            if (!settings.autoDetectDriving || !speedTracker.hasPermission() ||
                !startForegroundWith(ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            ) {
                shutdown()
                return@launch
            }
            autoDriving.reset()
            MonitoringStore.update { MonitorUiState(phase = MonitorPhase.WATCHING_MOTION) }
            speedTracker.resetTrip()
            speedTracker.start(WATCH_LOCATION_INTERVAL_MS)
            refreshOngoingNotification(force = true)
            delay(WATCH_TIMEOUT_MS)
            if (MonitoringStore.state.value.phase == MonitorPhase.WATCHING_MOTION) shutdown()
        }
    }

    private fun finishTrip() {
        val state = MonitoringStore.state.value
        if (state.phase == MonitorPhase.MONITORING && state.sessionId != null) {
            val session = DrivingSession(
                sessionId = state.sessionId,
                startTime = state.startTimeMs,
                endTime = System.currentTimeMillis(),
                distanceKm = speedTracker.distanceKm,
                maxDrowsinessLevel = maxLevel,
                attentionAlerts = attentionAlerts,
                drowsyAlerts = drowsyAlerts,
                criticalAlerts = criticalAlerts,
                autoStarted = state.autoStarted,
                route = speedTracker.routeSnapshot(),
            )
            app.appScope.launch { app.sessionRepository.save(session) }
        }
        shutdown()
    }

    private fun shutdown(keepCalibrationResult: CalibrationStatus? = null) {
        alerts.reset()
        notifications.cancelAlert()
        speedTracker.stop()
        wakeLock.release()
        val cameraToRelease = camera
        val faceToClose = face
        camera = null
        face = null
        cameraToRelease?.unbind()
        // Let the analysis thread drain before closing the native landmarker.
        Thread {
            cameraToRelease?.releaseBlocking()
            faceToClose?.close()
        }.start()
        MonitoringStore.reset()
        if (keepCalibrationResult != null) {
            MonitoringStore.update { it.copy(calibrationStatus = keepCalibrationResult) }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        if (MonitoringStore.state.value.phase != MonitorPhase.IDLE) {
            alerts.reset()
            speedTracker.stop()
            wakeLock.release()
            camera?.unbind()
            MonitoringStore.reset()
        }
        alerts.release()
        super.onDestroy()
    }

    // endregion

    // region Frames

    private fun onFrame(obs: FrameObservation) {
        when (MonitoringStore.state.value.phase) {
            MonitorPhase.CALIBRATING -> onCalibrationFrame(obs)
            MonitorPhase.MONITORING -> onMonitoringFrame(obs)
            else -> Unit
        }
    }

    private fun beginCalibration() {
        calibration.begin()
        calibrationElapsedMs = 0L
        lastCalibrationFrameMs = 0L
    }

    private fun restartCalibration() {
        if (MonitoringStore.state.value.phase != MonitorPhase.CALIBRATING) return
        beginCalibration()
        MonitoringStore.update {
            it.copy(calibrationStatus = CalibrationStatus.RUNNING, calibrationProgress = 0f)
        }
    }

    private fun onCalibrationFrame(obs: FrameObservation) {
        if (MonitoringStore.state.value.calibrationStatus != CalibrationStatus.RUNNING) return
        val now = obs.timestampMs
        val faceVisible = obs.faceCount == 1 && obs.metrics.faceConfidence >= MIN_CALIBRATION_CONFIDENCE
        // Progress only advances while the face is clearly visible.
        if (faceVisible && lastCalibrationFrameMs > 0) {
            calibrationElapsedMs += (now - lastCalibrationFrameMs).coerceAtMost(MAX_CALIBRATION_STEP_MS)
        }
        lastCalibrationFrameMs = now
        if (faceVisible) calibration.addSample(obs.metrics)
        val progress = (calibrationElapsedMs.toFloat() / CalibrationManager.DURATION_MS).coerceAtMost(1f)
        MonitoringStore.update { it.copy(calibrationProgress = progress, calibrationFaceVisible = faceVisible) }
        if (calibrationElapsedMs < CalibrationManager.DURATION_MS) return

        val profile = calibration.finish()
        if (profile == null) {
            MonitoringStore.update { it.copy(calibrationStatus = CalibrationStatus.FAILED) }
            return
        }
        engine.update(config, profile)
        engine.reset()
        if (calibrateOnly) {
            lifecycleScope.launch { shutdown(keepCalibrationResult = CalibrationStatus.DONE) }
        } else {
            MonitoringStore.update {
                it.copy(
                    phase = MonitorPhase.MONITORING,
                    calibrationStatus = CalibrationStatus.DONE,
                    startTimeMs = System.currentTimeMillis(),
                )
            }
        }
    }

    private fun onMonitoringFrame(obs: FrameObservation) {
        if (!screenOn && !settings.screenOffMonitoring) return
        val now = obs.timestampMs
        val result = engine.process(obs)
        power.onEvaluation(result.state, result.suspicious, now)
        when (alerts.onState(result.state, now)) {
            DrowsinessState.ATTENTION -> attentionAlerts++
            DrowsinessState.DROWSY -> drowsyAlerts++
            DrowsinessState.CRITICAL -> criticalAlerts++
            else -> Unit
        }
        if (result.state > maxLevel) maxLevel = result.state

        val issue = promptIssue(result.condition, now)
        MonitoringStore.update {
            it.copy(
                state = result.state,
                acknowledgedLevel = if (result.state < it.acknowledgedLevel) result.state else it.acknowledgedLevel,
                drowsinessScore = result.metrics.totalScore,
                condition = result.condition,
                issue = issue,
                targetFps = power.strategy(now).targetFps,
            )
        }
        updateAlertNotification(result.state)
        refreshOngoingNotification(force = result.state != lastNotifiedState)
    }

    /** A camera problem becomes a prompt only after it persists (spec §6: don't react to a few frames). */
    private fun promptIssue(condition: MonitorCondition, now: Long): MonitorIssue? {
        val candidate = when (condition) {
            MonitorCondition.FACE_NOT_DETECTED, MonitorCondition.LOW_CONFIDENCE -> MonitorIssue.EYES_NOT_VISIBLE
            MonitorCondition.LOW_LIGHT -> MonitorIssue.LOW_LIGHT
            else -> null
        }
        if (candidate != issueCandidate) {
            issueCandidate = candidate
            issueCandidateSince = now
        }
        if (candidate == null || now - issueCandidateSince < ISSUE_PROMPT_DELAY_MS) return null
        return if (now >= issueSnoozedUntil[candidate.ordinal]) candidate else null
    }

    // endregion

    // region Events

    private fun acknowledge() {
        alerts.acknowledge()
        notifications.cancelAlert()
        MonitoringStore.update { it.copy(acknowledgedLevel = maxOf(it.acknowledgedLevel, it.state)) }
    }

    private fun dismissIssue(issue: MonitorIssue, snoozeMs: Long) {
        issueSnoozedUntil[issue.ordinal] = SystemClock.uptimeMillis() + snoozeMs
        MonitoringStore.update { if (it.issue == issue) it.copy(issue = null) else it }
    }

    private fun onMotion(activity: MotionActivity) {
        val state = MonitoringStore.state.value
        when {
            state.phase == MonitorPhase.WATCHING_MOTION && activity != MotionActivity.IN_VEHICLE -> shutdown()
            state.phase == MonitorPhase.MONITORING && state.autoStarted && activity == MotionActivity.WALKING ->
                finishTrip()
        }
    }

    private fun onSpeed(speedKmh: Float, distanceKm: Float) {
        MonitoringStore.update { it.copy(speedKmh = speedKmh, distanceKm = distanceKm) }
        val state = MonitoringStore.state.value
        val now = SystemClock.uptimeMillis()
        when (state.phase) {
            MonitorPhase.WATCHING_MOTION ->
                if (autoDriving.onSpeedWhileWatching(speedKmh, now) == AutoDrivingController.Action.VEHICLE_CONFIRMED) {
                    MonitoringStore.reset()
                    startMonitoring(calibrate = false, calibrationOnly = false, autoStarted = true)
                }
            MonitorPhase.MONITORING -> if (state.autoStarted) {
                val action = autoDriving.onSpeedWhileDriving(speedKmh, now)
                power.stoppedInGrace = autoDriving.isStoppedInGrace
                if (action == AutoDrivingController.Action.END_TRIP) finishTrip()
            }
            else -> Unit
        }
        refreshOngoingNotification(force = false)
    }

    private fun onScreenChanged(on: Boolean) {
        screenOn = on
        power.screenOn = on
        val active = MonitoringStore.state.value.phase.let {
            it == MonitorPhase.MONITORING || it == MonitorPhase.CALIBRATING
        }
        if (!on && active && settings.screenOffMonitoring) wakeLock.acquire() else wakeLock.release()
    }

    // endregion

    // region Notifications

    private fun updateAlertNotification(state: DrowsinessState) {
        if (state >= DrowsinessState.DROWSY && !AppVisibility.isVisible) {
            if (state != lastAlertNotified) {
                notifications.showAlert(state)
                lastAlertNotified = state
            }
        } else if (lastAlertNotified != DrowsinessState.NORMAL && state < DrowsinessState.DROWSY) {
            notifications.cancelAlert()
            lastAlertNotified = DrowsinessState.NORMAL
        }
    }

    private fun refreshOngoingNotification(force: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastNotificationMs < NOTIFICATION_REFRESH_MS) return
        lastNotificationMs = now
        val s = MonitoringStore.state.value
        lastNotifiedState = s.state
        notifications.updateOngoing(
            notifications.ongoing(s.state, s.phase, s.speedKmh, s.targetFps, settings.ultraBatterySaver && !screenOn)
        )
    }

    // endregion

    private suspend fun loadSettings() {
        settings = app.settingsRepository.current()
        config = DrowsinessConfig.from(settings)
        power.update(config)
        power.ultraSaver = settings.ultraBatterySaver
        autoDriving.update(config)
        alerts.configure(settings, config.alertCooldownMs)
    }

    private fun startForegroundWith(types: Int): Boolean {
        val s = MonitoringStore.state.value
        val notification = notifications.ongoing(s.state, s.phase, s.speedKmh, s.targetFps, false)
        return runCatching {
            ServiceCompat.startForeground(this, MonitoringNotificationManager.ID_ONGOING, notification, types)
        }.isSuccess
    }

    private fun cameraAndLocationTypes(): Int {
        var types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        if (granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        }
        return types
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_START = "com.mobile.safedrive.START"
        const val ACTION_RECALIBRATE = "com.mobile.safedrive.RECALIBRATE"
        const val ACTION_WATCH_MOTION = "com.mobile.safedrive.WATCH_MOTION"
        const val ACTION_STOP = "com.mobile.safedrive.STOP"
        const val ACTION_ACKNOWLEDGE = "com.mobile.safedrive.ACKNOWLEDGE"
        const val ACTION_DISMISS_ISSUE = "com.mobile.safedrive.DISMISS_ISSUE"
        const val ACTION_MOTION = "com.mobile.safedrive.MOTION"
        private const val EXTRA_CALIBRATE = "calibrate"
        private const val EXTRA_CALIBRATION_ONLY = "calibration_only"
        private const val EXTRA_ISSUE = "issue"
        private const val EXTRA_SNOOZE_MS = "snooze_ms"
        private const val EXTRA_ACTIVITY = "activity"

        private const val LOCATION_INTERVAL_MS = 2_000L
        private const val WATCH_LOCATION_INTERVAL_MS = 3_000L
        private const val WATCH_TIMEOUT_MS = 10 * 60_000L
        private const val NOTIFICATION_REFRESH_MS = 5_000L
        private const val ISSUE_PROMPT_DELAY_MS = 5_000L
        private const val DEFAULT_SNOOZE_MS = 30_000L
        private const val MIN_CALIBRATION_CONFIDENCE = 0.5f
        private const val MAX_CALIBRATION_STEP_MS = 500L

        fun start(context: Context, calibrate: Boolean, calibrationOnly: Boolean = false) {
            // Clear a previous calibration result so the UI doesn't act on stale state.
            if (MonitoringStore.state.value.phase == MonitorPhase.IDLE) MonitoringStore.reset()
            val intent = Intent(context, DrowsinessMonitoringService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_CALIBRATE, calibrate)
                .putExtra(EXTRA_CALIBRATION_ONLY, calibrationOnly)
            ContextCompat.startForegroundService(context, intent)
        }

        fun recalibrate(context: Context) = send(context, ACTION_RECALIBRATE)
        fun stop(context: Context) = send(context, ACTION_STOP)
        fun acknowledge(context: Context) = send(context, ACTION_ACKNOWLEDGE)

        fun dismissIssue(context: Context, issue: MonitorIssue, snoozeMs: Long) = context.startService(
            Intent(context, DrowsinessMonitoringService::class.java)
                .setAction(ACTION_DISMISS_ISSUE)
                .putExtra(EXTRA_ISSUE, issue.name)
                .putExtra(EXTRA_SNOOZE_MS, snoozeMs)
        )

        fun watchMotion(context: Context) {
            // A location FGS started from the background needs background location access;
            // without it startForeground would throw after the service was already started.
            val background = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            if (!background) return
            val intent = Intent(context, DrowsinessMonitoringService::class.java).setAction(ACTION_WATCH_MOTION)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun sendMotion(context: Context, activity: MotionActivity) {
            runCatching {
                context.startService(
                    Intent(context, DrowsinessMonitoringService::class.java)
                        .setAction(ACTION_MOTION)
                        .putExtra(EXTRA_ACTIVITY, activity.name)
                )
            }
        }

        private fun send(context: Context, action: String) {
            if (MonitoringStore.state.value.phase == MonitorPhase.IDLE) return
            runCatching { context.startService(Intent(context, DrowsinessMonitoringService::class.java).setAction(action)) }
        }
    }
}

/** Whether any app screen is visible; decides between in-app alert screens and full-screen intents. */
object AppVisibility {
    @Volatile var isVisible = false
}
