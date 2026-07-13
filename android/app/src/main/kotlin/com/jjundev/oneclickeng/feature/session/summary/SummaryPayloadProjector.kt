package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.core.network.SummaryExpressionCandidateDto
import com.jjundev.oneclickeng.core.network.SummaryPayload
import com.jjundev.oneclickeng.core.network.SummaryTurnDto

/**
 * 세션 turn 버퍼 → summary 요청 페이로드 투영(순수 함수). 백엔드 서브콜 계약
 * (functions/src/config/summary-prompts.ts)에 맞춰 4개 입력을 산출한다:
 * - [SummaryPayload.turns] → coaching (before/after/score)
 * - [SummaryPayload.expressionCandidates] → 표현 필터 (turn 당 accurate/natural before→after 후보)
 * - [SummaryPayload.userOriginalSentences] → 단어 필터가 lemma 로 제외할 원문
 * - [SummaryPayload.sentences] → 단어 필터가 신규 어휘를 캐는 개선문(교정 ∪ 자연스러운 표현)
 *
 * [SummaryPayload.words] 는 비운다 — 클라에 개별 단어 목록이 없고, 단어 서브콜은 sentences 로 grounding 한다.
 * 반환 페이로드의 [SummaryPayload.sections] 는 null(초기 호출 = 전 섹션); 재시도 필터는 코디네이터가 채운다.
 */
object SummaryPayloadProjector {
    fun project(
        turns: List<BufferedTurn>,
        totalScore: Int,
    ): SummaryPayload {
        val originals = turns.map { it.userText }.filter { it.isNotBlank() }
        val sentences =
            turns
                .flatMap { listOfNotNull(it.correctedText, it.naturalExpression) }
                .filter { it.isNotBlank() }
                .distinct()
        val candidates =
            turns.flatMap { turn ->
                buildList {
                    turn.correctedText
                        ?.takeIf { it.isNotBlank() && it != turn.userText }
                        ?.let { add(SummaryExpressionCandidateDto("accurate", turn.koreanPrompt, turn.userText, it)) }
                    turn.naturalExpression
                        ?.takeIf { it.isNotBlank() && it != turn.userText }
                        ?.let { add(SummaryExpressionCandidateDto("natural", turn.koreanPrompt, turn.userText, it)) }
                }
            }
        val wireTurns =
            turns.map { turn ->
                SummaryTurnDto(
                    koreanPrompt = turn.koreanPrompt,
                    before = turn.userText,
                    after = turn.correctedText ?: turn.naturalExpression,
                    score = turn.slimScore,
                )
            }
        return SummaryPayload(
            totalScore = totalScore,
            turns = wireTurns,
            expressionCandidates = candidates,
            words = emptyList(),
            sentences = sentences,
            userOriginalSentences = originals,
            sections = null,
        )
    }
}
