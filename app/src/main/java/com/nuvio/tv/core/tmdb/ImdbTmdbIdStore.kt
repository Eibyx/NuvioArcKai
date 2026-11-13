package com.nuvio.tv.core.tmdb

import java.io.File

/**
 * Kai: IMDb → TMDB ids kept on disk. The mapping never changes, yet it was only held in memory,
 * so every start asked TMDB again for each title on Home and in Continue Watching. Holds ids
 * only ("tt0133093:movie" → 603), never a key or a token. Keeps at most [maxEntries].
 */
internal class ImdbTmdbIdStore(private val file: File, private val maxEntries: Int = MAX_ENTRIES) {

    fun load(): Map<String, Int> {
        if (!file.isFile) return emptyMap()
        return runCatching {
            val out = LinkedHashMap<String, Int>()
            file.forEachLine { line ->
                val tab = line.indexOf('\t')
                if (tab <= 0) return@forEachLine
                val key = line.substring(0, tab)
                val id = line.substring(tab + 1).toIntOrNull() ?: return@forEachLine
                if (isValidKey(key) && id > 0) {
                    out.remove(key)
                    out[key] = id
                }
            }
            out.trimToNewest(maxEntries)
        }.getOrElse { emptyMap() }
    }

    fun save(entries: Map<String, Int>) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.bufferedWriter().use { w ->
                entries.filter { (k, v) -> isValidKey(k) && v > 0 }.trimToNewest(maxEntries).forEach { (k, v) ->
                    w.write(k); w.write("\t"); w.write(v.toString()); w.write("\n")
                }
            }
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    fun clear() {
        file.delete()
    }

    private fun Map<String, Int>.trimToNewest(max: Int): Map<String, Int> =
        if (size <= max) this else entries.toList().takeLast(max).associate { it.key to it.value }

    companion object {
        const val MAX_ENTRIES = 5000
        private val KEY = Regex("^tt\\d{1,12}:(movie|tv|series)$")
        fun isValidKey(key: String): Boolean = KEY.matches(key)
    }
}
