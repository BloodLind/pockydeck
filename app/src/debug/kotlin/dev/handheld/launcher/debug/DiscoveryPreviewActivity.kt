package dev.handheld.launcher.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.handheld.launcher.core.data.discovery.PackageManagerAndroidAppDiscovery
import dev.handheld.launcher.core.designsystem.theme.LauncherTheme
import dev.handheld.launcher.core.domain.model.CatalogInventory
import kotlinx.coroutines.launch

/** On-device evidence of the production query under the app's own package visibility. */
class DiscoveryPreviewActivity : ComponentActivity() {
    private var report by mutableStateOf("Reading launchable components…")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val inventory = PackageManagerAndroidAppDiscovery(applicationContext).discover()
            report = if (inventory is CatalogInventory.Complete) {
                val selfIncluded = inventory.observedItems.any { it.id.value.startsWith("android:$packageName/") }
                buildString {
                    appendLine("Complete current-user inventory: ${inventory.observedItems.size} components")
                    appendLine("Self included: $selfIncluded")
                    inventory.observedItems.forEach { appendLine("${it.title}: ${it.id.value}") }
                }
            } else "Inventory failed; no complete inventory produced."
        }
        setContent {
            LauncherTheme(referenceScale = 2f / 3f) {
                Column(Modifier.fillMaxSize().background(LauncherTheme.colors.backgroundGradient())
                    .verticalScroll(rememberScrollState()).padding(24.dp)) {
                    BasicText(report, style = LauncherTheme.typography.body.copy(color = LauncherTheme.colors.textPrimary))
                }
            }
        }
    }
}
