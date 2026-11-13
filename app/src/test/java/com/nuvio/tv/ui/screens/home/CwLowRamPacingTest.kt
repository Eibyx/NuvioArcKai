package com.nuvio.tv.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class CwLowRamPacingTest {
    @Test
    fun `other devices keep arc values`() {
        assertEquals(32, CwLowRamPacing.nextUpLookups(lowRam = false))
        assertEquals(4, CwLowRamPacing.concurrency(lowRam = false))
        assertEquals(0L, CwLowRamPacing.olderSeedGapMs(lowRam = false))
        assertEquals(0L, CwLowRamPacing.olderPassWaitMs(lowRam = false, firstBuiltAtMs = 1_000L, nowMs = 1_000L))
    }

    @Test
    fun `low ram looks up fewer shows first and fewer at a time`() {
        assertEquals(12, CwLowRamPacing.nextUpLookups(lowRam = true))
        assertEquals(2, CwLowRamPacing.concurrency(lowRam = true))
        assertEquals(1_500L, CwLowRamPacing.olderSeedGapMs(lowRam = true))
    }

    @Test
    fun `older pass waits from the first build and not from each refresh`() {
        assertEquals(45_000L, CwLowRamPacing.olderPassWaitMs(true, firstBuiltAtMs = 10_000L, nowMs = 10_000L))
        assertEquals(15_000L, CwLowRamPacing.olderPassWaitMs(true, firstBuiltAtMs = 10_000L, nowMs = 40_000L))
        assertEquals(0L, CwLowRamPacing.olderPassWaitMs(true, firstBuiltAtMs = 10_000L, nowMs = 90_000L))
    }
}
