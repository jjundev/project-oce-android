# Loading Quiz Session Messages Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep `"첫 대화를 준비하고 있어요"` for the onboarding flow while showing one randomly selected returning-user loading message for every second-or-later learning session, with all copy stored in a JSON asset.

**Architecture:** Add an asset-backed `LoadingMessageSource` beside the existing `QuizBank` asset repository. The ViewModel selects one message when a generation starts, using the existing `isOnboarding` flow context as the selector; the route exposes that selected value to the stateless generating screen, so recomposition does not reshuffle the copy. The onboarding and returning message sets live in `android/app/src/main/assets/loading_messages.json` and are parsed with the project’s existing Kotlin Serialization setup.

**Tech Stack:** Kotlin, Jetpack Compose, Android assets, Kotlinx Serialization JSON, Hilt, JUnit, Robolectric/Robolectric Compose screenshot tests, Gradle.

## Global Constraints

- The onboarding copy must remain exactly `"첫 대화를 준비하고 있어요"`.
- The returning-user copy must be selected randomly from multiple messages stored in a JSON file, not written as production Kotlin string literals.
- Use `isOnboarding` to choose the copy set; keep `firstSession` unchanged for its existing quiz-tier behavior.
- Select one returning message per generation start and keep it stable across Compose recompositions and the ready/loading state transitions.
- Preserve the existing 1,000 ms loading gate, quiz behavior, TTS warm-up gate, retry behavior, analytics, and kill-switch semantics.
- Do not add a runtime network dependency or a new third-party JSON library.

---

## File Structure

- Create: `android/app/src/main/assets/loading_messages.json` — onboarding copy and the returning-session message pool.
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/loading/LoadingMessageRepository.kt` — source interface, JSON asset shell, catalog model, and pure selector/parser seam.
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/loading/LoadingMessageParserTest.kt` — JSON schema, exact onboarding copy, and returning-pool selection tests.
- Create: `docs/plans/2026-07-21-loading-quiz-returning-messages.md` — this implementation plan, included in the documentation commit when the plan is executed.
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueFeatureModule.kt` — bind the asset-backed source to the interface.
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModel.kt` — select and expose one message at `start()`.
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModelTest.kt` — inject a fake source and verify onboarding/returning selection requests.
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationFunnelAnalyticsTest.kt` — supply the new source dependency to its direct ViewModel factory.
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/WaitQuizShownEndedEmitTest.kt` — supply the new source dependency to its direct ViewModel factory.
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeSessionGraph.kt` — make the returning-session context explicit at the home route call site.
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreen.kt` — pass the selected copy from the route and render it in `SlimLoading`.
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreenshotTest.kt` — provide deterministic copy to the screen and protect the visible loading-copy surface.
- Modify: `docs/ux/loading-quiz-interstitial.md` — document the two message pools and JSON ownership.
- Modify: `docs/ux/dialogue-learning-flow.md` — make the neutral-copy rule distinguish onboarding from returning sessions.
- Modify: `docs/ux/01-onboarding-first-session.md` — clarify that the existing exact copy is onboarding-only while remaining the onboarding source of truth.

## Decision Checkpoint

No unresolved execution-level fork remains. The repository already has the exact asset-reader/parser pattern (`QuizBankRepository`), Kotlin Serialization is already configured, and `isOnboarding` is already the explicit onboarding-context flag passed from both navigation graphs. The plan therefore does not introduce a new persistence layer, remote config, or a user-history query.

### Task 1: Add the JSON-backed loading-message source

**Files:**
- Create: `android/app/src/main/assets/loading_messages.json`
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/loading/LoadingMessageRepository.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/loading/LoadingMessageParserTest.kt`

**Interfaces:**
- Produces `LoadingMessageSource.forSession(isOnboarding: Boolean): String` for the ViewModel.
- Produces `LoadingMessageParser.parse(json: Json, text: String): LoadingMessageCatalog` for pure unit tests.
- Produces `LoadingMessageSelector.select(catalog: LoadingMessageCatalog, isOnboarding: Boolean, random: Random = Random.Default): String` for deterministic selector tests.

- [ ] **Step 1: Write the failing parser and selector tests**

Create the test package and add tests that lock the asset shape, exact onboarding copy, and returning-pool behavior:

```kotlin
package com.jjundev.oneclickeng.feature.session.dialogue.loading

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LoadingMessageParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val asset =
        """
        {
          "onboarding": "첫 대화를 준비하고 있어요",
          "returning": ["다음 대화를 준비하고 있어요", "오늘의 연습을 준비하고 있어요"]
        }
        """.trimIndent()

    @Test
    fun `parse preserves the onboarding copy and returning pool`() {
        val catalog = LoadingMessageParser.parse(json, asset)

        assertEquals("첫 대화를 준비하고 있어요", catalog.onboarding)
        assertEquals(
            listOf("다음 대화를 준비하고 있어요", "오늘의 연습을 준비하고 있어요"),
            catalog.returning,
        )
    }

    @Test
    fun `selector always keeps the exact onboarding copy`() {
        val catalog = LoadingMessageParser.parse(json, asset)

        assertEquals(
            "첫 대화를 준비하고 있어요",
            LoadingMessageSelector.select(catalog, isOnboarding = true, random = Random(0)),
        )
    }

    @Test
    fun `selector chooses a returning message from the JSON pool`() {
        val catalog = LoadingMessageParser.parse(json, asset)

        val selected = LoadingMessageSelector.select(catalog, isOnboarding = false, random = Random(0))

        assertTrue(selected in catalog.returning)
    }

    @Test
    fun `shipped asset contains the exact onboarding copy and multiple returning messages`() {
        val assetFile =
            sequenceOf(
                java.io.File("src/main/assets/loading_messages.json"),
                java.io.File("app/src/main/assets/loading_messages.json"),
            ).first { it.isFile }
        val catalog = LoadingMessageParser.parse(json, assetFile.readText())

        assertEquals("첫 대화를 준비하고 있어요", catalog.onboarding)
        assertTrue(catalog.returning.size >= 2)
    }
}
```

- [ ] **Step 2: Run the focused test to verify it fails**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.session.dialogue.loading.LoadingMessageParserTest'
```

Expected: `FAIL` because the loading-message package, parser, selector, and catalog types do not exist yet.

- [ ] **Step 3: Add the JSON asset with production copy**

Create `android/app/src/main/assets/loading_messages.json` with one onboarding message and at least five returning messages so the product copy can be edited without a Kotlin change:

```json
{
  "onboarding": "첫 대화를 준비하고 있어요",
  "returning": [
    "다음 대화를 준비하고 있어요",
    "오늘의 연습을 준비하고 있어요",
    "새로운 대화 상대를 만나볼까요?",
    "이번 연습에 맞는 대화를 만들고 있어요",
    "곧 영어로 이야기할 수 있어요"
  ]
}
```

- [ ] **Step 4: Implement the pure catalog/parser/selector and asset-backed interface**

Create `LoadingMessageRepository.kt` with the same thin asset-reader boundary used by `QuizBankRepository`:

```kotlin
package com.jjundev.oneclickeng.feature.session.dialogue.loading

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

interface LoadingMessageSource {
    fun forSession(isOnboarding: Boolean): String
}

@Serializable
data class LoadingMessageCatalog(
    val onboarding: String,
    val returning: List<String>,
)

object LoadingMessageParser {
    fun parse(json: Json, text: String): LoadingMessageCatalog =
        json.decodeFromString(LoadingMessageCatalog.serializer(), text)
}

object LoadingMessageSelector {
    fun select(
        catalog: LoadingMessageCatalog,
        isOnboarding: Boolean,
        random: Random = Random.Default,
    ): String =
        if (isOnboarding) {
            catalog.onboarding
        } else {
            catalog.returning.random(random)
        }
}

@Singleton
class LoadingMessageRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val json: Json,
    ) : LoadingMessageSource {
        private val catalog: LoadingMessageCatalog by lazy {
            val text = context.assets.open(ASSET).bufferedReader().use { it.readText() }
            LoadingMessageParser.parse(json, text)
        }

        override fun forSession(isOnboarding: Boolean): String =
            LoadingMessageSelector.select(catalog, isOnboarding)

        private companion object {
            const val ASSET = "loading_messages.json"
        }
    }
```

The returning list must be non-empty in the shipped asset; do not add a Kotlin fallback message that would bypass JSON ownership. The fixed onboarding sentence is also read from the asset, not duplicated in production code.

- [ ] **Step 5: Run the focused test to verify it passes**

Run the same Gradle command from Step 2. Expected: `PASS`, including the exact onboarding string and returning-pool membership assertions.

- [ ] **Step 6: Commit the asset-backed source**

```bash
git add android/app/src/main/assets/loading_messages.json \
  android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/loading/LoadingMessageRepository.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/loading/LoadingMessageParserTest.kt
git commit -m "feat(dialogue): add asset-backed loading messages"
```

### Task 2: Select one message per generation in the ViewModel

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueFeatureModule.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModel.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModelTest.kt`

**Interfaces:**
- Consumes `LoadingMessageSource.forSession(isOnboarding: Boolean): String` from Task 1.
- Produces `DialogueGenerationViewModel.loadingMessage: StateFlow<String>`.
- Keeps `DialogueGenerationViewModel.start(level, topic, length, firstSession, isOnboarding)` unchanged for callers; only its internal initialization gains message selection.

- [ ] **Step 1: Add a fake source and failing ViewModel tests**

In `DialogueGenerationViewModelTest.kt`, add a fake source that records the context and returns deterministic values:

```kotlin
private class FakeLoadingMessageSource : LoadingMessageSource {
    val requests = mutableListOf<Boolean>()

    override fun forSession(isOnboarding: Boolean): String {
        requests += isOnboarding
        return if (isOnboarding) "onboarding-copy" else "returning-copy"
    }
}
```

Add these tests:

```kotlin
@Test
fun `onboarding start exposes the onboarding loading message`() =
    runTest {
        val source = FakeLoadingMessageSource()
        val vm = viewModel(RecordingAnalytics(), FakeConfig(true), loadingMessages = source)

        vm.start(level = "easy", topic = "t", length = 5, firstSession = true, isOnboarding = true)

        assertEquals("onboarding-copy", vm.loadingMessage.value)
        assertEquals(listOf(true), source.requests)
    }

@Test
fun `returning start exposes the returning loading message`() =
    runTest {
        val source = FakeLoadingMessageSource()
        val vm = viewModel(RecordingAnalytics(), FakeConfig(true), loadingMessages = source)

        vm.start(level = "hard", topic = "t", length = 10, firstSession = false, isOnboarding = false)

        assertEquals("returning-copy", vm.loadingMessage.value)
        assertEquals(listOf(false), source.requests)
    }
```

Extend the existing test factory with `loadingMessages: LoadingMessageSource = FakeLoadingMessageSource()` and pass it to the ViewModel constructor immediately after `quizBank`, matching the production constructor order.

There are three additional direct `DialogueGenerationViewModel(...)` constructions in this same test file (the sticky-Ready test, the server-TTS `prepareFirstLine` test, and the DEVICE-quality `prepareFirstLine` test). Insert the same `FakeLoadingMessageSource()` argument immediately after `bank` in each positional constructor call. The shared `viewModel(...)` factory must pass its `loadingMessages` parameter in that same position.

Update the other direct ViewModel construction sites in `DialogueGenerationFunnelAnalyticsTest.kt` and `WaitQuizShownEndedEmitTest.kt` in the same red phase. Import `LoadingMessageSource` and insert this deterministic test seam immediately after each test fake quiz bank argument:

```kotlin
object : LoadingMessageSource {
    override fun forSession(isOnboarding: Boolean): String = "test-loading-copy"
},
```

This keeps those tests focused on analytics while satisfying the new constructor contract; do not make the production dependency nullable or add a default constructor value.

Add one lifecycle test to prove selection is stable for coordinator retry but happens again for a fresh generation:

```kotlin
@Test
fun `retry keeps the selected message while a fresh start selects again`() =
    runTest {
        val source = FakeLoadingMessageSource()
        val vm = viewModel(RecordingAnalytics(), FakeConfig(true), loadingMessages = source)

        vm.start(level = "easy", topic = "t", length = 5, firstSession = false, isOnboarding = false)
        val selected = vm.loadingMessage.value
        vm.retry()
        assertEquals(listOf(false), source.requests)
        assertEquals(selected, vm.loadingMessage.value)

        vm.start(level = "easy", topic = "t2", length = 5, firstSession = false, isOnboarding = false)
        assertEquals(listOf(false, false), source.requests)
    }
```

- [ ] **Step 2: Run the focused ViewModel tests to verify they fail**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenerationViewModelTest'
```

Expected: `FAIL` because `loadingMessage` and the new constructor dependency do not exist yet.

- [ ] **Step 3: Bind the repository in Hilt**

In `DialogueFeatureModule.kt`, import `LoadingMessageSource` and `LoadingMessageRepository`, then add this binding beside `bindQuizBank`:

```kotlin
@Binds
@Singleton
abstract fun bindLoadingMessageSource(impl: LoadingMessageRepository): LoadingMessageSource
```

Update the module KDoc to state that it binds both the WaitQuiz bank and the loading-message asset source.

- [ ] **Step 4: Add the ViewModel state and initialize it at `start()`**

Add the import for `LoadingMessageSource`, add the constructor parameter after `private val quizBank: QuizBank`, and add this state beside `_quizItems`:

```kotlin
private val _loadingMessage = MutableStateFlow("")
val loadingMessage: StateFlow<String> = _loadingMessage.asStateFlow()
```

At the beginning of `start()`, after assigning `this.isOnboarding`, select exactly once for that generation:

```kotlin
this.isOnboarding = isOnboarding
_loadingMessage.value = loadingMessageSource.forSession(isOnboarding)
lastStart = StartParams(level, topic, length, firstSession)
```

Keep the existing `firstSession`-based quiz tier calculation and all offline/failed/retry state handling unchanged. A retry through `coordinator.retry()` must retain the already selected message; a fresh `start()` (including a pre-flight retry) may select a new returning message.

In `HomeSessionGraph.kt`, pass the already-true context explicitly at the home entry call site:

```kotlin
firstSession = false,
isOnboarding = false,
onStartConversation = {
```

Do not change the onboarding graph: it continues to pass `isOnboarding = first`, so the first onboarding flow selects the fixed onboarding copy.

- [ ] **Step 5: Run the focused ViewModel tests to verify they pass**

Run the same command from Step 2. Expected: `PASS`, including the pre-existing first-session quiz-tier, offline, TTS, analytics, and sticky-state tests.

- [ ] **Step 6: Commit the ViewModel seam**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueFeatureModule.kt \
  android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModel.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationViewModelTest.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGenerationFunnelAnalyticsTest.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/WaitQuizShownEndedEmitTest.kt \
  android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeSessionGraph.kt
git commit -m "feat(dialogue): select loading copy per session context"
```

### Task 3: Render the selected copy through the generating screen

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreen.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreenshotTest.kt`

**Interfaces:**
- Consumes `DialogueGenerationViewModel.loadingMessage` in `DialogueGeneratingRoute`.
- Produces `DialogueGeneratingScreen(..., loadingMessage: String = "")` and passes it to `SlimLoading(message: String)`.
- Does not change `OneClickWaitQuiz`, its item bank, its card copy, or its loading gate.

- [ ] **Step 1: Add a failing UI assertion for the loading copy**

In `DialogueGeneratingScreenshotTest.kt`, import `androidx.compose.ui.test.onNodeWithText` and `androidx.compose.ui.test.assertIsDisplayed`, then add a deterministic gate-before-quiz test:

```kotlin
@Test
fun onboarding_loading_copy_is_displayed_before_quiz() {
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
        OceTheme {
            Surface(color = MaterialTheme.colorScheme.background) {
                DialogueGeneratingScreen(
                    state = DialogueGenState.Generating,
                    quizItems = previewWaitQuizItems(),
                    loadingMessage = "첫 대화를 준비하고 있어요",
                    onStartConversation = {},
                    onRetry = {},
                )
            }
        }
    }

    composeRule.onNodeWithText("첫 대화를 준비하고 있어요").assertIsDisplayed()
}
```

The test intentionally does not advance past the 1,000 ms gate, so it verifies the exact `SlimLoading` surface rather than the quiz card.

- [ ] **Step 2: Run the focused screenshot/UI test to verify it fails**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.session.dialogue.DialogueGeneratingScreenshotTest.onboarding_loading_copy_is_displayed_before_quiz'
```

Expected: `FAIL` because `DialogueGeneratingScreen` does not yet accept or render a caller-provided loading message.

- [ ] **Step 3: Wire the ViewModel state through the route**

In `DialogueGeneratingRoute`, collect the new state beside `quizItems`:

```kotlin
val loadingMessage by viewModel.loadingMessage.collectAsStateWithLifecycle()
```

Pass it into the screen call:

```kotlin
DialogueGeneratingScreen(
    state = state,
    quizItems = quizItems,
    loadingMessage = loadingMessage,
    firstLineReady = firstLineReady,
    // existing callbacks remain unchanged
)
```

This keeps selection in the ViewModel and ensures a recomposition never calls `random()` again.

- [ ] **Step 4: Make `SlimLoading` render the supplied message and update stateless callers**

Add `loadingMessage: String = ""` to `DialogueGeneratingScreen`. Thread it through `GeneratingContent` and replace the current literal in `SlimLoading` with this implementation:

```kotlin
private fun SlimLoading(message: String) {
    OneClickProgressRing(mode = ProgressRingMode.Indeterminate)
    if (message.isNotBlank()) {
        Text(
            text = message,
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = OceTheme.spacing.md),
        )
    }
}
```

Update every internal `SlimLoading()` call to `SlimLoading(loadingMessage)`. Update the `DialogueGeneratingScreen` preview calls and every screenshot-test call with either the exact onboarding copy or a neutral test value; do not reintroduce the production copy as a Kotlin literal.

- [ ] **Step 5: Make the tests pass and preserve screenshot coverage**

Run the focused UI test from Step 2, then run the full generating-screen test class:

```bash
cd android
./gradlew testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.session.dialogue.DialogueGeneratingScreenshotTest'
```

Expected: `PASS`. The existing generating/ready screenshots must still render the same layout; only the supplied loading-copy text source changes. Keep `autoAdvance = false` for generating quiz screenshots because the quiz ring’s infinite transition still prevents `waitForIdle()`.

- [ ] **Step 6: Commit the UI wiring**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreen.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreenshotTest.kt
git commit -m "feat(dialogue): render session-specific loading copy"
```

### Task 4: Update UX source-of-truth documentation and run the full verification set

**Files:**
- Modify: `docs/ux/loading-quiz-interstitial.md`
- Modify: `docs/ux/dialogue-learning-flow.md`
- Modify: `docs/ux/01-onboarding-first-session.md`

**Interfaces:**
- Documents the runtime contract implemented by Tasks 1–3; no production API changes.

- [ ] **Step 1: Update the loading-quiz UX rules**

In `docs/ux/loading-quiz-interstitial.md`, change the layout rule from a single universal reassurance copy to an explicit copy slot:

```markdown
[안심 카피 1줄]                                  ← JSON asset에서 세션 맥락별로 선택
```

Add a content rule immediately after the layout bullets:

```markdown
- 온보딩 플로우(`isOnboarding=true`)는 `첫 대화를 준비하고 있어요`를 사용한다.
- 2차 이후 학습(`isOnboarding=false`)은 `android/app/src/main/assets/loading_messages.json`의
  `returning` 목록에서 생성 시작 시 멘트 하나를 랜덤 선택해 해당 대기 화면 동안 유지한다.
- 온보딩·재방문 멘트 모두 JSON이 문구의 정본이며, Kotlin UI 코드에는 실제 멘트를 복제하지 않는다.
```

Keep the existing quiz, gate, accessibility, and offline/static-bank rules unchanged.

- [ ] **Step 2: Update the general dialogue-flow wording**

Replace the current exact-copy sentence in `docs/ux/dialogue-learning-flow.md` with:

```markdown
대본 생성 중에는 중립 카피를 표시한다. 온보딩은 `첫 대화를 준비하고 있어요`를 유지하고,
2차 이후 학습은 `loading_messages.json`의 returning 목록에서 한 문구를 선택한다.
두 경우 모두 상대역 말풍선 placeholder는 렌더하지 않는다.
```

- [ ] **Step 3: Keep the onboarding copy table explicit**

In `docs/ux/01-onboarding-first-session.md`, retain the table row exactly as `첫 대화를 준비하고 있어요.` and add a note that this row is onboarding-only; returning-session alternatives are owned by `loading_messages.json`.

- [ ] **Step 4: Run parser, ViewModel, screen, and compile verification**

Run:

```bash
cd android
./gradlew testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.session.dialogue.loading.LoadingMessageParserTest' \
  --tests 'com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenerationViewModelTest' \
  --tests 'com.jjundev.oneclickeng.feature.session.dialogue.DialogueGenerationFunnelAnalyticsTest' \
  --tests 'com.jjundev.oneclickeng.feature.session.dialogue.WaitQuizShownEndedEmitTest' \
  --tests 'com.jjundev.oneclickeng.feature.session.dialogue.DialogueGeneratingScreenshotTest'
./gradlew lintDebug
```

Expected: all selected unit/Robolectric tests pass and `lintDebug` completes successfully without introducing a hardcoded loading-copy warning or an unused dependency.

- [ ] **Step 5: Inspect the final diff for copy duplication and scope drift**

Run:

```bash
git diff --check HEAD~4..HEAD
rg -n -F '첫 대화를 준비하고 있어요' android/app/src/main/kotlin
git status --short
```

Expected: the production Kotlin search returns no loading-copy literal; the exact onboarding sentence appears in `loading_messages.json` and documentation/tests only. `git status --short` is clean after the documentation commit.

- [ ] **Step 6: Commit the documentation and verification changes**

```bash
git add docs/ux/loading-quiz-interstitial.md \
  docs/ux/dialogue-learning-flow.md \
  docs/ux/01-onboarding-first-session.md \
  docs/plans/2026-07-21-loading-quiz-returning-messages.md
git commit -m "docs(dialogue): document session-specific loading copy"
```

## Self-Review

1. **Spec coverage:** The exact onboarding copy is stored in JSON and selected whenever the existing onboarding flag is true (Tasks 1–3). Returning sessions use a multi-message JSON pool and random selection (Tasks 1–2). The selection is stable for the generation because it is stored in ViewModel state and passed down (Tasks 2–3). The loading quiz, gate, retry, and analytics paths remain unchanged (Tasks 2–3). UX documentation is updated (Task 4).
2. **Placeholder scan:** The plan contains no `TODO`, `TBD`, “implement later”, or vague “write tests” steps. Every code-changing step includes the concrete file, API, code shape, and command with expected result.
3. **Type consistency:** `LoadingMessageSource.forSession(Boolean): String` is defined in Task 1, injected and consumed in Task 2, and its result is exposed as `StateFlow<String>` to Task 3. `LoadingMessageCatalog` and `LoadingMessageSelector.select` signatures match the parser tests and repository implementation.
