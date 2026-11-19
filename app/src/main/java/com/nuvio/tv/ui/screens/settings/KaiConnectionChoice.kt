package com.nuvio.tv.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.core.network.StreamSweepEngine
import com.nuvio.tv.ui.theme.NuvioTheme

/**
 * Kai: after the stream test, the user picks 1, 2 or 3 simultaneous connections by hand
 * (the KE network test idea, with the choice left to the user). Each option shows what the
 * test measured for that count, and which one is the lightest that reaches twice the film's
 * bitrate (the test's own target). Choosing writes the usual Network settings through the same
 * setters as the sliders, so their memory limits still apply.
 */
internal object KaiConnectionChoice {
    const val MAX_CHOICE = 3

    /** Chunk size used when a count was not measured: 8 MB, the lightest of the test's ladder. */
    const val DEFAULT_CHUNK_MB = 8

    /** The stream test's own bar (StreamSweepEngine): twice the bitrate, with its 5% near-miss allowance. */
    private const val TARGET_MULTIPLE = 2.0
    private const val SUFFICIENCY_TOLERANCE = 0.95

    data class Option(
        val connections: Int,
        /** Chunk for this count: the fastest measured at it, else [DEFAULT_CHUNK_MB]. Unused for 1. */
        val chunkMb: Int,
        /** Measured speed, null when the test did not try this count. */
        val mbps: Double?,
        val recommended: Boolean
    )

    fun options(baselineMbps: Double?, measured: List<StreamSweepEngine.MeasuredPass>, filmMbps: Double?): List<Option> {
        val raw = (1..MAX_CHOICE).map { count ->
            if (count == 1) {
                Option(1, 0, baselineMbps?.takeIf { it.isFinite() && it > 0 }, false)
            } else {
                val best = measured.filter { it.connections == count && it.mbps.isFinite() && it.mbps > 0 }
                    .maxByOrNull { it.mbps }
                Option(count, best?.chunkMb ?: DEFAULT_CHUNK_MB, best?.mbps, false)
            }
        }
        val target = filmMbps?.takeIf { it > 0 }?.let { it * TARGET_MULTIPLE }
        val pick = if (target != null) {
            raw.firstOrNull { it.mbps != null && it.mbps >= target * SUFFICIENCY_TOLERANCE }
                ?: raw.filter { it.mbps != null }.maxByOrNull { it.mbps!! }
        } else {
            raw.filter { it.mbps != null }.maxByOrNull { it.mbps!! }
        }
        return raw.map { it.copy(recommended = pick != null && it.connections == pick.connections) }
    }

    /** The option the saved settings match, for the "in use" mark. */
    fun currentCount(useParallel: Boolean, parallelCount: Int): Int = if (useParallel) parallelCount else 1
}

@Composable
internal fun KaiConnectionChoiceRow(
    options: List<KaiConnectionChoice.Option>,
    currentCount: Int,
    onChoose: (KaiConnectionChoice.Option) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.xs)) {
        Text(
            text = stringResource(R.string.kai_connection_choice_title),
            style = MaterialTheme.typography.titleSmall,
            color = NuvioTheme.colors.TextPrimary
        )
        Text(
            text = stringResource(R.string.kai_connection_choice_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = NuvioTheme.colors.TextSecondary
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.sm)) {
            options.forEach { option ->
                val speed = option.mbps?.let { stringResource(R.string.kai_connection_choice_speed, "%.1f".format(it)) }
                    ?: stringResource(R.string.kai_connection_choice_not_measured)
                val marks = buildList {
                    if (option.recommended) add(stringResource(R.string.kai_connection_choice_recommended))
                    if (option.connections == currentCount) add(stringResource(R.string.kai_connection_choice_in_use))
                }
                val name = if (option.connections == 1) stringResource(R.string.kai_connection_choice_one)
                    else stringResource(R.string.kai_connection_choice_many, option.connections)
                SettingsChoiceChip(
                    label = (listOf(name, speed) + marks).joinToString(" · "),
                    selected = option.connections == currentCount,
                    onClick = { onChoose(option) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
