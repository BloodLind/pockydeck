package dev.handheld.launcher.core.data.rom.source

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import dev.handheld.launcher.core.domain.rom.RomSource
import dev.handheld.launcher.core.domain.rom.scan.*
import kotlinx.coroutines.*
import java.io.IOException

data class SelectedRomTree(val uri:String,val documentId:String,val name:String)
data class RomEnumeration(val documents:List<RomDocument>,val descriptorText:Map<String,String>)

/** SAF remains the authority for access, identity and enumeration; no URI-to-file guessing. */
class SafRomSourceAccess(context:Context) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun isAvailable(source:RomSource):Boolean = withContext(Dispatchers.IO) {
        val tree=Uri.parse(source.treeUri)
        if(resolver.persistedUriPermissions.none { it.uri==tree && it.isReadPermission }) return@withContext false
        try {
            query(DocumentsContract.buildDocumentUriUsingTree(tree,source.rootDocumentId),arrayOf(Document.COLUMN_MIME_TYPE)) {
                it.moveToFirst() && it.getString(0)==Document.MIME_TYPE_DIR
            }
        } catch(cancelled:CancellationException) { throw cancelled }
        catch(_:Exception) { false }
    }

    suspend fun acceptTree(uri:Uri, grantedFlags:Int, existing:List<RomSource>):SelectedRomTree = withContext(Dispatchers.IO) {
        require(DocumentsContract.isTreeUri(uri)) { "Choose a ROM folder using Android's folder picker." }
        val id = DocumentsContract.getTreeDocumentId(uri)
        val tree = DocumentsContract.buildTreeDocumentUri(requireNotNull(uri.authority),id)
        val held = resolver.persistedUriPermissions.any { it.uri==tree && it.isReadPermission }
        require(held || grantedFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0) { "Read access to the folder was not granted." }
        if(!held) resolver.takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            val document = DocumentsContract.buildDocumentUriUsingTree(tree,id)
            val root = query(document,arrayOf(Document.COLUMN_DISPLAY_NAME,Document.COLUMN_MIME_TYPE)) { cursor ->
                require(cursor.moveToFirst() && cursor.getString(1)==Document.MIME_TYPE_DIR) { "This folder is unavailable." }
                cursor.getString(0) ?: "ROM folder"
            }
            existing.filter { it.enabled && it.treeUri != tree.toString() && Uri.parse(it.treeUri).authority == tree.authority }.forEach { old ->
                val oldTree = Uri.parse(old.treeUri)
                val oldDoc = DocumentsContract.buildDocumentUriUsingTree(oldTree,old.rootDocumentId)
                val overlap = if(tree.authority == "com.android.externalstorage.documents") {
                    id.startsWith(old.rootDocumentId.trimEnd('/')+"/") || old.rootDocumentId.startsWith(id.trimEnd('/')+"/") ||
                        id==old.rootDocumentId || id.endsWith(":") && old.rootDocumentId.startsWith(id) || old.rootDocumentId.endsWith(":") && id.startsWith(old.rootDocumentId)
                } else runCatching {
                    DocumentsContract.isChildDocument(resolver,oldDoc,document) || DocumentsContract.isChildDocument(resolver,document,oldDoc)
                }.getOrElse { throw IOException("This provider cannot verify overlapping folders. Use one common ROM root for this provider.") }
                require(!overlap) { "This folder overlaps ${old.name}. Use the existing root to avoid duplicate games." }
            }
            SelectedRomTree(tree.toString(),id,root)
        } catch(error:Exception) {
            if(!held) runCatching { resolver.releasePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            throw error
        }
    }

    suspend fun enumerate(source:RomSource, onProgress:(Int)->Unit = {}):RomEnumeration = withContext(Dispatchers.IO) {
        val tree = Uri.parse(source.treeUri)
        if(!isAvailable(source)) throw RomSourceUnavailableException("This ROM folder is unavailable. Reconnect storage or select the folder again.")
        require(resolver.persistedUriPermissions.any { it.uri==tree && it.isReadPermission }) { "Folder permission was revoked. Select the folder again." }
        val documents = mutableListOf<RomDocument>()
        val descriptors = linkedMapOf<String,String>()
        var descriptorBytes = 0
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<Triple<String,String,Int>>()
        queue.add(Triple(source.rootDocumentId,"",0))
        while(queue.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val (parent,path,depth) = queue.removeFirst()
            if(!visited.add(parent)) throw IOException("The provider returned a folder cycle; existing games were preserved.")
            if(depth>48) throw IOException("This folder tree is too deep to scan safely.")
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree,parent)
            val found = query(children,arrayOf(Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_MIME_TYPE,Document.COLUMN_SIZE,Document.COLUMN_FLAGS)) { cursor ->
                if(cursor.extras.getBoolean(DocumentsContract.EXTRA_LOADING,false)) throw IOException("The provider is still loading this folder. Try again shortly.")
                buildList {
                    while(cursor.moveToNext()) {
                        val name=cursor.getString(1) ?: throw IOException("A document name is missing.")
                        if(name.contains('/') || name.contains('\\') || name=="." || name=="..") throw IOException("The provider returned an invalid document name.")
                        val docId=cursor.getString(0) ?: throw IOException("A document identity is missing.")
                        val mime=cursor.getString(2)
                        val flags=cursor.getInt(4)
                        if(flags and Document.FLAG_VIRTUAL_DOCUMENT == 0) add(RomDocument(docId,
                            if(path.isEmpty()) name else "$path/$name",mime==Document.MIME_TYPE_DIR,
                            if(cursor.isNull(3)) null else cursor.getLong(3),mime))
                        if(size+documents.size>100_000) throw IOException("This source exceeds 100,000 documents. Choose smaller console folders.")
                    }
                }
            }
            for(document in found) {
                currentCoroutineContext().ensureActive()
                documents.add(document)
                if(document.isDirectory) queue.add(Triple(document.documentId,document.relativePath,depth+1))
                else if(RomScanPlanner.needsDescriptorText(document.relativePath)) {
                    val uri=DocumentsContract.buildDocumentUriUsingTree(tree,document.documentId)
                    val bytes=resolver.openInputStream(uri)?.use { it.readNBytes(RomScanPlanner.MAX_DESCRIPTOR_BYTES+1) }
                        ?: throw IOException("A disc descriptor could not be read.")
                    if(bytes.size<=RomScanPlanner.MAX_DESCRIPTOR_BYTES) {
                        descriptorBytes+=bytes.size
                        if(descriptorBytes>32*1024*1024) throw IOException("This source contains too many disc descriptors. Choose smaller console folders.")
                        descriptors[document.documentId]=bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                    }
                }
            }
            onProgress(documents.size)
        }
        RomEnumeration(documents,descriptors)
    }

    private suspend fun <T> query(uri:Uri,projection:Array<String>,read:(android.database.Cursor)->T):T = coroutineScope {
        val signal=CancellationSignal()
        val timeout=launch(Dispatchers.Default) { delay(30_000); signal.cancel() }
        try {
            resolver.query(uri,projection,null,null,null,signal)?.use(read) ?: throw IOException("The storage provider returned no result.")
        } finally { timeout.cancel(); signal.cancel() }
    }
}

class RomSourceUnavailableException(message:String):IOException(message)
