package com.nuvio.tv.kai.segments

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import com.nuvio.tv.core.player.dvmkv.ChaptersMetadata
import com.nuvio.tv.ui.screens.player.ChapterSkipIntervals
import com.nuvio.tv.ui.screens.player.PlayerChapter
import com.nuvio.tv.ui.screens.player.PlayerChapters
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ExoPlayer picks the chapter name in the device language. With a Spanish TV and a file named in English and
 * Spanish it used to pick "Apertura"/"Créditos", which the classifier did not know: no interval, no button.
 * The fixture is app/src/test/resources/mkv-chapters/multilang.mkv (scripts/tests/make_multilang_chapters_mkv.py).
 */
class MultilangChapterSkipTest {

    private val durationMs = 1_440_000L

    @Test
    fun `the multilingual fixture gives an opening and an ending on a Spanish device`() {
        val tracks = tracksOf(MkvChapterReader.read(resource("multilang.mkv")))
        val named = PlayerChapters.namedFileChapters(tracks, Locale("es"))!!
        assertEquals(listOf("Prólogo", "Apertura", "Parte A", "Créditos", "Avance"), named.map { it.title })

        val intervals = ChapterSkipIntervals.fromChapters(named, durationMs)
        val opening = intervals.single { it.type == "intro" }
        assertEquals(60.0, opening.startTime, 0.01)
        assertEquals(150.0, opening.endTime, 0.01)
        assertEquals("op", opening.label)
        val ending = intervals.single { it.type == "outro" }
        assertEquals(1_200.0, ending.startTime, 0.01)
        assertEquals(1_290.0, ending.endTime, 0.01)
        assertEquals("ed", ending.label)
    }

    @Test
    fun `the device language does not change the intervals`() {
        val metadata = MkvChapterReader.read(resource("multilang.mkv"))
        val results = listOf(Locale("es"), Locale.ENGLISH, Locale.JAPANESE, Locale("es", "419")).map { locale ->
            ChapterSkipIntervals.fromChapters(PlayerChapters.namedFileChapters(tracksOf(metadata), locale)!!, durationMs)
        }
        results.forEach { assertEquals(results.first(), it) }
        assertEquals(2, results.first().size)
    }

    @Test
    fun `Spanish-only names are understood`() {
        val chapters = listOf(
            PlayerChapter(1, 0L, "Resumen"),
            PlayerChapter(2, 45_000L, "Apertura"),
            PlayerChapter(3, 135_000L, "Episodio"),
            PlayerChapter(4, 1_250_000L, "Créditos"),
            PlayerChapter(5, 1_340_000L, "Escena post-créditos"),
        )
        val intervals = ChapterSkipIntervals.fromChapters(chapters, durationMs)
        assertEquals(listOf("recap", "intro", "outro"), intervals.map { it.type })
        assertEquals(listOf("recap", "op", "credits"), intervals.map { it.label })
    }

    @Test
    fun `mpv and ExoPlayer give the same intervals for the same chapters`() {
        val metadata = MkvChapterReader.read(resource("multilang.mkv"))
        val exo = ChapterSkipIntervals.fromChapters(PlayerChapters.namedFileChapters(tracksOf(metadata), Locale("es"))!!, durationMs)
        // mpv hands over one title per chapter (the one it chose); with English or Spanish titles the result matches.
        listOf(0, 1).forEach { language ->
            val mpv = metadata.chapters.mapIndexed { index, chapter ->
                PlayerChapter(index + 1, chapter.startUs / 1000, chapter.names[language].text)
            }
            // Times and types match; the label can differ, as mpv only knows the one title it shows.
            assertEquals(exo.map { Triple(it.startTime, it.endTime, it.type) }, ChapterSkipIntervals.fromChapters(mpv, durationMs).map { Triple(it.startTime, it.endTime, it.type) })
        }
    }

    @Test
    fun `the chapter list still hides only openings, intros and recaps`() {
        val metadata = MkvChapterReader.read(resource("multilang.mkv"))
        val story = PlayerChapters.fromMetadata(metadata, Locale("es"))
        assertEquals(listOf("Prólogo", "Parte A", "Créditos", "Avance"), story.map { it.title })
        assertTrue(story.none { it.allTitles.isNotEmpty() })
    }

    private fun tracksOf(metadata: ChaptersMetadata): Tracks {
        val format = Format.Builder().setSampleMimeType("video/avc").setMetadata(Metadata(metadata)).build()
        return Tracks(listOf(Tracks.Group(TrackGroup(format), false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true))))
    }

    private fun resource(name: String): ByteArray =
        javaClass.classLoader!!.getResourceAsStream("mkv-chapters/$name")!!.use { it.readBytes() }
}

/** Test-only EBML reader for the Chapters element (default edition, top-level atoms), independent of the extractor. */
internal object MkvChapterReader {
    fun read(bytes: ByteArray): ChaptersMetadata {
        val segment = children(bytes, 0, bytes.size).first { it.id == 0x18538067L }
        val chapters = children(bytes, segment.body, segment.end).first { it.id == 0x1043A770L }
        val edition = children(bytes, chapters.body, chapters.end).first { it.id == 0x45B9L }
        val atoms = children(bytes, edition.body, edition.end).filter { it.id == 0xB6L }.map { atom ->
            val parts = children(bytes, atom.body, atom.end)
            val startNs = parts.first { it.id == 0x91L }.uint(bytes)
            val names = parts.filter { it.id == 0x80L }.map { display ->
                val fields = children(bytes, display.body, display.end)
                ChaptersMetadata.Name(
                    fields.first { it.id == 0x85L }.text(bytes),
                    fields.firstOrNull { it.id == 0x437CL }?.text(bytes)
                )
            }
            ChaptersMetadata.Chapter(startNs / 1000, C.TIME_UNSET, names)
        }
        return ChaptersMetadata(atoms)
    }

    private data class Element(val id: Long, val body: Int, val end: Int) {
        fun uint(bytes: ByteArray): Long = (body until end).fold(0L) { acc, i -> (acc shl 8) or (bytes[i].toLong() and 0xFF) }
        fun text(bytes: ByteArray): String = String(bytes, body, end - body, Charsets.UTF_8)
    }

    private fun children(bytes: ByteArray, start: Int, end: Int): List<Element> {
        val out = mutableListOf<Element>()
        var i = start
        while (i < end) {
            val (id, idLength) = vint(bytes, i, keepMarker = true)
            val (size, sizeLength) = vint(bytes, i + idLength, keepMarker = false)
            val body = i + idLength + sizeLength
            val elementEnd = (body + size).toInt().coerceAtMost(end)
            out += Element(id, body, elementEnd)
            i = elementEnd
        }
        return out
    }

    private fun vint(bytes: ByteArray, at: Int, keepMarker: Boolean): Pair<Long, Int> {
        val first = bytes[at].toInt() and 0xFF
        var length = 1
        var marker = 0x80
        while (first and marker == 0) { marker = marker shr 1; length++ }
        var value = (if (keepMarker) first else first and (marker - 1)).toLong()
        for (k in 1 until length) value = (value shl 8) or (bytes[at + k].toLong() and 0xFF)
        return value to length
    }
}
