package com.jjundev.oneclickeng.core.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 앱 전역(Singleton) DI 바인딩 자리.
 * M0-01 스캐폴드 단계에서는 비어 있으며, 후속 이슈에서 provides/binds 를 추가한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule
