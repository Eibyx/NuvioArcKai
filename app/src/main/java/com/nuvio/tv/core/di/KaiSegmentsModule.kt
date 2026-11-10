package com.nuvio.tv.core.di

import com.nuvio.tv.kai.arm.ArmIdClient
import com.nuvio.tv.kai.arm.ArmIdLookup
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** NuvioArcKai: key-less ARM ID mapping for skip segments when Simkl is not configured. */
@Module
@InstallIn(SingletonComponent::class)
abstract class KaiSegmentsModule {
    @Binds
    abstract fun bindArmIdLookup(client: ArmIdClient): ArmIdLookup
}
