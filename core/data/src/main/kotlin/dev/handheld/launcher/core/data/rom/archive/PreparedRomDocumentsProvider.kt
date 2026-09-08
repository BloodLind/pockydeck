package dev.handheld.launcher.core.data.rom.archive

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException

/** Only explicitly granted completed cache trees are reachable; writes are never supported. */
class PreparedRomDocumentsProvider : DocumentsProvider() {
    override fun onCreate()=true
    override fun queryRoots(projection:Array<out String>?):Cursor = MatrixCursor(projection ?: arrayOf(Root.COLUMN_ROOT_ID))
    override fun queryDocument(documentId:String,projection:Array<out String>?):Cursor =
        cursor(projection).apply { addDocument(documentId,resolve(documentId)) }
    override fun queryChildDocuments(parentDocumentId:String,projection:Array<out String>?,sortOrder:String?):Cursor =
        cursor(projection).apply {
            val directory=resolve(parentDocumentId)
            if(!directory.isDirectory) throw FileNotFoundException("Not a folder")
            directory.listFiles()?.sortedBy { it.name }?.forEach { file ->
                val id="$parentDocumentId/${file.name}"
                addDocument(id,resolve(id))
            } ?: throw FileNotFoundException("Prepared folder is unavailable")
        }
    override fun openDocument(documentId:String,mode:String,signal:CancellationSignal?):ParcelFileDescriptor {
        if(mode!="r") throw FileNotFoundException("Prepared games are read-only")
        signal?.throwIfCanceled()
        val file=resolve(documentId)
        if(!file.isFile) throw FileNotFoundException("Not a game file")
        return ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun isChildDocument(parentDocumentId:String,documentId:String):Boolean =
        runCatching { val parent=resolve(parentDocumentId).canonicalFile.toPath(); val child=resolve(documentId).canonicalFile.toPath(); child!=parent && child.startsWith(parent) }.getOrDefault(false)
    override fun getDocumentType(documentId:String)=mime(resolve(documentId))

    private fun resolve(id:String):File {
        if(!id.startsWith("cache:")) throw FileNotFoundException("Unknown cache tree")
        val key=id.substringAfter("cache:").substringBefore('/')
        if(!key.matches(Regex("[a-f0-9]{64}"))) throw FileNotFoundException("Unknown cache tree")
        val relative=id.substringAfter('/',"")
        if(relative.contains('\\') || relative.split('/').any { it==".." || it=="." } || relative.startsWith('/')) throw FileNotFoundException("Invalid document")
        val directory=File(requireNotNull(context).noBackupFilesDir,"rom-prepared/$key")
        if(!File(directory,".complete").isFile) throw FileNotFoundException("This game copy is no longer cached")
        val root=File(directory,"content").canonicalFile
        val target=if(relative.isEmpty()) root else File(root,relative).canonicalFile
        if(!target.toPath().startsWith(root.toPath()) || !target.exists()) throw FileNotFoundException("Invalid document")
        return target
    }
    private fun cursor(projection:Array<out String>?)=MatrixCursor(projection ?: COLUMNS)
    private fun MatrixCursor.addDocument(id:String,file:File) {
        val values=mapOf<String,Any?>(Document.COLUMN_DOCUMENT_ID to id,Document.COLUMN_DISPLAY_NAME to file.name,
            Document.COLUMN_MIME_TYPE to mime(file),Document.COLUMN_SIZE to if(file.isFile) file.length() else null,
            Document.COLUMN_LAST_MODIFIED to file.lastModified(),Document.COLUMN_FLAGS to 0)
        addRow(columnNames.map { values[it] }.toTypedArray())
    }
    private fun mime(file:File)=if(file.isDirectory) Document.MIME_TYPE_DIR else MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
    companion object { private val COLUMNS=arrayOf(Document.COLUMN_DOCUMENT_ID,Document.COLUMN_DISPLAY_NAME,Document.COLUMN_MIME_TYPE,Document.COLUMN_SIZE,Document.COLUMN_LAST_MODIFIED,Document.COLUMN_FLAGS) }
}
