package com.nuvio.tv.core.logging

import com.nuvio.tv.core.subtitlelibrary.LibraryRedaction
import java.util.Locale

/** One line of the app's own log, already redacted. */
data class AppLogLine(val time: String, val level: Char, val tag: String, val message: String) {
    val category: AppLogCategory get() = AppLogCategory.of(tag, level)
    fun text(): String = "$time $level $tag: $message"
}

enum class AppLogCategory {
    PLAYER, SUBTITLES, CLOUD, NETWORK, OTHER;

    companion object {
        /** Tags as the app and its libraries write them (player, libass/mpv, cloud storage…). */
        fun of(tag: String, level: Char): AppLogCategory {
            val t = tag.lowercase(Locale.ROOT)
            return when {
                SUBTITLE_TAGS.any { t.contains(it) } -> SUBTITLES
                CLOUD_TAGS.any { t.contains(it) } -> CLOUD
                PLAYER_TAGS.any { t.contains(it) } -> PLAYER
                NETWORK_TAGS.any { t.contains(it) } -> NETWORK
                else -> OTHER
            }
        }

        private val SUBTITLE_TAGS = listOf("subtitle", "libass", "ass", "autosync", "kaifonts", "kaisubtitle")
        private val CLOUD_TAGS = listOf("cloud", "terabox", "drive", "rclone")
        private val PLAYER_TAGS = listOf(
            "player", "exoplayer", "media3", "mpv", "codec", "switchtrace", "ttff", "kaidiagnostics", "audio",
            "video", "afr", "tunnel", "playback", "dv7", "hdr"
        )
        private val NETWORK_TAGS = listOf("okhttp", "network", "http", "dns", "debrid", "stream")
    }
}

/**
 * Reads this app's own log (Android lets an app read its own lines without any permission), the
 * same idea as NuvioMobileKai's in-app log viewer. Every line passes [LibraryRedaction] so links,
 * tokens and cookies never show or leave the TV.
 */
object AppLogReader {
    @Volatile private var hiddenBefore: String? = null

    /** "Borrar": the lines written up to now are not shown again (the system log itself is not touched). */
    fun clear(lines: List<AppLogLine>) {
        hiddenBefore = lines.lastOrNull()?.time
    }

    fun read(maxLines: Int = 3_000): List<AppLogLine> {
        val pid = android.os.Process.myPid()
        val raw = runCatching {
            val process = ProcessBuilder("logcat", "-d", "-v", "threadtime", "--pid=$pid", "-t", maxLines.toString())
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { it.readLines() }.also { process.destroy() }
        }.getOrDefault(emptyList())
        val cut = hiddenBefore
        return parse(raw, pid).filter { cut == null || it.time > cut }
    }

    /** `MM-DD HH:MM:SS.mmm  PID  TID L TAG: message` (logcat's threadtime format). */
    fun parse(lines: List<String>, pid: Int? = null): List<AppLogLine> = lines.mapNotNull { line ->
        val match = THREADTIME.matchEntire(line) ?: return@mapNotNull null
        if (pid != null && match.groupValues[3].toIntOrNull() != pid) return@mapNotNull null
        AppLogLine(
            time = "${match.groupValues[1]} ${match.groupValues[2]}",
            level = match.groupValues[5].first(),
            tag = match.groupValues[6].trim(),
            message = LibraryRedaction.text(match.groupValues[7]).take(600)
        )
    }

    private val THREADTIME = Regex("""^(\d\d-\d\d)\s+(\d\d:\d\d:\d\d\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEFA])\s+([^:]*?)\s*:\s?(.*)$""")
}
