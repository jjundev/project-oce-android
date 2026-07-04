package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.core.network.SummaryTurnDto
import com.jjundev.oneclickeng.feature.session.feedback.TurnFeedbackBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * 세션 turn buffer 누적 저장소(M2-02, plan v3 #17 확정). 각 턴 종료마다 [SlimFeedbackCoordinator]
 * 의 [TurnFeedbackBuffer] 스냅샷 + 과제/답변 echo 를 [record] 로 밀어넣으면(호출부는 M1-08 마이크 4상태
 * 배선), 요약이 이 저장소를 **단일 소스**로 읽어 (a) 요약 SSE 입력([turns]), (b) 종합 점수([totalScore]),
 * (c) 하이라이트 base([highlightBase]) 를 산출한다.
 *
 * 정본: dialogue-learning-flow.md §8(turn buffer)·:216(점수=slim 평균)·:217(하이라이트=최고점 턴). 이
 * 저장소는 M2-02 이전엔 존재하지 않았고(bufferSnapshot 만 있었다), summary handoff 를 위해 신설한다.
 *
 * @Singleton — 세션은 프로세스 전역 1개이므로 앱 스코프 싱글톤이 자연스럽다. 기록(M1-08 턴 흐름)과 읽기
 * (요약 진입)가 다른 진입점에서 오므로 접근을 @Synchronized 로 직렬화한다(경합은 드물지만 방어적).
 */
@Singleton
class SessionTurnBufferStore
    @Inject
    constructor() {
        private val lock = Any()
        private var currentSessionId: String? = null
        private val turns = mutableListOf<SummaryTurnDto>()

        // 세션 시작 벽시계(M3-05 studytime). 새 세션 시작 시 캡처, 요약이 완주 시 경과 학습시간을 산출한다.
        private var startAtMillis: Long? = null

        /**
         * 세션 시작/전환 시 호출 — 새 sessionId 면 이전 세션 버퍼를 비우고 시작 벽시계를 캡처한다(같은 세션
         * 재진입이면 유지). 멱등: 같은 sessionId 로 여러 번 불러도 안전하다.
         */
        fun startSession(sessionId: String) {
            synchronized(lock) {
                if (sessionId != currentSessionId) {
                    turns.clear()
                    currentSessionId = sessionId
                    startAtMillis = System.currentTimeMillis()
                }
            }
        }

        /** 현재 세션의 시작 벽시계(epoch millis) — 없으면 null. 요약 완주 시 studytime 경과 산출용(M3-05). */
        fun sessionStartMillis(): Long? = synchronized(lock) { startAtMillis }

        /**
         * 완료된 한 턴을 기록한다(§8). `koreanPrompt`/`userText` 는 과제·내 답변 echo, 나머지는 슬림
         * 피드백 스냅샷([TurnFeedbackBuffer]) — 스킵/실패 섹션은 해당 키가 null 로 들어와 요약에서 낮은
         * 신뢰도로 처리된다(§9.1). 빈 transcript 턴은 애초에 호출부가 기록하지 않는다(§8).
         */
        fun record(
            koreanPrompt: String,
            userText: String,
            buffer: TurnFeedbackBuffer,
        ) {
            synchronized(lock) {
                turns +=
                    SummaryTurnDto(
                        koreanPrompt = koreanPrompt,
                        userText = userText,
                        correctedText = buffer.correctedText,
                        naturalExpression = buffer.naturalExpression,
                        slimScore = buffer.slimScore,
                    )
            }
        }

        /** 요약 SSE 입력용 turn 리스트 스냅샷(방어적 복사). */
        fun turns(): List<SummaryTurnDto> = synchronized(lock) { turns.toList() }

        /**
         * 종합 점수 = slim `writingScore` 평균(null 턴 제외, 반올림). 유효 점수가 하나도 없으면 null
         * (전 턴 스킵) — 헤로는 이를 "점수 없음" 으로 렌더한다(임의 기본값 생성 금지, §9.2 :153).
         */
        fun totalScore(): Int? =
            synchronized(lock) {
                val scores = turns.mapNotNull { it.slimScore }
                if (scores.isEmpty()) null else scores.average().roundToInt()
            }

        /** 하이라이트 base = slim 점수 최고 턴(≤1). 유효 점수 없으면 null. */
        fun highlightBase(): HighlightTurn? =
            synchronized(lock) {
                turns
                    .filter { it.slimScore != null }
                    .maxByOrNull { it.slimScore!! }
                    ?.let { HighlightTurn(it.koreanPrompt, it.userText, it.slimScore!!) }
            }

        /** 명시적 리셋(세션 종료·이탈). */
        fun clear() {
            synchronized(lock) {
                turns.clear()
                currentSessionId = null
                startAtMillis = null
            }
        }
    }
