package com.mobile.safedrive.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mobile.safedrive.MainActivity
import com.mobile.safedrive.R
import com.mobile.safedrive.drowsiness.DrowsinessState

/** Sticky ongoing notification + full-screen alert intent (spec §28.1, §29.1, Screen 6/7). */
class MonitoringNotificationManager(private val context: Context) {

    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels() {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITORING, "Safety monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while SafeDrive is protecting your trip"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, "Drowsiness alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Urgent warnings when drowsiness is detected"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_AUTO, "Driving detection", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Automatic driving detection"
            }
        )
    }

    fun ongoing(state: DrowsinessState, phase: MonitorPhase, speedKmh: Float, fps: Int, powerSaving: Boolean): Notification {
        val status = when (phase) {
            MonitorPhase.WATCHING_MOTION -> "Checking if you are driving…"
            MonitorPhase.CALIBRATING -> "Calibrating…"
            else -> "Protecting your trip • Status: ${statusLabel(state)}"
        }
        val detail = buildString {
            append("Speed: ${speedKmh.toInt()} km/h")
            if (fps > 0) append(if (powerSaving) " • Ultra battery saver ($fps FPS)" else " • $fps FPS")
        }
        return NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_stat_safedrive)
            .setContentTitle("Driver safety monitoring")
            .setContentText(status)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$status\n$detail"))
            .setSubText("RUNNING")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent())
            .addAction(0, "Open app", openAppIntent())
            .addAction(0, "Stop monitoring", stopIntent())
            .build()
    }

    fun updateOngoing(notification: Notification) {
        if (canPost()) manager.notify(ID_ONGOING, notification)
    }

    /** DROWSY / CRITICAL while the app is not visible: wake the screen over the lock screen. */
    fun showAlert(state: DrowsinessState) {
        if (!canPost()) return
        val critical = state == DrowsinessState.CRITICAL
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_ALERT, state.name)
        val fullScreen = PendingIntent.getActivity(
            context, REQUEST_ALERT, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_safedrive)
            .setContentTitle(if (critical) "⚠ WAKE UP" else "⚠ Stay Alert")
            .setContentText(
                if (critical) "You appear to be very drowsy. PLEASE STOP AND TAKE A BREAK"
                else "You may be getting sleepy. Please consider taking a break."
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .build()
        manager.notify(ID_ALERT, notification)
    }

    fun cancelAlert() = manager.cancel(ID_ALERT)

    fun showTapToStart() {
        if (!canPost()) return
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_START_DRIVING, true)
        val pending = PendingIntent.getActivity(
            context, REQUEST_START, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            ID_AUTO,
            NotificationCompat.Builder(context, CHANNEL_AUTO)
                .setSmallIcon(R.drawable.ic_stat_safedrive)
                .setContentTitle("Driving detected")
                .setContentText("Tap to start safety monitoring")
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
        )
    }

    private fun canPost(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context, REQUEST_OPEN,
        Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        context, REQUEST_STOP,
        Intent(context, DrowsinessMonitoringService::class.java).setAction(DrowsinessMonitoringService.ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun statusLabel(state: DrowsinessState) = when (state) {
        DrowsinessState.NORMAL -> "Alert"
        DrowsinessState.ATTENTION -> "Stay alert"
        DrowsinessState.DROWSY -> "Drowsy"
        DrowsinessState.CRITICAL -> "Danger"
    }

    companion object {
        const val ID_ONGOING = 1001
        private const val ID_ALERT = 1002
        private const val ID_AUTO = 1003
        private const val CHANNEL_MONITORING = "monitoring"
        private const val CHANNEL_ALERTS = "alerts"
        private const val CHANNEL_AUTO = "auto_detect"
        private const val REQUEST_OPEN = 1
        private const val REQUEST_STOP = 2
        private const val REQUEST_ALERT = 3
        private const val REQUEST_START = 4
    }
}
