package com.jjundev.oneclickeng.core.update

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** 강제 업데이트 게이트 seam 바인딩. [AppUpdateChecker] → [PlayAppUpdateChecker](Play Core 기반). */
@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {
    @Binds
    @Singleton
    abstract fun bindAppUpdateChecker(impl: PlayAppUpdateChecker): AppUpdateChecker
}
