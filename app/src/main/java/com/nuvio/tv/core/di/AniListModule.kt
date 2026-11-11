package com.nuvio.tv.core.di

import com.nuvio.tv.core.tracking.TrackingLibraryProvider
import com.nuvio.tv.core.tracking.TrackingProvider
import com.nuvio.tv.data.anilist.AniListTrackingLibraryProvider
import com.nuvio.tv.data.anilist.AniListTrackingProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/**
 * NuvioArcKai: AniList as a tracking provider and library source, registered like WeTrakr. It is not a
 * TrackingHistoryWriter (completions reach it once, through WatchProgressRepositoryImpl) nor a progress source.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AniListModule {
    @Binds @IntoSet
    abstract fun trackingProvider(provider: AniListTrackingProvider): TrackingProvider

    @Binds @IntoSet
    abstract fun libraryProvider(provider: AniListTrackingLibraryProvider): TrackingLibraryProvider
}
