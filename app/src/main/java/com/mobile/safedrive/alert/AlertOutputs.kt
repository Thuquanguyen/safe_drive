package com.mobile.safedrive.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Alarm-stream tones so alerts are heard even in Vibrate / Silent / DND (spec §29). */
class AudioAlert(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var tone: ToneGenerator? = null
    private var toneVolume = -1
    private var savedAlarmVolume: Int? = null

    fun attention(volume: Float) = play(volume, ToneGenerator.TONE_PROP_BEEP2, ATTENTION_MS)
    fun drowsy(volume: Float) = play(volume, ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, DROWSY_MS)

    /** Critical: rapid alarm at maximum alarm-stream volume. */
    fun critical() {
        if (savedAlarmVolume == null) {
            savedAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            runCatching {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0
                )
            }
        }
        play(1f, ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, CRITICAL_MS)
    }

    fun stop() {
        tone?.stopTone()
        savedAlarmVolume?.let { runCatching { audioManager.setStreamVolume(AudioManager.STREAM_ALARM, it, 0) } }
        savedAlarmVolume = null
    }

    fun release() {
        stop()
        tone?.release()
        tone = null
    }

    private fun play(volume: Float, toneType: Int, durationMs: Int) {
        val percent = (volume.coerceIn(MIN_VOLUME, 1f) * ToneGenerator.MAX_VOLUME).toInt()
        if (tone == null || percent != toneVolume) {
            tone?.release()
            tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, percent) }.getOrNull()
            toneVolume = percent
        }
        tone?.startTone(toneType, durationMs)
    }

    private companion object {
        const val ATTENTION_MS = 300
        const val DROWSY_MS = 1_500
        const val CRITICAL_MS = 4_000
        const val MIN_VOLUME = 0.1f
    }
}

class VibrationAlert(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    private val alarmAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun short() = vibrate(VibrationEffect.createOneShot(SHORT_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    fun long() = vibrate(VibrationEffect.createOneShot(LONG_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    fun repeated() = vibrate(VibrationEffect.createWaveform(REPEATED_PATTERN, REPEAT_FROM_START))
    fun stop() = vibrator.cancel()

    @Suppress("DEPRECATION")
    private fun vibrate(effect: VibrationEffect) {
        if (vibrator.hasVibrator()) vibrator.vibrate(effect, alarmAttributes)
    }

    private companion object {
        const val SHORT_MS = 150L
        const val LONG_MS = 800L
        const val REPEAT_FROM_START = 0
        val REPEATED_PATTERN = longArrayOf(0, 500, 200, 500, 200, 500, 400)
    }
}

class VoiceAlert(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Locale.US
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        ready = true
    }

    fun speak(text: String) {
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, text)
    }

    fun stop() {
        if (ready) tts.stop()
    }

    fun release() {
        tts.shutdown()
    }

    companion object {
        const val STAY_ALERT = "Please stay alert."
        const val GETTING_SLEEPY = "You appear to be getting sleepy."
        const val TAKE_A_BREAK = "Please take a break."
        const val MONITORING_ON = "Safety monitoring is on."
    }
}
