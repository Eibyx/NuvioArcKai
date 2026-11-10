package com.nuvio.tv.kai.segments

import com.nuvio.tv.R
import com.nuvio.tv.ui.screens.player.skipLabelRes
import org.junit.Assert.assertEquals
import org.junit.Test

class SkipLabelTest {
    @Test
    fun `each segment kind has its own label`() {
        assertEquals(R.string.skip_segment_opening, skipLabelRes("op", false))
        assertEquals(R.string.skip_segment_opening, skipLabelRes("mixed-op", false))
        assertEquals(R.string.skip_segment_intro, skipLabelRes("intro", false))
        assertEquals(R.string.skip_segment_ending, skipLabelRes("ed", false))
        assertEquals(R.string.skip_segment_outro, skipLabelRes("outro", false))
        assertEquals(R.string.skip_segment_credits, skipLabelRes("credits", false))
        assertEquals(R.string.skip_segment_recap, skipLabelRes("recap", false))
    }

    @Test
    fun `post-credits, movie credits and unknown types keep their labels`() {
        assertEquals(R.string.skip_to_post_credits, skipLabelRes("ed", true))
        assertEquals(R.string.skip_to_post_credits, skipLabelRes("credits", true))
        assertEquals(R.string.skip_movie_credits, skipLabelRes("movie-credits", false))
        assertEquals(R.string.skip_to_post_credits, skipLabelRes("movie-credits", true))
        assertEquals(R.string.skip_generic, skipLabelRes("something", false))
        assertEquals(R.string.skip_generic, skipLabelRes(null, false))
    }

    @Test
    fun `Spanish labels are the requested ones`() {
        val main = listOf(java.io.File("src/main"), java.io.File("app/src/main")).first { it.isDirectory }
        listOf("values-es", "values-b+es+419").forEach { dir ->
            val xml = java.io.File(main, "res/$dir/strings.xml").readText()
            mapOf(
                "opening" to "Saltar opening", "intro" to "Saltar intro", "ending" to "Saltar ending",
                "outro" to "Saltar outro", "credits" to "Saltar créditos", "recap" to "Saltar recapitulación"
            ).forEach { (key, text) -> assertEquals("$dir $key", true, xml.contains("<string name=\"skip_segment_$key\">$text</string>")) }
        }
    }
}
