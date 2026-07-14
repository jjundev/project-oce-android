package com.jjundev.oneclickeng.feature.session.summary

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.jjundev.oneclickeng.core.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 완주 XP 원장 seam(M2-02, plan v3 #20 확정). 정본(dialogue-learning-flow.md:210·gamification-emphasis.md
 * §4.1·firestore-schema.md §4.2)은 완주 적립 = "요약 라우트 진입 시점" 으로 정의하고, `point_ledger/{sessionId}`
 * **create** 를 클라→Firestore 직접(프록시 비경유, 규칙은 만료 미검사) 수행한다(backend-functions.md:101).
 *
 * **범위 경계:** M2-02 는 진입 시 create **시도**만 소유한다. XP/streak 값으로의 **집계**(`onCreate`
 * 트리거)는 M3 게임화 소관이다 — 이 seam 은 원장에 불변 마커를 남길 뿐 적립 스트립 값을 산출하지 않는다
 * ([AccrualStrip] 는 별도 주입 seam).
 */
interface CompletionLedger {
    /**
     * 요약 진입 시 `point_ledger/{sessionId}` create 를 시도한다. 멱등: 문서는 create-only 불변이라(규칙)
     * 재진입/재시도로 이미 존재하면 write 가 거부되며 이를 무해하게 삼킨다 — 게이트·원장이 같은 sessionId
     * 키를 공유하므로 세션당 정확히 한 번만 적립된다(firestore-schema.md:140·144).
     *
     * @param difficulty 규칙이 enum 검증(`easy|normal|hard`).
     * @param modeId 학습 모드 id(집계 트리거가 day-key/XP 산출에 사용, M3).
     */
    fun recordCompletion(
        sessionId: String,
        difficulty: String,
        modeId: String,
    )
}

/**
 * [FirebaseFirestore] 백엔드 구현. `users/{uid}/point_ledger/{sessionId}` 경로에 create-only set 을
 * 앱 스코프에서 fire-and-forget 한다. `awardedAt` 은 서버시각 강제(streak 스푸핑 차단, firestore.rules:26).
 * 미인증/오프라인/이미-존재는 모두 비치명적이라 로깅 후 삼킨다(완주 적립은 UI 진행을 막지 않는다).
 */
@Singleton
class FirestoreCompletionLedger
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val authRepository: AuthRepository,
        private val scope: CoroutineScope,
    ) : CompletionLedger {
        // 완주 적립은 UI 진행을 막지 않는 fire-and-forget 이라 어떤 실패든(권한 거부·오프라인·이미 존재)
        // 동일하게 비치명적으로 삼킨다 — 세분 분기가 무의미하므로 광범위 catch 를 의도적으로 허용한다.
        @Suppress("TooGenericExceptionCaught")
        override fun recordCompletion(
            sessionId: String,
            difficulty: String,
            modeId: String,
        ) {
            val uid = authRepository.currentUid ?: return
            scope.launch {
                try {
                    firestore
                        .collection(USERS).document(uid)
                        .collection(POINT_LEDGER).document(sessionId)
                        .set(
                            mapOf(
                                FIELD_DIFFICULTY to difficulty,
                                FIELD_MODE_ID to modeId,
                                FIELD_AWARDED_AT to FieldValue.serverTimestamp(),
                            ),
                        ).await()
                } catch (e: FirebaseFirestoreException) {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        // 규칙 거부(예: 5티어 이관 창에서 신토큰 미허용) — XP 미적립으로 이어지므로 가시화.
                        Log.w(TAG, "point_ledger create denied — XP not accrued: ${e.message}")
                    } else {
                        Log.d(TAG, "point_ledger create skipped (idempotent/offline): ${e.message}")
                    }
                } catch (e: Exception) {
                    // create-only 불변: 재진입 시 이미 존재하면 update 로 간주돼 규칙이 거부한다. 오프라인·
                    // 미인증도 동일하게 비치명적 — 적립은 다음 진입/트리거에서 자연 복구된다(멱등).
                    Log.d(TAG, "point_ledger create skipped (idempotent/offline): ${e.message}")
                }
            }
        }

        private companion object {
            const val TAG = "CompletionLedger"
            const val USERS = "users"
            const val POINT_LEDGER = "point_ledger"
            const val FIELD_DIFFICULTY = "difficulty"
            const val FIELD_MODE_ID = "modeId"
            const val FIELD_AWARDED_AT = "awardedAt"
        }
    }
