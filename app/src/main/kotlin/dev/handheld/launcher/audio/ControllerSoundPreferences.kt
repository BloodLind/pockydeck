package dev.handheld.launcher.audio

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small app preference; independent of the catalog and its database migrations. */
internal class ControllerSoundPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("controller-sounds", Context.MODE_PRIVATE)
    private val mutableEnabled = MutableStateFlow(preferences.getBoolean("enabled", true))
    val enabled: StateFlow<Boolean> = mutableEnabled.asStateFlow()

    fun setEnabled(value: Boolean) {
        mutableEnabled.value = value
        preferences.edit().putBoolean("enabled", value).apply()
    }
}
