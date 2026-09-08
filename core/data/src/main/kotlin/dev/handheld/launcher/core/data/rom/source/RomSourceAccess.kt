package dev.handheld.launcher.core.data.rom.source

import android.net.Uri
import android.provider.DocumentsContract
import dev.handheld.launcher.core.domain.rom.RomSource
import dev.handheld.launcher.core.domain.rom.RomSourceAccessKind
import dev.handheld.launcher.core.data.rom.source.shared.SharedDocumentId
import java.io.IOException

interface RomSourceAccess {
    suspend fun isAvailable(source: RomSource): Boolean
    suspend fun enumerate(source: RomSource, onProgress: (Int) -> Unit = {}): RomEnumeration
    fun documentUri(source: RomSource, documentId: String): String =
        DocumentsContract.buildDocumentUriUsingTree(Uri.parse(source.treeUri), documentId).toString()
}

class RoutingRomSourceAccess(
    private val saf: SafRomSourceAccess,
    private val shared: RomSourceAccess,
) : RomSourceAccess {
    private fun access(source: RomSource) = when (source.accessKind) {
        RomSourceAccessKind.SAF -> saf
        RomSourceAccessKind.SHARED_STORAGE -> shared
    }
    override suspend fun isAvailable(source: RomSource) = access(source).isAvailable(source)
    override suspend fun enumerate(source: RomSource, onProgress: (Int) -> Unit) = access(source).enumerate(source, onProgress)
    suspend fun acceptTree(uri: Uri, grantedFlags: Int, existing: List<RomSource>): SelectedRomTree {
        if (uri.authority !in setOf("com.android.externalstorage.documents",SharedDocumentId.AUTHORITY) &&
            existing.any { it.enabled && it.automaticallyDiscovered }) {
            throw IOException("This provider cannot verify overlap with discovered folders. Choose Android's storage provider, or remove the automatic sources before adding this folder.")
        }
        return saf.acceptTree(uri, grantedFlags, existing.filterNot { it.automaticallyDiscovered })
    }
}
