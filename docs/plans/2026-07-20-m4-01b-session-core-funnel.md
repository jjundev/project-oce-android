# M4-01b (Phase 2, Slice 1) — Session Core Funnel Analytics — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fire the 9 session-lifecycle funnel events — generation start, session start, per-turn (`turn_started`/`turn_completed`/`speaking_analyze_result`/`deep_feedback_opened`), and session end (`session_complete`/`summary_partial_failure`) — to Firebase Analytics from their exact transition points, so the completion-rate, turn-drop-off, deep-feedback-usage, and revisit metrics become computable.

**Architecture:** One new cross-feature seam `SessionFunnelAnalytics` (9 methods) with a Firebase impl that dispatches through the existing `AnalyticsSink` (from M4-01a Phase 1), a `NoOp` default, and a recording test fake. Each emit-site gets the seam injected and calls it at the transition the spec pins. Where a transition lives in an untested ViewModel, the emit decision is extracted into a pure, unit-tested helper and the thin call-site is verified in DebugView.

**Tech Stack:** Kotlin 2.1.20, Hilt (KSP), Firebase Analytics (via `AnalyticsSink` from Phase 1), JUnit4 + Robolectric + kotlinx-coroutines-test.

**Scope:** This is **Slice 1 of Phase 2** (issue [M4-01](../../issues/M4-01-analytics-instrumentation.md); Phase-2 parent list in [2026-07-20-m4-01a-analytics-dispatch-foundation.md](2026-07-20-m4-01a-analytics-dispatch-foundation.md)). It covers the session core funnel only. **Out of scope (later Phase-2 slices):** `saved_card_create`, the link-time `setUserId` re-call (§3b), `wait_quiz_shown`/`wait_quiz_ended`, `mic_permission_*`, the `*_latency_ms` series, and the home `topic_selected`/`session_setting_changed` emit-sites.

## Global Constraints

- **minSdk 26**, JDK 17, Kotlin 2.1.20. No mockk/Mockito — hand-written fakes only.
- **GA4 snake_case** for every event name and param key. **PII boundary:** enum/bool/count/id only, never free text (no transcript, user text, Korean prompt).
- **Reuse the Phase-1 `AnalyticsSink`** (`com.jjundev.oneclickeng.core.analytics.AnalyticsSink`) — do not add a second dispatch path. Use a top-of-file `import` for it (not an inline FQN).
- **Nullable params omit the key when null** (`writing_score`): use `buildMap`.
- **Numeric params → Long** at the seam call site (`turn_index`, `length`, `turn_count`, `sections_failed`, `writing_score`), because `RecordingAnalyticsSink` stores the raw map and only `FirebaseAnalyticsSink.toAnalyticsBundle()` widens Int→Long — a unit test comparing maps needs the value already `Long`.
- **Event-id authority** is `docs/ux/analytics-events.md` §4/§5. The one value this plan finalizes beyond the doc — `speaking_analyze_result.result = "analyze_failed"` for the technical-failure case — is an explicit M4-01 finalization (product-confirmed), to be back-filled into `analytics-events.md`.
- **Verify with `./scripts/verify-android.sh :app:testDebugUnitTest --tests "..."`** then `./scripts/verify-android.sh :app:compileDebugKotlin`. Do NOT run the full `check`/`testReleaseUnitTest` — it has ~30 pre-existing detekt failures in `OceThemeColorContractTest.kt` and ~9 release-variant Roborazzi failures, all unrelated.
- **detekt `MaxLineLength` = 120 is active on BOTH main and test sources.** The code snippets in this plan are illustrative and several exceed 120 chars — you MUST wrap every line you add to ≤120 chars (behavior identical: break long method signatures/`sink.log(...)` map literals across lines, Kotlin-idiomatically). After your task's edits, run `./scripts/verify-android.sh :app:detekt` and confirm the files YOU touched report zero `MaxLineLength` findings (ignore the ~30 pre-existing `OceThemeColorContractTest.kt` findings).
- **Target `GeneratedDialogueSession.kt`'s `GeneratedDialogueState`, NOT `DialogueUiState.kt`'s `DialogueState`** — they share type names but are unrelated; the latter is vestigial stub scaffolding.

## Event Decision Table (the seam's contract)

| Seam method | GA4 event | Params (key → type) | Fires at | Source |
|---|---|---|---|---|
| `firstSessionGenerationStarted(idempotencyKeyPresent)` | `first_session_generation_started` | `idempotency_key_present`→bool | onboarding generation kickoff | Pinned §4; `idempotency_key_present = (StartOutcome.Started)` (code-forced) |
| `firstSessionStarted(sessionId, topicId, length, difficulty)` | `first_session_started` | `session_id`→str, `topic_id`→str, `length`→long, `difficulty`→str | onboarding gen→session hand-off | Pinned §4 |
| `learningSessionStarted(sessionId, topicId, length, level)` | `learning_session_started` | `session_id`→str, `topic_id`→str, `length`→long, `level`→str | revisit gen→session hand-off | Pinned §4/§2.1 |
| `turnStarted(sessionId, turnIndex)` | `turn_started` | `session_id`→str, `turn_index`→long | `LearnerTurn` entry | Pinned §5.2 |
| `turnCompleted(sessionId, turnIndex, inputMode, writingScore)` | `turn_completed` | `session_id`→str, `turn_index`→long, `input_mode`→str(`voice`\|`text`), `writing_score`→long? (omit if null) | learner turn settles/advances | Pinned §4/§5.4 |
| `speakingAnalyzeResult(sessionId, turnIndex, result)` | `speaking_analyze_result` | `session_id`→str, `turn_index`→long, `result`→str(`transcript_present`\|`empty_transcript`\|`analyze_failed`) | speech analysis returns | Pinned §4 + `analyze_failed` finalized here |
| `deepFeedbackOpened(sessionId, turnIndex)` | `deep_feedback_opened` | `session_id`→str, `turn_index`→long | user taps "더보기" | Pinned §4/§6.4 |
| `sessionComplete(sessionId, turnCount, isFirst)` | `session_complete` | `session_id`→str, `turn_count`→long, `is_first`→bool | summary route start (== §5.1 SummaryPreparing) | Pinned §5.1 (fire site renamed — see note) |
| `summaryPartialFailure(sessionId, sectionsFailed)` | `summary_partial_failure` | `session_id`→str, `sections_failed`→long | first ≥1-section-failed settle, once/session | Pinned §4; count + once-per-session dedup finalized here |

**§5.1 fire-site note:** the spec pins `session_complete` to `SessionPhase.SummaryPreparing` entry, but no such phase exists in code (`SessionPhase = {InTurn, AwaitingStreamDone, Completed}`) and there is no "요약 보기" tap screen (the build auto-navigates to summary after a 1s delay). The functionally-equivalent one-shot, decoupled-from-summary-LLM site is `SummaryViewModel.start()` (guarded `if (started) return`). This plan fires there and documents the rename.

**`turn_index` consistency:** all per-turn events use the **0-based index of the current learner turn**, computed as `turnState.messages.count { it is DialogueMessage.Learner }` *before* this turn's learner answer is appended (used for `turn_started`, `speaking_analyze_result`), which equals the `count - 1` value computed *after* the append (used for `turn_completed`, `deep_feedback_opened` via `deepParams.turnIndex`). Both formulas yield the same integer for a given turn.

## File Structure

**New (production):**
- `feature/session/analytics/SessionFunnelAnalytics.kt` — interface + `NoOpSessionFunnelAnalytics` + `FirebaseSessionFunnelAnalytics(sink: AnalyticsSink)` + the pure helper `speakingResultLabel(...)`.
- `feature/session/analytics/SessionFunnelModule.kt` — `@Binds SessionFunnelAnalytics → FirebaseSessionFunnelAnalytics`.

**New (test):**
- `src/test/.../feature/session/analytics/RecordingSessionFunnelAnalytics.kt` — recording fake.
- One `*Test.kt` per task (below).

**Modified (production):**
- `feature/session/turn/GeneratedDialogueSession.kt` — inject seam into `GeneratedDialogueSessionViewModel`; `GeneratedDialogueState` gains an `onEnterLearnerTurn` callback; `PendingTurn` gains `inputMode`+`turnIndex`; `triggerFeedback` gains an `inputMode` param; emit in `recordTurn`/`onAnalysisState`/`expandDeep`; add a `deepLoggedThisTurn` dedup flag reset in `onAdvance`.
- `feature/session/summary/SummaryViewModel.kt` — inject seam + `SessionTurnBufferStore`; fire `session_complete` in `start()`.
- `feature/session/summary/SummaryCoordinator.kt` — inject seam; add `partialFailureLogged` flag + `maybeLogPartialFailure()` helper called from the 3 settle sites.
- `feature/session/dialogue/DialogueGenerationViewModel.kt` — inject seam; fire `first_session_generation_started` in `start()`; add `onConversationStarted()`.
- `feature/session/dialogue/DialogueGeneratingScreen.kt` — wrap `onStartConversation` in `DialogueGeneratingRoute` to call `viewModel.onConversationStarted()`.

---

## Task 1: `SessionFunnelAnalytics` seam + Firebase impl + recording fake

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics/SessionFunnelAnalytics.kt`
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics/SessionFunnelModule.kt`
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/RecordingSessionFunnelAnalytics.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics/SessionFunnelAnalyticsDispatchTest.kt`

**Interfaces:**
- Consumes: `com.jjundev.oneclickeng.core.analytics.AnalyticsSink` (Phase 1) — `fun log(event, params: Map<String,Any> = emptyMap())`.
- Produces: `interface SessionFunnelAnalytics` (9 methods below), `NoOpSessionFunnelAnalytics`, `FirebaseSessionFunnelAnalytics`, `internal fun speakingResultLabel(state: SpeakingAnalysisState): String?`, and (test) `RecordingSessionFunnelAnalytics`.

- [ ] **Step 1: Write the failing contract test** (`SessionFunnelAnalyticsDispatchTest.kt`):

```kotlin
package com.jjundev.oneclickeng.feature.session.analytics

import com.jjundev.oneclickeng.core.analytics.RecordingAnalyticsSink
import com.jjundev.oneclickeng.feature.session.speaking.SpeakingAnalysisState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionFunnelAnalyticsDispatchTest {
    private val sink = RecordingAnalyticsSink()
    private val analytics = FirebaseSessionFunnelAnalytics(sink)

    @Test
    fun `first_session_generation_started carries idempotency_key_present`() {
        analytics.firstSessionGenerationStarted(idempotencyKeyPresent = true)
        assertEquals(
            RecordingAnalyticsSink.Event("first_session_generation_started", mapOf("idempotency_key_present" to true)),
            sink.events.single(),
        )
    }

    @Test
    fun `first_session_started carries topic length difficulty`() {
        analytics.firstSessionStarted(sessionId = "s1", topicId = "cafe", length = 5, difficulty = "easy")
        assertEquals(
            mapOf("session_id" to "s1", "topic_id" to "cafe", "length" to 5L, "difficulty" to "easy"),
            sink.events.single().params,
        )
        assertEquals("first_session_started", sink.events.single().name)
    }

    @Test
    fun `learning_session_started carries topic length level`() {
        analytics.learningSessionStarted(sessionId = "s2", topicId = "airport", length = 10, level = "normal")
        assertEquals(
            RecordingAnalyticsSink.Event(
                "learning_session_started",
                mapOf("session_id" to "s2", "topic_id" to "airport", "length" to 10L, "level" to "normal"),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `turn_started carries session_id and turn_index`() {
        analytics.turnStarted(sessionId = "s1", turnIndex = 2)
        assertEquals(
            RecordingAnalyticsSink.Event("turn_started", mapOf("session_id" to "s1", "turn_index" to 2L)),
            sink.events.single(),
        )
    }

    @Test
    fun `turn_completed carries input_mode and writing_score when present`() {
        analytics.turnCompleted(sessionId = "s1", turnIndex = 0, inputMode = "voice", writingScore = 82)
        assertEquals(
            mapOf("session_id" to "s1", "turn_index" to 0L, "input_mode" to "voice", "writing_score" to 82L),
            sink.events.single().params,
        )
    }

    @Test
    fun `turn_completed omits writing_score when null`() {
        analytics.turnCompleted(sessionId = "s1", turnIndex = 1, inputMode = "text", writingScore = null)
        assertEquals(
            mapOf("session_id" to "s1", "turn_index" to 1L, "input_mode" to "text"),
            sink.events.single().params,
        )
        assertNull(sink.events.single().params["writing_score"])
    }

    @Test
    fun `speaking_analyze_result carries the result label`() {
        analytics.speakingAnalyzeResult(sessionId = "s1", turnIndex = 0, result = "empty_transcript")
        assertEquals(
            RecordingAnalyticsSink.Event(
                "speaking_analyze_result",
                mapOf("session_id" to "s1", "turn_index" to 0L, "result" to "empty_transcript"),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `deep_feedback_opened carries session_id and turn_index`() {
        analytics.deepFeedbackOpened(sessionId = "s1", turnIndex = 3)
        assertEquals(
            RecordingAnalyticsSink.Event("deep_feedback_opened", mapOf("session_id" to "s1", "turn_index" to 3L)),
            sink.events.single(),
        )
    }

    @Test
    fun `session_complete carries turn_count and is_first`() {
        analytics.sessionComplete(sessionId = "s1", turnCount = 5, isFirst = true)
        assertEquals(
            RecordingAnalyticsSink.Event(
                "session_complete",
                mapOf("session_id" to "s1", "turn_count" to 5L, "is_first" to true),
            ),
            sink.events.single(),
        )
    }

    @Test
    fun `summary_partial_failure carries sections_failed count`() {
        analytics.summaryPartialFailure(sessionId = "s1", sectionsFailed = 2)
        assertEquals(
            RecordingAnalyticsSink.Event("summary_partial_failure", mapOf("session_id" to "s1", "sections_failed" to 2L)),
            sink.events.single(),
        )
    }

    @Test
    fun `speakingResultLabel maps each analysis state`() {
        assertEquals("transcript_present", speakingResultLabel(SpeakingAnalysisState.Result("hi")))
        assertEquals("empty_transcript", speakingResultLabel(SpeakingAnalysisState.Empty))
        assertEquals("analyze_failed", speakingResultLabel(SpeakingAnalysisState.Failed))
        assertNull(speakingResultLabel(SpeakingAnalysisState.Analyzing))
        assertNull(speakingResultLabel(SpeakingAnalysisState.Idle))
    }
}
```

> **`SpeakingAnalysisState.Result` takes MORE than one arg** (it's a `data class Result(...)` at `feature/session/speaking/SpeakingAnalysisState.kt:18` with a `transcript` field plus at least an `encouragement` field). Open that file and pass ALL required args — `Result("hi")` as written will fail to compile on arity, not just a field-name mismatch. The other cases (`Empty`/`Failed`/`Analyzing`/`Idle`) are objects.

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*SessionFunnelAnalyticsDispatchTest"`
Expected: FAIL — `FirebaseSessionFunnelAnalytics` / `speakingResultLabel` unresolved.

- [ ] **Step 3: Write the seam + impls + helper** (`SessionFunnelAnalytics.kt`):

```kotlin
package com.jjundev.oneclickeng.feature.session.analytics

import com.jjundev.oneclickeng.core.analytics.AnalyticsSink
import com.jjundev.oneclickeng.feature.session.speaking.SpeakingAnalysisState
import javax.inject.Inject

/**
 * Session-lifecycle funnel telemetry seam (M4-01b). One seam for the whole dialogue-session funnel —
 * generation start → session start → per-turn → session end. Ids/params per analytics-events.md §4/§5
 * (Event Decision Table in the plan). Dispatches through [AnalyticsSink] so the id/param contract is
 * unit-testable (repo convention = no mockk). PII boundary: enum/bool/count/id only.
 */
@Suppress("TooManyFunctions")
interface SessionFunnelAnalytics {
    fun firstSessionGenerationStarted(idempotencyKeyPresent: Boolean)

    fun firstSessionStarted(sessionId: String, topicId: String, length: Int, difficulty: String)

    fun learningSessionStarted(sessionId: String, topicId: String, length: Int, level: String)

    fun turnStarted(sessionId: String, turnIndex: Int)

    fun turnCompleted(sessionId: String, turnIndex: Int, inputMode: String, writingScore: Int?)

    fun speakingAnalyzeResult(sessionId: String, turnIndex: Int, result: String)

    fun deepFeedbackOpened(sessionId: String, turnIndex: Int)

    fun sessionComplete(sessionId: String, turnCount: Int, isFirst: Boolean)

    fun summaryPartialFailure(sessionId: String, sectionsFailed: Int)
}

/** GA4 `result` value for a speech-analysis outcome, or null when the state is not a terminal result. */
internal fun speakingResultLabel(state: SpeakingAnalysisState): String? =
    when (state) {
        is SpeakingAnalysisState.Result -> "transcript_present"
        SpeakingAnalysisState.Empty -> "empty_transcript"
        SpeakingAnalysisState.Failed -> "analyze_failed"
        SpeakingAnalysisState.Analyzing, SpeakingAnalysisState.Idle -> null
    }

/** Default no-op binding until DebugView verification; also the fallback in tests that don't assert analytics. */
@Suppress("TooManyFunctions")
class NoOpSessionFunnelAnalytics
    @Inject
    constructor() : SessionFunnelAnalytics {
        override fun firstSessionGenerationStarted(idempotencyKeyPresent: Boolean) = Unit

        override fun firstSessionStarted(sessionId: String, topicId: String, length: Int, difficulty: String) = Unit

        override fun learningSessionStarted(sessionId: String, topicId: String, length: Int, level: String) = Unit

        override fun turnStarted(sessionId: String, turnIndex: Int) = Unit

        override fun turnCompleted(sessionId: String, turnIndex: Int, inputMode: String, writingScore: Int?) = Unit

        override fun speakingAnalyzeResult(sessionId: String, turnIndex: Int, result: String) = Unit

        override fun deepFeedbackOpened(sessionId: String, turnIndex: Int) = Unit

        override fun sessionComplete(sessionId: String, turnCount: Int, isFirst: Boolean) = Unit

        override fun summaryPartialFailure(sessionId: String, sectionsFailed: Int) = Unit
    }

/** Firebase dispatch via the shared [AnalyticsSink] (M4-01a). */
@Suppress("TooManyFunctions")
class FirebaseSessionFunnelAnalytics
    @Inject
    constructor(
        private val sink: AnalyticsSink,
    ) : SessionFunnelAnalytics {
        override fun firstSessionGenerationStarted(idempotencyKeyPresent: Boolean) =
            sink.log("first_session_generation_started", mapOf("idempotency_key_present" to idempotencyKeyPresent))

        override fun firstSessionStarted(sessionId: String, topicId: String, length: Int, difficulty: String) =
            sink.log(
                "first_session_started",
                mapOf("session_id" to sessionId, "topic_id" to topicId, "length" to length.toLong(), "difficulty" to difficulty),
            )

        override fun learningSessionStarted(sessionId: String, topicId: String, length: Int, level: String) =
            sink.log(
                "learning_session_started",
                mapOf("session_id" to sessionId, "topic_id" to topicId, "length" to length.toLong(), "level" to level),
            )

        override fun turnStarted(sessionId: String, turnIndex: Int) =
            sink.log("turn_started", mapOf("session_id" to sessionId, "turn_index" to turnIndex.toLong()))

        override fun turnCompleted(sessionId: String, turnIndex: Int, inputMode: String, writingScore: Int?) =
            sink.log(
                "turn_completed",
                buildMap {
                    put("session_id", sessionId)
                    put("turn_index", turnIndex.toLong())
                    put("input_mode", inputMode)
                    writingScore?.let { put("writing_score", it.toLong()) }
                },
            )

        override fun speakingAnalyzeResult(sessionId: String, turnIndex: Int, result: String) =
            sink.log(
                "speaking_analyze_result",
                mapOf("session_id" to sessionId, "turn_index" to turnIndex.toLong(), "result" to result),
            )

        override fun deepFeedbackOpened(sessionId: String, turnIndex: Int) =
            sink.log("deep_feedback_opened", mapOf("session_id" to sessionId, "turn_index" to turnIndex.toLong()))

        override fun sessionComplete(sessionId: String, turnCount: Int, isFirst: Boolean) =
            sink.log(
                "session_complete",
                mapOf("session_id" to sessionId, "turn_count" to turnCount.toLong(), "is_first" to isFirst),
            )

        override fun summaryPartialFailure(sessionId: String, sectionsFailed: Int) =
            sink.log("summary_partial_failure", mapOf("session_id" to sessionId, "sections_failed" to sectionsFailed.toLong()))
    }
```

- [ ] **Step 4: Write the DI module** (`SessionFunnelModule.kt`):

```kotlin
package com.jjundev.oneclickeng.feature.session.analytics

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the session funnel seam to its Firebase dispatch impl (M4-01b). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SessionFunnelModule {
    @Binds
    @Singleton
    abstract fun bindSessionFunnelAnalytics(impl: FirebaseSessionFunnelAnalytics): SessionFunnelAnalytics
}
```

- [ ] **Step 5: Write the recording fake** (`RecordingSessionFunnelAnalytics.kt`, test source set) — records each call as a labeled tuple for behavior tests in later tasks:

```kotlin
package com.jjundev.oneclickeng.feature.session.analytics

/** Records session-funnel calls for emit-site behavior tests (repo convention = fakes). */
@Suppress("TooManyFunctions")
class RecordingSessionFunnelAnalytics : SessionFunnelAnalytics {
    data class Call(val name: String, val args: Map<String, Any?>)

    val calls = mutableListOf<Call>()

    override fun firstSessionGenerationStarted(idempotencyKeyPresent: Boolean) {
        calls += Call("first_session_generation_started", mapOf("idempotency_key_present" to idempotencyKeyPresent))
    }

    override fun firstSessionStarted(sessionId: String, topicId: String, length: Int, difficulty: String) {
        calls += Call("first_session_started", mapOf("session_id" to sessionId, "topic_id" to topicId, "length" to length, "difficulty" to difficulty))
    }

    override fun learningSessionStarted(sessionId: String, topicId: String, length: Int, level: String) {
        calls += Call("learning_session_started", mapOf("session_id" to sessionId, "topic_id" to topicId, "length" to length, "level" to level))
    }

    override fun turnStarted(sessionId: String, turnIndex: Int) {
        calls += Call("turn_started", mapOf("session_id" to sessionId, "turn_index" to turnIndex))
    }

    override fun turnCompleted(sessionId: String, turnIndex: Int, inputMode: String, writingScore: Int?) {
        calls += Call("turn_completed", mapOf("session_id" to sessionId, "turn_index" to turnIndex, "input_mode" to inputMode, "writing_score" to writingScore))
    }

    override fun speakingAnalyzeResult(sessionId: String, turnIndex: Int, result: String) {
        calls += Call("speaking_analyze_result", mapOf("session_id" to sessionId, "turn_index" to turnIndex, "result" to result))
    }

    override fun deepFeedbackOpened(sessionId: String, turnIndex: Int) {
        calls += Call("deep_feedback_opened", mapOf("session_id" to sessionId, "turn_index" to turnIndex))
    }

    override fun sessionComplete(sessionId: String, turnCount: Int, isFirst: Boolean) {
        calls += Call("session_complete", mapOf("session_id" to sessionId, "turn_count" to turnCount, "is_first" to isFirst))
    }

    override fun summaryPartialFailure(sessionId: String, sectionsFailed: Int) {
        calls += Call("summary_partial_failure", mapOf("session_id" to sessionId, "sections_failed" to sectionsFailed))
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*SessionFunnelAnalyticsDispatchTest"`
Expected: PASS (11 tests).

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/analytics android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/analytics
git commit -m "feat(analytics): add SessionFunnelAnalytics seam, Firebase dispatch, recording fake"
```

---

## Task 2: `turn_started` — `GeneratedDialogueState` callback + VM wiring

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueStateLearnerTurnCallbackTest.kt`

**Interfaces:**
- Consumes: `SessionFunnelAnalytics` (Task 1).
- Produces: `GeneratedDialogueState.onEnterLearnerTurn: (() -> Unit)?` (public, settable); the VM sets it to fire `turnStarted`.

- [ ] **Step 1: Write the failing test** — the callback fires exactly once on the `OpponentTurn → LearnerTurn` edge, driven through the real state machine (harness copied from `GeneratedDialogueStateTest.kt`):

```kotlin
package com.jjundev.oneclickeng.feature.session.turn

import com.jjundev.oneclickeng.core.network.DialogueTurn as NetworkDialogueTurn
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenState
import com.jjundev.oneclickeng.feature.session.dialogue.DialogueStreamStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class GeneratedDialogueStateLearnerTurnCallbackTest {
    private fun model(en: String, ko: String = "") = NetworkDialogueTurn(ko = ko, en = en, role = "model")
    private fun user(en: String, ko: String = "학습자") = NetworkDialogueTurn(ko = ko, en = en, role = "user")

    @Test
    fun `entering the learner turn invokes onEnterLearnerTurn exactly once`() {
        var count = 0
        val state = GeneratedDialogueState().apply { onEnterLearnerTurn = { count++ } }

        state.accept(
            DialogueGenState.Ready(
                sessionId = "s1",
                remaining = 2,
                meta = null,
                turns = listOf(model("Hello"), user("A coffee, please.", "커피 주세요."), model("Sure?")),
                streamStatus = DialogueStreamStatus.Streaming,
            ),
        )
        state.commitReveal()
        state.completeOpponentTurn() // OpponentTurn -> LearnerTurn

        assertEquals(TurnPhase.LearnerTurn, state.turnPhase)
        assertEquals(1, count)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*GeneratedDialogueStateLearnerTurnCallbackTest"`
Expected: FAIL — `onEnterLearnerTurn` unresolved.

- [ ] **Step 3: Add the callback to `GeneratedDialogueState`** and invoke it in `enterLearnerTurn`. In `GeneratedDialogueSession.kt`, add the property near the class's other `var` state (e.g. just above `internal val turnState` usage in the state class — put it inside `class GeneratedDialogueState`):

```kotlin
    /** Fired once each time the machine enters a learner turn (turn_started emit hook, M4-01b). */
    var onEnterLearnerTurn: (() -> Unit)? = null
```

In `enterLearnerTurn(current: PendingOpponent)` (currently lines ~1185-1191), add the invocation as the LAST line (after `recomputeTyping()`), so state is fully consistent before the listener reads it:

```kotlin
    private fun enterLearnerTurn(current: PendingOpponent) {
        commitReveal()
        currentTask = current.task
        turnPhase = TurnPhase.LearnerTurn
        sessionPhase = SessionPhase.InTurn
        recomputeTyping()
        onEnterLearnerTurn?.invoke()
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*GeneratedDialogueStateLearnerTurnCallbackTest"`
Expected: PASS.

- [ ] **Step 5: Wire the VM to fire `turn_started`.** Add the seam to `GeneratedDialogueSessionViewModel`'s constructor (after `tts`, before `savedStateHandle`):

```kotlin
        private val sessionFunnel: com.jjundev.oneclickeng.feature.session.analytics.SessionFunnelAnalytics,
```

(Use a top-of-file import instead of the inline FQN.) In the VM `init { ... }` block (find where `turnState` is first set up / collectors are wired — the VM already has an `init`), register the callback:

```kotlin
        turnState.onEnterLearnerTurn = {
            currentSessionId()?.let { sid ->
                sessionFunnel.turnStarted(sid, turnState.messages.count { it is DialogueMessage.Learner })
            }
        }
```

> `turnState.messages.count { it is DialogueMessage.Learner }` at `enterLearnerTurn` time = the 0-based index of the turn just starting (no learner answer appended yet). This is the same integer the completed/deep events use (see the plan's turn_index note).

- [ ] **Step 6: Verify VM wiring compiles** (the emit-site itself is DebugView-verified — no JVM harness for this VM):

Run: `./scripts/verify-android.sh :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Hilt resolves the new `SessionFunnelAnalytics` constructor param from Task 1's binding).

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueStateLearnerTurnCallbackTest.kt
git commit -m "feat(analytics): fire turn_started at learner-turn entry"
```

---

## Task 3: `turn_completed` — thread input_mode + turn_index through `PendingTurn`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt`

**Interfaces:**
- Consumes: `SessionFunnelAnalytics` (injected in Task 2), `SlimFeedbackCoordinator.bufferSnapshot(): TurnFeedbackBuffer` whose `.slimScore: Int?` is `writing_score`.
- Produces: `PendingTurn(koreanPrompt, userText, inputMode, turnIndex)`; `triggerFeedback(userEnglish, inputMode)`.

> **Testing note:** the emit fires inside `recordTurn` on `GeneratedDialogueSessionViewModel`, which has no JVM unit-test harness (its runtime behavior is covered by Compose instrumentation, `GeneratedDialogueSessionContentTest`). The id/param contract is fully pinned by Task 1's dispatch test; this task's correctness (right mode, right score, once per turn) is a mechanical threading change plus a DebugView check at Checkpoint B. Do not build a 9-fake VM harness for it. Keep the change minimal and self-review carefully.

- [ ] **Step 1: Extend `PendingTurn`** (currently `private data class PendingTurn(val koreanPrompt: String, val userText: String)`):

```kotlin
        private data class PendingTurn(
            val koreanPrompt: String,
            val userText: String,
            val inputMode: String,
            val turnIndex: Int,
        )
```

- [ ] **Step 2: Add an `inputMode` param to `triggerFeedback`** and store it + `turnIndex` on `PendingTurn`. Change the signature and body (currently lines ~695-710):

```kotlin
        private fun triggerFeedback(userEnglish: String, inputMode: String) {
            val sid = currentSessionId() ?: return
            val level = currentLevel() ?: return
            val task = turnState.currentTask?.koreanPrompt
            val ref = turnState.currentReferenceEnglish()
            if (task != null && ref != null) {
                turnBuffer.startSession(sid)
                val turnIndex = turnState.messages.count { it is DialogueMessage.Learner } - 1
                pendingTurn = PendingTurn(koreanPrompt = task, userText = userEnglish, inputMode = inputMode, turnIndex = turnIndex)
                deepParams = DeepParams(sid, turnIndex, task, userEnglish, ref, level)
                feedback.start(sid, task, userEnglish, ref, level)
            }
        }
```

- [ ] **Step 3: Pass the mode at the two call sites.** In `onAnalysisState`'s `Result` branch (line ~638) change `triggerFeedback(state.transcript)` → `triggerFeedback(state.transcript, INPUT_MODE_VOICE)`. In `onSubmitText()` (line ~691) change `triggerFeedback(text)` → `triggerFeedback(text, INPUT_MODE_TEXT)`. Add the constants to the `companion object`:

```kotlin
            const val INPUT_MODE_VOICE = "voice"
            const val INPUT_MODE_TEXT = "text"
```

- [ ] **Step 4: Fire `turn_completed` in `recordTurn`.** Change `recordTurn` (currently lines ~744-747) to capture the snapshot once and log:

```kotlin
        private fun recordTurn(pending: PendingTurn) {
            val snapshot = feedback.bufferSnapshot()
            turnBuffer.record(pending.koreanPrompt, pending.userText, snapshot)
            currentSessionId()?.let { sid ->
                sessionFunnel.turnCompleted(sid, pending.turnIndex, pending.inputMode, snapshot.slimScore)
            }
            pendingTurn = null
        }
```

> `recordTurn` is the single choke point for both completion paths (settled via `onFeedbackState`, early via `onAdvance`), and `pendingTurn` is nulled here so it fires exactly once per turn. Empty-transcript turns never reach `recordTurn` (they never set `pendingTurn`), correctly matching §5.2.

- [ ] **Step 5: Verify compile**

Run: `./scripts/verify-android.sh :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (No new unit test — see the testing note; the seam contract is covered by Task 1.)

- [ ] **Step 6: Run the existing session unit suite to confirm no regression** in the state/feedback tests touched by the `triggerFeedback`/`recordTurn` changes:

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*GeneratedDialogueState*" --tests "*SlimFeedback*"`
Expected: PASS (pre-existing tests still green).

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt
git commit -m "feat(analytics): fire turn_completed with input_mode and writing_score"
```

---

## Task 4: `speaking_analyze_result` + `deep_feedback_opened`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt`

**Interfaces:**
- Consumes: `SessionFunnelAnalytics` (injected Task 2), `speakingResultLabel(state)` (Task 1).
- Produces: a `deepLoggedThisTurn` dedup flag.

> **Testing note:** both fire from `GeneratedDialogueSessionViewModel` methods (`onAnalysisState`, `expandDeep`) — no JVM harness (as Task 3). The `speakingResultLabel` mapping IS unit-tested in Task 1; the dedup + emit-site are DebugView-verified. Keep changes minimal.

- [ ] **Step 1: Fire `speaking_analyze_result` in `onAnalysisState`.** Compute the turn index once at method entry (pre-append), then log in each terminal branch via `speakingResultLabel`. Change the top of `onAnalysisState` (line ~621) and the branches:

```kotlin
        private fun onAnalysisState(state: SpeakingAnalysisState) {
            if (micState != MicState.Analyzing) return
            speakingResultLabel(state)?.let { label ->
                currentSessionId()?.let { sid ->
                    sessionFunnel.speakingAnalyzeResult(sid, turnState.messages.count { it is DialogueMessage.Learner }, label)
                }
            }
            when (state) {
                // ... existing branches unchanged ...
```

> `speakingResultLabel` returns non-null only for the three terminal states (`Result`/`Empty`/`Failed`), so `Analyzing`/`Idle` don't log. The index is computed BEFORE the `Result` branch appends the learner answer, matching the plan's turn_index note. Import `speakingResultLabel` from `feature.session.analytics`.

- [ ] **Step 2: Add the deep dedup flag + reset.** Add a field near `deepExpanded` (line ~230):

```kotlin
        // deep_feedback_opened dedup — one log per turn even if the user opens/collapses/re-opens (M4-01b).
        private var deepLoggedThisTurn = false
```

In `onAdvance()` (line ~656-671), reset it alongside `deepExpanded = false` (line ~668):

```kotlin
            deepExpanded = false
            deepLoggedThisTurn = false
```

- [ ] **Step 3: Fire `deep_feedback_opened` in `expandDeep`** (currently lines ~758-763), guarded by the dedup flag, using `deepParams`:

```kotlin
        fun expandDeep() {
            deepParams?.let { p ->
                deep.start(p.sessionId, p.turnIndex, p.koreanPrompt, p.userText, p.referenceEnglish, p.level)
                if (!deepLoggedThisTurn) {
                    deepLoggedThisTurn = true
                    sessionFunnel.deepFeedbackOpened(p.sessionId, p.turnIndex)
                }
            }
            deepExpanded = true
        }
```

> Firing here (user intent), NOT in `DeepFeedbackCoordinator.start()` — deep content is eagerly prefetched every turn from `onFeedbackState`, so logging in the coordinator would over-count §6.4's numerator. `collapseDeep()` stays unchanged (it only lowers `deepExpanded`), and the flag prevents a re-expand double-count within the same turn.

- [ ] **Step 4: Verify compile + no regression**

Run: `./scripts/verify-android.sh :app:compileDebugKotlin` then `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*GeneratedDialogueState*" --tests "*DeepFeedback*"`
Expected: BUILD SUCCESSFUL; pre-existing tests PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt
git commit -m "feat(analytics): fire speaking_analyze_result and deep_feedback_opened"
```

---

## Task 5: `session_complete` — `SummaryViewModel.start()`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryViewModel.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryViewModelSessionCompleteTest.kt`

**Interfaces:**
- Consumes: `SessionFunnelAnalytics` (Task 1), `SessionTurnBufferStore.bufferedTurns(): List<BufferedTurn>` (`.size` = `turn_count`).
- Produces: `SummaryViewModel(coordinator, sessionFunnel, turnBuffer)`.

- [ ] **Step 1: Write the failing test** (`SummaryViewModelSessionCompleteTest.kt`). `SummaryViewModel` is a thin VM — construct it with the real `SessionTurnBufferStore` (`@Inject constructor()`, trivially constructible), a `RecordingSessionFunnelAnalytics`, and the real `SummaryCoordinator`… but the coordinator has heavy deps. Instead, assert only the analytics behavior, and construct the coordinator via the same fakes the existing `SummaryCoordinatorTest.kt` uses — **read `SummaryCoordinatorTest.kt` first for its coordinator-construction helper and reuse it verbatim.** The test body:

```kotlin
package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.feature.session.analytics.RecordingSessionFunnelAnalytics
import com.jjundev.oneclickeng.feature.session.feedback.TurnFeedbackBuffer
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryViewModelSessionCompleteTest {
    @Test
    fun `start logs session_complete once with turn_count and is_first`() {
        val analytics = RecordingSessionFunnelAnalytics()
        val turnBuffer = SessionTurnBufferStore()
        turnBuffer.startSession("s1")
        turnBuffer.record("q1", "a1", TurnFeedbackBuffer(slimScore = 80, correctedText = null, naturalExpression = null))
        turnBuffer.record("q2", "a2", TurnFeedbackBuffer(slimScore = 90, correctedText = null, naturalExpression = null))
        val vm = SummaryViewModel(coordinator = newSummaryCoordinatorForTest(), sessionFunnel = analytics, turnBuffer = turnBuffer)

        vm.start(sessionId = "s1", difficulty = "easy", modeId = "m", accrual = AccrualStrip(streakDays = 0, xp = 0), isFirstSession = true)
        vm.start(sessionId = "s1", difficulty = "easy", modeId = "m", accrual = AccrualStrip(streakDays = 0, xp = 0), isFirstSession = true) // idempotent

        assertEquals(
            listOf(RecordingSessionFunnelAnalytics.Call("session_complete", mapOf("session_id" to "s1", "turn_count" to 2, "is_first" to true))),
            analytics.calls,
        )
    }
}
```

> `newSummaryCoordinatorForTest()`: `SummaryCoordinatorTest.kt`'s construction helper is a **private member (`coordinator(...)`, ~line 223) — unreachable from this file.** Build a local `private fun newSummaryCoordinatorForTest(): SummaryCoordinator` in `SummaryViewModelSessionCompleteTest.kt` that mirrors that helper's construction (read it and copy the fake `SummaryStream`/deps setup). `AccrualStrip` has no `EMPTY` constant — construct it as `AccrualStrip(streakDays = 0, xp = 0)` (as `SummaryScrollFabTest.kt:137` does). Confirm `TurnFeedbackBuffer`'s exact field names against `feature/session/feedback/SlimFeedbackState.kt:133` before finalizing.

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*SummaryViewModelSessionCompleteTest"`
Expected: FAIL — `SummaryViewModel` constructor doesn't take `sessionFunnel`/`turnBuffer`.

- [ ] **Step 3: Wire the VM.** Change `SummaryViewModel`'s constructor and `start()`:

```kotlin
@HiltViewModel
class SummaryViewModel
    @Inject
    constructor(
        private val coordinator: SummaryCoordinator,
        private val sessionFunnel: com.jjundev.oneclickeng.feature.session.analytics.SessionFunnelAnalytics,
        private val turnBuffer: SessionTurnBufferStore,
    ) : ViewModel() {
        val state: StateFlow<SummaryState> = coordinator.state

        private var started = false

        fun start(
            sessionId: String,
            difficulty: String,
            modeId: String,
            accrual: AccrualStrip,
            isFirstSession: Boolean = false,
        ) {
            if (started) return
            started = true
            sessionFunnel.sessionComplete(sessionId, turnBuffer.bufferedTurns().size, isFirstSession)
            coordinator.start(
                sessionId = sessionId,
                difficulty = difficulty,
                modeId = modeId,
                accrual = accrual,
                isFirstSession = isFirstSession,
            )
        }
        // ... rest unchanged ...
```

(Use a top-of-file import for `SessionFunnelAnalytics`.) Firing before `coordinator.start()` keeps `session_complete` decoupled from summary-LLM success (§5.1) and reads `turn_count` from the buffer before any reset.

- [ ] **Step 4: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*SummaryViewModelSessionCompleteTest"`
Expected: PASS (fires once — the second `start()` is a no-op via the `started` guard).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryViewModel.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryViewModelSessionCompleteTest.kt
git commit -m "feat(analytics): fire session_complete at summary entry"
```

---

## Task 6: `summary_partial_failure` — `SummaryCoordinator`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinator.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryPartialFailureAnalyticsTest.kt`

**Interfaces:**
- Consumes: `SessionFunnelAnalytics` (Task 1), the coordinator's private `sectionState(section)`, `SummarySection.entries`, `SummarySectionState.Failed`.
- Produces: `SummaryCoordinator(..., sessionFunnel)`; `partialFailureLogged` flag + `maybeLogPartialFailure()`.

- [ ] **Step 1: Write the failing test** (`SummaryPartialFailureAnalyticsTest.kt`) — mirror `SummaryCoordinatorTest.kt`'s construction into a LOCAL helper in this file (read it first; its own helper is private/unreachable — see the note below). Drive a stream that fails ≥1 section (there is almost certainly an existing test in `SummaryCoordinatorTest.kt` that produces a partial failure — copy that scenario), then assert exactly one `summary_partial_failure` call with the failed-section count:

```kotlin
package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.feature.session.analytics.RecordingSessionFunnelAnalytics
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryPartialFailureAnalyticsTest {
    @Test
    fun `partial failure logs summary_partial_failure once with the failed-section count`() {
        val analytics = RecordingSessionFunnelAnalytics()
        val coordinator = newSummaryCoordinatorForTest(sessionFunnel = analytics) // LOCAL helper — see note below
        coordinator.start(sessionId = "s1", difficulty = "easy", modeId = "m", accrual = AccrualStrip(streakDays = 0, xp = 0))

        // Drive the stream so exactly one section fails and ≥1 succeeds (copy the partial-failure scenario
        // from SummaryCoordinatorTest.kt — e.g. feed a Done event with one section outcome = Failed).
        driveOneSectionFailure(coordinator)

        val failures = analytics.calls.filter { it.name == "summary_partial_failure" }
        assertEquals(1, failures.size)
        assertEquals("s1", failures.single().args["session_id"])
        assertEquals(1, failures.single().args["sections_failed"])
    }
}
```

> `SummaryCoordinatorTest.kt`'s helpers are **private members of its own class — unreachable from this file.** Read it, then build a LOCAL `private fun newSummaryCoordinatorForTest(sessionFunnel: SessionFunnelAnalytics = NoOpSessionFunnelAnalytics()): SummaryCoordinator` in `SummaryPartialFailureAnalyticsTest.kt` that mirrors that file's coordinator construction and fake `SummaryStream` setup, threading `sessionFunnel` into the constructor (Task 6 Step 3 adds that param). Reuse its stream-driving approach to produce a partial failure; if no partial-failure scenario exists there, build one by emitting a `SummaryEvent.Done` with one `SectionOutcome.Failed` and the others `Ok`. `driveOneSectionFailure(coordinator)` in the snippet stands for that stream driving — inline it using the fake `SummaryStream` your local helper wires up.

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*SummaryPartialFailureAnalyticsTest"`
Expected: FAIL — coordinator has no `sessionFunnel` param.

- [ ] **Step 3: Inject the seam + add the dedup helper.** Add `private val sessionFunnel: SessionFunnelAnalytics` to the `SummaryCoordinator` constructor (after `scope`). **This is a positional constructor — every manual construction site must append the new arg**, or they stop compiling: update `SummaryCoordinatorTest.kt`'s `coordinator(...)` factory (pass `sessionFunnel`, default it to `NoOpSessionFunnelAnalytics()` so existing cases are unaffected) AND the `newSummaryCoordinatorForTest()` helper you added in Task 5's test file (the compiler lists both). Production construction is Hilt `@Inject`, so no production call site changes. Add a flag field near the other session state and reset it in `start()`:

```kotlin
        private var partialFailureLogged = false
```

In `start()` (line ~144, alongside `sectioned = false`), add:

```kotlin
            partialFailureLogged = false
```

Add the helper (private):

```kotlin
        // summary_partial_failure — fire once per session when the summary settles with ≥1 failed section
        // (partial OR total). Count-only per PII §7. Deduped so failed retries don't re-log (M4-01b).
        private fun maybeLogPartialFailure() {
            if (partialFailureLogged) return
            val failed = SummarySection.entries.count { sectionState(it) is SummarySectionState.Failed }
            if (failed == 0) return
            partialFailureLogged = true
            sessionId?.let { sessionFunnel.summaryPartialFailure(it, failed) }
        }
```

- [ ] **Step 4: Call the helper from the three settle sites.** Add `maybeLogPartialFailure()` immediately after `sectioned = true` in each of: `applyDone` (line ~451), `failLoadingSections` (line ~482), and `onQuotaExceeded`'s `anyArrived` branch (line ~424). Example for `applyDone`:

```kotlin
            sectioned = true
            maybeLogPartialFailure()
            emit()
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*SummaryPartialFailureAnalyticsTest"`
Expected: PASS (one call, count = 1).

- [ ] **Step 6: Confirm no regression in the coordinator suite**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*SummaryCoordinatorTest"`
Expected: PASS (the added `sessionFunnel` param with a `NoOp` default in the test helper leaves existing cases green).

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinator.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryPartialFailureAnalyticsTest.kt
git commit -m "feat(analytics): fire summary_partial_failure once per session"
```

---

## Task 7: generation + session-started events (`DialogueGenerationViewModel` + Route)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModel.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreen.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationFunnelAnalyticsTest.kt`

**Interfaces:**
- Consumes: `SessionFunnelAnalytics` (Task 1); `coordinator.sessionId(): String?`; the VM's `lastStart: StartParams(level, topic, length, firstSession)` and `isOnboarding`.
- Produces: `DialogueGenerationViewModel.onConversationStarted()`.

- [ ] **Step 1: Write the failing test** (`DialogueGenerationFunnelAnalyticsTest.kt`) — mirror `DialogueGenerationViewModelTest.kt`'s construction into a LOCAL helper in this file (read it first; its own helper is private/unreachable — see the note below. It builds this VM with fakes incl. `FakeConnectivity` and the existing analytics seams). Add a `RecordingSessionFunnelAnalytics`. Assert:
  1. `start(..., isOnboarding = true)` on a started (online) generation logs `first_session_generation_started {idempotency_key_present = true}`.
  2. after Ready, `onConversationStarted()` logs `first_session_started` (onboarding) or `learning_session_started` (home) with the right params, once.

```kotlin
package com.jjundev.oneclickeng.feature.session.dialogue

import com.jjundev.oneclickeng.feature.session.analytics.RecordingSessionFunnelAnalytics
import org.junit.Assert.assertEquals
import org.junit.Test
// ... same test infra imports as DialogueGenerationViewModelTest.kt ...

class DialogueGenerationFunnelAnalyticsTest {
    @Test
    fun `onboarding generation logs first_session_generation_started when started`() {
        val funnel = RecordingSessionFunnelAnalytics()
        val vm = newGenerationViewModel(sessionFunnel = funnel) // LOCAL helper mirroring DialogueGenerationViewModelTest — see note
        vm.start(level = "easy", topic = "cafe", length = 5, firstSession = true, isOnboarding = true)

        assertEquals(
            RecordingSessionFunnelAnalytics.Call("first_session_generation_started", mapOf("idempotency_key_present" to true)),
            funnel.calls.first(),
        )
    }

    @Test
    fun `onConversationStarted logs first_session_started once for onboarding`() {
        val funnel = RecordingSessionFunnelAnalytics()
        val vm = newGenerationViewModel(sessionFunnel = funnel, sessionId = "s1") // helper stubs coordinator.sessionId()
        vm.start(level = "easy", topic = "cafe", length = 5, firstSession = true, isOnboarding = true)

        vm.onConversationStarted()
        vm.onConversationStarted() // idempotent

        val started = funnel.calls.filter { it.name == "first_session_started" }
        assertEquals(1, started.size)
        assertEquals(
            mapOf("session_id" to "s1", "topic_id" to "cafe", "length" to 5, "difficulty" to "easy"),
            started.single().args,
        )
    }
}
```

> Read `DialogueGenerationViewModelTest.kt` first for its VM-construction helper (`private fun TestScope.viewModel(...)`, ~lines 402-426, incl. `FakeConnectivity` and the fake coordinator). That helper is a **private member of its own test class — you cannot call it from this new file.** Mirror it: copy its construction into a local `private fun viewModel(...)` (or a top-level helper) in `DialogueGenerationFunnelAnalyticsTest.kt`, adding a `sessionFunnel: SessionFunnelAnalytics = NoOpSessionFunnelAnalytics()` parameter and passing it to the (now 10-arg) `DialogueGenerationViewModel(...)` constructor. To make `coordinator.sessionId()` return a value, drive the fake coordinator to `Ready` exactly the way that file's existing started/offline tests do (they already produce a `DialogueGenState.Ready`).

- [ ] **Step 2: Run test to verify it fails**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*DialogueGenerationFunnelAnalyticsTest"`
Expected: FAIL — VM has no `sessionFunnel` param / `onConversationStarted`.

- [ ] **Step 3: Wire the VM.** Add `private val sessionFunnel: SessionFunnelAnalytics` to `DialogueGenerationViewModel`'s constructor (after `offlineAnalytics`, before `loadingQuizConfig`; top-of-file import). Fire `first_session_generation_started` in `start()` by capturing the outcome:

```kotlin
        fun start(level: String, topic: String, length: Int, firstSession: Boolean, isOnboarding: Boolean = false) {
            this.isOnboarding = isOnboarding
            lastStart = StartParams(level, topic, length, firstSession)
            val outcome = coordinator.start(level, topic, length, firstSession)
            if (isOnboarding) sessionFunnel.firstSessionGenerationStarted(outcome == StartOutcome.Started)
            when (outcome) {
                StartOutcome.OfflineGated -> {
                    preflightBlocked = true
                    _quizItems.value = emptyList()
                    offlineAnalytics.offlineBlocked(OFFLINE_GATE_SURFACE)
                }
                StartOutcome.Started -> {
                    preflightBlocked = false
                    val tier = if (firstSession) FIRST_SESSION_TIER else level
                    _quizItems.value = if (quizEnabled) quizBank.forTier(tier) else emptyList()
                    answeredCount = 0
                    appScope.launch { snapshotStore.clear() }
                }
            }
        }
```

Add `onConversationStarted()` with a once-guard:

```kotlin
        private var conversationStartedLogged = false

        /** Fired by the generating Route when the user commits to the conversation (auto or CTA). Emits
         *  first_session_started (onboarding) or learning_session_started (revisit). Once per session. */
        fun onConversationStarted() {
            if (conversationStartedLogged) return
            val params = lastStart ?: return
            val sid = coordinator.sessionId() ?: return
            conversationStartedLogged = true
            if (isOnboarding) {
                sessionFunnel.firstSessionStarted(sid, params.topic, params.length, params.level)
            } else {
                sessionFunnel.learningSessionStarted(sid, params.topic, params.length, params.level)
            }
        }
```

> `difficulty` (first_session_started) and `level` (learning_session_started) are the same underlying `params.level` — the seam method names differ per §4. `StartParams` field names are `level`/`topic`/`length`/`firstSession`; confirm against the private `data class StartParams` in this file and adjust if they differ.

- [ ] **Step 4: Fire it from the Route.** In `DialogueGeneratingRoute` (`DialogueGeneratingScreen.kt` line ~263), wrap the forwarded callback so the VM logs before the caller navigates:

```kotlin
        onStartConversation = {
            viewModel.onConversationStarted()
            onStartConversation()
        },
```

> Wrapping in the Route (which owns the `viewModel`) covers BOTH invocation paths inside the screen (auto-start and the CTA tap) with one change; the VM's `conversationStartedLogged` guard makes the double path fire once. The nav-graph lambdas (`OnboardingGraph.kt`, `HomeSessionGraph.kt`) need NO change — they still pass a plain `onStartConversation`.

- [ ] **Step 5: Run test to verify it passes**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest --tests "*DialogueGenerationFunnelAnalyticsTest"`
Expected: PASS.

- [ ] **Step 6: Full verification (debug suite + Hilt graph)**

Run: `./scripts/verify-android.sh :app:testDebugUnitTest` then `./scripts/verify-android.sh :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL — the whole debug unit suite green, Hilt graph resolves every `SessionFunnelAnalytics` injection (session VM, generation VM, summary VM, summary coordinator) against Task 1's single binding. (Ignore the known pre-existing `check`/release-variant failures — see Global Constraints.)

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModel.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreen.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationFunnelAnalyticsTest.kt
git commit -m "feat(analytics): fire first_session_generation_started and session-started events"
```

---

## Manual Checkpoint (final — human, GA4 DebugView)

Several emit-sites live in `GeneratedDialogueSessionViewModel`, which has no JVM unit-test harness (its behavior is covered by Compose instrumentation). Their id/params are unit-tested via the seam (Task 1); their firing is confirmed here.

- [ ] Enable DebugView (`adb shell setprop debug.firebase.analytics.app com.jjundev.oneclickeng`, debug build).
- [ ] Onboarding first session: confirm `first_session_generation_started {idempotency_key_present=true}` → `first_session_started {topic_id,length,difficulty}` → per turn `turn_started` → (speak) `speaking_analyze_result` → `turn_completed {input_mode,writing_score}` → (tap 더보기) `deep_feedback_opened` (only once even if you collapse/re-open) → at end `session_complete {turn_count,is_first=true}`.
- [ ] Revisit from home: confirm `learning_session_started {…,level}` and `session_complete {is_first=false}`.
- [ ] Force an empty utterance and a network failure mid-analysis: confirm `speaking_analyze_result {result=empty_transcript}` and `{result=analyze_failed}`, and that no `turn_completed` fires for the empty turn.
- [ ] Force a summary section failure: confirm exactly one `summary_partial_failure {sections_failed=N}` per session even across a retry.
- [ ] Back-fill `analytics-events.md`: add the `analyze_failed` value to the `speaking_analyze_result` row (§4) and record the `session_complete` fire-site rename (SummaryPreparing → SummaryViewModel.start).
