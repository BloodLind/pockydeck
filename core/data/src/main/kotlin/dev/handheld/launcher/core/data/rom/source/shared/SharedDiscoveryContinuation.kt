package dev.handheld.launcher.core.data.rom.source.shared

data class SharedDiscoveryContinuation(val pending: List<String>, val knownVolumeKeys: Set<String>) {
    companion object {
        /** Only newly mounted volumes join a continuing cycle; finished volumes stay finished. */
        fun resume(pending: List<String>, known: Set<String>, mounted: Set<String>): SharedDiscoveryContinuation {
            val remaining = pending.filter { id ->
                runCatching { SharedDocumentId.normalize(id).substringBefore(':') in mounted }.getOrDefault(false)
            }
            if (pending.isEmpty()) return SharedDiscoveryContinuation(mounted.sorted().map { "$it:" },mounted)
            val added = (mounted - known).sorted().map { "$it:" }
            return SharedDiscoveryContinuation((added + remaining).distinct(),mounted)
        }
    }
}
