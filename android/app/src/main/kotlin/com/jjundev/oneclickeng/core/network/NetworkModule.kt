package com.jjundev.oneclickeng.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * DI for the `/llm` network stack (M1-05). The base URL is the deployed Functions
 * origin; in M1 it points at the dev/emulator target and is swapped per environment.
 * TODO(M0-02): source BASE_URL from the Firebase project config once wired.
 *
 * The [TokenProvider] binding lives in `AuthModule` (M3-01): the real
 * `FirebaseTokenProvider` replaced the earlier stub. [provideOkHttpClient] still injects
 * the bound [TokenProvider].
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    // Trailing slash required by Retrofit. Deployed Functions origin for the `oce-v1`
    // project (asia-northeast3, backend-functions.md:25). The earlier `oce-dev` value was a
    // placeholder pointing at a non-existent project, so every `/llm` call failed (onboarding's
    // first dialogue surfaced "불러오지 못했어요"). TODO(M0-02): source this from Firebase project config.
    private const val BASE_URL = "https://asia-northeast3-oce-v1.cloudfunctions.net/"

    // OkHttp read timeout must exceed the client-side TTS watchdog (8s, tts.md §4) so the
    // watchdog — not the socket — is the authoritative bound on synthesis latency.
    private const val READ_TIMEOUT_SECONDS = 20L

    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(tokenProvider: TokenProvider): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenProvider))
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                },
            )
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        client: OkHttpClient,
        json: Json,
    ): Retrofit =
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideLlmApi(retrofit: Retrofit): LlmApi = retrofit.create(LlmApi::class.java)
}
