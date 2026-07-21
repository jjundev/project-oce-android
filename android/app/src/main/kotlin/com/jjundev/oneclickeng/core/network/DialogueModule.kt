package com.jjundev.oneclickeng.core.network

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the dialogue-generation seams: the SSE [DialogueStream] (M1-01), the WaitQuiz telemetry seam
 * (M1-01), and the daily-limit [LimitAnalytics] seam (M3-04) — both telemetry seams dispatch to
 * Firebase as of M4-01 (see [FirebaseWaitQuizAnalytics] / [FirebaseLimitAnalytics]).
 * The `/llm` OkHttpClient, Json, and Retrofit these depend on are provided by [NetworkModule].
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DialogueModule {
    @Binds
    @Singleton
    abstract fun bindDialogueStream(impl: DialogueSseStream): DialogueStream

    @Binds
    @Singleton
    abstract fun bindWaitQuizAnalytics(impl: FirebaseWaitQuizAnalytics): WaitQuizAnalytics

    @Binds
    @Singleton
    abstract fun bindLimitAnalytics(impl: FirebaseLimitAnalytics): LimitAnalytics
}
