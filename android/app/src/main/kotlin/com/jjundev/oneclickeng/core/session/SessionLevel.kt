package com.jjundev.oneclickeng.core.session

/**
 * 세션 난이도 단일 소스(SoT). 흩어져 있던 레벨 정의(HomeScreen/HomeSessionGraph/GeneratedDialogueSession
 * 등)를 이 enum 하나로 모은다. [token] 은 백엔드/DB(`users/{uid}.level`, `point_ledger.difficulty`)
 * 저장 값이자 서버 계약(functions/src/config/levels.ts 와 1:1). entries 는 쉬움→어려움 순.
 *
 * [cefr] 은 프롬프트 난이도 밴드용 내부 값으로 UI 에는 절대 노출하지 않는다. [labelKo]/[descKo] 가 화면 표기.
 */
enum class SessionLevel(
    val token: String,
    val labelKo: String,
    val descKo: String,
    val cefr: String,
    val xp: Int,
) {
    STARTER("starter", "매우 쉬움", "단어와 짧은 문장부터 천천히 시작해요", "A1", 5),
    EASY("easy", "쉬움", "쉬운 일상 표현으로 편하게 대화해요", "A2", 10),
    NORMAL("normal", "중간", "일상 대화를 자연스럽게 이어가요", "B1", 20),
    HARD("hard", "어려움", "조금 더 길고 깊은 대화까지 해봐요", "B2", 35),
    EXPERT("expert", "매우 어려움", "빠르고 풍부한 표현으로 도전해요", "C1", 55),
    ;

    companion object {
        /** 저장 토큰 → SessionLevel. 미지/누락 토큰은 NORMAL 로 폴백(구버전 값 안전). */
        fun fromToken(token: String?): SessionLevel =
            entries.firstOrNull { it.token == token } ?: NORMAL
    }
}
