# Learner Voice Replay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Retain each learner turn's recorded voice (the PCM already captured for STT, currently discarded) in session memory and add a speaker button to the learner's own chat bubble that replays that recording — mirroring the opponent bubble's "다시 듣기" button.

**Architecture:** The mic already captures the learner's utterance as an in-memory `RecordingResult.Captured(pcm, sampleRate, …)`; today it is base64'd to the server for transcription and thrown away. We stash that `Captured` in `stopRecording()`, and when the resulting transcript lands and the learner bubble is appended (`onAnalysisState → Result`), we move the clip into a per-session map keyed by the 0-based learner-turn ordinal. Playback reuses the existing single-playback authority: a new `TtsPlaybackCoordinator.playClip(pcm, sampleRate)` plays raw PCM through the same `PcmPlayer`, so learner replay and opponent speech never overlap. The UI adds a `LearnerTurn` wrapper that, when a clip exists for that bubble's ordinal, shows a `ReplayButton` to the left of the right-aligned learner bubble (reusing the opponent button's exact visual). Everything is in-memory and session-scoped — clips are gone on process-kill/restore, exactly like the opponent's replay.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Kotlin Coroutines, `android.media.AudioTrack` (behind the existing `PcmPlayer`/`PcmAudioPlayer`), JUnit4, `kotlinx-coroutines-test`.

## Global Constraints

- Verify ONLY via `scripts/verify-android.sh` (never call `./gradlew` directly — the worktree needs the bootstrapped `GRADLE_USER_HOME` + copied `google-services.json`). First run may take minutes provisioning dependencies; that is normal. The gradle module lives under `android/`; the script is at the repo root.
- detekt MUST pass on every task. `ktlintMainSourceSetCheck` is excluded from the default verify set (pre-existing master violation) — do not attempt to satisfy it.
- Roborazzi/Robolectric SCREENSHOT goldens `DialogueTurnScreenshotTest` and `SessionFlowScreenshotTest` (under `app/src/test`, run inside `testDebugUnitTest`/`testReleaseUnitTest`) MUST stay green. Every new Compose param MUST default to the value that reproduces the current render (`learnerClipIndices = emptySet()`, `hasAudio = false`) so existing goldens do not shift.
- Playback is **in-memory, session-scoped**. Do NOT write audio to disk, and do NOT touch `SessionTurnSnapshot`/`MessageData` — recorded clips are deliberately lost on process-kill/restore (parity with the opponent replay's `lastPcm`).
- Recorded audio is 16 kHz mono PCM16 (`RecordingResult.Captured.pcm` / `.sampleRate`). Play it with `PcmPlayer.play(pcm, sampleRateHz)` at its declared rate — never a hardcoded rate.
- `RECORD_AUDIO` is already declared in `android/app/src/main/AndroidManifest.xml:12`. No manifest change.
- Shared-source-set unit tests live under `app/src/test/kotlin/...` and run in both variants.
- Frequent commits: one commit per task, TDD (write the failing test first, watch it fail, implement, watch it pass).

---

### Task 1: `TtsPlaybackCoordinator.playClip` — play a raw PCM clip through the shared player

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt` (add `playClip` after `replay()`, ~line 115)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt` (add two tests)

**Interfaces:**
- Consumes: existing private `startNewSession()`, `playPcm(token, pcm, sampleRate)`, `finish(...)`, and the `advanceOnDone` flag inside the coordinator.
- Produces: `fun TtsPlaybackCoordinator.playClip(pcm: ByteArray, sampleRate: Int)` — plays a raw PCM16 buffer at `sampleRate`, cancelling any in-flight playback first; does NOT emit a completion (never advances the turn); silent no-op when muted.

**Context:** The coordinator is the single owner of the `PcmPlayer` (`AudioTrack`) and already has all the machinery: `startNewSession()` cancels the current job + stops the player, `playPcm` sets `PLAYING`/plays/settles to `IDLE`, and `finish` gates `completions` behind `advanceOnDone`. `playClip` is a thin public entry that mirrors `replay()` but takes an external PCM buffer instead of the retained `lastPcm`, and forces `advanceOnDone = false` so replaying your own voice never advances the conversation. Routing learner playback through this coordinator (rather than a second player) means tapping a learner clip stops any opponent speech and vice-versa — only one thing ever plays. It does NOT touch `lastPcm`, so the opponent's own replay is unaffected.

- [ ] **Step 1: Write the two failing tests**

In `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt`, add these two tests inside the `TtsPlaybackCoordinatorTest` class (e.g. immediately after the existing `advanceOnDone false suppresses the completion …` test, ~line 301):

```kotlin
    @Test
    fun `playClip plays given pcm at its rate without advancing`() =
        runTest {
            val player = FakePcmPlayer()
            val coordinator =
                TtsPlaybackCoordinator(FakeLlmApi(), player, FakeDeviceTts(), FakeSettings(), coordScope())

            val completions = collectCompletions(coordinator)
            val clip = byteArrayOf(9, 8, 7, 6)
            coordinator.playClip(clip, 16000)
            advanceUntilIdle()

            assertEquals(1, player.played.size)
            assertTrue(clip.contentEquals(player.played[0].first))
            assertEquals(16000, player.played[0].second) // honors the recording's own rate
            assertTrue(completions.isEmpty()) // 자기 녹음 재생은 턴을 전진시키지 않는다
            assertEquals(PlaybackState.IDLE, coordinator.state.value)
        }

    @Test
    fun `playClip is a silent no-op when muted`() =
        runTest {
            val player = FakePcmPlayer()
            val coordinator =
                TtsPlaybackCoordinator(
                    FakeLlmApi(),
                    player,
                    FakeDeviceTts(),
                    FakeSettings(TtsSettings(muted = true)),
                    coordScope(),
                )

            coordinator.playClip(byteArrayOf(1, 2, 3), 16000)
            advanceUntilIdle()

            assertTrue(player.played.isEmpty())
            assertEquals(PlaybackState.IDLE, coordinator.state.value)
        }
```

(These reuse the file's existing helpers `FakePcmPlayer`, `FakeLlmApi`, `FakeDeviceTts`, `FakeSettings`, `coordScope()`, `collectCompletions(...)`, and `TtsSettings` — all already imported/defined in this test file.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsPlaybackCoordinatorTest*'`
Expected: FAIL — compile error (`playClip` unresolved).

- [ ] **Step 3: Implement `playClip`**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt`, insert this method immediately after `replay()` (after its closing `}`, ~line 115, before `/** Stop all playback … */ fun stop()`):

```kotlin
        /**
         * 학습자 자기 녹음(캡처된 raw PCM16)을 그대로 재생한다. 진행 중 재생을 먼저 취소하고
         * ([startNewSession]) 이 클립을 재생한다. 상대 발화·`다시 듣기`와 **동일한 단일 재생 권위**
         * (같은 [player])를 공유해 두 오디오가 겹치지 않는다. 자기 녹음 재생은 대화 턴을 전진시키지
         * 않으므로 완료 신호를 내지 않는다([advanceOnDone]=false). muted 면 무음 no-op 으로 즉시 IDLE 로
         * 정착한다(상대 [replay] 정합). 상대 턴 재합성용 [lastPcm] 은 건드리지 않는다.
         */
        fun playClip(
            pcm: ByteArray,
            sampleRate: Int,
        ) {
            val token = startNewSession()
            this.advanceOnDone = false
            currentJob =
                scope.launch {
                    if (settingsRepo.current().muted) {
                        finish(token, PlaybackState.IDLE, advance = false)
                        return@launch
                    }
                    playPcm(token, pcm, sampleRate)
                }
        }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsPlaybackCoordinatorTest*'`
Expected: PASS (all existing tests + the 2 new ones). Confirm `BUILD SUCCESSFUL` and detekt clean.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt
git commit -m "feat(tts): add playClip to replay a raw PCM buffer through the shared player

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Learner bubble speaker button (`LearnerTurn`) + list wiring

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/ChatBubble.kt` (rename `OpponentFirstLineHeight` → `BubbleFirstLineHeight`; add `LearnerTurn` composable)
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt` (add `learnerOrdinalAt` helper; add 2 defaulted params to `DialogueTurnContent`; render `LearnerTurn` via `itemsIndexed`)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/LearnerClipRenderingTest.kt` (create; pure JVM unit test for the ordinal helper)

**Interfaces:**
- Consumes: existing private `ReplayButton(onReplay)` and internal `englishLocaleText(text)` (both in `ChatBubble.kt`, same package); `DialogueMessage.Learner`/`.Opponent` (`DialogueUiState.kt`, same package).
- Produces:
  - `fun LearnerTurn(text: String, modifier: Modifier = Modifier, hasAudio: Boolean = false, onReplay: () -> Unit = {})` — renders the right-aligned learner bubble; when `hasAudio`, prepends a `ReplayButton` to its left.
  - `internal fun learnerOrdinalAt(messages: List<DialogueMessage>, index: Int): Int` — the 0-based learner-turn ordinal of the message at `index` (= number of `Learner` messages strictly before it). Matches the ViewModel's `count { Learner } - 1` keying in Task 3.
  - `DialogueTurnContent(..., learnerClipIndices: Set<Int> = emptySet(), onPlayLearnerClip: (Int) -> Unit = {})`.

**Context:** Today the learner message renders as a bare `ChatBubble(text = message.english, isLearner = true)` — no chrome. The opponent's speaker button (`ReplayButton`, 28dp circular `OceIcon.VolumeUp` on `background` with `onSurfaceVariant` tint) sits *inside* the opponent bubble. For the right-aligned learner bubble on `primary`, we place the identical button *outside* the bubble to its left (on the thread background) so the existing button colors read correctly. `LearnerTurn` with `hasAudio = false` delegates straight to the untouched `ChatBubble`, so all existing screenshots render pixel-identically. The shared 23dp first-line-height constant is renamed `BubbleFirstLineHeight` since both bubbles now use it.

- [ ] **Step 1: Write the failing test for the ordinal helper**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/LearnerClipRenderingTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.turn

import org.junit.Assert.assertEquals
import org.junit.Test

class LearnerClipRenderingTest {
    private val messages =
        listOf(
            DialogueMessage.Opponent("hi"), // index 0
            DialogueMessage.Learner("one"), // index 1 -> learner ordinal 0
            DialogueMessage.Opponent("ok"), // index 2
            DialogueMessage.Learner("two"), // index 3 -> learner ordinal 1
            DialogueMessage.Learner("three"), // index 4 -> learner ordinal 2
        )

    @Test
    fun `learner ordinal counts learner bubbles strictly before the index`() {
        assertEquals(0, learnerOrdinalAt(messages, 1))
        assertEquals(1, learnerOrdinalAt(messages, 3))
        assertEquals(2, learnerOrdinalAt(messages, 4))
    }

    @Test
    fun `first learner bubble is ordinal zero`() {
        assertEquals(0, learnerOrdinalAt(listOf(DialogueMessage.Learner("solo")), 0))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*LearnerClipRenderingTest*'`
Expected: FAIL — compile error (`learnerOrdinalAt` unresolved).

- [ ] **Step 3: Add the `learnerOrdinalAt` helper**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt`, add this top-level internal function just below the imports, immediately before the `DialogueTurnScreen` KDoc/`@Composable` (around line 64):

```kotlin
/**
 * [messages] 안 [index] 위치 말풍선의 0-based 학습자 턴 순번(= 그 앞에 놓인 [DialogueMessage.Learner] 개수).
 * ViewModel 이 클립을 저장할 때 쓰는 키(`count { Learner } - 1`, append 직후)와 정확히 일치한다.
 */
internal fun learnerOrdinalAt(
    messages: List<DialogueMessage>,
    index: Int,
): Int = messages.take(index).count { it is DialogueMessage.Learner }
```

(This is O(index), called once per item, so full-list render is O(n²). Fine at real session lengths — a dialogue is ~5–10 turns / ~10–20 messages — so no precomputation is warranted. If sessions ever grow to hundreds of turns, hoist a running ordinal instead.)

- [ ] **Step 4: Run the test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*LearnerClipRenderingTest*'`
Expected: PASS (2/2).

- [ ] **Step 5: Rename the shared first-line-height constant**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/ChatBubble.kt`, rename the private constant (currently line 61) and its single use (currently line 184). Change the declaration from:

```kotlin
private val OpponentFirstLineHeight = 23.dp
```

to:

```kotlin
private val BubbleFirstLineHeight = 23.dp
```

and change its use inside `OpponentBubble` from:

```kotlin
            modifier = Modifier.height(OpponentFirstLineHeight),
```

to:

```kotlin
            modifier = Modifier.height(BubbleFirstLineHeight),
```

- [ ] **Step 6: Add the `LearnerTurn` composable**

In the same `ChatBubble.kt`, add this public composable immediately after the `ChatBubble(...)` function's closing `}` (currently line 102, before the `OpponentTurn` KDoc):

```kotlin
/**
 * 학습자 발화 1턴. 기본은 우측 primary 말풍선([ChatBubble])만 렌더한다(기존 스크린샷 계약 유지).
 * [hasAudio] 면 말풍선 왼쪽에 자기 녹음 재생 스피커 버튼([ReplayButton], 상대역 `다시 듣기`와 동일 외형)을
 * 함께 얹는다 — 버튼은 말풍선 첫 줄 높이 래퍼에 center 배치돼 텍스트가 여러 줄이어도 첫 줄 중앙에 고정된다.
 * 버튼은 primary 말풍선 바깥(스레드 배경) 좌측에 둔다 — 상대역 버튼색(background/onSurfaceVariant)이 primary
 * 위에서 뭉개지지 않게 하기 위함이다.
 */
@Composable
fun LearnerTurn(
    text: String,
    modifier: Modifier = Modifier,
    hasAudio: Boolean = false,
    onReplay: () -> Unit = {},
) {
    if (!hasAudio) {
        ChatBubble(text = text, isLearner = true, modifier = modifier)
        return
    }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * 0.8f
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 스피커 버튼을 첫 줄 높이(23dp) 래퍼에 center — 아이콘 center 를 말풍선 첫 줄 center 에 맞춘다.
            Box(
                modifier = Modifier.height(BubbleFirstLineHeight),
                contentAlignment = Alignment.Center,
            ) {
                ReplayButton(onReplay = onReplay)
            }
            Text(
                text = englishLocaleText(text),
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier =
                    Modifier
                        .widthIn(max = maxBubbleWidth)
                        .clip(OceTheme.shapes.radius18)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md),
            )
        }
    }
}
```

(All referenced symbols — `BoxWithConstraints`, `Row`, `Box`, `Arrangement`, `Alignment`, `Modifier.height`/`.align`/`.widthIn`/`.clip`/`.background`/`.padding`, `Text`, `MaterialTheme`, `OceTheme`, `ReplayButton`, `englishLocaleText`, `dp` — are already imported or private in `ChatBubble.kt`. No new imports.)

- [ ] **Step 7: Add the two `DialogueTurnContent` params and render `LearnerTurn`**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt`:

(a) Add two defaulted params to `DialogueTurnContent`, immediately after `opponentSpeaker: String = "Emma",` (currently line 134):

```kotlin
    opponentSpeaker: String = "Emma",
    // 자기 녹음이 있는 학습자 말풍선의 0-based 순번 집합(세션 메모리). 미주입(스텁·프리뷰·스크린샷)이면
    // 빈 집합이라 스피커 버튼이 없다(기존 렌더 유지).
    learnerClipIndices: Set<Int> = emptySet(),
    // 학습자 말풍선 스피커 탭 → 해당 순번 클립 재생. 미주입이면 no-op.
    onPlayLearnerClip: (Int) -> Unit = {},
```

(b) Change the message loop from `items(messages)` to `itemsIndexed(messages)` and render `LearnerTurn`. Replace the current block (currently lines 171-183):

```kotlin
                items(messages) { message ->
                    when (message) {
                        is DialogueMessage.Opponent ->
                            OpponentTurn(
                                text = message.english,
                                speaker = opponentSpeaker,
                                onReplay = { onReplay(message.english) },
                                onToggleTranslation = onToggleTranslation,
                            )
                        is DialogueMessage.Learner ->
                            ChatBubble(text = message.english, isLearner = true)
                    }
                }
```

with:

```kotlin
                itemsIndexed(messages) { index, message ->
                    when (message) {
                        is DialogueMessage.Opponent ->
                            OpponentTurn(
                                text = message.english,
                                speaker = opponentSpeaker,
                                onReplay = { onReplay(message.english) },
                                onToggleTranslation = onToggleTranslation,
                            )
                        is DialogueMessage.Learner -> {
                            val ordinal = learnerOrdinalAt(messages, index)
                            LearnerTurn(
                                text = message.english,
                                hasAudio = ordinal in learnerClipIndices,
                                onReplay = { onPlayLearnerClip(ordinal) },
                            )
                        }
                    }
                }
```

(c) Update the import. `items(...)` at line 171 is its **only** use in the file, and Step 7(b) replaces it with `itemsIndexed(...)`, so the old import becomes definitively unused — leaving it in fails detekt's `UnusedImports` rule (active via `buildUponDefaultConfig = true`, not disabled in `android/config/detekt/detekt.yml`). **Remove** `import androidx.compose.foundation.lazy.items` (currently line 34) and **replace** it with:

```kotlin
import androidx.compose.foundation.lazy.itemsIndexed
```

(The unrelated `item(key = "opponentTypingSkeleton")` call for the typing skeleton uses `LazyListScope.item`, which is not an import — it needs no change.)

- [ ] **Step 8: Run the full verify (screenshots must stay green)**

Run: `scripts/verify-android.sh`
Expected: `BUILD SUCCESSFUL`, detekt clean, `LearnerClipRenderingTest` + `TtsPlaybackCoordinatorTest` green, and the goldens `DialogueTurnScreenshotTest` / `SessionFlowScreenshotTest` PASS unchanged (default `learnerClipIndices = emptySet()` → every learner bubble `hasAudio = false` → `LearnerTurn` delegates to the identical `ChatBubble`).

- [ ] **Step 9: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/ChatBubble.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueTurnScreen.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/LearnerClipRenderingTest.kt
git commit -m "feat(turn): add learner-bubble speaker button (LearnerTurn) gated by per-turn audio

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Retain the recorded clip in the ViewModel and drive learner replay

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt`
  - `GeneratedDialogueSessionViewModel`: new observable `learnerClipIndices` state + private `learnerClips` map + `pendingClip`; stash on `stopRecording`, commit/clear on `onAnalysisState`; new `playLearnerClip(index)`.
  - `GeneratedDialogueSessionContent`: two new defaulted params forwarded to `DialogueTurnContent`.
  - `GeneratedDialogueSessionRoute`: supply the indices + play callback from the ViewModel.

**Interfaces:**
- Consumes: `RecordingResult.Captured` (already imported, `GeneratedDialogueSession.kt:23`); `TtsPlaybackCoordinator.playClip(pcm, sampleRate)` (Task 1); `DialogueTurnContent(..., learnerClipIndices, onPlayLearnerClip)` (Task 2).
- Produces: nothing downstream (integration endpoint).

**Context:** The record→transcribe→append flow is: `stopRecording()` gets `RecordingResult.Captured(pcm, sampleRate, …)` and calls `speaking.analyze(result, sid)`; later `onAnalysisState(Result)` runs `turnState.appendLearnerAnswer(transcript)`, appending the learner bubble. At that moment the just-appended bubble's 0-based learner ordinal is `turnState.messages.count { it is DialogueMessage.Learner } - 1` (the same expression the code already uses at the `deep` turn-index line). We stash the `Captured` in `stopRecording` (only when we actually proceed to analysis), then in `onAnalysisState` move it into `learnerClips[ordinal]` on `Result` and drop it on `Empty`/`Failed`. `learnerClipIndices` (an observable `Set<Int>`) mirrors the map keys so the bubble recomposes to show its button the instant the clip lands. Text-mode submissions (`onSubmitText`) never set `pendingClip`, so their bubbles get no button. The map lives for the whole session (scroll up → replay any past turn) and is never persisted — on process-kill/restore it starts empty, so restored bubbles show no button (matching the opponent replay). Two safety details: (a) `playLearnerClip` carries the same `turnPhase == OpponentTurn` no-op guard as `replayOpponent`, so tapping a clip during opponent auto-speech can't cancel the in-flight `playTurn` and stall turn progression (Step 4); (b) `reconcileLearnerClips` drops clips whose ordinal no longer exists after a `turnState` reset (Step 5). No VM unit test here (a full VM harness is out of scope, matching the sibling opponent-speaker plan); Task 1 covers playback and Task 2 covers the ordinal/render — the green bar here is a clean `scripts/verify-android.sh`.

- [ ] **Step 1: Add the clip store + observable index state**

In `GeneratedDialogueSessionViewModel`, add these declarations immediately after the `textValue` state block (currently ends at line 263, `var textValue by mutableStateOf("") private set`):

```kotlin

        /**
         * 자기 녹음 재생용 세션 메모리 클립 저장소. 키 = 0-based 학습자 턴 순번([learnerOrdinalAt] 와 동일).
         * 영속하지 않는다(프로세스킬/복원 시 비어 시작 — 상대 replay 정합). VM 소멸과 함께 GC 된다.
         */
        private val learnerClips = mutableMapOf<Int, RecordingResult.Captured>()

        /** 아직 전사 대기 중인(= append 전) 방금 캡처된 클립. [onAnalysisState] 가 소비하고 비운다. */
        private var pendingClip: RecordingResult.Captured? = null

        /**
         * 자기 녹음이 있는 학습자 말풍선 순번 집합. 관찰 가능 상태라, 클립이 들어오는 순간 해당 말풍선이
         * 재구성돼 스피커 버튼이 나타난다. Route 가 [GeneratedDialogueSessionContent] 로 흘려보낸다.
         */
        var learnerClipIndices by mutableStateOf<Set<Int>>(emptySet())
            private set
```

- [ ] **Step 2: Stash the captured clip in `stopRecording`**

In `stopRecording()` (the `is RecordingResult.Captured ->` branch, currently lines 468-477), record the clip when we proceed to analysis. Change:

```kotlin
                    is RecordingResult.Captured -> {
                        val sid = currentSessionId()
                        if (sid != null) {
                            micState = MicState.Analyzing
                            speaking.analyze(result, sid)
                        } else {
                            micState = MicState.Ready
                            retryHint = HINT_ERROR
                        }
                    }
```

to:

```kotlin
                    is RecordingResult.Captured -> {
                        val sid = currentSessionId()
                        if (sid != null) {
                            pendingClip = result // 전사 성공 시 append 되는 말풍선에 붙일 자기 녹음
                            micState = MicState.Analyzing
                            speaking.analyze(result, sid)
                        } else {
                            micState = MicState.Ready
                            retryHint = HINT_ERROR
                        }
                    }
```

- [ ] **Step 3: Commit the clip on transcript, drop it on failure**

In `onAnalysisState(state)` (currently lines 491-510), attach the pending clip to the appended learner bubble on `Result`, and clear it on `Empty`/`Failed`. Change:

```kotlin
                is SpeakingAnalysisState.Result -> {
                    turnState.appendLearnerAnswer(state.transcript)
                    micState = MicState.Complete
                    triggerFeedback(state.transcript)
                    persistResume()
                }
                SpeakingAnalysisState.Empty -> {
                    micState = MicState.Ready
                    retryHint = HINT_RETRY
                }
                SpeakingAnalysisState.Failed -> {
                    micState = MicState.Ready
                    retryHint = HINT_ERROR
                }
```

to:

```kotlin
                is SpeakingAnalysisState.Result -> {
                    turnState.appendLearnerAnswer(state.transcript)
                    // 방금 append 된 학습자 말풍선의 0-based 순번에 이 턴의 녹음을 매핑(있으면).
                    pendingClip?.let { clip ->
                        val ordinal = turnState.messages.count { it is DialogueMessage.Learner } - 1
                        learnerClips[ordinal] = clip
                        learnerClipIndices = learnerClips.keys.toSet()
                    }
                    pendingClip = null
                    micState = MicState.Complete
                    triggerFeedback(state.transcript)
                    persistResume()
                }
                SpeakingAnalysisState.Empty -> {
                    pendingClip = null
                    micState = MicState.Ready
                    retryHint = HINT_RETRY
                }
                SpeakingAnalysisState.Failed -> {
                    pendingClip = null
                    micState = MicState.Ready
                    retryHint = HINT_ERROR
                }
```

- [ ] **Step 4: Add `playLearnerClip(index)`**

In `GeneratedDialogueSessionViewModel`, add this public method next to the other playback methods, immediately after `replayOpponent(...)` (currently ends at line 428):

```kotlin

        /**
         * 학습자 말풍선 스피커 탭 → 그 순번의 세션 메모리 클립을 재생(상대 발화와 단일 재생 권위 공유).
         * 상대 자동발화 중(OpponentTurn)엔 no-op — [replayOpponent] 와 **동일한 가드**다. [TtsPlaybackCoordinator.playClip]
         * 은 `startNewSession()` 으로 진행 중 재생을 취소하는데, 그때 취소되는 상대 자동발화(`playTurn`,
         * advanceOnDone=true)는 완료 신호(completions)를 못 내 [onOpponentTtsDone]→completeOpponentTurn 이
         * 영영 호출되지 않아 턴이 OpponentTurn 에 갇힌다. 이 가드가 그 교착을 봉인한다(회귀 방지).
         */
        fun playLearnerClip(index: Int) {
            if (turnState.turnPhase == TurnPhase.OpponentTurn) return
            learnerClips[index]?.let { tts.playClip(it.pcm, it.sampleRate) }
        }
```

**Why the guard (do not drop it):** the learner bubble's speaker button is always visible in the scrollable message list regardless of `turnPhase`, so a user can tap it while the opponent is auto-speaking. Without this guard the tap cancels the in-flight opponent `playTurn` job before it emits its completion, and the session never advances past the opponent turn. `replayOpponent` (`GeneratedDialogueSession.kt:425-428`) carries the identical guard for exactly this reason. Replay during the learner's own turn — the primary use case (replay right after recording, or scroll up to a past learner bubble during a later learner turn) — is unaffected, since `turnPhase == LearnerTurn` then.

- [ ] **Step 5: Drop stale clips when the turn machine resets**

`turnState.accept(state)` internally calls `GeneratedDialogueState.reset()` (which empties `messages`) when the coordinator's turn list shrinks (`GeneratedDialogueSession.kt:830`, a genuinely new/restarted generation). If that fires mid-session, learner ordinals restart at 0 while `learnerClips` still holds old-ordinal entries — a stale recording could be misattributed to a fresh bubble at the same ordinal. Reconcile the clip store against the current learner-bubble count so any clip whose ordinal no longer exists is dropped.

Add this private helper to the ViewModel (next to `playLearnerClip`):

```kotlin

        /**
         * turnState 리셋(생성 재시작으로 turns 축소, [GeneratedDialogueState] `reset`) 등으로 학습자 말풍선이
         * 줄면, 더 이상 존재하지 않는 순번의 세션 클립을 버린다(stale 오귀속 방지). 정상 운영 시엔 모든 클립
         * 순번이 현재 학습자 수 미만이라 no-op 이다.
         */
        private fun reconcileLearnerClips() {
            val learnerCount = turnState.messages.count { it is DialogueMessage.Learner }
            if (learnerClips.keys.any { it >= learnerCount }) {
                learnerClips.keys.retainAll { it < learnerCount }
                learnerClipIndices = learnerClips.keys.toSet()
            }
        }
```

Then call it in `acceptGenerationState` right after `turnState.accept(state)` (currently line 406):

```kotlin
        private fun acceptGenerationState(state: DialogueGenState) {
            if (state is DialogueGenState.Ready) latestTurns = state.turns
            turnState.accept(state)
            reconcileLearnerClips() // turns 축소 리셋 시 사라진 순번의 stale 클립 파기
            assignSpeakerIfNeeded()
            persistResume()
        }
```

- [ ] **Step 6: Forward the two params through `GeneratedDialogueSessionContent`**

In `GeneratedDialogueSessionContent` (signature currently ends at line 703-704 with `opponentSpeaker: String = "Emma",` then `) {`), add the two params after `opponentSpeaker`:

```kotlin
    opponentSpeaker: String = "Emma",
    // 자기 녹음이 있는 학습자 말풍선 순번 집합. 미주입(프리뷰·테스트)이면 빈 집합(버튼 없음, 스크린샷 계약 유지).
    learnerClipIndices: Set<Int> = emptySet(),
    // 학습자 말풍선 스피커 탭 콜백. 미주입이면 no-op.
    onPlayLearnerClip: (Int) -> Unit = {},
) {
```

Then forward them in the `DialogueTurnContent(...)` call inside that function (currently ends with `opponentSpeaker = opponentSpeaker,` at line 727):

```kotlin
        onReplay = onReplay,
        opponentSpeaker = opponentSpeaker,
        learnerClipIndices = learnerClipIndices,
        onPlayLearnerClip = onPlayLearnerClip,
    )
```

- [ ] **Step 7: Supply the indices + callback from the Route**

In `GeneratedDialogueSessionRoute`, extend the `GeneratedDialogueSessionContent(...)` call. Change its tail (currently ends with the `opponentSpeaker = viewModel.opponentSpeaker?.name ?: "Emma",` line at 153):

```kotlin
        onReplay = { text -> viewModel.replayOpponent(text) },
        // 상대 발화자 이름을 말풍선에 반영. 미배정(초기·sessionId 미도착)이면 "Emma" 폴백.
        opponentSpeaker = viewModel.opponentSpeaker?.name ?: "Emma",
        // 자기 녹음 재생: 어떤 학습자 말풍선에 버튼을 띄울지 + 탭 시 그 순번 클립 재생.
        learnerClipIndices = viewModel.learnerClipIndices,
        onPlayLearnerClip = { index -> viewModel.playLearnerClip(index) },
    )
```

- [ ] **Step 8: Run the full verify**

Run: `scripts/verify-android.sh`
Expected: `BUILD SUCCESSFUL`, detekt clean, all unit + Roborazzi screenshot tests green (`learnerClipIndices` defaults to `emptySet()` and no screenshot injects clips, so learner bubbles stay button-less and goldens are unchanged).

- [ ] **Step 9: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt
git commit -m "feat(turn): retain learner recordings in session memory and drive self-replay

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Manual verification (post-implementation, on device)

Not a task step (needs a real device with a microphone), but the acceptance check:
1. Install (`scripts/verify-android.sh :app:installDebug`) and start a session. On the learner turn, record a spoken answer with the mic and let it transcribe.
2. Confirm the appended learner bubble now shows a speaker button to its left; tap it and confirm you hear your own recording play back.
3. Advance a few turns, scroll up, and confirm each past learner bubble's speaker button replays that turn's recording.
4. While the opponent's line is auto-speaking (opponent turn), scroll up and tap a past learner clip — confirm the tap is a no-op (the guard) and, critically, that the opponent turn still auto-advances to your next learner turn afterward (no stall).
5. Submit a turn via the text keyboard instead of voice — confirm that bubble has NO speaker button (no recording exists).
6. Mute (settings) then tap a learner clip during your own turn — confirm it is a silent no-op.
7. Kill/restore the process (or return via 이어하기) — confirm restored learner bubbles show no speaker button (clips are in-memory only, by design).

## Self-Review

**Spec coverage:**
- "사용자 음성 입력에 대한 저장 기능" → Task 3 retains the already-captured `RecordingResult.Captured` in `learnerClips` (session memory) instead of discarding it after STT. ✅
- "상대방 채팅 말풍선과 동일하게 … 스피커 버튼" → Task 2 reuses the exact opponent `ReplayButton` (`OceIcon.VolumeUp`, same size/tint) on the learner bubble. ✅
- "자신이 녹음한 내용을 들을 수 있도록" → Task 1 `playClip` plays the raw recorded PCM; Task 3 wires the tap to it. ✅
- Persistence scope (user decision: 세션 메모리 유지) → in-memory map, all session learner turns replayable, no disk/schema changes (Global Constraints). ✅

**Correctness guards:**
- Turn-stall regression (playback cancels in-flight opponent auto-speech): `playLearnerClip` no-ops during `OpponentTurn`, mirroring `replayOpponent` (Task 3 Step 4). ✅
- Stale-clip misattribution after `turnState` reset: `reconcileLearnerClips` drops orphaned ordinals (Task 3 Step 5). ✅
- detekt `UnusedImports`: the old `items` import is removed, not left conditional (Task 2 Step 7c). ✅

**Placeholder scan:** none — every step carries exact code, paths, and commands.

**Type consistency:** `TtsPlaybackCoordinator.playClip(pcm: ByteArray, sampleRate: Int)` (Task 1) is called by the VM as `tts.playClip(it.pcm, it.sampleRate)` where `it: RecordingResult.Captured` (Task 3) — `Captured.pcm: ByteArray` and `.sampleRate: Int` match. `learnerOrdinalAt(List<DialogueMessage>, Int): Int` (Task 2) mirrors the VM's `count { Learner } - 1` key (Task 3). `learnerClipIndices: Set<Int>` and `onPlayLearnerClip: (Int) -> Unit` have identical signatures across `DialogueTurnContent`, `GeneratedDialogueSessionContent`, and the Route. `LearnerTurn(text, modifier, hasAudio, onReplay)` (Task 2) is invoked with exactly those names in `DialogueTurnContent` (Task 2 Step 7). Consistent.
