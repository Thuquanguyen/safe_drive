package com.mobile.safedrive.motion

import com.mobile.safedrive.drowsiness.DrowsinessConfig

/**
 * Speed confirmation for auto-start and grace-period auto-stop (spec §2.2, §3.2).
 * Speed must stay above the threshold for the confirm duration; a stopped vehicle only
 * ends the trip after the grace period, so red lights and traffic jams don't flap.
 */
class AutoDrivingController(private var config: DrowsinessConfig) {

    enum class Action { NONE, VEHICLE_CONFIRMED, END_TRIP }

    private var fastSince = 0L
    private var stoppedSince = 0L

    val isStoppedInGrace: Boolean get() = stoppedSince > 0L

    fun update(config: DrowsinessConfig) {
        this.config = config
    }

    fun reset() {
        fastSince = 0L
        stoppedSince = 0L
    }

    /** While waiting for confirmation of an IN_VEHICLE transition. */
    fun onSpeedWhileWatching(speedKmh: Float, nowMs: Long): Action {
        if (speedKmh < config.autoStartSpeedThresholdKmh) {
            fastSince = 0L
            return Action.NONE
        }
        if (fastSince == 0L) fastSince = nowMs
        return if (nowMs - fastSince >= config.autoStartConfirmDurationSec * MS_PER_SECOND) {
            Action.VEHICLE_CONFIRMED
        } else {
            Action.NONE
        }
    }

    /** During a monitored trip. */
    fun onSpeedWhileDriving(speedKmh: Float, nowMs: Long): Action {
        if (speedKmh >= config.stoppedSpeedKmh) {
            stoppedSince = 0L
            return Action.NONE
        }
        if (stoppedSince == 0L) stoppedSince = nowMs
        return if (nowMs - stoppedSince >= config.autoStopGracePeriodMs) Action.END_TRIP else Action.NONE
    }

    private companion object {
        const val MS_PER_SECOND = 1_000L
    }
}
