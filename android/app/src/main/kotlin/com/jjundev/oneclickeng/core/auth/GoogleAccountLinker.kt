package com.jjundev.oneclickeng.core.auth

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Google 연결 결과(M3-03). 사용자에겐 Promoted/Merged 모두 "진도 저장됨 → 홈"으로 동일하게 보인다. */
sealed interface LinkOutcome {
    /** FR-3a — 신규 신원: 익명 UID 인플레이스 승격, 데이터 무이동. */
    data object Promoted : LinkOutcome

    /** FR-3b — 충돌 후 기존 계정 로그인 + mergeGuestData 이관 성공. */
    data object Merged : LinkOutcome

    /** signIn 이전 실패 — 여전히 게스트. 전체 흐름 재시도 가능. */
    data object FailedAsGuest : LinkOutcome

    /** signIn 성공 후 merge 실패 — 이미 target 인증됨. mergeGuestData 만 재시도해야 한다. */
    data object FailedAfterSignIn : LinkOutcome
}

/** 잔여 이관 마커에 대해 부트스트랩/재시도가 취할 행동(M3-03). [resolvePendingMergeAction] 결과. */
enum class PendingMergeAction {
    /** target 재로그인 확인됨 → mergeGuestData 재호출. */
    Merge,

    /** signIn 전 종료로 target 없이 게스트로 남은 무효 마커 → 능동 삭제(무한 잔존 방지, A10). */
    ClearInert,

    /** 지금은 이관 불가(잘못된 계정/미완성) → 그대로 둔다. */
    Skip
}

/**
 * 잔여 `pendingGuestMerge` 복구 결정(순수 — FirebaseAuth 없이 단위테스트). 이관은 **기록된 target 계정으로
 * 정확히 로그인된 경우에만** 수행한다(느슨한 `!isAnonymous` 는 오계정 병합 위험 — A1). `targetUid==null` 인
 * 마커는 signIn 전에 종료된 흔적이라, 게스트로 남아 있으면 정리한다(A10).
 */
@Suppress("ReturnCount")
fun resolvePendingMergeAction(
    pending: PendingMerge?,
    currentUid: String?,
    isAnonymous: Boolean,
): PendingMergeAction {
    if (pending == null) return PendingMergeAction.Skip
    val target = pending.targetUid
    if (target != null && !isAnonymous && currentUid == target) return PendingMergeAction.Merge
    if (target == null && isAnonymous) return PendingMergeAction.ClearInert
    return PendingMergeAction.Skip
}

/**
 * "Google로 계속하기" 오케스트레이터(M3-03, FR-3/3a/3b). **Context-free** — Credential Manager(Activity 필요)는
 * [GoogleCredentialProvider] 가 UI 측에서 처리하고, 여기는 raw Google ID 토큰을 받아 Firebase 링크/이관만 한다.
 *
 * FR-3a: `linkWithCredential` 성공 → 익명 UID 승격(데이터 자동 보존), 아무것도 옮기지 않는다.
 * FR-3b(`credential-already-in-use`): (a) 게스트 토큰을 **signIn 전에** 캡처·영속 → (b) `signInWithCredential`
 * 로 기존 계정 로그인 후 target UID 를 마커에 채움 → (c) `mergeGuestData` 콜러블 호출 → (d) 성공 시 마커 삭제.
 * 실제 union·삭제·멱등은 전부 서버 소유(firestore-schema.md §4.4).
 */
interface GoogleAccountLinker {
    /** raw Google ID 토큰으로 FR-3a/3b 를 수행한다. */
    suspend fun linkGuest(googleIdToken: String): LinkOutcome

    /**
     * 잔여 `pendingGuestMerge` 를 target 계정으로 재이관한다(부트스트랩 복구 + in-session 재시도 공용).
     * 마커 없음 → [LinkOutcome.Merged](할 일 없음). target 불일치/미완성 마커 → [LinkOutcome.FailedAfterSignIn].
     */
    suspend fun retryPendingMerge(): LinkOutcome
}

@Singleton
class FirebaseGoogleAccountLinker
    @Inject
    constructor(
        private val auth: FirebaseAuth,
        private val functions: FirebaseFunctions,
        private val pendingStore: PendingMergeStore,
    ) : GoogleAccountLinker {
        @Suppress("ReturnCount", "TooGenericExceptionCaught")
        override suspend fun linkGuest(googleIdToken: String): LinkOutcome {
            val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val guest = auth.currentUser ?: return LinkOutcome.FailedAsGuest
            val guestUid = guest.uid

            // FR-3a 시도: 익명 UID 인플레이스 승격.
            try {
                guest.linkWithCredential(credential).await()
                return LinkOutcome.Promoted
            } catch (e: FirebaseAuthUserCollisionException) {
                Log.d(TAG, "credential-already-in-use → FR-3b sign-in-then-migrate", e)
                // 충돌 → 아래 FR-3b 로 진행.
            } catch (e: Exception) {
                Log.w(TAG, "linkWithCredential failed (still guest)", e)
                return LinkOutcome.FailedAsGuest
            }

            // FR-3b (a): 게스트 토큰을 signIn 전에 캡처 + 영속(중도 종료 복구용).
            val guestToken =
                runCatching { guest.getIdToken(true).await().token }.getOrNull()
                    ?: return LinkOutcome.FailedAsGuest
            pendingStore.put(guestUid = guestUid, guestToken = guestToken)

            // FR-3b (b): 기존 계정으로 로그인 → target UID 마커에 채움.
            val targetUid =
                try {
                    auth.signInWithCredential(credential).await().user?.uid
                } catch (e: Exception) {
                    Log.w(TAG, "signInWithCredential failed — still guest", e)
                    pendingStore.clear()
                    return LinkOutcome.FailedAsGuest
                }
            if (targetUid == null) {
                return LinkOutcome.FailedAfterSignIn
            }
            pendingStore.setTargetUid(targetUid)

            // FR-3b (c)(d): 이관 콜러블 호출 → 성공 시 마커 삭제.
            return try {
                callMergeGuestData(guestToken)
                pendingStore.clear()
                LinkOutcome.Merged
            } catch (e: Exception) {
                Log.w(TAG, "mergeGuestData failed after signIn — pending kept for retry", e)
                LinkOutcome.FailedAfterSignIn
            }
        }

        @Suppress("TooGenericExceptionCaught")
        override suspend fun retryPendingMerge(): LinkOutcome {
            val pending = pendingStore.get() ?: return LinkOutcome.Merged
            val current = auth.currentUser
            return when (
                resolvePendingMergeAction(pending, current?.uid, current?.isAnonymous ?: true)
            ) {
                PendingMergeAction.Merge ->
                    try {
                        callMergeGuestData(pending.guestToken)
                        pendingStore.clear()
                        LinkOutcome.Merged
                    } catch (e: Exception) {
                        Log.w(TAG, "pending mergeGuestData retry failed — kept for next launch", e)
                        LinkOutcome.FailedAfterSignIn
                    }
                PendingMergeAction.ClearInert -> {
                    pendingStore.clear()
                    LinkOutcome.FailedAfterSignIn
                }
                PendingMergeAction.Skip -> LinkOutcome.FailedAfterSignIn
            }
        }

        private suspend fun callMergeGuestData(guestToken: String) {
            functions
                .getHttpsCallable("mergeGuestData")
                .call(mapOf("guestIdToken" to guestToken))
                .await()
        }

        private companion object {
            const val TAG = "GoogleAccountLinker"
        }
    }
