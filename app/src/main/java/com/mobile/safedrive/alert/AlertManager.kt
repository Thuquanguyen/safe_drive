package com.mobile.safedrive.alert

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.mobile.safedrive.drowsiness.DrowsinessState
import com.mobile.safedrive.settings.AppSettings

/**
 * Coordinates sound, vibration and voice per state with cooldown (spec §29, §30).
 * CRITICAL repeats until the driver recovers or monitoring stops.
 */
class AlertManager(context: Context) {
    private val audio = AudioAlert(context)
    private val vibration = VibrationAlert(context)
    private val voice = VoiceAlert(context)
    private val handler = Handler(Looper.getMainLooper())

    private var settings = AppSettings()
    private var cooldownMs = DEFAULT_COOLDOWN_MS
    private var alertedState = DrowsinessState.NORMAL
    private var lastAlertMs = 0L
    private var acknowledged = false

    fun configure(settings: AppSettings, cooldownMs: Long) {
        this.settings = settings
        this.cooldownMs = cooldownMs
    }

    /**
     * Called on every evaluation. Returns the state that was newly alerted (for session
     * counting) or null when nothing new fired.
     */
    fun onState(state: DrowsinessState, nowMs: Long): DrowsinessState? {
        if (state < alertedState) {
            stopOutputs()
            alertedState = state
            acknowledged = false
            return null
        }
        if (state == DrowsinessState.NORMAL) return null

        val escalated = state > alertedState
        val repeatAfter = when {
            state == DrowsinessState.CRITICAL -> CRITICAL_REPEAT_MS
            acknowledged -> cooldownMs * ACKNOWLEDGED_COOLDOWN_FACTOR
            else -> cooldownMs
        }
        if (!escalated && nowMs - lastAlertMs < repeatAfter) return null

        alertedState = state
        lastAlertMs = nowMs
        if (escalated) acknowledged = false
        fire(state)
        return if (escalated) state else null
    }

    /** Driver tapped Acknowledge: silence now, back off repeats (CRITICAL still repeats). */
    fun acknowledge() {
        acknowledged = true
        stopOutputs()
    }

    /** Auto-start confirmation: a light buzz and a short voice line (spec §2.2). */
    fun greet() {
        if (settings.vibration) vibration.short()
        if (settings.voiceAlerts) handler.postDelayed({ voice.speak(VoiceAlert.MONITORING_ON) }, GREETING_DELAY_MS)
    }

    fun reset() {
        stopOutputs()
        alertedState = DrowsinessState.NORMAL
        lastAlertMs = 0L
        acknowledged = false
    }

    fun release() {
        reset()
        audio.release()
        voice.release()
    }

    private fun fire(state: DrowsinessState) {
        val volume = settings.alertVolume
        when (state) {
            DrowsinessState.ATTENTION -> {
                audio.attention(volume)
                if (settings.vibration) vibration.short()
                speakLater(VoiceAlert.STAY_ALERT)
            }
            DrowsinessState.DROWSY -> {
                audio.drowsy(volume)
                if (settings.vibration) vibration.long()
                speakLater(VoiceAlert.GETTING_SLEEPY)
            }
            DrowsinessState.CRITICAL -> {
                audio.critical()
                if (settings.vibration) vibration.repeated()
                speakLater(VoiceAlert.TAKE_A_BREAK)
            }
            DrowsinessState.NORMAL -> Unit
        }
    }

    /** Voice follows the alarm tone rather than talking over it. */
    private fun speakLater(text: String) {
        if (!settings.voiceAlerts) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ voice.speak(text) }, VOICE_DELAY_MS)
    }

    private fun stopOutputs() {
        handler.removeCallbacksAndMessages(null)
        audio.stop()
        vibration.stop()
        voice.stop()
    }

    private companion object {
        const val DEFAULT_COOLDOWN_MS = 15_000L
        const val CRITICAL_REPEAT_MS = 5_000L
        const val ACKNOWLEDGED_COOLDOWN_FACTOR = 2
        const val VOICE_DELAY_MS = 1_600L
        const val GREETING_DELAY_MS = 800L
    }
}
