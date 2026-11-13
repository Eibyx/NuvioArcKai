package com.nuvio.tv.core.tmdb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImdbTmdbIdStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `ids survive a save and load`() {
        val file = File(tmp.root, "kai/ids.tsv")
        ImdbTmdbIdStore(file).save(mapOf("tt0133093:movie" to 603, "tt1751305:tv" to 56353))
        assertEquals(mapOf("tt0133093:movie" to 603, "tt1751305:tv" to 56353), ImdbTmdbIdStore(file).load())
    }

    @Test
    fun `malformed lines and keys are skipped`() {
        val file = tmp.newFile("ids.tsv")
        file.writeText("tt1:movie\t5\nbad line\nmal:30:tv\t7\ntt2:tv\tx\ntt3:tv\t-1\n")
        assertEquals(mapOf("tt1:movie" to 5), ImdbTmdbIdStore(file).load())
    }

    @Test
    fun `missing file loads empty and clear deletes`() {
        val file = File(tmp.root, "none.tsv")
        assertTrue(ImdbTmdbIdStore(file).load().isEmpty())
        ImdbTmdbIdStore(file).save(mapOf("tt1:tv" to 1))
        ImdbTmdbIdStore(file).clear()
        assertFalse(file.exists())
    }

    @Test
    fun `store keeps at most the limit`() {
        val file = File(tmp.root, "ids.tsv")
        ImdbTmdbIdStore(file, maxEntries = 3).save((1..10).associate { "tt$it:tv" to it })
        assertEquals(3, ImdbTmdbIdStore(file, maxEntries = 3).load().size)
    }
}
