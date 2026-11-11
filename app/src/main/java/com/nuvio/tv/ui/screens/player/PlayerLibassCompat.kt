package com.nuvio.tv.ui.screens.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor as StockMatroskaExtractor
import androidx.media3.extractor.text.SubtitleParser
import com.nuvio.tv.core.player.HdrColorSignalingExtractor
import com.nuvio.tv.core.player.RenderTimeSubtitles
import com.nuvio.tv.core.player.dvmkv.MatroskaExtractor as DvMatroskaExtractor
import com.nuvio.tv.libass.media.AssHandler
import com.nuvio.tv.ui.screens.player.autosync.AutoSyncLibassSubtitles
import com.nuvio.tv.libass.media.parser.AssSubtitleParserFactory
import com.nuvio.tv.libass.media.render.AssRenderersFactory
import java.util.Collections
import java.util.WeakHashMap

private class AssPlayerBinding(
    val handler: AssHandler,
    val subtitleTimeShiftUs: () -> Long,
    val libraryFonts: LibraryFontRegistry
)

private val assBindingsByPlayer = Collections.synchronizedMap(WeakHashMap<ExoPlayer, AssPlayerBinding>())

/**
 * [subtitleTimeShiftUs] moves libass subtitles against the picture (audio delay minus subtitle
 * delay, read on every frame), the same shift the text renderers get.
 */
@OptIn(UnstableApi::class)
internal fun ExoPlayer.Builder.buildWithAssSupportCompat(
    context: Context,
    playerMediaSourceFactory: PlayerMediaSourceFactory? = null,
    dataSourceFactory: DataSource.Factory = PlayerPlaybackNetworking.createDataSourceFactory(context),
    extractorsFactory: ExtractorsFactory = DefaultExtractorsFactory(),
    renderersFactory: RenderersFactory = DefaultRenderersFactory(context),
    autoSyncSourceKey: String? = null,
    subtitleTimeShiftUs: () -> Long = { 0L },
    libraryFonts: LibraryFontRegistry = LibraryFontRegistry()
): ExoPlayer {
    val assHandler = AssHandler()
    val assSubtitleParserFactory = CompatAssSubtitleParserFactory(assHandler, libraryFonts)
    // AutoSync and the tap wrap after the Matroska swap, which matches extractors by class.
    val swappedExtractorsFactory = extractorsFactory.withAssMkvSupportCompat(
        subtitleParserFactory = RenderTimeSubtitles.extractionParsers(
            assSubtitleParserFactory,
            autoSyncObservesText = autoSyncSourceKey != null
        ),
        assHandler = assHandler
    )
    val assExtractorsFactory = com.nuvio.tv.core.player.thumbnail.PlaybackTap.wrap(
        autoSyncSourceKey?.let {
            com.nuvio.tv.ui.screens.player.autosync.AutoSyncExtractorsFactory(swappedExtractorsFactory, it)
        } ?: swappedExtractorsFactory
    )
    playerMediaSourceFactory?.configureSubtitleParsing(
        extractorsFactory = assExtractorsFactory,
        subtitleParserFactory = assSubtitleParserFactory
    )

    val mediaSourceFactory = DefaultMediaSourceFactory(
        dataSourceFactory,
        assExtractorsFactory
    )
    mediaSourceFactory.setSubtitleParserFactory(assSubtitleParserFactory)

    // AssRenderersFactory adds the position renderer that drives libass in tunnelled playback.
    val player = this
        .setMediaSourceFactory(mediaSourceFactory)
        .setRenderersFactory(AssRenderersFactory(assHandler, renderersFactory))
        .build()

    assBindingsByPlayer[player] = AssPlayerBinding(assHandler, subtitleTimeShiftUs, libraryFonts)
    assHandler.init(player)
    // Registered after AssHandler, so it runs after libass has dropped the fonts: only a check
    // that the reload's transition did come before the folder subtitle's parser (see the registry).
    player.addListener(object : androidx.media3.common.Player.Listener {
        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            assSubtitleParserFactory.onMediaItemTransition()
        }
    })
    return player
}

internal fun ExoPlayer.getAssHandlerCompat(): AssHandler? = assBindingsByPlayer[this]?.handler

/** The fonts of the user's subtitle folders for this player (null without libass). */
internal fun ExoPlayer.libraryFontRegistryCompat(): LibraryFontRegistry? = assBindingsByPlayer[this]?.libraryFonts

/**
 * The frame callback for [AssHandler.videoFrameCallback]: [render] gets the subtitle time with the
 * player's audio and subtitle delays applied.
 */
internal fun ExoPlayer.assVideoFrameCallbackCompat(
    render: (presentationTimeUs: Long, releaseTimeNs: Long) -> Unit
): (Long, Long) -> Unit {
    val shift = assBindingsByPlayer[this]?.subtitleTimeShiftUs ?: return render
    return shiftedAssVideoFrameCallback(shift, render)
}

internal fun shiftedAssVideoFrameCallback(
    subtitleTimeShiftUs: () -> Long,
    render: (presentationTimeUs: Long, releaseTimeNs: Long) -> Unit
): (Long, Long) -> Unit = { presentationTimeUs, releaseTimeNs ->
    render((presentationTimeUs + subtitleTimeShiftUs()).coerceAtLeast(0L), releaseTimeNs)
}

/** Frees the native libass state (renderer, tracks, embedded MKV fonts) when the player goes. */
internal fun ExoPlayer.releaseAssHandlerCompat() {
    assBindingsByPlayer.remove(this)?.handler?.release()
}

@OptIn(UnstableApi::class)
private class CompatAssSubtitleParserFactory(
    private val assHandler: AssHandler,
    private val libraryFonts: LibraryFontRegistry
) : SubtitleParser.Factory {
    private val delegate = AssSubtitleParserFactory(assHandler)
    @Volatile private var folderParserCreatedAtMs = 0L

    fun onMediaItemTransition() {
        val createdAt = folderParserCreatedAtMs
        // A transition right after a folder subtitle's parser would have wiped its fonts.
        if (createdAt != 0L && android.os.SystemClock.elapsedRealtime() - createdAt < 2_000L) {
            android.util.Log.w("KaiSubtitleFonts", "media item transition after a folder subtitle parser; fonts may be lost")
        }
        folderParserCreatedAtMs = 0L
    }

    override fun supportsFormat(format: Format): Boolean {
        return delegate.supportsFormat(normalizeSsaFormat(format))
    }

    override fun getCueReplacementBehavior(format: Format): Int {
        return delegate.getCueReplacementBehavior(normalizeSsaFormat(format))
    }

    override fun create(format: Format): SubtitleParser {
        val normalized = normalizeSsaFormat(format)
        if (normalized.sampleMimeType == MimeTypes.TEXT_SSA && !libraryFonts.isEmpty()) {
            // Before delegate.create: AssHandler.createTrack flushes these pending fonts to libass.
            if (libraryFonts.addFontsFor(format.id) { name, data -> assHandler.addFont(name, data) } > 0) {
                folderParserCreatedAtMs = android.os.SystemClock.elapsedRealtime()
            }
        }
        return AutoSyncLibassSubtitles.wrapParser(assHandler, normalized, delegate.create(normalized)) // AutoSync hook
    }

    private fun normalizeSsaFormat(format: Format): Format {
        val isSsaByCodecs = format.codecs == MimeTypes.TEXT_SSA
        val isSsaByMime = format.sampleMimeType == MimeTypes.TEXT_SSA
        if (isSsaByCodecs && !isSsaByMime) {
            return format.buildUpon()
                .setSampleMimeType(MimeTypes.TEXT_SSA)
                .build()
        }
        return format
    }
}

@OptIn(UnstableApi::class)
internal fun ExtractorsFactory.withAssMkvSupportCompat(
    subtitleParserFactory: SubtitleParser.Factory,
    assHandler: AssHandler
): ExtractorsFactory {
    val delegate = this
    fun replace(extractor: Extractor): Extractor = when (extractor) {
        is HdrColorSignalingExtractor -> HdrColorSignalingExtractor(replace(extractor.delegate))
        is RewrappableExtractor -> extractor.rewrap(replace(extractor.wrappedExtractor))
        is DvMatroskaExtractor -> NuvioAssMatroskaExtractor(
            subtitleParserFactory = subtitleParserFactory,
            assHandler = assHandler,
            dolbyVisionSampleTransformer = extractor.dolbyVisionSampleTransformer
        )
        is StockMatroskaExtractor -> NuvioAssMatroskaExtractor(subtitleParserFactory, assHandler)
        else -> extractor
    }
    // Keeps the HDR wrapper, the vendored extractor's DV transformer and DTS detection, and any
    // rewrappable wrapper around the Matroska extractor.
    return object : ExtractorsFactory {
        override fun createExtractors(): Array<Extractor> =
            delegate.createExtractors().map(::replace).toTypedArray()

        override fun createExtractors(
            uri: Uri,
            responseHeaders: Map<String, List<String>>
        ): Array<Extractor> = delegate.createExtractors(uri, responseHeaders).map(::replace).toTypedArray()
    }
}
