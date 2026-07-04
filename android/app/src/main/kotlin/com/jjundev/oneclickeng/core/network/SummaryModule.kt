package com.jjundev.oneclickeng.core.network

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the summary SSE seam (M2-02): the [SummaryStream] used by the session summary screen. The
 * `/llm` OkHttpClient, Json, and Retrofit it depends on are provided by [NetworkModule] (shared with
 * the dialogue/feedback stacks). Mirrors [FeedbackModule].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SummaryModule {
    @Binds
    @Singleton
    abstract fun bindSummaryStream(impl: SummarySseStream): SummaryStream
}
