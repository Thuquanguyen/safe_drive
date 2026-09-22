package com.mobile.safedrive.ui.navigation

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mobile.safedrive.SafeDriveApp
import com.mobile.safedrive.drowsiness.DrowsinessState
import com.mobile.safedrive.motion.MotionDetector
import com.mobile.safedrive.service.CalibrationStatus
import com.mobile.safedrive.service.DrowsinessMonitoringService
import com.mobile.safedrive.service.MonitorIssue
import com.mobile.safedrive.service.MonitorPhase
import com.mobile.safedrive.service.MonitorUiState
import com.mobile.safedrive.service.MonitoringStore
import com.mobile.safedrive.session.RoutePoint
import com.mobile.safedrive.settings.AppSettings
import com.mobile.safedrive.ui.components.HomeTab
import com.mobile.safedrive.ui.screens.AttentionAlertScreen
import com.mobile.safedrive.ui.screens.CalibrationScreen
import com.mobile.safedrive.ui.screens.CameraPermissionScreen
import com.mobile.safedrive.ui.screens.CriticalAlertScreen
import com.mobile.safedrive.ui.screens.DrowsyAlertScreen
import com.mobile.safedrive.ui.screens.EyesNotVisibleScreen
import com.mobile.safedrive.ui.screens.HistoryScreen
import com.mobile.safedrive.ui.screens.HomeScreen
import com.mobile.safedrive.ui.screens.LowLightWarningScreen
import com.mobile.safedrive.ui.screens.MonitoringScreen
import com.mobile.safedrive.ui.screens.PositionCameraScreen
import com.mobile.safedrive.ui.screens.PrivacyScreen
import com.mobile.safedrive.ui.screens.SettingsScreen
import com.mobile.safedrive.ui.screens.SetupGuideScreen
import com.mobile.safedrive.ui.screens.TipsScreen
import com.mobile.safedrive.ui.screens.TripSummaryScreen
import com.mobile.safedrive.ui.screens.WelcomeScreen
import kotlinx.coroutines.launch

/** Where a setup screen was opened from; decides what "next" means. */
enum class FlowMode { ONBOARDING, TRIP, RECALIBRATE, TIPS }

private object Routes {
    const val WELCOME = "welcome"
    const val PRIVACY = "privacy"
    const val CAMERA_PERMISSION = "camera_permission/{mode}"
    const val SETUP_GUIDE = "setup_guide/{mode}"
    const val POSITION_CAMERA = "position_camera/{mode}"
    const val CALIBRATION = "calibration/{mode}"
    const val HOME = "home"
    const val HISTORY = "history"
    const val TIPS = "tips"
    const val SETTINGS = "settings"
    const val MONITORING = "monitoring"
    const val ALERT_ATTENTION = "alert/attention"
    const val ALERT_DROWSY = "alert/drowsy"
    const val ALERT_CRITICAL = "alert/critical"
    const val ISSUE_EYES = "issue/eyes"
    const val ISSUE_LOW_LIGHT = "issue/low_light"
    const val TRIP_SUMMARY = "trip/{id}"

    val OVERLAYS = setOf(ALERT_ATTENTION, ALERT_DROWSY, ALERT_CRITICAL, ISSUE_EYES, ISSUE_LOW_LIGHT)

    fun cameraPermission(mode: FlowMode) = "camera_permission/${mode.name}"
    fun setupGuide(mode: FlowMode) = "setup_guide/${mode.name}"
    fun positionCamera(mode: FlowMode) = "position_camera/${mode.name}"
    fun calibration(mode: FlowMode) = "calibration/${mode.name}"
    fun trip(id: String) = "trip/$id"
}

private const val ISSUE_RECHECK_MS = 20_000L
private const val EYES_DISMISS_MS = 5 * 60_000L
private const val LOW_LIGHT_DISMISS_MS = 10 * 60_000L

@Composable
fun SafeDriveNavHost(
    settings: AppSettings,
    startDrivingRequest: MutableState<Boolean>,
    onAlertCleared: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as SafeDriveApp
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val monitor by MonitoringStore.state.collectAsState()
    val sessions by app.sessionRepository.sessions.collectAsState()

    val startDestination = remember {
        val phase = MonitoringStore.state.value.phase
        when {
            phase == MonitorPhase.MONITORING -> Routes.MONITORING
            phase == MonitorPhase.CALIBRATING -> Routes.CALIBRATION
            settings.onboardingDone -> Routes.HOME
            else -> Routes.WELCOME
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        scope.launch { app.settingsRepository.update(transform) }
    }

    fun goTab(tab: HomeTab) {
        val route = when (tab) {
            HomeTab.Home -> Routes.HOME
            HomeTab.History -> Routes.HISTORY
            HomeTab.Tips -> Routes.TIPS
            HomeTab.Settings -> Routes.SETTINGS
        }
        nav.navigate(route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // --- Permissions -------------------------------------------------------------------------

    val tripPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Location only adds speed/distance; monitoring works without it.
        nav.navigate(if (hasCamera(context)) Routes.positionCamera(FlowMode.TRIP) else Routes.cameraPermission(FlowMode.TRIP))
    }
    fun startDriving() {
        val missing = buildList {
            if (!granted(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !granted(context, Manifest.permission.POST_NOTIFICATIONS)
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (missing.isEmpty()) {
            nav.navigate(if (hasCamera(context)) Routes.positionCamera(FlowMode.TRIP) else Routes.cameraPermission(FlowMode.TRIP))
        } else {
            tripPermissions.launch(missing.toTypedArray())
        }
    }

    val backgroundLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            updateSettings { it.copy(autoDetectDriving = true) }
            MotionDetector.register(context)
        }
    }
    val autoDetectPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            ) {
                backgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                updateSettings { it.copy(autoDetectDriving = true) }
                MotionDetector.register(context)
            }
        }
    }
    fun toggleAutoDetect(enable: Boolean) {
        if (!enable) {
            MotionDetector.unregister(context)
            updateSettings { it.copy(autoDetectDriving = false) }
            return
        }
        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Manifest.permission.ACTIVITY_RECOGNITION)
        }.filterNot { granted(context, it) }
        if (needed.isEmpty()) autoDetectPermissions.launch(emptyArray()) else autoDetectPermissions.launch(needed.toTypedArray())
    }

    // --- Global routing: alerts & issues appear over monitoring --------------------------------

    val overlayTarget = overlayRouteFor(monitor)
    LaunchedEffect(overlayTarget, monitor.phase) {
        val current = nav.currentDestination?.route
        if (overlayTarget != null) {
            if (current != overlayTarget) {
                nav.navigate(overlayTarget) {
                    popUpTo(Routes.MONITORING)
                    launchSingleTop = true
                }
            }
        } else if (current in Routes.OVERLAYS) {
            if (!nav.popBackStack(Routes.MONITORING, inclusive = false)) {
                nav.navigate(Routes.MONITORING) { popUpTo(Routes.HOME); launchSingleTop = true }
            }
            onAlertCleared()
        }
    }

    // "Driving detected — tap to start" notification.
    LaunchedEffect(startDrivingRequest.value) {
        if (startDrivingRequest.value) {
            startDrivingRequest.value = false
            if (MonitoringStore.state.value.phase == MonitorPhase.IDLE) startDriving()
        }
    }

    fun endTrip() {
        val sessionId = MonitoringStore.state.value.sessionId
        DrowsinessMonitoringService.stop(context)
        onAlertCleared()
        if (sessionId != null) {
            nav.navigate(Routes.trip(sessionId)) { popUpTo(Routes.HOME); launchSingleTop = true }
        } else {
            nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
        }
    }

    NavHost(navController = nav, startDestination = startDestination) {
        composable(Routes.WELCOME) {
            WelcomeScreen(onGetStarted = { nav.navigate(Routes.PRIVACY) })
        }
        composable(Routes.PRIVACY) {
            PrivacyScreen(onAgree = {
                if (settings.onboardingDone) {
                    nav.popBackStack()
                } else {
                    nav.navigate(if (hasCamera(context)) Routes.setupGuide(FlowMode.ONBOARDING) else Routes.cameraPermission(FlowMode.ONBOARDING))
                }
            })
        }
        composable(Routes.CAMERA_PERMISSION, arguments = listOf(modeArg())) { entry ->
            val mode = entry.mode()
            val next = {
                when (mode) {
                    FlowMode.TRIP, FlowMode.RECALIBRATE -> if (hasCamera(context)) {
                        nav.navigate(Routes.positionCamera(mode)) { popUpTo(Routes.HOME) }
                    } else {
                        nav.popBackStack()
                    }
                    else -> nav.navigate(Routes.setupGuide(FlowMode.ONBOARDING))
                }
            }
            val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
                val cameraGranted = result[Manifest.permission.CAMERA] == true || hasCamera(context)
                val activity = context as? android.app.Activity
                val permanentlyDenied = activity != null &&
                    !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
                when {
                    cameraGranted -> next()
                    mode == FlowMode.ONBOARDING -> next()
                    // "Don't ask again": the system dialog no longer appears, send the user to Settings.
                    permanentlyDenied -> openAppSettings(context)
                }
            }
            CameraPermissionScreen(
                onAllow = {
                    val permissions = buildList {
                        add(Manifest.permission.CAMERA)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    launcher.launch(permissions.toTypedArray())
                },
                onNotNow = { if (mode == FlowMode.ONBOARDING) next() else nav.popBackStack() },
            )
        }
        composable(Routes.SETUP_GUIDE, arguments = listOf(modeArg())) { entry ->
            val mode = entry.mode()
            SetupGuideScreen(
                onBack = { nav.popBackStack() },
                onNext = {
                    when (mode) {
                        FlowMode.ONBOARDING -> if (hasCamera(context)) {
                            nav.navigate(Routes.positionCamera(FlowMode.ONBOARDING))
                        } else {
                            // Camera declined: finish onboarding; Start Driving asks again.
                            updateSettings { it.copy(onboardingDone = true) }
                            nav.navigate(Routes.HOME) { popUpTo(0) }
                        }
                        else -> nav.popBackStack()
                    }
                },
            )
        }
        composable(Routes.POSITION_CAMERA, arguments = listOf(modeArg())) { entry ->
            val mode = entry.mode()
            PositionCameraScreen(
                requireGoodPosition = mode != FlowMode.ONBOARDING,
                onBack = { nav.popBackStack() },
                onContinue = {
                    when (mode) {
                        FlowMode.ONBOARDING -> {
                            updateSettings { it.copy(onboardingDone = true) }
                            nav.navigate(Routes.HOME) { popUpTo(0) }
                        }
                        FlowMode.RECALIBRATE -> {
                            DrowsinessMonitoringService.start(context, calibrate = true, calibrationOnly = true)
                            nav.navigate(Routes.calibration(FlowMode.RECALIBRATE)) { popUpTo(Routes.HOME) }
                        }
                        else -> {
                            DrowsinessMonitoringService.start(context, calibrate = true)
                            nav.navigate(Routes.calibration(FlowMode.TRIP)) { popUpTo(Routes.HOME) }
                        }
                    }
                },
                onCalibrationTips = { nav.navigate(Routes.setupGuide(FlowMode.TIPS)) },
            )
        }
        composable(Routes.CALIBRATION, arguments = listOf(modeArg())) { entry ->
            val mode = entry.mode()
            LaunchedEffect(monitor.phase, monitor.calibrationStatus) {
                when {
                    monitor.phase == MonitorPhase.MONITORING ->
                        nav.navigate(Routes.MONITORING) { popUpTo(Routes.HOME) }
                    mode == FlowMode.RECALIBRATE && monitor.calibrationStatus == CalibrationStatus.DONE ->
                        nav.popBackStack()
                }
            }
            CalibrationScreen(
                onCancel = {
                    DrowsinessMonitoringService.stop(context)
                    nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                },
                onRetry = { DrowsinessMonitoringService.recalibrate(context) },
            )
        }
        composable(Routes.HOME) {
            HomeScreen(
                sessions = sessions,
                onStartDriving = {
                    when (monitor.phase) {
                        MonitorPhase.MONITORING -> nav.navigate(Routes.MONITORING)
                        MonitorPhase.CALIBRATING -> nav.navigate(Routes.calibration(FlowMode.TRIP))
                        else -> startDriving()
                    }
                },
                onTab = ::goTab,
                onOpenSetupGuide = { nav.navigate(Routes.setupGuide(FlowMode.TIPS)) },
                onOpenPrivacy = { nav.navigate(Routes.PRIVACY) },
                onRecalibrate = { recalibrate(nav, context, monitor) },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                sessions = sessions,
                onBack = { goTab(HomeTab.Home) },
                onOpenTrip = { nav.navigate(Routes.trip(it)) },
                onTab = ::goTab,
            )
        }
        composable(Routes.TIPS) {
            TipsScreen(onBack = { goTab(HomeTab.Home) }, onTab = ::goTab)
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                settings = settings,
                monitoringActive = monitor.phase != MonitorPhase.IDLE,
                onBack = { goTab(HomeTab.Home) },
                onTab = ::goTab,
                onUpdate = ::updateSettings,
                onToggleAutoDetect = ::toggleAutoDetect,
                onRecalibrate = { recalibrate(nav, context, monitor) },
            )
        }
        composable(Routes.MONITORING) {
            val sessionId = remember { MonitoringStore.state.value.sessionId }
            LaunchedEffect(monitor.phase) {
                // Trip ended outside the UI (grace period, walking, notification "Stop").
                if (monitor.phase == MonitorPhase.IDLE) {
                    if (sessionId != null) {
                        nav.navigate(Routes.trip(sessionId)) { popUpTo(Routes.HOME); launchSingleTop = true }
                    } else {
                        nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                    }
                }
            }
            MonitoringScreen(monitor = monitor, keepScreenAwake = settings.keepScreenAwake, onEndTrip = ::endTrip)
        }
        composable(Routes.ALERT_ATTENTION) {
            AttentionAlertScreen(
                onAcknowledge = { DrowsinessMonitoringService.acknowledge(context) },
                onFindRestArea = { findRestArea(context) },
            )
        }
        composable(Routes.ALERT_DROWSY) {
            DrowsyAlertScreen(
                onAcknowledge = { DrowsinessMonitoringService.acknowledge(context) },
                onFindRestArea = { findRestArea(context) },
            )
        }
        composable(Routes.ALERT_CRITICAL) {
            CriticalAlertScreen(onEndTrip = ::endTrip, onFindRestArea = { findRestArea(context) })
        }
        composable(Routes.ISSUE_EYES) {
            EyesNotVisibleScreen(
                onAdjusted = { DrowsinessMonitoringService.dismissIssue(context, MonitorIssue.EYES_NOT_VISIBLE, ISSUE_RECHECK_MS) },
                onDismiss = { DrowsinessMonitoringService.dismissIssue(context, MonitorIssue.EYES_NOT_VISIBLE, EYES_DISMISS_MS) },
            )
        }
        composable(Routes.ISSUE_LOW_LIGHT) {
            LowLightWarningScreen(
                onFixed = { DrowsinessMonitoringService.dismissIssue(context, MonitorIssue.LOW_LIGHT, ISSUE_RECHECK_MS) },
                onDismiss = { DrowsinessMonitoringService.dismissIssue(context, MonitorIssue.LOW_LIGHT, LOW_LIGHT_DISMISS_MS) },
            )
        }
        composable(Routes.TRIP_SUMMARY, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("id")
            TripSummaryScreen(
                session = sessions.firstOrNull { it.sessionId == id },
                onBack = {
                    if (!nav.popBackStack()) nav.navigate(Routes.HOME)
                },
                onViewOnMap = { openRoute(context, it) },
            )
        }
    }
}

/** Highest-priority overlay: drowsiness alerts first, then camera-quality prompts. */
private fun overlayRouteFor(monitor: MonitorUiState): String? {
    if (monitor.phase != MonitorPhase.MONITORING) return null
    return when (monitor.pendingAlert) {
        DrowsinessState.CRITICAL -> Routes.ALERT_CRITICAL
        DrowsinessState.DROWSY -> Routes.ALERT_DROWSY
        DrowsinessState.ATTENTION -> Routes.ALERT_ATTENTION
        DrowsinessState.NORMAL -> when (monitor.issue) {
            MonitorIssue.EYES_NOT_VISIBLE -> Routes.ISSUE_EYES
            MonitorIssue.LOW_LIGHT -> Routes.ISSUE_LOW_LIGHT
            null -> null
        }
    }
}

private fun recalibrate(nav: NavHostController, context: Context, monitor: MonitorUiState) {
    if (monitor.phase != MonitorPhase.IDLE) return
    nav.navigate(
        if (hasCamera(context)) Routes.positionCamera(FlowMode.RECALIBRATE) else Routes.cameraPermission(FlowMode.RECALIBRATE)
    )
}

private fun modeArg() = navArgument("mode") {
    type = NavType.StringType
    defaultValue = FlowMode.TRIP.name
}

private fun androidx.navigation.NavBackStackEntry.mode(): FlowMode =
    arguments?.getString("mode")?.let { runCatching { FlowMode.valueOf(it) }.getOrNull() } ?: FlowMode.TRIP

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun hasCamera(context: Context) = granted(context, Manifest.permission.CAMERA)

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/** Opens the user's maps app searching nearby rest areas; nothing is sent by SafeDrive itself. */
private fun findRestArea(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=rest+area")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/rest+area"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun openRoute(context: Context, route: List<RoutePoint>) {
    val start = route.first()
    val end = route.last()
    val uri = Uri.parse(
        "https://www.google.com/maps/dir/?api=1&origin=${start.lat},${start.lng}&destination=${end.lat},${end.lng}"
    )
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
