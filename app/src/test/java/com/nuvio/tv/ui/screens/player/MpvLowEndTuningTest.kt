package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvLowEndTuningTest {
    @Test
    fun `low ram tv uses smaller demuxer cache`() {
        val low = MpvLowEndTuning.demuxerBytes(lowRam = true)
        assertEquals(32L * 1024 * 1024, low.forward)
        assertEquals(8L * 1024 * 1024, low.back)
    }

    @Test
    fun `other devices keep arc cache`() {
        assertEquals(MpvLowEndTuning.ARC_DEMUXER, MpvLowEndTuning.demuxerBytes(lowRam = false))
    }

    @Test
    fun `software decode turns on light options and off restores defaults`() {
        val on = MpvLowEndTuning.softwareDecodeOptions(true)
        val off = MpvLowEndTuning.softwareDecodeOptions(false)
        assertEquals("all", on["vd-lavc-skiploopfilter"])
        assertEquals("decoder+vo", on["framedrop"])
        assertEquals("default", off["vd-lavc-skiploopfilter"])
        assertEquals("vo", off["framedrop"])
        assertTrue(on.keys == off.keys)
    }
}
