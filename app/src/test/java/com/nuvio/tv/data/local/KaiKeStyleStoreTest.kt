package com.nuvio.tv.data.local

import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.domain.model.AccentMode
import com.nuvio.tv.domain.model.GlassPreset
import com.nuvio.tv.domain.model.NavigationStyle
import com.nuvio.tv.domain.model.SettingsBackground
import com.nuvio.tv.domain.model.V2AppearancePreferences
import com.nuvio.tv.ui.v2.navigation.topNavFocusFill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaiKeStyleStoreTest {
    @Test fun `KE style uses solid glass without blur on a plain dark page`() {
        val ke = V2AppearancePreferences().withKeStyle()
        assertTrue(ke.keStyle)
        assertEquals(NavigationStyle.TOP_NAVIGATION, ke.navigationStyle)
        assertEquals(AccentMode.ADAPTIVE_ARTWORK, ke.accentMode)
        assertEquals(SettingsBackground.CHARCOAL, ke.settingsBackground)
        assertTrue("panels must not be see-through", ke.glassTransparencyPercent <= 15)
        assertEquals(0, ke.glassBlurStrengthPercent)
    }

    @Test fun `KE turned on by 1_2_1 is moved to the solid look`() {
        val saved = mutablePreferencesOf(
            booleanPreferencesKey("kai_ke_style") to true,
            stringPreferencesKey("glass_preset") to "CLEAR",
            intPreferencesKey("glass_transparency_percent") to 85,
            intPreferencesKey("glass_blur_strength_percent") to 0,
            stringPreferencesKey("settings_background") to "POSTERS"
        )
        val decoded = V2AppearancePreferenceStore.decode(saved)
        assertEquals(V2AppearancePreferences().withKeStyle().glassTransparencyPercent, decoded.glassTransparencyPercent)
        assertEquals(SettingsBackground.CHARCOAL, decoded.settingsBackground)
    }

    @Test fun `changes made after the move are kept`() {
        val saved = mutablePreferencesOf()
        val tuned = V2AppearancePreferences().withKeStyle().copy(glassTransparencyPercent = 50,
            settingsBackground = SettingsBackground.POSTERS)
        V2AppearancePreferenceStore.encode(saved, tuned)
        assertEquals(tuned, V2AppearancePreferenceStore.decode(saved))
    }

    @Test fun `KE off and old snapshots without KE are untouched`() {
        val saved = mutablePreferencesOf(
            stringPreferencesKey("glass_preset") to "CLEAR",
            intPreferencesKey("glass_transparency_percent") to 85
        )
        val decoded = V2AppearancePreferenceStore.decode(saved)
        assertEquals(GlassPreset.CLEAR, decoded.glassPreset)
        assertEquals(85, decoded.glassTransparencyPercent)
        assertEquals(SettingsBackground.POSTERS, decoded.settingsBackground)
    }

    @Test fun `a light accent gets dark text on the focused tab`() {
        val light = topNavFocusFill(Color(0xFFF2F2F2), .38f)
        assertEquals(Color.Black, light.content)
        assertEquals(1f, light.container.alpha)
        val dark = topNavFocusFill(Color(0xFF2D6CDF), .38f)
        assertEquals(Color.White, dark.content)
        assertEquals(.38f, dark.container.alpha, .01f)
    }
}
