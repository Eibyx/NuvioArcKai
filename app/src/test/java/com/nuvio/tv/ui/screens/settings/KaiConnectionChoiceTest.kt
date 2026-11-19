package com.nuvio.tv.ui.screens.settings

import com.nuvio.tv.core.network.StreamSweepEngine.MeasuredPass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KaiConnectionChoiceTest {
    @Test
    fun `always offers one two and three connections`() {
        val options = KaiConnectionChoice.options(12.0, emptyList(), null)
        assertEquals(listOf(1, 2, 3), options.map { it.connections })
        assertNull(options[1].mbps)
        assertEquals(KaiConnectionChoice.DEFAULT_CHUNK_MB, options[2].chunkMb)
    }

    @Test
    fun `each count keeps its fastest measured chunk`() {
        val measured = listOf(MeasuredPass(2, 8, 20.0), MeasuredPass(2, 16, 26.0), MeasuredPass(3, 16, 30.0), MeasuredPass(4, 16, 40.0))
        val options = KaiConnectionChoice.options(10.0, measured, null)
        assertEquals(16, options[1].chunkMb)
        assertEquals(26.0, options[1].mbps!!, 0.0)
        assertEquals(30.0, options[2].mbps!!, 0.0)
    }

    @Test
    fun `recommends the lightest count that reaches twice the film`() {
        val measured = listOf(MeasuredPass(2, 8, 21.0), MeasuredPass(3, 8, 30.0))
        // Film 10 Mbps -> bar 20 Mbps (19 with the allowance): one connection at 12 is short, two is enough.
        val options = KaiConnectionChoice.options(12.0, measured, 10.0)
        assertTrue(options.single { it.recommended }.connections == 2)
    }

    @Test
    fun `one connection is recommended when it already suffices`() {
        val options = KaiConnectionChoice.options(25.0, listOf(MeasuredPass(2, 8, 40.0)), 10.0)
        assertEquals(1, options.single { it.recommended }.connections)
    }

    @Test
    fun `without a film bitrate the fastest is recommended`() {
        val options = KaiConnectionChoice.options(5.0, listOf(MeasuredPass(3, 8, 9.0)), null)
        assertEquals(3, options.single { it.recommended }.connections)
    }

    @Test
    fun `in use reads parallel off as one`() {
        assertEquals(1, KaiConnectionChoice.currentCount(false, 3))
        assertEquals(3, KaiConnectionChoice.currentCount(true, 3))
    }
}
