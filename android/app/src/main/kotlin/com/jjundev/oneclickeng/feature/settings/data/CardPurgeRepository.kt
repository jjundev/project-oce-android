package com.jjundev.oneclickeng.feature.settings.data

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.feature.session.saved.CardType
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** 저장 카드 정리 범위(FR-22, settings-data-account.md §7.2). 프리셋 30/90일 이전 · 전체. */
enum class PurgeScope {
    /** createdAt 이 30일보다 오래된 카드. */
    LAST_30_DAYS,

    /** createdAt 이 90일보다 오래된 카드. */
    LAST_90_DAYS,

    /** 활성 카드 전체. */
    ALL,
}

/**
 * 저장 카드 일괄 정리 seam(M3-09, FR-22). 대상(`deletedAt==null` 활성 카드 중 범위 조건)에 `deletedAt` 톰스톤을
 * **일괄 update** 한다(하드삭제 금지 — 규칙 `delete:false`, firestore.rules:223). SavedCardRepository.setDeleted 는
 * 단건 전용이라 부적합.
 *
 * **쿼리:** `saved_cards where deletedAt==null` (단일 필드, auto-index) 만 서버에 걸고, 30/90일 `createdAt` 컷오프는
 * **클라이언트에서 필터**한다 — `(deletedAt, createdAt)` 복합 인덱스 신설을 피한다(결과셋은 현실 카드수로 bounded).
 *
 * **멱등 재시도:** 배치는 WriteBatch ≤500건 청킹으로 **순차 await**. 중간 실패 후 재실행하면 이미 `deletedAt` 이 set 된
 * 카드는 `deletedAt==null` 필터에서 빠지므로 남은 대상만 다시 처리해 안전하게 수렴한다.
 */
interface CardPurgeRepository {
    /** [scope] 에 해당하는 활성 카드 수(확인 다이얼로그 카피용). 실행 직전 재쿼리로 카피와 실제 배치를 일치시킨다. */
    suspend fun count(scope: PurgeScope): Int

    /** [scope] 대상에 `deletedAt` 톰스톤을 일괄 update 하고 처리 건수를 반환한다. */
    suspend fun purge(scope: PurgeScope): Int
}

@Singleton
class FirestoreCardPurgeRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val authRepository: AuthRepository,
    ) : CardPurgeRepository {
        @Suppress("TooGenericExceptionCaught") // 표시/실행 전용 — 미인증/오프라인/권한은 0/부분 처리로 강등.
        override suspend fun count(scope: PurgeScope): Int =
            try {
                matchingTargets(scope).size
            } catch (e: Exception) {
                Log.d(TAG, "card purge count failed (offline/permission): ${e.message}")
                0
            }

        @Suppress("TooGenericExceptionCaught")
        override suspend fun purge(scope: PurgeScope): Int {
            val targets = matchingTargets(scope)
            val uid = authRepository.currentUid
            if (uid == null || targets.isEmpty()) return 0
            val savedCards = firestore.collection(USERS).document(uid).collection(SAVED_CARDS)
            var purged = 0
            // Sequential ≤500-per-batch chunking; a mid-run failure is resumable (re-query excludes
            // already-tombstoned docs on the next call).
            for (chunk in targets.chunked(MAX_BATCH)) {
                val batch = firestore.batch()
                for (target in chunk) {
                    batch.set(
                        savedCards.document(target.cardId),
                        // cardType is required by the update rule (firestore.rules:222); deletedAt=serverTs.
                        mapOf(
                            FIELD_CARD_TYPE to target.cardType.wire,
                            FIELD_DELETED_AT to FieldValue.serverTimestamp(),
                        ),
                        SetOptions.merge(),
                    )
                }
                batch.commit().await()
                purged += chunk.size
            }
            return purged
        }

        /** Active cards (`deletedAt==null`) whose createdAt falls in [scope]'s window. cardType-typed. */
        private suspend fun matchingTargets(scope: PurgeScope): List<Target> {
            val uid = authRepository.currentUid ?: return emptyList()
            val docs =
                firestore
                    .collection(USERS).document(uid)
                    .collection(SAVED_CARDS)
                    .whereEqualTo(FIELD_DELETED_AT, null)
                    .get()
                    .await()
                    .documents
            val cutoffMillis = scope.cutoffMillis(System.currentTimeMillis())
            return docs.mapNotNull { doc -> doc.toTarget(cutoffMillis) }
        }

        private fun DocumentSnapshot.toTarget(cutoffMillis: Long?): Target? {
            val cardType = getString(FIELD_CARD_TYPE)?.let(::cardTypeOrNull) ?: return null
            // ALL (cutoff null) → always in-window. 30/90-day preset → createdAt strictly older than the
            // cutoff. A pending serverTimestamp reads null locally → treated as "just created" (excluded
            // from a preset, included by ALL).
            val createdAtMillis = getTimestamp(FIELD_CREATED_AT)?.toDate()?.time
            val withinWindow = cutoffMillis == null || (createdAtMillis != null && createdAtMillis < cutoffMillis)
            return if (withinWindow) Target(cardId = id, cardType = cardType) else null
        }

        // cardType wire == enum name for all three (WORD/EXPRESSION/SENTENCE) — valueOf is exact.
        private fun cardTypeOrNull(wire: String): CardType? = runCatching { CardType.valueOf(wire) }.getOrNull()

        private data class Target(val cardId: String, val cardType: CardType)

        private companion object {
            const val TAG = "CardPurgeRepository"
            const val USERS = "users"
            const val SAVED_CARDS = "saved_cards"
            const val FIELD_CARD_TYPE = "cardType"
            const val FIELD_CREATED_AT = "createdAt"
            const val FIELD_DELETED_AT = "deletedAt"
            const val MAX_BATCH = 500
        }
    }

/** [scope] 컷오프 epoch millis(이보다 오래된 createdAt 이 대상). [PurgeScope.ALL] 은 null(컷오프 없음). */
internal fun PurgeScope.cutoffMillis(nowMillis: Long): Long? =
    when (this) {
        PurgeScope.LAST_30_DAYS -> nowMillis - 30L * DAY_MILLIS
        PurgeScope.LAST_90_DAYS -> nowMillis - 90L * DAY_MILLIS
        PurgeScope.ALL -> null
    }

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L
