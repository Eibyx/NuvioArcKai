package com.nuvio.tv.core.sync

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.di.redactCredentialQueryParams
import com.nuvio.tv.core.tmdb.TmdbApiKey
import com.nuvio.tv.data.remote.supabase.SupabaseProviderCredential
import com.nuvio.tv.domain.model.DebridSettings
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** NuvioArcKai: the TMDB key NuvioMobileKai keeps in the Nuvio account reaches the TV, read-only and never logged. */
class KaiAccountTmdbKeyTest {
    private val accountKey = "0123456789abcdef0123456789abcdef"

    /** What `sync_pull_provider_credentials` returns for a profile where Mobile saved its TMDB key. */
    private val pullResponse = """
        [
          {"provider":"debrid:torbox","credential_json":{"api_key":"tb-key"},"updated_at":"2026-11-01T10:00:00Z"},
          {"provider":"tmdb","credential_json":{"api_key":"$accountKey"},"updated_at":"2026-11-02T10:00:00Z"},
          {"provider":"mdblist","credential_json":{"api_key":""},"updated_at":"2026-11-02T10:00:00Z"}
        ]
    """.trimIndent()

    private val rows = Json { ignoreUnknownKeys = true }.decodeFromString<List<SupabaseProviderCredential>>(pullResponse)

    private class MemoryPersistence : AccountTmdbKeyPersistence {
        val values = mutableMapOf<Int, String>()
        override fun read(profileId: Int) = values[profileId]
        override fun write(profileId: Int, value: String?) { if (value == null) values.remove(profileId) else values[profileId] = value }
        override fun clear() = values.clear()
    }

    @After fun reset() = TmdbApiKey.setAccountKey(null)

    @Test fun `the TV client reads the tmdb row the server returns`() {
        assertEquals(3, rows.size)
        assertEquals(accountKey, accountTmdbApiKey(rows))
        assertNull(accountTmdbApiKey(rows.filterNot { it.provider == "tmdb" }))
        val blank = Json.decodeFromString<List<SupabaseProviderCredential>>("""[{"provider":"TMDB","credential_json":{"api_key":"  "}}]""")
        assertNull(accountTmdbApiKey(blank))
        val notString = Json.decodeFromString<List<SupabaseProviderCredential>>("""[{"provider":"tmdb","credential_json":{"api_key":123}}]""")
        assertNull(accountTmdbApiKey(notString))
    }

    @Test fun `the TV never pushes, seeds or overwrites the account's tmdb key`() {
        val local = localProviderCredentials(1, DebridSettings(), mdbListApiKey = "", animeSkipClientId = "")
        assertTrue(local.values.none { it.provider.equals("tmdb", ignoreCase = true) })
        // Merging the pulled rows does not add tmdb to what the TV would push back either.
        assertTrue(local.mergeRemote(rows).values.none { it.provider.equals("tmdb", ignoreCase = true) })
    }

    @Test fun `the account key wins for its profile and falls back to the build key`() {
        var active = 1
        val persistence = MemoryPersistence()
        val store = AccountTmdbKeyStore(persistence, activeProfileId = { active })
        assertEquals(BuildConfig.TMDB_API_KEY, TmdbApiKey.current())

        store.applyFromAccount(1, accountTmdbApiKey(rows))
        assertEquals(accountKey, TmdbApiKey.current())
        assertTrue(TmdbApiKey.fromAccount)
        assertEquals(accountKey, persistence.values[1])

        active = 2
        store.selectProfile(2)
        assertEquals(BuildConfig.TMDB_API_KEY, TmdbApiKey.current())
        active = 1
        store.selectProfile(1)
        assertEquals(accountKey, TmdbApiKey.current())

        store.applyFromAccount(1, null)  // removed from the account
        assertEquals(BuildConfig.TMDB_API_KEY, TmdbApiKey.current())
        assertNull(persistence.values[1])
    }

    @Test fun `deleting the profile or signing out clears the key`() {
        val persistence = MemoryPersistence()
        val store = AccountTmdbKeyStore(persistence, activeProfileId = { 1 })
        store.applyFromAccount(1, accountKey)
        store.removeProfile(1)
        assertFalse(TmdbApiKey.fromAccount)
        store.applyFromAccount(1, accountKey)
        store.clearAllProfiles()
        assertTrue(persistence.values.isEmpty())
        assertFalse(TmdbApiKey.fromAccount)
    }

    @Test fun `the key never shows in logs`() {
        val store = AccountTmdbKeyStore(MemoryPersistence(), activeProfileId = { 1 })
        store.applyFromAccount(1, accountKey)
        assertFalse(TmdbApiKey.toString().contains(accountKey))
        assertFalse(store.toString().contains(accountKey))
        val line = "--> GET https://api.themoviedb.org/3/movie/550?api_key=$accountKey&language=es-ES"
        assertFalse(redactCredentialQueryParams(line).contains(accountKey))
    }
}
