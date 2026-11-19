package com.nuvio.tv.core.network

import java.util.Locale

/**
 * Kai: which streaming service a test measured, as Nuvio KE shows it ("TorBox · tested ... on
 * Wi-Fi"). The key is the registrable domain of the host that served the bytes (after redirects:
 * the debrid CDN behind an addon link), so every TorBox CDN node counts as one service. Only host
 * names are used; paths and query strings (where tokens live) never reach here.
 */
object KaiServiceNetwork {
    /** Two-letter-label second levels that need three labels ("x.co.uk" is "x.co.uk", not "co.uk"). */
    private val SECOND_LEVELS = setOf("co", "com", "net", "org", "gov", "edu", "ac", "ne", "or", "go")

    private val NAMES = mapOf(
        "torbox.app" to "TorBox",
        "tb-cdn.st" to "TorBox",
        "tb-cdn.io" to "TorBox",
        "real-debrid.com" to "Real-Debrid",
        "rdeb.io" to "Real-Debrid",
        "alldebrid.com" to "AllDebrid",
        "debrid.it" to "AllDebrid",
        "premiumize.me" to "Premiumize",
        "debrid-link.com" to "Debrid-Link",
        "debrid-link.fr" to "Debrid-Link",
        "offcloud.com" to "Offcloud",
        "easydebrid.com" to "EasyDebrid",
        "terabox.com" to "TeraBox",
        "googleusercontent.com" to "Google Drive",
        "googleapis.com" to "Google Drive"
    )

    /** The service key of [host] (no port, no IP literal), or null when there is none to name. */
    fun serviceKeyOf(host: String?): String? {
        val clean = host?.substringBefore(':')?.trim()?.trimEnd('.')?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() } ?: return null
        if (clean.all { it.isDigit() || it == '.' } || clean == "localhost") return null
        val labels = clean.split('.').filter { it.isNotEmpty() }
        if (labels.size < 2) return null
        val take = if (labels.size >= 3 && labels.last().length == 2 && labels[labels.size - 2] in SECOND_LEVELS) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }

    /** What the user reads: the service's own name when known, else its domain. */
    fun displayName(serviceKey: String): String = NAMES[serviceKey] ?: serviceKey

    /** Same service name for two keys (TorBox's site and its CDN are one entry). */
    fun groupKey(serviceKey: String): String = NAMES[serviceKey]?.lowercase(Locale.ROOT) ?: serviceKey
}
