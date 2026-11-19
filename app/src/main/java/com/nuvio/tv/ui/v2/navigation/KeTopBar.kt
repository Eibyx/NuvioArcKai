package com.nuvio.tv.ui.v2.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.DrawerItem
import com.nuvio.tv.ui.v2.components.AvatarModel
import com.nuvio.tv.ui.v2.components.NuvioAvatar
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * Kai: Nuvio KE's top bar. Plain text over the picture (no glass bar, no icons): the NUVIO name,
 * the tabs with a line under the open one, and the clock and profile on the right. A soft shade
 * from the top keeps the text readable on bright artwork. Same focus wiring as the Arc bar.
 */
@Composable
@OptIn(ExperimentalTvMaterial3Api::class)
internal fun KeTopBar(
    items: List<DrawerItem>,
    selectedRoute: String?,
    profile: AvatarModel?,
    routeFocus: Map<String, FocusRequester>,
    contentFocus: FocusRequester,
    onNavFocused: (Boolean) -> Unit,
    onRouteFocused: (String) -> Unit,
    onNavigate: (String) -> Unit,
    onSwitchProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth().background(
        Brush.verticalGradient(listOf(Color.Black.copy(alpha = .72f), Color.Black.copy(alpha = .35f), Color.Transparent))
    )) {
        Row(Modifier.fillMaxWidth().padding(start = 56.dp, end = 48.dp, top = 18.dp, bottom = 22.dp)
            .onFocusChanged { onNavFocused(it.hasFocus) }.focusGroup(),
            verticalAlignment = Alignment.CenterVertically) {
            Text("NUVIO", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 4.sp)
            Spacer(Modifier.width(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                items.forEach { item ->
                    val isSelected = item.route == selectedRoute
                    var focused by remember { mutableStateOf(false) }
                    Button(onClick = { onNavigate(item.route) },
                        modifier = Modifier.focusRequester(routeFocus.getValue(item.route))
                            .focusProperties { down = contentFocus }
                            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onRouteFocused(item.route) }
                            .semantics { selected = isSelected },
                        shape = ButtonDefaults.shape(RoundedCornerShape(6.dp)),
                        scale = ButtonDefaults.scale(focusedScale = 1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = Color.White,
                            contentColor = if (isSelected) Color.White else Color.White.copy(alpha = .72f),
                            focusedContentColor = Color.Black)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(item.label, fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
                            Spacer(Modifier.height(4.dp))
                            Box(Modifier.width(22.dp).height(2.dp).background(
                                if (isSelected && !focused) Color.White else Color.Transparent, RoundedCornerShape(1.dp)))
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            KeClock()
            if (profile != null) {
                Spacer(Modifier.width(18.dp))
                Button(onSwitchProfile, Modifier.focusProperties { down = contentFocus },
                    shape = ButtonDefaults.shape(RoundedCornerShape(50)),
                    scale = ButtonDefaults.scale(focusedScale = 1.08f),
                    contentPadding = PaddingValues(2.dp),
                    colors = ButtonDefaults.colors(containerColor = Color.Transparent,
                        focusedContainerColor = Color.White)) {
                    NuvioAvatar(profile, 30.dp, description = profile.name)
                }
            }
        }
    }
}

/** The time as the TV shows it (12 or 24 h), checked every 15 s while the bar is on screen. */
@Composable
private fun KeClock() {
    val context = LocalContext.current
    val format = remember(context) { android.text.format.DateFormat.getTimeFormat(context) ?: DateFormat.getTimeInstance(DateFormat.SHORT) }
    var now by remember { mutableStateOf(format.format(Date())) }
    LaunchedEffect(format) {
        while (true) {
            now = format.format(Date())
            delay(15_000)
        }
    }
    Text(now, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelLarge)
}
