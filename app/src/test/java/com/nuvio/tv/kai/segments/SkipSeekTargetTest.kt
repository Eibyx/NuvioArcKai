package com.nuvio.tv.kai.segments

import com.nuvio.tv.data.repository.SkipInterval
import com.nuvio.tv.ui.screens.player.findActiveSkipInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipSeekTargetTest {

    private val opening = SkipInterval(60.0, 150.0, "intro", "chapters", label = "op")

    @Test
    fun `an opening lands 100 ms past its end, with forward tolerance only`() {
        val plan = SkipSeekTarget.plan(opening.endTime, durationMs = 1_440_000L, toPostCredits = false, movieCredits = false)
        assertEquals(150_100L, plan.positionMs)
        assertFalse(plan.exactOnly)
        assertEquals(300_000L, SkipSeekTarget.TOLERANCE_AFTER_US)
    }

    @Test
    fun `the landing point is outside the interval, so the button does not come back`() {
        val plan = SkipSeekTarget.plan(opening.endTime, 1_440_000L, toPostCredits = false, movieCredits = false)
        assertNull(findActiveSkipInterval(listOf(opening), plan.positionMs))
        // Even the furthest keyframe the tolerance allows stays past the end and inside the 400 ms window.
        val furthest = plan.positionMs + SkipSeekTarget.TOLERANCE_AFTER_US / 1000
        assertTrue(furthest - (opening.endTime * 1000).toLong() <= 400L)
        assertNull(findActiveSkipInterval(listOf(opening), furthest))
    }

    @Test
    fun `post-credits targets and movie credits keep their exact target`() {
        assertEquals(5_400_000L, SkipSeekTarget.plan(5_400.0, 6_000_000L, toPostCredits = true, movieCredits = false).positionMs)
        assertTrue(SkipSeekTarget.plan(5_400.0, 6_000_000L, toPostCredits = false, movieCredits = true).exactOnly)
    }

    @Test
    fun `an interval to the end of the file lands on the duration`() {
        val plan = SkipSeekTarget.plan(Double.MAX_VALUE, 1_440_000L, toPostCredits = false, movieCredits = false)
        assertEquals(1_440_000L, plan.positionMs)
        assertEquals(1_440_000L, SkipSeekTarget.plan(1_440.0, 1_440_000L, false, false).positionMs)
    }
}
