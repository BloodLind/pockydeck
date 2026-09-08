package dev.handheld.launcher.runtime

/** Match complete package/process identities, including named child processes, for one user. */
internal fun processPresence(lines: Sequence<String>, packages: Set<String>, userId: Int): Set<String> {
    require(userId >= 0)
    val candidates = packages.filterTo(hashSetOf()) { validProcessPackage(it) }
    return lines.take(8192).mapNotNull { line ->
        val fields = line.trim().split(Regex("\\s+"), limit = 2)
        if (fields.size != 2) return@mapNotNull null
        val uid = fields[0].toIntOrNull() ?: return@mapNotNull null
        if (uid / 100_000 != userId) return@mapNotNull null
        fields[1].substringBefore(':').takeIf { it in candidates }
    }.toSet()
}

internal fun validProcessPackage(value: String): Boolean = value.length in 1..255 &&
    value.matches(Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*"))
