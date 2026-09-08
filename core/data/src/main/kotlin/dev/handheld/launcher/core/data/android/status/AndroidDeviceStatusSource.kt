package dev.handheld.launcher.core.data.android.status

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.StatFs
import dev.handheld.launcher.core.domain.model.StatusValue
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DeviceStatusSnapshot(
    val batteryPercent: StatusValue<Int> = StatusValue.Unavailable,
    val batteryTemperatureCelsius: StatusValue<Float> = StatusValue.Unavailable,
    val usedMemoryBytes: StatusValue<Long> = StatusValue.Unavailable,
    val totalMemoryBytes: StatusValue<Long> = StatusValue.Unavailable,
    val freeStorageBytes: StatusValue<Long> = StatusValue.Unavailable,
    val wifiConnected: StatusValue<Boolean> = StatusValue.Unavailable,
    val wifiEnabled: StatusValue<Boolean> = StatusValue.Unavailable,
)

/** Starts callbacks and modest off-main sampling only while the foreground UI collects. */
class AndroidDeviceStatusSource(context: Context) {
    private val context = context.applicationContext

    val status: Flow<DeviceStatusSnapshot> = callbackFlow {
        val current = AtomicReference(DeviceStatusSnapshot())
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val wifiManager = context.getSystemService(WifiManager::class.java)

        fun update(transform: (DeviceStatusSnapshot) -> DeviceStatusSnapshot) {
            synchronized(current) { trySend(current.updateAndGet(transform)) }
        }

        fun readNetwork() {
            val wifi = try {
                val capabilities = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
                if (connectivity == null) StatusValue.Unsupported
                else StatusValue.Available(
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
                )
            } catch (_: SecurityException) {
                StatusValue.Unavailable
            } catch (_: RuntimeException) {
                StatusValue.Unavailable
            }
            update { it.copy(wifiConnected = wifi) }
        }

        fun readWifiRadio() {
            val enabled = readWifiRadioEnabled(wifiManager?.let { manager -> { manager.isWifiEnabled } })
            update { it.copy(wifiEnabled = enabled) }
        }

        val wifiReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.WIFI_STATE_CHANGED_ACTION) readWifiRadio()
            }
        }

        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val temperature = intent.getIntExtra(
                    BatteryManager.EXTRA_TEMPERATURE,
                    Int.MIN_VALUE,
                )
                update { previous ->
                    previous.copy(
                        batteryPercent = if (level >= 0 && scale > 0) {
                            StatusValue.Available((level * 100 / scale).coerceIn(0, 100))
                        } else {
                            StatusValue.Unavailable
                        },
                        batteryTemperatureCelsius = if (temperature != Int.MIN_VALUE) {
                            StatusValue.Available(temperature / 10f)
                        } else {
                            StatusValue.Unavailable
                        },
                    )
                }
            }
        }
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = readNetwork()
            override fun onLost(network: Network) = readNetwork()
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) = readNetwork()
        }

        val batteryRegistered = try {
            context.registerReceiver(
                batteryReceiver,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            )
            true
        } catch (_: SecurityException) {
            update {
                it.copy(
                    batteryPercent = StatusValue.Unavailable,
                    batteryTemperatureCelsius = StatusValue.Unavailable,
                )
            }
            false
        } catch (_: RuntimeException) {
            false
        }
        val networkRegistered = if (connectivity == null) {
            false
        } else try {
            connectivity.registerDefaultNetworkCallback(networkCallback)
            true
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
        val wifiRegistered = try {
            context.registerReceiver(wifiReceiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))
            true
        } catch (_: RuntimeException) { false }
        readNetwork()
        readWifiRadio()

        val sampling = launch(Dispatchers.IO) {
            while (isActive) {
                if (!networkRegistered) readNetwork()
                if (!wifiRegistered) readWifiRadio()
                val memory = ActivityManager.MemoryInfo()
                val memoryPair = try {
                    val manager = context.getSystemService(ActivityManager::class.java)
                    if (manager == null) {
                        StatusValue.Unsupported to StatusValue.Unsupported
                    } else {
                        manager.getMemoryInfo(memory)
                        StatusValue.Available(
                            (memory.totalMem - memory.availMem).coerceAtLeast(0L),
                        ) to StatusValue.Available(memory.totalMem)
                    }
                } catch (_: RuntimeException) {
                    StatusValue.Unavailable to StatusValue.Unavailable
                }
                val storage = try {
                    StatusValue.Available(StatFs(context.filesDir.absolutePath).availableBytes)
                } catch (_: RuntimeException) {
                    StatusValue.Unavailable
                }
                update {
                    it.copy(
                        usedMemoryBytes = memoryPair.first,
                        totalMemoryBytes = memoryPair.second,
                        freeStorageBytes = storage,
                    )
                }
                delay(SAMPLE_INTERVAL_MILLIS)
            }
        }

        awaitClose {
            sampling.cancel()
            if (batteryRegistered) {
                runCatching { context.unregisterReceiver(batteryReceiver) }
            }
            if (networkRegistered && connectivity != null) {
                runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
            }
            if (wifiRegistered) runCatching { context.unregisterReceiver(wifiReceiver) }
        }
    }.buffer(Channel.CONFLATED)

    private companion object {
        const val SAMPLE_INTERVAL_MILLIS = 15_000L
    }
}
