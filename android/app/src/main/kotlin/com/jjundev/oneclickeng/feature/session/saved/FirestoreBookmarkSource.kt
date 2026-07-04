package com.jjundev.oneclickeng.feature.session.saved

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.feature.session.summary.BookmarkCard
import com.jjundev.oneclickeng.feature.session.summary.BookmarkSource
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BookmarkSource] 의 Firestore 구현(M2-04) — M2-02 의 [com.jjundev.oneclickeng.feature.session.summary.EmptyBookmarkSource]
 * 를 교체한다. 요약 북마크 섹션이 표시할 SENTENCE 카드를 최신순으로 읽는다.
 *
 * 쿼리 = `where cardType==SENTENCE and deletedAt==null orderBy createdAt desc limit N` — 선언된 복합 인덱스
 * `(cardType ASC, deletedAt ASC, createdAt DESC)`(firestore.indexes.json)를 그대로 쓴다. 인덱스에 `sessionId`
 * 필드가 없어 쿼리는 **전역 최신순**이다; [sessionId] 는 seam 계약상 인자로 받되 필터에 쓰지 않는다 — 방금
 * 끝난 세션에서 저장한 북마크가 최신이라 상단을 차지하므로 표시 목적에 충분하다(saved-cards.md §3.3).
 * 오프라인이면 Firestore 네이티브 캐시가 마지막 동기화 스냅샷으로 답한다.
 */
@Singleton
class FirestoreBookmarkSource
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val authRepository: AuthRepository,
    ) : BookmarkSource {
        @Suppress("TooGenericExceptionCaught") // 표시 전용 로드 — 미인증/오프라인/인덱스 미비 모두 빈 리스트로 강등.
        override suspend fun latestSentences(
            sessionId: String,
            limit: Int,
        ): List<BookmarkCard> {
            val uid = authRepository.currentUid ?: return emptyList()
            return try {
                firestore
                    .collection(USERS).document(uid)
                    .collection(SAVED_CARDS)
                    .whereEqualTo(FIELD_CARD_TYPE, CardType.SENTENCE.wire)
                    .whereEqualTo(FIELD_DELETED_AT, null)
                    .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
                    .limit(limit.toLong())
                    .get().await()
                    .documents.mapNotNull { doc ->
                        val english = doc.getString(FIELD_ENGLISH) ?: return@mapNotNull null
                        BookmarkCard(english = english, korean = doc.getString(FIELD_KOREAN).orEmpty())
                    }
            } catch (e: Exception) {
                Log.d(TAG, "bookmark query failed: ${e.message}")
                emptyList()
            }
        }

        private companion object {
            const val TAG = "FirestoreBookmarkSource"
            const val USERS = "users"
            const val SAVED_CARDS = "saved_cards"
            const val FIELD_CARD_TYPE = "cardType"
            const val FIELD_DELETED_AT = "deletedAt"
            const val FIELD_CREATED_AT = "createdAt"
            const val FIELD_ENGLISH = "english"
            const val FIELD_KOREAN = "korean"
        }
    }
