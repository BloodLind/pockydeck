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
    private val mutableVolume = MutableStateFlow(preferences.getInt("volume", 40).coerceIn(0, 100))
    val volumePercent: StateFlow<Int> = mutableVolume.asStateFlow()
    private val mutableVibration = MutableStateFlow(preferences.getBoolean("vibration", true))
    val vibrationEnabled: StateFlow<Boolean> = mutableVibration.asStateFlow()

    fun setVolumePercent(value: Int) {
        mutableVolume.value = value.coerceIn(0, 100)
        preferences.edit().putInt("volume", mutableVolume.value).apply()
    }

    fun setVibrationEnabled(value: Boolean) {
        mutableVibration.value = value
        preferences.edit().putBoolean("vibration", value).apply()
    }

    fun setEnabled(value: Boolean) {
        mutableEnabled.value = value
        preferences.edit().putBoolean("enabled", value).apply()
    }
}
