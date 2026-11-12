package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.logging.AppLogCategory
import com.nuvio.tv.core.logging.AppLogLine
import com.nuvio.tv.core.logging.AppLogReader
import com.nuvio.tv.core.logging.AppLogShareServer
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.core.server.DeviceIpAddress
import com.nuvio.tv.ui.components.SourceStatusFilterChip
import com.nuvio.tv.ui.screens.player.KaiPlaybackDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class LogFilter { ALL, PLAYER, SUBTITLES, CLOUD, ERRORS }

/** Ajustes → Avanzado → "Registros de la app": what the app is doing, like NuvioMobileKai's log viewer. */
@Composable
internal fun AppLogsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf<List<AppLogLine>>(emptyList()) }
    var filter by remember { mutableStateOf(LogFilter.ALL) }
    var reload by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var share by remember { mutableStateOf<Pair<String, android.graphics.Bitmap?>?>(null) }
    var server by remember { mutableStateOf<AppLogShareServer?>(null) }

    LaunchedEffect(reload) {
        loading = true
        lines = withContext(Dispatchers.IO) { AppLogReader.read() }
        loading = false
    }
    DisposableEffect(Unit) { onDispose { runCatching { server?.stop() } } }

    val shown = remember(lines, filter) {
        when (filter) {
            LogFilter.ALL -> lines
            LogFilter.ERRORS -> lines.filter { it.level == 'E' || it.level == 'W' || it.level == 'F' }
            LogFilter.PLAYER -> lines.filter { it.category == AppLogCategory.PLAYER }
            LogFilter.SUBTITLES -> lines.filter { it.category == AppLogCategory.SUBTITLES }
            LogFilter.CLOUD -> lines.filter { it.category == AppLogCategory.CLOUD }
        }
    }
    val decisions = remember(reload) { KaiPlaybackDiagnostics.lines(20) }
    val listState = rememberLazyListState()
    LaunchedEffect(shown.size) { if (shown.isNotEmpty()) listState.scrollToItem(decisions.size + shown.size) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)).padding(32.dp)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(stringResource(R.string.kai_logs_title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Text(
                    stringResource(R.string.kai_logs_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                    val filters = listOf(
                        LogFilter.ALL to R.string.kai_logs_filter_all,
                        LogFilter.PLAYER to R.string.kai_logs_filter_player,
                        LogFilter.SUBTITLES to R.string.kai_logs_filter_subtitles,
                        LogFilter.CLOUD to R.string.kai_logs_filter_cloud,
                        LogFilter.ERRORS to R.string.kai_logs_filter_errors
                    )
                    items(filters, key = { it.first.name }) { (value, label) ->
                        SourceStatusFilterChip(name = stringResource(label), isSelected = filter == value, onClick = { filter = value })
                    }
                    item(key = "refresh") {
                        SourceStatusFilterChip(name = stringResource(R.string.kai_logs_refresh), isSelected = false, onClick = { reload++ })
                    }
                    item(key = "clear") {
                        SourceStatusFilterChip(name = stringResource(R.string.kai_logs_clear), isSelected = false, onClick = {
                            AppLogReader.clear(lines)
                            KaiPlaybackDiagnostics.clear()
                            reload++
                        })
                    }
                    item(key = "phone") {
                        SourceStatusFilterChip(name = stringResource(R.string.kai_logs_phone), isSelected = share != null, onClick = {
                            if (share != null) {
                                runCatching { server?.stop() }
                                server = null
                                share = null
                            } else {
                                val ip = DeviceIpAddress.get(context)
                                val started = ip?.let {
                                    AppLogShareServer.start(text = {
                                        (KaiPlaybackDiagnostics.lines(40) + listOf("") + AppLogReader.read().map { it.text() })
                                            .joinToString("\n")
                                    })
                                }
                                if (started != null) {
                                    server = started
                                    val url = "http://$ip:${started.listeningPort}${started.path}"
                                    share = url to runCatching { QrCodeGenerator.generate(url, 360, margin = 1) }.getOrNull()
                                }
                            }
                        })
                    }
                }
                share?.let { (url, qr) ->
                    Row(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        qr?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.size(160.dp)) }
                        Text(stringResource(R.string.kai_logs_phone_hint, url), color = Color.White.copy(alpha = 0.85f))
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (decisions.isNotEmpty()) {
                        item(key = "decisions-title") {
                            Text(
                                stringResource(R.string.kai_logs_decisions),
                                color = Color.White,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(decisions.size, key = { "d$it" }) { index -> LogRow(decisions[index], Color(0xFFB3E5FC)) }
                    }
                    item(key = "log-title") {
                        Text(
                            if (loading) stringResource(R.string.kai_logs_loading)
                            else stringResource(R.string.kai_logs_count, shown.size),
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(shown.size, key = { "l$it" }) { index ->
                        val line = shown[index]
                        val color = when (line.level) {
                            'E', 'F' -> Color(0xFFFF8A80)
                            'W' -> Color(0xFFFFE082)
                            else -> Color.White.copy(alpha = 0.85f)
                        }
                        LogRow(line.text(), color)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(text: String, color: Color) {
    // Focusable, so the D-pad scrolls through the lines.
    Surface(
        onClick = {},
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.12f)
        )
    ) {
        Text(
            text = text,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            maxLines = 3,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}
