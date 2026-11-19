package com.nuvio.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.kaiServiceNetworkDataStore by preferencesDataStore(
    name = "kai_service_network",
    corruptionHandler = androidx.datastore.core.handlers.ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * Kai: the last network test of each streaming service (Nuvio KE's per-service table), so the
 * 1 / 2 / 3 connections choice stays in Settings after leaving it. Holds speeds, the service's
 * domain, the CDN host name and the network label only: never a link, header or token.
 */
@Serializable
data class KaiServiceTest(
    val serviceKey: String,
    val cdnHost: String? = null,
    val testedAtMs: Long,
    val networkLabel: String,
    val baselineMbps: Double? = null,
    val passes: List<KaiServicePass> = emptyList(),
    val filmMbps: Double? = null
)

@Serializable
data class KaiServicePass(val connections: Int, val chunkMb: Int, val mbps: Double)

object KaiServiceNetworkStore {
    const val MAX_SERVICES = 6
    private val key = stringPreferencesKey("tests")
    private val json = Json { ignoreUnknownKeys = true }

    internal fun decode(raw: String?): List<KaiServiceTest> =
        raw?.let { runCatching { json.decodeFromString<List<KaiServiceTest>>(it) }.getOrNull() }.orEmpty()

    internal fun encode(tests: List<KaiServiceTest>): String = json.encodeToString(tests)

    /** [test] replaces the same service's older one and goes first; the oldest beyond [MAX_SERVICES] go. */
    internal fun merged(current: List<KaiServiceTest>, test: KaiServiceTest, group: (String) -> String): List<KaiServiceTest> =
        (listOf(test) + current.filter { group(it.serviceKey) != group(test.serviceKey) }).take(MAX_SERVICES)

    fun flow(context: Context): Flow<List<KaiServiceTest>> = context.kaiServiceNetworkDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { decode(it[key]) }

    suspend fun save(context: Context, test: KaiServiceTest) {
        context.kaiServiceNetworkDataStore.edit { p ->
            p[key] = encode(merged(decode(p[key]), test, com.nuvio.tv.core.network.KaiServiceNetwork::groupKey))
        }
    }

    suspend fun remove(context: Context, serviceKey: String) {
        context.kaiServiceNetworkDataStore.edit { p ->
            p[key] = encode(decode(p[key]).filter { it.serviceKey != serviceKey })
        }
    }
}
