package com.nuvio.tv.ui.screens.player

import android.util.Log
import com.nuvio.tv.core.subtitlelibrary.LibraryRedaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kai: what the player decided and why, for the "Diagnóstico" section of the stream info panel:
 * which engine Auto chose and the reason, engine switches, start-up waits, and every change of the
 * subtitle selection with who made it. Links and secrets never get in (see [LibraryRedaction]).
 */
internal object KaiPlaybackDiagnostics {
    data class Event(val atMs: Long, val kind: String, val message: String)

    private const val TAG = "KaiDiagnostics"
    private const val MAX_EVENTS = 40
    private val events = ArrayDeque<Event>()

    @Synchronized
    fun record(kind: String, message: String, now: Long = System.currentTimeMillis()) {
        val safe = LibraryRedaction.text(message).take(200)
        events.addLast(Event(now, kind, safe))
        while (events.size > MAX_EVENTS) events.removeFirst()
        runCatching { Log.i(TAG, "$kind: $safe") }
    }

    /** A new playback starts: older events stay, under a separator, so a failed start can be read later. */
    fun newPlayback(label: String) = record("playback", label)

    @Synchronized
    fun events(): List<Event> = events.toList()

    @Synchronized
    fun clear() = events.clear()

    /** The newest events first, as "hh:mm:ss  kind: message". */
    fun lines(limit: Int = 14): List<String> {
        val format = SimpleDateFormat("HH:mm:ss", Locale.ROOT)
        return events().asReversed().take(limit).map { "${format.format(Date(it.atMs))}  ${it.kind}: ${it.message}" }
    }
}
