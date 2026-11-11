package com.nuvio.tv.kai.config

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.calendar.TvdbCalendarClient
import com.nuvio.tv.data.mdblist.MdbListAuthError
import com.nuvio.tv.data.mdblist.MdbListAuthException
import com.nuvio.tv.data.mdblist.MdbListAuthRepository
import com.nuvio.tv.data.mdblist.MdbListConfiguration
import com.nuvio.tv.data.simkl.SimklApiConfiguration
import com.nuvio.tv.data.simkl.SimklAuthError
import com.nuvio.tv.data.simkl.SimklAuthException
import com.nuvio.tv.data.simkl.SimklAuthRepository
import com.nuvio.tv.data.wetrakr.WeTrakrAuthError
import com.nuvio.tv.data.wetrakr.WeTrakrAuthException
import com.nuvio.tv.data.wetrakr.WeTrakrAuthRepository
import com.nuvio.tv.data.wetrakr.WeTrakrConfiguration
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * NuvioArcKai ships without Trakt, Simkl, MDBList (OAuth), WeTrakr, TheTVDB and IntroDB keys. Each integration must
 * report itself as not configured and refuse to start, without a network call and without crashing.
 */
class KaiMissingServiceKeysTest {

    @Test fun `Simkl without a client ID is off and never calls the network`() = runBlocking {
        val api = mockk<com.nuvio.tv.data.simkl.SimklApiClient>()
        val storage = mockk<com.nuvio.tv.data.simkl.SimklAuthStorage>(relaxed = true) {
            every { state } returns MutableStateFlow(mockk(relaxed = true))
        }
        val repository = SimklAuthRepository(api, SimklApiConfiguration(clientId = "", appName = "nuvio", appVersion = "1"), storage)
        assertFalse(repository.hasRequiredCredentials())
        try { repository.startPinAuth(); fail("started without a client ID") }
        catch (error: SimklAuthException) { assertEquals(SimklAuthError.MISSING_CLIENT_ID, error.error) }
        confirmVerified(api)
    }

    @Test fun `MDBList sign-in without a client ID is off and never calls the network`() = runBlocking {
        val http = mockk<com.nuvio.tv.data.mdblist.MdbListHttpClient>()
        val store = mockk<com.nuvio.tv.data.mdblist.MdbListAuthStore>(relaxed = true)
        val repository = MdbListAuthRepository(http, MdbListConfiguration(clientId = "", appVersion = "1"), store)
        assertFalse(repository.hasRequiredCredentials())
        try { repository.startDeviceAuthorization(mockk(relaxed = true)); fail("started without a client ID") }
        catch (error: MdbListAuthException) { assertEquals(MdbListAuthError.MISSING_CLIENT_ID, error.error) }
        confirmVerified(http)
    }

    @Test fun `WeTrakr without a client ID is off and never calls the network`() = runBlocking {
        val http = mockk<com.nuvio.tv.data.wetrakr.WeTrakrHttpClient>()
        val store = mockk<com.nuvio.tv.data.wetrakr.WeTrakrAuthStore>(relaxed = true)
        val repository = WeTrakrAuthRepository(http, WeTrakrConfiguration(clientId = "", appVersion = "1"), store)
        assertFalse(repository.hasRequiredCredentials())
        try { repository.startDeviceAuthorization(mockk(relaxed = true)); fail("started without a client ID") }
        catch (error: WeTrakrAuthException) { assertEquals(WeTrakrAuthError.MISSING_CLIENT_ID, error.error) }
        confirmVerified(http)
    }

    @Test fun `TheTVDB calendar without a key is off and asks nothing`() = runBlocking {
        val api = mockk<com.nuvio.tv.data.remote.api.TvdbApi>()
        val client = TvdbCalendarClient(api, "", mockk(relaxed = true), mockk(relaxed = true)) { 0L }
        assertFalse(client.enabled)
        assertNull(client.airs(mockk(relaxed = true)))
        confirmVerified(api)
    }

    @Test fun `Trakt without client ID and secret is off and never calls the network`() = runBlocking {
        assumeTrue("Trakt keys are configured in this build", BuildConfig.TRAKT_CLIENT_ID.isBlank() || BuildConfig.TRAKT_CLIENT_SECRET.isBlank())
        val api = mockk<com.nuvio.tv.data.remote.api.TraktApi>()
        val context = mockk<android.content.Context> { every { getString(any()) } returns "Trakt is not configured" }
        val service = com.nuvio.tv.data.repository.TraktAuthService(context, api, mockk(relaxed = true), mockk(relaxed = true))
        assertFalse(service.hasRequiredCredentials())
        assertTrue(service.startDeviceAuth().isFailure)
        confirmVerified(api)
    }

    @Test fun `IntroDB without a URL is skipped, other skip sources still answer`() = runBlocking {
        assumeTrue("IntroDB is configured in this build", BuildConfig.INTRODB_API_URL.isBlank())
        val introDb = mockk<com.nuvio.tv.data.remote.api.IntroDbApi>()
        val settings = mockk<com.nuvio.tv.data.local.AnimeSkipSettingsDataStore> {
            io.mockk.coEvery { snapshot() } returns com.nuvio.tv.data.local.AnimeSkipSettingsSnapshot(1, false, "")
        }
        val repository = com.nuvio.tv.data.repository.SkipIntroRepository(
            introDb, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), settings, mockk(relaxed = true)
        )
        assertTrue(repository.getMovieSkipIntervals("tt0111161").isEmpty())
        confirmVerified(introDb)
    }
}
