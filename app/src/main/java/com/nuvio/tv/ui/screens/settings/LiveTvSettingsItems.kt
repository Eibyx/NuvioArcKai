package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.tv.R
import com.nuvio.tv.reshaped.livetv.LiveTvPreferences
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * "Live TV" rows (from Nuvio Reshaped, on Kai's settings rows): shows Live TV (IPTV channel lists)
 * in the menu. Off by default: while off, nothing of Live TV loads. Under it, while Live TV is on,
 * "Channel previews" and their sound.
 */
internal fun LazyListScope.liveTvSettingsItems(
    onItemFocused: () -> Unit = {},
) {
    item(key = "live_tv_enabled") {
        val context = LocalContext.current
        LiveTvPreferences.ensureLoaded(context)
        val checked by LiveTvPreferences.enabled.collectAsStateWithLifecycle()
        val previews by LiveTvPreferences.previews.collectAsStateWithLifecycle()
        val previewSound by LiveTvPreferences.previewSound.collectAsStateWithLifecycle()

        Column(verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md)) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_live_tv_title),
                subtitle = stringResource(R.string.settings_live_tv_description),
                checked = checked,
                onToggle = { LiveTvPreferences.setEnabled(context, !checked) },
                onFocused = onItemFocused,
                leadingIcon = Icons.Default.LiveTv,
            )
            if (checked) {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_live_tv_previews_title),
                    subtitle = stringResource(R.string.settings_live_tv_previews_description),
                    checked = previews,
                    onToggle = { LiveTvPreferences.setPreviews(context, !previews) },
                    onFocused = onItemFocused,
                    leadingIcon = Icons.Default.PictureInPicture,
                )
                if (previews) {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_live_tv_preview_sound_title),
                        subtitle = stringResource(R.string.settings_live_tv_preview_sound_description),
                        checked = previewSound,
                        onToggle = { LiveTvPreferences.setPreviewSound(context, !previewSound) },
                        onFocused = onItemFocused,
                        leadingIcon = Icons.AutoMirrored.Filled.VolumeUp,
                    )
                }
            }
        }
    }
}
