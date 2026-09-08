package dev.handheld.launcher.core.data.discovery

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dev.handheld.launcher.core.data.rom.emulator.EmulatorRegistry
import dev.handheld.launcher.core.domain.model.Availability
import dev.handheld.launcher.core.domain.model.CatalogInventory
import dev.handheld.launcher.core.domain.model.CurrentUserAndroidComponentId
import dev.handheld.launcher.core.domain.model.IncompleteInventoryReason
import dev.handheld.launcher.core.domain.model.InventoryScope
import dev.handheld.launcher.core.domain.model.LibraryCategory
import dev.handheld.launcher.core.domain.model.LibraryItem
import dev.handheld.launcher.core.domain.model.SupportedItemAction
import dev.handheld.launcher.core.domain.policy.LibraryItemOrdering
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Enumerates launchable Android activities for the user running this process. */
fun interface AndroidAppDiscovery {
    suspend fun discover(): CatalogInventory
}

/**
 * Reads the Android launcher surface through one exact MAIN/LAUNCHER PackageManager query.
 *
 * The [CurrentUserAndroidComponentId] carried by every result is also the stable reference a
 * later icon loader can resolve. Discovery does not decode or load artwork.
 */
class PackageManagerAndroidAppDiscovery private constructor(
    private val source: AndroidLauncherActivitySource,
    private val ownPackageName: String,
    private val dispatcher: CoroutineDispatcher,
) : AndroidAppDiscovery {
    constructor(
        context: Context,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(
        source = PackageManagerAndroidLauncherActivitySource(
            context.applicationContext.packageManager,
        ),
        ownPackageName = context.applicationContext.packageName,
        dispatcher = dispatcher,
    )

    internal constructor(
        source: AndroidLauncherActivitySource,
        ownPackageName: String,
        dispatcher: CoroutineDispatcher,
        @Suppress("UNUSED_PARAMETER") testOnly: Unit = Unit,
    ) : this(source, ownPackageName, dispatcher)

    override suspend fun discover(): CatalogInventory =
        try {
            withContext(dispatcher) {
                CatalogInventory.Complete(
                    scope = InventoryScope.CurrentUserAndroid,
                    observedItems = source.queryMainLauncherActivities()
                        .asSequence()
                        .mapNotNull { it.toLibraryItem(ownPackageName) }
                        .distinctBy { it.id }
                        .sortedWith(LibraryItemOrdering.titleThenId)
                        .toList(),
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CatalogInventory.Incomplete(
                scope = InventoryScope.CurrentUserAndroid,
                observedItems = emptyList(),
                reason = IncompleteInventoryReason.FAILED,
            )
        }
}

internal fun interface AndroidLauncherActivitySource {
    fun queryMainLauncherActivities(): List<AndroidLauncherActivity>
}

internal data class AndroidLauncherActivity(
    val packageName: String,
    val activityClassName: String,
    val label: String?,
    val activityEnabled: Boolean,
    val applicationEnabled: Boolean,
    val exported: Boolean,
    val declaredGame: Boolean = false,
)

internal class PackageManagerAndroidLauncherActivitySource(
    private val packageManager: PackageManager,
) : AndroidLauncherActivitySource {
    override fun queryMainLauncherActivities(): List<AndroidLauncherActivity> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(
            launcherIntent,
            PackageManager.ResolveInfoFlags.of(0L),
        ).mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            AndroidLauncherActivity(
                packageName = activityInfo.packageName,
                activityClassName = activityInfo.name,
                label = runCatching { resolveInfo.loadLabel(packageManager)?.toString() }
                    .getOrNull(),
                activityEnabled = activityInfo.enabled,
                applicationEnabled = activityInfo.applicationInfo.enabled,
                exported = activityInfo.exported,
                declaredGame = activityInfo.applicationInfo.category == ApplicationInfo.CATEGORY_GAME ||
                    activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_IS_GAME != 0,
            )
        }
    }
}

private fun AndroidLauncherActivity.toLibraryItem(
    ownPackageName: String,
): LibraryItem.AndroidApp? {
    val packageName = packageName.trim()
    if (packageName.isEmpty() || '/' in packageName || packageName == ownPackageName) return null
    if (!activityEnabled || !applicationEnabled || !exported) return null

    val rawClassName = activityClassName.trim()
    if (rawClassName.isEmpty() || rawClassName == "." || '/' in rawClassName) return null
    val qualifiedClassName = when {
        rawClassName.startsWith('.') -> packageName + rawClassName
        '.' !in rawClassName -> "$packageName.$rawClassName"
        else -> rawClassName
    }

    val componentId = runCatching {
        CurrentUserAndroidComponentId(packageName, qualifiedClassName)
    }.getOrNull() ?: return null
    val title = label?.trim().takeUnless { it.isNullOrEmpty() }
        ?: qualifiedClassName.substringAfterLast('.').ifBlank { qualifiedClassName }

    return LibraryItem.AndroidApp(
        componentId = componentId,
        title = title,
        category = when {
            packageName in knownGameFrontendPackages -> LibraryCategory.OTHER
            packageName in knownEmulatorPackages -> LibraryCategory.EMULATOR
            declaredGame -> LibraryCategory.GAME
            else -> LibraryCategory.OTHER
        },
        availability = Availability.Available,
        supportedActions = ANDROID_APP_ACTIONS,
    )
}

private val knownEmulatorPackages by lazy { EmulatorRegistry.profiles.map { it.packageName }.toSet() }

// These apps declare Android's game category for controller/system integration, but the
// installed frontend itself is an app; its individual game entries belong in Library.
private val knownGameFrontendPackages = setOf(
    "org.es_de.frontend", "app.gamenative", "gamehub.lite", "emuready.gamehub.lite", "com.limelight.noir",
)

private val ANDROID_APP_ACTIONS = setOf(
    SupportedItemAction.OPEN,
    SupportedItemAction.VIEW_DETAILS,
    SupportedItemAction.TOGGLE_FAVORITE,
    SupportedItemAction.OPEN_APP_INFO,
)
