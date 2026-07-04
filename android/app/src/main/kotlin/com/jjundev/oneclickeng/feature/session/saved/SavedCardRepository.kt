package com.jjundev.oneclickeng.feature.session.saved

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.jjundev.oneclickeng.core.auth.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 저장 카드 쓰기 계층(M2-04). 결정적 `cardId`([SavedCardId])로 `users/{uid}/saved_cards/{cardId}` 에 union
 * write 하고, 삭제는 `deletedAt` 톰스톤으로 처리한다(하드삭제 없음 — 규칙이 `delete:false`, firestore.rules).
 * [com.jjundev.oneclickeng.feature.session.summary.FirestoreCompletionLedger] 의 fire-and-forget seam 패턴을
 * 미러한다(firestore + AuthRepository + appScope, 예외 삼킴).
 */
interface SavedCardRepository {
    /**
     * 카드 저장(create 또는 revive). 결과는 멱등: 같은 [cardId] 를 다시 저장해도 같은 문서로 수렴한다.
     * 문서 부재 → 전체 create(`createdAt=serverTimestamp`, `deletedAt=null`). 문서 존재 → content + `deletedAt=null`
     * merge(revive/refresh) — **`createdAt` 은 절대 재기록하지 않아** 원본 정렬 위치를 보존한다(saved-cards.md:57-58).
     */
    fun save(
        cardId: String,
        card: SavedCard,
    )

    /**
     * 톰스톤 삭제([deleted]=true) 또는 되살리기([deleted]=false, undo/revive). `deletedAt` 만 갱신하고 `createdAt`
     * 은 건드리지 않는다. [cardType] 을 페이로드에 실어 update 규칙(`request.resource.data.cardType in [...]`,
     * firestore.rules:17-18)을 통과시킨다. [deleted]=true 인데 문서가 없으면 no-op(미생성 doc 에 톰스톤 merge 시
     * partial-create 가 create 규칙 `deletedAt==null` 에 거부되는 것을 회피).
     */
    fun setDeleted(
        cardId: String,
        cardType: CardType,
        deleted: Boolean,
    )
}

/**
 * [FirebaseFirestore] 백엔드. create/revive 분기는 **로컬 캐시 존재 확인**으로 한다(선행 read 이되 트랜잭션·
 * 네트워크 왕복이 아님):
 * - **오프라인 안전:** Firestore 트랜잭션은 오프라인에서 동작하지 않지만(saved_cards 는 오프라인 저장이 요건
 *   — ADR-0002), 로컬 캐시 `get(Source.CACHE)` 는 오프라인에서 동작하고 Android 기본 디스크 영속성으로
 *   프로세스 사망 후에도 생존한다(FirebaseModule 이 영속성을 끄지 않음 — 기본 on). 이 가정은 정적으로 증명
 *   불가한 SDK 기본 동작이라 계측 테스트로 검증한다(ProfileRepository.readLevel 이 동일 동작에 의존).
 * - **createdAt 보존 ≠ 존재 확인:** createdAt 보존 불변식은 **페이로드 규율**(revive 경로가 createdAt 미포함)로
 *   지켜지고, 캐시 read 는 오직 create/revive **분기 판정**에만 쓴다 — 두 관심사는 직교한다.
 * - **잔여 TOCTOU:** 같은 cardId 동시 이중 저장이 둘 다 create 분기를 타면 둘 다 createdAt=serverTs 를 써
 *   last-write-wins 로 미미한 정렬 jitter 만 남는다(결정적 cardId=같은 문서, 데이터 손상 아님). 온라인 앱-시작
 *   경로인 `ProfileRepository.ensureProfile` 의 트랜잭션과 달리, 오프라인 필수라 트랜잭션을 쓰지 않는다.
 */
@Singleton
class FirestoreSavedCardRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val authRepository: AuthRepository,
        private val scope: CoroutineScope,
    ) : SavedCardRepository {
        @Suppress("TooGenericExceptionCaught") // fire-and-forget: 권한·오프라인·경합 모두 비치명적으로 삼킨다.
        override fun save(
            cardId: String,
            card: SavedCard,
        ) {
            val uid = authRepository.currentUid ?: return
            scope.launch {
                try {
                    val ref = docRef(uid, cardId)
                    if (exists(ref)) {
                        // revive/refresh: createdAt 미포함(정렬 보존), deletedAt=null 로 되살림.
                        ref.set(SavedCardPayload.revive(card), SetOptions.merge()).await()
                    } else {
                        // create: 전체 페이로드 + 서버시각 createdAt.
                        ref.set(SavedCardPayload.create(card, FieldValue.serverTimestamp())).await()
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "saved_card save skipped (offline/permission): ${e.message}")
                }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        override fun setDeleted(
            cardId: String,
            cardType: CardType,
            deleted: Boolean,
        ) {
            val uid = authRepository.currentUid ?: return
            scope.launch {
                try {
                    val ref = docRef(uid, cardId)
                    // 톰스톤은 기존 문서에만 — 미생성 doc 에 대한 삭제는 무의미(partial-create 거부 회피).
                    if (deleted && !exists(ref)) return@launch
                    val deletedAt = if (deleted) FieldValue.serverTimestamp() else null
                    ref.set(SavedCardPayload.tombstone(cardType, deletedAt), SetOptions.merge()).await()
                } catch (e: Exception) {
                    Log.d(TAG, "saved_card setDeleted skipped (offline/permission): ${e.message}")
                }
            }
        }

        private fun docRef(
            uid: String,
            cardId: String,
        ): DocumentReference =
            firestore
                .collection(USERS).document(uid)
                .collection(SAVED_CARDS).document(cardId)

        /**
         * 로컬 캐시 우선 존재 확인. 캐시에 없으면(관측 이력 없음) 서버로 폴백 — 온라인이면 서버 진실, 오프라인이면
         * 실패해 false(=create 분기). 큐된 오프라인 create 는 캐시에 반영되므로 후속 재저장/언세이브가 올바로
         * revive 분기를 탄다. `runCatching` 으로 캐시 미스/오프라인을 삼킨다(양쪽 다 비치명적, 실패=미존재로 간주).
         */
        private suspend fun exists(ref: DocumentReference): Boolean =
            runCatching { ref.get(Source.CACHE).await().exists() }
                .recoverCatching { ref.get().await().exists() }
                .getOrDefault(false)

        private companion object {
            const val TAG = "SavedCardRepository"
            const val USERS = "users"
            const val SAVED_CARDS = "saved_cards"
        }
    }
