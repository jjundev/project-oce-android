package com.jjundev.oneclickeng.feature.review

import com.jjundev.oneclickeng.feature.review.data.FirestoreReviewSource
import com.jjundev.oneclickeng.feature.review.data.ReviewClock
import com.jjundev.oneclickeng.feature.review.data.ReviewSource
import com.jjundev.oneclickeng.feature.review.data.SystemReviewClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewModule {
    @Binds
    @Singleton
    abstract fun bindReviewSource(impl: FirestoreReviewSource): ReviewSource

    @Binds
    @Singleton
    abstract fun bindReviewClock(impl: SystemReviewClock): ReviewClock
}
