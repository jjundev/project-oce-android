package com.jjundev.oneclickeng.core.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 실제 파일 백드 DataStore 를 JVM 에서 구동(Robolectric 불필요) — `ReminderRepositoryTest` 와 동일 패턴.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SummarySaveSettingsRepositoryTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun newRepo(scope: CoroutineScope): DataStoreSummarySaveSettingsRepository {
        val dataStore =
            PreferenceDataStoreFactory.create(scope = scope) {
                File(tmpFolder.newFolder(), "summary_save.preferences_pb")
            }
        return DataStoreSummarySaveSettingsRepository(dataStore)
    }

    @Test
    fun `defaults to save-by-default true`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val repo = newRepo(scope)

            assertTrue(repo.current().saveByDefault)

            scope.cancel()
        }

    @Test
    fun `setSaveByDefault persists and is reflected in both current() and the settings flow`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val repo = newRepo(scope)

            repo.setSaveByDefault(false)

            assertEquals(false, repo.current().saveByDefault)
            assertEquals(false, repo.settings.first().saveByDefault)

            scope.cancel()
        }
}
