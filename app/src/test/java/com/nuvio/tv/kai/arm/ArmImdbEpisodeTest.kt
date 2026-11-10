package com.nuvio.tv.kai.arm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArmImdbEpisodeTest {
    private val rows = listOf(
        ArmImdbRow(mal = "16498", anilist = "16498", tvdbSeason = 1),
        ArmImdbRow(mal = "25777", anilist = "20958", tvdbSeason = 2),
        ArmImdbRow(mal = "35760", anilist = "99147", tvdbSeason = 3),
        ArmImdbRow(mal = "38524", anilist = "104578", tvdbSeason = 3),
    )

    @Test
    fun `a season owned by one entry maps with the same episode`() {
        val (row, episode) = armEntryForImdbEpisode(rows, season = 2, episode = 7)!!
        assertEquals("25777", row.mal)
        assertEquals(7, episode)
    }

    @Test
    fun `a season split in parts is not guessed`() = assertNull(armEntryForImdbEpisode(rows, season = 3, episode = 14))

    @Test
    fun `unknown seasons, specials and episode zero are not guessed`() {
        assertNull(armEntryForImdbEpisode(rows, season = 5, episode = 1))
        assertNull(armEntryForImdbEpisode(rows, season = 0, episode = 1))
        assertNull(armEntryForImdbEpisode(rows, season = 1, episode = 0))
        assertNull(armEntryForImdbEpisode(emptyList(), season = 1, episode = 1))
    }
}
