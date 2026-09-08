package dev.handheld.launcher.core.data.android.status

import dev.handheld.launcher.core.domain.model.StatusValue

/** Radio enablement is independent of association, default transport, and internet access. */
internal fun readWifiRadioEnabled(read: (() -> Boolean)?): StatusValue<Boolean> = when (read) {
    null -> StatusValue.Unsupported
    else -> try { StatusValue.Available(read()) } catch (_: RuntimeException) { StatusValue.Unavailable }
}
