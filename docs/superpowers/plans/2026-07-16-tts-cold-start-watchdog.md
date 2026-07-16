# TTS Cold-Start Watchdog Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the first opponent line from failing on a cold Gemini-TTS call. Let the *covered* synthesis paths (loading-quiz warm, per-turn prefetch) wait long enough for a cold call — including the server's 7s×2 retry — to finish and cache, while the *live* playback keeps its 8s device-fallback so it never stalls.

**Architecture:** Split the single `SERVER_WATCHDOG_MS = 8s` into two bounds in `TtsPlaybackCoordinator`: `SYNTH_WATCHDOG_MS` (16s) caps the synthesis job itself (used inside `synthesize`, so prefetch/warm tolerate a cold call), and `SERVER_WATCHDOG_MS` (8s, unchanged value) becomes a wrapper around `obtainAudio` in `playFromServer` only, preserving the live path's timely device fallback. Because the synthesis job runs on the coordinator's scope (a sibling of the live awaiter), when the live wrapper times out at 8s the cold synthesis keeps running in the background and still caches — so the line is instant on its next need (replay / a later turn). No backend change (chosen: client-tolerate, no keep-warm cost). The already-landed loading gate (commit `c5a9809`) covers the resulting ~10–14s cold-session loading with the quiz.

**Tech Stack:** Kotlin, Kotlin coroutines (`Dispatchers.Main.immediate` app scope), JUnit4 + kotlinx-coroutines-test. Client-only; no `functions/` change.

## Global Constraints

- Single Gradle module `:app`; all verification via `scripts/verify-android.sh` (never bare `./gradlew`). Known PRE-EXISTING unrelated reds outside any diff here: `SettingsScreen.kt:213` detekt (`LoopWithTooManyJumpStatements`) and `RecordsSkeleton*` Robolectric flakes — not this plan's defects; confirm via `git diff --stat` they are untouched.
- The synthesis budget must exceed the **server's** worst-case retry: `functions/src/providers/gemini.ts` uses `REQUEST_TIMEOUT_MS = 7000` × `MAX_ATTEMPTS = 2` = 14s. `SYNTH_WATCHDOG_MS` must be > 14s (16s, with margin) so a cold call that only succeeds on the server's second attempt still completes and caches. Note the client watchdog wraps the whole `/llm` round trip (any Cloud Function cold-start latency sits *in front of* the Gemini retry loop), so 16s is only a ~2s margin over the server's 14s — if Task 2 shows a covered path still device-falling-back on a cold call, raise `SYNTH_WATCHDOG_MS` (e.g., 20s) rather than assuming the design is wrong.
- The live-playback wait bound stays **8s** (`SERVER_WATCHDOG_MS` value unchanged) so `playFromServer` falls back to device TTS on time — do not raise it.
- Coordinator concurrency invariants are unchanged: single-threaded Main scope (no locks); the synthesis job caches its own result and self-evicts from `inFlight` via `invokeOnCompletion`; a cancelled awaiter must not cancel or evict a still-running synthesis. Do not alter `obtainAudio`'s structure beyond what this plan specifies.
- Remove ALL temporary `android.util.Log.d("DEBUG-tts", …)` instrumentation added during diagnosis (in `TtsPlaybackCoordinator.kt` and `GeneratedDialogueSession.kt`) as part of this work — none may be committed.

---

## File Structure

- **Modify:** `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt`
  - Add `SYNTH_WATCHDOG_MS = 16_000L`; keep `SERVER_WATCHDOG_MS = 8_000L`.
  - `synthesize`: use `SYNTH_WATCHDOG_MS` for its `withTimeoutOrNull`.
  - `playFromServer`: wrap `obtainAudio(...)` in `withTimeoutOrNull(SERVER_WATCHDOG_MS)` for the live device-fallback bound.
  - Update stale KDoc that the split invalidates: the class KDoc ("server (Gemini) synthesis with an 8s watchdog"), the companion comment ("Client watchdogs"), and — importantly — `awaitWarm`'s KDoc, which currently claims "Bounded by [obtainAudio]'s own 8s watchdog." After the split, `awaitWarm` (the loading gate's `prepareFirstLine` calls it) is bounded by `SYNTH_WATCHDOG_MS` (16s), so the loading quiz can hold up to ~16s on a cold call. Fix these comments to state the two-bound design.
  - Remove the temp `DEBUG-tts` logs in `synthesize`, `obtainAudio`, `playFromServer`.
- **Modify:** `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt`
  - Remove the temp `DEBUG-tts` logs in `prefetchOpponentLine` and `speakOpponent` (restore both methods to their pre-instrumentation form).
- **Modify (doc):** `docs/design/tts.md` — §4's "Gemini 워치독 8초" line rests on the now-disproven "프록시 warm + min-instances 전제"; update it to the two-bound design (16s synthesis budget covering the server retry; 8s live-playback wait → device fallback) and note the cold-first-call reality.
- **Modify (tests):** `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt`
  - Add tests for the two new bounds; the existing `server watchdog timeout falls back to device tts` stays green (behavior preserved by the 8s wrapper).

---

## Task 1: Split the synthesis budget from the live-playback wait

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt` (instrumentation removal only)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt`

**Interfaces:**
- Consumes: existing `obtainAudio`, `synthesize`, `playFromServer`, `FakeLlmApi(delayMs=…)`, `FakeDeviceTts`, `FakePcmPlayer`, `FakeSettings`, `coordScope()`, `collectCompletions()`.
- Produces: `SYNTH_WATCHDOG_MS` companion constant. No public API change — `playTurn`/`prefetch`/`awaitWarm` signatures are unchanged; only the internal timeouts differ.

- [ ] **Step 1: Remove the temporary diagnosis instrumentation**

In `TtsPlaybackCoordinator.kt`, delete the three temp log lines:
- in `synthesize`: `android.util.Log.d("DEBUG-tts", "synthStart '${text.take(24)}'")` and `android.util.Log.d("DEBUG-tts", "synthEnd '${text.take(24)}'")`
- in `obtainAudio`: the `android.util.Log.d("DEBUG-tts", "obtain '${text.take(24)}' cacheHit=… inflightJoin=…")` block
- in `playFromServer`: `android.util.Log.d("DEBUG-tts", "LIVE playFromServer '${text.take(24)}'")`

In `GeneratedDialogueSession.kt`, restore `prefetchOpponentLine` to its pre-instrumentation form (remove both `DEBUG-tts` logs and the branch that only logged):

```kotlin
        private fun prefetchOpponentLine(ordinal: Int) {
            if (ordinal == lastWarmedOrdinal) return
            val text = nextOpponentEnglish(latestTurns, ordinal) ?: return // 아직 미도착 — 다음 상태에서 재시도
            lastWarmedOrdinal = ordinal
            tts.prefetch(text, opponentSpeaker?.gender)
        }
```

and restore `speakOpponent`:

```kotlin
        fun speakOpponent(text: String) {
            tts.playTurn(text, gender = opponentSpeaker?.gender, advanceOnDone = true)
        }
```

Verify no leftovers: `grep -rn "DEBUG-tts" android/app/src/main` must return nothing.

- [ ] **Step 2: Write the failing tests**

Add to `TtsPlaybackCoordinatorTest.kt` (reusing the existing fakes/harness):

```kotlin
    @Test
    fun `prefetch tolerates a cold synthesis longer than the live watchdog and caches it`() =
        runTest {
            // 12s synth: exceeds the 8s live-playback bound but is within the 16s synthesis budget.
            val api = FakeLlmApi(delayMs = 12_000)
            val player = FakePcmPlayer()
            val coordinator =
                TtsPlaybackCoordinator(api, player, FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.prefetch("Cold", "female")
            advanceUntilIdle()
            assertEquals(1, api.callCount) // the cold synthesis completed (not killed at 8s)

            coordinator.playTurn("Cold", "female")
            advanceUntilIdle()
            assertEquals(1, api.callCount) // served from the warmed cache — no re-synthesis
            assertEquals(1, player.played.size)
        }

    @Test
    fun `live playTurn falls back to device at 8s while the cold synthesis keeps caching`() =
        runTest {
            val api = FakeLlmApi(delayMs = 12_000) // > 8s live bound, < 16s synth budget
            val device = FakeDeviceTts(result = DeviceTtsResult.COMPLETED)
            val player = FakePcmPlayer()
            val coordinator =
                TtsPlaybackCoordinator(api, player, device, FakeSettings(), coordScope())

            coordinator.playTurn("Cold", "female") // no prefetch: live path starts the synthesis
            advanceUntilIdle()
            assertEquals(1, device.callCount) // fell back to device at the 8s bound
            assertEquals(1, api.callCount) // one synthesis was started

            // The synthesis kept running past the 8s live bound and cached; a later play is a hit.
            coordinator.playTurn("Cold", "female")
            advanceUntilIdle()
            assertEquals(1, api.callCount) // no second synthesis — cache hit
            assertEquals(1, player.played.size) // and it played from the server cache this time
        }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsPlaybackCoordinatorTest*'`
Expected: FAIL — with the current single 8s watchdog, `synthesize` kills the 12s synth at 8s, so `prefetch tolerates a cold synthesis…` sees `api.callCount == 1` but the later `playTurn` re-synthesizes → `api.callCount == 2` (assertion fails); and `live playTurn falls back…`'s later cache-hit assertion fails (nothing was cached).

- [ ] **Step 4: Add `SYNTH_WATCHDOG_MS` and apply the split**

In the companion object (next to `SERVER_WATCHDOG_MS` / `DEVICE_WATCHDOG_MS`):

```kotlin
            // The synthesis job's own budget. Must exceed the SERVER's worst-case retry
            // (gemini.ts REQUEST_TIMEOUT_MS 7s × MAX_ATTEMPTS 2 = 14s) so a cold Gemini-TTS
            // call that only succeeds on the server's second attempt still completes and caches.
            // Prefetch/warm paths are covered (loading quiz / learner turn), so they can afford it.
            const val SYNTH_WATCHDOG_MS = 16_000L
```

In `synthesize`, change the timeout from `SERVER_WATCHDOG_MS` to `SYNTH_WATCHDOG_MS`:

```kotlin
            val response =
                withTimeoutOrNull(SYNTH_WATCHDOG_MS) {
                    try {
                        api.tts(TtsRequest(payload = TtsPayload(text = text, gender = gender, speechRate = rate)))
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                } ?: return null
```

In `playFromServer`, wrap `obtainAudio` in the live-wait bound so the live path still falls back to device at 8s (the synthesis, on the coordinator scope, keeps running and caches):

```kotlin
        @Suppress("ReturnCount")
        private suspend fun playFromServer(
            token: Long,
            text: String,
            gender: String?,
            rate: Float,
        ): Boolean {
            // Live playback waits at most SERVER_WATCHDOG_MS for the audio, then falls back to
            // device. The synthesis is a sibling job on `scope`, so this timeout only abandons the
            // *wait* — the cold synthesis finishes in the background and caches for the next need.
            val audio = withTimeoutOrNull(SERVER_WATCHDOG_MS) { obtainAudio(text, gender, rate) } ?: return false
            if (token != sessionToken) return true // stale: swallow, don't advance
            lastPcm = audio.pcm
            lastSampleRate = audio.sampleRate
            playPcm(token, audio.pcm, audio.sampleRate)
            return true
        }
```

Leave the *bodies* of `obtainAudio`, `prefetch`, `awaitWarm`, `playTurn`, `playPcm`, `playFromDevice`, `clearCache`, `stop` unchanged.

- [ ] **Step 4b: Update the now-stale watchdog KDoc/comments**

The split invalidates three comments — fix each so the docs match the two bounds:
- **`awaitWarm`'s KDoc** — currently ends "Bounded by [obtainAudio]'s own 8s watchdog." Replace the bound clause with: `Bounded by [SYNTH_WATCHDOG_MS] (16s) — so on a cold first call the loading gate that awaits this can hold the quiz up to ~16s.` (This is the loading gate's dependency, so it matters most.)
- **The class KDoc** — the phrase "server (Gemini) synthesis with an 8s watchdog" → "server (Gemini) synthesis with a 16s job budget ([SYNTH_WATCHDOG_MS]) and an 8s live-playback wait ([SERVER_WATCHDOG_MS]) before device fallback".
- **The companion comment** above the constants ("Client watchdogs (tts.md §4). …") → note that `SYNTH_WATCHDOG_MS` bounds the synthesis job (covers the server 7s×2 retry) and `SERVER_WATCHDOG_MS` bounds only the live-playback wait.

Also update `docs/design/tts.md` §4: the "Gemini 워치독 8초" bullet's premise ("프록시 warm + min-instances 전제로 8초로 단축") was disproven by the cold-first-call diagnosis. Replace it with a short description of the two-bound design (16s synthesis budget so a cold call — incl. the server's 7s×2 retry — completes and caches on covered paths; 8s live-playback wait → device fallback) and note the first-call cold reality and that backend keep-warm was declined.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsPlaybackCoordinatorTest*'`
Expected: PASS — the two new tests plus all existing ones. In particular `server watchdog timeout falls back to device tts` (api `delayMs = 1_000_000`) still passes: `playFromServer`'s 8s wrapper times out → device fallback (`device.callCount == 1`).

- [ ] **Step 6: Full verification**

Run: `scripts/verify-android.sh`
Expected: compiles; detekt clean on the changed files; both variant unit-test source sets green (aside from the pre-existing unrelated `SettingsScreen.kt:213` detekt and `RecordsSkeleton*` flakes). If the wrapped `playFromServer` trips a new detekt rule, add a `@Suppress(...)` matching repo convention and note it. Confirm `git diff --stat` shows only the coordinator, the session file, and the coordinator test.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt docs/design/tts.md android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt
git commit -m "fix(tts): tolerate a cold first synthesis on covered paths

The first Gemini-TTS call of a session is cold (>8s, exceeding the client
watchdog and even the server's 7s×2 retry window), so the first line failed
while warm subsequent lines (~5-6s) were instant. Split the single 8s watchdog:
SYNTH_WATCHDOG_MS (16s) bounds the synthesis job itself so prefetch/warm — which
are covered by the loading quiz and the learner's turn — let a cold call finish
and cache; SERVER_WATCHDOG_MS (8s) now bounds only the live playFromServer wait,
preserving timely device fallback while the cold synthesis keeps caching in the
background. Removes diagnosis instrumentation. No backend change.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: On-device verification

**Files:** none (verification). Requires a connected device (`adb`). If none, defer to a human/device gate.

- [ ] **Step 1: Build and install**

Run: `scripts/verify-android.sh :app:installDebug`
Expected: `BUILD SUCCESSFUL`, `Installed on 1 device`. (If it fails with "No connected devices", the device dropped — reconnect and retry.)

- [ ] **Step 2: Verify the cold first line now completes**

With 음질 = "자연스러운 발음", start a **fresh session after the app has been idle** (to hit a cold Gemini-TTS model). Expected:
1. The loading quiz holds ~10–14s on the cold call (the quiz covers it; ring keeps spinning until warm), then
2. the first opponent line plays with **natural (server) audio, instantly on entry** — not a device-voice fallback, not a long chat skeleton.
3. Subsequent lines remain instant (already warm).

- [ ] **Step 3: Verify live fallback is intact**

Confirm normal cases still behave: on a **warm** session the first line is fast (~5-6s loading); tapping "다시 듣기" is instant; 빠른 발음 (DEVICE) and 전체 음소거 unchanged; and if the server is unreachable the opponent line still falls back to device TTS within ~8s (no indefinite chat skeleton).

- [ ] **Step 4: Record results**

Note the cold-session first-line behavior (loading duration → instant server audio) and confirm no regression on warm sessions or DEVICE/mute. No commit.

---

## Non-Goals / Follow-ups

- **Backend keep-warm (scheduled cron / instance warm-up)** — the "always fast first call" root fix — was declined for its ongoing 24/7 cost. If the ~10–14s cold-session loading proves too long in practice, revisit: a scheduled TTS ping keeps the Gemini model warm so the first call is ~5-6s. (Also would let `SYNTH_WATCHDOG_MS` shrink.)
- **Reconciling the server retry with the client** — `gemini.ts` `REQUEST_TIMEOUT_MS`/`MAX_ATTEMPTS` are left as-is (server change avoided). `SYNTH_WATCHDOG_MS` is sized to cover them from the client side instead.
- **Capping the loading-gate hold** — `prepareFirstLine` naturally completes when `awaitWarm` returns (≤ `SYNTH_WATCHDOG_MS`), so no separate cap is added; if a hard ceiling on loading time is wanted later, cap the gate independently.

---

## Self-Review

**Spec coverage:** The cold-first-line failure (diagnosed: first Gemini-TTS call >8s, killed by the single 8s watchdog) is fixed by Task 1 splitting the budget so covered paths cache the cold result; the loading gate (already committed) presents it instantly. Live-path responsiveness is preserved by the 8s wrapper (Task 1 Step 4) and asserted by the retained fallback test. Instrumentation removal is Task 1 Step 1. Device confirmation is Task 2.

**Placeholder scan:** No TBD/vague steps. Every code step has full code; the two new tests have full bodies and exact expected pass/fail. The instrumentation removal names each log site and gives the restored method bodies.

**Type/name consistency:** `SYNTH_WATCHDOG_MS` (new, 16s) used only in `synthesize`; `SERVER_WATCHDOG_MS` (existing, 8s) used only as the `playFromServer` wrapper. `withTimeoutOrNull` is already imported. `obtainAudio(text, gender, rate)` call unchanged. Tests use the existing `FakeLlmApi(delayMs=…)` / `FakeDeviceTts` / `FakePcmPlayer` / `coordScope()` harness.
