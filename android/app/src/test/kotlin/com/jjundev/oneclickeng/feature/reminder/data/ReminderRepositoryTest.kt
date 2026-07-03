package com.jjundev.oneclickeng.feature.reminder.data

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
import java.time.LocalDate

/**
 * DataStore 저장소 검증. M2-02 실호출자 없이도 [ReminderRepository.recordSessionCompleted] 를 직접 호출해
 * opt-in 게이트(count==2 && !resolved)와 캐시 미러링을 반증가능하게 확인한다(grill-review #16·#19 요구).
 * 실제 파일 백드 DataStore 를 JVM 에서 구동한다(Robolectric 불필요).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderRepositoryTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun newRepo(scope: CoroutineScope): DataStoreReminderRepository {
        val dataStore =
            PreferenceDataStoreFactory.create(scope = scope) {
                File(tmpFolder.newFolder(), "reminder.preferences_pb")
            }
        return DataStoreReminderRepository(dataStore)
    }

    @Test
    fun `prompt gate opens exactly on second completion then closes on resolve`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val repo = newRepo(scope)

            assertFalse("no completions yet", repo.shouldPromptOptIn())

            repo.recordSessionCompleted(streak = 1, lastStudyDate = LocalDate.of(2026, 7, 1))
            assertFalse("1st completion — gate closed", repo.shouldPromptOptIn())

            repo.recordSessionCompleted(streak = 2, lastStudyDate = LocalDate.of(2026, 7, 2))
            assertTrue("2nd completion — gate open", repo.shouldPromptOptIn())

            repo.markOptInResolved()
            assertFalse("resolved — gate closed for good", repo.shouldPromptOptIn())

            scope.cancel()
        }

    @Test
    fun `recordSessionCompleted mirrors streak and lastStudyDate into cache`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val repo = newRepo(scope)

            val date = LocalDate.of(2026, 7, 3)
            repo.recordSessionCompleted(streak = 7, lastStudyDate = date)

            val cache = repo.cacheSnapshot()
            assertEquals(7, cache.streak)
            assertEquals(date, cache.lastStudyDate)

            scope.cancel()
        }

    @Test
    fun `config defaults to disabled at 20 00 and setters persist`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val repo = newRepo(scope)

            val default = repo.currentConfig()
            assertFalse(default.enabled)
            assertEquals(20, default.hour)
            assertEquals(0, default.minute)

            repo.setEnabled(true)
            repo.setTime(hour = 8, minute = 30)
            val updated = repo.currentConfig()
            assertTrue(updated.enabled)
            assertEquals(8, updated.hour)
            assertEquals(30, updated.minute)

            scope.cancel()
        }

    @Test
    fun `permission asked flag persists`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val repo = newRepo(scope)

            assertFalse(repo.wasPermissionAsked())
            repo.markPermissionAsked()
            assertTrue(repo.wasPermissionAsked())

            scope.cancel()
        }
}
