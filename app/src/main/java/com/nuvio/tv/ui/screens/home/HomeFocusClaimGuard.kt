package com.nuvio.tv.ui.screens.home

/**
 * Kai: stops two Home rows from taking focus from each other in a loop. A TV log showed
 * `continue_watching` and the row below it claiming focus in turn every ~40 ms for over 3 s
 * (both with `rowHasFocus=false`) while the user scrolled up; each claim scrolled the column and
 * made the other row claim again, causing 110–250 ms frame drops until it died down.
 *
 * A claim is refused when it would be the third of an A → B → A pattern with each step closer
 * than [windowMs]: a person cannot press the remote that fast, so it is the loop. Focus stays
 * where the last claim put it, the looping row stays refused until it has been quiet for a
 * window, and the next key press moves focus as usual.
 */
internal class HomeFocusClaimGuard(private val windowMs: Long = DEFAULT_WINDOW_MS) {
    private var lastRow: String? = null
    private var lastAtMs: Long = Long.MIN_VALUE
    private var previousRow: String? = null
    private var previousAtMs: Long = Long.MIN_VALUE
    private var refusedRow: String? = null
    private var refusedAtMs: Long = Long.MIN_VALUE

    /** Whether [rowKey] may claim focus at [nowMs]; records the claim when it may. */
    @Synchronized
    fun tryClaim(rowKey: String, nowMs: Long): Boolean {
        // A row caught looping stays refused until it has been quiet for a whole window.
        if (rowKey == refusedRow && nowMs - refusedAtMs in 0 until windowMs) {
            refusedAtMs = nowMs
            return false
        }
        val pingPong = lastRow != null && lastRow != rowKey && previousRow == rowKey &&
            nowMs - lastAtMs in 0 until windowMs && lastAtMs - previousAtMs in 0 until windowMs
        if (pingPong) {
            refusedRow = rowKey
            refusedAtMs = nowMs
            return false
        }
        if (rowKey != lastRow) {
            previousRow = lastRow
            previousAtMs = lastAtMs
        }
        lastRow = rowKey
        lastAtMs = nowMs
        return true
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 300L
        /** Home has one focus owner, so one guard is shared by every row. */
        val shared = HomeFocusClaimGuard()
    }
}
