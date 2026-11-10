package com.nuvio.tv.kai.segments

/**
 * Where the skip button lands. Ordinary segments land just past their end ([PAST_END_MS]) with an exact seek
 * that may only round forward to a keyframe within [TOLERANCE_AFTER_US]; landing before the end would put playback
 * back inside the interval and bring the button back. Post-credits targets and movie credits keep their exact seek.
 */
internal object SkipSeekTarget {
    const val PAST_END_MS = 100L
    const val TOLERANCE_AFTER_US = 300_000L

    data class Plan(val positionMs: Long, val exactOnly: Boolean)

    /**
     * @param targetSec where the segment ends (or where its post-credits scene starts); [Double.MAX_VALUE] means
     * "to the end of the file".
     */
    fun plan(targetSec: Double, durationMs: Long, toPostCredits: Boolean, movieCredits: Boolean): Plan {
        val duration = durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        if (targetSec == Double.MAX_VALUE) return Plan(duration, exactOnly = true)
        val exactOnly = toPostCredits || movieCredits
        val base = (targetSec * 1000).toLong()
        val position = if (exactOnly) base else base + PAST_END_MS
        return Plan(position.coerceAtMost(duration).coerceAtLeast(0L), exactOnly)
    }
}
