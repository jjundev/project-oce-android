# Opponent Speaker Pool Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hardcoded "Emma" opponent speaker with a locally-stored pool of dozens of (name, gender) entries, one deterministically assigned per session, driving the chat bubble name/avatar and the device-TTS voice gender.

**Architecture:** A pure `SpeakerDirectory` object holds ~40 `Speaker(name, gender)` entries and maps a session to one via `sessionId.hashCode()` (deterministic, so the same session always shows the same speaker — stable across recomposition and process-kill/restore with no persistence). The session ViewModel assigns the speaker when the sessionId first arrives (and re-derives it on snapshot restore), exposes it, threads the name down to the opponent chat bubble (name + avatar initial), and passes the gender to `speakOpponent`/`replayOpponent`. The backend's `DialogueMeta.opponentName/opponentGender` are deliberately NOT used.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Kotlin Coroutines, `android.speech.tts.TextToSpeech` (via existing `TtsPlaybackCoordinator`), JUnit4.

## Global Constraints

- Verify ONLY via `scripts/verify-android.sh` (never call `./gradlew` directly — the worktree needs the bootstrapped `GRADLE_USER_HOME` + `google-services.json`). First run may take minutes provisioning; that is normal.
- detekt MUST pass on every task. `ktlintMainSourceSetCheck` is excluded from the verify set (pre-existing master violation) — do not attempt to satisfy it.
- Roborazzi/Robolectric SCREENSHOT goldens are `DialogueTurnScreenshotTest` and `SessionFlowScreenshotTest` (under `app/src/test`, run inside `testDebugUnitTest`/`testReleaseUnitTest`) — they MUST stay green. New Compose params MUST default to the current "Emma" value so existing goldens do not shift. NOTE: `GeneratedDialogueSessionContentTest` is a *plain instrumented Compose test* under `app/src/androidTest` (assertions like `assertIsDisplayed`, no golden capture); the default `scripts/verify-android.sh` only **compiles** it (`:app:compileDebugAndroidTestKotlin`) and never executes it, so "green" for it means "still compiles." Adding defaulted params keeps it compiling and leaves its assertions unaffected.
- Gender values in the pool MUST be the strings `"male"` / `"female"` — that is the exact contract `AndroidDeviceTts.selectGenderVoice(gender)` matches on (`app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/AndroidDeviceTts.kt:89-104`).
- Do NOT read `DialogueMeta.opponentName` / `DialogueMeta.opponentGender` (the API-provided values). The speaker comes only from the local pool (explicit user requirement).
- Shared-source-set unit tests live under `app/src/test/kotlin/...` and run in both variants.
- Frequent commits: one commit per task, TDD (write the failing test first, watch it fail, implement, watch it pass).

---

### Task 1: SpeakerDirectory (local pool + deterministic assignment)

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/SpeakerDirectory.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SpeakerDirectoryTest.kt`

**Interfaces:**
- Consumes: nothing (leaf).
- Produces:
  - `data class Speaker(val name: String, val gender: String)` — `gender` is `"male"` or `"female"`.
  - `object SpeakerDirectory { val ENTRIES: List<Speaker>; fun assign(sessionId: String): Speaker }` — `assign` is a pure, deterministic mapping from `sessionId` to one pool entry.

- [ ] **Step 1: Write the failing test**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SpeakerDirectoryTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.turn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerDirectoryTest {
    @Test
    fun `pool holds dozens of entries with unique non-blank names`() {
        val entries = SpeakerDirectory.ENTRIES
        assertTrue("expected dozens of speakers, got ${entries.size}", entries.size >= 30)
        assertTrue("names must be non-blank", entries.all { it.name.isNotBlank() })
        assertEquals("names must be unique", entries.size, entries.map { it.name }.toSet().size)
    }

    @Test
    fun `every entry gender is male or female (selectGenderVoice contract)`() {
        assertTrue(SpeakerDirectory.ENTRIES.all { it.gender == "male" || it.gender == "female" })
    }

    @Test
    fun `pool contains both genders`() {
        assertTrue(SpeakerDirectory.ENTRIES.any { it.gender == "male" })
        assertTrue(SpeakerDirectory.ENTRIES.any { it.gender == "female" })
    }

    @Test
    fun `assign is deterministic for the same sessionId`() {
        val a = SpeakerDirectory.assign("session-abc-123")
        val b = SpeakerDirectory.assign("session-abc-123")
        assertEquals(a, b)
    }

    @Test
    fun `assign always returns a pool member`() {
        listOf("s1", "s2", "another-session", "", "服务器", "x").forEach {
            assertTrue(SpeakerDirectory.assign(it) in SpeakerDirectory.ENTRIES)
        }
    }

    @Test
    fun `assign spreads distinct sessions across more than one speaker`() {
        val distinct = (0 until 200).map { SpeakerDirectory.assign("session-$it") }.toSet()
        assertTrue("expected variety across sessions, got ${distinct.size}", distinct.size > 1)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SpeakerDirectoryTest*'`
Expected: FAIL — compile error (`SpeakerDirectory` / `Speaker` unresolved).

- [ ] **Step 3: Write the minimal implementation**

Create `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/SpeakerDirectory.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.turn

/**
 * 상대 발화자 1명 = 이름 + TTS 성별. [gender] 는 반드시 `"male"`/`"female"` —
 * `AndroidDeviceTts.selectGenderVoice` 가 이 문자열로 en-US 남/녀 보이스를 고른다.
 */
data class Speaker(val name: String, val gender: String)

/**
 * 기기에 미리 저장된 상대 발화자 풀. 세션마다 [assign] 이 `sessionId` 결정적 매핑으로 1명을 배정한다
 * — 순수 함수라 재구성·프로세스킬/회전 복원에도 같은 세션이면 같은 발화자가 나온다(영속 불필요).
 * 백엔드의 `DialogueMeta.opponentName/opponentGender` 는 쓰지 않는다(로컬 풀만 사용).
 */
object SpeakerDirectory {
    /** en-US 이름 + 성별 풀(성별 혼합). 아바타 이니셜은 이름 첫 글자에서 파생한다. */
    val ENTRIES: List<Speaker> =
        listOf(
            Speaker("Emma", "female"),
            Speaker("Olivia", "female"),
            Speaker("Ava", "female"),
            Speaker("Sophia", "female"),
            Speaker("Isabella", "female"),
            Speaker("Mia", "female"),
            Speaker("Charlotte", "female"),
            Speaker("Amelia", "female"),
            Speaker("Harper", "female"),
            Speaker("Evelyn", "female"),
            Speaker("Grace", "female"),
            Speaker("Chloe", "female"),
            Speaker("Lily", "female"),
            Speaker("Zoe", "female"),
            Speaker("Nora", "female"),
            Speaker("Hannah", "female"),
            Speaker("Aria", "female"),
            Speaker("Ruby", "female"),
            Speaker("Ella", "female"),
            Speaker("Scarlett", "female"),
            Speaker("Liam", "male"),
            Speaker("Noah", "male"),
            Speaker("Oliver", "male"),
            Speaker("James", "male"),
            Speaker("William", "male"),
            Speaker("Benjamin", "male"),
            Speaker("Lucas", "male"),
            Speaker("Henry", "male"),
            Speaker("Alexander", "male"),
            Speaker("Mason", "male"),
            Speaker("Ethan", "male"),
            Speaker("Daniel", "male"),
            Speaker("Jacob", "male"),
            Speaker("Logan", "male"),
            Speaker("Jack", "male"),
            Speaker("Owen", "male"),
            Speaker("Samuel", "male"),
            Speaker("David", "male"),
            Speaker("Leo", "male"),
            Speaker("Nathan", "male"),
        )

    /**
     * `sessionId` 로 결정적 배정. `String.hashCode()` 는 JVM 스펙상 안정적이라 같은 sessionId → 같은
     * 발화자다. 부호비트를 지워([Int.MAX_VALUE] AND) 음수 해시로 인한 음수 인덱스를 막는다.
     */
    fun assign(sessionId: String): Speaker {
        val index = (sessionId.hashCode() and Int.MAX_VALUE) % ENTRIES.size
        return ENTRIES[index]
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SpeakerDirectoryTest*'`
Expected: PASS (6/6). Also confirm `BUILD SUCCESSFUL` and detekt clean.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/SpeakerDirectory.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SpeakerDirectoryTest.kt
git commit -m "feat(turn): add local SpeakerDirectory pool with deterministic per-session assignment

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Parameterize the opponent chat bubble name + avatar

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/ChatBubble.kt:126` (the hardcoded avatar letter)
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt` (`DialogueTurnContent` signature + the `OpponentTurn(...)` call in the `items(messages)` loop)

**Interfaces:**
- Consumes: nothing new (uses the existing `OpponentTurn(speaker: String = "Emma", ...)` param).
- Produces: `DialogueTurnContent(..., opponentSpeaker: String = "Emma", ...)` — the opponent speaker display name that Task 3 will supply from the ViewModel.

**Context:** `OpponentTurn` already takes `speaker: String = "Emma"` (`ChatBubble.kt:114`) and renders it as the name label, but the avatar initial is hardcoded `TurnAvatar(letter = "E", ...)` (`ChatBubble.kt:126`), and no caller ever passes `speaker`, so both are effectively fixed. This task (a) derives the avatar initial from `speaker`, and (b) adds a defaulted `opponentSpeaker` param to `DialogueTurnContent` and forwards it. Keeping the default `"Emma"` means the stub route, previews, and all screenshot goldens are unchanged.

- [ ] **Step 1: Write the failing test**

Add this test to `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SpeakerDirectoryTest.kt` (a pure helper test for the avatar-initial derivation the bubble will use — keeps the UI edit test-anchored without a screenshot):

```kotlin
    @Test
    fun `avatar initial is the uppercased first letter of the speaker name`() {
        assertEquals("E", avatarInitial("Emma"))
        assertEquals("L", avatarInitial("liam"))
        assertEquals("?", avatarInitial(""))
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SpeakerDirectoryTest*'`
Expected: FAIL — compile error (`avatarInitial` unresolved).

- [ ] **Step 3: Add the `avatarInitial` helper (make it pass)**

Append to `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/SpeakerDirectory.kt` (top-level, after the `object`):

```kotlin
/** 아바타 이니셜 = 이름 첫 글자 대문자. 빈 이름이면 `"?"`(방어적). */
fun avatarInitial(name: String): String = name.firstOrNull()?.uppercase() ?: "?"
```

- [ ] **Step 4: Run the helper test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SpeakerDirectoryTest*'`
Expected: PASS (7/7).

- [ ] **Step 5: Wire the avatar initial into `OpponentTurn`**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/ChatBubble.kt`, change the hardcoded avatar letter (currently line 126):

```kotlin
            TurnAvatar(letter = "E", modifier = Modifier.padding(top = 20.dp))
```

to derive it from `speaker`:

```kotlin
            TurnAvatar(letter = avatarInitial(speaker), modifier = Modifier.padding(top = 20.dp))
```

(`avatarInitial` is in the same package — no import needed. `speaker` still defaults to `"Emma"`, so the initial stays `"E"` for existing callers.)

- [ ] **Step 6: Add `opponentSpeaker` to `DialogueTurnContent` and forward it**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt`, add a defaulted param to `DialogueTurnContent`. Insert it immediately after the existing `opponentTyping: Boolean = false,` parameter (the last param, around line 132):

```kotlin
    opponentTyping: Boolean = false,
    // 상대역 말풍선 화자명(로컬 SpeakerDirectory 배정). 미주입(스텁·프리뷰·스크린샷)이면 "Emma" 고정.
    opponentSpeaker: String = "Emma",
```

Then in the `items(messages)` loop, pass it to the opponent bubble. Change the `OpponentTurn(...)` call (around lines 171-176) from:

```kotlin
                        is DialogueMessage.Opponent ->
                            OpponentTurn(
                                text = message.english,
                                onReplay = { onReplay(message.english) },
                                onToggleTranslation = onToggleTranslation,
                            )
```

to:

```kotlin
                        is DialogueMessage.Opponent ->
                            OpponentTurn(
                                text = message.english,
                                speaker = opponentSpeaker,
                                onReplay = { onReplay(message.english) },
                                onToggleTranslation = onToggleTranslation,
                            )
```

- [ ] **Step 7: Run the full verify (screenshots must stay green)**

Run: `scripts/verify-android.sh`
Expected: `BUILD SUCCESSFUL`, detekt clean, and the Roborazzi goldens `DialogueTurnScreenshotTest` / `SessionFlowScreenshotTest` PASS (unchanged — default `opponentSpeaker = "Emma"` → avatar `"E"`, identical to before). `GeneratedDialogueSessionContentTest` (androidTest) only needs to keep **compiling** under the default verify (it is not executed) — the new defaulted param does not touch its call.

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/SpeakerDirectory.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SpeakerDirectoryTest.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/ChatBubble.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt
git commit -m "feat(turn): derive opponent avatar from speaker name + thread opponentSpeaker through DialogueTurnContent

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Assign the speaker in the ViewModel and drive name + TTS gender

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt`
  - `GeneratedDialogueSessionViewModel`: new `opponentSpeaker` state; assign it in `onGenerationState` and `seedFrom`; use its gender in `speakOpponent`/`replayOpponent`.
  - `GeneratedDialogueSessionRoute`: pass the speaker name into `GeneratedDialogueSessionContent`.
  - `GeneratedDialogueSessionContent`: new `opponentSpeaker` param forwarded to `DialogueTurnContent`.

**Interfaces:**
- Consumes: `Speaker`, `SpeakerDirectory.assign(sessionId)` (Task 1); `DialogueTurnContent(..., opponentSpeaker = ...)` (Task 2).
- Produces: nothing downstream (integration endpoint).

**Context:** `speakOpponent`/`replayOpponent` currently pass `gender = null` (`GeneratedDialogueSession.kt:378,385`). The session's `sessionId` is available via `currentSessionId()` (`= generation.sessionId() ?: restoredSessionId`). We assign the speaker the first time a sessionId is known (live path: `onGenerationState`; restore path: `seedFrom`), store it in an observable state, expose it, feed the name to the chat bubble and the gender to TTS. Because `assign` is deterministic, no snapshot/schema change is needed — restore re-derives the identical speaker from the restored sessionId. No new VM unit test (a full VM harness is out of scope, matching the prior TTS-wiring plan); Task 1 covers the assignment logic and the green bar here is a clean `scripts/verify-android.sh`.

- [ ] **Step 1: Add observable speaker state to the ViewModel**

In `GeneratedDialogueSessionViewModel`, add the state next to `headerIdentity` (immediately after the `headerIdentity` `private set` block, around line 211):

```kotlin
        /**
         * 이 세션의 상대 발화자(로컬 [SpeakerDirectory] 배정). sessionId 가 처음 알려질 때 1회 배정하고,
         * 배정은 sessionId 결정적이라 복원 시 [seedFrom] 이 동일 발화자를 재도출한다(영속 불필요).
         * Route 가 이름을 말풍선에, [speakOpponent]/[replayOpponent] 가 성별을 TTS 에 쓴다.
         */
        var opponentSpeaker by mutableStateOf<Speaker?>(null)
            private set
```

- [ ] **Step 2: Add a private assignment helper**

Add this method to the ViewModel (place it right after `onGenerationState`, around line 374):

```kotlin
        /** sessionId 가 알려져 있고 아직 미배정이면 상대 발화자를 배정한다(멱등 — 결정적 매핑). */
        private fun assignSpeakerIfNeeded() {
            if (opponentSpeaker == null) {
                currentSessionId()?.let { opponentSpeaker = SpeakerDirectory.assign(it) }
            }
        }
```

- [ ] **Step 3: Assign on the live path (`onGenerationState`)**

Change `onGenerationState` (currently lines 370-374) from:

```kotlin
        fun onGenerationState(state: DialogueGenState) {
            if (state is DialogueGenState.Ready) latestTurns = state.turns
            turnState.accept(state)
            persistResume()
        }
```

to:

```kotlin
        fun onGenerationState(state: DialogueGenState) {
            if (state is DialogueGenState.Ready) latestTurns = state.turns
            turnState.accept(state)
            assignSpeakerIfNeeded()
            persistResume()
        }
```

- [ ] **Step 4: Assign on the restore path (`seedFrom`)**

In `seedFrom` (currently lines 312-326), add the assignment after `restoredSessionId` is set. Change:

```kotlin
        private fun seedFrom(snapshot: SessionTurnSnapshot) {
            turnState.restoreFrom(snapshot)
            latestTurns = snapshot.turns.map { it.toDomain() }
            restoredSessionId = snapshot.sessionId
            restoredLevel = snapshot.level
            restoreHeaderIdentity(snapshot)
```

to (insert one line after `restoredLevel = snapshot.level`):

```kotlin
        private fun seedFrom(snapshot: SessionTurnSnapshot) {
            turnState.restoreFrom(snapshot)
            latestTurns = snapshot.turns.map { it.toDomain() }
            restoredSessionId = snapshot.sessionId
            restoredLevel = snapshot.level
            assignSpeakerIfNeeded() // 결정적 매핑이라 복원 sessionId 로 동일 발화자 재도출
            restoreHeaderIdentity(snapshot)
```

- [ ] **Step 5: Use the speaker's gender in TTS**

Change `speakOpponent` (currently lines 377-379) from:

```kotlin
        fun speakOpponent(text: String) {
            tts.playTurn(text, gender = null, deviceOnly = true, advanceOnDone = true)
        }
```

to:

```kotlin
        fun speakOpponent(text: String) {
            tts.playTurn(text, gender = opponentSpeaker?.gender, deviceOnly = true, advanceOnDone = true)
        }
```

Change `replayOpponent` (currently lines 383-386) from:

```kotlin
        fun replayOpponent(text: String) {
            if (turnState.turnPhase == TurnPhase.OpponentTurn) return
            tts.playTurn(text, gender = null, deviceOnly = true, advanceOnDone = false)
        }
```

to:

```kotlin
        fun replayOpponent(text: String) {
            if (turnState.turnPhase == TurnPhase.OpponentTurn) return
            tts.playTurn(text, gender = opponentSpeaker?.gender, deviceOnly = true, advanceOnDone = false)
        }
```

- [ ] **Step 6: Add `opponentSpeaker` to `GeneratedDialogueSessionContent` and forward it**

In `GeneratedDialogueSessionContent` (signature around lines 631-643), add a defaulted param after `onReplay`:

```kotlin
    // 상대역 말풍선 "다시 듣기" 콜백(발화 텍스트 전달). 미주입이면 no-op(프리뷰·테스트 호환).
    onReplay: (String) -> Unit = {},
    // 상대역 화자명(로컬 SpeakerDirectory 배정). 미주입(프리뷰·테스트)이면 "Emma" 고정(스크린샷 계약 유지).
    opponentSpeaker: String = "Emma",
) {
```

Then forward it in the `DialogueTurnContent(...)` call inside that function (currently ends with `onReplay = onReplay,` around line 665):

```kotlin
        opponentTyping = state.opponentTyping,
        onReplay = onReplay,
        opponentSpeaker = opponentSpeaker,
    )
```

- [ ] **Step 7: Supply the speaker name from the Route**

In `GeneratedDialogueSessionRoute`, add `opponentSpeaker` to the `GeneratedDialogueSessionContent(...)` call. Change the call's tail (currently ends with `onReplay = { text -> viewModel.replayOpponent(text) },` around line 148):

```kotlin
        onReplay = { text -> viewModel.replayOpponent(text) },
        // 상대 발화자 이름을 말풍선에 반영. 미배정(초기·sessionId 미도착)이면 "Emma" 폴백.
        opponentSpeaker = viewModel.opponentSpeaker?.name ?: "Emma",
    )
```

- [ ] **Step 8: Run the full verify**

Run: `scripts/verify-android.sh`
Expected: `BUILD SUCCESSFUL`, detekt clean, all unit + Roborazzi screenshot tests (`DialogueTurnScreenshotTest`, `SessionFlowScreenshotTest`) green, and `GeneratedDialogueSessionContentTest` (androidTest) still compiling. All are unaffected — every new Compose param defaults to `"Emma"`, and the `GeneratedDialogueSessionContent` callers in tests pass no `opponentSpeaker`.

- [ ] **Step 9: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt
git commit -m "feat(turn): assign per-session opponent speaker from local pool, drive name + TTS gender

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Manual verification (post-implementation, on device)

Not a task step (needs a device with real framework TTS), but the acceptance check:
1. Install (`scripts/verify-android.sh :app:installDebug`) and start a session; confirm the opponent bubble shows a non-"Emma" name/avatar for most sessions and the TTS voice gender matches the assigned speaker (male names → male voice).
2. Start several fresh sessions; confirm the speaker varies across sessions.
3. Kill/restore mid-session (rotate device or background+return); confirm the same speaker name persists.

## Self-Review

**Spec coverage:**
- "다양한 상대 이름 제공" → Task 1 pool (40 entries) + Task 3 per-session assignment; Task 2 renders name + avatar. ✅
- "API로 받지 않고 미리 기기에 저장된 수십개" → `SpeakerDirectory.ENTRIES` local constant, `DialogueMeta` untouched (Global Constraints). ✅
- "이름 및 성별을 돌려쓰는" → gender carried on `Speaker`, fed to TTS (Task 3 Step 5); deterministic `sessionId` mapping cycles the pool across sessions (decision confirmed by user). ✅

**Placeholder scan:** none — every step carries exact code/paths/commands.

**Type consistency:** `Speaker(name, gender)` and `SpeakerDirectory.assign(String): Speaker` / `avatarInitial(String): String` are defined in Task 1/2 and consumed with matching signatures in Task 3. `opponentSpeaker` is a `String` (display name) at the Compose boundary (`DialogueTurnContent`, `GeneratedDialogueSessionContent`) and a `Speaker?` (name+gender) inside the ViewModel — the Route bridges them via `viewModel.opponentSpeaker?.name ?: "Emma"`. Consistent.
