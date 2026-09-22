package com.mobile.safedrive.motion

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.mobile.safedrive.SafeDriveApp
import com.mobile.safedrive.drowsiness.MotionActivity
import com.mobile.safedrive.service.DrowsinessMonitoringService
import com.mobile.safedrive.service.MonitorPhase
import com.mobile.safedrive.service.MonitoringStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Low-power Activity Recognition transitions (IN_VEHICLE / WALKING / STILL). */
object MotionDetector {

    fun hasPermission(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun register(context: Context) {
        if (!hasPermission(context)) return
        val transitions = listOf(
            transition(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            transition(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_EXIT),
            transition(DetectedActivity.WALKING, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            transition(DetectedActivity.STILL, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
        )
        ActivityRecognition.getClient(context)
            .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pendingIntent(context))
    }

    @SuppressLint("MissingPermission")
    fun unregister(context: Context) {
        if (!hasPermission(context)) return
        ActivityRecognition.getClient(context).removeActivityTransitionUpdates(pendingIntent(context))
    }

    private fun transition(activity: Int, type: Int) = ActivityTransition.Builder()
        .setActivityType(activity)
        .setActivityTransition(type)
        .build()

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, MotionTransitionReceiver::class.java),
        // Play services fills in the transition result, so the intent must be mutable.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
}

class MotionTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val result = ActivityTransitionResult.extractResult(intent) ?: return
        val event = result.transitionEvents.lastOrNull() ?: return
        val entered = event.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER
        val activity = when (event.activityType) {
            DetectedActivity.IN_VEHICLE -> if (entered) MotionActivity.IN_VEHICLE else MotionActivity.UNKNOWN
            DetectedActivity.WALKING -> MotionActivity.WALKING
            DetectedActivity.STILL -> MotionActivity.STILL
            else -> MotionActivity.UNKNOWN
        }
        val phase = MonitoringStore.state.value.phase
        if (activity == MotionActivity.IN_VEHICLE && phase == MonitorPhase.IDLE) {
            DrowsinessMonitoringService.watchMotion(context)
        } else if (phase != MonitorPhase.IDLE) {
            DrowsinessMonitoringService.sendMotion(context, activity)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val app = context.applicationContext as SafeDriveApp
        CoroutineScope(Dispatchers.Default).launch {
            try {
                if (app.settingsRepository.current().autoDetectDriving) MotionDetector.register(context)
            } finally {
                pending.finish()
            }
        }
    }
}
