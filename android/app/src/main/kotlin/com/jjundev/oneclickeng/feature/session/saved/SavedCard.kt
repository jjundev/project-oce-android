package com.jjundev.oneclickeng.feature.session.saved

/**
 * `saved_cards` 문서의 판별자(firestore-schema.md §3). [wire] 는 Firestore `cardType` 필드·보안 규칙 enum
 * (`['WORD','SENTENCE','EXPRESSION']`, firestore.rules:15-18) 값이다.
 */
enum class CardType(val wire: String) {
    WORD("WORD"),
    EXPRESSION("EXPRESSION"),
    SENTENCE("SENTENCE"),
}

/**
 * 저장 카드 3종(M2-04). 각 변형은 firestore-schema.md §3 의 타입별 필드만 담고([contentMap]), `cardType`·
 * `createdAt`·`deletedAt` 공통 필드는 리포지토리([SavedCardRepository])가 주입한다 — create 시점의 서버시각·
 * 톰스톤 상태는 도메인이 아니라 저장 계층의 관심사다.
 *
 * 필드 매핑은 도메인 모델(WordCard/ExpressionCard/ParaphraseBookmark)에서 스키마명으로 정합한다. schema
 * optional `afterHighlights`(firestore-schema.md:68)는 요약 `ExpressionCard` 에 소스 필드가 없어 요약-출처
 * 저장에선 항상 생략된다(M2-04 rev-3 #6 — 도달 불가한 optional).
 */
sealed interface SavedCard {
    val cardType: CardType

    /** 타입별 content 필드(공통 필드 제외). null 값은 리포가 그대로 실어 스키마의 nullable 을 표현한다. */
    fun contentMap(): Map<String, Any?>

    /** WORD — 요약 신규 단어 카드. WordCard(en/ko/exampleEn/exampleKo) → 스키마명. */
    data class Word(
        val english: String,
        val korean: String,
        val exampleEnglish: String,
        val exampleKorean: String,
    ) : SavedCard {
        override val cardType get() = CardType.WORD

        override fun contentMap() =
            mapOf(
                "english" to english,
                "korean" to korean,
                "exampleEnglish" to exampleEnglish,
                "exampleKorean" to exampleKorean,
            )
    }

    /** EXPRESSION — 요약 표현 개선 카드. `type` 은 스키마 문자열(`natural|accurate`). */
    data class Expression(
        val type: String,
        val koreanPrompt: String,
        val before: String,
        val after: String,
        val explanation: String,
    ) : SavedCard {
        override val cardType get() = CardType.EXPRESSION

        override fun contentMap() =
            mapOf(
                "type" to type,
                "koreanPrompt" to koreanPrompt,
                "before" to before,
                "after" to after,
                "explanation" to explanation,
            )
    }

    /** SENTENCE — 턴 중 deep 패러프레이즈 북마크. ParaphraseBookmark(sentence/translation) → english/korean. */
    data class Sentence(
        val english: String,
        val korean: String,
    ) : SavedCard {
        override val cardType get() = CardType.SENTENCE

        override fun contentMap() =
            mapOf(
                "english" to english,
                "korean" to korean,
            )
    }
}
