package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.repository.SkipInterval
import com.nuvio.tv.data.repository.segmentCategory
import com.nuvio.tv.kai.segments.SegmentVocabulary

/**
 * Derive skip intervals from container chapters (MKV/Matroska).
 *
 * Chapter titles are matched case-insensitively. Only titles that are essentially
 * the segment label itself (with an optional number suffix) are matched, to avoid
 * false positives on scene-named chapters like "The Opening" or "Alternate Ending".
 *
 * Matched types: OP/Opening, ED/Ending/Credits/Outro, Recap/Previously, Intro, in the languages of
 * [SegmentVocabulary]. Every name the chapter carries is checked, not only the one shown, so a file whose
 * chapters are named in several languages gives the same intervals whatever the device language.
 * "Intro" is promoted to opening only when no dedicated OP/Opening chapter exists.
 *
 * Interval types stay intro/outro/recap (auto-skip, next-episode and merge rules read them);
 * [SkipInterval.label] keeps what the chapter said (op, intro, ed, outro, credits, recap) for the button.
 *
 * Titles containing "post-credits", "mid-credits", or "after-credits" are excluded
 * to avoid skipping post-credits scenes.
 */
internal object ChapterSkipIntervals {

    private const val PROVIDER = "chapters"

    private const val MIN_DURATION_SEC = 5.0
    private const val MAX_DURATION_SEC = 240.0 // 4 minutes max for OP/ED/recap

    fun fromChapters(
        chapters: List<PlayerChapter>,
        durationMs: Long
    ): List<SkipInterval> {
        if (chapters.size < 2 || durationMs <= 0L) return emptyList()

        val classified = chapters.mapIndexed { index, chapter ->
            val endMs = if (index + 1 < chapters.size) {
                chapters[index + 1].startMs
            } else {
                durationMs
            }
            ClassifiedChapter(
                chapter = chapter,
                endMs = endMs,
                type = classify(chapter)
            )
        }

        val hasExplicitOpening = classified.any { it.type == ChapterType.OPENING }
        val resolved = if (hasExplicitOpening) {
            classified.map { c ->
                if (c.type == ChapterType.INTRO) c.copy(type = null) else c
            }
        } else {
            classified.map { c ->
                if (c.type == ChapterType.INTRO) c.copy(type = ChapterType.OPENING) else c
            }
        }

        return resolved.mapNotNull { c ->
            val type = c.type ?: return@mapNotNull null
            // Post-credits scenes are classified but excluded from skip intervals.
            if (type == ChapterType.POST_CREDITS) return@mapNotNull null
            val skipType = type.skipIntervalType
            val startSec = c.chapter.startMs / 1000.0
            val endSec = c.endMs / 1000.0
            val duration = endSec - startSec
            if (duration < MIN_DURATION_SEC) return@mapNotNull null
            if (duration > MAX_DURATION_SEC) return@mapNotNull null
            SkipInterval(
                startTime = startSec,
                endTime = endSec,
                type = skipType,
                provider = PROVIDER,
                label = type.label
            )
        }
    }

    private fun classify(chapter: PlayerChapter): ChapterType? =
        // All of the file's names in file order, so the device language never changes the result; mpv gives one title.
        when (SegmentVocabulary.classifyAny(chapter.allTitles.ifEmpty { listOf(chapter.title) })) {
            SegmentVocabulary.Kind.OPENING -> ChapterType.OPENING
            SegmentVocabulary.Kind.INTRO -> ChapterType.INTRO
            SegmentVocabulary.Kind.RECAP -> ChapterType.RECAP
            SegmentVocabulary.Kind.ENDING -> ChapterType.ENDING
            SegmentVocabulary.Kind.OUTRO -> ChapterType.OUTRO
            SegmentVocabulary.Kind.CREDITS -> ChapterType.CREDITS
            SegmentVocabulary.Kind.POST_CREDITS -> ChapterType.POST_CREDITS
            null -> null
        }

    private enum class ChapterType(val skipIntervalType: String, val label: String) {
        OPENING("intro", "op"),
        ENDING("outro", "ed"),
        OUTRO("outro", "outro"),
        CREDITS("outro", "credits"),
        RECAP("recap", "recap"),
        INTRO("intro", "intro"),
        POST_CREDITS("post-credits", "post-credits"),
    }

    private data class ClassifiedChapter(
        val chapter: PlayerChapter,
        val endMs: Long,
        val type: ChapterType?
    )
}

internal fun mergeSkipIntervals(
    apiIntervals: List<SkipInterval>,
    chapterIntervals: List<SkipInterval>,
    isMovie: Boolean = false
): List<SkipInterval> {
    if (chapterIntervals.isEmpty()) return apiIntervals
    if (apiIntervals.isEmpty()) return chapterIntervals
    // For movies: prefer API entirely when it has data (handles post-credits trimming)
    if (isMovie && apiIntervals.isNotEmpty()) return apiIntervals
    val chosen = LinkedHashMap<String, List<SkipInterval>>()
    for (interval in chapterIntervals) {
        val category = segmentCategory(interval.type) ?: continue
        chosen.putIfAbsent(category, listOf(interval))
    }
    apiIntervals.filter { segmentCategory(it.type) != null }
        .groupBy { segmentCategory(it.type)!! }
        .forEach { (category, intervals) -> chosen.putIfAbsent(category, intervals) }
    val uncategorized = (chapterIntervals + apiIntervals)
        .filter { segmentCategory(it.type) == null }
    return chosen.values.flatten() + uncategorized
}
