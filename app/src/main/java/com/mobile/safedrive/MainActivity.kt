package com.mobile.safedrive

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import com.mobile.safedrive.service.AppVisibility
import com.mobile.safedrive.ui.navigation.SafeDriveNavHost
import com.mobile.safedrive.ui.theme.SafeDriveTheme

class MainActivity : ComponentActivity() {

    /** Set when launched from the "Driving detected — tap to start" notification. */
    private val startDrivingRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBars(dark = false)
        handleIntent(intent)
        val app = application as SafeDriveApp
        setContent {
            val settings by app.settingsRepository.settings.collectAsState(initial = null)
            settings?.let { loaded ->
                LaunchedEffect(loaded.darkMode) { applySystemBars(loaded.darkMode) }
                SafeDriveTheme(darkMode = loaded.darkMode) {
                    SafeDriveNavHost(
                        settings = loaded,
                        startDrivingRequest = startDrivingRequest,
                        onAlertCleared = ::clearLockScreenFlags,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.isVisible = true
    }

    override fun onStop() {
        AppVisibility.isVisible = false
        super.onStop()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_ALERT) != null) {
            // Full-screen alert: light up and show over the lock screen without unlocking (spec §29.1).
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        if (intent?.getBooleanExtra(EXTRA_START_DRIVING, false) == true) startDrivingRequest.value = true
    }

    private fun clearLockScreenFlags() {
        setShowWhenLocked(false)
        setTurnScreenOn(false)
    }

    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    companion object {
        const val EXTRA_ALERT = "alert_state"
        const val EXTRA_START_DRIVING = "start_driving"
    }
}
