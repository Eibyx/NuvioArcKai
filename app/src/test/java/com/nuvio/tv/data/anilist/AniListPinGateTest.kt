package com.nuvio.tv.data.anilist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AniListPinGateTest {
    private val token = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxIn0.c2lnbmF0dXJlLXBhcnQ"
    private var now = 0L
    private fun gate() = AniListPinGate(code = "123456", now = { now })

    @Test fun `right code and token are accepted once`() {
        val gate = gate()
        assertEquals(AniListPinGate.Result.Accepted(token), gate.submit("123 456", " $token\n"))
        assertEquals(AniListPinGate.Result.Closed, gate.submit("123456", token))
        assertFalse(gate.isOpen())
    }

    @Test fun `five wrong codes close the page`() {
        val gate = gate()
        repeat(4) { assertEquals(AniListPinGate.Result.WrongCode, gate.submit("000000", token)) }
        assertEquals(AniListPinGate.Result.Closed, gate.submit("000000", token))
        assertEquals(AniListPinGate.Result.Closed, gate.submit("123456", token))
    }

    @Test fun `the page expires after ten minutes`() {
        val gate = gate()
        now = 10 * 60_000L
        assertEquals(AniListPinGate.Result.Closed, gate.submit("123456", token))
    }

    @Test fun `an invalid token keeps the page open and is not accepted`() {
        val gate = gate()
        assertEquals(AniListPinGate.Result.InvalidToken, gate.submit("123456", "not a token"))
        assertEquals(AniListPinGate.Result.InvalidToken, gate.submit("123456", "short.a.b"))
        assertTrue(gate.isOpen())
        assertEquals(AniListPinGate.Result.Accepted(token), gate.submit("123456", token))
    }

    @Test fun `only private IPv4 callers are served`() {
        assertTrue(AniListPinPairing.isPrivateCaller("192.168.1.20"))
        assertTrue(AniListPinPairing.isPrivateCaller("10.0.0.4"))
        assertFalse(AniListPinPairing.isPrivateCaller("8.8.8.8"))
        assertFalse(AniListPinPairing.isPrivateCaller("fe80::1"))
        assertFalse(AniListPinPairing.isPrivateCaller(null))
    }

    @Test fun `the phone page has no scripts and links to AniList's own authorize page`() {
        val html = AniListPinPage.form(AniListConfig.pinAuthorizeUrl("12345"), null)
        assertFalse(html.contains("<script", ignoreCase = true))
        assertTrue(html.contains("https://anilist.co/api/v2/oauth/authorize?client_id=12345&amp;response_type=token"))
    }

    @Test fun `Mobile's client is never used for the PIN flow`() {
        assertTrue(AniListConfig.pinClientId != AniListConfig.MOBILE_CLIENT_ID)
        assertEquals(AniListConfig.MOBILE_CLIENT_ID, AniListConfig.clientId)
        assertEquals("https://anilist.co/api/v2/oauth/pin", AniListConfig.PIN_REDIRECT_URI)
    }
}
