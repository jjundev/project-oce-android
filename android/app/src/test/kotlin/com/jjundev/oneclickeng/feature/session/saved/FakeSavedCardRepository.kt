package com.jjundev.oneclickeng.feature.session.saved

/**
 * 코디네이터 단위 테스트용 [SavedCardRepository] 페이크. Firestore 없이 save/setDeleted 호출을 기록만 한다
 * (실제 영속·오프라인 분기는 계측/에뮬레이터 테스트 소관 — rev-3 #4-c). DeepFeedback/Summary 코디네이터
 * 양쪽에서 공유한다(생성자 param 추가로 갱신된 호출부).
 */
class FakeSavedCardRepository : SavedCardRepository {
    data class SaveCall(val cardId: String, val card: SavedCard)

    data class DeleteCall(val cardId: String, val cardType: CardType, val deleted: Boolean)

    val saves = mutableListOf<SaveCall>()
    val deletes = mutableListOf<DeleteCall>()

    override fun save(
        cardId: String,
        card: SavedCard,
    ) {
        saves += SaveCall(cardId, card)
    }

    override fun setDeleted(
        cardId: String,
        cardType: CardType,
        deleted: Boolean,
    ) {
        deletes += DeleteCall(cardId, cardType, deleted)
    }
}
