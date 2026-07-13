package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.feature.session.feedback.TurnFeedbackBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** 한 턴의 원시 버퍼(슬림 스냅샷 + 과제/답변 echo). 요약 페이로드 투영([SummaryPayloadProjector])의 입력. */
data class BufferedTurn(
    val koreanPrompt: String,
    val userText: String,
    val correctedText: String?,
    val naturalExpression: String?,
    val slimScore: Int?,
)

/**
 * 세션 turn buffer 누적 저장소(M2-02). 각 턴 종료마다 슬림 피드백 스냅샷 + 과제/답변 echo 를 [record] 로
 * 밀어넣으면, 요약이 이 저장소를 단일 소스로 읽어 (a) 요약 페이로드 투영([bufferedTurns]),
 * (b) 종합 점수([totalScore]), (c) 하이라이트 base([highlightBase]) 를 산출한다.
 *
 * @Singleton — 세션은 프로세스 전역 1개. 기록(턴 흐름)과 읽기(요약 진입)가 다른 진입점에서 오므로 접근을
 * @Synchronized 로 직렬화한다.
 */
@Singleton
class SessionTurnBufferStore
    @Inject
    constructor() {
        private val lock = Any()
        private var currentSessionId: String? = null
        private val turns = mutableListOf<BufferedTurn>()
        private var startAtMillis: Long? = null

        /** 새 sessionId 면 이전 버퍼를 비우고 시작 벽시계를 캡처한다(같은 세션 재진입이면 유지). 멱등. */
        fun startSession(sessionId: String) {
            synchronized(lock) {
                if (sessionId != currentSessionId) {
                    turns.clear()
                    currentSessionId = sessionId
                    startAtMillis = System.currentTimeMillis()
                }
            }
        }

        /** 현재 세션의 시작 벽시계(epoch millis) — 없으면 null(M3-05 studytime 경과 산출용). */
        fun sessionStartMillis(): Long? = synchronized(lock) { startAtMillis }

        /** 완료된 한 턴을 기록한다. 스킵/실패 섹션은 해당 키가 null 로 들어온다(§9.1). */
        fun record(
            koreanPrompt: String,
            userText: String,
            buffer: TurnFeedbackBuffer,
        ) {
            synchronized(lock) {
                turns +=
                    BufferedTurn(
                        koreanPrompt = koreanPrompt,
                        userText = userText,
                        correctedText = buffer.correctedText,
                        naturalExpression = buffer.naturalExpression,
                        slimScore = buffer.slimScore,
                    )
            }
        }

        /** 요약 페이로드 투영용 원시 turn 리스트 스냅샷(방어적 복사). */
        fun bufferedTurns(): List<BufferedTurn> = synchronized(lock) { turns.toList() }

        /** 종합 점수 = slim writingScore 평균(null 턴 제외, 반올림). 유효 점수 없으면 null. */
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
