package com.jjundev.oneclickeng.feature.session.feedback

import org.junit.Assert.assertEquals
import org.junit.Test

/** 점수→밴드 경계(§19). 색 토큰 매핑은 테마 소관이라 순수 밴드 판정만 검증한다. */
class ScoreColorTest {
    @Test
    fun `70 and above is Natural`() {
        assertEquals(ScoreBand.Natural, scoreBand(100))
        assertEquals(ScoreBand.Natural, scoreBand(70))
    }

    @Test
    fun `50 to 69 is Neutral`() {
        assertEquals(ScoreBand.Neutral, scoreBand(69))
        assertEquals(ScoreBand.Neutral, scoreBand(50))
    }

    @Test
    fun `below 50 is Error`() {
        assertEquals(ScoreBand.Error, scoreBand(49))
        assertEquals(ScoreBand.Error, scoreBand(0))
    }
}
