package com.jjundev.oneclickeng.core.di

import com.jjundev.oneclickeng.core.audio.AudioRecordRecordingController
import com.jjundev.oneclickeng.core.audio.RecordingController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 오디오 캡처 계층 DI 바인딩(M1-04).
 * 기존 [AppModule] 과 같은 패키지에 두어 DI 를 한 곳에 모은다.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {
    @Binds
    @Singleton
    abstract fun bindRecordingController(impl: AudioRecordRecordingController): RecordingController
}
