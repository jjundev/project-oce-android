package com.jjundev.oneclickeng.feature.records

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.feature.session.saved.CardType
import com.jjundev.oneclickeng.feature.session.saved.SavedCard
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 기록 탭 읽기용 uid 해석. currentUid 가 이미 있으면(재실행·정상 경로) 그대로 쓰고, 없으면(첫 실행에서
 * 익명 사인인이 아직 진행 중) [AuthRepository.ensureSignedIn] 으로 대기한다(멱등·single-flight). 사인인이
 * 실패하면(오프라인 첫 실행 등) null 로 강등해 호출부가 빈 페이지로 degrade 하게 한다 — 표시 전용.
 */
internal suspend fun AuthRepository.uidForSavedCardRead(): String? =
    currentUid ?: runCatching { ensureSignedIn() }.getOrNull()

/** 기록 탭이 다루는 저장 카드 1건 = 문서 id([cardId]) + 타입별 도메인 content([card]). */
data class SavedCardEntry(
    val cardId: String,
    val card: SavedCard,
)

/**
 * 커서 페이지네이션 1페이지 결과. [cursor] 는 다음 `startAfter` 앵커(마지막 문서), [endReached] 는 마지막
 * 페이지 도달 여부(반환 수 < 요청 limit).
 */
data class SavedCardPage(
    val entries: List<SavedCardEntry>,
    val cursor: DocumentSnapshot?,
    val endReached: Boolean,
)

/**
 * 기록 탭 읽기 seam(M2-05). M2-04 의 [com.jjundev.oneclickeng.feature.session.summary.BookmarkSource] 는
 * SENTENCE 를 `english`/`korean` 만 매핑해 요약 북마크 섹션 표시에 특화돼 있어, 3종 전체 content + `cardId` 를
 * 요구하는 기록 탭엔 부족하다 — 별도 seam 으로 분리한다.
 *
 * SoT(saved-cards.md §4·§10 #9)가 지정한 **cursor 증분 로드**를 구현한다(라이브 스냅샷 리스너 아님): 1회성
 * `.get()` + `startAfter` 커서. 오프라인은 Firestore 기본 소스가 네이티브 캐시로 응답한다(NFR-4).
 */
interface SavedCardQuerySource {
    /**
     * [cardType] 의 다음 페이지를 최신순([FIELD_CREATED_AT] desc)으로 읽는다. [after] 가 null 이면 첫 페이지.
     * 미인증(currentUid null)이면 빈 종단 페이지로 강등한다(표시 전용 — 실패를 화면에 노출하지 않음).
     */
    suspend fun page(
        cardType: CardType,
        after: DocumentSnapshot?,
        limit: Int = PAGE_SIZE,
    ): SavedCardPage

    companion object {
        const val PAGE_SIZE = 30
    }
}

/**
 * [FirebaseFirestore] 구현. 쿼리 = `where cardType==T and deletedAt==null orderBy createdAt desc limit(N)`
 * (+ 커서 `startAfter`) — 선언된 복합 인덱스 `(cardType ASC, deletedAt ASC, createdAt DESC)`(firestore.indexes.json)를
 * 그대로 쓴다. 문서→[SavedCard] 역매핑은 `cardType` 판별자로 3종을 분기하며, 필수 필드 누락 문서는 조용히 건너뛴다.
 */
@Singleton
class FirestoreSavedCardQuerySource
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val authRepository: AuthRepository,
    ) : SavedCardQuerySource {
        @Suppress("TooGenericExceptionCaught") // 표시 전용 — 미인증/오프라인/인덱스 미비 모두 빈 페이지로 강등.
        override suspend fun page(
            cardType: CardType,
            after: DocumentSnapshot?,
            limit: Int,
        ): SavedCardPage {
            val uid = authRepository.uidForSavedCardRead()
                ?: return SavedCardPage(emptyList(), null, endReached = true)
            return try {
                var query: Query =
                    firestore
                        .collection(USERS).document(uid)
                        .collection(SAVED_CARDS)
                        .whereEqualTo(FIELD_CARD_TYPE, cardType.wire)
                        .whereEqualTo(FIELD_DELETED_AT, null)
                        .orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
                        .limit(limit.toLong())
                if (after != null) query = query.startAfter(after)

                val docs = query.get().await().documents
                val entries = docs.mapNotNull { doc -> doc.toEntry(cardType) }
                SavedCardPage(
                    entries = entries,
                    cursor = docs.lastOrNull(),
                    endReached = docs.size < limit,
                )
            } catch (e: Exception) {
                // 표시 전용 강등은 유지하되, 실기기 진단을 위해 예외 종류를 warn 으로 드러낸다
                // (인덱스 미비=FAILED_PRECONDITION, 규칙/권한=PERMISSION_DENIED 를 logcat 기본 필터에서 식별).
                Log.w(TAG, "saved_card page query failed [${e::class.simpleName}]: ${e.message}")
                SavedCardPage(emptyList(), null, endReached = true)
            }
        }

        private fun DocumentSnapshot.toEntry(cardType: CardType): SavedCardEntry? {
            // 필수 필드(english/after) 누락 문서는 null 로 강등(단일 return — ReturnCount 준수).
            val card: SavedCard? =
                when (cardType) {
                    CardType.WORD ->
                        getString(FIELD_ENGLISH)?.let { english ->
                            SavedCard.Word(
                                english = english,
                                korean = getString(FIELD_KOREAN).orEmpty(),
                                exampleEnglish = getString(FIELD_EXAMPLE_ENGLISH).orEmpty(),
                                exampleKorean = getString(FIELD_EXAMPLE_KOREAN).orEmpty(),
                            )
                        }
                    CardType.EXPRESSION ->
                        getString(FIELD_AFTER)?.let { after ->
                            SavedCard.Expression(
                                type = getString(FIELD_TYPE).orEmpty(),
                                koreanPrompt = getString(FIELD_KOREAN_PROMPT).orEmpty(),
                                before = getString(FIELD_BEFORE).orEmpty(),
                                after = after,
                                explanation = getString(FIELD_EXPLANATION).orEmpty(),
                            )
                        }
                    CardType.SENTENCE ->
                        getString(FIELD_ENGLISH)?.let { english ->
                            SavedCard.Sentence(english = english, korean = getString(FIELD_KOREAN).orEmpty())
                        }
                }
            return card?.let { SavedCardEntry(cardId = id, card = it) }
        }

        private companion object {
            const val TAG = "SavedCardQuerySource"
            const val USERS = "users"
            const val SAVED_CARDS = "saved_cards"
            const val FIELD_CARD_TYPE = "cardType"
            const val FIELD_DELETED_AT = "deletedAt"
            const val FIELD_CREATED_AT = "createdAt"
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
