package dev.handheld.launcher.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.handheld.launcher.contract.SemanticActionPort
import dev.handheld.launcher.core.data.android.status.DeviceStatusSnapshot
import dev.handheld.launcher.core.data.android.status.StorageVolumeKind
import dev.handheld.launcher.core.data.android.status.StorageVolumeStatus
import dev.handheld.launcher.core.designsystem.foundation.ShellMetrics
import dev.handheld.launcher.core.designsystem.foundation.ShellMetricsInput
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.StatusValue
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceStatusStripTest {
    @get:Rule val compose = createComposeRule()

    @Test fun multipleVolumesKeepAllSemanticsAndStayBeforeBatteryAtLargeDisplayScales() {
        var uiScale by mutableStateOf(1.1f)
        val status = DeviceStatusSnapshot(
            batteryPercent = StatusValue.Available(10), batteryCharging = StatusValue.Available(true),
            batteryTemperatureCelsius = StatusValue.Available(32f),
            usedMemoryBytes = StatusValue.Available(3 * GIB), totalMemoryBytes = StatusValue.Available(8 * GIB),
            wifiEnabled = StatusValue.Available(true), bluetoothEnabled = StatusValue.Available(true),
            notificationsPresent = StatusValue.Available(true), storageVolumes = StatusValue.Available(listOf(
                StorageVolumeStatus("internal", StorageVolumeKind.INTERNAL, StatusValue.Available(100 * GIB)),
                StorageVolumeStatus("private-card", StorageVolumeKind.EXTERNAL, StatusValue.Available(80 * GIB)),
                StorageVolumeStatus("private-usb", StorageVolumeKind.EXTERNAL, StatusValue.Available(20 * GIB)),
            )),
        ).toShellStatus().copy(clock = "10:42")
        val externalDescription = status.readings.last().accessibilityDescription!!
        compose.setContent {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val metrics = with(density) {
                    ShellMetrics.calculate(ShellMetricsInput(maxWidth.roundToPx(), maxHeight.roundToPx(), this.density, fontScale))
                }
                LauncherTheme(referenceScale = metrics.referenceScale, uiScaleFactor = uiScale, reducedMotion = true) {
                    LauncherShell(metrics, LauncherShellState(status = status), SemanticActionPort { false },
                        onDestinationSelected = {}, content = { Box(it) })
                }
            }
        }
        for (scale in listOf(1.1f, 1.2f)) {
            compose.runOnIdle { uiScale = scale }
            val external = compose.onNodeWithContentDescription(externalDescription)
            val battery = compose.onNodeWithContentDescription("Battery: 10%, charging")
            external.assertIsDisplayed()
            battery.assertIsDisplayed()
            compose.onNodeWithContentDescription("Bluetooth enabled").assertIsDisplayed()
            compose.onNodeWithContentDescription("Notifications available").assertIsDisplayed()
            val extBounds = external.fetchSemanticsNode().boundsInRoot
            val batteryBounds = battery.fetchSemanticsNode().boundsInRoot
            val shellBounds = compose.onNodeWithTag(LauncherShellTags.Status).fetchSemanticsNode().boundsInRoot
            assertTrue("External storage overlaps battery at $scale", extBounds.right <= batteryBounds.left)
            assertTrue("Battery overflows the status strip at $scale", batteryBounds.right <= shellBounds.right + 1f)
            assertTrue("Each external volume retains an accessible reading", externalDescription.contains("External storage 1") &&
                externalDescription.contains("External storage 2"))
        }
    }

    private companion object { const val GIB = 1_073_741_824L }
}
