package com.nuvio.tv.data.anilist

import com.nuvio.tv.domain.model.WatchedItem

/*
 * Pure helpers adapted from NuvioMobileKai (branch `kai`):
 * AniListLinkUi.kt (aniListLinkStatus), AniListListLinker.kt (list entries to link, ARM seasons, anime-id links)
 * and AniListWatchedSyncAdapter.kt (withoutImported). Mobile's UI and background worker are not ported.
 */

/** Where linking one title stands, for the AniList dialog and the detail page. */
internal sealed interface AniListLinkStatus {
    /** Linked (automatically or by hand): [titles] are the AniList entries, one per linked season. */
    data class Linked(val titles: List<String>) : AniListLinkStatus
    /** Not linked yet: it links itself when the user starts watching. */
    data object Pending : AniListLinkStatus
    /** A season could not be matched: nothing is written for it. */
    data object NotFound : AniListLinkStatus
}

/** The status of [keys] (one per season, or the movie): a missing match wins, then any link. */
internal fun aniListLinkStatus(keys: List<String>, state: AniListQueueState): AniListLinkStatus {
    if (keys.any { it in state.unmatched }) return AniListLinkStatus.NotFound
    val titles = keys.mapNotNull { key -> state.links.firstOrNull { it.key == key }?.title }.distinct()
    return if (titles.isEmpty()) AniListLinkStatus.Pending else AniListLinkStatus.Linked(titles)
}

/** The list entries whose episodes count as seen: watching, completed, rewatching and paused. */
internal val ANILIST_LIST_LINK_STATUSES = setOf(
    AniListListStatus.CURRENT,
    AniListListStatus.COMPLETED,
    AniListListStatus.REPEATING,
    AniListListStatus.PAUSED,
)

/** A week: an entry looked up without a match is not looked up again before. */
internal const val ANILIST_LIST_RECHECK_MS = 7L * 24 * 60 * 60 * 1000

/** Entries of [snapshot] linking is for: seen (see [ANILIST_LIST_LINK_STATUSES]) and not movies. */
internal fun aniListListEntries(snapshot: AniListSyncSnapshot): List<AniListEntry> =
    snapshot.entries.filter { entry -> entry.status in ANILIST_LIST_LINK_STATUSES && entry.media?.isMovie != true }

/** Entries still to link: no link has them, not looked up in the last [recheckMs], and their ids are resolved. */
internal fun aniListEntriesToLink(
    snapshot: AniListSyncSnapshot,
    linkedMediaIds: Set<Long>,
    checked: Map<Long, Long>,
    nowMs: Long,
    recheckMs: Long = ANILIST_LIST_RECHECK_MS,
): List<AniListEntry> =
    aniListListEntries(snapshot).filter { entry ->
        entry.mediaId !in linkedMediaIds &&
            checked[entry.mediaId]?.let { nowMs - it in 0 until recheckMs } != true &&
            (snapshot.mappings[entry.mediaId]?.resolvedAtEpochMs ?: 0L) > 0L
    }

/** The TVDB seasons ARM puts [mediaIds] in; rows without a season (one entry for the whole show) are left out. */
internal fun armSeasonsOf(rows: List<ArmImdbEntry>, mediaIds: Set<Long>): List<Int> =
    rows.filter { it.anilist in mediaIds }.mapNotNull { it.tvdbSeason?.takeIf { season -> season > 0 } }.distinct().sorted()

/** Catalogs with anime ids (Kitsu, MAL) have one AniList entry per title, season 1: those links need no lookup. */
internal fun animeIdLinks(entry: AniListEntry, mapping: AniListMapping?): List<AniListLink> {
    val title = entry.media?.displayTitle() ?: "AniList ${entry.mediaId}"
    val part = AniListLinkPart(entry.mediaId, title, episodes = entry.media?.episodes)
    return listOfNotNull(mapping?.mal?.let { "mal:$it" }, mapping?.kitsu?.let { "kitsu:$it" })
        .map { id -> AniListLink(aniListSendKey(id, "series", 1), listOf(part), manual = false) }
}

/** Key of one watched episode (or a movie) as the AniList import marks it. */
internal fun aniListWatchedKey(contentType: String, contentId: String, season: Int?, episode: Int?): String =
    listOf(contentType.lowercase(), contentId.trim().lowercase(), season?.toString().orEmpty(), episode?.toString().orEmpty())
        .joinToString(":")

/** Keys of what an import marked watched. */
internal fun aniListImportedKeys(contentId: String, parts: List<AniListImportedPart>): Set<String> =
    parts.flatMap { part -> part.episodes.map { aniListWatchedKey("series", contentId, it.season, it.episode) } }.toSet()

/** What came from AniList is never sent back: [items] minus the episodes the import marked. */
internal fun withoutImported(items: Collection<WatchedItem>, imported: Set<String>): List<WatchedItem> =
    if (imported.isEmpty()) items.toList()
    else items.filterNot { item -> aniListWatchedKey(item.contentType, item.contentId, item.season, item.episode) in imported }
