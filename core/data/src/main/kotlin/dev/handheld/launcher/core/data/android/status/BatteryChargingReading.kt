package dev.handheld.launcher.core.data.android.status

import android.os.BatteryManager
import dev.handheld.launcher.core.domain.model.StatusValue

/** A plugged-in or full battery is not necessarily charging. Unknown is never inferred as false. */
internal fun batteryChargingFromStatus(status: Int): StatusValue<Boolean> = when (status) {
    BatteryManager.BATTERY_STATUS_CHARGING -> StatusValue.Available(true)
    BatteryManager.BATTERY_STATUS_DISCHARGING,
    BatteryManager.BATTERY_STATUS_NOT_CHARGING,
    BatteryManager.BATTERY_STATUS_FULL -> StatusValue.Available(false)
    else -> StatusValue.Unavailable
}
