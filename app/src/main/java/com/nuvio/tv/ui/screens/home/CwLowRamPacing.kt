package com.nuvio.tv.ui.screens.home

/**
 * Kai: Continue Watching on low-RAM TVs (1 GB). A TV log showed 90 meta requests in the first
 * minute, nearly all from Continue Watching working out the next episode of every show in the
 * watch history, while ~40 home rows were also loading: GC every second and 0.7–1 s frame drops.
 *
 * On low-RAM devices only:
 * - the first pass looks up the [LOW_RAM_NEXT_UP_LOOKUPS] most recent shows (what the row shows)
 *   instead of 32; the rest go to the older-shows pass, as they already did past 32;
 * - the older-shows pass starts [LOW_RAM_OLDER_PASS_DELAY_MS] after the app first built Continue
 *   Watching and leaves [LOW_RAM_OLDER_SEED_GAP_MS] between shows;
 * - lookups run two at a time instead of four.
 * Nothing is dropped: every show is still checked, just later. The saved Continue Watching list
 * keeps its items on screen meanwhile. Other devices keep Arc's values.
 */
internal object CwLowRamPacing {
    const val DEFAULT_NEXT_UP_LOOKUPS = 32
    const val DEFAULT_CONCURRENCY = 4
    const val LOW_RAM_NEXT_UP_LOOKUPS = 12
    const val LOW_RAM_CONCURRENCY = 2
    const val LOW_RAM_OLDER_PASS_DELAY_MS = 45_000L
    const val LOW_RAM_OLDER_SEED_GAP_MS = 1_500L

    fun nextUpLookups(lowRam: Boolean): Int = if (lowRam) LOW_RAM_NEXT_UP_LOOKUPS else DEFAULT_NEXT_UP_LOOKUPS

    fun concurrency(lowRam: Boolean): Int = if (lowRam) LOW_RAM_CONCURRENCY else DEFAULT_CONCURRENCY

    fun olderSeedGapMs(lowRam: Boolean): Long = if (lowRam) LOW_RAM_OLDER_SEED_GAP_MS else 0L

    /**
     * How long the older-shows pass still has to wait. Anchored to the first time Continue
     * Watching was built in this process, so a list that refreshes often does not keep
     * pushing the pass back.
     */
    fun olderPassWaitMs(lowRam: Boolean, firstBuiltAtMs: Long, nowMs: Long): Long =
        if (!lowRam) 0L else (firstBuiltAtMs + LOW_RAM_OLDER_PASS_DELAY_MS - nowMs).coerceAtLeast(0L)

    @Volatile
    private var firstBuiltAtMs: Long = -1L

    /** Records the first Continue Watching build of the process and returns its time. */
    @Synchronized
    fun firstBuiltAt(nowMs: Long): Long {
        if (firstBuiltAtMs < 0L) firstBuiltAtMs = nowMs
        return firstBuiltAtMs
    }
}
