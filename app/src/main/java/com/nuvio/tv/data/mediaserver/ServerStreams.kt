package com.nuvio.tv.data.mediaserver

import android.util.Log
import com.nuvio.tv.core.cloudstorage.CloudStreams
import com.nuvio.tv.data.mediaserver.mediabrowser.formatBitrate
import com.nuvio.tv.domain.model.Stream
import com.nuvio.tv.domain.model.StreamBehaviorHints
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

class ServerStreamSource(
    val name: String,
    val preferred: Boolean,
    private val loader: suspend ServerStreamSource.() -> List<Stream>
) {
    /** How many titles the last [load] matched on the server, null when it did not get that far. */
    @Volatile
    var matchedItems: Int? = null
        internal set

    suspend fun load(): List<Stream> = loader()
}

@Singleton
class ServerStreams @Inject constructor(
    private val repository: ServerRepository,
    private val matcher: ServerMatcher,
    /** NuvioArcKai cloud storage accounts; their sources are listed after the media servers'. */
    private val cloudStreams: CloudStreams? = null
) {

    val revision: Int
        get() = repository.uiState.value.let {
            4 * it.revision + (if (it.fullSourceText) 1 else 0) + (if (it.showServerLogos) 2 else 0)
        } * 31 + (cloudStreams?.revision ?: 0)

    fun isNativeRequest(videoId: String): Boolean = ServerItemRef.isServerId(videoId)

    fun canServe(type: String, videoId: String): Boolean {
        ServerItemRef.parse(videoId)?.let { ref -> return repository.connection(ref.connectionId)?.enabled == true }
        val connections = repository.enabledConnections().ifEmpty { return false }
        val request = matcher.request(type, videoId, season = null, episode = null) ?: return false
        return connections.any { matcher.supports(it, request.kind) }
    }

    fun sources(
        type: String,
        videoId: String,
        season: Int?,
        episode: Int?,
        forceRefresh: Boolean = false
    ): List<ServerStreamSource> {
        ServerItemRef.parse(videoId)?.let { ref ->
            val connection = repository.connection(ref.connectionId) ?: return emptyList()
            return listOf(source(connection, preferred = true) { matchedItems = 1; candidates(ref) })
        }
        // Cloud storage accounts (Google Drive, TeraBox): empty when none is connected; also offered with no server.
        val cloud = cloudSources(type, videoId, season, episode)
        val connections = repository.enabledConnections().ifEmpty { return cloud }
        val request = matcher.request(type, videoId, season, episode) ?: return cloud
        return connections
            .filter { matcher.supports(it, request.kind) }
            .map { connection ->
                source(connection, preferred = connection.useCatalogMetadata) {
                    suspend fun load(): List<Stream> {
                        val refs = matcher.match(connection, request, forceRefresh)
                        matchedItems = refs.size
                        return refs.flatMap { candidates(it) }
                    }
                    try {
                        load()
                    } catch (error: ServerException) {
                        if (error.serverFailure() != ServerFailure.NOT_FOUND || !matcher.forgetOrigin(connection, request)) throw error
                        load()
                    }
                }
            } + cloud
    }

    /** Cloud sources, with their logo only when server logos are shown, as for media servers. */
    private fun cloudSources(type: String, videoId: String, season: Int?, episode: Int?): List<ServerStreamSource> {
        val sources = cloudStreams?.sources(type, videoId, season, episode).orEmpty()
        if (sources.isEmpty() || repository.uiState.value.showServerLogos) return sources
        return sources.map { source ->
            ServerStreamSource(source.name, source.preferred) {
                source.load().map { it.copy(addonLogo = null) }.also { matchedItems = source.matchedItems }
            }
        }
    }

    /** One log line for each connected server that a stream search for this title leaves out. */
    fun logSkippedSources(type: String, videoId: String, season: Int?, episode: Int?) {
        if (ServerItemRef.isServerId(videoId)) return
        repository.ensureLoaded()
        val connections = repository.uiState.value.connections.ifEmpty { return }
        val request = matcher.request(type, videoId, season, episode)
        connections.forEach { connection ->
            val reason = serverSourceSkipReason(
                connection = connection,
                kind = request?.kind,
                canLookUpIds = repository.provider(connection)?.supports(ServerCapability.EXTERNAL_ID_LOOKUP) == true
            ) ?: return@forEach
            Log.i(TAG, "SERVER_SOURCE name=${repository.sourceLabel(connection)} outcome=skipped reason=$reason type=$type")
        }
    }

    fun preferredSourceNames(type: String, videoId: String): Set<String> =
        sources(type, videoId, season = null, episode = null)
            .filter { it.preferred }
            .mapTo(mutableSetOf()) { it.name }

    private fun source(
        connection: ServerConnection,
        preferred: Boolean,
        loader: suspend ServerStreamSource.() -> List<Stream>
    ): ServerStreamSource = ServerStreamSource(repository.sourceLabel(connection), preferred, loader)

    suspend fun candidates(ref: ServerItemRef): List<Stream> {
        val connection = repository.connection(ref.connectionId) ?: throw ServerException(ServerFailure.NOT_FOUND)
        val label = repository.sourceLabel(connection)
        val providerName = repository.provider(connection)?.displayName ?: connection.providerId
        val state = repository.uiState.value
        val logo = serverLogoUri(connection.providerId).takeIf { state.showServerLogos }
        val candidates = repository.call(ref.connectionId) { provider, session -> provider.candidates(session, ref.itemId) }
        return candidates.zip(serverStreamTexts(candidates, providerName, connection.shownName, state.fullSourceText))
            .map { (candidate, text) ->
                Stream(
                    name = text.name,
                    title = null,
                    description = text.description,
                    url = null,
                    ytId = null,
                    infoHash = null,
                    fileIdx = null,
                    externalUrl = null,
                    behaviorHints = StreamBehaviorHints(
                        notWebReady = null,
                        bingeGroup = null,
                        countryWhitelist = null,
                        proxyHeaders = null,
                        videoSize = candidate.sizeBytes,
                        filename = candidate.filename
                    ),
                    addonName = label,
                    addonLogo = logo,
                    serverTarget = candidate.target,
                    durationMs = candidate.durationMs
                )
            }
    }

    companion object {
        const val TAG = "ServerStreams"
    }
}

/** Why a server takes no part in a stream search, or null when it is asked. A null [kind] means the title has no ids to match on. */
internal fun serverSourceSkipReason(connection: ServerConnection, kind: ServerMediaKind?, canLookUpIds: Boolean): String? = when {
    !connection.enabled -> "turned_off"
    kind == null -> "no_title_ids"
    connection.librariesHolding(kind).isEmpty() -> "no_library"
    !canLookUpIds -> "no_id_lookup"
    else -> null
}

internal fun serverSourceOutcome(streams: Int, matchedItems: Int?, error: Throwable?, timedOut: Boolean): String = when {
    timedOut -> "timeout"
    error != null -> when {
        error.serverFailure() == ServerFailure.AUTH_REQUIRED -> "signed_out"
        error.serverFailure() == ServerFailure.UNREACHABLE ||
            error.serverFailure() == ServerFailure.CERTIFICATE ||
            (error as? ServerException)?.network == true -> "unreachable"
        else -> "failed"
    }
    streams > 0 -> "found"
    matchedItems == 0 -> "no_match"
    else -> "no_streams"
}

internal fun serverSourceLogLine(name: String, ms: Long, streams: Int, outcome: String, error: Throwable?): String =
    "SERVER_SOURCE name=$name ms=$ms streams=$streams outcome=$outcome" +
        (error?.let { " error=${it.forLog()}" } ?: "")

internal data class ServerStreamText(
    val name: String,
    val description: String?
)

internal fun serverStreamTexts(
    candidates: List<ServerCandidate>,
    providerName: String,
    serverName: String,
    full: Boolean = true
): List<ServerStreamText> {
    val names = candidates.map { candidate ->
        listOf(providerName, candidate.title).filter { it.isNotBlank() }.joinToString(" · ")
    }
    val repeated = names.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
    return candidates.mapIndexed { index, candidate ->
        val versionName = candidate.versionName?.takeIf { names[index] in repeated }
        val description = if (full) {
            serverStreamDescription(candidate, serverName, versionName)
        } else {
            cleanServerStreamDescription(candidate, serverName, versionName)
        }
        ServerStreamText(names[index], description)
    }
}

internal fun cleanServerStreamDescription(
    candidate: ServerCandidate,
    serverName: String,
    versionName: String? = null
): String? {
    val details = listOfNotNull(
        candidate.container?.takeIf { it.isNotBlank() },
        candidate.bitrateBps?.let(::formatBitrate),
        candidate.sizeBytes?.takeIf { it > 0 }?.let(::formatServerSize),
        serverName.takeIf { it.isNotBlank() },
        versionName?.takeIf { it.isNotBlank() }
    ).joinToString(" • ").ifEmpty { null }
    return listOfNotNull(
        candidate.video?.takeIf { it.isNotBlank() },
        candidate.audio?.takeIf { it.isNotBlank() },
        details
    ).joinToString("\n").ifEmpty { null }
}

internal fun serverStreamDescription(
    candidate: ServerCandidate,
    serverName: String,
    versionName: String? = null
): String? {
    val file = candidate.filename?.takeIf { it.isNotBlank() }
    val media = listOfNotNull(
        candidate.video?.takeIf { it.isNotBlank() },
        candidate.audio?.takeIf { it.isNotBlank() }
    ).joinToString(" | ").ifEmpty { null }
    val details = listOfNotNull(
        candidate.sizeBytes?.takeIf { it > 0 }?.let(::formatServerSize),
        candidate.bitrateBps?.let(::formatBitrate),
        candidate.container?.takeIf { it.isNotBlank() }
    )
    val source = listOfNotNull(
        serverName.takeIf { it.isNotBlank() },
        versionName?.takeIf { it.isNotBlank() }
    )
    val last = when {
        details.isNotEmpty() -> (details + source).joinToString(" • ")
        source.isNotEmpty() -> source.joinToString(" • ")
        else -> null
    }
    return listOfNotNull(file, media, last).joinToString("\n").ifEmpty { null }
}

internal fun formatServerSize(bytes: Long): String {
    val gib = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gib >= 1.0) {
        String.format(Locale.US, "%.1f GB", gib)
    } else {
        "${(bytes / (1024.0 * 1024.0)).roundToInt()} MB"
    }
}
