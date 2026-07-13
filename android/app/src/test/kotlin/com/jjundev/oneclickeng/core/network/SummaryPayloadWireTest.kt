package com.jjundev.oneclickeng.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * summary 요청 페이로드의 wire 형태가 백엔드 계약(functions/src/types/summary.ts)과 일치하는지 고정한다.
 * 회귀 방지: 예전 페이로드는 expressionCandidates/words/sentences/userOriginalSentences 를 보내지 않아
 * 표현/단어 섹션이 항상 비어 있었다(하이라이트만 표시).
 */
class SummaryPayloadWireTest {
    // 프로덕션과 동일 정책: 기본값은 wire 에서 생략된다(백엔드가 부재 배열을 [] 로 관대 처리).
    private val json = Json { encodeDefaults = false }

    @Test
    fun `full payload serializes every backend-consumed field`() {
        val payload =
            SummaryPayload(
                totalScore = 82,
                turns =
                    listOf(
                        SummaryTurnDto(
                            "커피 주세요",
                            before = "One coffee",
                            after = "Could I get a coffee?",
                            score = 80,
                        ),
                    ),
                expressionCandidates =
                    listOf(
                        SummaryExpressionCandidateDto("natural", "커피 주세요", "One coffee", "Could I get a coffee?"),
                    ),
                words = emptyList(),
                sentences = listOf("Could I get a coffee?"),
                userOriginalSentences = listOf("One coffee"),
                sections = listOf("expressions"),
            )
        val wire = json.encodeToString(SummaryPayload.serializer(), payload)

        assertTrue(wire.contains("\"totalScore\":82"))
        assertTrue(wire.contains("\"expressionCandidates\""))
        assertTrue(wire.contains("\"sentences\""))
        assertTrue(wire.contains("\"userOriginalSentences\""))
        // 턴은 백엔드 SummaryTurn 형태(before/after/score) — 옛 userText/slimScore 아님.
        assertTrue(wire.contains("\"before\":\"One coffee\""))
        assertTrue(wire.contains("\"after\":\"Could I get a coffee?\""))
        assertTrue(wire.contains("\"score\":80"))
        assertFalse(wire.contains("userText"))
        assertFalse(wire.contains("slimScore"))
        // 재시도 필터는 backend 필드명 `sections` (옛 retrySections 아님).
        assertTrue(wire.contains("\"sections\":[\"expressions\"]"))
        assertFalse(wire.contains("retrySections"))
    }

    @Test
    fun `initial payload omits sections so the backend runs all three`() {
        val payload = SummaryPayload(totalScore = 0)
        val wire = json.encodeToString(SummaryPayload.serializer(), payload)
        assertFalse("absent sections = run all", wire.contains("sections"))
        assertEquals("{\"totalScore\":0}", wire)
    }
}
