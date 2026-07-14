package com.jjundev.oneclickeng.feature.review

import com.jjundev.oneclickeng.feature.review.data.LeitnerLogic
import com.jjundev.oneclickeng.feature.review.data.ReviewState
import org.junit.Assert.assertEquals
import org.junit.Test

class LeitnerLogicTest {
    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    @Test
    fun `first correct on a new card promotes to box 1 with 1-day interval`() {
        val s = LeitnerLogic.onGrade(prev = null, correct = true, nowMs = now)
        assertEquals(1, s.box)
        assertEquals(now + 1 * day, s.nextReviewAt)
        assertEquals(now, s.lastReviewedAt)
        assertEquals(1, s.reps)
        assertEquals(0, s.lapses)
    }

    @Test
    fun `first incorrect on a new card stays box 1`() {
        val s = LeitnerLogic.onGrade(prev = null, correct = false, nowMs = now)
        assertEquals(1, s.box)
        assertEquals(now + 1 * day, s.nextReviewAt)
        assertEquals(1, s.lapses)
    }

    @Test
    fun `correct promotes box and uses that box interval`() {
        val prev = ReviewState(box = 2, nextReviewAt = 0, lastReviewedAt = 0, reps = 5, lapses = 1)
        val s = LeitnerLogic.onGrade(prev = prev, correct = true, nowMs = now)
        assertEquals(3, s.box)
        assertEquals(now + 7 * day, s.nextReviewAt)
        assertEquals(6, s.reps)
        assertEquals(1, s.lapses)
    }

    @Test
    fun `correct at box 5 caps at box 5 with 35-day interval`() {
        val prev = ReviewState(box = 5, nextReviewAt = 0, lastReviewedAt = 0, reps = 9, lapses = 0)
        val s = LeitnerLogic.onGrade(prev = prev, correct = true, nowMs = now)
        assertEquals(5, s.box)
        assertEquals(now + 35 * day, s.nextReviewAt)
    }

    @Test
    fun `incorrect resets box to 1 and increments lapses`() {
        val prev = ReviewState(box = 4, nextReviewAt = 0, lastReviewedAt = 0, reps = 8, lapses = 2)
        val s = LeitnerLogic.onGrade(prev = prev, correct = false, nowMs = now)
        assertEquals(1, s.box)
        assertEquals(now + 1 * day, s.nextReviewAt)
        assertEquals(9, s.reps)
        assertEquals(3, s.lapses)
    }
}
