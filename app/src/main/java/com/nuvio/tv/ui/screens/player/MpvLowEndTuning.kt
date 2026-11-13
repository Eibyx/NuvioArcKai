package com.nuvio.tv.ui.screens.player

/**
 * Kai: MPV settings for weak TVs (the user's TCL: 1 GB of RAM, armeabi-v7a), taken from a log
 * where MPV stuttered (audio underruns, 1–1.7 s frozen frames, 96 MB of free RAM):
 *
 * - [demuxerBytes]: a 64 MB forward + 64 MB back cache does not fit next to software decoding on
 *   a 1 GB box; low-RAM devices keep 32 MB forward and 8 MB back (seeking back still works, just
 *   re-downloads sooner). Other devices keep Arc's values.
 * - [softwareDecodeOptions]: when MPV decodes in software (10-bit H.264 the TV cannot decode),
 *   skip the H.264 loop filter and allow the fast decoder paths, and drop late frames in the
 *   decoder as well as at output, so sound stays in sync instead of underrunning. These are the
 *   usual mpv settings for slow CPUs; quality loss is small next to stutter.
 */
internal object MpvLowEndTuning {
    data class DemuxerBytes(val forward: Long, val back: Long)

    private const val MB = 1024L * 1024L
    val ARC_DEMUXER = DemuxerBytes(forward = 64 * MB, back = 64 * MB)
    val LOW_RAM_DEMUXER = DemuxerBytes(forward = 32 * MB, back = 8 * MB)

    fun demuxerBytes(lowRam: Boolean): DemuxerBytes = if (lowRam) LOW_RAM_DEMUXER else ARC_DEMUXER

    /** Option → value while software decoding; the second map puts mpv's defaults back. */
    fun softwareDecodeOptions(active: Boolean): Map<String, String> = if (active) {
        mapOf("vd-lavc-skiploopfilter" to "all", "vd-lavc-fast" to "yes", "framedrop" to "decoder+vo")
    } else {
        mapOf("vd-lavc-skiploopfilter" to "default", "vd-lavc-fast" to "no", "framedrop" to "vo")
    }
}
