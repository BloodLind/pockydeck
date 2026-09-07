package dev.handheld.launcher.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IdentityContractTest {
    @Test
    fun twoActivitiesInOnePackageHaveDistinctStableLosslessIdentities() {
        val first = CurrentUserAndroidComponentId(
            packageName = "example.game",
            activityClassName = "example.game.StandardActivity",
        )
        val second = CurrentUserAndroidComponentId(
            packageName = "example.game",
            activityClassName = "example.game.Arcade${'$'}Activity",
        )

        assertNotEquals(first.itemId, second.itemId)
        assertEquals(first, CurrentUserAndroidComponentId.fromItemId(first.itemId))
        assertEquals(second, CurrentUserAndroidComponentId.fromItemId(second.itemId))

        val rediscovered = first.copy()
        assertEquals(first.itemId, rediscovered.itemId)
        assertEquals(
            ContractFixtures.androidApp(first.packageName, first.activityClassName, "Same title").id,
            ContractFixtures.androidApp(rediscovered.packageName, rediscovered.activityClassName, "Renamed").id,
        )
        assertNull(CurrentUserAndroidComponentId.fromItemId(ItemId("android:bad/.Relative")))
        assertNull(CurrentUserAndroidComponentId.fromItemId(ItemId("android:bad/Class/Extra")))
    }
}
