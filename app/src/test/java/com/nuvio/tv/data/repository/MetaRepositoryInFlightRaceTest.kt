package com.nuvio.tv.data.repository

import android.content.Context
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.data.remote.api.AddonApi
import com.nuvio.tv.data.remote.dto.MetaDto
import com.nuvio.tv.data.remote.dto.MetaResponseDto
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonResource
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.repository.AddonRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Kai: a lookup that fails at once must not stay "in flight". Started eagerly, it could remove its
 * entry before it was stored, so every later request joined the old failure. The race depends on
 * timing, so it is run many times; it failed on GitHub's runners every time before the fix.
 */
class MetaRepositoryInFlightRaceTest {
    private val contentId = "tt0944947"

    @Test
    fun `a lookup that failed at once is asked again next time`() = runBlocking {
        repeat(300) { round ->
            val calls = AtomicInteger()
            val api = mockk<AddonApi>()
            coEvery { api.getMeta(any()) } coAnswers {
                if (calls.incrementAndGet() == 1) throw IOException("offline")
                Response.success(MetaResponseDto(meta = MetaDto(id = contentId, type = "movie", name = "Recovered")))
            }
            val repository = newRepository(api)
            repository.getMetaFromAllAddons("movie", contentId).last()
            val second = repository.getMetaFromAllAddons("movie", contentId).last()
            assertTrue("round $round: the second lookup joined the first one's failure", second is NetworkResult.Success)
        }
    }

    private fun newRepository(api: AddonApi): MetaRepositoryImpl {
        val context = mockk<Context>(relaxed = true) {
            every { getString(any()) } returns "Episode"
            every { getString(any(), *anyVararg()) } returns "No supported addon"
        }
        val addon = Addon(
            id = "test.addon.a", name = "Test Addon a", version = "1.0.0", description = null, logo = null,
            baseUrl = "https://a.addon.example", catalogs = emptyList(),
            types = listOf(ContentType.MOVIE, ContentType.SERIES), rawTypes = listOf("movie", "series"),
            resources = listOf(AddonResource(name = "meta", types = listOf("movie", "series"), idPrefixes = null)),
            idPrefixes = listOf("tt")
        )
        val addonRepository = mockk<AddonRepository>(relaxed = true) {
            every { getInstalledAddons() } returns flowOf(listOf(addon))
        }
        return MetaRepositoryImpl(context = context, api = api, addonRepository = addonRepository, healthStore = mockk(relaxed = true), serverCatalog = mockk(relaxed = true))
    }
}
