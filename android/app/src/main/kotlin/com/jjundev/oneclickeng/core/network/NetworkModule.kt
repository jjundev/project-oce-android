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
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    // Trailing slash required by Retrofit. Placeholder dev origin until M0-02 wires the
    // real Functions URL (asia-northeast3, backend-functions.md:25).
    private const val BASE_URL = "https://asia-northeast3-oce-dev.cloudfunctions.net/"

    // OkHttp read timeout must exceed the client-side TTS watchdog (8s, tts.md §4) so the
    // watchdog — not the socket — is the authoritative bound on synthesis latency.
    private const val READ_TIMEOUT_SECONDS = 20L

    @Provides
    @Singleton
    fun provideTokenProvider(): TokenProvider = StubTokenProvider()

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
