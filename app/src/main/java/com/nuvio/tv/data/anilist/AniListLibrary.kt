package com.nuvio.tv.data.anilist

import com.nuvio.tv.core.tracking.TrackingCapability
import com.nuvio.tv.core.tracking.TrackingLibraryProvider
import com.nuvio.tv.core.tracking.TrackingMembershipRemovalConfirmation
import com.nuvio.tv.core.tracking.TrackingMembershipRemovalImpact
import com.nuvio.tv.core.tracking.TrackingProvider
import com.nuvio.tv.core.tracking.TrackingProviderDescriptor
import com.nuvio.tv.core.tracking.TrackingProviderId
import com.nuvio.tv.core.tracking.TrackingRefreshIntent
import com.nuvio.tv.domain.model.LibraryEntry
import com.nuvio.tv.domain.model.LibraryEntryInput
import com.nuvio.tv.domain.model.LibraryListTab
import com.nuvio.tv.domain.model.ListMembershipChanges
import com.nuvio.tv.domain.model.ListMembershipSnapshot
import com.nuvio.tv.domain.model.PosterShape
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/*
 * NuvioArcKai: AniList library by list status. Logic adapted from NuvioMobileKai (`kai`)
 * AniListTrackingLibraryProvider.kt / AniListProjection.kt (status definitions, REPEATING shown in Watching,
 * one status per entry, confirmation before losing progress or score), on top of LiteOG's list snapshot.
 */

internal const val ANILIST_STATUS_GROUP = "anilist:status"
internal const val ANILIST_TAB_CURRENT = "anilist:status:current"
internal const val ANILIST_TAB_PLANNING = "anilist:status:planning"
internal const val ANILIST_TAB_COMPLETED = "anilist:status:completed"
internal const val ANILIST_TAB_PAUSED = "anilist:status:paused"
internal const val ANILIST_TAB_DROPPED = "anilist:status:dropped"

/** The five tabs, in order, with the AniList status each one writes. */
internal val aniListStatusTabs: List<Pair<String, String>> = listOf(
    ANILIST_TAB_CURRENT to AniListListStatus.CURRENT,
    ANILIST_TAB_PLANNING to AniListListStatus.PLANNING,
    ANILIST_TAB_COMPLETED to AniListListStatus.COMPLETED,
    ANILIST_TAB_PAUSED to AniListListStatus.PAUSED,
    ANILIST_TAB_DROPPED to AniListListStatus.DROPPED,
)

/** The tab an entry shows in: REPEATING shows in Watching (marked), and is never written from Nuvio. */
internal fun aniListTabFor(status: String?): String? = when (status) {
    AniListListStatus.REPEATING -> ANILIST_TAB_CURRENT
    else -> aniListStatusTabs.firstOrNull { it.second == status }?.first
}

/** A detail id the user's add-ons can resolve: MAL, then Kitsu, then IMDb. Null when there is none. */
internal fun aniListResolvableId(entry: AniListEntry, mapping: AniListMapping?): String? =
    (entry.media?.idMal ?: mapping?.mal)?.let { "mal:$it" }
        ?: mapping?.kitsu?.let { "kitsu:$it" }
        ?: mapping?.imdb?.takeIf { it.startsWith("tt") }

/** Every id an AniList entry is known by, lowercase, to match a detail page's id. */
internal fun aniListAliases(entry: AniListEntry, mapping: AniListMapping?): Set<String> = buildSet {
    add("anilist:${entry.mediaId}")
    (entry.media?.idMal ?: mapping?.mal)?.let { add("mal:$it") }
    mapping?.kitsu?.let { add("kitsu:$it") }
    mapping?.anidb?.let { add("anidb:$it") }
    mapping?.imdb?.takeIf { it.startsWith("tt") }?.let { add(it.lowercase()) }
}

internal const val ANILIST_UNRESOLVED_PREFIX = "anilist:"
internal const val ANILIST_REPEATING_MARK = " ↻"

/** The list as library entries. Entries without a resolvable id keep `anilist:<id>` and are shown, not opened. */
internal fun AniListSyncSnapshot.aniListLibraryEntries(): List<LibraryEntry> =
    entries.mapNotNull { entry ->
        val media = entry.media ?: return@mapNotNull null
        val tab = aniListTabFor(entry.status) ?: return@mapNotNull null
        val mapping = mappings[entry.mediaId]
        val id = aniListResolvableId(entry, mapping) ?: "$ANILIST_UNRESOLVED_PREFIX${entry.mediaId}"
        val poster = media.coverImage?.extraLarge ?: media.coverImage?.large ?: media.coverImage?.medium
        LibraryEntry(
            id = id,
            type = if (media.isMovie) "movie" else "series",
            name = media.displayTitle() + if (entry.status == AniListListStatus.REPEATING) ANILIST_REPEATING_MARK else "",
            poster = poster,
            posterShape = PosterShape.POSTER,
            background = media.bannerImage,
            logo = null,
            description = null,
            releaseInfo = (media.seasonYear ?: media.startDate?.year)?.toString(),
            imdbRating = null,
            genres = media.genres,
            addonBaseUrl = null,
            listKeys = setOf(tab),
            listedAt = maxOf(entry.updatedAt, entry.createdAt) * 1000L,
            imdbId = mapping?.imdb?.takeIf { it.startsWith("tt") },
            mediaCategory = "anime",
            rawPosterUrl = poster,
            trackingProviderId = TrackingProviderId.ANILIST.storageId,
            trackingProviderItemId = "anilist:${entry.mediaId}",
            trackingSourceUrl = media.siteUrl
        )
    }.sortedByDescending(LibraryEntry::listedAt)

/** True for an AniList library entry with no id the add-ons can open: it is listed, but opens no empty detail page. */
fun LibraryEntry.isUnresolvedAniListEntry(): Boolean =
    trackingProviderId == TrackingProviderId.ANILIST.storageId && id.startsWith(ANILIST_UNRESOLVED_PREFIX)

/** The list entries a detail page's [contentId] belongs to. */
internal fun AniListSyncSnapshot.entriesFor(contentId: String): List<AniListEntry> {
    val key = contentId.trim().lowercase()
    return entries.filter { key in aniListAliases(it, mappings[it.mediaId]) }
}

/** Removing these entries loses something AniList keeps: progress or a score. */
internal fun aniListRemovalNeedsConfirmation(removed: List<AniListEntry>): Boolean =
    removed.any { it.progress > 0 || it.score > 0.0 }

@Singleton
class AniListTrackingProvider @Inject constructor(repository: AniListRepository) : TrackingProvider {
    override val descriptor = TrackingProviderDescriptor(
        TrackingProviderId.ANILIST,
        "AniList",
        setOf(TrackingCapability.AUTHENTICATION, TrackingCapability.LIBRARY_READ, TrackingCapability.LIBRARY_WRITE)
    )
    override val isAuthenticated: StateFlow<Boolean> = repository.state.map { it.connected }.distinctUntilChanged()
        .stateIn(CoroutineScope(SupervisorJob() + Dispatchers.IO), SharingStarted.Eagerly, repository.state.value.connected)
}

@Singleton
class AniListTrackingLibraryProvider @Inject constructor(
    private val repository: AniListRepository,
    tracking: AniListTrackingProvider
) : TrackingLibraryProvider {
    override val providerId = TrackingProviderId.ANILIST
    override val isAuthenticated: Flow<Boolean> = tracking.isAuthenticated
    override val isRefreshing: Flow<Boolean> = repository.state.map { it.syncing }.distinctUntilChanged()
    override val items: Flow<List<LibraryEntry>> =
        repository.listSnapshot.map { it?.aniListLibraryEntries().orEmpty() }.distinctUntilChanged()
    override val tabs: Flow<List<LibraryListTab>> = tracking.isAuthenticated.map { connected ->
        if (!connected) emptyList() else aniListStatusTabs.map { (key, status) ->
            LibraryListTab(
                key = key,
                title = key.substringAfterLast(':'),
                type = if (status == AniListListStatus.PLANNING) LibraryListTab.Type.WATCHLIST else LibraryListTab.Type.STATUS,
                trackingProviderId = TrackingProviderId.ANILIST.storageId,
                selectionGroup = ANILIST_STATUS_GROUP,
                supportedContentTypes = setOf("movie", "series"),
                isMembershipDestination = true
            )
        }
    }.distinctUntilChanged()

    override fun recognizesListKey(key: String): Boolean = aniListStatusTabs.any { it.first == key }

    override fun countsAsInLibrary(listKey: String): Boolean = listKey != ANILIST_TAB_COMPLETED

    override fun observeMembership(itemId: String, itemType: String): Flow<Set<String>> =
        repository.listSnapshot.map { snapshot ->
            snapshot?.entriesFor(itemId).orEmpty().mapNotNullTo(linkedSetOf()) { aniListTabFor(it.status) }
        }.distinctUntilChanged()

    override fun toggledDefaultMembership(currentMembership: Map<String, Boolean>): Map<String, Boolean> =
        currentMembership.mapValues { false }.toMutableMap().apply {
            if (currentMembership.values.none { it }) this[ANILIST_TAB_PLANNING] = true
        }

    override suspend fun getMembershipSnapshot(item: LibraryEntryInput): ListMembershipSnapshot {
        val keys = repository.listSnapshot.value?.entriesFor(item.itemId).orEmpty().mapNotNull { aniListTabFor(it.status) }.toSet()
        return ListMembershipSnapshot(aniListStatusTabs.associate { (key, _) -> key to (key in keys) })
    }

    override suspend fun membershipRemovalConfirmation(
        item: LibraryEntryInput,
        changes: ListMembershipChanges
    ): TrackingMembershipRemovalConfirmation? {
        val removed = removedEntries(item, changes) ?: return null
        if (!aniListRemovalNeedsConfirmation(removed)) return null
        return TrackingMembershipRemovalConfirmation(
            TrackingProviderId.ANILIST,
            setOf(TrackingMembershipRemovalImpact.WATCHED_HISTORY, TrackingMembershipRemovalImpact.RATING)
        )
    }

    override suspend fun applyMembershipChanges(
        item: LibraryEntryInput,
        changes: ListMembershipChanges,
        destructiveRemovalConfirmed: Boolean
    ) {
        val snapshot = repository.listSnapshot.value
        val current = snapshot?.entriesFor(item.itemId).orEmpty()
        val currentKeys = current.mapNotNull { aniListTabFor(it.status) }.toSet()
        val desiredKeys = aniListStatusTabs.map { it.first }.filter { changes.desiredMembership[it] == true }.toSet()
        val added = (desiredKeys - currentKeys)
        require(added.size <= 1) { "An AniList entry has only one list status" }
        val addedStatus = added.singleOrNull()?.let { key -> aniListStatusTabs.first { it.first == key }.second }
        if (addedStatus != null) {
            val mediaId = current.firstOrNull()?.mediaId
                ?: repository.resolveMediaId(item.itemId, item.itemType, item.title)
                ?: error("This title could not be found on AniList")
            repository.setListStatus(mediaId, addedStatus)
            return
        }
        val removed = removedEntries(item, changes) ?: return
        require(!aniListRemovalNeedsConfirmation(removed) || destructiveRemovalConfirmed) {
            "Removing this title from AniList would also clear its progress and score"
        }
        removed.forEach { repository.deleteListEntry(it.id) }
    }

    private fun removedEntries(item: LibraryEntryInput, changes: ListMembershipChanges): List<AniListEntry>? {
        val current = repository.listSnapshot.value?.entriesFor(item.itemId).orEmpty()
        val currentKeys = current.mapNotNull { aniListTabFor(it.status) }.toSet()
        val desiredKeys = aniListStatusTabs.map { it.first }.filter { changes.desiredMembership[it] == true }.toSet()
        if ((desiredKeys - currentKeys).isNotEmpty()) return null
        val removedKeys = currentKeys - desiredKeys
        if (removedKeys.isEmpty()) return null
        return current.filter { aniListTabFor(it.status) in removedKeys }
    }

    override suspend fun refresh(intent: TrackingRefreshIntent) {
        repository.syncNow()
    }
}
