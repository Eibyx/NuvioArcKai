package com.nuvio.tv.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KaiPlaybackChangesTest {
    private fun step(elapsedMs: Long, ready: Boolean, buffered: Long, last: Long, patient: Boolean, playing: Boolean = false) =
        StartupWatchdogDecision.next(
            elapsedMs = elapsedMs,
            intervalMs = 20_000,
            ceilingMs = 80_000,
            slowStart = false,
            bufferedAheadMs = buffered,
            lastBufferedAheadMs = last,
            ready = ready,
            playing = playing,
            positionMs = 0,
            lastPositionMs = 0,
            videoRestarted = false,
            tracksRead = ready,
            hasVideoTrack = true,
            videoSelected = true,
            patientStart = patient,
        )

    @Test
    fun `a slow start keeps waiting while the player is still loading`() {
        // Before: data stopped growing at 20 s -> the "decoder not responding" error.
        assertTrue(step(20_000, ready = false, buffered = 3_000, last = 3_000, patient = false) is StartupWatchdogDecision.Step.Fire)
        assertEquals(StartupWatchdogDecision.Step.WaitForData, step(20_000, ready = false, buffered = 3_000, last = 3_000, patient = true))
        assertEquals(StartupWatchdogDecision.Step.WaitForData, step(40_000, ready = false, buffered = 0, last = 0, patient = true))
    }

    @Test
    fun `it still gives up at the ceiling and still catches a hung decoder`() {
        assertTrue(step(80_000, ready = false, buffered = 0, last = 0, patient = true) is StartupWatchdogDecision.Step.Fire)
        // READY but no picture and nothing moving: a hung decoder, reported as before.
        val hung = step(20_000, ready = true, buffered = 5_000, last = 5_000, patient = true)
        assertTrue(hung is StartupWatchdogDecision.Step.Fire)
    }

    @Test
    fun `diagnostics keep the newest events and no links or tokens`() {
        KaiPlaybackDiagnostics.clear()
        repeat(50) { KaiPlaybackDiagnostics.record("e", "event $it", now = 1_000L + it) }
        val events = KaiPlaybackDiagnostics.events()
        assertEquals(40, events.size)
        assertEquals("event 49", events.last().message)
        KaiPlaybackDiagnostics.record("engine_switch", "error at https://cdn.example.com/x?token=SECRET Bearer abc.SECRET")
        val last = KaiPlaybackDiagnostics.events().last().message
        assertFalse(last, last.contains("SECRET"))
        assertTrue(KaiPlaybackDiagnostics.lines(3).first().contains("engine_switch"))
        KaiPlaybackDiagnostics.clear()
    }

    @Test
    fun `a subtitle attach that needs a reload waits while the video plays`() {
        assertFalse(addonSubtitleAttachIsSeamless(usingMpv = false, preAttached = false, drawnOnTop = false, playing = true))
        assertTrue(addonSubtitleAttachIsSeamless(usingMpv = false, preAttached = true, drawnOnTop = false, playing = true))
        assertTrue(addonSubtitleAttachIsSeamless(usingMpv = true, preAttached = false, drawnOnTop = false, playing = true))
    }
}
