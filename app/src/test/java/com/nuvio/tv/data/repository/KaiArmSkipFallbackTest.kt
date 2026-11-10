package com.nuvio.tv.data.repository

import com.nuvio.tv.data.local.AnimeSkipSettingsDataStore
import com.nuvio.tv.data.local.AnimeSkipSettingsSnapshot
import com.nuvio.tv.data.remote.api.*
import com.nuvio.tv.kai.arm.ArmIdLookup
import com.nuvio.tv.kai.arm.ArmIds
import com.nuvio.tv.kai.arm.ArmImdbRow
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/** NuvioArcKai: without a Simkl client ID, ARM (no key) still reaches AniSkip for MAL-mapped entries. */
class KaiArmSkipFallbackTest {
    private val introDb = mockk<IntroDbApi>()
    private val aniSkip = mockk<AniSkipApi>()
    private val animeSkip = mockk<AnimeSkipApi>(relaxed = true)
    private val noSimkl = mockk<SimklIdResolver>()
    private val settings = mockk<AnimeSkipSettingsDataStore>()

    private class FakeArm(val ids: Map<String, ArmIds>, val rows: Map<String, List<ArmImdbRow>>) : ArmIdLookup {
        val calls = mutableListOf<String>()
        override suspend fun ids(source: String, id: String): ArmIds? { calls += "ids:$source:$id"; return ids["$source:$id"] }
        override suspend fun imdbRows(imdbId: String): List<ArmImdbRow> { calls += "imdb:$imdbId"; return rows[imdbId].orEmpty() }
    }

    private val arm = FakeArm(
        ids = mapOf(
            "kitsu:7442" to ArmIds(mal = "16498", anilist = "16498", kitsu = "7442", imdb = null),
            "anilist:20958" to ArmIds(mal = "25777", anilist = "20958", kitsu = null, imdb = null),
        ),
        rows = mapOf(
            "tt2560140" to listOf(
                ArmImdbRow("16498", "16498", 1),
                ArmImdbRow("25777", "20958", 2),
                ArmImdbRow("35760", "99147", 3),
                ArmImdbRow("38524", "104578", 3),
            )
        )
    )
    private val repository = SkipIntroRepository(introDb, aniSkip, animeSkip, noSimkl, settings, mockk(relaxed = true), arm)

    init {
        coEvery { settings.snapshot() } returns AnimeSkipSettingsSnapshot(1, false, "")
        coEvery { introDb.getSegments(any(), any(), any(), any()) } returns Response.success(IntroDbSegmentsResponse())
        coEvery { noSimkl.resolveIds(any(), any()) } returns null
        coEvery { noSimkl.resolveIdsForImdbEpisode(any(), any(), any()) } returns null
        coEvery { noSimkl.resolveEpisodeTvdb(any(), any(), any()) } returns null
        coEvery { aniSkip.getSkipTimes(any(), any(), any(), any()) } returns Response.success(
            AniSkipResponse(true, listOf(AniSkipResult(AniSkipInterval(10.0, 80.0), "op")))
        )
    }

    @Test fun `kitsu IDs reach AniSkip through ARM`() = runTest {
        val result = repository.getSkipIntervalsForKitsu("7442", 3)
        assertEquals(listOf("aniskip"), result.map { it.provider })
        coVerify { aniSkip.getSkipTimes("16498", 3, any(), any()) }
    }

    @Test fun `anilist IDs reach AniSkip through ARM`() = runTest {
        val result = repository.getSkipIntervalsForAnimeId("anilist", "20958", 5)
        assertEquals(1, result.size)
        coVerify { aniSkip.getSkipTimes("25777", 5, any(), any()) }
    }

    @Test fun `an IMDb season owned by one entry is looked up with the same episode`() = runTest {
        repository.getSkipIntervals("tt2560140", 2, 7)
        coVerify { aniSkip.getSkipTimes("25777", 7, any(), any()) }
    }

    @Test fun `an IMDb season split in parts is never guessed`() = runTest {
        val result = repository.getSkipIntervals("tt2560140", 3, 14)
        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { aniSkip.getSkipTimes(any(), any(), any(), any()) }
    }

    @Test fun `with Simkl configured ARM is not asked`() = runTest {
        val simkl = mockk<SimklIdResolver>()
        coEvery { simkl.resolveIds(any(), any()) } returns SimklIdResolver.ResolvedIds(1, "anime", mal = "16498", anilist = "16498")
        coEvery { simkl.resolveEpisodeTvdb(any(), any(), any()) } returns null
        val withSimkl = SkipIntroRepository(introDb, aniSkip, animeSkip, simkl, settings, mockk(relaxed = true), arm)
        withSimkl.getSkipIntervalsForKitsu("7442", 1)
        assertTrue(arm.calls.isEmpty())
    }
}
