package com.jjundev.oneclickeng.feature.session.saved

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
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
 * 쿼리 = `where cardType==SENTENCE and deletedAt==null` — 서버 `orderBy(createdAt)`/`limit` 는 쓰지 않는다(방금
 * 저장한 카드는 `createdAt` 이 서버타임스탬프라 로컬에선 pending=null 이어서, 서버 정렬 쿼리가 제외/기아시킨다).
 * 대신 [Source.CACHE] 를 먼저 읽어(오프라인-우선, 방금 저장한 pending write 도 즉시 포함) 캐시가 비어 있을 때만
 * (신규 기기) 서버로 폴백한다 — `FirestoreSavedCardRepository.exists()` 와 동일 패턴. 각 doc 의 `createdAt` 은
 * [DocumentSnapshot.ServerTimestampBehavior.ESTIMATE] 로 해석해(pending → ~now 추정) [BookmarkOrdering] 으로
 * 클라이언트에서 정렬·capping 한다 — 방금 저장한 카드가 항상 최상단을 차지한다. 두 등호 필터는 선언된 복합 인덱스
 * `(cardType ASC, deletedAt ASC, createdAt DESC)`(firestore.indexes.json)의 접두사라 인덱스 없이도 서빙된다.
 * 인덱스에 `sessionId` 필드가 없어 쿼리는 **전역 최신순**이다; [sessionId] 는 seam 계약상 인자로 받되 필터에 쓰지
 * 않는다 — 방금 끝난 세션에서 저장한 북마크가 최신이라 상단을 차지하므로 표시 목적에 충분하다(saved-cards.md §3.3).
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
            val query =
                firestore
                    .collection(USERS).document(uid)
                    .collection(SAVED_CARDS)
                    .whereEqualTo(FIELD_CARD_TYPE, CardType.SENTENCE.wire)
                    .whereEqualTo(FIELD_DELETED_AT, null)
            return try {
                // 오프라인-우선(ADR-0002): 캐시는 방금 저장한 pending write 를 즉시 포함한다. 캐시가 비면
                // (신규 기기) 서버로 폴백 — FirestoreSavedCardRepository.exists() 와 동일 패턴.
                val snapshot =
                    runCatching { query.get(Source.CACHE).await() }
                        .recoverCatching { query.get().await() }
                        .getOrThrow()
                val docs =
                    snapshot.documents.mapNotNull { doc ->
                        val english = doc.getString(FIELD_ENGLISH) ?: return@mapNotNull null
                        BookmarkDoc(
                            english = english,
                            korean = doc.getString(FIELD_KOREAN).orEmpty(),
                            // pending server timestamp → ESTIMATE 로 ~now 추정(정렬 시 최신 취급).
                            createdAtMillis =
                                doc.getTimestamp(FIELD_CREATED_AT, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                                    ?.toDate()?.time,
                        )
                    }
                BookmarkOrdering.latest(docs, limit)
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
