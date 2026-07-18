# Reminder Notification Variety (A+B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Diversify the single daily learning reminder's body copy — rotate several streak-branch phrasings, occasionally surface a recently auto-saved expression or a recommended situation, and override with a one-time celebration the day after hitting a streak milestone — without adding any new notification channel, trigger, or settings toggle.

**Architecture:** The reminder stays exactly what it is today: one `WorkManager` `OneTimeWorkRequest` per day, posting through the single `learning_reminder` channel. `ReminderLogic.buildContent` (a pure, Android-free object already covered by JVM unit tests) grows from "3 fixed strings" into "3 branches, each a small pool of variants, plus two optional bonus variants (review/situation) appended to whichever branch is active, plus a milestone override that bypasses the pool entirely." Milestone detection and the "last saved expression" mirror both live in the existing `ReminderStore` DataStore cache — the same place `streak`/`lastStudyDate` are already mirrored for offline-safe reads. `ReminderNotifier` (the only Android-aware edge) resolves the day's recommended situation from the already-installed, purely local `TopicCatalog` and threads everything into `buildContent`. `SummaryCoordinator`'s existing auto-save hooks gain one new fire-and-forget call each to mirror the saved expression text.

**Tech Stack:** Kotlin, Hilt DI, WorkManager, Jetpack DataStore (Preferences), JUnit4 + hand-rolled fakes (no Mockito in this module), Robolectric only where already used (`ReminderWorkerTest`).

## Global Constraints

- **No loss/countdown framing, ever.** Every new body string must read as a future-tense invitation ("이어가볼까요?", "어때요?"), never as a warning or a countdown (`docs/ux/notification-reminder.md` §1, §5.1).
- **🔥 only appears when a streak is live or being celebrated.** The "streak 0/신규" branch never uses 🔥; the "gap==1" and "gap>=2" branches and all milestone bodies do.
- **Still exactly one notification per day, one channel (`learning_reminder`), `IMPORTANCE_DEFAULT`.** No new channel, no new `WorkManager` unique work name, no heads-up popup.
- **No new Settings UI.** Per the confirmed decision, all variety is bundled under the existing single "학습 리마인더" on/off toggle — do not touch `SettingsScreen.kt` or add new DataStore-backed toggles for individual notification "kinds."
- **`ReminderLogic` stays a pure, Android-free object.** No direct `TopicCatalog`/`Context`/DataStore calls inside it — all variable data (situation title, saved-expression text, milestone streak, the random pick) comes in as parameters with production-safe defaults.
- **Milestone thresholds are exactly `{1, 3, 7, 14, 30}`**, reused verbatim from `docs/ux/gamification-emphasis.md` §5 (decision #18) — do not invent different values.
- **Every `ReminderCache` field added must default to `null`** so existing positional/named test construction sites keep compiling unchanged.

---

## File Structure

**Modify:**
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderLogic.kt` — pool-based `buildContent`, `MILESTONE_THRESHOLDS`, `milestoneBody`.
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/reminder/data/ReminderStore.kt` — `ReminderCache` gains `milestoneStreak`/`lastSavedReviewText`; `ReminderStore` gains `recordSavedReviewText`/`clearMilestone`; `DataStoreReminderRepository` persists both, and `recordSessionCompleted`/`resetProgressCache` are extended.
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderOrchestrator.kt` — interface gains `recordSavedReviewText`; `runDueReminder` clears a consumed milestone after posting.
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderNotifier.kt` — resolves the day's recommended situation from `TopicCatalog` and passes the full parameter set into `buildContent`.
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinator.kt` — injects `ReminderOrchestrator`; `autoSaveExpressions`/`autoSaveWords` mirror the first saved item's text.
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderLogicTest.kt` — pin the 4 existing `buildContent` tests to the original copy, add pool/review/situation/milestone tests.
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/data/ReminderRepositoryTest.kt` — new tests for milestone set/clear and saved-review-text persistence.
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderOrchestratorTest.kt` — `FakeReminderStore` grows two methods; new tests for delegation and milestone-clear-on-fire.
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderWorkerTest.kt` — compile fix: one new override on the local fake.
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModelTest.kt` — compile fix: one new override on the local fake.
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinatorTest.kt` — new `FakeReminderOrchestrator`, factory param, one new test.
- `docs/ux/notification-reminder.md` — §5.1 rewritten to document the pool/review/situation/milestone model.

**No new files.** Every piece of this feature extends an existing seam; nothing warrants a new class file.

---

## Task 1: `ReminderLogic` — content pool, review/situation variants, milestone override

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderLogic.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderLogicTest.kt`

**Interfaces:**
- Produces: `ReminderLogic.MILESTONE_THRESHOLDS: Set<Int>` (value `{1, 3, 7, 14, 30}`).
- Produces: `ReminderLogic.buildContent(streak: Int?, lastStudyDate: LocalDate?, today: LocalDate, milestoneStreak: Int? = null, lastSavedReviewText: String? = null, recommendedSituationTitle: String? = null, pickVariant: (Int) -> Int = { Random.nextInt(it) }): ReminderContent` — the `pickVariant` seam receives the candidate pool's size and must return an index in `[0, size)`; tests pass a fixed lambda for determinism, production uses the default.

- [ ] **Step 1: Write the failing tests for the new `buildContent` signature**

Replace the four existing `buildContent`-related tests in `ReminderLogicTest.kt` (the ones under `// --- buildContent (§5.1) ---`) with versions that pin `pickVariant = { 0 }`, and add the new pool/review/situation/milestone tests right after them:

```kotlin
    // --- buildContent (§5.1, pool + review/situation/milestone) --------------

    @Test
    fun `streak zero yields neutral start invite`() {
        val content =
            ReminderLogic.buildContent(
                streak = 0,
                lastStudyDate = null,
                today = LocalDate.of(2026, 7, 3),
                pickVariant = { 0 },
            )
        assertEquals("딸깍영어", content.title)
        assertEquals("오늘 시작하면 1일째예요", content.body)
    }

    @Test
    fun `cache miss yields neutral start invite`() {
        val content =
            ReminderLogic.buildContent(
                streak = null,
                lastStudyDate = null,
                today = LocalDate.of(2026, 7, 3),
                pickVariant = { 0 },
            )
        assertEquals("오늘 시작하면 1일째예요", content.body)
    }

    @Test
    fun `gap of one day shows future streak number`() {
        val today = LocalDate.of(2026, 7, 3)
        val content =
            ReminderLogic.buildContent(
                streak = 5,
                lastStudyDate = today.minusDays(1),
                today = today,
                pickVariant = { 0 },
            )
        assertEquals("🔥 5일째 — 오늘 이어가면 6일째예요", content.body)
    }

    @Test
    fun `gap of two or more falls to neutral invite without number`() {
        val today = LocalDate.of(2026, 7, 3)
        val content =
            ReminderLogic.buildContent(
                streak = 5,
                lastStudyDate = today.minusDays(2),
                today = today,
                pickVariant = { 0 },
            )
        assertEquals("🔥 오늘 5분 이어가볼까요?", content.body)
    }

    @Test
    fun `pool includes alternate copy at other indices for the same branch`() {
        val today = LocalDate.of(2026, 7, 3)
        val content =
            ReminderLogic.buildContent(
                streak = 5,
                lastStudyDate = today.minusDays(1),
                today = today,
                pickVariant = { 1 },
            )
        assertEquals("🔥 어제 이어서, 오늘도 5분 가볼까요?", content.body)
    }

    @Test
    fun `review variant is appended and selectable when a saved expression is cached`() {
        val today = LocalDate.of(2026, 7, 3)
        val content =
            ReminderLogic.buildContent(
                streak = 5,
                lastStudyDate = today.minusDays(1),
                today = today,
                lastSavedReviewText = "Could I grab a coffee?",
                // branch pool has 3 variants (indices 0-2); index 3 is the review variant appended after.
                pickVariant = { 3 },
            )
        assertEquals("저장한 표현 'Could I grab a coffee?', 오늘 한 번 더 써볼까요?", content.body)
    }

    @Test
    fun `situation variant is appended and selectable when a recommended situation is supplied`() {
        val today = LocalDate.of(2026, 7, 3)
        val content =
            ReminderLogic.buildContent(
                streak = 0,
                lastStudyDate = null,
                today = today,
                recommendedSituationTitle = "카페에서 주문하기",
                // branch pool has 3 variants (indices 0-2); index 3 is the situation variant appended after.
                pickVariant = { 3 },
            )
        assertEquals("오늘은 '카페에서 주문하기' 어때요?", content.body)
    }

    @Test
    fun `milestone overrides the branch pool regardless of streak or gap`() {
        val today = LocalDate.of(2026, 7, 3)
        val content =
            ReminderLogic.buildContent(
                streak = 7,
                lastStudyDate = today.minusDays(5),
                today = today,
                milestoneStreak = 7,
                pickVariant = { 0 },
            )
        assertEquals("🔥 일주일을 채웠어요 — 오늘도 이어가볼까요?", content.body)
    }

    @Test
    fun `milestone body covers every configured threshold`() {
        val today = LocalDate.of(2026, 7, 3)
        val expected =
            mapOf(
                1 to "🔥 어제 1일째를 시작했어요 — 오늘 이어가볼까요?",
                3 to "🔥 3일째까지 왔어요 — 오늘도 이어가볼까요?",
                7 to "🔥 일주일을 채웠어요 — 오늘도 이어가볼까요?",
                14 to "🔥 2주 연속, 대단해요 — 오늘도 가볼까요?",
                30 to "🔥 한 달을 채웠어요 — 오늘도 이어가볼까요?",
            )
        expected.forEach { (threshold, body) ->
            val content =
                ReminderLogic.buildContent(
                    streak = threshold,
                    lastStudyDate = today.minusDays(1),
                    today = today,
                    milestoneStreak = threshold,
                    pickVariant = { 0 },
                )
            assertEquals(body, content.body)
        }
    }

    @Test
    fun `milestone thresholds match the completion screen thresholds`() {
        assertEquals(setOf(1, 3, 7, 14, 30), ReminderLogic.MILESTONE_THRESHOLDS)
    }
```

- [ ] **Step 2: Run the tests to confirm they fail to compile (the new signature does not exist yet)**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReminderLogicTest*'`
Expected: `compileDebugUnitTestKotlin` FAILS — `buildContent` does not have `milestoneStreak`/`lastSavedReviewText`/`recommendedSituationTitle`/`pickVariant` parameters, and `MILESTONE_THRESHOLDS` is unresolved.

- [ ] **Step 3: Implement the pool-based `buildContent`, `MILESTONE_THRESHOLDS`, and `milestoneBody`**

In `ReminderLogic.kt`, add the import and replace the body from `private const val TITLE = "딸깍영어"` down to the end of `buildContent`:

```kotlin
import kotlin.random.Random
```

```kotlin
    private const val TITLE = "딸깍영어"

    /** 완주 화면(gamification-emphasis.md §5)과 동일한 스트릭 임계값. 도달 다음날 리마인더가 축하 문구로 변주된다. */
    val MILESTONE_THRESHOLDS = setOf(1, 3, 7, 14, 30)

    /**
     * body 카피 분기(§5.1 확장). skip-if-studied 로 인해 "오늘 아직 학습 안 한" 사용자에게만 호출된다.
     * - [milestoneStreak] 이 있으면 다른 모든 입력을 무시하고 축하 문구로 즉시 override 한다(희소·1회성이라
     *   랜덤 풀에 섞지 않는다 — 매번 나올 확률로 등장하면 특별함이 옅어진다).
     * - 그 외엔 스트릭 상태로 3분기 base 문구 풀을 고르고, [lastSavedReviewText]/[recommendedSituationTitle]
     *   가 있으면 각각 1개 변형을 풀 끝에 덧붙인 뒤 [pickVariant] 로 하나를 고른다.
     * - [pickVariant] 는 프로덕션에서 `Random.nextInt`, 테스트에서 고정 인덱스를 주입하는 seam이다
     *   (`ReminderScheduler.nowProvider` 와 동일한 스타일).
     */
    fun buildContent(
        streak: Int?,
        lastStudyDate: LocalDate?,
        today: LocalDate,
        milestoneStreak: Int? = null,
        lastSavedReviewText: String? = null,
        recommendedSituationTitle: String? = null,
        pickVariant: (Int) -> Int = { Random.nextInt(it) },
    ): ReminderContent {
        if (milestoneStreak != null) {
            return ReminderContent(title = TITLE, body = milestoneBody(milestoneStreak))
        }
        val branchVariants =
            when {
                streak == null || streak == 0 || lastStudyDate == null ->
                    listOf(
                        "오늘 시작하면 1일째예요",
                        "오늘 5분, 첫 대화 시작해볼까요?",
                        "가볍게 한마디, 오늘 어때요?",
                    )
                ChronoUnit.DAYS.between(lastStudyDate, today) == 1L ->
                    listOf(
                        "🔥 ${streak}일째 — 오늘 이어가면 ${streak + 1}일째예요",
                        "🔥 어제 이어서, 오늘도 5분 가볼까요?",
                        "🔥 ${streak}일째 기록, 오늘도 이어가볼까요?",
                    )
                else ->
                    listOf(
                        "🔥 오늘 5분 이어가볼까요?",
                        "🔥 가볍게 다시 시작해볼까요?",
                        "🔥 오늘 5분이면 충분해요",
                    )
            }
        val pool =
            branchVariants +
                listOfNotNull(
                    lastSavedReviewText?.let { "저장한 표현 '$it', 오늘 한 번 더 써볼까요?" },
                    recommendedSituationTitle?.let { "오늘은 '$it' 어때요?" },
                )
        return ReminderContent(title = TITLE, body = pool[pickVariant(pool.size)])
    }

    /**
     * 마일스톤 축하(gamification-emphasis.md §5 톤 재사용, 결정 #18 임계값). 리마인더는 정의상 "다음날 아직
     * 미방문" 상태에서만 발화하므로, 완주 화면의 즉시 축하와 달리 재참여를 부드럽게 초대하는 톤으로 다시 쓴다.
     */
    private fun milestoneBody(streak: Int): String =
        when (streak) {
            1 -> "🔥 어제 1일째를 시작했어요 — 오늘 이어가볼까요?"
            3 -> "🔥 3일째까지 왔어요 — 오늘도 이어가볼까요?"
            7 -> "🔥 일주일을 채웠어요 — 오늘도 이어가볼까요?"
            14 -> "🔥 2주 연속, 대단해요 — 오늘도 가볼까요?"
            30 -> "🔥 한 달을 채웠어요 — 오늘도 이어가볼까요?"
            else -> "🔥 ${streak}일째, 대단해요 — 오늘도 이어가볼까요?"
        }
```

- [ ] **Step 4: Run the tests to confirm they pass**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReminderLogicTest*'`
Expected: BUILD SUCCESSFUL, all `ReminderLogicTest` cases pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderLogic.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderLogicTest.kt
git commit -m "feat(reminder): pool-based body copy with review/situation/milestone variants"
```

---

## Task 2: `ReminderStore`/`ReminderCache` — milestone + saved-review-text persistence

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/reminder/data/ReminderStore.kt`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderOrchestratorTest.kt` (compile fix only — see Step 3b)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/data/ReminderRepositoryTest.kt`

**Interfaces:**
- Consumes: `ReminderLogic.MILESTONE_THRESHOLDS: Set<Int>` (Task 1).
- Produces: `ReminderCache(lastStudyDate: LocalDate?, streak: Int?, milestoneStreak: Int? = null, lastSavedReviewText: String? = null)`.
- Produces: `ReminderStore.recordSavedReviewText(text: String): Unit` (suspend), `ReminderStore.clearMilestone(): Unit` (suspend).

> **Why `ReminderOrchestratorTest.kt` is touched here, not in Task 3:** `ReminderStore` has a second implementer besides `DataStoreReminderRepository` — `FakeReminderStore`, declared inside `ReminderOrchestratorTest.kt`. Gradle compiles the whole `testDebugUnitTest` source set as one unit, so the moment the `ReminderStore` interface gains the two new abstract methods (Step 3), `FakeReminderStore` stops satisfying the interface and **the entire test module fails to compile** — including `ReminderRepositoryTest`, even though that test doesn't touch `FakeReminderStore` at all. Step 3b below adds trivial no-op stubs to keep the build green; Task 3 then replaces those stubs with real tracking logic (not new overrides — see Task 3's note).

- [ ] **Step 1: Write the failing tests**

Add to `ReminderRepositoryTest.kt`, after the existing `recordSessionCompleted mirrors streak and lastStudyDate into cache` test:

```kotlin
    @Test
    fun `recordSessionCompleted sets milestone cache on a threshold streak`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val repo = newRepo(scope)

            repo.recordSessionCompleted(streak = 7, lastStudyDate = LocalDate.of(2026, 7, 3))

            assertEquals(7, repo.cacheSnapshot().milestoneStreak)
            scope.cancel()
        }

    @Test
    fun `recordSessionCompleted clears milestone cache on a non-threshold streak`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val repo = newRepo(scope)

            repo.recordSessionCompleted(streak = 7, lastStudyDate = LocalDate.of(2026, 7, 3))
            repo.recordSessionCompleted(streak = 8, lastStudyDate = LocalDate.of(2026, 7, 4))

            assertEquals(null, repo.cacheSnapshot().milestoneStreak)
            scope.cancel()
        }

    @Test
    fun `clearMilestone removes the cached milestone`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val repo = newRepo(scope)

            repo.recordSessionCompleted(streak = 3, lastStudyDate = LocalDate.of(2026, 7, 3))
            repo.clearMilestone()

            assertEquals(null, repo.cacheSnapshot().milestoneStreak)
            scope.cancel()
        }

    @Test
    fun `recordSavedReviewText persists into cache snapshot`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val repo = newRepo(scope)

            repo.recordSavedReviewText("Could I grab a coffee?")

            assertEquals("Could I grab a coffee?", repo.cacheSnapshot().lastSavedReviewText)
            scope.cancel()
        }

    @Test
    fun `resetProgressCache also clears the milestone cache`() =
        runTest {
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
            val repo = newRepo(scope)

            repo.recordSessionCompleted(streak = 14, lastStudyDate = LocalDate.of(2026, 7, 3))
            repo.resetProgressCache()

            assertEquals(null, repo.cacheSnapshot().milestoneStreak)
            scope.cancel()
        }
```

- [ ] **Step 2: Run the tests to confirm they fail to compile**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReminderRepositoryTest*'`
Expected: FAILS — `cacheSnapshot().milestoneStreak`/`.lastSavedReviewText` and `repo.clearMilestone()`/`repo.recordSavedReviewText(...)` are unresolved.

- [ ] **Step 3: Extend `ReminderCache`, the `ReminderStore` interface, and `DataStoreReminderRepository`**

In `ReminderStore.kt`, add the import and update `ReminderCache`:

```kotlin
import com.jjundev.oneclickeng.feature.reminder.ReminderLogic
```

```kotlin
/** ReminderWorker 가 오프라인에서 읽는 캐시 스냅샷(§4.1). Firestore 미독 — 이 미러만 신뢰. */
data class ReminderCache(
    val lastStudyDate: LocalDate?,
    val streak: Int?,
    /** B3: 마지막 완주가 마일스톤 임계값([ReminderLogic.MILESTONE_THRESHOLDS])에 닿았으면 그 값, 아니면 null. */
    val milestoneStreak: Int? = null,
    /** B1: 자동 저장된 표현/단어 중 가장 최근 것의 표시 텍스트. */
    val lastSavedReviewText: String? = null,
)
```

Add two new methods to the `ReminderStore` interface, right after `cacheSnapshot()` and before `resetProgressCache()`:

```kotlin
    /** B1(복습): 자동 저장된 표현/단어 중 가장 최근 것을 리마인더 body 후보로 미러링한다(§5.1 확장). */
    suspend fun recordSavedReviewText(text: String)

    /** B3(마일스톤): 다음 리마인더가 축하 문구를 1회 소비한 뒤 정상 분기로 복귀시킨다. */
    suspend fun clearMilestone()
```

Update `DataStoreReminderRepository.recordSessionCompleted` to also set/clear the milestone key:

```kotlin
        override suspend fun recordSessionCompleted(
            streak: Int,
            lastStudyDate: LocalDate,
        ) {
            dataStore.edit { prefs ->
                val count = prefs[KEY_COMPLETED_SESSIONS] ?: 0
                prefs[KEY_COMPLETED_SESSIONS] = count + 1
                prefs[KEY_STREAK_CACHE] = streak
                prefs[KEY_LAST_STUDY_DATE_CACHE] = lastStudyDate.toString()
                if (streak in ReminderLogic.MILESTONE_THRESHOLDS) {
                    prefs[KEY_MILESTONE_STREAK] = streak
                } else {
                    prefs.remove(KEY_MILESTONE_STREAK)
                }
            }
        }
```

Add the two new method implementations right after `recordSessionCompleted`:

```kotlin
        override suspend fun recordSavedReviewText(text: String) {
            dataStore.edit { it[KEY_LAST_SAVED_REVIEW_TEXT] = text }
        }

        override suspend fun clearMilestone() {
            dataStore.edit { it.remove(KEY_MILESTONE_STREAK) }
        }
```

Update `cacheSnapshot()` and `resetProgressCache()`:

```kotlin
        override suspend fun cacheSnapshot(): ReminderCache {
            val prefs = dataStore.data.first()
            return ReminderCache(
                lastStudyDate =
                    prefs[KEY_LAST_STUDY_DATE_CACHE]?.let { iso ->
                        runCatching { LocalDate.parse(iso) }.getOrNull()
                    },
                streak = prefs[KEY_STREAK_CACHE],
                milestoneStreak = prefs[KEY_MILESTONE_STREAK],
                lastSavedReviewText = prefs[KEY_LAST_SAVED_REVIEW_TEXT],
            )
        }

        override suspend fun resetProgressCache() {
            dataStore.edit { prefs ->
                prefs.remove(KEY_STREAK_CACHE)
                prefs.remove(KEY_LAST_STUDY_DATE_CACHE)
                prefs.remove(KEY_MILESTONE_STREAK)
            }
        }
```

Add the two new DataStore keys to the companion object:

```kotlin
            val KEY_MILESTONE_STREAK = intPreferencesKey("reminder_milestone_streak")
            val KEY_LAST_SAVED_REVIEW_TEXT = stringPreferencesKey("reminder_last_saved_review_text")
```

- [ ] **Step 3b: Stub the two new methods on `FakeReminderStore` so the test module keeps compiling**

In `ReminderOrchestratorTest.kt`, add these two trivial overrides to `FakeReminderStore` (near its other overrides — exact placement doesn't matter, Task 3 will replace their bodies with real tracking logic):

```kotlin
    override suspend fun recordSavedReviewText(text: String) = Unit

    override suspend fun clearMilestone() = Unit
```

- [ ] **Step 4: Run the tests to confirm they pass**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReminderRepositoryTest*' --tests '*ReminderOrchestratorTest*'`
Expected: BUILD SUCCESSFUL — `ReminderRepositoryTest`'s new cases pass, and `ReminderOrchestratorTest` compiles and its pre-existing cases still pass (Task 3 adds the new orchestrator-level tests).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/reminder/data/ReminderStore.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/data/ReminderRepositoryTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderOrchestratorTest.kt
git commit -m "feat(reminder): persist milestone and saved-review-text cache fields"
```

---

## Task 3: `ReminderOrchestrator` — review-text delegation + milestone consumption

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderOrchestrator.kt`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderWorkerTest.kt`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModelTest.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderOrchestratorTest.kt`

**Interfaces:**
- Consumes: `ReminderStore.recordSavedReviewText`/`clearMilestone` (Task 2), `ReminderCache.milestoneStreak` (Task 2).
- Produces: `ReminderOrchestrator.recordSavedReviewText(text: String): Unit` (suspend) — Task 5 (`SummaryCoordinator`) calls this.

- [ ] **Step 1: Write the failing tests**

In `ReminderOrchestratorTest.kt`, `FakeReminderStore` already has the two stub overrides added in Task 2 Step 3b (`recordSavedReviewText`/`clearMilestone`, both `= Unit`). **Replace those two stub bodies** (do not add new overrides — that would be a duplicate-declaration compile error) with real tracking state, and add the tracking fields near `completionCalls`:

```kotlin
    val reviewTexts = mutableListOf<String>()
    var clearMilestoneCalls = 0

    override suspend fun recordSavedReviewText(text: String) {
        reviewTexts += text
    }

    override suspend fun clearMilestone() {
        clearMilestoneCalls += 1
        cacheValue = cacheValue.copy(milestoneStreak = null)
    }
```

Add these tests after `record session completion delegates to store`:

```kotlin
    @Test
    fun `record saved review text delegates to store`() =
        runTest {
            val store = FakeReminderStore()

            orchestrator(store).recordSavedReviewText("Could I grab a coffee?")

            assertEquals(listOf("Could I grab a coffee?"), store.reviewTexts)
        }

    @Test
    fun `due reminder fire clears a cached milestone after posting`() =
        runTest {
            val today = LocalDate.now(ReminderLogic.KST)
            val store =
                FakeReminderStore(
                    configValue = ReminderConfig(enabled = true, hour = 20, minute = 0),
                    cacheValue =
                        ReminderCache(
                            lastStudyDate = today.minusDays(1),
                            streak = 7,
                            milestoneStreak = 7,
                        ),
                )
            val notifications = RecordingNotificationSink()

            orchestrator(store, notifications = notifications).runDueReminder()

            assertEquals(1, notifications.posts.size)
            assertEquals(1, store.clearMilestoneCalls)
        }

    @Test
    fun `due reminder skip does not clear a cached milestone`() =
        runTest {
            val today = LocalDate.now(ReminderLogic.KST)
            val store =
                FakeReminderStore(
                    configValue = ReminderConfig(enabled = true, hour = 20, minute = 0),
                    cacheValue = ReminderCache(lastStudyDate = today, streak = 7, milestoneStreak = 7),
                )

            orchestrator(store).runDueReminder()

            assertEquals(0, store.clearMilestoneCalls)
        }

    @Test
    fun `due reminder fire without a cached milestone does not call clear`() =
        runTest {
            val today = LocalDate.now(ReminderLogic.KST)
            val store =
                FakeReminderStore(
                    configValue = ReminderConfig(enabled = true, hour = 20, minute = 0),
                    cacheValue = ReminderCache(lastStudyDate = today.minusDays(1), streak = 3),
                )

            orchestrator(store).runDueReminder()

            assertEquals(0, store.clearMilestoneCalls)
        }
```

- [ ] **Step 2: Run the tests to confirm they fail to compile**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReminderOrchestratorTest*'`
Expected: FAILS — `orchestrator(store).recordSavedReviewText(...)` doesn't compile because `ReminderOrchestrator` has no such method yet, and the new tests' assertions on `store.clearMilestoneCalls`/`store.reviewTexts` reference tracking fields Step 1 just added to `FakeReminderStore`, but `runDueReminder()` in `DefaultReminderOrchestrator` never calls `store.clearMilestone()` yet — so even once it compiles, `due reminder fire clears a cached milestone after posting` would fail on the `assertEquals(1, store.clearMilestoneCalls)` line.

- [ ] **Step 3: Add `recordSavedReviewText` to the interface + implementation, and clear milestone on fire**

In `ReminderOrchestrator.kt`, add to the `ReminderOrchestrator` interface, right after `recordSessionCompleted` and before `clearProgressCache`:

```kotlin
    /** B1: 자동 저장된 표현/단어 텍스트를 리마인더 body 후보로 넘긴다. */
    suspend fun recordSavedReviewText(text: String)
```

Add the implementation in `DefaultReminderOrchestrator`, right after `recordSessionCompleted`'s implementation and before `clearProgressCache`'s:

```kotlin
        override suspend fun recordSavedReviewText(text: String) {
            store.recordSavedReviewText(text)
        }
```

Replace `runDueReminder`'s body to clear a consumed milestone on both fire paths:

```kotlin
        override suspend fun runDueReminder(): ReminderRunResult {
            val current = store.currentConfig()
            if (!current.enabled) return ReminderRunResult.DisabledNoOp

            val cache = store.cacheSnapshot()
            val today = LocalDate.now(ReminderLogic.KST)
            val result =
                when (ReminderLogic.decideFire(cache.lastStudyDate, today)) {
                    ReminderLogic.FireDecision.SKIP_STUDIED_TODAY -> {
                        analytics.fireSkipped(ReminderSkipReason.STUDIED_TODAY)
                        ReminderRunResult.SkippedStudiedToday
                    }
                    ReminderLogic.FireDecision.FIRE_CACHE_MISS -> {
                        analytics.fireSkipped(ReminderSkipReason.CACHE_MISS)
                        notificationSink.post(cache, today)
                        if (cache.milestoneStreak != null) store.clearMilestone()
                        ReminderRunResult.FiredCacheMiss
                    }
                    ReminderLogic.FireDecision.FIRE -> {
                        notificationSink.post(cache, today)
                        if (cache.milestoneStreak != null) store.clearMilestone()
                        ReminderRunResult.Fired
                    }
                }
            schedule.schedule(current.hour, current.minute)
            return result
        }
```

Fix the two other `ReminderOrchestrator` implementers so the module compiles:

In `ReminderWorkerTest.kt`, add to `RecordingReminderOrchestrator`, right after `recordSessionCompleted`:

```kotlin
    override suspend fun recordSavedReviewText(text: String) = Unit
```

In `SettingsViewModelTest.kt`, add to `FakeReminderOrchestrator`, right after `recordSessionCompleted`:

```kotlin
    override suspend fun recordSavedReviewText(text: String) = Unit
```

- [ ] **Step 4: Run the tests to confirm they pass**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReminderOrchestratorTest*' --tests '*ReminderWorkerTest*' --tests '*SettingsViewModelTest*'`
Expected: BUILD SUCCESSFUL, all three test classes pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderOrchestrator.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderOrchestratorTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderWorkerTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsViewModelTest.kt
git commit -m "feat(reminder): orchestrator delegates saved review text, consumes milestone on fire"
```

---

## Task 4: `ReminderNotifier` — wire the situation catalog and the full content call

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderNotifier.kt`

**Interfaces:**
- Consumes: `ReminderLogic.buildContent(...)` (Task 1, all new params), `ReminderCache.milestoneStreak`/`.lastSavedReviewText` (Task 2), `com.jjundev.oneclickeng.feature.home.topic.TopicCatalog.recommended(dayIndex: Long, refresh: Int = 0, count: Int = 6): List<Topic>` (existing, already installed by `OceApp.onCreate()` before any `Application`-scoped component — including this `WorkManager` worker — can run).

`ReminderNotifier` has no dedicated unit test in this codebase today (it is a thin `Context`/`NotificationManager` adapter, same as `ReminderScheduler` — both are exercised only indirectly through `ReminderWorkerTest`'s fake orchestrator and manual on-device verification). This task's correctness is covered by Task 1-3's tests plus a full-module regression run.

- [ ] **Step 1: Update `post()` to resolve the situation title and pass every new parameter**

In `ReminderNotifier.kt`, add the import:

```kotlin
import com.jjundev.oneclickeng.feature.home.topic.TopicCatalog
```

Replace the body of `post()` from `ensureChannel()` through the `val notification = ...` block:

```kotlin
            ensureChannel()
            val situationTitle =
                TopicCatalog.recommended(dayIndex = today.toEpochDay(), count = 1).firstOrNull()?.titleKo
            val content =
                ReminderLogic.buildContent(
                    streak = cache.streak,
                    lastStudyDate = cache.lastStudyDate,
                    today = today,
                    milestoneStreak = cache.milestoneStreak,
                    lastSavedReviewText = cache.lastSavedReviewText,
                    recommendedSituationTitle = situationTitle,
                )
            val notification =
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_local_fire_department)
                    .setContentTitle(content.title)
                    .setContentText(content.body)
                    .setColor(ContextCompat.getColor(context, R.color.reminder_accent))
                    .setContentIntent(homePendingIntent())
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
```

- [ ] **Step 2: Run the full reminder module test suite to confirm no regressions**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*.feature.reminder.*'`
Expected: BUILD SUCCESSFUL, every test under `feature.reminder` (logic, repository, orchestrator, worker, permission logic) passes.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/reminder/ReminderNotifier.kt
git commit -m "feat(reminder): resolve the day's recommended situation into the notification body"
```

---

## Task 5: `SummaryCoordinator` — mirror auto-saved expressions/words into the reminder cache

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinator.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinatorTest.kt`

**Interfaces:**
- Consumes: `ReminderOrchestrator.recordSavedReviewText(text: String)` (Task 3).
- Produces: nothing new consumed by later tasks — this is the last production-code task.

- [ ] **Step 1: Write the failing test**

In `SummaryCoordinatorTest.kt`, add these imports:

```kotlin
import com.jjundev.oneclickeng.feature.reminder.ReminderOrchestrator
import com.jjundev.oneclickeng.feature.reminder.ReminderPromptDecision
import com.jjundev.oneclickeng.feature.reminder.ReminderRunResult
import com.jjundev.oneclickeng.feature.reminder.data.ReminderConfig
import java.time.LocalDate
```

Add a new fake near the other fakes (after `FakeStudytimeRepository`, before `GatedSummarySaveSettingsRepository`):

```kotlin
private class FakeReminderOrchestrator : ReminderOrchestrator {
    val reviewTexts = mutableListOf<String>()

    override val config: Flow<ReminderConfig> = MutableStateFlow(ReminderConfig.DISABLED)

    override suspend fun evaluateOptInPrompt(): ReminderPromptDecision = ReminderPromptDecision.DoNotShow

    override suspend fun acceptOptIn() = Unit

    override suspend fun dismissOptIn() = Unit

    override suspend fun enableReminder() = Unit

    override suspend fun disableReminder() = Unit

    override suspend fun setReminderTime(
        hour: Int,
        minute: Int,
    ) = Unit

    override suspend fun markPermissionAsked() = Unit

    override suspend fun repairSchedule() = Unit

    override suspend fun handleTimezoneChanged() = Unit

    override suspend fun runDueReminder(): ReminderRunResult = ReminderRunResult.DisabledNoOp

    override suspend fun recordSessionCompleted(
        streak: Int,
        lastStudyDate: LocalDate,
    ) = Unit

    override suspend fun recordSavedReviewText(text: String) {
        reviewTexts += text
    }

    override suspend fun clearProgressCache() = Unit
}
```

Update the `coordinator()` factory to accept and thread the new dependency:

```kotlin
    @Suppress("LongParameterList") // 코디네이터 seam 을 그대로 반영하는 테스트 팩토리 — 기본값 오버라이드용.
    private fun coordinator(
        scope: CoroutineScope,
        stream: FakeSummaryStream,
        bookmarks: FakeBookmarkSource = FakeBookmarkSource(),
        ledger: FakeLedger = FakeLedger(),
        savedCards: FakeSavedCardRepository = FakeSavedCardRepository(),
        saveSettings: SummarySaveSettingsRepository = FakeSummarySaveSettingsRepository(),
        studytime: FakeStudytimeRepository = FakeStudytimeRepository(),
        reminderOrchestrator: FakeReminderOrchestrator = FakeReminderOrchestrator(),
    ) = SummaryCoordinator(
        stream,
        store(),
        bookmarks,
        ledger,
        savedCards,
        saveSettings,
        studytime,
        reminderOrchestrator,
        scope,
    )
```

Add the test right after `expression and word cards are auto-saved for every index when save-by-default is enabled`:

```kotlin
    @Test
    fun `auto-saving an expression mirrors its corrected text into the reminder orchestrator`() =
        runTest {
            val stream = FakeSummaryStream()
            val saveSettings = FakeSummarySaveSettingsRepository(initial = true)
            val reminderOrchestrator = FakeReminderOrchestrator()
            val coordinator =
                coordinator(
                    coordScope(),
                    stream,
                    saveSettings = saveSettings,
                    reminderOrchestrator = reminderOrchestrator,
                )

            coordinator.begin()
            runCurrent()
            stream.push(SummaryEvent.Card.Expression(listOf(expressionItem())))
            runCurrent()

            assertEquals(listOf("Could I grab a coffee?"), reminderOrchestrator.reviewTexts)
        }
```

- [ ] **Step 2: Run the test to confirm it fails to compile**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SummaryCoordinatorTest*'`
Expected: FAILS — `SummaryCoordinator`'s constructor does not yet accept a `ReminderOrchestrator` argument.

- [ ] **Step 3: Inject `ReminderOrchestrator` and mirror the first auto-saved item's text**

In `SummaryCoordinator.kt`, add the import:

```kotlin
import com.jjundev.oneclickeng.feature.reminder.ReminderOrchestrator
```

Add the constructor parameter, right after `studytime` and before `scope`:

```kotlin
        private val studytime: StudytimeRepository,
        private val reminderOrchestrator: ReminderOrchestrator,
        private val scope: CoroutineScope,
    ) {
```

Update `autoSaveExpressions` and `autoSaveWords` to mirror the first item's display text (fire-and-forget, matching this file's existing pattern for suspend side-effects called from non-suspend event handlers):

```kotlin
        private fun autoSaveExpressions(items: List<ExpressionCard>) {
            val id = sessionId ?: return
            savedExprIndices = items.indices.toSet()
            items.forEachIndexed { index, card ->
                savedCardRepository.save(SavedCardId.forSummary(id, CardType.EXPRESSION, index), card.toSavedCard())
            }
            items.firstOrNull()?.let { first ->
                scope.launch { reminderOrchestrator.recordSavedReviewText(first.after) }
            }
        }

        /** [autoSaveExpressions] 와 동형(WORD). */
        private fun autoSaveWords(items: List<WordCard>) {
            val id = sessionId ?: return
            savedWordIndices = items.indices.toSet()
            items.forEachIndexed { index, card ->
                savedCardRepository.save(SavedCardId.forSummary(id, CardType.WORD, index), card.toSavedCard())
            }
            items.firstOrNull()?.let { first ->
                scope.launch { reminderOrchestrator.recordSavedReviewText(first.en) }
            }
        }
```

- [ ] **Step 4: Run the test to confirm it passes, then run the full summary + reminder suites for regressions**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SummaryCoordinatorTest*' --tests '*.feature.reminder.*'`
Expected: BUILD SUCCESSFUL, all tests pass — including the pre-existing `expression and word cards are auto-saved...` test (unaffected, since it doesn't assert on `reminderOrchestrator`).

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinator.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryCoordinatorTest.kt
git commit -m "feat(summary): mirror auto-saved expressions/words into the reminder review cache"
```

---

## Task 6: Documentation — update the reminder content model's source of truth

**Files:**
- Modify: `docs/ux/notification-reminder.md`

**Interfaces:**
- None (documentation only).

- [ ] **Step 1: Replace §5.1 with the pool/review/situation/milestone model**

In `docs/ux/notification-reminder.md`, replace the entire `### 5.1 body 카피 (캐시 분기, 미래형 초대만)` section (from its heading through the `> **후속:** ...` line) with:

```markdown
### 5.1 body 카피 (캐시 분기 + 변주 풀, 미래형 초대만)

알림은 skip-if-studied로 인해 "오늘 아직 학습 안 한 사용자"에게만 발화된다. body는 `ReminderLogic.buildContent`
가 고른다: 마일스톤 override가 없으면 스트릭 상태로 3개 base 분기 중 하나를 고르고, 각 분기는 고정 1개가 아니라
**변주 풀**(2~3개)을 가진다. 여기에 조건부로 복습/상황 변형이 풀 끝에 덧붙고, 최종적으로 하나를 무작위로 뽑는다
(발화마다 달라져 반복 노출로 인한 무뎌짐을 줄인다).

| 조건 | title | body 풀 |
|---|---|---|
| `streakCache == 0` 또는 신규(캐시 부재) | `딸깍영어` | `오늘 시작하면 1일째예요` / `오늘 5분, 첫 대화 시작해볼까요?` / `가볍게 한마디, 오늘 어때요?` |
| `lastStudyDate == 어제` (정상 연속, gap==1) | `딸깍영어` | `🔥 ${N}일째 — 오늘 이어가면 ${N+1}일째예요` / `🔥 어제 이어서, 오늘도 5분 가볼까요?` / `🔥 ${N}일째 기록, 오늘도 이어가볼까요?` |
| `gap >= 2` (유예/리셋 임박) | `딸깍영어` | `🔥 오늘 5분 이어가볼까요?` / `🔥 가볍게 다시 시작해볼까요?` / `🔥 오늘 5분이면 충분해요` |

`${N+1}` 미래 숫자는 gap==1일 때만 정확하다(§5.1 기존 근거 유지 — gap>=2에서 오늘 학습해도 streak가 평탄 유지라
"+1"이 거짓이 되므로 숫자 없는 중립 초대만 쓴다).

**조건부 추가 변형(위 풀에 append, 스트릭 분기와 무관하게 동일한 두 항목이 후보에 들어간다):**

| 조건 | body |
|---|---|
| 캐시에 최근 자동 저장된 표현/단어가 있음(B1) | `저장한 표현 '{text}', 오늘 한 번 더 써볼까요?` |
| `TopicCatalog.recommended(dayIndex=오늘)` 가 항상 반환하는 로컬 상황 1개(B2) | `오늘은 '{situation}' 어때요?` |

**마일스톤 override(B3, 다른 모든 입력을 무시하고 우선한다):** 마지막 완주 스트릭이
`gamification-emphasis.md §5`(결정 #18)와 같은 임계값 **1·3·7·14·30일**에 닿으면, 그 값을
`ReminderStore`(`milestoneStreak` 캐시)에 남겨 **다음 리마인더 발화 1회에 한해** 아래 문구로 override한다.
소비 즉시 캐시를 지워 그 다음 날부터는 다시 일반 분기로 돌아간다.

| 임계 | body |
|---|---|
| 1일 | `🔥 어제 1일째를 시작했어요 — 오늘 이어가볼까요?` |
| 3일 | `🔥 3일째까지 왔어요 — 오늘도 이어가볼까요?` |
| 7일 | `🔥 일주일을 채웠어요 — 오늘도 이어가볼까요?` |
| 14일 | `🔥 2주 연속, 대단해요 — 오늘도 가볼까요?` |
| 30일 | `🔥 한 달을 채웠어요 — 오늘도 이어가볼까요?` |

세션 완료 즉시(같은 화면에서 이미 §5 카피 변주를 봄)가 아니라 **다음날 미방문 리마인더에 얹는** 이유: 완주
화면에서 이미 본 축하를 그 자리에서 또 push하는 것은 재참여 가치가 없다 — 재참여 알림은 정의상 사용자가 앱
밖에 있을 때 가치가 있다.

> 이모지 `🔥`(U+1F525, Unicode 6.0)는 minSdk 26(Android 8) 기본 폰트에 포함되어 `EmojiCompat` 없이 전 기기 렌더링된다.
> `ReminderLogic.buildContent` 는 여전히 Android/코루틴 의존 없는 순수 함수다 — `TopicCatalog` 조회(B2)는
> `ReminderNotifier`(Android 경계)가 수행해 문자열로 넘기고, 저장된 표현 텍스트(B1)는 `ReminderStore` 캐시가
> `SummaryCoordinator`의 자동 저장 훅에서 미러링해 넘긴다.
```

- [ ] **Step 2: No test to run — documentation only. Verify the file renders (no broken markdown table syntax) by reading it back.**

- [ ] **Step 3: Commit**

```bash
git add docs/ux/notification-reminder.md
git commit -m "docs(reminder): document the pool/review/situation/milestone content model"
```

---

## Self-Review Notes (for the plan author, not a task)

- **Spec coverage:** A (문구 다양화) → Task 1's branch pools. B1 (복습) → Tasks 1/2/3/5. B2 (상황 추천) → Tasks 1/4 (no new storage — `TopicCatalog` is already local and static). B3 (마일스톤) → Tasks 1/2/3, using the confirmed thresholds and the confirmed "fold into next day's reminder" timing.
- **Global Constraints honored:** no new channel/toggle/trigger touched in any task; every new `ReminderCache` field defaults to `null`; `ReminderLogic` never imports Android/`TopicCatalog` directly.
- **Compile-breakage sweep:** every existing implementer of `ReminderOrchestrator` (`ReminderWorkerTest`, `SettingsViewModelTest`, and the new one in `SummaryCoordinatorTest`) is updated in the same task that changes that interface (Task 3). `ReminderStore`'s only other implementer, `ReminderOrchestratorTest`'s `FakeReminderStore`, is stubbed in Task 2 Step 3b (the task that actually changes the `ReminderStore` interface) and then enriched with real tracking logic in Task 3 — this two-step handling exists specifically because `FakeReminderStore` lives in a different file/task than the interface it implements, and an auto-review pass caught the original draft leaving it stubbed-but-uncompiling for one task longer than intended.
