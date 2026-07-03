package com.jjundev.oneclickeng.core.network

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the slim-feedback SSE seam (M1-07): the [FeedbackStream] used by the turn feedback sheet.
 * The `/llm` OkHttpClient, Json, and Retrofit it depends on are provided by [NetworkModule] (shared
 * with the dialogue stack). Mirrors [DialogueModule].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FeedbackModule {
    @Binds
    @Singleton
    abstract fun bindFeedbackStream(impl: FeedbackSseStream): FeedbackStream
}
