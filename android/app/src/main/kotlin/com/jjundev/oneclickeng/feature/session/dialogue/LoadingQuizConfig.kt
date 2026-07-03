package com.jjundev.oneclickeng.feature.session.dialogue

import javax.inject.Inject

/**
 * Local kill-switch seam for the WaitQuiz loading interstitial (M1-01, loading-quiz-interstitial.md
 * §7). The remote `config.features.loadingQuiz` wiring is deliberately deferred (new config-read
 * infra); this is the default-on seam the screen checks so the quiz can be turned off at a single
 * point once remote config lands. [DefaultLoadingQuizConfig] returns `true` (v1 ships the quiz on).
 */
interface LoadingQuizConfig {
    val loadingQuizEnabled: Boolean
}

/** Default binding: quiz on (v1 immediate ship, loading-quiz-interstitial.md §7). */
class DefaultLoadingQuizConfig
    @Inject
    constructor() : LoadingQuizConfig {
        override val loadingQuizEnabled: Boolean = true
    }
