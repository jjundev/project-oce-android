package com.jjundev.oneclickeng.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Adds `Authorization: Bearer <idToken>` to every `/llm` request. The token comes from
 * an injected [TokenProvider] (harness-supplied in M1). If no token is available the
 * request proceeds unauthenticated and the server responds 401 — the client surfaces a
 * retry rather than crashing. `runBlocking` is acceptable here: OkHttp interceptors run
 * on their own dispatcher thread, never the main thread.
 */
class AuthInterceptor(
    private val tokenProvider: TokenProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenProvider.idToken() }
        val request =
            if (token.isNullOrBlank()) {
                chain.request()
            } else {
                chain.request().newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            }
        return chain.proceed(request)
    }
}
