# M4-01e (Phase 2, Slice 3b) — Latency Telemetry — HANDOFF / PRE-PLAN

> **What this is:** a handoff/scoping doc for the LAST remaining M4-01 analytics slice — the `*_latency_ms` series — to be picked up in a **separate session**. It is NOT yet a full implementation plan. It captures the event contract, the emit-site map, the shared infrastructure prerequisite, the open product decisions that must be answered first, and the codebase conventions established by the four completed slices.
>
> **How to use it (next session):**
> 1. Read this doc + `docs/ux/analytics-events.md` §10 (+ `dialogue-learning-flow.md` §13 / lines 304, 310, 320).
> 2. **Answer the Open Product Decisions below** (they change the task decomposition — resolve them with the user via `AskUserQuestion` before writing tasks).
> 3. Verify the emit-anchor map against current code (line numbers drift).
> 4. Run **`/writing-plans`** to produce the TDD plan, then `/subagent-driven-development` to execute — same as slices M4-01a…d.
>
> **Branch context:** the four completed slices live on `claude/plan-implementation-status-86d57c` (unmerged). This doc is on that branch. If the separate session branches from `master`, this file won't be present there — copy it over, or branch from the analytics branch.

## Where M4-01 stands (2026-07-20)

| Slice | Plan | Status |
|---|---|---|
| Phase 1 (AnalyticsSink foundation + ambient seams) | `2026-07-20-m4-01a-analytics-dispatch-foundation.md` | ✅ done |
| Slice 1 (session core funnel, 9 events) | `2026-07-20-m4-01b-session-core-funnel.md` | ✅ done |
| Slice 2 (save-rate + cohort + home) | `2026-07-20-m4-01c-save-identity-home.md` | ✅ done |
| Slice 3a (mic + wait-quiz) | `2026-07-20-m4-01d-mic-waitquiz-telemetry.md` | ✅ done |
| **Slice 3b (latency ×6)** | **this doc → to write** | ⬜ **not started** (no code, no plan) |

After 3b, the remaining M4-01 work is the **human DebugView checkpoint** + the `analytics-events.md` back-fill (see the completed plans' checkpoints).

## Scope — the 6 latency events

Per `analytics-events.md` §10: `*_latency_ms {outcome}` for **script_gen · tts · speaking · slim · deep · summary**. Auxiliary (§10: "성능 관측용 보조 계열이며 5대 지표의 분모는 아니다") — measure how long each backend/LLM/TTS round-trip takes, not a 5-지표 numerator/denominator.

**Event names** (only `speaking_analyze_latency_ms` is pinned; the rest are proposed — finalize per the §2 snake_case convention, as prior slices finalized ids):

| Operation | Event (proposed) | Status |
|---|---|---|
| script generation | `script_gen_latency_ms` | net-new (finalize name) |
| TTS synthesis | `tts_latency_ms` | net-new (finalize name) |
| speaking analysis | `speaking_analyze_latency_ms` | pinned (§10, `dialogue-learning-flow.md:320`) |
| slim feedback | `slim_latency_ms` | net-new (finalize name) |
| deep feedback | `deep_latency_ms` | net-new (finalize name) |
| summary | `summary_latency_ms` | net-new (finalize name) |

**`outcome`** param: `dialogue-learning-flow.md:320` gives `successful / failed / canceled`. Confirm this enum applies to all six; each event = `{outcome, <duration as the *_latency_ms value or a param>}`. **Decide the shape:** GA4 convention here is the duration IS the event's numeric value — i.e. log `<name>_latency_ms` with the elapsed ms as a `value`/`latency_ms` param plus `outcome`. Pin the exact param key (e.g. `latency_ms` + `outcome`) during planning.

## Prerequisite infrastructure — the fake-able Clock/TimeSource seam

**This is the gating first task and the reason latency was split out.** No timing seam exists today: coordinators call `System.currentTimeMillis()` directly where they time anything (e.g. `SessionTurnBufferStore.kt:40`, `SummaryCoordinator.kt:343` — both for studytime, not latency). Slice 3a's wait-quiz timing also used raw `System.currentTimeMillis()` and could only assert `dwell_ms ≥ 0`, not exact values.

All six coordinators already have **JVM unit-test harnesses using `runTest` + virtual time** (`UnconfinedTestDispatcher`/`TestScope`). To assert latency deterministically (e.g. "advance virtual clock 500ms → event logs `latency_ms=500`), introduce a small injectable seam so tests can supply a controllable clock:

- Design sketch (decide the exact shape in planning): an interface like `interface ElapsedClock { fun nowMillis(): Long }` (monotonic preferred — wrap `android.os.SystemClock.elapsedRealtime()` or `System.nanoTime()/1_000_000`, NOT wall-clock `currentTimeMillis` which jumps on NTP/timezone). Provide a `@Singleton` real impl (Hilt-bound) + a `FakeElapsedClock(var now)` test double. Inject it into each timed coordinator.
- **Why monotonic:** latency is a duration; wall-clock can go backwards. Confirm SystemClock is acceptable (it's Android-framework — Robolectric-backed, fine in these tests) vs. a pure-Kotlin `kotlin.time.TimeSource.Monotonic` (no Android dep, but its `markNow()`/`elapsedNow()` shape differs).
- This seam is reusable — after 3b it could retro-fit wait-quiz's `dwell_ms`/`delay_ms_at_show` (Slice 3a) for exact-value testing, but that's out of scope here unless trivially free.

## Emit-anchor map (verify against current code — line numbers drift)

Source root: `android/app/src/main/kotlin/com/jjundev/oneclickeng`. All six are `@Singleton` coordinators, constructor-injected, wrapped by thin `@HiltViewModel`s. Each already has a `*CoordinatorTest.kt` JVM harness.

1. **script_gen** — `feature/session/dialogue/DialogueGenerationCoordinator.kt`. Start = `launchAttempt()` (~154-173). End(success) = first `DialogueEvent.Turn` → `Ready` transition (~191-197). End(failure) = `fail(token)` (~251-258, watchdog/stream-closed). **Cleanest of the six — single start/end pair.** Harness: `DialogueGenerationCoordinatorTest.kt`.

2. **speaking** — `feature/session/speaking/SpeakingAnalysisCoordinator.kt`. Start = `analyze()` (~66-115). End = the `_state.value = when {...}` resolving to `Result`/`Empty`/`Failed` (~102-113) → maps to `outcome` (transcript→successful, empty/failed→…). Clean. Harness: `SpeakingAnalysisCoordinatorTest.kt`.

3. **slim** — `feature/session/feedback/SlimFeedbackCoordinator.kt`. Structurally mirrors deep (section-accumulator state machine with a begin/terminal pattern). Start = its begin-attempt; end = terminal (Ready/Failed). Harness: `SlimFeedbackCoordinatorTest.kt`. (Verify the exact begin/terminal method names.)

4. **deep** — `feature/session/feedback/DeepFeedbackCoordinator.kt`. Start = `beginAttempt()` (~175-191) — NOT `start()` (which is a no-op unless `Idle`, ~85, due to the turn-scoped prefetch cache). End = `readyState()`/`Error` in `settleOnClose`/`afterSection` (~220-241). Harness: `DeepFeedbackCoordinatorTest.kt`.

5. **summary** — `feature/session/summary/SummaryCoordinator.kt`. Start = `launchAttempt()` (~365-384). End = `applyDone`/`onQuotaExceeded`/`failLoadingSections` (~449-461, 421-436, 484-493). **NOT a clean single-shot** — `retry()` (~219-226) re-issues `launchAttempt` per failed section → **see Open Decision #2**. Harness: `SummaryCoordinatorTest.kt` (note: this ctor already gained `sessionFunnel` + `savedCardAnalytics` positional params in slices 1-2, and its manual construction sites in sibling tests need updating on any further ctor change — see Conventions).

6. **tts** — `feature/session/tts/TtsPlaybackCoordinator.kt`. **Hardest.** No single start point: `playTurn` (~128-152), `prefetch` (~207-219), `warmUpModel` (~231-239, deliberately discarded), `awaitWarm` (~248-261) all funnel through `obtainAudio`/`synthesize` (~341-355, 302-330) with **join semantics** — a `prefetch` and a later `playTurn` for the same `(text, gender)` key share one `Deferred`; a cache hit returns instantly (0 network). → **see Open Decision #1.** Harness: `TtsPlaybackCoordinatorTest.kt`.

## Open Product Decisions (ask the user BEFORE writing tasks)

1. **`tts_latency_ms` scope.** Which path counts, given the shared `obtainAudio`/`synthesize` core with join semantics + zero-latency cache hits?
   - *Recommended:* time the actual network synthesis inside `synthesize()` only (the one place a real round-trip always happens); cache hits don't emit (or emit `outcome=cache_hit` with ~0). This measures "how long a real TTS call takes," which is the useful signal.
   - *Alt:* time `playTurn` end-to-end (includes cache-hit fast path → mostly 0s, noisy).

2. **`summary_latency_ms` scope**, given `retry()` re-issues `launchAttempt` per failed section.
   - *Recommended:* measure the **first attempt only** (start→first terminal settle), so the metric is "initial summary generation time," not conflated with user-driven retries.
   - *Alt:* whole multi-attempt session (start→final settle across retries).

3. **Subset vs. all six.** All six are aux/observability. Consider shipping the **4 clean ones first** (`script_gen`, `speaking`, `slim`, `deep`) + `summary` (with decision #2), and treating `tts` (decision #1, hardest) as its own follow-up — OR all six in one plan once #1/#2 are settled. (This is a scoping fork like the earlier slice choices.)

4. **`outcome` enum + duration param shape.** Confirm `successful/failed/canceled` for all six, and the param key for the duration (e.g. `latency_ms`) — pin these in the Event Decision Table.

5. **Clock seam shape** (see Prerequisite): monotonic source (`SystemClock.elapsedRealtime` vs `System.nanoTime` vs `kotlin.time.TimeSource.Monotonic`), interface name, Hilt binding.

## Conventions & gotchas carried from slices M4-01a…d (apply verbatim)

- **Seam pattern (repo convention):** `interface Xxx + NoOpXxx + FirebaseXxx(sink: AnalyticsSink)` dispatching through the existing `core/analytics/AnalyticsSink`; `@Binds` in a Hilt module (a new `LatencyModule`, or extend an existing one). Recommend a single shared **`LatencyAnalytics`** seam with one method (e.g. `fun latency(operation: String, outcome: String, latencyMs: Long)`) rather than bolting 6 methods onto per-feature seams — one dispatch impl, one contract test covering the 6 operation ids.
- **No mockk/Mockito** — hand-written fakes only. Add a `RecordingLatencyAnalytics` fake for emit-site tests.
- **detekt `MaxLineLength` = 120 on BOTH main and test sources; `ReturnCount` max = 2.** Plan snippets routinely exceed 120 — wrap. After each task run `./scripts/verify-android.sh :app:detekt` and confirm touched files are clean (there are ~30 PRE-EXISTING unrelated findings in `OceThemeColorContractTest.kt` — ignore them).
- **Verify with** `./scripts/verify-android.sh :app:testDebugUnitTest --tests "..."` + `:app:compileDebugKotlin`. **Do NOT** run full `check`/`testReleaseUnitTest` (pre-existing failures: the 30 detekt + ~9 release-variant Roborazzi).
- **Positional-ctor breakage:** adding a `latencyAnalytics`/`clock` param to a `@Singleton` coordinator breaks EVERY manual test construction site (the compiler lists them); update each, default new args to `NoOp*`/a fake where it keeps existing cases unaffected. `SummaryCoordinator` especially has multiple construction sites across `SummaryCoordinatorTest.kt` + the local helpers added in slices 1-2 (`SummaryViewModelSessionCompleteTest.kt`, `SummaryPartialFailureAnalyticsTest.kt`, `SummarySavedCardAnalyticsTest.kt`). `DeepFeedbackCoordinatorTest.kt` has ~11 inline construction sites.
- **Same-package top-level `private class` fakes COLLIDE by name** (real Kotlin redeclaration error, hit repeatedly) — give new local test fakes unique prefixes.
- **Kotlin visibility:** `DialogueGenerationViewModel`'s `companion object` is `private` — don't reference its members cross-class. Import TYPES explicitly (a function import does not bring its return type into scope — this bit Slice 3a with `LimitSurface`).
- **Test-name identifiers:** no `;` inside backtick test names (Kotlin rejects it — swap to `,`).
- **Good news for latency:** unlike the session/home VMs (which have NO JVM harness → DebugView-only verification), ALL SIX coordinators HAVE JVM harnesses. With the Clock seam, latency is **deterministically unit-testable** (advance the fake clock, assert exact `latency_ms`). This slice should have real unit tests for every emit-site — no DebugView-only gaps.

## Suggested task decomposition (starting point for /writing-plans)

Assuming decisions land as recommended:
1. `ElapsedClock` seam + real impl + Hilt binding + `FakeElapsedClock` test double. (prerequisite infra)
2. `LatencyAnalytics` seam + `FirebaseLatencyAnalytics(sink)` + `NoOp` + `RecordingLatencyAnalytics` + `@Binds` + contract test (all 6 operation ids/params).
3. script_gen latency (DialogueGenerationCoordinator) — inject clock + latency seam; test via `DialogueGenerationCoordinatorTest` harness (advance clock, assert).
4. speaking latency (SpeakingAnalysisCoordinator).
5. slim latency (SlimFeedbackCoordinator).
6. deep latency (DeepFeedbackCoordinator).
7. summary latency (SummaryCoordinator) — per decision #2.
8. tts latency (TtsPlaybackCoordinator) — per decision #1 (hardest; consider deferring per decision #3).

## Manual / back-fill (human, after implementation)

- GA4 DebugView: confirm each `*_latency_ms` fires with a sane duration + correct `outcome`.
- Back-fill `docs/ux/analytics-events.md` §10 with the finalized event names + the duration param key + the confirmed `outcome` enum.
- This also clears the last item on M4-01's overall DebugView checkpoint (shared with the earlier slices' back-fills: `analyze_failed`, `session_complete` fire-site rename, saved auto-save inclusion, home ids, mic `source`, wait_quiz `skipped`-deferred).
