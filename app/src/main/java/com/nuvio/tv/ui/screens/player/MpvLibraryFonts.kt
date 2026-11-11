package com.nuvio.tv.ui.screens.player

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * mpv's `sub-fonts-dir` for the user's subtitle folders.
 *
 * In mpv 0.41 (the app's libmpv) every subtitle decoder builds its own libass library when it is
 * created (`sd_ass` init → `mp_ass_init` → `ass_set_fonts_dir`) and libass reads every font of that
 * directory into memory. A decoder is created by `sub-add` and whenever a track is selected again.
 * So the directory holds only the app's bundled fonts plus the fonts of the folder subtitle about
 * to be added, and it is rewritten before each `sub-add` (strategy "a").
 *
 * Files are hard links where the file system allows (no extra space), copies otherwise, and each
 * appears through a rename, so mpv never reads half a font.
 */
internal object MpvLibraryFonts {
    private const val TAG = "KaiSubtitleFonts"
    private const val ACTIVE_DIR = "mpv-sub-fonts-active"
    private const val FOLDER_FONT_PREFIX = "kai-"

    private val fontsByPath = ConcurrentHashMap<String, List<Pair<String, File>>>()
    /** Installed once per process: checking the bundled fonts reads about 4 MB. */
    @Volatile private var bundledDir: File? = null

    fun register(subtitlePath: String, fonts: List<Pair<String, File>>) {
        if (fonts.isEmpty()) fontsByPath.remove(subtitlePath) else fontsByPath[subtitlePath] = fonts
    }

    fun clear() = fontsByPath.clear()

    fun activeDir(context: Context): File = File(context.noBackupFilesDir, ACTIVE_DIR)

    /**
     * Makes the active directory hold the bundled fonts plus the fonts registered for [subtitlePath]
     * (none for any other subtitle). Never throws: on failure mpv keeps whatever is there.
     */
    @Synchronized
    fun prepare(context: Context, subtitlePath: String?): File {
        val dir = activeDir(context)
        runCatching {
            check(dir.isDirectory || dir.mkdirs())
            val bundledFonts = bundledDir ?: prepareMpvSubtitleFonts(context).also { bundledDir = it }
            val bundled = bundledFonts.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".tmp") }
            val wanted = LinkedHashMap<String, File>()
            bundled.forEach { wanted[it.name] = it }
            subtitlePath?.let { fontsByPath[it] }.orEmpty().forEachIndexed { index, (name, file) ->
                val safe = name.replace(Regex("""[^A-Za-z0-9._-]"""), "_").take(80)
                wanted["$FOLDER_FONT_PREFIX$index-$safe"] = file
            }
            dir.listFiles().orEmpty().forEach { existing ->
                if (existing.name !in wanted || existing.name.endsWith(".tmp")) existing.delete()
            }
            wanted.forEach { (name, source) ->
                val target = File(dir, name)
                if (target.isFile && target.length() == source.length()) return@forEach
                val temporary = File(dir, "$name.tmp")
                temporary.delete()
                val linked = runCatching { android.system.Os.link(source.absolutePath, temporary.absolutePath); true }.getOrDefault(false)
                if (!linked) source.copyTo(temporary, overwrite = true)
                check(temporary.renameTo(target)) { "rename failed" }
            }
        }.onFailure {
            Log.w(TAG, "mpv font directory not updated: ${it::class.java.simpleName}")
            // As before Kai: the bundled fonts directory itself.
            if (subtitlePath == null) return runCatching { prepareMpvSubtitleFonts(context) }.getOrDefault(dir)
        }
        return dir
    }
}
