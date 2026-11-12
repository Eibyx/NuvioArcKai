package com.nuvio.tv.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppLogReaderTest {
    private val sample = listOf(
        "--------- beginning of main",
        "11-12 15:40:01.123  4321  4400 I PlayerViewModel: Selecting ADDON subtitle via sidecar",
        "11-12 15:40:02.456  4321  4401 W KaiSubtitleLibrary: Subtitle folder terabox not read: IOException",
        "11-12 15:40:03.789  4321  4402 E CloudStorageRepository: GET https://d.terabox.com/file?sign=SECRET failed",
        "11-12 15:40:04.000  9999  9999 I OtherApp: not ours"
    )

    @Test
    fun `parses threadtime lines of this process only, redacted`() {
        val lines = AppLogReader.parse(sample, pid = 4321)
        assertEquals(3, lines.size)
        assertEquals('I', lines[0].level)
        assertEquals("PlayerViewModel", lines[0].tag)
        assertFalse(lines[2].message, lines[2].message.contains("SECRET"))
    }

    @Test
    fun `categories follow the tags`() {
        val lines = AppLogReader.parse(sample, pid = 4321)
        assertEquals(AppLogCategory.PLAYER, lines[0].category)
        assertEquals(AppLogCategory.SUBTITLES, lines[1].category)
        assertEquals(AppLogCategory.CLOUD, lines[2].category)
    }
}
