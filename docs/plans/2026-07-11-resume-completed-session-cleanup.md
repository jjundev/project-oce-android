# Resume Completed Session Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 완료된 대화가 DataStore의 미완 세션 스냅샷으로 남아 홈의 `이어서 대화하기` CTA에 노출되는 회귀를 제거한다.

**Architecture:** `SessionSnapshotStore`에 스냅샷의 종결 상태를 기준으로 쓰기 또는 삭제를 결정하는 단일 영속화 API를 둔다. `SessionTurnProgress`가 상대역 자동 진행과 “상태가 바뀐 뒤 저장” 콜백을 한 단위로 소유하고, `GeneratedDialogueSessionViewModel`은 이 콜백으로 현재 스냅샷을 영속화한다. 홈의 `resumeInfo` 검증은 방어선으로 유지하며, 완료 세션을 다시 이어하기로 만들지 않는다.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt ViewModel, Kotlin Coroutines, DataStore Preferences, JUnit4/Robolectric.

## Global Constraints

- `resumeInfo`의 현재 방어 규칙(정상 스키마, 제목 존재, 학습자 메시지 1개 이상, `sessionPhase != Completed`)을 약화하지 않는다.
- 새 세션이 실제로 시작됐을 때 기존 스냅샷을 삭제하는 `DialogueGenerationViewModel.start()` 정책은 유지한다.
- 마지막 학습자 답변 뒤에도 피드백 시트의 `다음`을 누르기 전에는 세션을 완료로 취급하거나 스냅샷을 삭제하지 않는다.
- DataStore I/O는 기존처럼 앱 범위 코루틴에서 수행해 화면/백스택 엔트리 종료로 취소되지 않게 한다.
- Android 검증은 `docs/agents/android-verification.md`에 지정된 `scripts/verify-android.sh`만 사용한다.

---

## File Structure

- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStore.kt` — `SessionTurnSnapshot`의 완료 여부를 포함해 영속 상태를 단일 호출에서 결정한다.
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStoreTest.kt` — 미완 스냅샷은 보존하고, 같은 세션이 완료되면 기존 DataStore 키가 실제로 삭제되는 계약을 고정한다.
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt` — `SessionTurnProgress`가 자동 상대역 전이와 저장 콜백을 결합하고, Compose 타이머는 ViewModel 경유로 이를 호출하게 한다.
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStoreTest.kt` — 실제 완료 자동 전이에서 저장 콜백이 DataStore의 기존 미완 스냅샷을 삭제하는 통합 회귀를 추가한다.
- Modify: `docs/ux/home-learning-entry.md` — “새 세션 시작 시에만 폐기” 문구를 미완 스냅샷의 만료 정책으로 한정하고, 대화가 완료되면 이어하기 후보에서 즉시 제외됨을 명시한다.

## Decision Checkpoint

추가 사용자 결정은 필요 없다. 사용자의 문제 정의(이미 완료된 대화가 `이어서 대화하기`로 보이면 안 됨)와 현재 `SessionSnapshotStore` KDoc의 “finished dialogue is not a resume candidate” 계약을 따라, 대화 완료 시점에 스냅샷을 폐기한다. 마지막 답변 제출 자체가 아니라 피드백의 `다음` 이후 실제 `SessionPhase.Completed`가 된 시점이 폐기 기준이다.

### Task 1: 완료 상태를 인지하는 스냅샷 영속화 계약

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStore.kt:72-80`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStoreTest.kt:57-119`

**Interfaces:**
- Consumes: `SessionTurnSnapshot.sessionPhase: String` and `SessionPhase.Completed.name`.
- Produces: `suspend fun persist(snapshot: SessionTurnSnapshot): Unit`; non-completed snapshots overwrite `session_snapshot_json`, while completed snapshots remove that key.

- [ ] **Step 1: Write the failing store regression test**

Add this test below `valid incomplete snapshot yields ResumeInfo with learner-turn count` in `SessionSnapshotStoreTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the regression test to verify it fails**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionSnapshotStoreTest.persist removes an earlier incomplete snapshot when the session completes*'
```

Expected: compilation fails because `SessionSnapshotStore.persist` does not exist.

- [ ] **Step 3: Add the atomic caller-facing persistence API**

In `SessionSnapshotStore.kt`, add the following method immediately before the existing `write` method. Keep `write` public for existing direct store tests and low-level fixtures; production session code will use `persist`.

```kotlin
/**
 * Store exactly one recoverable session state. A completed session is terminal, so this removes
 * any older in-progress JSON rather than leaving a stale resume candidate behind.
 */
suspend fun persist(snapshot: SessionTurnSnapshot) {
    if (snapshot.sessionPhase == SessionPhase.Completed.name) {
        clear()
    } else {
        write(snapshot)
    }
}
```

- [ ] **Step 4: Run the store test to verify it passes**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionSnapshotStoreTest'
```

Expected: all `SessionSnapshotStoreTest` tests pass, including the new write-then-complete deletion case.

- [ ] **Step 5: Commit the storage contract**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStore.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStoreTest.kt
git commit -m "fix(resume): discard completed session snapshots"
```

### Task 2: 자동 상대역 완료를 ViewModel 영속화 경로로 수렴

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt:121-128,237-243,342-359,436-452,653-735`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStoreTest.kt:1-119`
- Modify: `docs/ux/home-learning-entry.md:77-87,407-420`

**Interfaces:**
- Consumes: `GeneratedDialogueState.commitReveal(): Unit`, `GeneratedDialogueState.completeOpponentTurn(): Unit`, and `SessionSnapshotStore.persist(snapshot: SessionTurnSnapshot): Unit` from Task 1.
- Produces: `internal class SessionTurnProgress(state: GeneratedDialogueState, onStateChanged: () -> Unit)` with `revealOpponentTurn()` and `completeOpponentTurn()`; each mutates state and invokes `onStateChanged` exactly once. `GeneratedDialogueSessionViewModel` delegates its public methods to this class and persists its `currentSnapshot()` through the callback.


- [ ] **Step 1: Write the failing auto-completion-to-DataStore regression test**

Add these imports to `SessionSnapshotStoreTest.kt`:

```kotlin
import com.jjundev.oneclickeng.core.network.DialogueTurn
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenState
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueStreamStatus
import com.jjundev.oneclickeng.feature.session.turn.GeneratedDialogueState
import com.jjundev.oneclickeng.feature.session.turn.SessionTurnProgress
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
```

Add this test below Task 1's `persist removes an earlier incomplete snapshot when the session completes` test:

```kotlin
@Test
fun `automatic final opponent completion removes the durable resume snapshot`() =
    runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val store = newStore(scope)
        var durable = snapshot(messages = listOf(learner("Hi")))
        store.persist(durable)
        val state = GeneratedDialogueState()
        state.accept(
            DialogueGenState.Ready(
                sessionId = "s1",
                remaining = 1,
                meta = null,
                turns = listOf(DialogueTurn(ko = "상대역", en = "See you.", role = "model")),
                streamStatus = DialogueStreamStatus.Done,
            ),
        )
        val progress =
            SessionTurnProgress(state) {
                durable = durable.copy(sessionPhase = state.sessionPhase.name)
                scope.launch { store.persist(durable) }
            }

        progress.revealOpponentTurn()
        progress.completeOpponentTurn()
        runCurrent()

        assertEquals(SessionPhase.Completed, state.sessionPhase)
        assertNull(store.read())
        assertNull(store.resumeInfo.first())
        scope.cancel()
    }
```

- [ ] **Step 2: Run the regression test to verify it fails**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionSnapshotStoreTest.automatic final opponent completion removes the durable resume snapshot*'
```

Expected: compilation fails because `SessionTurnProgress` does not exist. This locks the reported path: a previously durable in-progress snapshot, then the timer's final opponent transition, then no resumable value.

- [ ] **Step 3: Add the transition-and-persist callback seam and delegate from the ViewModel**

In `GeneratedDialogueSessionViewModel`, directly after `internal val turnState = GeneratedDialogueState()`, add:

```kotlin
private val progress = SessionTurnProgress(turnState, ::persistResume)
```

Replace `persistResume()` with this implementation and add the two public delegates immediately below it:

```kotlin
private fun persistResume() {
    val snapshot = currentSnapshot()
    appScope.launch { snapshotStore.persist(snapshot) }
}

fun revealOpponentTurn() = progress.revealOpponentTurn()

fun completeOpponentTurn() = progress.completeOpponentTurn()
```

Add this class immediately before the existing `@Stable`-annotated `GeneratedDialogueState` declaration in the same file:

```kotlin
/**
 * Couples timer-driven opponent-state mutations to their durable-state notification. Keeping this
 * separate from Compose lets a regression test drive the exact automatic completion path.
 */
internal class SessionTurnProgress(
    private val state: GeneratedDialogueState,
    private val onStateChanged: () -> Unit,
) {
    fun revealOpponentTurn() {
        state.commitReveal()
        onStateChanged()
    }

    fun completeOpponentTurn() {
        state.completeOpponentTurn()
        onStateChanged()
    }
}
```

Do not change `onAdvance()`: it must continue recording the pending feedback turn, resetting feedback/deep state, and then calling `persistResume()` after `turnState.advanceTurn()`.

- [ ] **Step 4: Route the Compose timer through the ViewModel methods**

In the `LaunchedEffect(state.opponentTurnSerial)` block in `GeneratedDialogueSessionRoute`, replace the two direct state mutations with the ViewModel calls:

```kotlin
LaunchedEffect(state.opponentTurnSerial) {
    if (state.turnPhase == TurnPhase.OpponentTurn && state.sessionPhase == SessionPhase.InTurn) {
        delay(effectiveSkeleton)
        viewModel.revealOpponentTurn()
        delay(effectiveAdvance)
        viewModel.completeOpponentTurn()
    }
}
```

This is intentionally the only behavioral wiring change: user-answer, generation-state, and feedback-next paths already call `persistResume()`.

- [ ] **Step 5: Clarify the UX source-of-truth wording**

Replace the snapshot expiry bullet in `docs/ux/home-learning-entry.md` with:

```markdown
- **만료:** 시간 만료 없음. 미완 세션 스냅샷은 새 세션이 실제로 시작될 때 폐기한다. 대화가 `Completed`에 도달하면 더 이상 이어가기 대상이 아니므로 즉시 폐기한다.
```

Replace the final `미완 복귀` table value with:

```markdown
| 미완 복귀 | 로컬의 검증된 미완 snapshot이 있으면 이어하기, 없으면 새로 시작 (시간 만료 없음; 새 세션 시작 또는 대화 완료 시 폐기) |
```

- [ ] **Step 6: Run focused regression tests and compile verification**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionSnapshotStoreTest' --tests '*GeneratedDialogueStateTest' --tests '*SessionTurnSnapshotTest'
scripts/verify-android.sh :app:compileDebugKotlin
```

Expected: all selected unit tests and `compileDebugKotlin` pass. Manually on an emulator or dev build, finish a 5-turn dialogue, return from summary to Home, and confirm the hero says `바로 대화 시작하기` rather than `이어서 대화하기`; exit during an earlier turn and confirm the resume CTA still appears.

- [ ] **Step 7: Commit the terminal-transition wiring and documentation**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStoreTest.kt docs/ux/home-learning-entry.md
git commit -m "fix(session): persist automatic completion"
```

## Self-Review

1. **Spec coverage:** The plan preserves valid mid-session recovery; fixes the completed-session false positive; keeps new-session discard semantics; and documents the terminal exception explicitly. Task 1 covers durable-store behavior, Task 2 drives the exact timer-triggered completion through the same mutation-plus-persist seam used in production and asserts the DataStore result.
2. **Placeholder scan:** No TODO/TBD or unspecified implementation/test step remains. Every changed API, command, and expected result is named.
3. **Type consistency:** `SessionSnapshotStore.persist` takes `SessionTurnSnapshot`; `SessionTurnProgress` takes `GeneratedDialogueState` plus an `onStateChanged: () -> Unit`; `GeneratedDialogueSessionViewModel` supplies `::persistResume`; Compose invokes the ViewModel delegates.
