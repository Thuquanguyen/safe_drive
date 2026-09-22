package com.mobile.safedrive.power

import android.content.Context
import android.os.PowerManager

/** PARTIAL_WAKE_LOCK only: CPU keeps analysing while display and GPU sleep (spec §36.2). */
class WakeLockManager(context: Context) {
    private val wakeLock: PowerManager.WakeLock =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafeDrive:monitoring")
            .apply { setReferenceCounted(false) }

    fun acquire() {
        if (!wakeLock.isHeld) wakeLock.acquire(MAX_HOLD_MS)
    }

    fun release() {
        if (wakeLock.isHeld) wakeLock.release()
    }

    private companion object {
        const val MAX_HOLD_MS = 10 * 60 * 60 * 1_000L
    }
}
