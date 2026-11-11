package com.nuvio.tv.data.anilist

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Adapted from NuvioMobileKai commonTest AniListListLinkerTest: the selection rules of the list linker (which entries
 * are linked, when they are looked up again, ARM seasons, anime-id links). Mobile's background linker class itself
 * is not ported, so its end-to-end cases are not either.
 */
class AniListListLinkerTest {
    private val mushoku = "tt13293588"
    private val clock = 1_000_000_000L

    private fun entry(mediaId: Long, status: String, progress: Int = 12, format: String = "TV") = AniListEntry(
        id = mediaId, mediaId = mediaId, status = status, progress = progress,
        media = AniListMedia(id = mediaId, format = format, episodes = 12, title = AniListTitle(english = "Anime $mediaId")),
    )

    private fun mapping(mediaId: Long, imdb: String?, mal: Long? = null, kitsu: Long? = null, resolved: Boolean = true) =
        AniListMapping(anilist = mediaId, mal = mal, kitsu = kitsu, imdb = imdb, resolvedAtEpochMs = if (resolved) 1L else 0L)

    private val mushokuList = AniListSyncSnapshot(
        viewerId = 7,
        entries = listOf(
            entry(108465, AniListListStatus.COMPLETED, 11),
            entry(127720, AniListListStatus.COMPLETED),
            entry(146065, AniListListStatus.CURRENT, 5),
            entry(1, AniListListStatus.PLANNING, 0),
            entry(2, AniListListStatus.DROPPED, 3),
            entry(3, AniListListStatus.COMPLETED, 1, format = "MOVIE"),
        ),
        mappings = mapOf(
            108465L to mapping(108465, mushoku, mal = 39535, kitsu = 42422),
            127720L to mapping(127720, mushoku),
            146065L to mapping(146065, mushoku),
            1L to mapping(1, "tt0000001"),
            2L to mapping(2, "tt0000002"),
            3L to mapping(3, "tt0000003"),
        ),
    )

    @Test
    fun onlySeenEntriesThatAreNotMoviesAreLinked() {
        assertEquals(listOf(108465L, 127720L, 146065L), aniListListEntries(mushokuList).map { it.mediaId })
        val unresolved = mushokuList.copy(mappings = mushokuList.mappings + (146065L to mapping(146065, mushoku, resolved = false)))
        assertEquals(listOf(108465L, 127720L), aniListEntriesToLink(unresolved, emptySet(), emptyMap(), clock).map { it.mediaId })
        assertEquals(listOf(146065L), aniListEntriesToLink(mushokuList, setOf(108465L), mapOf(127720L to clock - 1_000), clock).map { it.mediaId })
        assertEquals(3, aniListEntriesToLink(mushokuList, emptySet(), mapOf(127720L to clock - ANILIST_LIST_RECHECK_MS), clock).size)
        assertEquals(
            listOf(1, 2),
            armSeasonsOf(listOf(ArmImdbEntry(5, 2, null), ArmImdbEntry(4, 1, null), ArmImdbEntry(6, 3, null), ArmImdbEntry(7, null, null)), setOf(4, 5, 7)),
        )
    }

    @Test
    fun animeIdCatalogsLinkWithoutALookup() {
        val links = animeIdLinks(mushokuList.entries.first(), mushokuList.mappings[108465L])
        assertEquals(listOf(aniListSendKey("mal:39535", "series", 1), aniListSendKey("kitsu:42422", "series", 1)), links.map { it.key })
        assertEquals(listOf(108465L), links.flatMap { it.parts }.map { it.mediaId }.distinct())
        assertEquals(emptyList(), animeIdLinks(mushokuList.entries[1], mushokuList.mappings[127720L]))
    }
}
