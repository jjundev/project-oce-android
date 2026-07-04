package com.jjundev.oneclickeng.core.di

import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Firebase 싱글톤(Auth·Firestore·Analytics)을 DI 로 제공한다(M0-02).
 *
 * 이 인스턴스들은 생성자 주입이 불가한 프레임워크 싱글톤이라 @Provides 로 노출한다.
 * FirebaseApp 초기화 자체는 google-services 플러그인의 ContentProvider 가 담당한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = Firebase.auth

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = Firebase.firestore

    @Provides
    @Singleton
    fun provideFirebaseAnalytics(): FirebaseAnalytics = Firebase.analytics

    /**
     * `mergeGuestData` 콜러블용 Functions 클라이언트(M3-03). 리전은 백엔드 배포와 반드시 일치해야 한다
     * (asia-northeast3 — `llm`·모든 함수와 동일, backend-functions.md:25). 리전 불일치는 콜러블 NOT_FOUND 로 죽는다.
     */
    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions = Firebase.functions(FUNCTIONS_REGION)

    private const val FUNCTIONS_REGION = "asia-northeast3"
}
