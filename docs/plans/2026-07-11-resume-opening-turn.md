# Opening-Turn Session Resume Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 첫 상대방 대사가 화면에 표시된 뒤 나간 미완 세션을 홈의 “이어서 대화하기”로 원래 상태에서 복원하되, 아직 타이핑 스켈레톤만 보인 세션은 복귀 후보로 표시하지 않는다.

**Architecture:** 스냅샷의 `messages`를 실제로 렌더된 말풍선만으로 직렬화한다. 표시 대기 상대방 대사는 기존 `pending` 필드로 보존하고, 복원 시 현재 `pending` 대사가 마지막 렌더 메시지인지 판별해 `awaitingReveal`을 재구성한다. 그러면 스키마 v3을 유지하면서 `SessionSnapshotStore.resumeInfo`가 `messages.isNotEmpty()`를 실제 렌더 기준으로 안전하게 사용할 수 있다.

**Tech Stack:** Kotlin, Jetpack DataStore Preferences, Kotlin Flow, kotlinx.serialization, JUnit 4, kotlinx.coroutines-test.

## Global Constraints

- 기존 `session_resume_prefs` DataStore, `SessionTurnSnapshot` 필드, 그리고 스키마 버전 3을 유지한다. 새 라이브러리·저장소·스키마 필드를 추가하지 않는다.
- 이어하기 후보는 정상 스키마, 비어 있지 않은 `topicTitle`, `sessionPhase != Completed`, 실제 렌더 메시지 1개 이상을 모두 만족해야 한다.
- 빈 메시지/미표시 스켈레톤, 손상 JSON, 스키마 불일치, 완료 세션은 계속 `ResumeInfo? == null`이어야 한다.
- `ResumeInfo.doneTurns`는 `messages.count { it.isLearner }`를 유지한다. 첫 상대방 발화 복귀는 `0 / 전체 턴`으로 정확히 표시한다.
- 복원된 표시 대기 상대방 대사는 기존 `pending` 데이터에서 한 번만 `commitReveal()`되어야 하며, 이미 표시된 상대방 대사를 중복 추가하면 안 된다.
- 변경과 검증은 `scripts/verify-android.sh`로 실행한다.

---

## File Structure

- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt` — 스냅샷에는 렌더된 메시지만 쓰고, `pending`과 마지막 메시지로 스켈레톤 표시 대기를 복원한다.
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionTurnSnapshotTest.kt` — 스켈레톤 스냅샷과 표시 완료 스냅샷의 메시지 차이 및 복원 뒤 한 번의 reveal을 고정한다.
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStore.kt` — 복귀 후보의 단일 판정을 “학습자 발화 1개”에서 “실제 렌더 메시지 1개”로 변경한다.
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStoreTest.kt` — 첫 상대방 발화는 복귀 가능하고 빈 스켈레톤 스냅샷은 불가능한 DataStore 통합 회귀를 추가한다.
- Modify: `docs/ux/home-learning-entry.md` — 첫 상대방 발화와 스켈레톤의 복귀 정책을 UX 계약으로 기록한다.

## Decision Checkpoint

추가 결정은 필요 없다. 현재 `effectiveMessages()`는 표시 대기 상대방 대사를 스냅샷 `messages`에 미리 넣어, 홈이 스켈레톤을 이미 렌더된 메시지로 오인하게 한다. `pending`에는 그 대사와 과제가 이미 보존되어 있으므로, 메시지를 미리 복제하지 않고 복원 시 마지막 메시지와 현재 `pending`을 비교해 표시 대기 여부를 복구하는 것이 v3 호환성을 지키는 최소 수정이다. 상대역 차례에서 마지막 메시지가 현재 `pending`의 상대방 말풍선이면 이미 표시됐고, 그렇지 않으면 스켈레톤을 다시 보인 뒤 `commitReveal()`한다.

### Task 1: 스키마 v3에서 렌더된 메시지와 스켈레톤 상태를 분리해 복원

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt:903-960`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionTurnSnapshotTest.kt:1-110`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionTurnSnapshotTest.kt`

**Interfaces:**
- Consumes: existing v3 `SessionTurnSnapshot.messages`, `pending`, and `turnPhase` fields.
- Produces: a v3 snapshot where `messages` has only rendered bubbles; `restoreFrom(snapshot)` reconstructs `awaitingReveal` from the current `pending` opponent and the last rendered message.

- [ ] **Step 1: Write the failing skeleton-versus-rendered snapshot test**

In `SessionTurnSnapshotTest.kt`, add `assertFalse` to the JUnit imports:

```kotlin
import org.junit.Assert.assertFalse
```

Then add this test before `snapshot json round-trip preserves messages phase task and buffered opponent`:

```kotlin
@Test
fun `snapshot stores only rendered opening and restores awaiting reveal`() {
    val state = GeneratedDialogueState()
    state.accept(ready(listOf(model("Hello"))))

    val awaiting = state.toSnapshot(MicState.Ready, listOf(model("Hello")), "s1", "easy")
    assertTrue(awaiting.messages.isEmpty())
    val restoredAwaiting = GeneratedDialogueState().apply { restoreFrom(awaiting) }
    assertTrue(restoredAwaiting.opponentTyping)
    restoredAwaiting.commitReveal()
    assertEquals(listOf(DialogueMessage.Opponent("Hello")), restoredAwaiting.messages)
    assertFalse(restoredAwaiting.opponentTyping)

    state.commitReveal()

    val revealed = state.toSnapshot(MicState.Ready, listOf(model("Hello")), "s1", "easy")
    assertEquals(listOf(MessageData(isLearner = false, english = "Hello")), revealed.messages)
    val restoredRevealed = GeneratedDialogueState().apply { restoreFrom(revealed) }
    assertFalse(restoredRevealed.opponentTyping)
    restoredRevealed.commitReveal()
    assertEquals(listOf(DialogueMessage.Opponent("Hello")), restoredRevealed.messages)
}
```

- [ ] **Step 2: Run the test to verify it fails against eager message serialization**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionTurnSnapshotTest.snapshot stores only rendered opening and restores awaiting reveal*'
```

Expected: FAIL at `assertTrue(awaiting.messages.isEmpty())`, because `effectiveMessages()` currently inserts the pending `Hello` before it is rendered.

- [ ] **Step 3: Persist only rendered messages and restore the reveal gate from existing v3 fields**

In `GeneratedDialogueSession.kt`, delete the entire `effectiveMessages()` function and its KDoc (current lines 905-913).

In `toSnapshot`, replace the current comment plus assignment:

```kotlin
// 표시 대기 중인 상대역 대사를 커밋한 것으로 간주해 저장(무손실·스냅샷 스키마 무변경). 복원 시엔
// 이미 messages 에 있으므로 스켈레톤을 재생하지 않고 곧장 대사가 보인다.
messages = effectiveMessages().map { MessageData(it is DialogueMessage.Learner, it.english) },
```

with:

```kotlin
// messages는 실제로 렌더된 말풍선만 보존한다. 표시 대기 상대역 대사는 pending에 남겨 복원 시
// 스켈레톤을 다시 거친다. 따라서 홈의 resumeInfo가 messages를 렌더 사실로 사용할 수 있다.
messages = messages.map { MessageData(it is DialogueMessage.Learner, it.english) },
```

In `restoreFrom`, replace the current two-line comment and `awaitingReveal = false` assignment with:

```kotlin
// v3에는 awaitingReveal 전용 필드가 없다. 상대역 차례에서 현재 pending 대사가 마지막 말풍선이면
// 이미 표시된 상태이고, 아니면 pending에만 보존된 스켈레톤 상태다. 직전 턴은 학습자 말풍선으로 끝나므로
// 이 마지막-메시지 비교는 현재 pending 대사의 표시 여부를 결정한다.
val pendingOpponent = pending.opponentEnglish
awaitingReveal =
    turnPhase == TurnPhase.OpponentTurn &&
        pendingOpponent != null &&
        messages.lastOrNull() != DialogueMessage.Opponent(pendingOpponent)
```

Keep the following `recomputeTyping()` call in place. Do not change `SessionTurnSnapshot` or its `SCHEMA_VERSION`.

- [ ] **Step 4: Run the snapshot regression tests**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionTurnSnapshotTest'
```

Expected: PASS. A pre-reveal snapshot has an empty `messages` list but restores `opponentTyping == true` and reveals `Hello` once; a post-reveal snapshot contains `Hello`, restores with `opponentTyping == false`, and a second `commitReveal()` does not duplicate it. Existing JSON round-trip and raw-turn buffer tests also pass.

- [ ] **Step 5: Commit the v3-compatible snapshot repair**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionTurnSnapshotTest.kt
git commit -m "fix(session): preserve reveal state in v3 snapshots"
```

### Task 2: 홈 이어하기 판정을 실제 렌더 메시지로 변경

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStore.kt:40-63`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStoreTest.kt:45-70,138-148`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStoreTest.kt`

**Interfaces:**
- Consumes: Task 1's v3 invariant that `SessionTurnSnapshot.messages` contains only rendered `MessageData`.
- Produces: `SessionSnapshotStore.resumeInfo: Flow<ResumeInfo?>`; it emits `ResumeInfo(doneTurns = 0)` for an incomplete snapshot containing one rendered opponent `MessageData`.

- [ ] **Step 1: Add failing Store eligibility tests**

In `SessionSnapshotStoreTest.kt`, add this fixture below `learner`:

```kotlin
private fun opponent(text: String) = MessageData(isLearner = false, english = text)
```

Replace the existing `turn0 snapshot (no learner message) is not resumable` test with these two tests:

```kotlin
@Test
fun `rendered opening opponent snapshot is resumable before first learner reply`() =
    runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val store = newStore(scope)

        store.write(snapshot(messages = listOf(opponent("Hello! What would you like?"))))

        val info = store.resumeInfo.first()
        assertEquals("카페에서 주문하기", info?.topicTitle)
        assertEquals(0, info?.doneTurns)
        assertEquals(5, info?.totalTurns)
        scope.cancel()
    }

@Test
fun `snapshot with no rendered message is not resumable`() =
    runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val store = newStore(scope)

        store.write(snapshot(messages = emptyList()))

        assertNull(store.resumeInfo.first())
        scope.cancel()
    }
```

- [ ] **Step 2: Run the rendered-opening test to verify it fails at the current Store gate**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionSnapshotStoreTest.rendered opening opponent snapshot is resumable before first learner reply*'
```

Expected: FAIL because `SessionSnapshotStore.resumeInfo` still rejects `done == 0`.

- [ ] **Step 3: Replace the learner-turn gate with the rendered-message gate**

In `SessionSnapshotStore.kt`, replace the KDoc and predicate in `resumeInfo` with this code. Do not change `doneTurns` calculation:

```kotlin
/**
 * 이어하기 프롬프트용 검증·해석 스냅샷. `recoverable`(key 존재만 검사)의 팬텀을 근절한다:
 * 디코드 성공 + 스키마 일치 + 미완(sessionPhase != Completed) + 실제 렌더 메시지 1개 이상 +
 * 표시 가능한 제목이 모두 성립할 때만 [ResumeInfo]. [SessionTurnSnapshot.messages]는 렌더된
 * 말풍선만 담으므로, 비어 있으면 아직 타이핑 스켈레톤 상태여서 제외한다. 진행 단위(doneTurns)는
 * 완료한 학습자 턴 수로, 세션 헤더 `completedTurns`(GeneratedDialogueSession) 및 `totalTurns` 와 같은 축이다.
 */
val resumeInfo: Flow<ResumeInfo?> =
    dataStore.data.map { prefs ->
        val snap =
            prefs[KEY_SNAPSHOT]
                ?.let { runCatching { json.decodeFromString<SessionTurnSnapshot>(it) }.getOrNull() }
                ?.takeIf { it.schemaVersion == SessionTurnSnapshot.SCHEMA_VERSION }
                ?: return@map null
        val title = snap.topicTitle
        val done = snap.messages.count { it.isLearner }
        if (title.isNullOrBlank() || snap.messages.isEmpty() || snap.sessionPhase == SessionPhase.Completed.name) {
            return@map null
        }
        ResumeInfo(
            topicTitle = title,
            doneTurns = done,
            totalTurns = snap.totalTurns ?: DEFAULT_TOTAL_TURNS,
        )
    }
```

- [ ] **Step 4: Run Store regressions**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionSnapshotStoreTest'
```

Expected: PASS. The rendered opening emits `카페에서 주문하기`, `0`, and `5`; the empty skeleton snapshot, completed, stale-schema, and blank-title cases emit `null`.

- [ ] **Step 5: Commit the resume eligibility change**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStore.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStoreTest.kt
git commit -m "fix(resume): restore rendered opening turns"
```

### Task 3: Document and verify the end-to-end opening-turn contract

**Files:**
- Modify: `docs/ux/home-learning-entry.md:407`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionTurnSnapshotTest.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/resume/SessionSnapshotStoreTest.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueStateTest.kt`

**Interfaces:**
- Consumes: Task 1's actual-rendered `messages` invariant and Task 2's `SessionSnapshotStore.resumeInfo` eligibility.
- Produces: a documented UX rule: a rendered opponent opening is resumable; a skeleton-only opening is not; complete sessions remain excluded.

- [ ] **Step 1: Update the UX decision table**

In `docs/ux/home-learning-entry.md`, replace the row at line 407 with:

```markdown
| 미완 복귀 | 제목이 있고 미완이며 실제 말풍선이 1개 이상 렌더된 로컬 snapshot이면 이어하기, 없으면 새로 시작. 첫 상대방 발화만 표시된 상태도 이어하기 대상이고 진행 표시는 완료 학습자 턴 수(0 / 전체 턴)로 유지한다. 타이핑 스켈레톤만 보인 상태는 이어하기 후보가 아니다. 시간 만료 없음; 새 세션 시작 또는 대화 완료 시 폐기. |
```

- [ ] **Step 2: Run the complete save, restore, and eligibility regression suite**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionTurnSnapshotTest' --tests '*SessionSnapshotStoreTest' --tests '*GeneratedDialogueStateTest'
```

Expected: PASS. 스켈레톤 스냅샷은 `messages`가 비어 홈에서 제외되지만 `pending`에서 복원 후 한 번만 대사를 표시한다. 표시 완료 첫 발화는 `doneTurns=0`으로 복귀 후보가 되고, 완료 세션은 durable snapshot을 지운다.

- [ ] **Step 3: Compile production sources**

Run:

```bash
scripts/verify-android.sh :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL` with no new compiler errors.

- [ ] **Step 4: Perform the emulator/dev-build acceptance check**

1. 새 대화를 시작해 타이핑 스켈레톤만 보일 때 나가고 홈으로 돌아와 `바로 대화 시작하기`가 보이는지 확인한다.
2. 새 대화를 다시 시작해 첫 상대방 말풍선이 실제로 표시될 때까지 기다린 뒤 나가고, 홈에 동일 주제의 `이어서 대화하기`와 `0 / 5턴`(선택 길이)이 보이는지 확인한다.
3. 이어하기를 탭해 기존 상대방 말풍선과 학습자 과제가 복원되고 생성 화면/API 호출을 거치지 않는지 확인한다.
4. 대화를 완주하고 요약에서 홈으로 돌아와 `바로 대화 시작하기`가 보이는지 확인한다.

- [ ] **Step 5: Commit the UX contract**

```bash
git add docs/ux/home-learning-entry.md
git commit -m "docs(ux): define opening-turn resume eligibility"
```

## Self-Review

1. **Spec coverage:** Task 1 removes the pre-render message leak while preserving v3 snapshot restore; Task 2 fixes the only Home eligibility gate; Task 3 documents and verifies opening-turn recovery, skeleton exclusion, and completed-session cleanup.
2. **Placeholder scan:** No unresolved placeholder or unspecified test/code step remains; all changed files, state transitions, test bodies, commands, and expected results are explicit.
3. **Type consistency:** Task 1 keeps `SessionTurnSnapshot` v3 unchanged and makes `messages` the rendered-bubble source of truth; Task 2 consumes `snap.messages.isEmpty()`; `ResumeInfo.doneTurns: Int` remains the learner-message count throughout.

