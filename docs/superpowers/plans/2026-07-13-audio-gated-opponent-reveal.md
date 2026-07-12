# Audio-Gated Opponent Reveal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep the opponent "typing" skeleton visible until the turn's audio actually starts, then reveal the opponent chat bubble in sync with the first sound — instead of showing the text after a fixed 700 ms and then sitting in silence while the audio engine loads.

**Architecture:** Today the dialogue route reveals the opponent bubble after a fixed `delay(700ms)` and *then* starts TTS, so the text is visible during the 1–2 s device-engine warm-up (dead air). We invert this: start synthesis while the skeleton stays up, and gate the bubble's reveal on a new `TtsPlaybackCoordinator.audioReady` signal that fires exactly when audio truly begins. For the opponent path (device-only TTS) the true "audio started" moment is the Android `TextToSpeech` `onStart` callback *after* engine init — currently ignored — not the point where `playFromDevice` optimistically sets `PLAYING`. Failure / mute / text-only paths keep revealing + advancing through the existing `completions` and `ERROR_TEXT_ONLY` → `completeOpponentTurn()` → `commitReveal()` safety nets, so the skeleton can never hang forever.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, kotlinx.coroutines (`StateFlow`/`SharedFlow`), JUnit4 + kotlinx-coroutines-test. Single Gradle module `:app`.

## Global Constraints

- **Verification command:** always run gradle via `scripts/verify-android.sh` from the repo root — never bare `./gradlew` in a worktree (shared-cache contamination + missing `google-services.json`; see `docs/agents/android-verification.md`). The default task set is `:app:detekt :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:testReleaseUnitTest`.
- **Single module:** all production code under `android/app/src/main/kotlin/...`; all unit tests under `android/app/src/test/kotlin/...`.
- **No new UI:** reuse the existing `OpponentTypingSkeleton` — this change only alters *when* `opponentTyping` flips, not how it renders. No new screenshot goldens.
- **reduce-motion:** the skeleton is now an audio-loading gate, not a motion flourish, so it must stay visible under `reduceMotion` (it already renders as a static, non-shimmering, non-fading placeholder). Do **not** re-introduce any `reduceMotion → skip skeleton` branch on the generated route.
- **Never an infinite skeleton:** every terminal TTS outcome (mute, `FAILED`, `ERROR_TEXT_ONLY`, watchdog) must still reveal the bubble and advance the turn via the pre-existing `completeOpponentTurn()` paths. Do not remove those collectors.
- **Uniform across all opponent turns:** the audio-gated reveal applies to every opponent turn, not just the first. Subsequent turns have negligible synth latency, so the skeleton is brief there.
- **Style:** match the surrounding Korean KDoc/comment density and the existing coroutine-test idioms (hand-rolled fakes, `runTest`, `UnconfinedTestDispatcher(testScheduler)`, `advanceUntilIdle()` — no Turbine, no mocking library).

---

### Task 1: `audioReady` signal fired at true audio start

Add a non-conflating `audioReady` `SharedFlow` to `TtsPlaybackCoordinator`, emitted exactly when audio truly begins. For the server (PCM) path that is the start of `player.play`. For the device path it is the framework `onStart` callback (after engine init) — so we add an `onStart` hook to the `DeviceTts` seam and stop setting `PLAYING` prematurely before `deviceTts.speak()`.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/DeviceTts.kt` (interface `speak` signature)
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/AndroidDeviceTts.kt:37-82` (invoke `onStart` from framework listener)
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt` (add `audioReady`; move device `PLAYING` to an `onPlaybackStarted`)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt` (update `FakeDeviceTts`; add `audioReady` tests)

**Interfaces:**
- Produces: `TtsPlaybackCoordinator.audioReady: SharedFlow<Unit>` — emits once per turn when audio actually starts. Consumers must phase-guard (replay / self-clip playback also reach `PLAYING`). Consumed by Task 3's ViewModel collector.
- Produces: `DeviceTts.speak(text, gender, speechRate, onStart: () -> Unit = {}): DeviceTtsResult` — `onStart` is invoked when the utterance's audio actually begins (never for `LANGUAGE_MISSING` / init `ERROR`).

- [ ] **Step 1: Write the failing coordinator tests**

Open `TtsPlaybackCoordinatorTest.kt`. First update the shared `FakeDeviceTts` (currently lines 68-85) so its `speak` override matches the new interface and faithfully fires `onStart` only when audio would actually begin:

```kotlin
private class FakeDeviceTts(
    var result: DeviceTtsResult = DeviceTtsResult.COMPLETED,
    var delayMs: Long = 0,
) : DeviceTts {
    var callCount = 0

    override suspend fun speak(
        text: String,
        gender: String?,
        speechRate: Float,
        onStart: () -> Unit,
    ): DeviceTtsResult {
        callCount++
        if (delayMs > 0) delay(delayMs)
        // Faithful to AndroidDeviceTts: onStart fires only when audio actually begins — i.e. the
        // engine initialized and the utterance started. LANGUAGE_MISSING / ERROR return before that.
        if (result == DeviceTtsResult.COMPLETED) onStart()
        return result
    }

    override fun stop() = Unit
}
```

Add an `audioReady` collector helper next to `collectCompletions` (after line 354):

```kotlin
    /** Subscribe to audioReady before the action so the SharedFlow (replay=0) delivers. */
    private fun TestScope.collectAudioReady(coordinator: TtsPlaybackCoordinator): List<Unit> {
        val received = mutableListOf<Unit>()
        CoroutineScope(UnconfinedTestDispatcher(testScheduler)).launch {
            coordinator.audioReady.collect { received += it }
        }
        return received
    }
```

Add four tests inside `class TtsPlaybackCoordinatorTest`:

```kotlin
    @Test
    fun `audioReady emits when the server path starts playing`() =
        runTest {
            val coordinator =
                TtsPlaybackCoordinator(
                    FakeLlmApi(),
                    FakePcmPlayer(),
                    FakeDeviceTts(),
                    FakeSettings(),
                    coordScope(),
                )

            val ready = collectAudioReady(coordinator)
            coordinator.playTurn("Hello", "female")
            advanceUntilIdle()

            assertEquals(1, ready.size)
        }

    @Test
    fun `audioReady emits when the device path starts playing`() =
        runTest {
            val coordinator =
                TtsPlaybackCoordinator(
                    FakeLlmApi(),
                    FakePcmPlayer(),
                    FakeDeviceTts(result = DeviceTtsResult.COMPLETED),
                    FakeSettings(),
                    coordScope(),
                )

            val ready = collectAudioReady(coordinator)
            coordinator.playTurn("Hello", null, deviceOnly = true)
            advanceUntilIdle()

            assertEquals(1, ready.size)
        }

    @Test
    fun `audioReady does not emit when muted`() =
        runTest {
            val coordinator =
                TtsPlaybackCoordinator(
                    FakeLlmApi(),
                    FakePcmPlayer(),
                    FakeDeviceTts(),
                    FakeSettings(TtsSettings(muted = true)),
                    coordScope(),
                )

            val ready = collectAudioReady(coordinator)
            coordinator.playTurn("Hello", "female", deviceOnly = true)
            advanceUntilIdle()

            assertTrue(ready.isEmpty()) // muted → no audio; reveal comes from the completions path
        }

    @Test
    fun `audioReady does not emit when the device has no english voice`() =
        runTest {
            val coordinator =
                TtsPlaybackCoordinator(
                    FakeLlmApi(error = RuntimeException("server down")),
                    FakePcmPlayer(),
                    FakeDeviceTts(result = DeviceTtsResult.LANGUAGE_MISSING),
                    FakeSettings(),
                    coordScope(),
                )

            val ready = collectAudioReady(coordinator)
            coordinator.playTurn("Hello", "female", deviceOnly = true)
            advanceUntilIdle()

            assertTrue(ready.isEmpty()) // reveal comes from the ERROR_TEXT_ONLY safety net, not audioReady
        }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsPlaybackCoordinatorTest*'`
Expected: FAIL — compilation error `Unresolved reference: audioReady` and the `FakeDeviceTts.speak` override no longer matches `DeviceTts` (once the interface below is changed, this compiles; before that, the fake's 4-arg override does not match the 3-arg interface). It must not pass.

- [ ] **Step 3: Add the `onStart` hook to the `DeviceTts` interface**

In `DeviceTts.kt`, change the `speak` signature (lines 13-17) to:

```kotlin
    suspend fun speak(
        text: String,
        gender: String?,
        speechRate: Float,
        onStart: () -> Unit = {},
    ): DeviceTtsResult
```

Update the KDoc line above it to mention the new param, e.g. append: ` [onStart] is invoked once when the utterance's audio actually begins (after engine init); it never fires for LANGUAGE_MISSING or an init error.`

- [ ] **Step 4: Invoke `onStart` from the framework listener in `AndroidDeviceTts`**

In `AndroidDeviceTts.kt`, change the `speak` signature (lines 37-41) to add the param:

```kotlin
        override suspend fun speak(
            text: String,
            gender: String?,
            speechRate: Float,
            onStart: () -> Unit,
        ): DeviceTtsResult {
```

Inside `speak`, just before the `return suspendCancellableCoroutine { cont ->` (currently line 54), capture the callback to avoid shadowing the framework override's own `onStart`:

```kotlin
            val notifyStarted = onStart
```

Then change the listener's `onStart` override (currently line 57, `override fun onStart(id: String?) = Unit`) to:

```kotlin
                        override fun onStart(id: String?) {
                            if (id == utteranceId) notifyStarted()
                        }
```

Leave `onDone` / `onError` unchanged. (The `notifyStarted()` call runs on the engine's binder thread; its only effect downstream is a thread-safe `StateFlow` write and `SharedFlow.tryEmit`, so no additional synchronization is needed.)

- [ ] **Step 5: Add `audioReady` and move device `PLAYING` in `TtsPlaybackCoordinator`**

In `TtsPlaybackCoordinator.kt`, right after the `completions` declaration (after line 49), add:

```kotlin
        /** emits once when the current turn's audio actually begins — the server PCM playback
         *  start, or the device engine's onStart after init. The opponent bubble reveal is gated
         *  on this so the "typing" skeleton stays up through synthesis / engine-init LOADING and
         *  the bubble appears in sync with the first audible sound (not before). Consumers must
         *  phase-guard — replay and self-clip playback also reach PLAYING. */
        private val _audioReady = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val audioReady: SharedFlow<Unit> = _audioReady.asSharedFlow()
```

In `playPcm` (lines 200-201), after `_state.value = PlaybackState.PLAYING`, add the emit:

```kotlin
            if (token != sessionToken) return
            _state.value = PlaybackState.PLAYING
            _audioReady.tryEmit(Unit)
```

Replace `playFromDevice` (lines 213-233) so it no longer sets `PLAYING` before the engine may still be initializing, and instead promotes to `PLAYING` from `onStart`:

```kotlin
        private suspend fun playFromDevice(
            token: Long,
            text: String,
            gender: String?,
            rate: Float,
        ) {
            if (token != sessionToken) return
            // No premature PLAYING here: the device engine may still be initializing on the first
            // utterance (the 1–2s first-audio load). PLAYING + audioReady fire from onStart, when
            // audio actually begins, so the opponent skeleton stays up until then.
            val result =
                withTimeoutOrNull(DEVICE_WATCHDOG_MS) {
                    deviceTts.speak(text, gender, rate) { onPlaybackStarted(token) }
                }
            if (token != sessionToken) return
            when (result) {
                null -> finish(token, PlaybackState.FAILED, advance = true) // 7s watchdog fired
                DeviceTtsResult.COMPLETED -> finish(token, PlaybackState.IDLE, advance = true)
                DeviceTtsResult.LANGUAGE_MISSING ->
                    finish(token, PlaybackState.ERROR_TEXT_ONLY, advance = false) // retry, no advance
                DeviceTtsResult.ERROR -> finish(token, PlaybackState.FAILED, advance = true)
            }
        }

        /** Device engine reported audio actually started — promote to PLAYING and signal reveal.
         *  A stale token (a newer turn started) is ignored. */
        private fun onPlaybackStarted(token: Long) {
            if (token != sessionToken) return
            _state.value = PlaybackState.PLAYING
            _audioReady.tryEmit(Unit)
        }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsPlaybackCoordinatorTest*'`
Expected: PASS — all existing coordinator tests plus the four new `audioReady` tests. (Existing tests are unaffected: `LANGUAGE_MISSING` still ends `ERROR_TEXT_ONLY`, device `ERROR` still `FAILED`, mute still emits `completions` with no audio — only the intermediate `PLAYING` moment moved.)

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/DeviceTts.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/AndroidDeviceTts.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt
git commit -m "feat(tts): emit audioReady when opponent audio truly starts"
```

---

### Task 2: Pending-line accessor + phase-guarded audio-ready reveal

Give the turn state machine a way to read the not-yet-revealed opponent line (so the route can start synthesis before revealing it), and add a phase-guarded reveal entry point to `SessionTurnProgress` that the audio-ready signal will drive.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt` (add `GeneratedDialogueState.pendingOpponentEnglish()`, add `SessionTurnProgress.revealOnAudioReady()`)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueStateTest.kt`

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `GeneratedDialogueState.pendingOpponentEnglish(): String?` — the staged opponent line while it is awaiting reveal (read from `pending`, not `messages`). Retained after `commitReveal()`. This is a new accessor added *alongside* the existing `lastOpponentEnglish()` (which stays — it has its own test and simply stops being called by the route in Task 3).
- Produces: `SessionTurnProgress.revealOnAudioReady()` — reveals the staged opponent bubble and fires the durable-state notification, but only during `TurnPhase.OpponentTurn`; a no-op otherwise (so replay / self-clip `PLAYING` during a learner turn does not reveal or persist). Consumed by Task 3's ViewModel.

- [ ] **Step 1: Write the failing tests**

In `GeneratedDialogueStateTest.kt`, add three tests (the file already provides `ready(...)`, `model(...)`, `user(...)` helpers):

```kotlin
    @Test
    fun `pendingOpponentEnglish exposes the staged line before and after reveal`() {
        val state = GeneratedDialogueState()
        state.accept(ready(listOf(model("Hello"))))

        assertTrue(state.opponentTyping) // typing skeleton, not revealed
        assertTrue(state.messages.isEmpty())
        assertEquals("Hello", state.pendingOpponentEnglish())

        state.commitReveal()
        assertEquals("Hello", state.pendingOpponentEnglish()) // pending is retained after reveal
    }

    @Test
    fun `revealOnAudioReady reveals the staged opponent line during an opponent turn`() {
        val state = GeneratedDialogueState()
        state.accept(ready(listOf(model("Hello"))))
        var changes = 0
        val progress = SessionTurnProgress(state) { changes++ }

        progress.revealOnAudioReady()

        assertFalse(state.opponentTyping)
        assertEquals(DialogueMessage.Opponent("Hello"), state.messages.last())
        assertEquals(1, changes)
    }

    @Test
    fun `revealOnAudioReady is a no-op during a learner turn`() {
        val state = GeneratedDialogueState()
        state.accept(ready(listOf(model("Hello"))))
        state.completeOpponentTurn()
        state.accept(ready(listOf(model("Hello"), user("A coffee, please.", "커피 주세요."))))
        assertEquals(TurnPhase.LearnerTurn, state.turnPhase)
        var changes = 0
        val progress = SessionTurnProgress(state) { changes++ }

        progress.revealOnAudioReady()

        assertEquals(0, changes) // guarded out — no reveal, no persist
    }
```

Add the import for the assertion if missing — `org.junit.Assert.assertFalse` is already imported in this file.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*GeneratedDialogueStateTest*'`
Expected: FAIL — `Unresolved reference: pendingOpponentEnglish` and `Unresolved reference: revealOnAudioReady`.

- [ ] **Step 3: Add `pendingOpponentEnglish()`**

In `GeneratedDialogueSession.kt`, add a pending-line accessor immediately after the existing `lastOpponentEnglish()` declaration (after line 940). **Do not delete `lastOpponentEnglish()`** — it is still exercised by the existing test `GeneratedDialogueStateTest.kt:250` (`` `lastOpponentEnglish returns revealed opponent line and null after learner reply` ``); it simply stops being called by the route once Task 3 rewrites the route effect. Keeping it means no intermediate broken compile and no test churn.

```kotlin
    /** 아직 표시 대기(awaitingReveal)인 상대역 대사 = 이번 턴 스켈레톤 뒤에서 합성/발화할 대상. Route 가
     *  오디오 준비 전 선(先)합성을 위해 읽는다(표시 전이라 messages.last 가 아니라 pending 에서 가져온다). */
    fun pendingOpponentEnglish(): String? = pending.opponentEnglish
```

- [ ] **Step 4: Add `revealOnAudioReady()` to `SessionTurnProgress`**

In `GeneratedDialogueSession.kt`, inside `class SessionTurnProgress` (after `revealOpponentTurn()`, around line 825), add:

```kotlin
    /** 상대역 오디오가 실제 재생을 시작할 때(코디네이터 audioReady) 호출 — 표시 대기 중인 상대역 대사를
     *  표시한다. OpponentTurn 일 때만 실효해 replay·자기녹음 재생(LearnerTurn)의 재생 시작을 무시한다.
     *  commitReveal 은 멱등이라 이미 표시됐으면 append 는 no-op 다. */
    fun revealOnAudioReady() {
        if (state.turnPhase != TurnPhase.OpponentTurn) return
        state.commitReveal()
        onStateChanged()
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*GeneratedDialogueStateTest*'`
Expected: PASS — the three new tests plus all pre-existing state tests (including the untouched `lastOpponentEnglish` test). Nothing else in the tree changed, so the whole `:app` main source set still compiles.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueStateTest.kt
git commit -m "feat(turn): add pending-line accessor and audio-ready reveal seam"
```

---

### Task 3: Wire the ViewModel + route to reveal on audio-ready

Connect Task 1's signal to Task 2's seam: the ViewModel collects `tts.audioReady` and reveals the bubble; the route starts synthesis on the pending line while the skeleton stays up, and drops the fixed pre-reveal delay.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt` (ViewModel collector + `revealOnAudioReady()` forwarder; route `LaunchedEffect`; remove the `kotlinx.coroutines.delay` import)

**Interfaces:**
- Consumes: `TtsPlaybackCoordinator.audioReady` (Task 1), `SessionTurnProgress.revealOnAudioReady()` and `GeneratedDialogueState.pendingOpponentEnglish()` (Task 2).
- Produces: no new public surface. Behavior change only: opponent bubble reveal is now gated on real audio start.

- [ ] **Step 1: Add the ViewModel forwarder and audio-ready collector**

In `GeneratedDialogueSession.kt`, add a forwarder next to `revealOpponentTurn()` (line 410):

```kotlin
        fun revealOnAudioReady() = progress.revealOnAudioReady()
```

In the ViewModel `init` block, right after the existing `ERROR_TEXT_ONLY` collector (after line 348), add:

```kotlin
            // 상대역 오디오가 실제 재생을 시작하는 순간(코디네이터 audioReady: 디바이스 엔진 onStart / 서버 PCM
            // 재생 시작) 말풍선을 표시한다. 그 전까지는 스켈레톤이 유지돼 첫 오디오 API 로딩(디바이스 엔진 init)
            // 동안 "표시된 대사 + 침묵"이 아니라 "타이핑 중"으로 보인다. OpponentTurn 가드는 progress 안에 있어
            // replay/자기녹음 재생(LearnerTurn)의 PLAYING 은 무시된다.
            viewModelScope.launch { tts.audioReady.collect { revealOnAudioReady() } }
```

- [ ] **Step 2: Rewrite the route turn-progress effect**

In `GeneratedDialogueSession.kt`, replace the comment block + `effectiveSkeleton` val + `LaunchedEffect(state.opponentTurnSerial)` (lines 118-133) with:

```kotlin
    // 턴 진행: 상대역 턴에 진입하면 스켈레톤을 유지한 채 곧바로 대사를 합성/발화한다. 말풍선 표시는 더 이상
    // 고정 지연이 아니라 오디오가 실제 재생을 시작하는 순간(VM 의 tts.audioReady 수집 → revealOnAudioReady)이
    // 구동한다. 합성/엔진-init 지연(첫 오디오 API 로딩 등) 동안 "표시된 대사 + 침묵" 대신 "타이핑 중" 스켈레톤이
    // 보이고, 오디오가 준비되면 대사와 소리가 함께 나타난다. 자동진행(턴 마감)은 발화 완료(VM 의 completions/
    // ERROR_TEXT_ONLY 수집 → completeOpponentTurn)가 구동한다 — 발화 실패·mute 도 그 경로에서 표시+전진 폴백이라
    // 스켈레톤이 영원히 남지 않는다. reduce-motion 은 스켈레톤을 숨기지 않는다(청각 로딩 게이트이지 모션 아님 —
    // 스켈레톤은 reduceMotion 이면 시머/페이드 없이 정적으로 렌더된다).
    LaunchedEffect(state.opponentTurnSerial) {
        if (state.turnPhase == TurnPhase.OpponentTurn && state.sessionPhase == SessionPhase.InTurn) {
            state.pendingOpponentEnglish()?.let(viewModel::speakOpponent)
        }
    }
```

Then remove the now-unused import `import kotlinx.coroutines.delay` (line 46). Leave the `reduceMotion` route parameter in place — it still feeds `MicSessionDock` (line ~148).

- [ ] **Step 3: Verify the full build + unit suites pass**

Run: `scripts/verify-android.sh`
Expected: PASS — `:app:detekt`, `:app:compileDebugAndroidTestKotlin`, `:app:testDebugUnitTest`, `:app:testReleaseUnitTest` all green. In particular there must be no unused-import finding for `kotlinx.coroutines.delay` (removed in Step 2) and no detekt finding on the edited file.

- [ ] **Step 4: Manual smoke verification (device/emulator)**

This wiring (Compose route effect + ViewModel collector) is not covered by unit tests, so verify behavior on a running app:

1. Launch the debug app on a device/emulator with device TTS available (`scripts/verify-android.sh :app:assembleDebug` then install, or run from the IDE).
2. Start a new dialogue session and watch the **first** opponent turn: the "typing" skeleton must stay visible through the audio-engine warm-up, and the opponent bubble must appear **at the same moment** the voice starts — no window of visible text sitting in silence.
3. Confirm subsequent opponent turns behave the same (brief skeleton → bubble + voice together).
4. Toggle mute (TTS muted) and confirm the bubble still appears promptly and the turn advances (mute → `completions` reveal path).
5. If a device with English voice data is unavailable, the bubble should still eventually appear as text and advance (the `ERROR_TEXT_ONLY` / `FAILED` fallback) — never a stuck skeleton.

Record the outcome (pass/fail + any deviation) in the task notes.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt
git commit -m "feat(turn): reveal opponent bubble when audio starts, not on a timer"
```

---

## Notes / accepted trade-offs

- **Failure window:** the opponent path is device-only (`deviceOnly = true`), so a total TTS failure resolves within the 7 s device watchdog; during that window the skeleton stays up, then the bubble reveals as text and the turn advances (via `completions` → `completeOpponentTurn`). This is rarer and more honest than the old "text shown, then silence" behavior, so it is accepted rather than special-cased.
- **No minimum-skeleton floor:** we deliberately do not add an artificial minimum skeleton duration to prevent a fast-turn "flash." Synthesis + `AudioTrack`/engine setup realistically spans at least a frame, and adding a floor would re-introduce timer coupling. Revisit only if a flash is observed in manual verification.
- **Stub route untouched:** `DEFAULT_OPPONENT_SKELETON_DELAY_MS` and `rememberDialogueState` in `DialogueUiState.kt` belong to the legacy stub `DialogueTurnScreen` preview route (no TTS) and are left as-is.
- **Device-only in practice:** every opponent-turn TTS call (`speakOpponent`, `replayOpponent`) passes `deviceOnly = true`, so the reveal is always driven by the device `onStart` branch of `audioReady`. The server/PCM branch (the emit in `playPcm`) exists for completeness and replay symmetry but is not exercised by the opponent flow — don't spend manual-verification effort on server-path reveal timing.
- **Now-idle forwarders:** after Task 3 the route no longer calls `viewModel.revealOpponentTurn()`, so that forwarder and `SessionTurnProgress.revealOpponentTurn()` retain only their test caller (`SessionSnapshotStoreTest.kt`) and the internal defensive `commitReveal()` calls inside the state machine. This is expected — leave them in place; it is not a latent bug.
