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
import org.junit.Assert.assertNull
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
    fun `first accrual reports before=0 and not a same-day repeat (M3-06 countup baseline)`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            val r = store.accrue("s1", 300, "2026-07-04")

            assertEquals(0L, r.todaySecondsBefore) // nothing studied today yet → roll 0→after
            assertFalse(r.sameDayRepeat) // first study of the day → streak animates

            scope.cancel()
        }

    @Test
    fun `same-day second session reports before=prior bucket and same-day repeat (streak static)`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            store.accrue("s1", 300, "2026-07-04")
            val r = store.accrue("s2", 120, "2026-07-04")

            assertEquals(300L, r.todaySecondsBefore) // before = today bucket prior to this session
            assertEquals(420L, r.state.todaySeconds) // after = accumulated
            assertTrue(r.sameDayRepeat) // already studied today → streak stays static

            scope.cancel()
        }

    @Test
    fun `replay reports before==after and same-day repeat (strip snaps static)`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            store.accrue("s1", 300, "2026-07-04")
            val replay = store.accrue("s1", 300, "2026-07-04")

            assertFalse(replay.changed) // idempotent no-op
            assertEquals(replay.state.todaySeconds, replay.todaySecondsBefore) // before == after → static
            assertTrue(replay.sameDayRepeat)

            scope.cancel()
        }

    @Test
    fun `day rollover reports before=0 (new day bucket rolls from zero)`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            store.accrue("s1", 300, "2026-07-04")
            val r = store.accrue("s2", 200, "2026-07-05")

            assertEquals(0L, r.todaySecondsBefore) // new KST day → today bucket starts at 0
            assertEquals(200L, r.state.todaySeconds)
            assertFalse(r.sameDayRepeat) // different day → streak animates

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

    @Test
    fun `reset zeroes state and clears unsynced so drain cannot re-push (M3-09 revival guard)`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)
            store.accrue("s1", 300, "2026-07-04") // total=300, streak=1, unsynced=true

            store.reset()

            val snap = store.snapshot()
            assertEquals(0L, snap.totalSeconds)
            assertEquals(0L, snap.todaySeconds)
            assertEquals(0, snap.streak)
            assertNull(snap.lastStudyDate)
            assertFalse("unsynced must be false after reset — else drain() revives the total", snap.unsynced)

            scope.cancel()
        }

    @Test
    fun `seedIfEmpty is a no-op after reset (non-null total blocks re-seed from a not-yet-reset server)`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)
            store.accrue("s1", 300, "2026-07-04")
            store.reset() // total=0 (non-null), lastStudyDate=null, streak=0

            // A server that has NOT been reset yet must not revive streak/lastStudyDate/total.
            store.seedIfEmpty(serverTotalSeconds = 5000, serverStreak = 9, serverLastStudyDate = "2026-07-03")

            val snap = store.snapshot()
            assertEquals(0L, snap.totalSeconds)
            assertEquals(0, snap.streak)
            assertNull(snap.lastStudyDate)

            scope.cancel()
        }

    @Test
    fun `reconcileFromServer adopts a larger server total after a guest-to-google merge`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)
            store.accrue("s1", 300, "2026-07-04") // local total = 300 (this device's guest-only portion)

            // Server total after merge = pre-existing target total (2200s, studied on another device)
            // + this device's guest total (300s), per merge.ts:resolveStudytimeTotal.
            store.reconcileFromServer(serverTotalSeconds = 2500)

            val snap = store.snapshot()
            assertEquals(2500L, snap.totalSeconds)
            assertFalse("adopted value already matches server — no re-push needed", snap.unsynced)

            scope.cancel()
        }

    @Test
    fun `reconcileFromServer is a no-op when local total already covers the server value`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)
            store.accrue("s1", 300, "2026-07-04")
            store.accrue("s2", 400, "2026-07-05") // local total = 700, unsynced = true

            store.reconcileFromServer(serverTotalSeconds = 500) // stale/behind read

            val snap = store.snapshot()
            assertEquals(700L, snap.totalSeconds)
            assertTrue("local ahead of server — still needs its own push", snap.unsynced)

            scope.cancel()
        }
}
