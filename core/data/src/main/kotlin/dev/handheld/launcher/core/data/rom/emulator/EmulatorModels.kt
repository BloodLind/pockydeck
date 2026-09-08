package dev.handheld.launcher.core.data.rom.emulator

/** A recognized game, after any required archive preparation. URIs remain opaque to callers. */
data class RomLaunchInput(
    val platformId: String,
    val extension: String,
    val documentUri: String,
    val mimeType: String? = null,
    val treeUri: String? = null,
    val relativePath: String? = null,
    val companionUris: List<String> = emptyList(),
    val coreId: String? = null,
)

enum class EmulatorLaunchSupport { DIRECT, NEEDS_CORE, UNSUPPORTED }

data class RetroArchCore(
    val id: String,
    val displayName: String,
    val platforms: Set<String>,
    val supportedExtensions: Set<String>,
)

data class InstalledEmulator(
    val id: String,
    val packageName: String,
    val displayName: String,
    val versionName: String?,
    val platforms: Set<String>,
    val supportedExtensions: Set<String>,
    val launchSupport: EmulatorLaunchSupport,
    val reason: String? = null,
    /** Supported choices, not an assertion that these private emulator cores are installed. */
    val cores: List<RetroArchCore> = emptyList(),
)

data class EmulatorResolution(
    val candidates: List<InstalledEmulator>,
    val detectedUnsupported: List<InstalledEmulator>,
    val needsExtraction: Boolean = false,
) {
    /** Persisted choice wins only while it remains eligible. More than one always needs a chooser. */
    fun automaticChoice(preferredId: String?): InstalledEmulator? =
        candidates.firstOrNull { it.id == preferredId } ?: candidates.singleOrNull()
}

sealed interface RomDispatchResult {
    data object Started : RomDispatchResult
    data class Unavailable(val reason: String) : RomDispatchResult
    data class Unreadable(val reason: String) : RomDispatchResult
    data class Unsupported(val reason: String) : RomDispatchResult
    data class Rejected(val reason: String) : RomDispatchResult
    data class Failed(val reason: String) : RomDispatchResult
}

internal enum class EmulatorContract { VIEW, MELONDS, BOOT_PATH, RETROARCH, GAMENATIVE, GAMEHUB_STEAM, DETECTION_ONLY }

internal data class EmulatorProfile(
    val id: String,
    val packageName: String,
    val displayName: String,
    val platforms: Set<String>,
    val extensions: Set<String>,
    val activityName: String? = null,
    val contract: EmulatorContract = EmulatorContract.VIEW,
    val unavailableReason: String? = null,
)
