package com.jjundev.oneclickeng.feature.session.resume

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.jjundev.oneclickeng.feature.session.turn.MessageData
import com.jjundev.oneclickeng.feature.session.turn.PendingData
import com.jjundev.oneclickeng.feature.session.turn.SessionPhase
import com.jjundev.oneclickeng.feature.session.turn.SessionTurnSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * SessionSnapshotStore.resumeInfo 검증 — 팬텀 이어하기 배제(스키마 불일치·완주·턴0·빈 제목)와
 * 표시 단위(완료 학습자 턴). 실제 파일 백드 DataStore 를 JVM 에서 구동한다(StudytimeStoreTest 패턴).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionSnapshotStoreTest {
    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun newStore(scope: CoroutineScope): SessionSnapshotStore {
        val dataStore =
            PreferenceDataStoreFactory.create(scope = scope) {
                File(tmpFolder.newFolder(), "resume.preferences_pb")
            }
        return SessionSnapshotStore(dataStore)
    }

    private fun learner(text: String) = MessageData(isLearner = true, english = text)

    private fun snapshot(
        messages: List<MessageData>,
        sessionPhase: String = SessionPhase.InTurn.name,
        topicTitle: String? = "카페에서 주문하기",
        totalTurns: Int? = 5,
        schemaVersion: Int = SessionTurnSnapshot.SCHEMA_VERSION,
    ) = SessionTurnSnapshot(
        schemaVersion = schemaVersion,
        topicTitle = topicTitle,
        totalTurns = totalTurns,
        messages = messages,
        turnPhase = "LearnerTurn",
        sessionPhase = sessionPhase,
        currentTaskKo = null,
        consumedTurnCount = 0,
        opponentTurnSerial = 0,
        pending = PendingData(),
        bufferedPending = emptyList(),
        streamStatus = "Idle",
        diagnostic = null,
        micState = "Ready",
        turns = emptyList(),
    )

    @Test
    fun `valid incomplete snapshot yields ResumeInfo with learner-turn count`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            store.write(snapshot(messages = listOf(learner("Hi"), learner("Thanks"))))
            val info = store.resumeInfo.first()

            assertEquals("카페에서 주문하기", info?.topicTitle)
            assertEquals(2, info?.doneTurns)
            assertEquals(5, info?.totalTurns)

            scope.cancel()
        }

    @Test
    fun `persist removes an earlier incomplete snapshot when the session completes`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)
            val incomplete = snapshot(messages = listOf(learner("Hi")))

            store.persist(incomplete)
            assertEquals("카페에서 주문하기", store.resumeInfo.first()?.topicTitle)

            store.persist(incomplete.copy(sessionPhase = SessionPhase.Completed.name))

            assertNull(store.read())
            assertNull(store.resumeInfo.first())
            scope.cancel()
        }

    @Test
    fun `turn0 snapshot (no learner message) is not resumable`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            store.write(snapshot(messages = emptyList()))

            assertNull(store.resumeInfo.first())
            scope.cancel()
        }

    @Test
    fun `completed snapshot is not resumable`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            store.write(
                snapshot(messages = listOf(learner("Hi")), sessionPhase = SessionPhase.Completed.name),
            )

            assertNull(store.resumeInfo.first())
            scope.cancel()
        }

    @Test
    fun `stale-schema snapshot is not resumable`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            store.write(snapshot(messages = listOf(learner("Hi")), schemaVersion = 999))

            assertNull(store.resumeInfo.first())
            scope.cancel()
        }

    @Test
    fun `blank-title snapshot is not resumable`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            store.write(snapshot(messages = listOf(learner("Hi")), topicTitle = null))

            assertNull(store.resumeInfo.first())
            scope.cancel()
        }

    @Test
    fun `empty store yields null`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val store = newStore(scope)

            assertNull(store.resumeInfo.first())
            scope.cancel()
        }
}
