package com.jjundev.oneclickeng.feature.gamification.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * StudytimeStore DataStore 검증(M3-05) — 실제 파일 백드 DataStore 를 JVM 에서 구동한다(Robolectric 불필요,
 * ReminderRepositoryTest 패턴). 핵심: 세션 멱등 누적(WAQ 재생 중복 없음), 일 롤오버, 낙관 streak, unsynced 플래그.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudytimeStoreTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun newStore(scope: CoroutineScope): StudytimeStore {
        val dataStore =
            PreferenceDataStoreFactory.create(scope = scope) {
                File(tmpFolder.newFolder(), "gamification.preferences_pb")
            }
        return StudytimeStore(dataStore)
    }

    @Test
    fun `first accrual sets total, today, streak and marks unsynced`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            val r = store.accrue(sessionId = "s1", seconds = 300, dayKey = "2026-07-04")

            assertTrue(r.changed)
            assertEquals(300L, r.state.totalSeconds)
            assertEquals("2026-07-04", r.state.todayDayKey)
            assertEquals(300L, r.state.todaySeconds)
            assertEquals(1, r.state.streak)
            assertTrue(r.state.unsynced)

            scope.cancel()
        }

    @Test
    fun `same session accrued twice counts exactly once (WAQ replay idempotent)`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            store.accrue("s1", 300, "2026-07-04")
            val second = store.accrue("s1", 300, "2026-07-04")

            assertFalse("already settled → no-op", second.changed)
            assertEquals(300L, second.state.totalSeconds) // not 600
            assertEquals(300L, store.snapshot().totalSeconds)

            scope.cancel()
        }

    @Test
    fun `same-day second session accumulates today but does not bump streak`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            store.accrue("s1", 300, "2026-07-04")
            val r = store.accrue("s2", 120, "2026-07-04")

            assertTrue(r.changed)
            assertEquals(420L, r.state.totalSeconds)
            assertEquals(420L, r.state.todaySeconds) // same day → accumulates
            assertEquals(1, r.state.streak) // same day → unchanged

            scope.cancel()
        }

    @Test
    fun `next day rolls over today bucket and advances streak`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            store.accrue("s1", 300, "2026-07-04")
            val r = store.accrue("s2", 200, "2026-07-05")

            assertEquals(500L, r.state.totalSeconds) // total accumulates across days
            assertEquals("2026-07-05", r.state.todayDayKey)
            assertEquals(200L, r.state.todaySeconds) // today resets to the new day's seconds
            assertEquals(2, r.state.streak) // consecutive day → +1

            scope.cancel()
        }

    @Test
    fun `markSynced clears the write-ahead flag`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            store.accrue("s1", 300, "2026-07-04")
            assertTrue(store.snapshot().unsynced)

            store.markSynced()
            assertFalse(store.snapshot().unsynced)

            scope.cancel()
        }

    @Test
    fun `seedIfEmpty seeds only a fresh store`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            store.seedIfEmpty(serverTotalSeconds = 5000, serverStreak = 7, serverLastStudyDate = "2026-07-03")
            var snap = store.snapshot()
            assertEquals(5000L, snap.totalSeconds)
            assertEquals(7, snap.streak)
            assertEquals("2026-07-03", snap.lastStudyDate)

            // Second seed is a no-op — local state is now authoritative.
            store.seedIfEmpty(serverTotalSeconds = 9999, serverStreak = 99, serverLastStudyDate = "2020-01-01")
            snap = store.snapshot()
            assertEquals(5000L, snap.totalSeconds)
            assertEquals(7, snap.streak)

            scope.cancel()
        }
}
