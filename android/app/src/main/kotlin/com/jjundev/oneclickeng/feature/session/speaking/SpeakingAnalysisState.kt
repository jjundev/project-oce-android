package com.jjundev.oneclickeng.feature.session.speaking

/**
 * 스피킹 분석(M1-06)의 UI 상태축.
 *
 * 전사 + 한 줄 격려만 표현하며 **숫자 점수 필드가 없다**(speaking-analyze.md, PRD A8/R3) —
 * "음성 점수 미노출"을 구조적으로 보장한다. 마이크 4상태(M1-08)나 슬림 피드백(M1-07)과는
 * 별개의 축으로, 이 상태는 녹음 오디오→전사 왕복만 다룬다.
 */
sealed interface SpeakingAnalysisState {
    /** 분석 이력 없음(초기/리셋). */
    data object Idle : SpeakingAnalysisState

    /** 서버 왕복 진행 중. */
    data object Analyzing : SpeakingAnalysisState

    /** 전사 성공. [transcript] 는 후속 피드백(M1-07)에 재사용된다. */
    data class Result(
        val transcript: String,
        val encouragement: String,
    ) : SpeakingAnalysisState

    /** 무의미/무음 오디오(전사 공백) — 부드러운 재시도 신호. 재시도 UX 는 M1-08 소관. */
    data object Empty : SpeakingAnalysisState

    /** 네트워크/서버/워치독 실패 — "다시 시도" 신호. */
    data object Failed : SpeakingAnalysisState
}
