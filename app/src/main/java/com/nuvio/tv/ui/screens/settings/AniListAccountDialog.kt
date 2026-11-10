@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.data.anilist.AniListTvState
import com.nuvio.tv.ui.components.NuvioDialog
import com.nuvio.tv.ui.theme.NuvioTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
internal fun AniListAccountDialog(state: AniListTvState, onConnect: () -> Unit, onDisconnect: () -> Unit,
    onSync: () -> Unit, onDismiss: () -> Unit, onConnectWithPhone: () -> Unit = {}) {
    // NuvioArcKai: the Auth Pin page on the TV (pinPageUrl) or LiteOG's optional HTTPS relay (authorizationUrl).
    val qrTarget = state.pinPageUrl ?: state.authorizationUrl
    val qr by produceState<android.graphics.Bitmap?>(null, qrTarget) {
        value = qrTarget?.let { url -> withContext(Dispatchers.Default) {
            runCatching { QrCodeGenerator.generate(url, 640, margin = 4) }.getOrNull()
        } }
    }
    NuvioDialog(onDismiss = onDismiss, title = stringResource(R.string.anilist_name),
        subtitle = stringResource(R.string.anilist_description), width = 620.dp, suppressFirstKeyUp = false) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = com.nuvio.tv.ui.util.rememberRawSvgPainter(R.raw.anilist_logo, 40.dp),
                    contentDescription = stringResource(R.string.anilist_name),
                    modifier = Modifier.size(40.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                Text(if (state.connected) stringResource(R.string.anilist_connected_as, state.accountName.orEmpty())
                    else stringResource(R.string.anilist_disconnected), color = NuvioTheme.colors.TextSecondary)
            }
            if (!state.configured) Text(stringResource(R.string.anilist_missing_config))
            state.error?.let { Text(it) }
            qr?.let { bitmap ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(bitmap.asImageBitmap(), stringResource(R.string.anilist_qr_description), Modifier.size(220.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.pinPageUrl != null) {
                            Text(stringResource(R.string.kai_anilist_pin_instructions))
                            state.pinCode?.let { code ->
                                Text(stringResource(R.string.kai_anilist_pin_code, code.chunked(3).joinToString(" ")),
                                    fontSize = 30.sp, fontWeight = FontWeight.Bold, color = NuvioTheme.colors.TextPrimary)
                            }
                            Text(state.pinPageUrl, color = NuvioTheme.colors.TextTertiary)
                        } else {
                            Text(stringResource(R.string.anilist_phone_instructions))
                        }
                    }
                }
            }
            if (!state.connected && !state.pinAvailable && qr == null)
                Text(stringResource(R.string.kai_anilist_pin_unavailable), color = NuvioTheme.colors.TextTertiary)
            if (!state.connected && state.configured && !com.nuvio.tv.data.anilist.AniListConfig.usesPhonePairing)
                Text(stringResource(R.string.anilist_tv_browser_instructions), color = NuvioTheme.colors.TextSecondary)
            state.lastSyncAt?.let { Text(stringResource(R.string.anilist_last_sync,
                DateFormat.getDateTimeInstance().format(Date(it)))) }
            if (state.pending > 0) Text(stringResource(R.string.anilist_pending, state.pending))
            if (state.syncing) Text(stringResource(R.string.anilist_syncing))
        }
        SettingsDialogActionRow {
                SettingsDialogActionButton(stringResource(R.string.action_close), onDismiss)
                if (state.connected) {
                    SettingsDialogActionButton(stringResource(R.string.anilist_sync_now), onSync)
                    SettingsDialogActionButton(stringResource(R.string.anilist_disconnect), onDisconnect)
                } else if (state.authorizationUrl == null && state.webAuthorizationUrl == null && state.pinPageUrl == null) {
                    if (state.pinAvailable) {
                        SettingsDialogActionButton(stringResource(R.string.kai_anilist_connect_phone), onConnectWithPhone, primary = true)
                    }
                    if (state.configured) {
                        SettingsDialogActionButton(stringResource(R.string.kai_anilist_connect_tv), onConnect, primary = !state.pinAvailable)
                    }
                }
        }
    }
}
