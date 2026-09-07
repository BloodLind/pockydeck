package dev.handheld.launcher.core.domain.policy

import org.junit.Assert.assertThrows
import org.junit.Test

class ModuleBoundaryTest {
    @Test
    fun domainRuntimeClasspathContainsNoAndroidOrComposeTypes() {
        listOf(
            "android.content.Context",
            "androidx.compose.runtime.Composable",
        ).forEach { forbiddenType ->
            assertThrows(ClassNotFoundException::class.java) {
                Class.forName(forbiddenType)
            }
        }
    }
}
