package com.jjundev.oneclickeng.feature.review.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.feature.records.uidForSavedCardRead
import com.jjundev.oneclickeng.feature.session.saved.CardType
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 복습 풀 읽기 seam. pool = due 쿼리 + srs 없는 신규 보충(dedupe·cap). dueCount = 배너용 캡된 due 수.
 * Firestore 구현은 FirestoreReviewSource(Task 6).
 */
interface ReviewSource {
    suspend fun pool(
        nowMs: Long,
        target: Int = 20,
    ): List<ReviewItem>

    suspend fun dueCount(
        nowMs: Long,
        cap: Int = 20,
    ): Int
}

/**
 * [FirebaseFirestore] 구현. due = `deletedAt==null && srsNextReviewAt<=now orderBy srsNextReviewAt` (Task 5 복합
 * 인덱스). 신규 보충 = 3타입 per-type `cardType==T && deletedAt==null orderBy createdAt desc`(기존 인덱스 재사용) +
 * client-side `srsNextReviewAt==null` 필터. 둘을 [ReviewPool.merge] 로 합친다.
 */
@Singleton
class FirestoreReviewSource
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val authRepository: AuthRepository,
    ) : ReviewSource {
        @Suppress("TooGenericExceptionCaught", "ReturnCount") // 표시 전용 — 미인증/오프라인/인덱스 미비는 빈 풀로 강등.
        override suspend fun pool(
            nowMs: Long,
            target: Int,
        ): List<ReviewItem> {
            val uid = authRepository.uidForSavedCardRead() ?: return emptyList()
            val due = runCatching { dueItems(uid, nowMs, target) }.getOrDefault(emptyList())
            if (due.size >= target) return ReviewPool.merge(due, emptyList(), target)
            val fresh = runCatching { newItems(uid, target) }.getOrDefault(emptyList())
            return ReviewPool.merge(due, fresh, target)
        }

        @Suppress("TooGenericExceptionCaught")
        override suspend fun dueCount(
            nowMs: Long,
            cap: Int,
        ): Int {
            val uid = authRepository.uidForSavedCardRead() ?: return 0
            return runCatching { dueItems(uid, nowMs, cap).size }.getOrDefault(0)
        }

        private suspend fun dueItems(
            uid: String,
            nowMs: Long,
            limit: Int,
        ): List<ReviewItem> {
            val docs =
                collection(uid)
                    .whereEqualTo(FIELD_DELETED_AT, null)
                    .whereLessThanOrEqualTo(FIELD_SRS_NEXT_REVIEW_AT, nowMs)
                    .orderBy(FIELD_SRS_NEXT_REVIEW_AT, Query.Direction.ASCENDING)
                    .limit(limit.toLong())
                    .get().await().documents
            return docs.mapNotNull { it.toReviewItem() }
        }

        /** srs 없는 신규 카드 보충 = 3타입 per-type 쿼리(기존 인덱스 재사용) + client-side srsNextReviewAt==null. */
        private suspend fun newItems(
            uid: String,
            need: Int,
        ): List<ReviewItem> {
            val out = mutableListOf<ReviewItem>()
            for (type in CardType.values()) {
                val docs =
                    collection(uid)
                        .whereEqualTo(FIELD_CARD_TYPE, type.wire)
                        .whereEqualTo(FIELD_DELETED_AT, null)
                        .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
                        .limit(PER_TYPE_SCAN.toLong())
                        .get().await().documents
                out +=
                    docs.filter { it.get(FIELD_SRS_NEXT_REVIEW_AT) == null }
                        .mapNotNull { it.toReviewItem() }
            }
            return out.take(need)
        }

        private fun collection(uid: String): Query =
            firestore.collection(USERS).document(uid).collection(SAVED_CARDS)

        @Suppress("ReturnCount")
        private fun DocumentSnapshot.toReviewItem(): ReviewItem? {
            val cardType = CardType.values().firstOrNull { it.wire == getString(FIELD_CARD_TYPE) } ?: return null
            val card = toSavedCard(cardType) ?: return null
            val nextReviewAt = getLong(FIELD_SRS_NEXT_REVIEW_AT)
            val review =
                if (nextReviewAt == null) {
                    null
                } else {
                    ReviewState(
                        box = getLong(FIELD_SRS_BOX)?.toInt() ?: 0,
                        nextReviewAt = nextReviewAt,
                        lastReviewedAt = getLong(FIELD_SRS_LAST_REVIEWED_AT) ?: 0L,
                        reps = getLong(FIELD_SRS_REPS)?.toInt() ?: 0,
                        lapses = getLong(FIELD_SRS_LAPSES)?.toInt() ?: 0,
                    )
                }
            return ReviewItem(cardId = id, card = card, review = review)
        }

        private fun DocumentSnapshot.toSavedCard(cardType: CardType): SavedCard? =
            when (cardType) {
                CardType.WORD ->
                    getString(FIELD_ENGLISH)?.let {
                        SavedCard.Word(
                            english = it,
                            korean = getString(FIELD_KOREAN).orEmpty(),
                            exampleEnglish = getString(FIELD_EXAMPLE_ENGLISH).orEmpty(),
                            exampleKorean = getString(FIELD_EXAMPLE_KOREAN).orEmpty(),
                        )
                    }
                CardType.EXPRESSION ->
                    getString(FIELD_AFTER)?.let {
                        SavedCard.Expression(
                            type = getString(FIELD_TYPE).orEmpty(),
                            koreanPrompt = getString(FIELD_KOREAN_PROMPT).orEmpty(),
                            before = getString(FIELD_BEFORE).orEmpty(),
                            after = it,
                            explanation = getString(FIELD_EXPLANATION).orEmpty(),
                        )
                    }
                CardType.SENTENCE ->
                    getString(FIELD_ENGLISH)?.let {
                        SavedCard.Sentence(english = it, korean = getString(FIELD_KOREAN).orEmpty())
                    }
            }

        private companion object {
            const val USERS = "users"
            const val SAVED_CARDS = "saved_cards"
            const val PER_TYPE_SCAN = 30
            const val FIELD_CARD_TYPE = "cardType"
            const val FIELD_DELETED_AT = "deletedAt"
            const val FIELD_CREATED_AT = "createdAt"
            const val FIELD_SRS_BOX = "srsBox"
            const val FIELD_SRS_NEXT_REVIEW_AT = "srsNextReviewAt"
            const val FIELD_SRS_LAST_REVIEWED_AT = "srsLastReviewedAt"
            const val FIELD_SRS_REPS = "srsReps"
            const val FIELD_SRS_LAPSES = "srsLapses"
            const val FIELD_ENGLISH = "english"
            const val FIELD_KOREAN = "korean"
            const val FIELD_EXAMPLE_ENGLISH = "exampleEnglish"
            const val FIELD_EXAMPLE_KOREAN = "exampleKorean"
            const val FIELD_TYPE = "type"
            const val FIELD_KOREAN_PROMPT = "koreanPrompt"
            const val FIELD_BEFORE = "before"
            const val FIELD_AFTER = "after"
            const val FIELD_EXPLANATION = "explanation"
        }
    }
