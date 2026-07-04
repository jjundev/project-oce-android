package com.jjundev.oneclickeng.feature.session.saved

/**
 * 저장 카드 write 페이로드 조립(순수). `createdAt`/`deletedAt` 값은 호출자(리포지토리)가 Firestore
 * `FieldValue.serverTimestamp()`/null 로 주입하므로 이 계층은 Firestore 에 의존하지 않는다 — 덕분에
 * **createdAt 보존 불변식(saved-cards.md:57-58)을 Firestore 없이 회귀 검증**할 수 있다.
 *
 * 핵심 규율: [revive] 페이로드는 `createdAt` 키를 절대 포함하지 않는다(정렬 위치 보존). [create] 만
 * `createdAt` 을 싣는다. 존재 여부 분기(create vs revive)는 리포지토리의 로컬 캐시 read 가 결정한다.
 */
internal object SavedCardPayload {
    const val FIELD_CARD_TYPE = "cardType"
    const val FIELD_CREATED_AT = "createdAt"
    const val FIELD_DELETED_AT = "deletedAt"

    /** 타입별 content + cardType(공통 판별자). createdAt/deletedAt 은 미포함. */
    private fun base(card: SavedCard): Map<String, Any?> =
        card.contentMap() + mapOf(FIELD_CARD_TYPE to card.cardType.wire)

    /** create: 전체 페이로드 + [createdAt](서버시각) + `deletedAt=null`. */
    fun create(
        card: SavedCard,
        createdAt: Any,
    ): Map<String, Any?> =
        base(card) +
            mapOf(
                FIELD_CREATED_AT to createdAt,
                FIELD_DELETED_AT to null,
            )

    /** revive/refresh: content + `deletedAt=null`. **createdAt 키 없음**(정렬 보존, merge write). */
    fun revive(card: SavedCard): Map<String, Any?> = base(card) + mapOf(FIELD_DELETED_AT to null)

    /** 톰스톤/되살리기: cardType(update 규칙) + [deletedAt](삭제=서버시각 / 되살리기=null). createdAt 없음. */
    fun tombstone(
        cardType: CardType,
        deletedAt: Any?,
    ): Map<String, Any?> =
        mapOf(
            FIELD_CARD_TYPE to cardType.wire,
            FIELD_DELETED_AT to deletedAt,
        )
}
