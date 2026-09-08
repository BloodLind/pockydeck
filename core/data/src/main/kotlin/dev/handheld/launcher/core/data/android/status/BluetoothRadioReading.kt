package dev.handheld.launcher.core.data.android.status

import dev.handheld.launcher.core.domain.model.StatusValue

/** Null means no adapter; denied or failed reads never become a false/off reading. */
internal fun readBluetoothRadioEnabled(read: () -> Boolean?): StatusValue<Boolean> = try {
    read()?.let { StatusValue.Available(it) } ?: StatusValue.Unsupported
} catch (_: RuntimeException) {
    StatusValue.Unavailable
}
