package com.nuvio.tv.kai.segments

import android.util.Log
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.repository.SkipInterval
import com.nuvio.tv.ui.screens.player.PlayerChapter
import java.util.Locale

/**
 * Structured trace of the skip-segment pipeline, so ExoPlayer and mpv runs of the same episode can be compared.
 * On in debug builds, or on a release with `adb shell setprop log.tag.ArcSkipDiag VERBOSE`.
 * It never logs URLs, tokens or file paths: only IDs, counts, chapter names and positions.
 *
 * Every line starts with `ArcSkipDiag` and a stage (ids, fetch, chapters, intervals, active, button, skip, case).
 */
internal object ArcSkipDiag {
    const val TAG = "ArcSkipDiag"

    enum class Case { SIN_FUENTE, CAPITULOS_NO_RECONOCIDOS, DATOS_SIN_BOTON, OK }

    fun enabled(): Boolean = BuildConfig.DEBUG || runCatching { Log.isLoggable(TAG, Log.VERBOSE) }.getOrDefault(false)

    private fun line(stage: String, text: String) {
        if (!enabled()) return
        runCatching { Log.i(TAG, "$stage $text") }
    }

    fun ids(source: String, id: String, mal: String?, anilist: String?, via: String) =
        line("ids", "source=$source id=$id mal=${mal ?: "-"} anilist=${anilist ?: "-"} via=$via")

    fun fetch(engine: String, contentId: String?, videoId: String?, route: String, count: Int?) =
        line("fetch", "engine=$engine contentId=${contentId ?: "-"} videoId=${videoId ?: "-"} route=$route intervals=${count ?: "timeout"}")

    fun chapters(engine: String, chapters: List<PlayerChapter>) {
        if (!enabled()) return
        line("chapters", "engine=$engine count=${chapters.size}")
        chapters.forEach { chapter ->
            val names = (listOfNotNull(chapter.title) + chapter.allTitles).distinct()
            val kind = SegmentVocabulary.classifyAny(names)
            line("chapter", "n=${chapter.number} startMs=${chapter.startMs} names=${names.joinToString("|")} kind=${kind ?: "-"}")
        }
    }

    fun intervals(engine: String, chapters: List<SkipInterval>, server: List<SkipInterval>, provider: List<SkipInterval>, merged: List<SkipInterval>) =
        line(
            "intervals",
            "engine=$engine chapters=${chapters.describe()} server=${server.describe()} provider=${provider.describe()} merged=${merged.describe()}"
        )

    fun active(engine: String, positionMs: Long, interval: SkipInterval?) =
        line("active", "engine=$engine posMs=$positionMs interval=${interval?.let { listOf(it).describe() } ?: "none"}")

    fun button(visible: Boolean, interval: SkipInterval?) =
        line("button", "visible=$visible interval=${interval?.let { listOf(it).describe() } ?: "none"}")

    fun skip(controller: Any?, interval: SkipInterval, targetMs: Long) =
        line("skip", "interval=${listOf(interval).describe()} targetMs=$targetMs")

    fun reached(engine: String, targetMs: Long, positionMs: Long) =
        line("reached", "engine=$engine targetMs=$targetMs posMs=$positionMs deltaMs=${positionMs - targetMs}")

    fun case(engine: String, case: Case, reason: String) = line("case", "engine=$engine case=$case reason=$reason")

    /**
     * SIN_FUENTE: no provider, server or chapter gave an interval and no chapter looked like a segment.
     * CAPITULOS_NO_RECONOCIDOS: the file has named chapters but none was classified.
     * DATOS_SIN_BOTON: an interval was active but the button never showed.
     * OK: an interval was active and the button showed.
     */
    fun classify(
        namedChapters: Int,
        classifiedChapters: Int,
        mergedIntervals: Int,
        sawActiveInterval: Boolean,
        sawButton: Boolean
    ): Case = when {
        sawActiveInterval && sawButton -> Case.OK
        sawActiveInterval -> Case.DATOS_SIN_BOTON
        mergedIntervals == 0 && namedChapters >= 2 && classifiedChapters == 0 -> Case.CAPITULOS_NO_RECONOCIDOS
        mergedIntervals == 0 -> Case.SIN_FUENTE
        else -> Case.OK
    }

    private fun List<SkipInterval>.describe(): String =
        if (isEmpty()) "[]" else joinToString(prefix = "[", postfix = "]") {
            String.format(Locale.US, "%s/%s:%.1f-%.1f@%s", it.type, it.label ?: "-", it.startTime, it.endTime, it.provider)
        }
}
