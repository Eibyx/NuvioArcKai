package com.nuvio.tv.kai.arm

import android.util.Log
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** IDs of one anime entry as ARM (arm.haglund.dev, no key) maps them. */
data class ArmIds(val mal: String?, val anilist: String?, val kitsu: String?, val imdb: String?)

/** One row of ARM's /imdb table: an anime entry and the TVDB season it belongs to. */
data class ArmImdbRow(val mal: String?, val anilist: String?, val tvdbSeason: Int?)

/**
 * Key-less ID mapping used when Simkl is not configured. Only direct mappings are trusted: an anime ID maps to
 * the same entry; an IMDb episode maps only when ARM lists exactly one entry for that TVDB season.
 */
interface ArmIdLookup {
    suspend fun ids(source: String, id: String): ArmIds?
    suspend fun imdbRows(imdbId: String): List<ArmImdbRow>

    object None : ArmIdLookup {
        override suspend fun ids(source: String, id: String): ArmIds? = null
        override suspend fun imdbRows(imdbId: String): List<ArmImdbRow> = emptyList()
    }
}

/** The anime entry an IMDb season episode belongs to, or null when ARM's table does not settle it. */
fun armEntryForImdbEpisode(rows: List<ArmImdbRow>, season: Int, episode: Int): Pair<ArmImdbRow, Int>? {
    if (season <= 0 || episode <= 0) return null
    val inSeason = rows.filter { it.tvdbSeason == season }
    // Two cours or parts in one TVDB season: which entry owns the episode is not known without an episode table.
    if (inSeason.size != 1) return null
    return inSeason.single() to episode
}

@Singleton
class ArmIdClient @Inject constructor() : ArmIdLookup {
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .build()
    private val idsCache = BoundedCache<String, ArmIds?>(128)
    private val imdbCache = BoundedCache<String, List<ArmImdbRow>>(64)

    override suspend fun ids(source: String, id: String): ArmIds? {
        val armSource = when (source.lowercase()) {
            "mal", "myanimelist" -> "myanimelist"
            "kitsu" -> "kitsu"
            "anilist" -> "anilist"
            "anidb" -> "anidb"
            else -> return null
        }
        if (id.isBlank() || !id.all(Char::isDigit)) return null
        val key = "$armSource:$id"
        idsCache.get(key)?.let { return it.value }
        val body = get("$BASE/ids?source=$armSource&id=$id") ?: return null
        val ids = runCatching { JSONObject(body).toIds() }.getOrNull()
        idsCache.put(key, ids)
        return ids
    }

    override suspend fun imdbRows(imdbId: String): List<ArmImdbRow> {
        if (!imdbId.matches(Regex("tt[0-9]+"))) return emptyList()
        imdbCache.get(imdbId)?.let { return it.value }
        val body = get("$BASE/imdb?id=$imdbId") ?: return emptyList()
        val rows = runCatching {
            val array = if (body.trimStart().startsWith("[")) JSONArray(body) else JSONArray().put(JSONObject(body))
            (0 until array.length()).mapNotNull { index ->
                val row = array.optJSONObject(index) ?: return@mapNotNull null
                ArmImdbRow(
                    mal = row.idString("myanimelist"),
                    anilist = row.idString("anilist"),
                    tvdbSeason = if (row.has("thetvdb-season") && !row.isNull("thetvdb-season")) row.optInt("thetvdb-season") else null
                )
            }
        }.getOrDefault(emptyList())
        imdbCache.put(imdbId, rows)
        return rows
    }

    private suspend fun get(url: String): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TIMEOUT_MS) {
            try {
                client.newCall(Request.Builder().url(url).header("Accept", "application/json").build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body ?: return@use null
                    if (body.contentLength() > MAX_BODY_BYTES) return@use null
                    body.string().takeIf { it.length <= MAX_BODY_BYTES }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.d(TAG, "ARM lookup failed: ${error.javaClass.simpleName}")
                null
            }
        }
    }

    private fun JSONObject.toIds() = ArmIds(
        mal = idString("myanimelist"),
        anilist = idString("anilist"),
        kitsu = idString("kitsu"),
        imdb = optString("imdb").takeIf { it.startsWith("tt") }
    ).takeIf { it.mal != null || it.anilist != null || it.kitsu != null || it.imdb != null }

    private fun JSONObject.idString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optLong(name, 0L).takeIf { it > 0L }?.toString()
    }

    private class BoundedCache<K, V>(private val max: Int) {
        class Entry<V>(val value: V)
        private val map = object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?) = size > max
        }
        @Synchronized fun get(key: K): Entry<V>? = map[key]
        @Synchronized fun put(key: K, value: V) { map[key] = Entry(value) }
    }

    private companion object {
        const val TAG = "ArcSkipDiag"
        const val BASE = "https://arm.haglund.dev/api/v2"
        const val TIMEOUT_MS = 3_000L
        const val MAX_BODY_BYTES = 256 * 1024
    }
}
