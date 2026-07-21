package com.jjundev.oneclickeng.feature.session.dialogue

import com.jjundev.oneclickeng.feature.session.dialogue.loading.LoadingMessageRepository
import com.jjundev.oneclickeng.feature.session.dialogue.loading.LoadingMessageSource
import com.jjundev.oneclickeng.feature.session.dialogue.quiz.QuizBank
import com.jjundev.oneclickeng.feature.session.dialogue.quiz.QuizBankRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the dialogue feature seams (M1-01): the asset-backed [QuizBank], loading-message asset source
 * ([LoadingMessageSource]), and the local WaitQuiz kill-switch ([LoadingQuizConfig], default-on). The SSE [com.jjundev.oneclickeng.core.network.DialogueStream]
 * and [com.jjundev.oneclickeng.core.network.WaitQuizAnalytics] seams are bound in the network layer's
 * `DialogueModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DialogueFeatureModule {
    @Binds
    @Singleton
    abstract fun bindQuizBank(impl: QuizBankRepository): QuizBank

    @Binds
    @Singleton
    abstract fun bindLoadingMessageSource(impl: LoadingMessageRepository): LoadingMessageSource

    @Binds
    @Singleton
    abstract fun bindLoadingQuizConfig(impl: DefaultLoadingQuizConfig): LoadingQuizConfig
}
