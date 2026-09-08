package dev.handheld.launcher.core.data.rom.archive

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import dev.handheld.launcher.core.domain.rom.scan.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

data class PreparedArchive(val key:String,val treeUri:String,val documents:List<RomDocument>,val descriptorText:Map<String,String>) {
    fun uri(documentId:String):String = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri),documentId).toString()
}

/** App-owned, content-addressed copies. Published copies are immutable and exposed read-only. */
class PreparedRomCache(context:Context) {
    private val context=context.applicationContext
    private val root=File(context.noBackupFilesDir,"rom-prepared")
    private val lock=Mutex()

    suspend fun prepare(uri:String,format:String,limitBytes:Long,onProgress:(String)->Unit,sourceName:String="game.$format"):PreparedArchive = lock.withLock {
        withContext(Dispatchers.IO) {
            root.mkdirs()
            cleanInterrupted()
            val incomingDir=File(root,".incoming-${UUID.randomUUID()}").apply { mkdirs() }
            val safeName=sourceName.takeIf { it.isNotBlank() && '/' !in it && '\\' !in it && it!="." && it!=".." } ?: "game.$format"
            val incoming=File(incomingDir,safeName)
            val job=currentCoroutineContext().job
            try {
                onProgress("Reading compressed game…")
                val digest=MessageDigest.getInstance("SHA-256")
                // Find a content-addressed hit without allocating a second copy of the archive.
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                    val buffer=ByteArray(64*1024)
                    var read=0L
                    while(true) {
                        job.ensureActive()
                        val count=input.read(buffer); if(count<0) break
                        read+=count
                        if(read>limitBytes) throw IOException("This archive exceeds the cache limit. Increase it in Settings.")
                        digest.update(buffer,0,count)
                    }
                } ?: throw IOException("The compressed game could not be read. Check the folder permission.")
                val contentHash=digest.digest()
                val key=MessageDigest.getInstance("SHA-256").apply {
                    update(contentHash)
                    update(format.lowercase().toByteArray(Charsets.UTF_8))
                    if(format in setOf("gz","gzip","xz","bz2","bzip2")) update(safeName.toByteArray(Charsets.UTF_8))
                }.digest().joinToString("") { "%02x".format(it) }
                val completed=File(root,key)
                if(File(completed,".complete").isFile) { completed.setLastModified(System.currentTimeMillis()); return@withContext readPrepared(key) }
                val occupied=trimUnused()
                var copied=0L
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { input -> incoming.outputStream().buffered().use { output ->
                    val buffer=ByteArray(64*1024)
                    while(true) {
                        job.ensureActive()
                        val count=input.read(buffer)
                        if(count<0) break
                        copied+=count
                        if(copied>limitBytes-occupied || root.usableSpace<RESERVE_BYTES) throw IOException("Not enough game-cache space. Clear unused copies or increase the cache limit in Settings.")
                        digest.update(buffer,0,count); output.write(buffer,0,count)
                    }
                } } ?: throw IOException("The compressed game could not be read. Check the folder permission.")
                if(!digest.digest().contentEquals(contentHash)) throw IOException("The archive changed while being read. Try opening it again.")
                val partial=File(root,".partial-$key")
                deleteOwned(partial)
                val content=File(partial,"content").apply { mkdirs() }
                try {
                    val remaining=limitBytes-occupied-copied
                    if(remaining<=0) throw IOException("Increase the game-cache limit to extract this game.")
                    onProgress("Extracting game…")
                    var lastReport=0L
                    ArchiveExtractor().extract(incoming,format,content,ExtractionLimits(remaining),
                        onProgress={ bytes -> if(bytes-lastReport>=16*1024*1024) { lastReport=bytes; onProgress("Extracting game · ${bytes/(1024*1024)} MiB") } },
                        isCancelled={ !job.isActive })
                    job.ensureActive()
                    File(partial,".complete").writeText("1")
                    if(completed.exists()) deleteOwned(completed)
                    if(!partial.renameTo(completed)) throw IOException("Could not finish preparing this game.")
                    readPrepared(key)
                } catch(error:Throwable) { deleteOwned(partial); throw error }
            } finally { deleteOwned(incomingDir) }
        }
    }

    /** Protect dispatched copies across process restarts; emulator liveness is never inferred. */
    suspend fun reserveForLaunch(key:String):Boolean = withContext(Dispatchers.IO) {
        require(key.matches(Regex("[a-f0-9]{64}")))
        val dir=File(root,key)
        if(!File(dir,".complete").isFile) throw IOException("This prepared copy is unavailable. Open the game again.")
        val marker=File(dir,".reserved")
        if(marker.exists()) false else { marker.writeText("1"); true }
    }

    suspend fun releaseFailedReservation(key:String,newlyReserved:Boolean) = withContext(Dispatchers.IO) {
        require(key.matches(Regex("[a-f0-9]{64}")))
        if(newlyReserved) File(File(root,key),".reserved").delete()
        Unit
    }

    suspend fun usageBytes():Long = withContext(Dispatchers.IO) { size(root) }
    suspend fun clear(includeReserved:Boolean=false):Long = lock.withLock { withContext(Dispatchers.IO) {
        val before=size(root)
        root.listFiles()?.forEach { file ->
            if(includeReserved || !File(file,".reserved").exists()) deleteOwned(file)
        }
        before-size(root)
    } }

    private fun readPrepared(key:String):PreparedArchive {
        val content=File(File(root,key),"content")
        val treeId="cache:$key"
        val docs=mutableListOf<RomDocument>()
        val text=linkedMapOf<String,String>()
        var descriptorBytes=0
        content.walkTopDown().drop(1).forEach { file ->
            if(docs.size>=20_000) throw IOException("The prepared archive contains too many documents.")
            val relative=file.relativeTo(content).invariantSeparatorsPath
            val id="$treeId/$relative"
            docs.add(RomDocument(id,relative,file.isDirectory,if(file.isFile) file.length() else null))
            if(file.isFile && RomScanPlanner.needsDescriptorText(relative) && file.length()<=RomScanPlanner.MAX_DESCRIPTOR_BYTES) {
                descriptorBytes+=file.length().toInt()
                if(descriptorBytes>32*1024*1024) throw IOException("The archive contains too many descriptors.")
                text[id]=file.readText().removePrefix("\uFEFF")
            }
        }
        return PreparedArchive(key,DocumentsContract.buildTreeDocumentUri("${context.packageName}.romcache",treeId).toString(),docs,text)
    }

    private fun trimUnused():Long {
        var total=size(root)
        // A cache hit was already handled. Reclaim unreserved copies before preparing a new game.
        root.listFiles()?.filter { it.name.matches(Regex("[a-f0-9]{64}")) && !File(it,".reserved").exists() }
            ?.sortedBy { it.lastModified() }?.forEach { file -> val bytes=size(file); deleteOwned(file); total-=bytes }
        return total
    }
    private fun cleanInterrupted() { root.listFiles()?.filter { it.name.startsWith(".partial-") || it.name.startsWith(".incoming-") }?.forEach(::deleteOwned) }
    private fun deleteOwned(file:File) {
        val base=root.canonicalFile.toPath()
        require(file.canonicalFile.toPath().startsWith(base) && file.canonicalFile.toPath()!=base)
        if(file.exists() && !file.deleteRecursively()) throw IOException("Could not clear an unused game copy.")
    }
    private fun size(file:File):Long = if(!file.exists()) 0 else if(file.isFile) file.length() else file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    companion object { const val RESERVE_BYTES=512L*1024*1024 }
}
