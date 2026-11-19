package com.nuvio.tv.ui.screens.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.util.localizeEpisodeTitle

/**
 * Kai: Nuvio KE's detail header for a show. The episode in focus (else the next to watch) takes the
 * top of the page: "EPISODE 1" in the accent colour, its title large, date and length, and its
 * synopsis, over the show's backdrop. Crossfades as focus moves along the row.
 */
@Composable
internal fun KeEpisodeHeader(episode: Video?, startInset: Dp, modifier: Modifier = Modifier) {
    if (episode == null) return
    val context = LocalContext.current
    val shadow = remember { Shadow(color = Color.Black.copy(alpha = 0.8f), offset = Offset(0f, 2f), blurRadius = 6f) }
    AnimatedContent(
        targetState = episode,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it.id },
        label = "keEpisodeHeader",
        modifier = modifier.fillMaxWidth().padding(start = startInset, end = 96.dp, bottom = 12.dp)
    ) { shown ->
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val number = shown.episode
            if (number != null) {
                Text(
                    text = stringResource(R.string.kai_ke_episode_eyebrow, number).uppercase(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing * 1.5f, shadow = shadow),
                    color = NuvioTheme.colors.Secondary
                )
            }
            Text(
                text = shown.title.localizeEpisodeTitle(context),
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold, shadow = shadow),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val facts = listOfNotNull(
                shown.runtime?.takeIf { it > 0 }?.let { "${it}m" },
                shown.released?.let(::formatEpisodeCardDate)?.takeIf { it.isNotBlank() }
            ).joinToString("  •  ")
            if (facts.isNotBlank()) {
                Text(text = facts, style = MaterialTheme.typography.bodyLarge.copy(shadow = shadow), color = Color.White.copy(alpha = 0.85f))
            }
            shown.overview?.trim()?.takeIf { it.isNotEmpty() }?.let { overview ->
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyLarge.copy(shadow = shadow),
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.62f)
                )
            }
        }
    }
}
