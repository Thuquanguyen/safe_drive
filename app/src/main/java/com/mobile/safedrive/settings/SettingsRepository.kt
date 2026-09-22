package com.mobile.safedrive.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class Sensitivity { LOW, NORMAL, HIGH }

data class AppSettings(
    val onboardingDone: Boolean = false,
    val autoDetectDriving: Boolean = false,
    val autoStartSpeedKmh: Int = 25,
    val screenOffMonitoring: Boolean = true,
    val ultraBatterySaver: Boolean = true,
    val gracePeriodMinutes: Int = 5,
    val alertVolume: Float = 1f,
    val vibration: Boolean = true,
    val voiceAlerts: Boolean = true,
    val sensitivity: Sensitivity = Sensitivity.NORMAL,
    val keepScreenAwake: Boolean = false,
    val darkMode: Boolean = false,
) {
    companion object {
        val SPEED_OPTIONS = listOf(20, 25, 30)
        val GRACE_OPTIONS = listOf(3, 5, 10)
    }
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val onboardingDone = booleanPreferencesKey("onboarding_done")
        val autoDetect = booleanPreferencesKey("auto_detect")
        val autoSpeed = intPreferencesKey("auto_speed_kmh")
        val screenOff = booleanPreferencesKey("screen_off_monitoring")
        val batterySaver = booleanPreferencesKey("ultra_battery_saver")
        val grace = intPreferencesKey("grace_minutes")
        val volume = floatPreferencesKey("alert_volume")
        val vibration = booleanPreferencesKey("vibration")
        val voice = booleanPreferencesKey("voice_alerts")
        val sensitivity = stringPreferencesKey("sensitivity")
        val keepAwake = booleanPreferencesKey("keep_screen_awake")
        val darkMode = booleanPreferencesKey("dark_mode")
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            onboardingDone = this[Keys.onboardingDone] ?: defaults.onboardingDone,
            autoDetectDriving = this[Keys.autoDetect] ?: defaults.autoDetectDriving,
            autoStartSpeedKmh = this[Keys.autoSpeed] ?: defaults.autoStartSpeedKmh,
            screenOffMonitoring = this[Keys.screenOff] ?: defaults.screenOffMonitoring,
            ultraBatterySaver = this[Keys.batterySaver] ?: defaults.ultraBatterySaver,
            gracePeriodMinutes = this[Keys.grace] ?: defaults.gracePeriodMinutes,
            alertVolume = this[Keys.volume] ?: defaults.alertVolume,
            vibration = this[Keys.vibration] ?: defaults.vibration,
            voiceAlerts = this[Keys.voice] ?: defaults.voiceAlerts,
            sensitivity = this[Keys.sensitivity]?.let { runCatching { Sensitivity.valueOf(it) }.getOrNull() }
                ?: defaults.sensitivity,
            keepScreenAwake = this[Keys.keepAwake] ?: defaults.keepScreenAwake,
            darkMode = this[Keys.darkMode] ?: defaults.darkMode,
        )
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { p ->
            val new = transform(p.toSettings())
            p[Keys.onboardingDone] = new.onboardingDone
            p[Keys.autoDetect] = new.autoDetectDriving
            p[Keys.autoSpeed] = new.autoStartSpeedKmh
            p[Keys.screenOff] = new.screenOffMonitoring
            p[Keys.batterySaver] = new.ultraBatterySaver
            p[Keys.grace] = new.gracePeriodMinutes
            p[Keys.volume] = new.alertVolume
            p[Keys.vibration] = new.vibration
            p[Keys.voice] = new.voiceAlerts
            p[Keys.sensitivity] = new.sensitivity.name
            p[Keys.keepAwake] = new.keepScreenAwake
            p[Keys.darkMode] = new.darkMode
        }
    }
}
