package com.nuvio.tv.data.anilist

import com.nuvio.tv.domain.model.WatchedItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Adapted from NuvioMobileKai commonTest AniListTwoWaySyncTest. Mobile checks its detail-page actions; the TV
 * port checks the same import result (which episodes are marked, where a rewatch resumes) and that nothing
 * imported is sent back.
 */
class AniListTwoWaySyncTest {
    private val imdb = "tt13293588"

    private val link = AniListLink(
        key = aniListSendKey(imdb, "series", 1),
        parts = listOf(AniListLinkPart(108465, "Mushoku Tensei", 1, 11), AniListLinkPart(127720, "Part 2", 12, 12)),
        manual = false,
    )

    private fun completed(id: Long, episodes: Int) = AniListEntry(
        id = id, mediaId = id, status = AniListListStatus.COMPLETED, progress = episodes,
        media = AniListMedia(id = id, episodes = episodes),
    )

    @Test
    fun seasonOneCompletedOnAniListMarksAllOfSeasonOne() {
        val parts = importLinkedSeason(link, 1, mapOf(108465L to completed(108465, 11), 127720L to completed(127720, 12)))
        val keys = aniListImportedKeys(imdb, parts)
        assertEquals((1..23).map { aniListWatchedKey("series", imdb, 1, it) }.toSet(), keys)
        // So the next episode is S2E1: nothing of season two is marked.
        assertTrue(keys.none { it.endsWith(":2:1") })
    }

    @Test
    fun rewatchingResumesAtTheRewatchEpisode() {
        val parts = importLinkedSeason(
            link, 1,
            mapOf(
                108465L to completed(108465, 11),
                127720L to AniListEntry(2, 127720, AniListListStatus.REPEATING, 3, media = AniListMedia(127720, episodes = 12)),
            ),
        )
        val resume = parts.firstNotNullOf { it.resume }
        assertEquals(1 to 15, resume.season to resume.episode)
    }

    @Test
    fun whatCameFromAniListIsNotSentBack() {
        val watching = AniListEntry(2, 127720, AniListListStatus.CURRENT, progress = 5, media = AniListMedia(127720, episodes = 12))
        val keys = aniListImportedKeys(imdb, importLinkedSeason(link, 1, mapOf(108465L to completed(108465, 11), 127720L to watching)))
        // Marking the whole season in Nuvio: E1-E16 came from AniList, only E17-E23 are new.
        val season = (1..23).map { WatchedItem(contentId = imdb, contentType = "series", title = "", season = 1, episode = it, watchedAt = 1L) }
        assertEquals((17..23).toList(), withoutImported(season, keys).map { it.episode })
        val all = aniListImportedKeys(imdb, importLinkedSeason(link, 1, mapOf(108465L to completed(108465, 11), 127720L to completed(127720, 12))))
        assertTrue(withoutImported(season, all).isEmpty())
        assertEquals(23, withoutImported(season, emptySet()).size)
    }
}
