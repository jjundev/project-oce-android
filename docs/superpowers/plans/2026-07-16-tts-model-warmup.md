# TTS Model Warm-Up + Warm-Path Retry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop the first opponent line of a cold session from falling back to the device voice, with **no backend change and no standing cost** — preheat the Gemini TTS model when the app comes to the foreground, and retry once on the (covered) warm path if a cold call still slips through.

**Architecture:** Two client-side changes to `TtsPlaybackCoordinator`, both grounded in on-device evidence:
1. **`warmUpModel()`** — a fire-and-forget throwaway TTS synthesis fired from `MainActivity.onStart()`. Dialogue/text tasks run on `gemini-3.1-flash-lite` while TTS runs on `gemini-2.5-flash-preview-tts` (`functions/src/config/models.ts:11-22`) — **different models**, so generating the script never warms the speech model. Preheating at app foreground means the first real line's synthesis is warm (~5-6s) by session time instead of cold (>7s).
2. **One retry in `awaitWarm`** — a cold call fails because the *server* aborts its own Gemini request at 7s/attempt (`gemini.ts` `REQUEST_TIMEOUT_MS = 7000`, `MAX_ATTEMPTS = 2`), which no client watchdog can prevent. But a failed attempt **still preheats the model** (proven on device: two failed synths, then the next succeeded in 5.3s), so a single retry lands. The loading gate already covers this wait, so the first line stays on the natural voice.

Together these remove the device-voice fallback without touching the backend. The server-side root fix (`REQUEST_TIMEOUT_MS` 7s → 20s, one constant, zero standing cost) remains a **follow-up** that would make the retry a rarely-used safety net.

**Tech Stack:** Kotlin, Hilt, Kotlin coroutines (`Dispatchers.Main.immediate` app scope), JUnit4 + kotlinx-coroutines-test. Client-only; no `functions/` change.

## Global Constraints

- Single Gradle module `:app`; all verification via `scripts/verify-android.sh` (never bare `./gradlew`). Known PRE-EXISTING unrelated reds outside any diff here: `SettingsScreen.kt:213` detekt (`LoopWithTooManyJumpStatements`) and `RecordsSkeleton*` Robolectric flakes — confirm untouched via `git diff --stat`. Note detekt fails the build on that pre-existing violation and can short-circuit before tests; if so run `:app:testDebugUnitTest :app:testReleaseUnitTest` directly.
- **The warm-up must be SERVER-quality + unmuted only** — never fire a `/llm` call for users on 빠른 발음 (DEVICE) or muted.
- **The warm-up result must be discarded and NOT cached.** Call `synthesize` directly, bypassing `obtainAudio`, so the throwaway line never occupies a slot in the `CACHE_CAP`(4)-bounded LRU and never evicts a real opponent line.
- **Exactly one retry** in `awaitWarm` — not a loop. Two failed attempts must return `false` and let the existing device fallback handle it.
- Do not alter `obtainAudio`'s structure or the concurrency invariants (single-threaded Main scope, no locks; the synthesis job caches its own result and self-evicts from `inFlight` via `invokeOnCompletion`; a cancelled awaiter must not evict a running synthesis).
- The working tree must contain no `DEBUG-tts` instrumentation (the controller reverted it before Task 1 — verified `grep -rn "DEBUG-tts" android/app/src/main` returns nothing). If any turns up, revert it rather than committing it.
- **Lead time is measured, not assumed:** a live on-device capture showed app launch at `23:12:17` and the first real synthesis (`prepareFirstLine` at generation `Ready`) at `23:12:51.685` — **~34s** of lead between app foreground and the first line's synthesis. That comfortably exceeds the preheat's worst case (~14s, the server's 7s×2), which is why firing at `onStart` is sufficient. The retry is the safety net for the case where a user is faster than that.
- **Retry worst case is bounded but long:** each `awaitWarm` attempt is capped by `SYNTH_WATCHDOG_MS` (16s), and the server itself errors at ~14s, so two failed attempts hold the loading gate ~28s before opening (then the live path device-falls-back as before). The expected cold path is far shorter (~14s failed attempt + ~5-6s successful retry ≈ 20s). Task 3 must watch for the pathological case; if it shows up in practice, cap the gate rather than removing the retry.

---

## File Structure

- **Modify:** `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt`
  - Add `WARM_UP_TEXT` constant, a `warmUpJob` field, and the public `warmUpModel()`.
  - Add the single retry to `awaitWarm` (via `||` short-circuit, which keeps it at 2 returns so detekt's `ReturnCount` stays happy).
- **Modify:** `android/app/src/main/kotlin/com/jjundev/oneclickeng/MainActivity.kt` — inject `TtsPlaybackCoordinator`, call `warmUpModel()` from `onStart()` (fires on every app foreground).
- **Modify (doc):** `docs/design/tts.md` — record the two-model reality (text vs speech model), the app-foreground warm-up, and the warm-path retry.
- **Modify (tests):** `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt` — extend `FakeLlmApi` with `failFirst`, add `assertFalse` import, add warm-up + retry tests.

---

## Task 1: Coordinator — `warmUpModel()` + one retry in `awaitWarm`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt`

**Interfaces:**
- Consumes: existing private `synthesize(text, gender, rate)`, `obtainAudio(text, gender, rate)`, `settingsRepo.current()`, `scope`.
- Produces: `fun warmUpModel()` (public, fire-and-forget — MainActivity calls it in Task 2) and `const val WARM_UP_TEXT` in the companion. `awaitWarm(text, gender): Boolean` keeps its signature; only its internals gain the retry.

- [ ] **Step 1: Extend the test fake and write the failing tests**

In `TtsPlaybackCoordinatorTest.kt`, add `assertFalse` to the JUnit imports:

```kotlin
import org.junit.Assert.assertFalse
```

Extend `FakeLlmApi` with a fail-then-succeed mode (this models the real cold behavior: the first call fails but preheats, the next succeeds):

```kotlin
private class FakeLlmApi(
    var response: TtsResponse = okResponse(),
    var error: Throwable? = null,
    var delayMs: Long = 0,
    var failFirst: Int = 0, // first N calls throw — models a cold call the server aborts (it still preheats)
) : LlmApi {
    var callCount = 0

    override suspend fun tts(body: TtsRequest): TtsResponse {
        callCount++
        if (delayMs > 0) delay(delayMs)
        if (callCount <= failFirst) throw RuntimeException("cold synth aborted by server")
        error?.let { throw it }
        return response
    }

    override suspend fun speaking(body: SpeakingRequest): SpeakingResponse = error("unused")
}
```

Then add the tests:

```kotlin
    @Test
    fun `warmUpModel preheats with a throwaway synthesis that is never played`() =
        runTest {
            val api = FakeLlmApi()
            val player = FakePcmPlayer()
            val coordinator =
                TtsPlaybackCoordinator(api, player, FakeDeviceTts(), FakeSettings(), coordScope())
            val completions = collectCompletions(coordinator)

            coordinator.warmUpModel()
            advanceUntilIdle()

            assertEquals(1, api.callCount) // the preheat call fired
            assertTrue(player.played.isEmpty()) // but nothing was played
            assertEquals(PlaybackState.IDLE, coordinator.state.value) // and playback state is untouched
            assertTrue(completions.isEmpty())
        }

    @Test
    fun `warmUpModel is a no-op in DEVICE quality and when muted`() =
        runTest {
            val deviceApi = FakeLlmApi()
            TtsPlaybackCoordinator(
                deviceApi,
                FakePcmPlayer(),
                FakeDeviceTts(),
                FakeSettings(TtsSettings(quality = TtsQuality.DEVICE)),
                coordScope(),
            ).warmUpModel()
            advanceUntilIdle()
            assertEquals(0, deviceApi.callCount)

            val mutedApi = FakeLlmApi()
            TtsPlaybackCoordinator(
                mutedApi,
                FakePcmPlayer(),
                FakeDeviceTts(),
                FakeSettings(TtsSettings(muted = true)),
                coordScope(),
            ).warmUpModel()
            advanceUntilIdle()
            assertEquals(0, mutedApi.callCount)
        }

    @Test
    fun `warmUpModel does not stack while one is already in flight`() =
        runTest {
            val api = FakeLlmApi(delayMs = 1_000) // keep the first preheat in flight
            val coordinator =
                TtsPlaybackCoordinator(api, FakePcmPlayer(), FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.warmUpModel()
            coordinator.warmUpModel() // a second foreground before the first finished
            advanceUntilIdle()

            assertEquals(1, api.callCount)
        }

    @Test
    fun `warmUpModel does not occupy the cache`() =
        runTest {
            // The throwaway line must not take a slot in the CACHE_CAP-bounded cache, so a later
            // real play of the same text still synthesizes.
            val api = FakeLlmApi()
            val coordinator =
                TtsPlaybackCoordinator(api, FakePcmPlayer(), FakeDeviceTts(), FakeSettings(), coordScope())

            coordinator.warmUpModel()
            advanceUntilIdle()
            assertEquals(1, api.callCount)

            coordinator.playTurn(TtsPlaybackCoordinator.WARM_UP_TEXT, null)
            advanceUntilIdle()
            assertEquals(2, api.callCount) // not served from cache — the preheat was discarded
        }

    @Test
    fun `awaitWarm retries once after a cold failure and caches the retry`() =
        runTest {
            val api = FakeLlmApi(failFirst = 1) // cold attempt aborts; the retry lands
            val player = FakePcmPlayer()
            val coordinator =
                TtsPlaybackCoordinator(api, player, FakeDeviceTts(), FakeSettings(), coordScope())

            assertTrue(coordinator.awaitWarm("Line one", "female"))
            advanceUntilIdle()
            assertEquals(2, api.callCount) // cold attempt + successful retry

            coordinator.playTurn("Line one", "female")
            advanceUntilIdle()
            assertEquals(2, api.callCount) // the retry's audio was cached → live play is a hit
            assertEquals(1, player.played.size)
        }

    @Test
    fun `awaitWarm gives up after exactly one retry`() =
        runTest {
            val api = FakeLlmApi(failFirst = 2) // both attempts fail
            val coordinator =
                TtsPlaybackCoordinator(api, FakePcmPlayer(), FakeDeviceTts(), FakeSettings(), coordScope())

            assertFalse(coordinator.awaitWarm("Line one", null))
            advanceUntilIdle()
            assertEquals(2, api.callCount) // one retry only — not a loop
        }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsPlaybackCoordinatorTest*'`
Expected: FAIL to compile — `warmUpModel` and `TtsPlaybackCoordinator.WARM_UP_TEXT` are unresolved. (`awaitWarm retries once…` would additionally fail on `api.callCount == 1` once it compiles, since there is no retry yet.)

- [ ] **Step 3: Add `WARM_UP_TEXT`, `warmUpJob`, and `warmUpModel()`**

Add to the companion object (next to `SERVER_WATCHDOG_MS` / `SYNTH_WATCHDOG_MS` / `DEVICE_WATCHDOG_MS`):

```kotlin
            // Throwaway text for [warmUpModel] — one short word is enough to make the server issue a
            // real TTS request; the audio is discarded, so keep it minimal (cost is per audio token).
            const val WARM_UP_TEXT = "Hi"
```

Add the field next to `prefetchJobs`:

```kotlin
        // 전면 진입 시 모델 예열용 throwaway 합성 job. 진행 중이면 중복 발주하지 않는다.
        private var warmUpJob: Job? = null
```

Add the public method (place it next to `prefetch`):

```kotlin
        /** Preheat the Gemini TTS model with a throwaway synthesis (fire-and-forget; call when the app
         *  comes to the foreground). The text tasks run on a DIFFERENT model (`gemini-3.1-flash-lite`)
         *  than TTS (`gemini-2.5-flash-preview-tts`, config/models.ts), so generating the dialogue never
         *  warms the speech model: the first real line's synthesis is cold (>7s) and the SERVER aborts
         *  its own Gemini request at 7s/attempt → the line device-falls-back. Preheating early means the
         *  first real synthesis is warm (~5-6s) and simply succeeds.
         *
         *  SERVER + unmuted only. Goes straight to [synthesize] — NOT [obtainAudio] — so the throwaway
         *  line is discarded and never occupies a slot in the [CACHE_CAP]-bounded cache. Skipped while a
         *  preheat is already in flight. */
        fun warmUpModel() {
            if (warmUpJob?.isActive == true) return
            warmUpJob =
                scope.launch {
                    val settings = settingsRepo.current()
                    if (settings.muted || settings.quality != TtsQuality.SERVER) return@launch
                    synthesize(WARM_UP_TEXT, gender = null, rate = settings.speechRate) // discarded on purpose
                }
        }
```

- [ ] **Step 4: Add the single retry to `awaitWarm`**

Replace `awaitWarm`'s body's final line so it retries exactly once. Use the `||` short-circuit — it gives "retry only if the first failed" while keeping the function at two `return`s (detekt's `ReturnCount` limit is 2; a third `return` would trip it):

```kotlin
        suspend fun awaitWarm(
            text: String,
            gender: String?,
        ): Boolean {
            val settings = settingsRepo.current()
            if (settings.muted || settings.quality != TtsQuality.SERVER) return true
            // One retry, no loop. A cold call fails because the SERVER aborts its own Gemini request at
            // 7s/attempt (gemini.ts) — no client budget can save it — but that failed attempt still
            // preheats the model, so the retry typically lands (~5-6s). The loading gate covers this
            // wait; succeeding here is what keeps the first line on the natural voice. `||` short-
            // circuits, so the retry only runs when the first attempt failed.
            return obtainAudio(text, gender, settings.speechRate) != null ||
                obtainAudio(text, gender, settings.speechRate) != null
        }
```

Keep the rest of `awaitWarm`'s KDoc, but update its bound sentence to note the retry doubles the worst case (two `SYNTH_WATCHDOG_MS` attempts) — so the loading gate can hold up to ~2×16s in the pathological case where both attempts run to the full budget.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsPlaybackCoordinatorTest*'`
Expected: PASS — the six new tests plus all pre-existing ones.

- [ ] **Step 6: Full verification**

Run: `scripts/verify-android.sh` (or the two `test*UnitTest` tasks directly if detekt short-circuits on the pre-existing violation).
Expected: compiles; no NEW detekt violation from this diff; unit tests green. If new code trips a detekt rule, add a `@Suppress(...)` matching repo convention and note it.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinator.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt
git commit -m "feat(tts): preheat the speech model + retry a cold warm once

The dialogue text model (gemini-3.1-flash-lite) and the speech model
(gemini-2.5-flash-preview-tts) are different, so generating the script never
warms TTS: the first line's synthesis is cold (>7s) and the server aborts its
own Gemini request at 7s/attempt, so the line fell back to the device voice.

warmUpModel() fires a throwaway synthesis (SERVER+unmuted only, discarded and
deliberately uncached) to preheat the speech model. awaitWarm() retries exactly
once — a failed cold attempt still preheats, so the retry lands (~5-6s) and the
loading gate covers the wait, keeping the first line on the natural voice.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: Fire the warm-up when the app comes to the foreground

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/MainActivity.kt`
- Modify: `docs/design/tts.md`

**Interfaces:**
- Consumes: `TtsPlaybackCoordinator.warmUpModel()` (Task 1). `MainActivity` is already `@AndroidEntryPoint`, so field injection works.
- Produces: nothing new. No unit test — this is a 1-line lifecycle wiring with no test seam (MainActivity is not unit-tested in this repo); it is verified on device in Task 3.

- [ ] **Step 1: Inject the coordinator and warm up on `onStart`**

In `MainActivity.kt`, add the imports:

```kotlin
import com.jjundev.oneclickeng.feature.session.tts.TtsPlaybackCoordinator
import javax.inject.Inject
```

Add the injected field and the `onStart` override (place the field above `pendingNav`, and `onStart` after `onCreate`):

```kotlin
    @Inject
    lateinit var tts: TtsPlaybackCoordinator
```

```kotlin
    /**
     * 앱이 전면에 올 때마다 TTS 모델을 예열한다. 대본 생성(`gemini-3.1-flash-lite`)과 음성 합성
     * (`gemini-2.5-flash-preview-tts`)은 **서로 다른 모델**이라 생성이 아무리 돌아도 음성 모델은 차갑다.
     * 콜드 상태의 첫 합성은 7초를 넘겨 서버가 자체 타임아웃으로 포기 → 첫 대사가 단말 음성으로 폴백된다.
     * 세션 시작 전에 미리 데워두면 첫 합성이 웜(~5-6초)이라 그대로 성공한다. SERVER·비음소거 게이트와
     * 중복 방지는 코디네이터가 처리하므로 여기선 무조건 호출한다(fire-and-forget).
     */
    override fun onStart() {
        super.onStart()
        tts.warmUpModel()
    }
```

- [ ] **Step 2: Verify it compiles and nothing regressed**

Run: `scripts/verify-android.sh :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` (Hilt can inject the `@Singleton` coordinator into the `@AndroidEntryPoint` Activity with no module change).

Then: `scripts/verify-android.sh :app:testDebugUnitTest`
Expected: green (aside from the pre-existing `RecordsSkeleton*` flakes).

- [ ] **Step 3: Update the design doc**

In [docs/design/tts.md](docs/design/tts.md), add a short note to §1 (제공자 & 음성) or §4 recording the cold-model reality and the mitigation. Add these lines:

```markdown
- **모델 예열(2026-07-16):** 텍스트 태스크(`gemini-3.1-flash-lite`)와 TTS(`gemini-2.5-flash-preview-tts`)는
  **다른 모델**이라 대본 생성이 음성 모델을 데우지 못한다. 세션 첫 합성은 콜드(>7초)라 서버가 자체
  per-attempt 타임아웃(`gemini.ts` REQUEST_TIMEOUT_MS 7초 × 2회)으로 포기 → 첫 대사가 단말 폴백됐다.
  대응: 앱 전면 진입 시 throwaway 합성으로 모델을 예열(`warmUpModel`, SERVER·비음소거 한정, 결과 폐기·
  캐시 미적재)하고, 워밍 경로는 1회 재시도한다(`awaitWarm` — 실패한 콜드 호출도 모델을 데우므로 재시도가
  성사된다). 후속 근본 수정: 서버 `REQUEST_TIMEOUT_MS` 를 콜드 호출(~10초)보다 길게(예: 20초) 올리면
  재시도는 거의 쓰이지 않는 안전망이 된다(상시 비용 없음).
```

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/MainActivity.kt docs/design/tts.md
git commit -m "feat(app): preheat the TTS model on app foreground

MainActivity.onStart fires TtsPlaybackCoordinator.warmUpModel() so the speech
model is warm by the time a session's first line is synthesized (the text model
used for generation is a different model and never warms it). Documents the
two-model cold-start reality and the retry in tts.md.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: On-device cold verification

**Files:** none (verification). Requires a connected device (`adb` at `~/Library/Android/sdk/platform-tools/adb` — not on PATH). If none, defer to a device gate.

> This is the task that actually proves the fix. The two previous attempts both looked right in code and failed on device, so treat this as the real gate — not a formality.

- [ ] **Step 1: Install**

Run: `scripts/verify-android.sh :app:installDebug`
Expected: `Installed on 1 device`. (If it fails with "No connected devices", the cable dropped — reconnect and retry.)

- [ ] **Step 2: Create a genuinely cold state**

The model only goes cold after a stretch with no TTS traffic. **Leave the app closed for ~30+ minutes** (or test first thing after a long idle). Testing right after a successful session proves nothing — the model is already warm and everything passes trivially.

- [ ] **Step 3: Run a cold session**

With 음질 = "자연스러운 발음", not muted: open the app (this fires the preheat), then start a dialogue as usual and let the first opponent line play.

Expected: the first line plays in the **natural (server) voice** — not the device voice. The loading quiz may hold a few seconds longer than a warm session; that is the preheat/retry doing its job.

- [ ] **Step 4: Confirm no regressions**

Warm session: first line fast, subsequent lines instant. "다시 듣기" instant. 빠른 발음 (DEVICE) → device voice with **no** `/llm` warm-up call fired. 전체 음소거 → silent, turns still advance.

- [ ] **Step 5: If it STILL device-falls-back**

Do not guess. Re-add the `DEBUG-tts` probe (it distinguishes `api.tts THREW after Xms: <error>` = the server gave up, from `synth FAILED after 16000ms` = the client timed out), capture a cold run with a **live** `adb logcat -v time -s DEBUG-tts` stream to a file, and read it. If the log shows the server erroring at ~14s even after the preheat, the server's `REQUEST_TIMEOUT_MS` is the remaining blocker and the backend follow-up below becomes necessary.

- [ ] **Step 6: Record results**

Note the cold first-line outcome (natural vs device), the loading duration, and whether the retry was exercised. Specifically watch for the **pathological hold**: if the loading quiz ever sits ~28s and *then* still gives the device voice, both attempts failed — that means the preheat didn't land and the retry couldn't save it, and the server `REQUEST_TIMEOUT_MS` follow-up is required rather than optional. No commit.

---

## Non-Goals / Follow-ups

- **Server `REQUEST_TIMEOUT_MS` 7s → 20s (the true root fix, one constant + one deploy, zero standing cost).** Deferred, not rejected: with it, a cold call simply succeeds and the retry becomes a rarely-used safety net. Revisit if Task 3 shows the preheat+retry insufficient, or whenever a deploy is convenient.
- **Scheduled keep-warm cron** — declined (24/7 standing cost).
- **Time-based warm-up debounce** — `warmUpModel` fires once per app foreground with only an in-flight guard. A "skip if a synthesis happened in the last N minutes" debounce would trim a few calls but needs an injected clock to stay testable; the current cost (one short throwaway synthesis per foreground, SERVER users only) does not justify it.
- **Warming from the server when generation starts** — considered and rejected: Cloud Run does not guarantee background work after a response, so it is unreliable.
- **Streaming TTS / a faster speech model** — larger changes, out of scope.

---

## Self-Review

**Spec coverage:** The user's chosen approach — "A + 재시도" — is Task 1 (`warmUpModel` = A; the `awaitWarm` retry = 재시도) and Task 2 (fire it on app open, per "앱을 켰을 때"). Task 3 verifies on a genuinely cold model, which is the only state where the bug appears.

**Placeholder scan:** No TBD/vague steps. Every code step carries full code; the six tests have complete bodies and exact expected red/green. Task 3 Step 5 gives a concrete next probe rather than "investigate further".

**Type/name consistency:** `warmUpModel()` (no args, fire-and-forget) matches the `MainActivity.onStart` call site. `WARM_UP_TEXT` is a public companion `const val`, referenced as `TtsPlaybackCoordinator.WARM_UP_TEXT` in the cache test. `awaitWarm(text, gender): Boolean` signature unchanged (only internals). `synthesize(text, gender, rate)` is the existing private helper the warm-up calls directly (bypassing `obtainAudio` per the no-cache constraint). `FakeLlmApi(failFirst = N)` is the new fake param used by both retry tests.
