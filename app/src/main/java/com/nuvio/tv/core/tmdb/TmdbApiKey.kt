package com.nuvio.tv.core.tmdb

import com.nuvio.tv.BuildConfig

/**
 * NuvioArcKai: the TMDB key every TMDB call uses. The key the user saved in their Nuvio account (provider "tmdb",
 * field "api_key", uploaded by NuvioMobileKai) wins for the active profile; otherwise the build's
 * BuildConfig.TMDB_API_KEY. Never logged; [toString] is redacted.
 */
object TmdbApiKey {
    @Volatile
    private var accountKey: String? = null

    /** The key to use now: the active profile's account key, else the build key (may be blank). */
    fun current(): String = accountKey?.takeIf { it.isNotBlank() } ?: BuildConfig.TMDB_API_KEY

    /** True when TMDB can be used at all. */
    val isAvailable: Boolean get() = current().isNotBlank()

    /** Whether the key in use comes from the Nuvio account (for diagnostics, never the value). */
    val fromAccount: Boolean get() = !accountKey.isNullOrBlank()

    internal fun setAccountKey(value: String?) {
        accountKey = value?.trim()?.takeIf { it.isNotEmpty() }
    }

    override fun toString(): String = "TmdbApiKey(fromAccount=$fromAccount, available=$isAvailable)"
}
