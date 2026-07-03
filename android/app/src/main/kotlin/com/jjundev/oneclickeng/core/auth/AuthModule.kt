package com.jjundev.oneclickeng.core.auth

import com.jjundev.oneclickeng.core.network.FirebaseTokenProvider
import com.jjundev.oneclickeng.core.network.TokenProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the guest-auth seams (M3-01): [AuthRepository], [ProfileRepository], and the real
 * [TokenProvider]. Binding [FirebaseTokenProvider] here replaces `NetworkModule`'s stub
 * binding so `/llm` requests carry the guest ID token.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: FirestoreProfileRepository): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindTokenProvider(impl: FirebaseTokenProvider): TokenProvider
}
