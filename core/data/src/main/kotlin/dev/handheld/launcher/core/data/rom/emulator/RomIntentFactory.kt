package dev.handheld.launcher.core.data.rom.emulator

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.net.Uri

/** Builds only independently verified contracts. Eligibility and document checks precede this call. */
internal object RomIntentFactory {
    fun build(
        profile: EmulatorProfile,
        input: RomLaunchInput,
        validatedTree: Uri?,
        emulatorDataDir: String?,
        primaryStorage: String,
    ): Intent {
        val uri = Uri.parse(input.documentUri)
        val intent = Intent(Intent.ACTION_VIEW)
            .setComponent(ComponentName(profile.packageName, requireNotNull(profile.activityName)))
            .setDataAndType(uri, input.mimeType ?: "application/octet-stream")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        when (profile.contract) {
            EmulatorContract.MELONDS -> {
                intent.action = "${profile.packageName}.LAUNCH_ROM"
                intent.putExtra("uri", uri.toString())
            }
            EmulatorContract.BOOT_PATH -> {
                intent.action = Intent.ACTION_MAIN
                intent.putExtra("bootPath", uri.toString())
            }
            EmulatorContract.RETROARCH -> {
                val core = EmulatorRegistry.coresForPlatform(input.platformId).first { it.id == input.coreId }
                val safPath = requireNotNull(validatedTree?.let { RomLaunchPolicy.retroArchPath(it.toString(), input.relativePath.orEmpty()) })
                val dataDir = requireNotNull(emulatorDataDir)
                intent.action = Intent.ACTION_MAIN
                intent.putExtra("ROM", safPath)
                intent.putExtra("LIBRETRO", "$dataDir/cores/${core.id}_libretro_android.so")
                // Upstream's legacy entry point needs the normal emulator configuration locations.
                // These describe its private configuration, never an invented ROM filesystem path.
                val emulatorFiles = "$primaryStorage/Android/data/${profile.packageName}/files"
                intent.putExtra("DATADIR", dataDir)
                intent.putExtra("SDCARD", primaryStorage)
                intent.putExtra("EXTERNAL", emulatorFiles)
                intent.putExtra("CONFIGFILE", "$emulatorFiles/retroarch.cfg")
            }
            EmulatorContract.VIEW -> Unit
            EmulatorContract.DETECTION_ONLY -> error("A detection-only emulator cannot be dispatched")
        }
        val readUris = (listOf(input.documentUri) + input.companionUris).distinct().map(Uri::parse)
        // Dolphin treats every ClipData entry as a disc, never as a permission-only item.
        val grants = if (profile.id == "dolphin") listOf(uri)
        else if (validatedTree != null && (profile.contract == EmulatorContract.RETROARCH || input.companionUris.isNotEmpty())) {
            intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            (readUris + validatedTree).distinct()
        } else readUris
        intent.clipData = ClipData.newRawUri("Game", grants.first()).apply {
            grants.drop(1).forEach { addItem(ClipData.Item(it)) }
        }
        return intent
    }
}
