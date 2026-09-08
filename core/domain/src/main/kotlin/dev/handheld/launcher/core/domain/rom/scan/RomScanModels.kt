package dev.handheld.launcher.core.domain.rom.scan

/** Provider-neutral metadata. Document IDs are opaque; relative paths are only for classification. */
data class RomDocument(
    val documentId: String,
    val relativePath: String,
    val isDirectory: Boolean = false,
    val sizeBytes: Long? = null,
    val mimeType: String? = null,
)

data class RomScanRequest(
    val documents: List<RomDocument>,
    val rootDisplayName: String = "",
    val assignedPlatformId: String? = null,
    /** Relative directory path (empty string for root) to a user-selected platform ID. */
    val folderPlatformOverrides: Map<String, String> = emptyMap(),
    /** Bounded, complete descriptor content keyed by opaque document ID. Never pass truncated text. */
    val descriptorText: Map<String, String> = emptyMap(),
)

enum class RomEntryKind { SINGLE_FILE, ARCHIVE, DISC_DESCRIPTOR, PLAYLIST, FOLDER_PACKAGE }

data class PlannedRomEntry(
    val documentId: String,
    val relativePath: String,
    val title: String,
    val platformId: String,
    val format: String,
    val kind: RomEntryKind,
    /** Ordered, distinct dependencies, excluding the launch document itself. */
    val companionDocumentIds: List<String> = emptyList(),
)

data class UnresolvedRomEntry(
    val documentId: String,
    val relativePath: String,
    val title: String,
    val format: String,
    val candidatePlatformIds: Set<String>,
    val reason: String,
    val companionDocumentIds: List<String> = emptyList(),
    /** Assignment alone cannot repair malformed descriptors or absent companion documents. */
    val requiresRepair: Boolean = false,
)

enum class RomScanIssueCode {
    INVALID_DOCUMENT_PATH, DUPLICATE_DOCUMENT_PATH, EMPTY_FILE, UNKNOWN_PLATFORM,
    PLATFORM_FORMAT_MISMATCH, MISSING_DESCRIPTOR_TEXT, INVALID_DESCRIPTOR, MISSING_COMPANION,
    AMBIGUOUS_COMPANION, CROSS_PLATFORM_PLAYLIST, CYCLIC_PLAYLIST, INCOMPLETE_PACKAGE,
}

data class RomScanIssue(
    val documentId: String?,
    val relativePath: String,
    val code: RomScanIssueCode,
    val message: String,
)

data class RomScanPlan(
    val entries: List<PlannedRomEntry>,
    val unresolved: List<UnresolvedRomEntry>,
    val issues: List<RomScanIssue>,
)
