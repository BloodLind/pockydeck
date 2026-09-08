package dev.handheld.launcher.core.data.rom.emulator

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Current-user, allowlisted emulator inventory. Does not query arbitrary VIEW handlers. */
class AndroidEmulatorResolver(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val applicationContext = context.applicationContext
    private val packageManager = applicationContext.packageManager

    suspend fun installedForPlatform(platformId: String): List<InstalledEmulator> = withContext(dispatcher) {
        EmulatorRegistry.profiles.filter { platformId in it.platforms }.mapNotNull { profile ->
            installed(profile, platformId)
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
    }

    suspend fun resolve(input: RomLaunchInput): EmulatorResolution = withContext(dispatcher) {
        val candidates = mutableListOf<InstalledEmulator>()
        val unsupported = mutableListOf<InstalledEmulator>()
        for (profile in EmulatorRegistry.profiles.filter { input.platformId in it.platforms }) {
            val installed = installed(profile, input.platformId) ?: continue
            val reason = installed.reason ?: RomLaunchPolicy.unsupportedReason(profile, input) ?: when {
                !isDocumentUri(input.documentUri) -> "The game needs a readable document URI. Add its folder again."
                profile.contract == EmulatorContract.RETROARCH && validatedTree(input) == null ->
                    "This provider does not expose the folder layout required by RetroArch. Use a local ROM folder or the launcher's prepared archive."
                else -> null
            }
            if (reason != null) unsupported += installed.copy(launchSupport = EmulatorLaunchSupport.UNSUPPORTED, reason = reason)
            else candidates += installed.copy(
                supportedExtensions = RomLaunchPolicy.extensions(profile, input.platformId, input.coreId),
                launchSupport = if (profile.contract == EmulatorContract.RETROARCH && input.coreId == null)
                    EmulatorLaunchSupport.NEEDS_CORE else EmulatorLaunchSupport.DIRECT,
            )
        }
        EmulatorResolution(
            candidates.sortedBy { it.displayName.lowercase(Locale.ROOT) },
            unsupported.sortedBy { it.displayName.lowercase(Locale.ROOT) },
            needsExtraction = input.extension.removePrefix(".").lowercase(Locale.ROOT) in RomLaunchPolicy.archiveExtensions &&
                candidates.isEmpty() && unsupported.any { it.supportedExtensions.isNotEmpty() },
        )
    }

    internal fun installed(profile: EmulatorProfile, platformId: String): InstalledEmulator? {
        val info = packageInfo(profile.packageName) ?: return null
        val appInfo = info.applicationInfo ?: return null
        if (!appInfo.enabled || appInfo.flags and ApplicationInfo.FLAG_SUSPENDED != 0) return null
        val componentAvailable = profile.activityName?.let { name ->
            try {
                val activity = packageManager.getActivityInfo(ComponentName(profile.packageName, name), PackageManager.ComponentInfoFlags.of(0))
                activity.enabled && activity.exported && activity.applicationInfo.enabled &&
                    (activity.permission.isNullOrBlank() || packageManager.checkPermission(activity.permission, applicationContext.packageName) == PackageManager.PERMISSION_GRANTED)
            } catch (_: PackageManager.NameNotFoundException) { false }
            catch (_: RuntimeException) { false }
        } ?: false
        val reason = when {
            profile.contract == EmulatorContract.DETECTION_ONLY -> profile.unavailableReason
            !componentAvailable -> "This installed version does not expose the expected ROM entry point. Update the emulator or choose another app."
            profile.contract == EmulatorContract.RETROARCH && !supportsRetroArchSaf(info.versionName) ->
                "Update RetroArch to version 1.22.2 or newer for this folder launch adapter."
            else -> null
        }
        return InstalledEmulator(
            profile.id, profile.packageName, profile.displayName, info.versionName, profile.platforms,
            RomLaunchPolicy.extensions(profile, platformId, null),
            when {
                reason != null -> EmulatorLaunchSupport.UNSUPPORTED
                profile.contract == EmulatorContract.RETROARCH -> EmulatorLaunchSupport.NEEDS_CORE
                else -> EmulatorLaunchSupport.DIRECT
            }, reason,
            if (profile.contract == EmulatorContract.RETROARCH) EmulatorRegistry.coresForPlatform(platformId) else emptyList(),
        )
    }

    internal fun packageInfo(packageName: String): PackageInfo? = try {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } catch (_: PackageManager.NameNotFoundException) { null }
    catch (_: RuntimeException) { null }

    internal fun validatedTree(input: RomLaunchInput): Uri? {
        return try {
        val tree = input.treeUri?.let(Uri::parse) ?: return null
        val relative = input.relativePath ?: return null
        if (tree.scheme != "content" || !RomLaunchPolicy.validRelativePath(relative) || !DocumentsContract.isTreeUri(tree)) return null
        val game = Uri.parse(input.documentUri)
        if (game.scheme != "content" || game.authority != tree.authority) return null
        val expectedId = DocumentsContract.getTreeDocumentId(tree) + "/" + relative
        if (DocumentsContract.getDocumentId(game) != expectedId) return null
        val projected = DocumentsContract.buildDocumentUriUsingTree(tree, expectedId)
        if (projected != game) return null
        tree
        } catch (_: RuntimeException) { null }
    }

    internal companion object {
        fun isDocumentUri(value: String): Boolean = try {
            val uri = Uri.parse(value)
            uri.scheme == "content" && !uri.authority.isNullOrBlank() && uri.fragment == null && uri.query == null
        } catch (_: RuntimeException) { false }

        fun supportsRetroArchSaf(version: String?): Boolean {
            val match = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)").find(version.orEmpty()) ?: return false
            val numbers = match.groupValues.drop(1).map { it.toIntOrNull() ?: return false }
            return numbers[0] > 1 || numbers[0] == 1 && (numbers[1] > 22 || numbers[1] == 22 && numbers[2] >= 2)
        }
    }
}
