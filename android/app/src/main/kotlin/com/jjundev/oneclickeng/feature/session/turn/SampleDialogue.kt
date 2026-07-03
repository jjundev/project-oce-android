package com.jjundev.oneclickeng.feature.session.turn

/**
 * M1-03 정적 셸을 구동하는 인메모리 스텁 대본. 실 SSE(M1-01)·백엔드 대본 생성(M1-02)에 의존하지 않는다
 * (이슈 blocked_by = M0-09/05/06). 대본은 상대역 첫 발화로 시작해 `model → user` 를 교대한다
 * (dialogue-learning-flow.md §4).
 *
 * **`referenceEnglish` 의미 부채(리뷰 반영):** 이 필드는 대본에 사전 작성된 **목표 문장**이다. 입력(마이크
 * M1-04 · 텍스트 M1-06)이 전부 범위 밖인 정적 셸에서는 학습자의 실제 발화를 얻을 수 없으므로, 스텁 버튼이
 * 이 목표 문장을 학습자 말풍선([DialogueMessage.Learner])으로 **임시 재생**한다. 프로덕션 턴버퍼 스키마의
 * `userEnglish`(실제 전사/입력)와는 다르며(feedback-slim.md §입력, dialogue-learning-flow.md §8) 의미가
 * 반대다. M1-04/M1-06 배선 시 이 소스는 실제 사용자 입력으로 교체되어야 한다.
 */
data class DialogueTurn(
    /** 상대역이 이 턴에서 말하는 영어 대사. 항상 존재한다. */
    val opponentEnglish: String,
    /** 학습자 응답 과제(한국어 발판). null 이면 학습자 응답 없는 마감 상대역 턴. */
    val learnerTask: ScaffoldTask? = null,
    /** 사전 작성 목표 문장. [learnerTask] 와 항상 짝(둘 다 null 또는 둘 다 비-null). 스텁 재생 전용. */
    val referenceEnglish: String? = null,
)

/** 카탈로그/프리뷰·스텁 구동 공용 샘플 대본. */
object SampleDialogue {
    val script: List<DialogueTurn> =
        listOf(
            DialogueTurn(
                opponentEnglish = "Hi! Welcome to the coffee shop. What can I get for you?",
                learnerTask = ScaffoldTask("따뜻한 아메리카노 한 잔 주세요."),
                referenceEnglish = "Can I get a hot americano, please?",
            ),
            DialogueTurn(
                opponentEnglish = "Sure! Would you like it for here or to go?",
                learnerTask = ScaffoldTask("가지고 갈게요."),
                referenceEnglish = "To go, please.",
            ),
            DialogueTurn(
                opponentEnglish = "Great. That'll be four dollars. Anything else?",
                learnerTask = ScaffoldTask("아니요, 그게 다예요. 감사합니다."),
                referenceEnglish = "No, that's all. Thank you.",
            ),
            DialogueTurn(
                opponentEnglish = "Perfect. Your americano will be ready in a moment. Have a nice day!",
            ),
        )
}
