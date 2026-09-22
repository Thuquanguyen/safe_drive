package com.mobile.safedrive.service

import com.mobile.safedrive.drowsiness.DrowsinessState
import com.mobile.safedrive.drowsiness.MonitorCondition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class MonitorPhase { IDLE, WATCHING_MOTION, CALIBRATING, MONITORING }

/** Camera-quality problems that get their own prompt screen. */
enum class MonitorIssue { EYES_NOT_VISIBLE, LOW_LIGHT }

enum class CalibrationStatus { NONE, RUNNING, DONE, FAILED }

data class MonitorUiState(
    val phase: MonitorPhase = MonitorPhase.IDLE,
    val sessionId: String? = null,
    val startTimeMs: Long = 0L,
    val autoStarted: Boolean = false,
    val state: DrowsinessState = DrowsinessState.NORMAL,
    /** Highest level the driver has acknowledged; alerts at or below it are not re-shown. */
    val acknowledgedLevel: DrowsinessState = DrowsinessState.NORMAL,
    val drowsinessScore: Float = 0f,
    val condition: MonitorCondition = MonitorCondition.OK,
    val issue: MonitorIssue? = null,
    val distanceKm: Float = 0f,
    val speedKmh: Float = 0f,
    val targetFps: Int = 0,
    val calibrationStatus: CalibrationStatus = CalibrationStatus.NONE,
    val calibrationProgress: Float = 0f,
    val calibrationFaceVisible: Boolean = false,
    val cameraError: Boolean = false,
    val modelAvailable: Boolean = true,
) {
    /** Alert level the UI should currently present, or NORMAL. */
    val pendingAlert: DrowsinessState
        get() = if (phase == MonitorPhase.MONITORING && state > acknowledgedLevel) state else DrowsinessState.NORMAL
}

/** Process-wide bridge between [DrowsinessMonitoringService] and the Compose UI. */
object MonitoringStore {
    private val _state = MutableStateFlow(MonitorUiState())
    val state: StateFlow<MonitorUiState> = _state.asStateFlow()

    internal fun update(transform: (MonitorUiState) -> MonitorUiState) = _state.update(transform)

    internal fun reset() {
        _state.value = MonitorUiState()
    }
}
