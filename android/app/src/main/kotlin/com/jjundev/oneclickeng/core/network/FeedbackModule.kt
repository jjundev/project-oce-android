package com.jjundev.oneclickeng.core.network

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the feedback SSE seams: the slim [FeedbackStream] (M1-07) used by the turn feedback sheet,
 * and the on-demand deep [DeepFeedbackStream] (M2-03) used by "더 보기". The `/llm` OkHttpClient, Json,
 * and Retrofit they depend on are provided by [NetworkModule] (shared with the dialogue stack).
 * Mirrors [DialogueModule].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class FeedbackModule {
    @Binds
    @Singleton
    abstract fun bindFeedbackStream(impl: FeedbackSseStream): FeedbackStream

    @Binds
    @Singleton
    abstract fun bindDeepFeedbackStream(impl: DeepFeedbackSseStream): DeepFeedbackStream
}
