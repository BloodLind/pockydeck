package dev.handheld.launcher.ui.artwork

/** Byte-bounded LRU ownership only: eviction never recycles an image held by a painter. */
internal class ArtworkMemoryCache<K, V>(val maximumBytes: Int, private val sizeOf: (V) -> Int) {
    private val entries = LinkedHashMap<K, V>(16, .75f, true)
    private var bytes = 0
    private var epoch = 0L
    val revision: Long @Synchronized get() = epoch
    val sizeBytes: Int @Synchronized get() = bytes

    @Synchronized fun get(key: K): V? = entries[key]

    /** A trim invalidates work started earlier, so late decodes cannot refill a cleared cache. */
    @Synchronized fun put(key: K, value: V, expectedRevision: Long): Boolean {
        if (expectedRevision != epoch) return false
        val size = sizeOf(value).coerceAtLeast(1)
        entries.remove(key)?.let { bytes -= sizeOf(it).coerceAtLeast(1) }
        if (size > maximumBytes) return false
        entries[key] = value
        bytes += size
        evictTo(maximumBytes)
        return true
    }

    @Synchronized fun trimTo(targetBytes: Int) {
        epoch++
        evictTo(targetBytes.coerceIn(0, maximumBytes))
    }

    private fun evictTo(targetBytes: Int) {
        val iterator = entries.entries.iterator()
        while (bytes > targetBytes && iterator.hasNext()) {
            bytes -= sizeOf(iterator.next().value).coerceAtLeast(1)
            iterator.remove()
        }
    }
}
