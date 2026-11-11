package com.nuvio.tv.data.anilist

import com.nuvio.tv.core.tracking.TrackingProviderId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AniListLibraryTest {
    private fun entry(mediaId: Long, status: String, idMal: Long? = null, progress: Int = 0, score: Double = 0.0, format: String = "TV") =
        AniListEntry(
            id = mediaId * 10, mediaId = mediaId, status = status, progress = progress, score = score, updatedAt = mediaId,
            media = AniListMedia(id = mediaId, idMal = idMal, format = format, title = AniListTitle(english = "Anime $mediaId"),
                coverImage = AniListCoverImage(large = "https://img/$mediaId.jpg"), siteUrl = "https://anilist.co/anime/$mediaId"),
        )

    private val snapshot = AniListSyncSnapshot(
        viewerId = 1,
        entries = listOf(
            entry(1, AniListListStatus.CURRENT, idMal = 101, progress = 3),
            entry(2, AniListListStatus.PLANNING, idMal = 102),
            entry(3, AniListListStatus.COMPLETED, progress = 12, score = 8.5),
            entry(4, AniListListStatus.PAUSED),
            entry(5, AniListListStatus.DROPPED),
            entry(6, AniListListStatus.REPEATING, idMal = 106, progress = 2),
            entry(7, AniListListStatus.COMPLETED, idMal = 107, format = "MOVIE"),
        ),
        mappings = mapOf(
            3L to AniListMapping(anilist = 3, kitsu = 303),
            4L to AniListMapping(anilist = 4, imdb = "tt0000004"),
        ),
    )

    @Test
    fun fiveTabsAndRepeatingShowsInWatching() {
        assertEquals(
            listOf("CURRENT", "PLANNING", "COMPLETED", "PAUSED", "DROPPED"),
            aniListStatusTabs.map { it.second },
        )
        assertEquals(ANILIST_TAB_CURRENT, aniListTabFor(AniListListStatus.REPEATING))
        val entries = snapshot.aniListLibraryEntries().associateBy { it.trackingProviderItemId }
        assertEquals(setOf(ANILIST_TAB_CURRENT), entries.getValue("anilist:6").listKeys)
        assertTrue(entries.getValue("anilist:6").name.endsWith(ANILIST_REPEATING_MARK))
        assertFalse(entries.getValue("anilist:1").name.endsWith(ANILIST_REPEATING_MARK))
        assertEquals(setOf(ANILIST_TAB_DROPPED), entries.getValue("anilist:5").listKeys)
    }

    @Test
    fun entriesOpenAResolvableDetailPage() {
        val entries = snapshot.aniListLibraryEntries().associateBy { it.trackingProviderItemId }
        assertEquals("mal:101", entries.getValue("anilist:1").id)
        assertEquals("kitsu:303", entries.getValue("anilist:3").id)
        assertEquals("tt0000004", entries.getValue("anilist:4").id)
        assertEquals("movie", entries.getValue("anilist:7").type)
        assertEquals(TrackingProviderId.ANILIST.storageId, entries.getValue("anilist:1").trackingProviderId)
        // No MAL, Kitsu or IMDb id: listed, but never opens an empty detail page.
        val unresolved = entries.getValue("anilist:5")
        assertEquals("anilist:5", unresolved.id)
        assertTrue(unresolved.isUnresolvedAniListEntry())
        assertFalse(entries.getValue("anilist:1").isUnresolvedAniListEntry())
    }

    @Test
    fun membershipFollowsEveryKnownId() {
        assertEquals(listOf(1L), snapshot.entriesFor("MAL:101").map { it.mediaId })
        assertEquals(listOf(3L), snapshot.entriesFor("kitsu:303").map { it.mediaId })
        assertEquals(listOf(4L), snapshot.entriesFor("tt0000004").map { it.mediaId })
        assertEquals(listOf(5L), snapshot.entriesFor("anilist:5").map { it.mediaId })
        assertTrue(snapshot.entriesFor("tt9999999").isEmpty())
    }

    @Test
    fun removingProgressOrAScoreNeedsConfirmation() {
        assertTrue(aniListRemovalNeedsConfirmation(listOf(snapshot.entries[0])))  // progress 3
        assertTrue(aniListRemovalNeedsConfirmation(listOf(snapshot.entries[2])))  // score
        assertFalse(aniListRemovalNeedsConfirmation(listOf(snapshot.entries[1])))  // planning, nothing to lose
    }

    @Test
    fun unknownStatusesAreNotListed() {
        assertNull(aniListTabFor("SOMETHING"))
        assertEquals(7, snapshot.aniListLibraryEntries().size)
    }
}
