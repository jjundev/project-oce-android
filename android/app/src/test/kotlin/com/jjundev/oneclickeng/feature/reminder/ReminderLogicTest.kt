package com.jjundev.oneclickeng.feature.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * M3-07 순수 결정 로직 검증(notification-reminder.md §4·§4.1·§5.1). 안드로이드 없이 값→값만.
 */
class ReminderLogicTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    // --- shouldPrompt (§2.2) -------------------------------------------------

    @Test
    fun `prompt only on 2nd completion when unresolved`() {
        assertTrue(ReminderLogic.shouldPrompt(completedSessionCount = 2, optInResolved = false))
    }

    @Test
    fun `no prompt before 2nd completion`() {
        assertEquals(false, ReminderLogic.shouldPrompt(1, optInResolved = false))
    }

    @Test
    fun `no prompt after 2nd completion once resolved`() {
        assertEquals(false, ReminderLogic.shouldPrompt(2, optInResolved = true))
    }

    @Test
    fun `no prompt on 3rd completion even if unresolved`() {
        // 닫기=해소이므로 정상 경로에선 도달 안 하지만, 게이트는 count==2 로 엄격히 잠근다.
        assertEquals(false, ReminderLogic.shouldPrompt(3, optInResolved = false))
    }

    // --- decideFire (§4.1) ---------------------------------------------------

    @Test
    fun `skip when studied today`() {
        val today = LocalDate.of(2026, 7, 3)
        assertEquals(
            ReminderLogic.FireDecision.SKIP_STUDIED_TODAY,
            ReminderLogic.decideFire(lastStudyDate = today, today = today),
        )
    }

    @Test
    fun `fire when last study was earlier`() {
        val today = LocalDate.of(2026, 7, 3)
        assertEquals(
            ReminderLogic.FireDecision.FIRE,
            ReminderLogic.decideFire(lastStudyDate = today.minusDays(1), today = today),
        )
    }

    @Test
    fun `cache miss fires (safer than silent)`() {
        assertEquals(
            ReminderLogic.FireDecision.FIRE_CACHE_MISS,
            ReminderLogic.decideFire(lastStudyDate = null, today = LocalDate.of(2026, 7, 3)),
        )
    }

    // --- buildContent (§5.1) -------------------------------------------------

    @Test
    fun `streak zero yields neutral start invite`() {
        val content = ReminderLogic.buildContent(streak = 0, lastStudyDate = null, today = LocalDate.of(2026, 7, 3))
        assertEquals("딸깍영어", content.title)
        assertEquals("오늘 시작하면 1일째예요", content.body)
    }

    @Test
    fun `cache miss yields neutral start invite`() {
        val content = ReminderLogic.buildContent(streak = null, lastStudyDate = null, today = LocalDate.of(2026, 7, 3))
        assertEquals("오늘 시작하면 1일째예요", content.body)
    }

    @Test
    fun `gap of one day shows future streak number`() {
        val today = LocalDate.of(2026, 7, 3)
        val content = ReminderLogic.buildContent(streak = 5, lastStudyDate = today.minusDays(1), today = today)
        assertEquals("🔥 5일째 — 오늘 이어가면 6일째예요", content.body)
    }

    @Test
    fun `gap of two or more falls to neutral invite without number`() {
        val today = LocalDate.of(2026, 7, 3)
        val content = ReminderLogic.buildContent(streak = 5, lastStudyDate = today.minusDays(2), today = today)
        assertEquals("🔥 오늘 5분 이어가볼까요?", content.body)
    }

    // --- computeInitialDelay (§4, device-local wall-clock) -------------------

    @Test
    fun `delay to later time today`() {
        val now = ZonedDateTime.of(2026, 7, 3, 8, 0, 0, 0, seoul)
        val delay = ReminderLogic.computeInitialDelay(now, hour = 20, minute = 0)
        assertEquals(Duration.ofHours(12), delay)
    }

    @Test
    fun `delay rolls to tomorrow when target already passed`() {
        val now = ZonedDateTime.of(2026, 7, 3, 21, 0, 0, 0, seoul)
        val delay = ReminderLogic.computeInitialDelay(now, hour = 20, minute = 0)
        assertEquals(Duration.ofHours(23), delay)
    }

    @Test
    fun `delay rolls to tomorrow when exactly at target (not after)`() {
        val now = ZonedDateTime.of(2026, 7, 3, 20, 0, 0, 0, seoul)
        val delay = ReminderLogic.computeInitialDelay(now, hour = 20, minute = 0)
        // 정확히 같은 시각은 "이후"가 아니므로 내일로 넘어가 24h.
        assertEquals(Duration.ofHours(24), delay)
    }

    @Test
    fun `delay is positive and within a day`() {
        val now = ZonedDateTime.of(2026, 7, 3, 13, 37, 12, 0, seoul)
        val delay = ReminderLogic.computeInitialDelay(now, hour = 20, minute = 30)
        assertTrue(delay > Duration.ZERO)
        assertTrue(delay <= Duration.ofDays(1))
    }
}
