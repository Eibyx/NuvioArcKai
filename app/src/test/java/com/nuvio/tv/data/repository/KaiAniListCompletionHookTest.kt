package com.nuvio.tv.data.repository

import com.nuvio.tv.core.auth.AuthManager
import com.nuvio.tv.core.di.AniListModule
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.sync.WatchProgressSyncService
import com.nuvio.tv.core.sync.WatchStateMutationStore
import com.nuvio.tv.core.sync.WatchedItemsSyncService
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.core.tracking.TrackingHistoryWriter
import com.nuvio.tv.core.tracking.TrackingHistoryWriterRegistry
import com.nuvio.tv.core.tracking.TrackingProgressProviderRegistry
import com.nuvio.tv.data.anilist.AniListRepository
import com.nuvio.tv.data.local.LayoutPreferenceDataStore
import com.nuvio.tv.data.local.TraktSettingsDataStore
import com.nuvio.tv.data.local.WatchProgressPreferences
import com.nuvio.tv.data.local.WatchedItemsPreferences
import com.nuvio.tv.domain.model.WatchProgress
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

/** NuvioArcKai: every real completion reaches AniList exactly once, whatever the history broadcast says. */
class KaiAniListCompletionHookTest {
    private val aniList = mockk<AniListRepository>(relaxed = true)
    private val calls = mutableListOf<Pair<Int, WatchProgress>>()

    private fun repository(): WatchProgressRepositoryImpl {
        coEvery { aniList.episodeWatched(any(), any()) } answers { calls += firstArg<Int>() to secondArg<WatchProgress>() }
        val profileManager = mockk<ProfileManager>(relaxed = true) {
            every { activeProfileId } returns MutableStateFlow(1)
            every { profileSelectionRevision } returns MutableStateFlow(0L)
            every { profileHistoryGenerationChanges } returns MutableStateFlow(0L)
        }
        return WatchProgressRepositoryImpl(
            watchProgressPreferences = mockk<WatchProgressPreferences>(relaxed = true),
            traktSettingsDataStore = mockk<TraktSettingsDataStore>(relaxed = true),
            layoutPreferenceDataStore = mockk<LayoutPreferenceDataStore>(relaxed = true),
            watchProgressSyncService = mockk<WatchProgressSyncService>(relaxed = true),
            watchedItemsPreferences = mockk<WatchedItemsPreferences>(relaxed = true),
            watchedItemsSyncService = mockk<WatchedItemsSyncService>(relaxed = true),
            authManager = mockk<AuthManager>(relaxed = true) { every { isAuthenticated } returns false },
            metaRepository = mockk(relaxed = true),
            tmdbService = mockk<TmdbService>(relaxed = true),
            profileManager = profileManager,
            trackingProgressProviders = TrackingProgressProviderRegistry(emptySet()),
            trackingHistoryWriters = TrackingHistoryWriterRegistry(emptySet()),
            mutationStore = mockk<WatchStateMutationStore>(relaxed = true),
            serverWatched = mockk(relaxed = true),
            aniList = aniList
        )
    }

    private fun episode(n: Int) = WatchProgress(
        contentId = "mal:39535", contentType = "series", name = "Mushoku Tensei", poster = null, backdrop = null,
        logo = null, videoId = "mal:39535:$n", season = 1, episode = n, episodeTitle = null,
        position = 1_400_000L, duration = 1_440_000L, lastWatched = 1L
    )

    @Test fun `the player's completion (no history broadcast) is queued once`() = runBlocking {
        repository().markAsCompleted(episode(3), broadcastTrackingHistory = false)
        assertEquals(listOf(3), calls.map { it.second.episode })
        assertEquals(1, calls.single().first)
        assertEquals(100f, calls.single().second.progressPercent)
    }

    @Test fun `a completion marked by hand (broadcast) is queued once`() = runBlocking {
        repository().markAsCompleted(episode(4), broadcastTrackingHistory = true)
        assertEquals(listOf(4), calls.map { it.second.episode })
    }

    @Test fun `marking a season queues one entry per episode`() = runBlocking {
        repository().markAsCompletedBatch((1..5).map(::episode))
        assertEquals((1..5).toList(), calls.map { it.second.episode })
    }

    @Test fun `repeating the end reaches the queue again, where the key keeps a single send`() = runBlocking {
        val repository = repository()
        repository.markAsCompleted(episode(6), broadcastTrackingHistory = false)
        repository.markAsCompleted(episode(6), broadcastTrackingHistory = false)
        // Two events for the same episode; AniListSendQueue keeps one pending send per anime/season key and
        // skips an episode already delivered (AniListSendQueueTest.anEpisodeAlreadyDeliveredIsNotSentAgainUntilUnmarked,
        // onlyTheHighestPendingEpisodePerAnimeIsKept).
        assertEquals(2, calls.size)
        assertEquals(1, calls.map { it.second.videoId }.distinct().size)
    }

    @Test fun `an AniList failure does not break the completion, cancellation still propagates`() = runBlocking {
        val repository = repository()
        coEvery { aniList.episodeWatched(any(), any()) } throws IllegalStateException("disk full")
        repository.markAsCompleted(episode(7), broadcastTrackingHistory = false)
        coEvery { aniList.episodeWatched(any(), any()) } throws CancellationException("left the screen")
        try {
            repository.markAsCompleted(episode(8), broadcastTrackingHistory = false)
            fail("CancellationException was swallowed")
        } catch (_: CancellationException) {
        }
        coVerify(exactly = 2) { aniList.episodeWatched(any(), any()) }
    }

    @Test fun `AniList is not registered as a history writer, so no second path sends to it`() {
        val bound = AniListModule::class.java.declaredMethods.map { it.returnType }
        assertFalse(TrackingHistoryWriter::class.java in bound)
    }
}
