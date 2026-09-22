package com.mobile.safedrive

import android.app.Application
import com.mobile.safedrive.motion.MotionDetector
import com.mobile.safedrive.service.MonitoringNotificationManager
import com.mobile.safedrive.session.SessionRepository
import com.mobile.safedrive.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SafeDriveApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var sessionRepository: SessionRepository
        private set

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository(this)
        sessionRepository = SessionRepository(this)
        MonitoringNotificationManager(this).ensureChannels()
        appScope.launch {
            sessionRepository.load()
            if (settingsRepository.current().autoDetectDriving) MotionDetector.register(this@SafeDriveApp)
        }
    }
}
