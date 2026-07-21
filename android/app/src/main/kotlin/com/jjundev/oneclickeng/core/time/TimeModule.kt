package com.jjundev.oneclickeng.core.time

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the monotonic clock seam to its real impl (M4-01f). */
@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {
    @Binds
    @Singleton
    abstract fun bindElapsedClock(impl: SystemElapsedClock): ElapsedClock
}
