# Dialogue Server-TTS Prefetch & Caching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make server (Gemini) TTS feel instant in the live dialogue by pre-synthesizing upcoming opponent lines during the learner's turn, caching the PCM, and replaying cached lines with no server round-trip — plus verifying the backend stays warm.

**Architecture:** Add a session-scoped, LRU-bounded synthesis cache to `TtsPlaybackCoordinator`, keyed by `(text, gender, speechRate)`. `playTurn`'s existing SERVER branch becomes cache-aware (cache hit → play instantly; miss → synthesize + store). A new `prefetch(text, gender)` warms the cache in the background without touching the playback state machine. The dialogue ViewModel triggers prefetch at turn boundaries (warm line N+1 when line N finishes), and the loading-quiz screen warms the very first line the moment generation is ready — while the `@Singleton` coordinator (and its cache) persist across the generating→chat navigation. Because `playTurn` is now cache-aware, the existing "다시 듣기" path (`replayOpponent → playTurn(advanceOnDone=false)`) replays cached lines instantly — no separate replay mechanism. A separate verification task confirms the Cloud Function's `minInstances` is warm.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt DI, Retrofit (`/llm` proxy), Kotlin coroutines (`Dispatchers.Main.immediate` app scope), JUnit4 + kotlinx-coroutines-test. Backend: Firebase Cloud Functions v2 (TypeScript), region `asia-northeast3`, Gemini `gemini-2.5-flash-preview-tts`.

## Global Constraints

- Single Gradle module `:app`; `core`/`feature` are **package** boundaries. All Gradle verification runs through `scripts/verify-android.sh` (never bare `./gradlew` — worktree cache/daemon/`google-services.json` gotchas). See [docs/agents/android-verification.md](docs/agents/android-verification.md).
- The coordinator runs all playback/prefetch coroutines on its injected `scope` = `SupervisorJob() + Dispatchers.Main.immediate` (single-threaded Main). Cache reads/writes happen on Main around suspension points, so the cache needs **no locking** — do not add synchronization or move cache access off Main.
- Prefetch must be **SERVER-quality + unmuted only**, and must **never** call `startNewSession()`, `playTurn`, `playPcm`, `player`, `deviceTts`, or mutate `sessionToken`/`currentJob`/`_state`/`lastPcm`/`lastSampleRate`. It only synthesizes + writes the cache. This is what keeps a background warm from cancelling or corrupting live playback.
- **Exactly-once synthesis is enforced by a shared in-flight `Deferred` map**, not by prefetch-only dedup. Both the live play path and prefetch go through one `obtainAudio(...)` that returns a cache hit, else joins an in-flight `Deferred` for the same key, else starts one. A live `playTurn` arriving while its line's prefetch is still synthesizing **joins** that synthesis — it never fires a second `/llm` call.
- `TtsQuality.SERVER` = "자연스러운 발음" (default); `TtsQuality.DEVICE` = "빠른 발음". Server voice/locale are code-fixed (en-US; male→`Puck`, else→`Kore`) — do not add voice/locale controls.
- Wire contract (do not change): `DialogueTurn(ko, en, role)` with `role ∈ {"model","user"}`; even indices (0,2,4,…) are opponent (model) lines, odd are learner (user). `LlmApi.tts(TtsRequest(payload = TtsPayload(text, gender, speechRate))): TtsResponse(pcmBase64, sampleRate, mimeType)`.
- Backend `minInstances` default is **1** per SoT (NFR-3); production **must not** run at 0. Setting it is a non-prod-only knob (`LLM_MIN_INSTANCES`).
- Prefetch adds **no extra** total server calls: each opponent line is synthesized exactly once (just earlier). Do not synthesize the whole script eagerly (tts.md §3: line-by-line).

---

## File Structure

- **Modify:** `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt`
  - Add `TtsCacheKey` (internal top-level data class), private `CachedAudio`, the LRU cache, in-flight `Deferred` map, prefetch-job list, `CACHE_CAP`.
  - Extract `synthesize(text, gender, rate): CachedAudio?` (DRY: watchdog + `api.tts` + base64 decode) and `obtainAudio(text, gender, rate): CachedAudio?` (cache → join in-flight → synthesize, the single synthesis authority).
  - Rewrite `playFromServer` to call `obtainAudio` (hit/join → play; miss → synthesize + store).
  - Add public `prefetch(text, gender)` (calls `obtainAudio` off the playback path) and `clearCache()`.
  - `playTurn` body is **unchanged** — the cache is transparent behind `playFromServer`.
- **Modify:** `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt`
  - Add internal top-level `nextOpponentEnglish(turns, opponentOrdinal): String?`.
  - Add `prefetchOpponentLine(ordinal)` + a `lastWarmedOrdinal` guard; call it from `acceptGenerationState` with the **pre-`accept()`** ordinal (first-line warm at ordinal 0 + streamed-line retries) and from `onOpponentTtsDone` with the current serial (next-line warm). Reset the guard on restore.
  - Call `tts.clearCache()` in the existing cleanup where `tts.stop()` lives (`onCleared`).
- **Modify (Task 4):** `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModel.kt` (inject `TtsPlaybackCoordinator`, add `warmFirstLine()`) and `DialogueGeneratingScreen.kt` (Route calls `warmFirstLine()` on `Ready`) — warm the first opponent line while the loading quiz is on screen; the `@Singleton` coordinator keeps the cache warm across the generating→chat nav pop.
- **Modify (tests):** `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt` — add cache/prefetch tests (mirror existing fakes/harness); `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModelTest.kt` — add the `warmFirstLine` test + minimal TTS fakes.
- **Create (test):** `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/NextOpponentLineTest.kt` — pure test for `nextOpponentEnglish`.
- **Verify/modify (backend, Task 5):** `functions/` deploy config for `/llm` `minInstances` — verification-first; config change only if found at 0.

---

## Task 1: Coordinator — cache-aware server synthesis

**Goal:** A second `playTurn` for the same `(text, gender, rate)` — including the "다시 듣기" replay path — reuses cached PCM instead of calling the server again.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt`

**Interfaces:**
- Consumes: `LlmApi.tts`, `TtsRequest`, `TtsPayload`, `TtsResponse`, `TtsSettingsRepository.current()` (all existing).
- Produces: `internal data class TtsCacheKey(val text: String, val gender: String?, val speechRate: Float)`; private `obtainAudio(text, gender, rate): CachedAudio?` (the single synthesis authority — cache/join/synthesize) and `synthesize(...)`. `playFromServer` now goes through `obtainAudio`. `lastPcm`/`lastSampleRate` still set on play (unchanged replay-legacy retention). Task 2 will add `prefetch`/`clearCache` reusing `obtainAudio` + the in-flight map.

- [ ] **Step 1: Write the failing tests**

Add these tests to `TtsPlaybackCoordinatorTest.kt` (they reuse the existing `FakeLlmApi`, `FakePcmPlayer`, `FakeDeviceTts`, `FakeSettings`, `coordScope()`, `collectCompletions()`, `PCM_BYTES`, `okResponse()`):

```kotlin
    @Test
    fun `second playTurn of same line reuses cache without a second synthesis`() =
        runTest {
            val api = FakeLlmApi(response = okResponse(rate = 24000))
            val player = FakePcmPlayer()
            val coordinator = TtsPlaybackCoordinator(api, player, FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.playTurn("Hello", "female")
            advanceUntilIdle()
            coordinator.playTurn("Hello", "female")
            advanceUntilIdle()

            assertEquals(1, api.callCount) // synthesized once, second play served from cache
            assertEquals(2, player.played.size) // but played both times
            assertTrue(PCM_BYTES.contentEquals(player.played[1].first))
        }

    @Test
    fun `replay path advanceOnDone false of a cached line makes no server call`() =
        runTest {
            val api = FakeLlmApi()
            val player = FakePcmPlayer()
            val coordinator = TtsPlaybackCoordinator(api, player, FakeDeviceTts(), FakeSettings(), coordScope())
            val completions = collectCompletions(coordinator)

            coordinator.playTurn("Hi", "male") // auto-speak: synthesizes + caches
            advanceUntilIdle()
            coordinator.playTurn("Hi", "male", advanceOnDone = false) // "다시 듣기"
            advanceUntilIdle()

            assertEquals(1, api.callCount) // replay served from cache, no re-synthesis
            assertEquals(2, player.played.size)
            assertEquals(1, completions.size) // only the auto-speak advanced; replay did not
        }

    @Test
    fun `cache key includes speech rate so a rate change re-synthesizes`() =
        runTest {
            val api = FakeLlmApi()
            val settings = FakeSettings(TtsSettings(speechRate = 1.0f))
            val coordinator = TtsPlaybackCoordinator(api, FakePcmPlayer(), FakeDeviceTts(), settings, coordScope())

            coordinator.playTurn("Hello", null)
            advanceUntilIdle()
            settings.setSpeechRate(1.5f)
            coordinator.playTurn("Hello", null)
            advanceUntilIdle()

            assertEquals(2, api.callCount) // different rate → different key → fresh synthesis
        }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsPlaybackCoordinatorTest*'`
Expected: FAIL — `second playTurn of same line...` and `replay path...` fail with `api.callCount == 2` (no cache yet); `cache key includes speech rate` passes only by coincidence. Compilation succeeds (no new production symbols referenced yet).

- [ ] **Step 3: Add the cache scaffolding to the coordinator**

In `TtsPlaybackCoordinator.kt`, add the key type at the top of the file (after the imports, before the class):

```kotlin
/** Cache key for a synthesized opponent line — server output depends only on
 *  (text, gender, rate). Rate is part of the key so a mid-session speed change
 *  re-synthesizes instead of playing stale-speed audio. */
internal data class TtsCacheKey(
    val text: String,
    val gender: String?,
    val speechRate: Float,
)
```

Inside the class, add fields near `lastPcm`/`lastSampleRate` (after line ~70):

```kotlin
        private class CachedAudio(val pcm: ByteArray, val sampleRate: Int)

        // Session-scoped synthesis cache: opponent-line PCM keyed by (text, gender, rate).
        // accessOrder=true + removeEldestEntry makes it LRU-bounded. All access is on the
        // coordinator's single-threaded Main scope, so no locking is needed (see Global
        // Constraints). Populated by obtainAudio; cleared by clearCache (Task 2) on screen exit.
        private val cache =
            object : LinkedHashMap<TtsCacheKey, CachedAudio>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<TtsCacheKey, CachedAudio>): Boolean = size > CACHE_CAP
            }

        // Shared in-flight synthesis: one Deferred per key so a live playFromServer and a
        // concurrent prefetch for the same line join a single /llm call (exactly-once).
        private val inFlight = mutableMapOf<TtsCacheKey, Deferred<CachedAudio?>>()
```

Add the imports `import kotlinx.coroutines.Deferred` and `import kotlinx.coroutines.async` to the file's import block (alphabetical order, near the existing `import kotlinx.coroutines.Job`).

Add to the companion object (alongside the watchdog constants ~267):

```kotlin
            // A dialogue has ~2–3 opponent lines; 4 covers current + next with margin, and
            // LRU eviction bounds memory if a longer session accumulates more.
            const val CACHE_CAP = 4
```

- [ ] **Step 4: Add `synthesize` + `obtainAudio` and route `playFromServer` through them**

Replace the existing `playFromServer` (lines ~171-200) with the version below, and add the two helpers above it:

```kotlin
        /** Server synthesis + base64 decode under the watchdog. Returns null on
         *  timeout / network / HTTP / malformed / undecodable payload (caller falls back
         *  to device, or — for prefetch — simply skips). Never touches player/state/token,
         *  so it is safe to call from a background prefetch. */
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        private suspend fun synthesize(
            text: String,
            gender: String?,
            rate: Float,
        ): CachedAudio? {
            val response =
                withTimeoutOrNull(SERVER_WATCHDOG_MS) {
                    try {
                        api.tts(TtsRequest(payload = TtsPayload(text = text, gender = gender, speechRate = rate)))
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                } ?: return null
            return try {
                CachedAudio(Base64.getDecoder().decode(response.pcmBase64), response.sampleRate)
            } catch (e: IllegalArgumentException) {
                null // undecodable payload
            }
        }

        /** The single synthesis authority: cache hit → return it; else join an in-flight
         *  synthesis for the same key (so a live play and a prefetch never double-call);
         *  else start one. The synthesis job caches its own result and evicts its own
         *  in-flight entry via [invokeOnCompletion] — tied to the SYNTHESIS finishing, not to
         *  any awaiter. This matters: `scope.async` is a sibling of the awaiters (not their
         *  child), so a `startNewSession()` that cancels a live awaiter mid-await must NOT
         *  evict the still-running entry — otherwise a same-key caller would start a second
         *  synthesis. A cancelled awaiter therefore still leaves the job running and cached.
         *  Main-thread confined, so the map ops need no lock. */
        private suspend fun obtainAudio(
            text: String,
            gender: String?,
            rate: Float,
        ): CachedAudio? {
            val key = TtsCacheKey(text, gender, rate)
            cache[key]?.let { return it }
            val deferred =
                inFlight[key] ?: scope.async {
                    synthesize(text, gender, rate)?.also { cache[key] = it }
                }.also { d ->
                    inFlight[key] = d
                    d.invokeOnCompletion { inFlight.remove(key, d) } // identity remove; tied to the job, not awaiters
                }
            return deferred.await()
        }

        /** @return true if the server path terminally handled the turn (played or swallowed
         *  as stale); false if synthesis failed and the caller should try device TTS.
         *  A cache hit / in-flight join plays without a fresh network call. */
        private suspend fun playFromServer(
            token: Long,
            text: String,
            gender: String?,
            rate: Float,
        ): Boolean {
            val audio = obtainAudio(text, gender, rate) ?: return false
            if (token != sessionToken) return true // stale: swallow, don't advance
            lastPcm = audio.pcm
            lastSampleRate = audio.sampleRate
            playPcm(token, audio.pcm, audio.sampleRate)
            return true
        }
```

Leave `playTurn`, `playPcm`, `playFromDevice`, `replay`, `playClip`, `stop`, `finish` unchanged. `playTurn` still sets `lastPcm = null` then `_state = LOADING` before calling `playFromServer` (a cache hit promotes LOADING→PLAYING immediately in `playPcm`). Note `MutableMap.remove(key, value)` (Kotlin stdlib) removes only if the mapped `Deferred` is still this one — a late awaiter never evicts a newer entry.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsPlaybackCoordinatorTest*'`
Expected: PASS — all existing tests plus the three new ones. `second playTurn...` now shows `api.callCount == 1`; `replay path...` shows `api.callCount == 1` and one completion; `cache key includes speech rate` shows `api.callCount == 2`.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt
git commit -m "feat(tts): cache-aware server synthesis so replays reuse PCM

playFromServer now keys synthesized PCM by (text,gender,rate) in an LRU cache
(cap 4). A repeat playTurn of the same line — including 다시 듣기
(advanceOnDone=false) — plays from cache with no second /llm call. Extracts
synthesize() as the shared watchdog+decode step for Task 2's prefetch.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: Coordinator — background prefetch + cache clearing

**Goal:** `prefetch(text, gender)` warms the cache off the playback path so an upcoming line's `playTurn` is a cache hit; `clearCache()` drops everything on screen exit.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt`

**Interfaces:**
- Consumes: Task 1's `obtainAudio`, `cache`, `inFlight`, `TtsCacheKey`.
- Produces: `fun prefetch(text: String, gender: String?)` and `fun clearCache()`. The dialogue ViewModel (Task 3) calls these.

- [ ] **Step 1: Write the failing tests**

Add to `TtsPlaybackCoordinatorTest.kt`:

```kotlin
    @Test
    fun `prefetch warms cache so the later playTurn makes no server call`() =
        runTest {
            val api = FakeLlmApi()
            val player = FakePcmPlayer()
            val coordinator = TtsPlaybackCoordinator(api, player, FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.prefetch("Ahead", "female")
            advanceUntilIdle()
            assertEquals(1, api.callCount) // synthesized in the background
            assertEquals(0, player.played.size) // but not played

            coordinator.playTurn("Ahead", "female")
            advanceUntilIdle()
            assertEquals(1, api.callCount) // served from the warmed cache — no second call
            assertEquals(1, player.played.size)
        }

    @Test
    fun `prefetch is a no-op in DEVICE quality and when muted`() =
        runTest {
            val deviceApi = FakeLlmApi()
            val deviceCoord =
                TtsPlaybackCoordinator(deviceApi, FakePcmPlayer(), FakeDeviceTts(), FakeSettings(TtsSettings(quality = TtsQuality.DEVICE)), coordScope())
            deviceCoord.prefetch("x", null)
            advanceUntilIdle()
            assertEquals(0, deviceApi.callCount)

            val mutedApi = FakeLlmApi()
            val mutedCoord =
                TtsPlaybackCoordinator(mutedApi, FakePcmPlayer(), FakeDeviceTts(), FakeSettings(TtsSettings(muted = true)), coordScope())
            mutedCoord.prefetch("x", null)
            advanceUntilIdle()
            assertEquals(0, mutedApi.callCount)
        }

    @Test
    fun `duplicate prefetch of the same line dedups to one synthesis`() =
        runTest {
            val api = FakeLlmApi(delayMs = 50) // keep the first in flight while the second arrives
            val coordinator = TtsPlaybackCoordinator(api, FakePcmPlayer(), FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.prefetch("Dup", null)
            coordinator.prefetch("Dup", null)
            advanceUntilIdle()

            assertEquals(1, api.callCount)
        }

    @Test
    fun `prefetch does not disturb playback state`() =
        runTest {
            val coordinator = TtsPlaybackCoordinator(FakeLlmApi(), FakePcmPlayer(), FakeDeviceTts(), FakeSettings(), coordScope())
            val completions = collectCompletions(coordinator)
            val audioReady = collectAudioReady(coordinator)

            coordinator.prefetch("Bg", null)
            advanceUntilIdle()

            assertEquals(PlaybackState.IDLE, coordinator.state.value) // never left IDLE
            assertEquals(0, completions.size)
            assertEquals(0, audioReady.size)
        }

    @Test
    fun `clearCache forces the next playTurn to re-synthesize`() =
        runTest {
            val api = FakeLlmApi()
            val coordinator = TtsPlaybackCoordinator(api, FakePcmPlayer(), FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.prefetch("Gone", null)
            advanceUntilIdle()
            coordinator.clearCache()
            coordinator.playTurn("Gone", null)
            advanceUntilIdle()

            assertEquals(2, api.callCount) // cache cleared → fresh synthesis
        }

    @Test
    fun `playTurn joins an in-flight prefetch instead of re-synthesizing`() =
        runTest {
            val api = FakeLlmApi(delayMs = 100) // keep the synthesis in flight
            val player = FakePcmPlayer()
            val coordinator = TtsPlaybackCoordinator(api, player, FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.prefetch("Join", null) // starts synthesis (Unconfined runs it to the delay)
            coordinator.playTurn("Join", null) // same line arrives before synthesis resolves → joins it
            advanceUntilIdle()

            assertEquals(1, api.callCount) // live play joined the in-flight synthesis — no 2nd call
            assertEquals(1, player.played.size)
        }

    @Test
    fun `a cancelled awaiter does not evict an in-flight synthesis (no duplicate call)`() =
        runTest {
            val api = FakeLlmApi(delayMs = 100) // keep the synthesis in flight across the cancel
            val coordinator = TtsPlaybackCoordinator(api, FakePcmPlayer(), FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.playTurn("Same", null) // live synthesis for "Same" begins (in flight)
            coordinator.playTurn("Other", null) // startNewSession() cancels the first awaiter mid-await
            coordinator.playTurn("Same", null) // same key again, BEFORE the original synth resolves
            advanceUntilIdle()

            // The original "Same" synthesis was never evicted by the cancelled awaiter, so the
            // third call joins it rather than starting a second: one "Same" call + one "Other" call.
            assertEquals(2, api.callCount)
        }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsPlaybackCoordinatorTest*'`
Expected: FAIL to compile — `prefetch` and `clearCache` are unresolved. (That is the expected red for this step.)

- [ ] **Step 3: Add `prefetch`, `clearCache`, and the job-tracking field**

In `TtsPlaybackCoordinator.kt`, add one field next to the `cache`/`inFlight` fields:

```kotlin
        // Outstanding prefetch launches, cancelled by clearCache on screen exit.
        private val prefetchJobs = mutableListOf<Job>()
```

Add the public methods (place them after `playClip`, before `stop`):

```kotlin
        /** Warm the cache for an upcoming opponent line so its later [playTurn] is a cache
         *  hit (instant, no watchdog). SERVER + unmuted only. Delegates to [obtainAudio], so
         *  a live [playTurn] for the same line that arrives mid-synthesis joins this one call
         *  (exactly-once). Never touches player/state/token, so it cannot disturb playback. */
        fun prefetch(
            text: String,
            gender: String?,
        ) {
            val job =
                scope.launch {
                    val settings = settingsRepo.current()
                    if (settings.muted || settings.quality != TtsQuality.SERVER) return@launch
                    obtainAudio(text, gender, settings.speechRate) // result cached as a side effect
                }
            prefetchJobs.add(job)
            job.invokeOnCompletion { prefetchJobs.remove(job) }
        }

        /** Drop all cached and in-flight synthesis (call on leaving the dialogue screen).
         *  Cancels outstanding prefetch launches and their in-flight synthesis jobs. Does not
         *  affect live playback — [stop] handles that separately. */
        fun clearCache() {
            prefetchJobs.toList().forEach { it.cancel() }
            prefetchJobs.clear()
            inFlight.values.toList().forEach { it.cancel() }
            inFlight.clear()
            cache.clear()
        }
```

`Job` is already imported (`import kotlinx.coroutines.Job`).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsPlaybackCoordinatorTest*'`
Expected: PASS — all five new tests plus Task 1's and the pre-existing suite.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt
git commit -m "feat(tts): background prefetch + cache clearing

prefetch(text,gender) warms the synthesis cache off the playback path (SERVER+
unmuted only, deduped, never touching player/state/token), so an upcoming line's
playTurn is an instant cache hit. clearCache() cancels in-flight prefetch and
empties the cache on screen exit.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: Dialogue ViewModel — trigger prefetch at turn boundaries

**Goal:** Warm the first opponent line when the script is ready, warm line N+1 when line N finishes, and clear the cache on exit — so by the time each opponent turn arrives, its audio is already cached.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt`
- Create (test): `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/NextOpponentLineTest.kt`

**Interfaces:**
- Consumes: `TtsPlaybackCoordinator.prefetch(text, gender)` and `clearCache()` (Task 2); `latestTurns: List<NetworkDialogueTurn>` (VM field); `turnState.opponentTurnSerial` (observable, incremented inside `turnState.accept`); `opponentSpeaker?.gender`.
- Produces: `internal fun nextOpponentEnglish(turns: List<NetworkDialogueTurn>, opponentOrdinal: Int): String?` (pure, testable) and the private `prefetchOpponentLine(ordinal)` + `lastWarmedOrdinal` guard. `NetworkDialogueTurn` is the file's alias for `com.jjundev.oneclickeng.core.network.DialogueTurn` (import at line 23); it is file-scoped, so the test aliases the import itself.

- [ ] **Step 1: Write the failing pure-function test**

Create `NextOpponentLineTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.turn

// The feature.session.turn package declares its OWN unrelated `DialogueTurn`
// (SampleDialogue.kt), so the network turn MUST be import-aliased — matching the
// sibling tests (GeneratedDialogueStateTest, OpponentSkeletonFloorTest, SessionTurnSnapshotTest).
import com.jjundev.oneclickeng.core.network.DialogueTurn as NetworkDialogueTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextOpponentLineTest {
    private val turns =
        listOf(
            NetworkDialogueTurn(ko = "안녕", en = "Hello", role = "model"), // 0 = opponent #0
            NetworkDialogueTurn(ko = "나 좋아", en = "I am good", role = "user"), // 1 = learner
            NetworkDialogueTurn(ko = "잘가", en = "Goodbye", role = "model"), // 2 = opponent #1
            NetworkDialogueTurn(ko = "응", en = "Bye", role = "user"), // 3 = learner
        )

    @Test
    fun `ordinal 0 returns the first opponent line`() {
        assertEquals("Hello", nextOpponentEnglish(turns, 0))
    }

    @Test
    fun `ordinal 1 returns the second opponent line at index 2`() {
        assertEquals("Goodbye", nextOpponentEnglish(turns, 1))
    }

    @Test
    fun `out-of-range ordinal returns null`() {
        assertNull(nextOpponentEnglish(turns, 2)) // index 4 does not exist
    }

    @Test
    fun `null when the even-index turn is not a model line`() {
        val malformed = listOf(NetworkDialogueTurn(ko = "x", en = "y", role = "user"))
        assertNull(nextOpponentEnglish(malformed, 0))
    }

    @Test
    fun `null when the opponent line text is blank`() {
        val blank = listOf(NetworkDialogueTurn(ko = "x", en = "  ", role = "model"))
        assertNull(nextOpponentEnglish(blank, 0))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*NextOpponentLineTest*'`
Expected: FAIL to compile — `nextOpponentEnglish` is unresolved.

- [ ] **Step 3: Add the pure helper**

In `GeneratedDialogueSession.kt`, add a top-level function (near the other file-level helpers, e.g. just above `class GeneratedDialogueSessionViewModel` at line ~174):

```kotlin
/** The English of the opponent (model) line at the given 0-based opponent ordinal, or
 *  null if that line is not yet available. Opponent lines occupy even indices (0,2,4,…)
 *  of the raw turn buffer per the wire contract (DialogueTurn.role ∈ {"model","user"});
 *  ordinal 0 = the first opponent line. Used to prefetch/​warm a line's TTS ahead of its
 *  turn. Defensive against malformed data (non-model / blank → null). */
internal fun nextOpponentEnglish(
    turns: List<NetworkDialogueTurn>,
    opponentOrdinal: Int,
): String? {
    val turn = turns.getOrNull(2 * opponentOrdinal) ?: return null
    return turn.en.takeIf { turn.role == "model" && it.isNotBlank() }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*NextOpponentLineTest*'`
Expected: PASS (all five cases).

- [ ] **Step 5: Wire prefetch into the turn lifecycle**

> **Ordinal timing — read this first.** `opponentTurnSerial` counts opponent lines that have been *displayed*, and it is incremented **inside** `turnState.accept(state)` (via `displayOpponent`). So it means different things at the two call sites:
> - In `acceptGenerationState`, `accept()` may bump it this call, so read it **before** `accept()`. On the first `Ready` (turns[0] present) the pre-accept value is `0` → warm ordinal 0 = the line about to be spoken. On later states during a learner turn the pre-accept value is the current displayed-count `k` → warm ordinal k = the next line.
> - In `onOpponentTtsDone`, no display happens, so the current `opponentTurnSerial` already equals the displayed-count `k`, and ordinal `k` (index `2k`) is the next opponent line. Read it directly.
>
> A `lastWarmedOrdinal` guard avoids re-launching a coroutine for an ordinal already warmed (both sites and every SSE chunk otherwise re-trigger). Coordinator-level cache/in-flight dedup is still the correctness backstop; the guard is a cheap optimization that also silences the per-chunk `settingsRepo.current()` churn.

In `GeneratedDialogueSessionViewModel`, add the guard field near the other private turn fields (e.g. beside `latestTurns` ~line 269) and the trigger method near `speakOpponent` (~line 430):

```kotlin
        // 이미 프리페치를 발주한 상대 라인 서수(중복 발주·SSE 청크마다의 코루틴 런치 억제). 실제 발주 성공 시에만
        // 갱신하므로 turns 미도착으로 실패하면 다음 상태에서 재시도된다. 세션 복원 시 -1로 리셋.
        private var lastWarmedOrdinal = -1
```

```kotlin
        /** 주어진 상대 서수(ordinal, 0-기반)의 라인 오디오를 미리 서버 합성해 캐시에 채운다. 코디네이터가
         *  SERVER·비음소거 게이트와 중복요청 dedup을 처리하므로 발주 자체는 안전하다. lastWarmedOrdinal 로
         *  같은 서수 반복 발주만 억제한다. 라인 미도착(null)이면 lastWarmedOrdinal 을 갱신하지 않아 재시도된다. */
        private fun prefetchOpponentLine(ordinal: Int) {
            if (ordinal == lastWarmedOrdinal) return
            val text = nextOpponentEnglish(latestTurns, ordinal) ?: return // 아직 미도착 — 다음 상태에서 재시도
            lastWarmedOrdinal = ordinal
            tts.prefetch(text, opponentSpeaker?.gender)
        }
```

In `acceptGenerationState` (lines 414-420), capture the ordinal **before** `accept()` (which may bump it) and prefetch that line after the speaker is known:

```kotlin
        private fun acceptGenerationState(state: DialogueGenState) {
            if (state is DialogueGenState.Ready) latestTurns = state.turns
            val ordinalBeforeAccept = turnState.opponentTurnSerial // accept()가 이번에 증가시킬 수 있어 그 전에 읽는다
            turnState.accept(state)
            reconcileLearnerClips() // turns 축소 리셋 시 사라진 순번의 stale 클립 파기
            assignSpeakerIfNeeded()
            prefetchOpponentLine(ordinalBeforeAccept) // 첫 라인 워밍(서수 0) + 스트리밍으로 늦게 온 라인 재시도
            persistResume()
        }
```

In `onOpponentTtsDone()` (lines 467-471), append the next-line prefetch using the current serial (no display happened, so it already equals the displayed-count):

```kotlin
        private fun onOpponentTtsDone() {
            // 자동발화 완료가 턴을 마감한다. progress 경유(completeOpponentTurn())라야 마감 후 durable 스냅샷이
            // 갱신돼 master 의 "전진 후 영속" 계약이 유지된다(turnState 직접 호출은 persistResume 를 건너뜀).
            completeOpponentTurn()
            prefetchOpponentLine(turnState.opponentTurnSerial) // 학습자 턴 진입 → 다음 상대 라인 미리 합성
        }
```

> Note: keep the existing body of `onOpponentTtsDone()` (lines 467-471) — including its `completeOpponentTurn()` call — and append the `prefetchOpponentLine(...)` line as the last statement.

Reset the guard on session restore, where `latestTurns` is re-seeded from a snapshot (line ~347):

```kotlin
            latestTurns = snapshot.turns.map { it.toDomain() }
            lastWarmedOrdinal = -1 // 복원된 위치에서 다시 워밍하도록 리셋
```

In the cleanup where `tts.stop()` is called (`onCleared`, line ~689), add cache clearing:

```kotlin
            tts.stop() // 잔여 발화 차단(nav-pop 시 이 훅이 커버 — 별도 onExit 훅 없음).
            tts.clearCache() // 프리페치/캐시 파기(화면 이탈 — 다음 세션에 stale 오디오가 새지 않게).
```

- [ ] **Step 6: Run the full verification set**

Run: `scripts/verify-android.sh`
Expected: compiles; detekt clean on the changed files; both variant unit-test source sets green (including the new `NextOpponentLineTest` and Task 1/2 coordinator tests). Note: the branch may show the pre-existing, unrelated master reds (`SettingsScreen.kt:213` detekt, `RecordsSkeletonTest` Robolectric flake) — confirm those files are untouched by this diff (`git diff --stat`) and that no NEW failure is attributable to this change. Register the new `NextOpponentLineTest` in `build.gradle.kts`'s release-variant exclude list **only if** the repo convention requires it for JVM unit tests (it typically applies to Robolectric/Compose tests; `NextOpponentLineTest` is a pure JUnit test with no Android deps, so it should need no exclusion — verify by confirming `:app:testReleaseUnitTest` compiles it).

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/NextOpponentLineTest.kt
git commit -m "feat(dialogue): prefetch upcoming opponent line so TTS feels instant

Warms the first opponent line when the script is ready and line N+1 the moment
line N finishes (during the learner's turn), via the coordinator's prefetch/cache.
By the time each opponent turn arrives its PCM is cached → instant playback and
instant 다시 듣기. Clears the cache on screen exit. Adds pure nextOpponentEnglish
(even-index=model) with unit tests.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Warm the first opponent line during the loading-quiz screen

**Goal:** Eliminate the first-line wait too. Generation runs while the loading-quiz screen (`DialogueGeneratingScreen`) is on screen, and the first opponent line + `sessionId` exist the moment generation reaches `Ready` (the same signal that surfaces the "대화 시작하기" CTA). Warm that line then, so it is already cached by the time the chat screen opens. The `TtsPlaybackCoordinator` is a `@Singleton`, so a cache warmed during the loading quiz survives the generating→session nav pop.

**Design note (why a synchronous method, not a collector):** the trigger is a plain `warmFirstLine()` called by the Route when `Ready` first appears, **not** a `coordinator.state.collect{}` inside the ViewModel. `DialogueGenerationCoordinator` is a process `@Singleton`; a suspended `state.first { it is Ready }` from a *previous* (e.g. failed) generation's VM would still be alive on the shared flow and would fire for the *next* generation's `Ready`, prefetching a stale line. A synchronous method invoked from the Route's `Ready`-scoped `LaunchedEffect` is bound to the live screen and reads the current state directly, sidestepping that.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModel.kt` — inject `TtsPlaybackCoordinator`; add `warmFirstLine()`.
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreen.kt` — call `viewModel.warmFirstLine()` from a `Ready`-keyed `LaunchedEffect` in `DialogueGeneratingRoute`.
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModelTest.kt`.

**Interfaces:**
- Consumes: `TtsPlaybackCoordinator.prefetch(text, gender)` (Task 2); `nextOpponentEnglish(turns, ordinal)` (Task 3, `internal` — same `:app` module, cross-package OK); `DialogueGenerationCoordinator.state` (`DialogueGenState.Ready(sessionId: String?, …, turns: List<DialogueTurn>)`); `SpeakerDirectory.assign(sessionId: String): Speaker` (`.gender`).
- Produces: `fun warmFirstLine()` on `DialogueGenerationViewModel`.

- [ ] **Step 1: Write the failing test**

Add to `DialogueGenerationViewModelTest.kt`. First, minimal TTS fakes (place beside the existing `FakeStream`/`FakeQuizBank` fakes) — the real `TtsPlaybackCoordinator` is `final`, so build one from these:

```kotlin
private class CountingTtsApi : LlmApi {
    var callCount = 0
    var lastText: String? = null
    var lastGender: String? = null

    override suspend fun tts(body: TtsRequest): TtsResponse {
        callCount++
        lastText = body.payload.text
        lastGender = body.payload.gender
        return TtsResponse(pcmBase64 = "", sampleRate = 24000, mimeType = "audio/L16;rate=24000")
    }

    override suspend fun speaking(body: SpeakingRequest): SpeakingResponse = error("unused")
}

private class NoopPcmPlayer : PcmPlayer {
    override suspend fun play(pcm: ByteArray, sampleRateHz: Int) = Unit
    override fun stop() = Unit
}

private class NoopDeviceTts : DeviceTts {
    override suspend fun speak(text: String, gender: String?, speechRate: Float, onStart: () -> Unit): DeviceTtsResult =
        DeviceTtsResult.COMPLETED

    override fun stop() = Unit
}

private class ServerTtsSettings : TtsSettingsRepository {
    override val settings: Flow<TtsSettings> = flowOf(TtsSettings())
    override suspend fun current(): TtsSettings = TtsSettings() // default quality = SERVER
    override suspend fun setQuality(quality: TtsQuality) = Unit
    override suspend fun setSpeechRate(rate: Float) = Unit
    override suspend fun setMuted(muted: Boolean) = Unit
}
```

Then the test:

```kotlin
    @Test
    fun `warmFirstLine prefetches the first opponent line once generation is Ready`() =
        runTest {
            val stream = FakeStream()
            val scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
            val coordinator = DialogueGenerationCoordinator(stream, scope, FakeConnectivity(offline = false))
            val ttsApi = CountingTtsApi()
            val tts = TtsPlaybackCoordinator(ttsApi, NoopPcmPlayer(), NoopDeviceTts(), ServerTtsSettings(), scope)

            coordinator.start("easy", "t", 5, firstSession = true)
            runCurrent()
            stream.push(DialogueEvent.Start(sessionId = "s1", remaining = 3))
            stream.push(DialogueEvent.Turn(DialogueTurn(ko = "안녕", en = "Hello there", role = "model")))
            runCurrent()
            assertTrue(coordinator.state.value is DialogueGenState.Ready)

            val vm =
                DialogueGenerationViewModel(
                    coordinator,
                    bank,
                    RecordingAnalytics(),
                    RecordingLimitAnalytics(),
                    SessionSnapshotStore(inMemoryPrefsDataStore()),
                    scope,
                    RecordingOfflineAnalytics(),
                    FakeConfig(true),
                    tts,
                )
            vm.warmFirstLine()
            advanceUntilIdle()

            assertEquals(1, ttsApi.callCount)
            assertEquals("Hello there", ttsApi.lastText) // the first opponent (model, index 0) line
        }
```

> Note the VM's `init { coordinator.reset() }` sets state to Idle; construct the VM **after** driving the coordinator to Ready so the reset does not wipe the state (this mirrors the existing `sticky Ready` test's ordering — the `reset()` runs at construction, and here Ready is re-established by the already-pushed events being sticky). If `reset()` clears the pushed Ready, restructure the test to push the Start/Turn events **after** VM construction (still before `warmFirstLine()`); either way `warmFirstLine()` must run while `coordinator.state.value is Ready`.

Add imports to the test: `com.jjundev.oneclickeng.core.network.LlmApi`, `TtsRequest`, `TtsResponse`, `SpeakingRequest`, `SpeakingResponse`, `com.jjundev.oneclickeng.core.audio.PcmPlayer`, `com.jjundev.oneclickeng.feature.session.tts.{TtsPlaybackCoordinator, DeviceTts, DeviceTtsResult}`, `com.jjundev.oneclickeng.core.settings.{TtsSettings, TtsQuality, TtsSettingsRepository}`, `kotlinx.coroutines.flow.flowOf`, `kotlinx.coroutines.test.advanceUntilIdle`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*DialogueGenerationViewModelTest*'`
Expected: FAIL to compile — `warmFirstLine` is unresolved and the `DialogueGenerationViewModel(...)` constructor does not yet take a `tts` argument.

- [ ] **Step 3: Inject the coordinator and add `warmFirstLine()`**

In `DialogueGenerationViewModel.kt`, add `TtsPlaybackCoordinator` to the constructor (after `coordinator`):

```kotlin
        private val coordinator: DialogueGenerationCoordinator,
        private val tts: TtsPlaybackCoordinator,
```

Add the method (e.g. after `start()`):

```kotlin
        /** 로딩 퀴즈가 떠 있는 동안 첫 상대 대사 오디오를 미리 서버 합성해 캐시에 채운다(Route 가 Ready 도착 시 호출).
         *  TtsPlaybackCoordinator 는 @Singleton 이라 이 VM 이 파괴돼도(생성→채팅 nav pop) 캐시가 살아 있어,
         *  채팅의 speakOpponent 가 같은 라인을 즉시 재생한다(같은 sessionId→같은 gender→같은 캐시 키). 코디네이터
         *  prefetch 가 SERVER·비음소거 게이트와 dedup 을 처리하므로 여기선 무조건 호출해도 안전(중복 호출 무해). */
        fun warmFirstLine() {
            val ready = coordinator.state.value as? DialogueGenState.Ready ?: return
            val text = nextOpponentEnglish(ready.turns, 0) ?: return
            val gender = ready.sessionId?.let { SpeakerDirectory.assign(it).gender }
            tts.prefetch(text, gender)
        }
```

Add imports: `com.jjundev.oneclickeng.feature.session.tts.TtsPlaybackCoordinator`, `com.jjundev.oneclickeng.feature.session.turn.SpeakerDirectory`, `com.jjundev.oneclickeng.feature.session.turn.nextOpponentEnglish`. (`DialogueGenState` is already in-package.) Hilt provides `TtsPlaybackCoordinator` (`@Singleton @Inject`) with no module change.

Update the existing test's shared `viewModel(...)` builder (~line 279) and the `sticky Ready` test's direct construction (~line 252) to pass a throwaway coordinator as the new last arg, e.g. give the builder a defaulted param and pass it through:

```kotlin
    private fun TestScope.viewModel(
        analytics: RecordingAnalytics,
        config: FakeConfig,
        stream: FakeStream = FakeStream(),
        limitAnalytics: RecordingLimitAnalytics = RecordingLimitAnalytics(),
        connectivity: ConnectivityObserver = FakeConnectivity(offline = false),
        offlineAnalytics: OfflineAnalytics = RecordingOfflineAnalytics(),
    ): DialogueGenerationViewModel {
        val scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val coordinator = DialogueGenerationCoordinator(stream, scope, connectivity)
        val snapshotStore = SessionSnapshotStore(inMemoryPrefsDataStore())
        val tts = TtsPlaybackCoordinator(CountingTtsApi(), NoopPcmPlayer(), NoopDeviceTts(), ServerTtsSettings(), scope)
        return DialogueGenerationViewModel(
            coordinator,
            tts,
            bank,
            analytics,
            limitAnalytics,
            snapshotStore,
            scope,
            offlineAnalytics,
            config,
        )
    }
```

Apply the same new `tts` argument (position 2, right after `coordinator`) to the `sticky Ready` test's inline `DialogueGenerationViewModel(...)` construction.

- [ ] **Step 4: Wire the Route to call it on `Ready`**

In `DialogueGeneratingScreen.kt`, in `DialogueGeneratingRoute`, after `val state by viewModel.state.collectAsStateWithLifecycle()`, add:

```kotlin
    // 로딩 퀴즈가 떠 있는 동안 첫 상대 대사를 미리 합성해둔다(Ready = 첫 대사 도착) → 채팅 진입 시 즉시 재생.
    LaunchedEffect(state is DialogueGenState.Ready) {
        if (state is DialogueGenState.Ready) viewModel.warmFirstLine()
    }
```

`DialogueGenState` is already imported in this file (the screen switches on it). The `LaunchedEffect` key is the `Ready` boolean, so the call fires once when generation becomes ready (and the coordinator's `prefetch` dedup makes any extra call from recomposition a no-op).

- [ ] **Step 5: Run the test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*DialogueGenerationViewModelTest*'`
Expected: PASS — the new test shows `ttsApi.callCount == 1` and `lastText == "Hello there"`, and all pre-existing VM tests still pass with the added constructor arg.

- [ ] **Step 6: Run the full verification set**

Run: `scripts/verify-android.sh`
Expected: compiles; detekt clean on changed files; both variant unit-test source sets green. (Pre-existing unrelated master reds — `SettingsScreen.kt:213` detekt, `RecordsSkeletonTest` flake — may still appear; confirm they are not newly caused by this diff via `git diff --stat`.)

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModel.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreen.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModelTest.kt
git commit -m "feat(dialogue): warm the first opponent line during the loading quiz

Generation runs while the loading-quiz screen is up, so the first opponent line
exists at Ready. warmFirstLine() prefetches it then (a synchronous method called
from the Route's Ready-scoped LaunchedEffect — not a shared-singleton collector
that could fire for the next session). The @Singleton coordinator keeps the cache
warm across the generating→chat nav pop, so even the first line plays instantly.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: Backend — verify the `/llm` function stays warm

**Goal:** Confirm the deployed Cloud Function runs with `minInstances ≥ 1` (no cold-start penalty per opponent line). This is a **verification-first** task — the code default is already 1, so a change is needed only if the deployed function is found at 0. Since "매 대사가 너무 느려" (every line, not just the first) points to synthesis time rather than cold starts, this is a secondary lever, not the primary fix.

**Files:**
- Inspect: `functions/src/llm/handler.ts` (`minInstances: LLM_MIN_INSTANCES`), `functions/src/llm/options.ts` (`LLM_MIN_INSTANCES_DEFAULT = 1`), `.firebaserc` (project id).
- Modify only if found at 0: the deploy environment for `LLM_MIN_INSTANCES` (must not be `0` in prod), then redeploy `functions/`.

**Interfaces:**
- Consumes: nothing in the app. Produces: a confirmed-warm `/llm` endpoint. No code artifact unless a redeploy is required.

> **May require human/ops with prod credentials.** The commands below need `gcloud`/`firebase` authenticated against the prod project. If the executor lacks prod access, record the exact commands and expected checks and hand this task to the human — do not fabricate a result.

- [ ] **Step 1: Read the prod project id**

Read `.firebaserc` and note the prod project (expected `oce-v1`, per memory / `functions` deploy target).

- [ ] **Step 2: Inspect the deployed function's min-instances**

Run one of (whichever the environment has authenticated):

```bash
# Cloud Run (gen2 functions run on Cloud Run) — check min instance count:
gcloud run services describe llm --region=asia-northeast3 --project=<PROD_PROJECT> \
  --format='value(spec.template.metadata.annotations["autoscaling.knative.dev/minScale"])'
# or the Functions view:
gcloud functions describe llm --region=asia-northeast3 --gen2 --project=<PROD_PROJECT> \
  --format='value(serviceConfig.minInstanceCount)'
```

Expected (healthy): `1` (or higher). Record the value.

- [ ] **Step 3: Decide and act**

- **If `≥ 1` → PASS.** No change. Note in the report that warmth is confirmed and the primary latency win is the client prefetch (Tasks 1-4). Done.
- **If `0` → cold starts are contributing.** Ensure the prod deploy sets `LLM_MIN_INSTANCES` to `1` (never leave it defaulted-away in prod). Redeploy from `functions/`:

```bash
cd functions
# per this project's deploy convention (see functions/README.md); do NOT invent flags:
firebase deploy --only functions:llm --project <PROD_PROJECT>
```

Re-run Step 2 to confirm it now reports `1`. Note the always-on warm instance has a standing cost (~1 idle instance); this is the SoT-mandated NFR-3 tradeoff, not a regression.

- [ ] **Step 4: Record the outcome**

No commit unless a config file under `functions/` changed. If a redeploy happened, commit any changed deploy config with:

```bash
git add functions/
git commit -m "chore(functions): ensure /llm minInstances=1 in prod (warm TTS)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

Otherwise report "verified warm, no change".

---

## Task 6: On-device end-to-end verification (before/after latency)

**Goal:** Prove the dialogue TTS is now fast: capture a baseline, then confirm the first line (warmed during the loading quiz) and subsequent opponent lines play near-instantly, "다시 듣기" is instant, and DEVICE mode / mute / rate-change still behave.

**Files:** none (verification). Requires a connected device/emulator (`adb`). If none is available, defer to a human/device gate and record that.

- [ ] **Step 1: Baseline (optional but recommended)**

Before merging Tasks 1-4, on the current build, run a dialogue with 음질 = "자연스러운 발음" and note the wait between each opponent bubble appearing and its audio starting. If instrumenting: temporarily wrap `synthesize`'s `api.tts` call in a `System.currentTimeMillis()` delta `android.util.Log.d("TTS", …)` and read via `adb logcat -s TTS` — **revert this temporary log before committing anything.** Record the per-line synthesis time and whether the first line is a spike (cold start → Task 5 relevant) or all lines are similar (synthesis-bound → prefetch is the win).

- [ ] **Step 2: Build and install the branch with Tasks 1-4**

Run: `scripts/verify-android.sh :app:installDebug`
Expected: `BUILD SUCCESSFUL`, `Installed on 1 device`.

- [ ] **Step 3: Verify prefetch makes lines instant**

With 음질 = "자연스러운 발음", not muted:
1. Start a dialogue and **linger on the loading quiz** (this needs a generation that takes >1s so the quiz actually shows; if generation is <1s the screen auto-skips and the first line has less warm lead). Tap "대화 시작하기" → the **first** opponent line should start audio near-instantly (warmed during the quiz, Task 4). If generation was <1s, a short first-line wait is acceptable.
2. Answer the first turn. While you're answering, the next opponent line is being prefetched.
3. When the **next** opponent turn arrives, its audio should start **near-instantly** (cache hit) — the multi-second wait per line should be gone. Repeat for each turn.

- [ ] **Step 4: Verify instant replay and mode/setting correctness**

1. Tap "다시 듣기" on a revealed opponent bubble → audio replays **instantly** with no server wait (cache hit); the turn does not advance.
2. Switch 음질 = "빠른 발음" (DEVICE) → opponent lines use device TTS, no `/llm` `task=tts` prefetch or playback requests fire.
3. Change the **speed** slider mid-session, then hear a new opponent line → it plays at the new speed (cache re-synthesized for the new rate, not stale-speed).
4. Toggle **전체 음소거** on → no audio, turns still advance; no prefetch requests fire.

Expected: subsequent lines and replay are near-instant in SERVER mode; DEVICE/mute/rate behaviors unchanged.

- [ ] **Step 5: Record results**

Note the before/after per-line wait (e.g. "~4s → <0.5s on cached lines"), whether the **first** line was instant when the quiz was shown (>1s generation) vs a short wait when the quiz auto-skipped (<1s generation), and any anomaly (a line that fell back to device — capture logcat). No commit.

---

## Non-Goals / Follow-ups

- **Streaming TTS** (start playback before full synthesis): a larger change with uncertain Gemini support; not needed once prefetch hides per-line latency. Out of scope.
- **First-line latency when generation is fast (<1s):** Task 4 warms the first line during the loading quiz, but if generation finishes under the 1s quiz gate the screen auto-skips (`DialogueGeneratingScreen.kt:100-103`) and there is little warm lead — a short first-line wait can remain in that case. Fully hiding it would require warming before `Ready` (the first line does not exist yet), which is out of scope.
- **Cross-session TTS cache persistence / disk cache:** the cache is in-memory and session-scoped (cleared on exit) by design (tts.md §17: lines are session-unique).
- **`replay()`/`lastPcm` legacy path:** left as-is. The instant-replay requirement is met by the cache-aware `playTurn`, so the dormant `replay()` auto-advance bug is not on this plan's path; fixing it is a separate cleanup.
- **Prefetch depth > 1 line:** only the immediate next opponent line is warmed. Deeper look-ahead is unnecessary for the observed turn cadence and would enlarge the cache/cost surface.

---

## Self-Review

**Spec coverage:**
- "매 대사가 너무 느려" → Tasks 1-3 add prefetch so each opponent line's audio is synthesized during the prior learner turn and played from cache instantly; Task 4 warms the first line during the loading quiz. Task 5 confirms the backend is warm (secondary lever). Covered.
- "보조래버까지 포함" (secondary levers) → first-line warm inside the chat VM (Task 3, `acceptGenerationState` prefetch at the **pre-`accept()`** ordinal 0) **and** earlier during the loading-quiz screen (Task 4, `warmFirstLine()` at `Ready`) + backend min-instances verification (Task 5). Covered.
- "로딩 퀴즈 화면에서 첫 대사 미리 저장" (warm the first line during the loading-quiz screen) → Task 4: generation reaches `Ready` (first line + sessionId exist) while the quiz is on screen; the `@Singleton` coordinator's cache survives the nav pop to chat; tested by `warmFirstLine prefetches the first opponent line once generation is Ready`. Covered.
- "다시 재생 … 서버에서 받은 음성을 저장" (cache and replay from stored PCM) → Task 1's cache-aware `playFromServer` (via `obtainAudio`) makes the existing `replayOpponent → playTurn(advanceOnDone=false)` a cache hit; tested by `replay path advanceOnDone false of a cached line makes no server call`. Covered.

**Placeholder scan:** No TBD/"handle edge cases". Every code step contains full code; every test step contains full test bodies and exact run commands with expected output. Task 4's deploy commands are marked as possibly-human and instruct not to invent flags.

**Concurrency:** Exactly-once synthesis is enforced by the shared in-flight `Deferred` map inside `obtainAudio` (a live `playTurn` joins an in-progress `prefetch` rather than double-calling); tested by `playTurn joins an in-flight prefetch instead of re-synthesizing`. In-flight eviction is attached to the synthesis job's own `invokeOnCompletion`, **not** to any awaiter's `finally` — because `scope.async` is a sibling (not a child) of the awaiters, a `startNewSession()` that cancels a live awaiter mid-await must not evict a still-running entry; otherwise a same-key caller would start a second synthesis. This is regression-tested by `a cancelled awaiter does not evict an in-flight synthesis (no duplicate call)`. The synthesis job caches its own result, so a cancelled awaiter still populates the cache. All cache/in-flight map access is on the single-threaded `Dispatchers.Main.immediate` scope (no locks).

**Type consistency:** `TtsCacheKey(text, gender, speechRate)` used identically in `obtainAudio` and cache access. `CachedAudio(pcm, sampleRate)`. `obtainAudio(text, gender, rate): CachedAudio?` is the single shared synthesis authority used by both `playFromServer` and `prefetch`; `synthesize(text, gender, rate): CachedAudio?` is the raw network+decode step it wraps. `prefetch(text, gender)` / `clearCache()` signatures match their VM call sites (`tts.prefetch(text, opponentSpeaker?.gender)`, `tts.clearCache()`). `nextOpponentEnglish(turns: List<NetworkDialogueTurn>, opponentOrdinal: Int): String?` and `prefetchOpponentLine(ordinal: Int)` match the test and the two call sites. `CACHE_CAP` referenced by its companion name. Imports added: `kotlinx.coroutines.Deferred`, `kotlinx.coroutines.async`.
