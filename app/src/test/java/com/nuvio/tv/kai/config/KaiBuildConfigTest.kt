package com.nuvio.tv.kai.config

import android.content.Context
import android.content.SharedPreferences
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.local.ServerConfigurationStore
import io.mockk.every
import io.mockk.mockk
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * NuvioArcKai: without NUVIO_SUPABASE_* in local.properties the build (debug and release) carries Nuvio's public
 * Supabase project, the defaults NuvioMobileKai uses, and TV sign-in talks to it. Runs in testFullDebugUnitTest and,
 * in the release workflow, in testFullReleaseUnitTest.
 */
class KaiBuildConfigTest {
    @Test
    fun `the build carries Nuvio's public Supabase project`() {
        assertEquals("https://api.nuvio.tv", BuildConfig.SUPABASE_URL)
        assertEquals("https://api-two.nuvioapp.space", BuildConfig.SUPABASE_FALLBACK_URL)
        val parts = BuildConfig.SUPABASE_ANON_KEY.split('.')
        assertEquals(3, parts.size)
        val payload = String(Base64.getUrlDecoder().decode(parts[1]))
        // The public client key only: never a service role.
        assertTrue(payload, payload.contains("\"role\":\"anon\""))
        assertFalse(payload.contains("service_role"))
    }

    @Test
    fun `TV sign-in uses that server and the nuvio tv login page`() {
        val preferences = mockk<SharedPreferences>(relaxed = true) {
            every { getBoolean(any(), any()) } returns false
        }
        val context = mockk<Context> { every { getSharedPreferences(any(), any()) } returns preferences }
        val active = ServerConfigurationStore(context).loadActive()
        assertFalse(active.isCustom)
        assertEquals("https://api.nuvio.tv", active.backendUrl)
        assertEquals(BuildConfig.SUPABASE_ANON_KEY, active.publishableKey)
        assertEquals("https://api-two.nuvioapp.space", active.fallbackBackendUrl)
        assertEquals("https://nuvio.tv/tv-login", active.tvLoginWebBaseUrl)
        assertTrue(active.capabilities.tvLogin)
    }
}
