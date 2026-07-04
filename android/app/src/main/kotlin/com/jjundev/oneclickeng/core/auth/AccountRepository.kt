package com.jjundev.oneclickeng.core.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 계정 섹션 동작 seam(M3-09, FR-23, settings §8). 로그아웃·계정 삭제·게스트 판별을 소유한다. 로컬 상태 정리는
 * [LocalStateCleaner], 재부트 신호는 [AccountResetBus] 로 위임하고, 서버 파괴는 `deleteAccount` 콜러블 경유다.
 *
 * 게스트 판별은 `currentUser.isAnonymous`(익명=게스트, 링크됨=Google) — 스키마의 `sign_in_provider` 파생과
 * 동치의 클라 신호(AuthRepository.kt:13-14).
 */
interface AccountRepository {
    /** 현재 사용자가 게스트(익명)인가. 미로그인도 게스트로 간주. */
    fun isGuest(): Boolean

    /**
     * 로그아웃(로그인 사용자, §8.2). 로컬 전체 클리어 → `signOut()` → 재부트 신호(익명 재로그인→온보딩). Google
     * uid 데이터는 서버 보존(재로그인 시 복귀). `pendingGuestMerge` 는 로컬 클리어로 함께 제거(로그아웃=머지 트리거 아님).
     */
    suspend fun signOut()

    /**
     * 계정 삭제(탈퇴, §8.3). "삭제 진행 중" 플래그 set → 로컬 전체 클리어(AccountStateStore 제외) → `deleteAccount`
     * 콜러블 await → 성공 시 플래그 clear·`signOut()`·재부트 신호. 콜러블 실패는 재던진다(호출자가 에러 노출) —
     * 플래그가 남아 다음 진입 시 [completePendingDeletion] 이 멱등 재시도한다.
     */
    suspend fun deleteAccount()

    /**
     * cold-start 재개 훅(bootstrap 최상단). "삭제 진행 중" 플래그가 있으면 `deleteAccount` 콜러블을 멱등 재호출해
     * 수렴시킨다. 반환 true = 삭제가 진행/재개됨 → 부트가 게스트-머지 재개를 **건너뛰고** 신규 게스트로 라우팅해야 함
     * (삭제 중인 신원으로의 머지 방지). 절대 throw 하지 않는다(재개 실패는 플래그 유지 + 다음 실행 재시도).
     */
    suspend fun completePendingDeletion(): Boolean
}

@Singleton
class FirebaseAccountRepository
    @Inject
    constructor(
        private val auth: FirebaseAuth,
        private val functions: FirebaseFunctions,
        private val accountStateStore: AccountStateStore,
        private val localStateCleaner: LocalStateCleaner,
        private val accountResetBus: AccountResetBus,
    ) : AccountRepository {
        override fun isGuest(): Boolean = auth.currentUser?.isAnonymous ?: true

        override suspend fun signOut() {
            localStateCleaner.clearAll()
            auth.signOut()
            accountResetBus.signal()
        }

        override suspend fun deleteAccount() {
            accountStateStore.setDeleteInProgress(true)
            // Clear feature stores (incl. stale pendingGuestMerge) BEFORE the destroy; AccountStateStore
            // survives so a mid-flight kill is resumable at cold-start.
            localStateCleaner.clearAll()
            functions.getHttpsCallable(FN_DELETE_ACCOUNT).call().await()
            accountStateStore.setDeleteInProgress(false)
            auth.signOut()
            accountResetBus.signal()
        }

        @Suppress("TooGenericExceptionCaught", "ReturnCount")
        override suspend fun completePendingDeletion(): Boolean {
            if (!accountStateStore.isDeleteInProgress()) return false
            // If already signed out (delete had completed past signOut), nothing left to destroy — clear flag.
            if (auth.currentUser == null) {
                accountStateStore.setDeleteInProgress(false)
                return true
            }
            try {
                functions.getHttpsCallable(FN_DELETE_ACCOUNT).call().await()
                accountStateStore.setDeleteInProgress(false)
                auth.signOut()
            } catch (e: Exception) {
                // Keep the flag; retry next launch. Still short-circuit — never operate on a to-be-deleted id.
                Log.w(TAG, "pending deletion retry failed — kept for next launch", e)
            }
            return true
        }

        private companion object {
            const val TAG = "AccountRepository"
            const val FN_DELETE_ACCOUNT = "deleteAccount"
        }
    }
