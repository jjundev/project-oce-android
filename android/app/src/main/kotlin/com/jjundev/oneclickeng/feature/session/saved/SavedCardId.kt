package com.jjundev.oneclickeng.feature.session.saved

/**
 * 결정적 `cardId` 파생(ADR-0001 · firestore-schema.md §3). 랜덤 UUID 대신 소스 튜플에서 문서 id 를 파생해,
 * 같은 항목을 요약 재렌더·화면 재진입·프로세스 재시작에서 다시 저장해도 같은 문서로 수렴한다(멱등 dedup).
 * dedup 은 문서 id 자체에 살고 별도 `itemKey` 필드는 두지 않는다.
 *
 * `sessionId` 는 서버 UUID(고정 길이)라 산출 문자열이 Firestore 문서 id 한도(1500바이트)를 넘지 않는다.
 * 순수 함수 — Firestore 없이 단위 검증(NFR-8).
 */
object SavedCardId {
    private const val SEP = "__"

    /**
     * WORD/EXPRESSION(요약 출처): `"{sessionId}__{cardType}__{sourceIndex}"`. [sourceIndex] 는 요약 SSE 해당
     * 섹션 배열(`summary.words`/`summary.expressions`)의 0-기반 순번. SENTENCE 에는 쓰지 않는다([forSentence]).
     */
    fun forSummary(
        sessionId: String,
        cardType: CardType,
        sourceIndex: Int,
    ): String {
        require(cardType != CardType.SENTENCE) { "SENTENCE cardId 는 forSentence 로 파생한다" }
        return "$sessionId$SEP${cardType.wire}$SEP$sourceIndex"
    }

    /**
     * SENTENCE(턴 중 deep 패러프레이즈): `"{sessionId}__SENTENCE__{turnIndex}__{level}"`. [level] 단독은 턴마다
     * 반복돼 같은 세션 내 충돌하므로 [turnIndex] 를 반드시 포함한다(ADR-0001).
     */
    fun forSentence(
        sessionId: String,
        turnIndex: Int,
        level: Int,
    ): String = "$sessionId$SEP${CardType.SENTENCE.wire}$SEP$turnIndex$SEP$level"
}
