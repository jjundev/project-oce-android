package com.jjundev.oneclickeng.core.network

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the dialogue-generation seams (M1-01): the SSE [DialogueStream] and the WaitQuiz telemetry
 * seam (no-op until M4-01). The `/llm` OkHttpClient, Json, and Retrofit these depend on are provided
 * by [NetworkModule].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DialogueModule {
    @Binds
    @Singleton
    abstract fun bindDialogueStream(impl: DialogueSseStream): DialogueStream

    @Binds
    @Singleton
    abstract fun bindWaitQuizAnalytics(impl: NoOpWaitQuizAnalytics): WaitQuizAnalytics
}
