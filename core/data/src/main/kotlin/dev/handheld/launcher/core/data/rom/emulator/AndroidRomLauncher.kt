package dev.handheld.launcher.core.data.rom.emulator

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One dispatch, with fresh eligibility and readability checks. Started means Android accepted it. */
class AndroidRomLauncher(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val applicationContext = context.applicationContext
    private val resolver = AndroidEmulatorResolver(applicationContext, dispatcher)

    suspend fun dispatch(input: RomLaunchInput, emulatorId: String): RomDispatchResult = withContext(dispatcher) {
        val profile = EmulatorRegistry.profiles.firstOrNull { it.id == emulatorId }
            ?: return@withContext RomDispatchResult.Unavailable("This emulator is no longer supported. Choose another app.")
        val resolution = resolver.resolve(input)
        val candidate = resolution.candidates.firstOrNull { it.id == emulatorId }
            ?: return@withContext RomDispatchResult.Unsupported(
                resolution.detectedUnsupported.firstOrNull { it.id == emulatorId }?.reason
                    ?: "The selected emulator is no longer available. Choose another app.",
            )
        if (candidate.launchSupport == EmulatorLaunchSupport.NEEDS_CORE) {
            return@withContext RomDispatchResult.Unsupported("Choose and install a RetroArch core for this console in Settings.")
        }
        if (input.companionUris.size > 256 || input.companionUris.any { !AndroidEmulatorResolver.isDocumentUri(it) }) {
            return@withContext RomDispatchResult.Unsupported("This game has an unsupported companion-file layout.")
        }
        val readUris = (listOf(input.documentUri) + input.companionUris).distinct().map(Uri::parse)
        try {
            for (uri in readUris) {
                val descriptor = applicationContext.contentResolver.openFileDescriptor(uri, "r")
                    ?: return@withContext RomDispatchResult.Unreadable("The game or a companion file is unavailable. Reconnect its storage and rescan.")
                descriptor.use { /* Opening establishes current readability without reading game contents. */ }
            }
        } catch (_: SecurityException) {
            return@withContext RomDispatchResult.Unreadable("Folder access was removed. Add the ROM folder again.")
        } catch (_: FileNotFoundException) {
            return@withContext RomDispatchResult.Unreadable("The game or a companion file is missing. Reconnect its storage and rescan.")
        } catch (_: IOException) {
            return@withContext RomDispatchResult.Unreadable("The game could not be read. Reconnect its storage and try again.")
        } catch (_: RuntimeException) {
            return@withContext RomDispatchResult.Unreadable("The document provider could not open this game. Try again after reconnecting its storage.")
        }
        val tree = resolver.validatedTree(input)
        val appInfo = resolver.packageInfo(profile.packageName)?.applicationInfo
            ?: return@withContext RomDispatchResult.Unavailable("The emulator was removed. Choose another app.")
        val intent = try {
            RomIntentFactory.build(profile, input, tree, appInfo.dataDir, Environment.getExternalStorageDirectory().absolutePath)
        } catch (_: IllegalArgumentException) {
            return@withContext RomDispatchResult.Unsupported("This game or core configuration cannot be passed to the emulator.")
        } catch (_: NoSuchElementException) {
            return@withContext RomDispatchResult.Unsupported("Choose a compatible RetroArch core in Settings.")
        }
        try {
            applicationContext.startActivity(intent)
            RomDispatchResult.Started
        } catch (_: ActivityNotFoundException) {
            RomDispatchResult.Unavailable("The emulator's ROM activity is unavailable. Choose another app.")
        } catch (_: SecurityException) {
            RomDispatchResult.Rejected("Android refused access to the emulator or game. Re-add its folder or choose another app.")
        } catch (_: RuntimeException) {
            RomDispatchResult.Failed("Android could not open the emulator. Try again or choose another app.")
        }
    }
}
