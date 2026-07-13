# Feedback Save-Icon Color + Summary Saved-Sentences & Missing-Sections Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix three defects in the session feedback→summary flow: (1) the feedback-sheet paraphrase save icon should be prototype gold, (2) sentences saved during a session must reliably appear in the summary's bookmark section, and (3) the summary's remote sections (natural expressions / new words / coaching) must populate instead of showing only the local highlight.

**Architecture:** Three independent fixes in one feature area. **(1)** is a one-line tint swap to an existing color token, guarded by the existing Roborazzi screenshot baseline. **(2)** is the root cause that the bookmark read is server-first with `orderBy(createdAt)` while `createdAt` is a *pending server timestamp* for just-saved cards — realigning the read to the app's offline-first design (cache-first + estimated timestamps + client-side ordering via a pure, unit-tested helper) makes current-session saves appear. **(3)** is the root cause that the client's summary request payload only carries `totalScore` + `turns`, but the backend's `expressions`/`words` sub-calls read `payload.expressionCandidates`/`words`/`sentences`/`userOriginalSentences` (types/summary.ts) — the client never sends them, so those sections get empty input → empty output. The fix reconciles the client wire contract with the backend and projects the session turn buffer into the full payload. No backend change is required.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization, Firebase Firestore (offline-first), Hilt DI, JUnit4 + kotlinx-coroutines-test, Roborazzi (Robolectric screenshot). Backend is TypeScript Cloud Functions (unchanged by this plan).

## Global Constraints

- **Verification runs through `scripts/verify-android.sh`** (worktree-isolated `GRADLE_USER_HOME`, copies `google-services.json`). Never call `./gradlew` directly from the worktree. Pass explicit tasks after the script name, e.g. `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SummaryPayloadProjectorTest*'`. With no args it runs the default set (`:app:detekt :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:testReleaseUnitTest`).
- **detekt is part of the default gate.** Match surrounding style: KDoc on new public types, `private companion object` for constants, no magic numbers where the file already extracts them, Korean 해요체 in user-facing/doc comments consistent with the file.
- **Roborazzi record switch is `-Proborazzi.record`** (writes PNGs via `captureRoboImage`); without it the screenshot tests verify.
- **Color tokens come from `OceTheme.colors` / `OceColors`** — never raw hex in composables. The prototype "saved/bookmark" color is the existing token `gameSaveGold` (`#FFC107` light / `#FFD24D` dark).
- **The shared `Json` has `encodeDefaults=false`** — payload fields left at their default (empty list / null) are omitted from the wire; the backend tolerates absent arrays (defaults them to `[]`) and absent `sections` (runs all three).
- **Backend section vocabulary is PLURAL** (`expressions`/`words`/`coaching`, `SUMMARY_SECTIONS` in `functions/src/types/summary.ts`) for both the `done.sections` verdict map and the retry `sections` filter. The per-card `kind` is SINGULAR (`expression`/`word`/`coaching`). Do not conflate them.

---

## File Structure

**Request 1 — gold save icon**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackSections.kt:409-410` — bookmark tint → `gameSaveGold`.
- Baseline: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionFlowScreenshotTest.kt` (renders `bookmarkedLevels = setOf(2)`; regenerate `flow_deep_light.png`).

**Request 3 — summary sections populate (client-only)**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/network/SummaryContracts.kt` — reshape `SummaryPayload` + `SummaryTurnDto` to the backend contract; add `SummaryExpressionCandidateDto`; rename `retrySections`→`sections`.
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SessionTurnBufferStore.kt` — store raw per-turn fields in a new internal `BufferedTurn`; expose `bufferedTurns()`.
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryPayloadProjector.kt` — pure projection: buffered turns → full `SummaryPayload`.
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinator.kt` — build the projected payload at `start()`; send plural `sections` on retry; make `SummarySection` keys plural.
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/network/SummaryPayloadWireTest.kt` (new), `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SessionTurnBufferStoreTest.kt` (new), `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryPayloadProjectorTest.kt` (new), `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinatorTest.kt` (edit), `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/network/SummarySseStreamTest.kt` (unchanged — verify it still compiles).

**Request 2 — reliable bookmark loading**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/saved/BookmarkOrdering.kt` — pure "latest N, pending writes first" ordering.
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/saved/FirestoreBookmarkSource.kt` — cache-first read, equality-only query, estimated timestamps, order via `BookmarkOrdering`.
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/saved/BookmarkOrderingTest.kt` (new).

**No backend changes.** `functions/src/llm/summary.ts` + `functions/src/types/summary.ts` already consume `expressionCandidates`/`words`/`sentences`/`userOriginalSentences`/`turns`/`sections`.

---

## Task 1: Feedback-sheet paraphrase save icon → prototype gold

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackSections.kt:409-410`
- Baseline: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionFlowScreenshotTest.kt`

**Interfaces:**
- Consumes: existing token `OceTheme.colors.gameSaveGold` (already used in `SummaryScreen.kt`; `OceTheme` is already imported in this file).
- Produces: nothing consumed by later tasks.

**Context:** `ParaphraseCard` currently tints the bookmark icon with `MaterialTheme.colorScheme.primary` (blue) when bookmarked — a mismatch with the prototype and with `SummaryScreen.kt`, which uses `gameSaveGold`. Only the *bookmarked* (filled) state goes gold; the unbookmarked (`BookmarkBorder`) state stays neutral `textTertiary`, matching `SummaryScreen.kt`'s `if (saved) gameSaveGold else …` convention.

- [ ] **Step 1: Change the bookmarked tint to the gold token**

In `DeepFeedbackSections.kt`, replace lines 409-410:

```kotlin
        val bookmarkTint =
            if (bookmarked) MaterialTheme.colorScheme.primary else OceTheme.colors.textTertiary
```

with:

```kotlin
        // 저장(북마크)=프로토타입 정합 골드(gameSaveGold, SummaryScreen 저장 표식과 동일), 미저장=중립 textTertiary.
        val bookmarkTint =
            if (bookmarked) OceTheme.colors.gameSaveGold else OceTheme.colors.textTertiary
```

- [ ] **Step 2: Compile to confirm the token resolves**

Run: `scripts/verify-android.sh :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (no unresolved reference — `gameSaveGold` and `OceTheme` are already in scope in this file).

- [ ] **Step 3: Regenerate the feedback-sheet screenshot baseline and confirm gold**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionFlowScreenshotTest*' -Proborazzi.record`
Expected: PASS. Open `android/app/build/outputs/roborazzi/flow_deep_light.png` and visually confirm the paraphrase card's bookmark icon (level 2 is bookmarked in the fixture) is now gold, not blue.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackSections.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionFlowScreenshotTest.kt
git commit -m "fix(ui): tint feedback-sheet save icon prototype gold (gameSaveGold)"
```

---

## Task 2: Reconcile the summary wire contract with the backend

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/core/network/SummaryContracts.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/network/SummaryPayloadWireTest.kt` (create)

**Interfaces:**
- Consumes: the shared `Json` (encodeDefaults=false), backend contract `functions/src/types/summary.ts` (`SummaryPayload`, `ExpressionCandidate`, `SummaryTurn`).
- Produces (relied on by Tasks 3-5):
  - `data class SummaryExpressionCandidateDto(type: String, koreanPrompt: String, before: String, after: String, explanation: String? = null)`
  - `data class SummaryTurnDto(koreanPrompt: String, before: String, after: String? = null, score: Int? = null)`
  - `data class SummaryPayload(totalScore: Int, turns: List<SummaryTurnDto> = emptyList(), expressionCandidates: List<SummaryExpressionCandidateDto> = emptyList(), words: List<String> = emptyList(), sentences: List<String> = emptyList(), userOriginalSentences: List<String> = emptyList(), sections: List<String>? = null)`

**Context:** The backend `parseSummaryPayload` + `sliceFor` (functions/src/llm/summary.ts) read these top-level fields; the current client `SummaryPayload` sends none of the expression/word inputs, so those sections receive `[]` and return `[]`. The retry field is also mis-named (`retrySections` vs backend `sections`) — realign it now.

- [ ] **Step 1: Write the failing wire-shape test**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/core/network/SummaryPayloadWireTest.kt`:

```kotlin
package com.jjundev.oneclickeng.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * summary 요청 페이로드의 wire 형태가 백엔드 계약(functions/src/types/summary.ts)과 일치하는지 고정한다.
 * 회귀 방지: 예전 페이로드는 expressionCandidates/words/sentences/userOriginalSentences 를 보내지 않아
 * 표현/단어 섹션이 항상 비어 있었다(하이라이트만 표시).
 */
class SummaryPayloadWireTest {
    // 프로덕션과 동일 정책: 기본값은 wire 에서 생략된다(백엔드가 부재 배열을 [] 로 관대 처리).
    private val json = Json { encodeDefaults = false }

    @Test
    fun `full payload serializes every backend-consumed field`() {
        val payload =
            SummaryPayload(
                totalScore = 82,
                turns = listOf(SummaryTurnDto("커피 주세요", before = "One coffee", after = "Could I get a coffee?", score = 80)),
                expressionCandidates =
                    listOf(SummaryExpressionCandidateDto("natural", "커피 주세요", "One coffee", "Could I get a coffee?")),
                words = emptyList(),
                sentences = listOf("Could I get a coffee?"),
                userOriginalSentences = listOf("One coffee"),
                sections = listOf("expressions"),
            )
        val wire = json.encodeToString(SummaryPayload.serializer(), payload)

        assertTrue(wire.contains("\"totalScore\":82"))
        assertTrue(wire.contains("\"expressionCandidates\""))
        assertTrue(wire.contains("\"sentences\""))
        assertTrue(wire.contains("\"userOriginalSentences\""))
        // 턴은 백엔드 SummaryTurn 형태(before/after/score) — 옛 userText/slimScore 아님.
        assertTrue(wire.contains("\"before\":\"One coffee\""))
        assertTrue(wire.contains("\"after\":\"Could I get a coffee?\""))
        assertTrue(wire.contains("\"score\":80"))
        assertFalse(wire.contains("userText"))
        assertFalse(wire.contains("slimScore"))
        // 재시도 필터는 backend 필드명 `sections` (옛 retrySections 아님).
        assertTrue(wire.contains("\"sections\":[\"expressions\"]"))
        assertFalse(wire.contains("retrySections"))
    }

    @Test
    fun `initial payload omits sections so the backend runs all three`() {
        val payload = SummaryPayload(totalScore = 0)
        val wire = json.encodeToString(SummaryPayload.serializer(), payload)
        assertFalse("absent sections = run all", wire.contains("sections"))
        assertEquals("{\"totalScore\":0}", wire)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SummaryPayloadWireTest*'`
Expected: FAIL to compile — `SummaryExpressionCandidateDto` unresolved, `SummaryTurnDto` has no `before`/`after`/`score`, `SummaryPayload` has no `sections`/`expressionCandidates`.

- [ ] **Step 3: Reshape the contract types**

In `SummaryContracts.kt`, replace the `SummaryPayload` and `SummaryTurnDto` declarations (lines 25-56) with:

```kotlin
/**
 * summary payload — the client PROJECTS its whole-session turn buffer into these already-shaped fields
 * before sending (buffer→sub-call input projection is the client's job, prompt-system.md:71). Field
 * names/shape mirror the backend contract `functions/src/types/summary.ts` exactly: the expressions
 * sub-call reads [expressionCandidates], the words sub-call reads [words]/[sentences]/[userOriginalSentences],
 * coaching reads [turns]. Omitting an empty array is fine — the backend defaults absent arrays to [].
 *
 * [sections] — retry filter (backend-functions.md §10): run ONLY these sections and report only them in
 * `done.sections`. `null`(초기 호출) 은 wire 에서 생략돼 "세 섹션 전부"를 뜻한다. 값이 있으면 백엔드 PLURAL
 * 키(`expressions`/`words`/`coaching`)의 비어있지 않은 부분집합이어야 한다(빈/미지 키는 400).
 */
@Serializable
data class SummaryPayload(
    val totalScore: Int,
    val turns: List<SummaryTurnDto> = emptyList(),
    val expressionCandidates: List<SummaryExpressionCandidateDto> = emptyList(),
    val words: List<String> = emptyList(),
    val sentences: List<String> = emptyList(),
    val userOriginalSentences: List<String> = emptyList(),
    val sections: List<String>? = null,
)

/**
 * One before/after candidate feeding the expressions filter (backend `ExpressionCandidate`). [type] 은
 * 클라의 힌트(`natural`|`accurate`)이며 백엔드 프롬프트가 최종 재분류한다. [explanation] 은 선택(백엔드가 채움).
 */
@Serializable
data class SummaryExpressionCandidateDto(
    val type: String,
    val koreanPrompt: String,
    val before: String,
    val after: String,
    val explanation: String? = null,
)

/**
 * One turn feeding coaching (backend `SummaryTurn`): [before]=사용자 원문, [after]=교정/자연스러운 개선문,
 * [score]=slim writingScore. 스킵/실패 턴은 after/score 가 null 로 들어가 백엔드가 낮은 신뢰도로 처리한다.
 */
@Serializable
data class SummaryTurnDto(
    val koreanPrompt: String,
    val before: String,
    val after: String? = null,
    val score: Int? = null,
)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SummaryPayloadWireTest*'`
Expected: `SummaryPayloadWireTest` may still fail to *compile the module* because `SessionTurnBufferStore.kt` and `SummaryCoordinator.kt` reference the old `SummaryTurnDto` shape. That is expected — those are fixed in Tasks 3-5. To validate this task in isolation, compile only this file's peers is not possible; instead confirm the type shape by proceeding — the wire test will pass once Tasks 3-5 land. **Do not commit Task 2 alone**; commit Tasks 2-5 together at the end of Task 5 (they form one compilable unit). Mark this step done once the type declarations match the spec above.

> Note: Tasks 2-5 are one compilation unit (the contract change ripples through the buffer, projector, and coordinator). Implement Steps in order across Tasks 2→3→4→5, then compile + test + commit once at Task 5.

---

## Task 3: Store raw per-turn fields in the buffer (`BufferedTurn`)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SessionTurnBufferStore.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SessionTurnBufferStoreTest.kt` (create)

**Interfaces:**
- Consumes: `TurnFeedbackBuffer(slimScore: Int?, correctedText: String?, naturalExpression: String?)` (from `SlimFeedbackState.kt`).
- Produces (relied on by Task 4):
  - `data class BufferedTurn(koreanPrompt: String, userText: String, correctedText: String?, naturalExpression: String?, slimScore: Int?)`
  - `fun bufferedTurns(): List<BufferedTurn>` (defensive copy)
  - `record(...)`, `totalScore()`, `highlightBase()`, `sessionStartMillis()`, `startSession()`, `clear()` unchanged in signature.

**Context:** The store currently stores the wire `SummaryTurnDto`. After Task 2, that DTO no longer holds `userText`/`correctedText`/`naturalExpression` separately — but the expression-candidate projection needs them split (natural vs accurate). So the store must keep the *raw* fields in its own type.

- [ ] **Step 1: Write the failing buffer test**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SessionTurnBufferStoreTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.feature.session.feedback.TurnFeedbackBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionTurnBufferStoreTest {
    private fun seeded() =
        SessionTurnBufferStore().apply {
            startSession("s1")
            record("커피 주세요", "One coffee", TurnFeedbackBuffer(slimScore = 80, correctedText = "Could I get a coffee?", naturalExpression = "Can I grab a coffee?"))
            record("길 알려줘", "Where station", TurnFeedbackBuffer(slimScore = 90, correctedText = null, naturalExpression = null))
        }

    @Test
    fun `bufferedTurns preserves raw per-turn fields`() {
        val turns = seeded().bufferedTurns()
        assertEquals(2, turns.size)
        assertEquals("One coffee", turns[0].userText)
        assertEquals("Could I get a coffee?", turns[0].correctedText)
        assertEquals("Can I grab a coffee?", turns[0].naturalExpression)
        assertEquals(80, turns[0].slimScore)
        assertNull(turns[1].correctedText)
        assertNull(turns[1].naturalExpression)
    }

    @Test
    fun `totalScore averages slim scores and highlight picks the top turn`() {
        val store = seeded()
        assertEquals(85, store.totalScore())
        assertEquals("Where station", store.highlightBase()?.userText)
    }

    @Test
    fun `new session clears the previous buffer`() {
        val store = seeded()
        store.startSession("s2")
        assertEquals(0, store.bufferedTurns().size)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionTurnBufferStoreTest*'`
Expected: FAIL to compile — `bufferedTurns()` and `BufferedTurn` do not exist.

- [ ] **Step 3: Rewrite the store to keep raw fields**

Replace the body of `SessionTurnBufferStore.kt` (keep the file KDoc header at the top; replace from the class declaration onward). Full class:

```kotlin
package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.feature.session.feedback.TurnFeedbackBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** 한 턴의 원시 버퍼(슬림 스냅샷 + 과제/답변 echo). 요약 페이로드 투영([SummaryPayloadProjector])의 입력. */
data class BufferedTurn(
    val koreanPrompt: String,
    val userText: String,
    val correctedText: String?,
    val naturalExpression: String?,
    val slimScore: Int?,
)

/**
 * 세션 turn buffer 누적 저장소(M2-02). 각 턴 종료마다 슬림 피드백 스냅샷 + 과제/답변 echo 를 [record] 로
 * 밀어넣으면, 요약이 이 저장소를 단일 소스로 읽어 (a) 요약 페이로드 투영([bufferedTurns]),
 * (b) 종합 점수([totalScore]), (c) 하이라이트 base([highlightBase]) 를 산출한다.
 *
 * @Singleton — 세션은 프로세스 전역 1개. 기록(턴 흐름)과 읽기(요약 진입)가 다른 진입점에서 오므로 접근을
 * @Synchronized 로 직렬화한다.
 */
@Singleton
class SessionTurnBufferStore
    @Inject
    constructor() {
        private val lock = Any()
        private var currentSessionId: String? = null
        private val turns = mutableListOf<BufferedTurn>()
        private var startAtMillis: Long? = null

        /** 새 sessionId 면 이전 버퍼를 비우고 시작 벽시계를 캡처한다(같은 세션 재진입이면 유지). 멱등. */
        fun startSession(sessionId: String) {
            synchronized(lock) {
                if (sessionId != currentSessionId) {
                    turns.clear()
                    currentSessionId = sessionId
                    startAtMillis = System.currentTimeMillis()
                }
            }
        }

        /** 현재 세션의 시작 벽시계(epoch millis) — 없으면 null(M3-05 studytime 경과 산출용). */
        fun sessionStartMillis(): Long? = synchronized(lock) { startAtMillis }

        /** 완료된 한 턴을 기록한다. 스킵/실패 섹션은 해당 키가 null 로 들어온다(§9.1). */
        fun record(
            koreanPrompt: String,
            userText: String,
            buffer: TurnFeedbackBuffer,
        ) {
            synchronized(lock) {
                turns +=
                    BufferedTurn(
                        koreanPrompt = koreanPrompt,
                        userText = userText,
                        correctedText = buffer.correctedText,
                        naturalExpression = buffer.naturalExpression,
                        slimScore = buffer.slimScore,
                    )
            }
        }

        /** 요약 페이로드 투영용 원시 turn 리스트 스냅샷(방어적 복사). */
        fun bufferedTurns(): List<BufferedTurn> = synchronized(lock) { turns.toList() }

        /** 종합 점수 = slim writingScore 평균(null 턴 제외, 반올림). 유효 점수 없으면 null. */
        fun totalScore(): Int? =
            synchronized(lock) {
                val scores = turns.mapNotNull { it.slimScore }
                if (scores.isEmpty()) null else scores.average().roundToInt()
            }

        /** 하이라이트 base = slim 점수 최고 턴(≤1). 유효 점수 없으면 null. */
        fun highlightBase(): HighlightTurn? =
            synchronized(lock) {
                turns
                    .filter { it.slimScore != null }
                    .maxByOrNull { it.slimScore!! }
                    ?.let { HighlightTurn(it.koreanPrompt, it.userText, it.slimScore!!) }
            }

        /** 명시적 리셋(세션 종료·이탈). */
        fun clear() {
            synchronized(lock) {
                turns.clear()
                currentSessionId = null
                startAtMillis = null
            }
        }
    }
```

- [ ] **Step 4: (deferred) — the test compiles/passes after Task 5.** Proceed to Task 4. (`SummaryCoordinator.kt` still references the old `turns()`; it is fixed in Task 5, so the module compiles only after Task 5.)

---

## Task 4: Pure projector — buffered turns → full summary payload

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryPayloadProjector.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryPayloadProjectorTest.kt` (create)

**Interfaces:**
- Consumes: `BufferedTurn` (Task 3), `SummaryPayload`/`SummaryTurnDto`/`SummaryExpressionCandidateDto` (Task 2).
- Produces (relied on by Task 5): `object SummaryPayloadProjector { fun project(turns: List<BufferedTurn>, totalScore: Int): SummaryPayload }` — returns a payload with `sections = null` (the caller sets the retry filter).

**Context:** Projection rules, derived from the backend prompts (`functions/src/config/summary-prompts.ts`):
- `turns` → coaching: `before`=userText, `after`=correctedText ?: naturalExpression, `score`=slimScore.
- `expressionCandidates` → expressions filter: one `accurate` candidate per turn whose `correctedText` differs from `userText`, one `natural` candidate per turn whose `naturalExpression` differs from `userText`.
- `userOriginalSentences` = the user's raw sentences (words filter excludes their lemmas).
- `sentences` = the improved sentences (corrected ∪ natural) the words filter mines for new vocab.
- `words` = empty: the client has no discrete vocabulary list; the words sub-call grounds on `sentences`.

- [ ] **Step 1: Write the failing projector test**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryPayloadProjectorTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryPayloadProjectorTest {
    private val turns =
        listOf(
            // 교정 O + 자연스러운 표현 O, 둘 다 원문과 다름 → accurate + natural 후보 각 1.
            BufferedTurn("커피 주세요", "One coffee", correctedText = "Could I get a coffee?", naturalExpression = "Can I grab a coffee?", slimScore = 80),
            // 교정 없음(원문 정답) + 자연스러운 표현만 → natural 후보 1, after=자연스러운 표현.
            BufferedTurn("고마워", "Thank you", correctedText = null, naturalExpression = "Thanks a lot!", slimScore = 90),
            // 교정==원문(변화 없음) → 후보 없음.
            BufferedTurn("네", "Yes", correctedText = "Yes", naturalExpression = null, slimScore = 70),
        )

    @Test
    fun `turns map to backend before-after-score shape`() {
        val p = SummaryPayloadProjector.project(turns, totalScore = 80)
        assertEquals(3, p.turns.size)
        assertEquals("One coffee", p.turns[0].before)
        assertEquals("Could I get a coffee?", p.turns[0].after) // corrected wins over natural
        assertEquals(80, p.turns[0].score)
        assertEquals("Thanks a lot!", p.turns[1].after) // no correction → natural
        assertEquals("Yes", p.turns[2].after)
    }

    @Test
    fun `expression candidates split accurate and natural, dropping no-change`() {
        val p = SummaryPayloadProjector.project(turns, totalScore = 80)
        // turn0: accurate + natural; turn1: natural; turn2: none (corrected==original, natural null)
        assertEquals(3, p.expressionCandidates.size)
        val t0 = p.expressionCandidates.filter { it.before == "One coffee" }
        assertEquals(setOf("accurate", "natural"), t0.map { it.type }.toSet())
        assertEquals("Could I get a coffee?", t0.first { it.type == "accurate" }.after)
        assertTrue(p.expressionCandidates.none { it.before == "Yes" })
    }

    @Test
    fun `sentences are improved forms, originals are user text, words empty`() {
        val p = SummaryPayloadProjector.project(turns, totalScore = 80)
        assertEquals(listOf("One coffee", "Thank you", "Yes"), p.userOriginalSentences)
        assertTrue(p.sentences.contains("Could I get a coffee?"))
        assertTrue(p.sentences.contains("Can I grab a coffee?"))
        assertTrue(p.sentences.contains("Thanks a lot!"))
        assertTrue(p.words.isEmpty())
        assertEquals(80, p.totalScore)
        assertNull(p.sections)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SummaryPayloadProjectorTest*'`
Expected: FAIL to compile — `SummaryPayloadProjector` does not exist.

- [ ] **Step 3: Implement the projector**

Create `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryPayloadProjector.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.summary

import com.jjundev.oneclickeng.core.network.SummaryExpressionCandidateDto
import com.jjundev.oneclickeng.core.network.SummaryPayload
import com.jjundev.oneclickeng.core.network.SummaryTurnDto

/**
 * 세션 turn 버퍼 → summary 요청 페이로드 투영(순수 함수). 백엔드 서브콜 계약
 * (functions/src/config/summary-prompts.ts)에 맞춰 4개 입력을 산출한다:
 * - [SummaryPayload.turns] → coaching (before/after/score)
 * - [SummaryPayload.expressionCandidates] → 표현 필터 (turn 당 accurate/natural before→after 후보)
 * - [SummaryPayload.userOriginalSentences] → 단어 필터가 lemma 로 제외할 원문
 * - [SummaryPayload.sentences] → 단어 필터가 신규 어휘를 캐는 개선문(교정 ∪ 자연스러운 표현)
 *
 * [SummaryPayload.words] 는 비운다 — 클라에 개별 단어 목록이 없고, 단어 서브콜은 sentences 로 grounding 한다.
 * 반환 페이로드의 [SummaryPayload.sections] 는 null(초기 호출 = 전 섹션); 재시도 필터는 코디네이터가 채운다.
 */
object SummaryPayloadProjector {
    fun project(
        turns: List<BufferedTurn>,
        totalScore: Int,
    ): SummaryPayload {
        val originals = turns.map { it.userText }.filter { it.isNotBlank() }
        val sentences =
            turns
                .flatMap { listOfNotNull(it.correctedText, it.naturalExpression) }
                .filter { it.isNotBlank() }
                .distinct()
        val candidates =
            turns.flatMap { turn ->
                buildList {
                    turn.correctedText
                        ?.takeIf { it.isNotBlank() && it != turn.userText }
                        ?.let { add(SummaryExpressionCandidateDto("accurate", turn.koreanPrompt, turn.userText, it)) }
                    turn.naturalExpression
                        ?.takeIf { it.isNotBlank() && it != turn.userText }
                        ?.let { add(SummaryExpressionCandidateDto("natural", turn.koreanPrompt, turn.userText, it)) }
                }
            }
        val wireTurns =
            turns.map { turn ->
                SummaryTurnDto(
                    koreanPrompt = turn.koreanPrompt,
                    before = turn.userText,
                    after = turn.correctedText ?: turn.naturalExpression,
                    score = turn.slimScore,
                )
            }
        return SummaryPayload(
            totalScore = totalScore,
            turns = wireTurns,
            expressionCandidates = candidates,
            words = emptyList(),
            sentences = sentences,
            userOriginalSentences = originals,
            sections = null,
        )
    }
}
```

- [ ] **Step 4: (deferred) — passes after Task 5** (module still references old `turns()` in the coordinator). Proceed to Task 5.

---

## Task 5: Coordinator sends the projected payload + plural retry sections

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinator.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinatorTest.kt` (edit)

**Interfaces:**
- Consumes: `SessionTurnBufferStore.bufferedTurns()`/`totalScore()` (Task 3), `SummaryPayloadProjector.project(...)` (Task 4), `SummaryPayload.copy(sections=…)` (Task 2).
- Produces: unchanged public API (`start`/`retry`/`reset`/`toggleSave*`/`state`).

**Context:** `SummarySection.wireKey` is used ONLY to build the retry `sections` list (`loadingSectionKeys()` at line 417-420; the SSE parser matches card `kind` with hard-coded strings, not this enum). The backend retry filter expects PLURAL keys, so make the keys plural and rename `wireKey`→`sectionKey`. Replace the stored `payloadTurns`/`payloadScore` with a single projected `basePayload`.

- [ ] **Step 1: Update the failing coordinator test assertions**

In `SummaryCoordinatorTest.kt`, update the three retry assertions:
- Line ~321: `assertNull(stream.requests[0].payload.retrySections)` → `assertNull(stream.requests[0].payload.sections)`
- Line ~322: `assertEquals(listOf("word"), stream.requests[1].payload.retrySections)` → `assertEquals(listOf("words"), stream.requests[1].payload.sections)`
- Line ~484: `assertEquals(listOf("expression", "word"), stream.requests.last().payload.retrySections)` → `assertEquals(listOf("expressions", "words"), stream.requests.last().payload.sections)`

Add one new test asserting the initial call now carries projected inputs (place after the existing `bookmarks load asynchronously…` test):

```kotlin
    @Test
    fun `initial request projects expression candidates and sentences from the buffer`() =
        runTest {
            val stream = FakeSummaryStream()
            val coordinator = coordinator(coordScope(), stream)
            coordinator.begin()
            runCurrent()
            val payload = stream.requests.first().payload
            // store() seeds two turns with correctedText/naturalExpression → non-empty candidates + sentences.
            assertTrue("expected projected expression candidates", payload.expressionCandidates.isNotEmpty())
            assertTrue("expected projected sentences", payload.sentences.isNotEmpty())
            assertEquals(listOf("One coffee", "Where station"), payload.userOriginalSentences)
            assertNull(payload.sections)
        }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SummaryCoordinatorTest*'`
Expected: FAIL to compile — `payload.sections`/`payload.expressionCandidates` unresolved on the (old) coordinator wiring, and the coordinator still calls `turnBuffer.turns()`.

- [ ] **Step 3: Rewire the coordinator**

In `SummaryCoordinator.kt`:

(a) Replace the `SummarySection` enum (lines 24-29) with plural keys:

```kotlin
/** 재시도 필터가 지정하는 요약 SSE 섹션. [sectionKey] 는 백엔드 PLURAL 키(`done.sections`/`payload.sections`). */
enum class SummarySection(val sectionKey: String) {
    Expression("expressions"),
    Word("words"),
    Coaching("coaching"),
}
```

(b) Replace the two request-input fields (lines 82-84):

```kotlin
        // The request inputs of the current summary, retained so retry() re-issues the same call.
        private var sessionId: String? = null
        private var basePayload: SummaryPayload =
            com.jjundev.oneclickeng.core.network.SummaryPayload(totalScore = 0)
```

(c) In `start()`, replace the two payload lines (currently lines 131-132, `payloadTurns = turnBuffer.turns()` and `payloadScore = totalScore ?: 0`) with:

```kotlin
            basePayload = SummaryPayloadProjector.project(turnBuffer.bufferedTurns(), totalScore ?: 0)
```

(d) In `launchAttempt(retrySections: List<String>?)` (lines 259-283), rename the parameter to `sections` and build the request from `basePayload`:

```kotlin
        private fun launchAttempt(sections: List<String>?) {
            val id = sessionId ?: return
            val token = ++sessionToken
            currentJob?.cancel()
            val request =
                SummaryRequest(
                    sessionId = id,
                    payload = basePayload.copy(sections = sections),
                )
            armWatchdog(token)
            currentJob =
                scope.launch {
                    stream.events(request).collect { event ->
                        if (token != sessionToken) return@collect
                        onEvent(token, event)
                    }
                    // Stream closed without a clean `done` — fail whatever is still Loading.
                    if (token == sessionToken) failLoadingSections(token)
                }
        }
```

(e) Update the two `launchAttempt(...)` call sites: line 150 `launchAttempt(retrySections = null)` → `launchAttempt(sections = null)`; line 166 `launchAttempt(retrySections = loadingSectionKeys())` → `launchAttempt(sections = loadingSectionKeys())`.

(f) Update `loadingSectionKeys()` (lines 417-420) to use the renamed accessor:

```kotlin
        private fun loadingSectionKeys(): List<String> =
            SummarySection.entries
                .filter { sectionState(it) is SummarySectionState.Loading }
                .map { it.sectionKey }
```

(g) Add the projector/payload imports near the top (after the existing `SummaryRequest` import):

```kotlin
import com.jjundev.oneclickeng.core.network.SummaryPayload
```

(`SummaryPayloadProjector` is in the same package — no import needed. Remove the now-unused `SummaryPayload` fully-qualified reference in (b) by using the imported name: `private var basePayload: SummaryPayload = SummaryPayload(totalScore = 0)`.)

(h) Update the stale KDoc references to `retrySections` in the class header (lines 43-45) and the `retry` KDoc (line 155) to say `sections`/plural — replace occurrences of `retrySections` with `sections` and note keys are plural. (detekt does not require this, but keep docs truthful.)

- [ ] **Step 4: Compile the whole module + run the affected unit tests**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SummaryPayloadWireTest*' --tests '*SessionTurnBufferStoreTest*' --tests '*SummaryPayloadProjectorTest*' --tests '*SummaryCoordinatorTest*' --tests '*SummarySseStreamTest*'`
Expected: PASS for all five classes. (`SummarySseStreamTest` uses `SummaryPayload(totalScore = 85, turns = emptyList())` — still valid via defaults.)

- [ ] **Step 5: Run the full default gate**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL (detekt + debug/release unit tests + androidTest compile).

- [ ] **Step 6: Commit Tasks 2-5 together**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/core/network/SummaryContracts.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SessionTurnBufferStore.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryPayloadProjector.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinator.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/core/network/SummaryPayloadWireTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SessionTurnBufferStoreTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryPayloadProjectorTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinatorTest.kt
git commit -m "fix(summary): send full projected payload so expression/word/coaching sections populate"
```

---

## Task 6: Pure "latest bookmarks, pending writes first" ordering

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/saved/BookmarkOrdering.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/saved/BookmarkOrderingTest.kt` (create)

**Interfaces:**
- Consumes: `BookmarkCard(english, korean)` (`feature/session/summary/SummaryState.kt:153`).
- Produces (relied on by Task 7):
  - `data class BookmarkDoc(english: String, korean: String, createdAtMillis: Long?)`
  - `object BookmarkOrdering { fun latest(docs: List<BookmarkDoc>, limit: Int): List<BookmarkCard> }`

**Context:** Root cause of the missing saved sentences: `FirestoreBookmarkSource` reads server-first and orders by `createdAt`, but a card just saved in this session has a *pending* server timestamp (`createdAt` unresolved/null locally, and absent on the server until sync). Such cards are excluded from / starved out of the `orderBy(createdAt).limit(N)` query. Fix: read cache-first with *estimated* timestamps (pending → ~now) and order client-side, treating a still-null timestamp as newest (a just-saved card must surface). This pure helper is the testable core, mirroring the existing `SavedCardReconcile` seam.

- [ ] **Step 1: Write the failing ordering test**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/saved/BookmarkOrderingTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.saved

import com.jjundev.oneclickeng.feature.session.summary.BookmarkCard
import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkOrderingTest {
    @Test
    fun `newest createdAt first, capped to limit`() {
        val docs =
            listOf(
                BookmarkDoc("a", "가", createdAtMillis = 100),
                BookmarkDoc("b", "나", createdAtMillis = 300),
                BookmarkDoc("c", "다", createdAtMillis = 200),
            )
        assertEquals(
            listOf(BookmarkCard("b", "나"), BookmarkCard("c", "다")),
            BookmarkOrdering.latest(docs, limit = 2),
        )
    }

    @Test
    fun `pending write (null createdAt) is treated as newest`() {
        val docs =
            listOf(
                BookmarkDoc("old", "옛", createdAtMillis = 500),
                BookmarkDoc("justSaved", "방금", createdAtMillis = null),
            )
        assertEquals(
            listOf(BookmarkCard("justSaved", "방금"), BookmarkCard("old", "옛")),
            BookmarkOrdering.latest(docs, limit = 8),
        )
    }

    @Test
    fun `empty input yields empty output`() {
        assertEquals(emptyList<BookmarkCard>(), BookmarkOrdering.latest(emptyList(), limit = 8))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*BookmarkOrderingTest*'`
Expected: FAIL to compile — `BookmarkDoc`/`BookmarkOrdering` do not exist.

- [ ] **Step 3: Implement the ordering helper**

Create `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/saved/BookmarkOrdering.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.saved

import com.jjundev.oneclickeng.feature.session.summary.BookmarkCard

/** 정렬용 북마크 doc — [createdAtMillis] 는 estimate 로 해석된 생성시각(미해결 pending write 는 null). */
data class BookmarkDoc(
    val english: String,
    val korean: String,
    val createdAtMillis: Long?,
)

/**
 * "최신 N개" 북마크 정렬(순수). 방금 저장한(pending server timestamp) 카드가 반드시 최상단에 오도록,
 * createdAt 이 여전히 null 인 doc 을 **가장 최신**으로 취급한다(estimate 로도 못 푼 = 막 큐된 로컬 쓰기).
 * 이는 표시 정렬 전용 규칙으로, 충돌 해소의 [SavedCardReconcile](null=가장 오래됨)과는 관심사가 다르다.
 */
object BookmarkOrdering {
    fun latest(
        docs: List<BookmarkDoc>,
        limit: Int,
    ): List<BookmarkCard> =
        docs
            .sortedByDescending { it.createdAtMillis ?: Long.MAX_VALUE }
            .take(limit)
            .map { BookmarkCard(english = it.english, korean = it.korean) }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*BookmarkOrderingTest*'`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/saved/BookmarkOrdering.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/saved/BookmarkOrderingTest.kt
git commit -m "feat(saved): pure latest-bookmarks ordering treating pending writes as newest"
```

---

## Task 7: Cache-first bookmark read so current-session saves appear

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/saved/FirestoreBookmarkSource.kt`

**Interfaces:**
- Consumes: `BookmarkDoc`/`BookmarkOrdering` (Task 6), Firestore `Source.CACHE`, `DocumentSnapshot.ServerTimestampBehavior.ESTIMATE`.
- Produces: unchanged `BookmarkSource.latestSentences(sessionId, limit): List<BookmarkCard>` behavior contract (now reliable for current-session saves).

**Context:** This is a thin Firestore adapter — its logic (ordering) is unit-tested in Task 6; the Firestore behavior it relies on (cache read includes pending local writes; `ESTIMATE` resolves a pending server timestamp to ~now) is an SDK default that the repo verifies by instrumented/manual test, exactly like `FirestoreSavedCardRepository.exists()`'s `Source.CACHE` assumption. Changes:
1. Drop the server-side `.orderBy(createdAt)` + `.limit()` (they exclude/starve pending-timestamp docs). Keep the two equality filters — covered by the existing composite index prefix `(cardType ASC, deletedAt ASC, …)` (firestore.indexes.json), so no index change.
2. Read `Source.CACHE` first (offline-first, includes just-saved writes), falling back to a default get when the cache is cold (fresh install) — mirroring `exists()`.
3. Resolve each doc's `createdAt` with `ServerTimestampBehavior.ESTIMATE`, then order + cap via `BookmarkOrdering`.

- [ ] **Step 1: Rewrite `latestSentences` and the doc-mapping helper**

Replace the class body of `FirestoreBookmarkSource.kt` from the `latestSentences` override through the `companion object` (keep the file's KDoc header, but update its query description — see Step 2). New implementation:

```kotlin
@Singleton
class FirestoreBookmarkSource
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val authRepository: AuthRepository,
    ) : BookmarkSource {
        @Suppress("TooGenericExceptionCaught") // 표시 전용 로드 — 미인증/오프라인/인덱스 미비 모두 빈 리스트로 강등.
        override suspend fun latestSentences(
            sessionId: String,
            limit: Int,
        ): List<BookmarkCard> {
            val uid = authRepository.currentUid ?: return emptyList()
            val query =
                firestore
                    .collection(USERS).document(uid)
                    .collection(SAVED_CARDS)
                    .whereEqualTo(FIELD_CARD_TYPE, CardType.SENTENCE.wire)
                    .whereEqualTo(FIELD_DELETED_AT, null)
            return try {
                // 오프라인-우선(ADR-0002): 캐시는 방금 저장한 pending write 를 즉시 포함한다. 캐시가 비면
                // (신규 기기) 서버로 폴백 — FirestoreSavedCardRepository.exists() 와 동일 패턴.
                val snapshot =
                    runCatching { query.get(Source.CACHE).await() }
                        .recoverCatching { query.get().await() }
                        .getOrThrow()
                val docs =
                    snapshot.documents.mapNotNull { doc ->
                        val english = doc.getString(FIELD_ENGLISH) ?: return@mapNotNull null
                        BookmarkDoc(
                            english = english,
                            korean = doc.getString(FIELD_KOREAN).orEmpty(),
                            // pending server timestamp → ESTIMATE 로 ~now 추정(정렬 시 최신 취급).
                            createdAtMillis =
                                doc.getTimestamp(FIELD_CREATED_AT, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                                    ?.toDate()?.time,
                        )
                    }
                BookmarkOrdering.latest(docs, limit)
            } catch (e: Exception) {
                Log.d(TAG, "bookmark query failed: ${e.message}")
                emptyList()
            }
        }

        private companion object {
            const val TAG = "FirestoreBookmarkSource"
            const val USERS = "users"
            const val SAVED_CARDS = "saved_cards"
            const val FIELD_CARD_TYPE = "cardType"
            const val FIELD_DELETED_AT = "deletedAt"
            const val FIELD_CREATED_AT = "createdAt"
            const val FIELD_ENGLISH = "english"
            const val FIELD_KOREAN = "korean"
        }
    }
```

Add the required imports at the top of the file (alongside the existing `com.google.firebase.firestore.*` imports):

```kotlin
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Source
```

Remove the now-unused `import com.google.firebase.firestore.Query` (the `Query.Direction` reference is gone). Keep `import com.jjundev.oneclickeng.feature.session.summary.BookmarkCard` and add nothing else — `BookmarkDoc`/`BookmarkOrdering` are in the same package.

- [ ] **Step 2: Update the file KDoc to describe the new read**

In the class KDoc (lines 13-22), replace the query description paragraph so it states the read is cache-first, equality-only (`cardType==SENTENCE and deletedAt==null`), ordered client-side via `BookmarkOrdering` with `ESTIMATE` timestamps so a just-saved (pending) sentence surfaces at the top. Note the existing composite index `(cardType, deletedAt, createdAt)` still covers the equality filters as a prefix (no index change). Keep it truthful and in 해요체-consistent style with the rest of the file.

- [ ] **Step 3: Run the full default gate (compile + detekt + unit tests)**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL. (`FirestoreBookmarkSource` has no direct unit test — it is a thin adapter; the ordering logic is covered by `BookmarkOrderingTest`, and the `SummaryCoordinatorTest` fake `BookmarkSource` is unaffected.)

- [ ] **Step 4: Manually verify current-session saved sentences appear in the summary**

This is the behavior the plan exists to fix; verify it end-to-end (a unit test cannot exercise real Firestore timestamp semantics). **Do not skip this step** — it is the *only* end-to-end guard for Request 2. The `Source.CACHE` fallback and `ESTIMATE` timestamp resolution are Firestore SDK behaviors that no JVM unit test reaches, so a subtly wrong interaction here would ship silently without this manual check.
1. Build/run the app on a device/emulator, sign in.
2. Start a session; in a turn's deep feedback sheet, tap the (now-gold) save icon on one or more paraphrase sentences.
3. Finish the session to reach the summary.
4. Confirm the "북마크 문장" section lists the sentence(s) you just saved (not the empty state "아직 저장한 문장이 없어요"). Toggle airplane mode on before saving and repeat to confirm the offline path also surfaces them.

Report the observed result. If they still do not appear, capture Logcat for tag `FirestoreBookmarkSource` and re-diagnose before committing.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/saved/FirestoreBookmarkSource.kt
git commit -m "fix(summary): read bookmarks cache-first so current-session saved sentences appear"
```

---

## Verification Summary

After all tasks:
- `scripts/verify-android.sh` (full default gate) is green.
- Request 1: `flow_deep_light.png` shows a gold save icon.
- Request 2: manual on-device check — sentences saved this session appear in the summary's 북마크 문장 section (online and offline).
- Request 3: `SummaryPayloadWireTest`/`SummaryPayloadProjectorTest`/`SummaryCoordinatorTest` prove the client now sends `expressionCandidates`/`sentences`/`userOriginalSentences`/`turns{before,after,score}` and plural `sections`; on a live run the summary's 자연스러운 표현 / 새 단어 / 코칭 sections populate instead of only the highlight. (Optional deeper check: verify against the deployed backend that a real session returns non-empty expression cards.)
