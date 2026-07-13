# Floating Bottom Navigation Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Render the three-tab navigation as the prototype's floating overlay, while keeping each tab's final content and snackbar reachable above it.

**Architecture:** Replace the main-tab shell's Scaffold bottom-bar reservation with an internal MainTabsOverlay composition: tab content fills the viewport and OceBottomNav is aligned over its bottom edge. Centralize the prototype's 104dp trailing clearance in OceBottomNavDefaults, then consume it in the permanent-tab scroll surfaces and in Records/Settings snackbar hosts. Routes, selection, and full-screen flows stay unchanged.

**Tech Stack:** Kotlin, Jetpack Compose, Navigation Compose, Robolectric Compose UI tests, Roborazzi

## Global Constraints

- Prototype Flow is the realization source of truth for this change's layout relationship: nav overlays the permanent-tab viewport and Home, Records, and Settings scroll surfaces reserve 104px at the bottom. Existing OceBottomNav visual styling is already in scope and remains unchanged.
- Only Home, Records, and Settings show the nav. Onboarding, generating, dialogue, feedback, summary, limit gate, and notification flows remain navigation-free.
- Preserve OceBottomNav routes, selection semantics, icons, and state-restoring navigation.
- Do not add dependencies or raw color literals. Use OceTheme tokens and scripts/verify-android.sh.
- Roborazzi output under android/app/build/outputs/roborazzi/ is gitignored manual parity output, not committed golden assertions.

---

## File Structure

| File | Responsibility after this change |
|---|---|
| android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt | Own MainTabsOverlay and layer full-height tab content beneath the nav. |
| android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/OceBottomNav.kt | Export overlayContentBottomPadding. |
| android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/TabScreenScaffold.kt | Apply clearance to the Records LazyColumn. |
| android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt | Apply clearance to Home's LazyColumn. |
| android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreen.kt | Lift its snackbar and revise obsolete Scaffold-inset KDoc. |
| android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt | Reuse clearance instead of local 104.dp and lift snackbar. |
| android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/root/MainTabsOverlayTest.kt | Test that content extends beneath the nav. |
| android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreenScreenshotTest.kt | Render Home with overlay geometry for manual prototype comparison. |
| docs/ui/01-foundations.md | Document the floating overlay and its 104dp clearance. |

## Decision Checkpoint

No execution decision remains. The prototype determines overlay placement and clearance; existing tab scope and snackbar policy determine the rest.

### Task 1: Convert the permanent-tab shell to a floating overlay and preserve clearance

**Files:**

- Create: android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/root/MainTabsOverlayTest.kt
- Modify: android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt
- Modify: android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/OceBottomNav.kt
- Modify: android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/TabScreenScaffold.kt
- Modify: android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt
- Modify: android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreen.kt
- Modify: android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt
- Modify: android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreenScreenshotTest.kt
- Modify: docs/ui/01-foundations.md

**Interfaces:**

- Consumes: OceNavHost(navController, onStartSession, onResume, modifier), OceBottomNav(navController, modifier), and OneClickSnackbarHost(hostState, modifier, bottomInset).
- Produces: internal fun MainTabsOverlay(navController: NavHostController, isOnline: Boolean, modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) and OceBottomNavDefaults.overlayContentBottomPadding: Dp.

- [ ] **Step 1: Write the failing overlay geometry test**

Create android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/root/MainTabsOverlayTest.kt:

~~~kotlin
package com.jjundev.oneclickeng.ui.root

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val MAIN_TABS_CONTENT_TAG = "main_tabs_content"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class MainTabsOverlayTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tab_content_extends_behind_floating_navigation() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                val navController = rememberNavController()
                MainTabsOverlay(navController = navController, isOnline = true) { contentModifier ->
                    Box(
                        modifier =
                            contentModifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .testTag(MAIN_TABS_CONTENT_TAG),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(MAIN_TABS_CONTENT_TAG).assertIsDisplayed()
        val contentBottom =
            composeRule.onNodeWithTag(MAIN_TABS_CONTENT_TAG).getUnclippedBoundsInRoot().bottom
        val navLabelTop =
            composeRule
                .onNodeWithText("학습", useUnmergedTree = true)
                .getUnclippedBoundsInRoot()
                .top

        assertTrue(
            "Floating navigation must overlap full-height tab content; contentBottom=${contentBottom}, navLabelTop=${navLabelTop}",
            navLabelTop < contentBottom,
        )
    }
}
~~~

- [ ] **Step 2: Run the test to verify it fails**

Run:

~~~bash
scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.ui.root.MainTabsOverlayTest'
~~~

Expected: compilation fails with Unresolved reference: MainTabsOverlay in the new test.

- [ ] **Step 3: Implement the overlay seam, shared clearance, and tab consumers**

1. In AppRoot.kt, remove the Scaffold import; add fillMaxWidth, weight, and androidx.navigation.NavHostController. Replace the terminal Scaffold block in MainTabsScaffold with:

~~~kotlin
    MainTabsOverlay(
        navController = navController,
        isOnline = isOnline,
    ) { contentModifier ->
        OceNavHost(
            navController = navController,
            onStartSession = onStartSession,
            onResume = onResume,
            modifier = contentModifier,
        )
    }
~~~

Immediately after MainTabsScaffold, add:

~~~kotlin
@Composable
internal fun MainTabsOverlay(
    navController: NavHostController,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            OneClickOfflineBanner(visible = !isOnline)
            content(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
        OceBottomNav(
            navController = navController,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
~~~

Update MainTabsScaffold KDoc: MainTabsOverlay owns the floating overlay; OceNavHost fills the tab viewport and nav aligns over its bottom edge.

2. In OceBottomNav.kt, import Dp and dp and add before OceBottomNav:

~~~kotlin
/**
 * Permanent-tab floating-navigation dimensions derived from Prototype Flow.
 * Scroll surfaces use this trailing clearance so their final item can move above
 * the bar even though the bar itself overlays the viewport.
 */
object OceBottomNavDefaults {
    val overlayContentBottomPadding: Dp = 104.dp
}
~~~

3. In TabScreenScaffold.kt, import PaddingValues and OceBottomNavDefaults, then add this argument to its LazyColumn:

~~~kotlin
        contentPadding =
            PaddingValues(bottom = OceBottomNavDefaults.overlayContentBottomPadding),
~~~

4. In HomeScreen.kt, import PaddingValues and OceBottomNavDefaults, then add the same argument to HomeContent's LazyColumn:

~~~kotlin
        contentPadding =
            PaddingValues(bottom = OceBottomNavDefaults.overlayContentBottomPadding),
~~~

5. In RecordsScreen.kt, import OceBottomNavDefaults. Replace the KDoc sentence saying AppRoot Scaffold absorbs the inset with:

~~~kotlin
 * [Box] 에 [OneClickSnackbarHost] 로 얹는다. 플로팅 BottomNav 가 뷰포트를 덮으므로 스낵바는
 * [OceBottomNavDefaults.overlayContentBottomPadding] 만큼 위로 띄운다.
~~~

Add to its OneClickSnackbarHost call:

~~~kotlin
            bottomInset = OceBottomNavDefaults.overlayContentBottomPadding,
~~~

6. In SettingsScreen.kt, import OceBottomNavDefaults; replace the current LazyColumn contentPadding with:

~~~kotlin
            contentPadding =
                PaddingValues(
                    top = 8.dp,
                    bottom = OceBottomNavDefaults.overlayContentBottomPadding,
                ),
~~~

Add to its OneClickSnackbarHost call:

~~~kotlin
            bottomInset = OceBottomNavDefaults.overlayContentBottomPadding,
~~~

7. In HomeScreenScreenshotTest.kt, import Box. In home_light_grid(), home_light_resume_nav(), and private capture(), replace the Column that makes content and OceBottomNav siblings with a Box. Content or NavHost must use Modifier.fillMaxSize(), never weight(1f), and the final child is:

~~~kotlin
                    OceBottomNav(
                        navController = nav,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
~~~

The full root layout in home_light_resume_nav() is:

~~~kotlin
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    NavHost(
                        navController = nav,
                        startDestination = "home",
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        composable("home") {
                            HomeContent(
                                state =
                                    HomeUiState(
                                        studyTimeLabel = "오늘 0분",
                                        streak = 7,
                                        isOnline = true,
                                        hasResume = true,
                                        resumeTopic = "카페에서 주문하기",
                                        resumeTurn = 2,
                                        resumeTotalTurns = 5,
                                        situations = sampleSituations,
                                    ),
                                onStartLearning = {},
                                onResumeContinue = {},
                                onResumeStartNew = {},
                                onViewRecords = {},
                                onOfflineBlocked = {},
                                reduceMotion = true,
                            )
                        }
                    }
                    OceBottomNav(
                        navController = nav,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
~~~

8. In docs/ui/01-foundations.md, replace decisions 1 and 2 under 결정 (rev2 확정) with:

~~~markdown
1. **골격·거터** — 상시 3탭 셸은 Compose Box 오버레이로 구성한다. 화면 가로 거터 20dp(space-xl), 섹션 세로 갭 24dp(space-section-gap), 액션 버튼 갭 12dp(space-action-gap), 로딩 영역 40dp(space-loading-padding). elevation 0 + border.hairline 기조.
2. **BottomNav 노출·배치 범위** — 학습·기록·설정 3탭에만 OceBottomNav를 보이며(첫 탭 라벨은 학습), Prototype Flow처럼 화면 하단 위에 플로팅 오버레이한다. OceNavHost는 바 아래까지 전체 뷰포트를 차지하고, 홈·기록·설정의 스크롤 끝과 기록/설정 스낵바는 OceBottomNavDefaults.overlayContentBottomPadding = 104dp만큼 위로 피한다. 온보딩·대화·피드백시트·요약·한도게이트·리마인더는 내비 없는 전체화면/오버레이. 컴포넌트는 type token(tabActive 13sp Bold / tabInactive 11sp), elevation-nav, 생성 번들의 BottomNav 실현을 유지한다.
~~~

- [ ] **Step 4: Run focused tests and record prototype-comparison screenshots**

Run:

~~~bash
scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.ui.root.MainTabsOverlayTest' --tests 'com.jjundev.oneclickeng.feature.home.HomeScreenScreenshotTest' -Proborazzi.record
~~~

Expected: BUILD SUCCESSFUL; the geometry test passes and Home PNGs are recorded.

Open android/app/build/outputs/roborazzi/home_light_resume_nav.png beside prototype/screenshot/home.png. Confirm nav sits over the viewport, content continues behind it, the final item can scroll clear using 104dp trailing padding, and tab labels/selection appearance are unchanged.

- [ ] **Step 5: Run repository verification**

Run:

~~~bash
scripts/verify-android.sh
~~~

Expected: BUILD SUCCESSFUL for detekt, debug android-test compilation, debug unit tests, and release unit tests. Do not run ktlintMainSourceSetCheck; repository documentation records an unrelated existing violation in DialogueTurnScreen.kt.

- [ ] **Step 6: Inspect change scope**

Run:

~~~bash
git diff --check
git diff -- android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/OceBottomNav.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/TabScreenScaffold.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreen.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/root/MainTabsOverlayTest.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreenScreenshotTest.kt docs/ui/01-foundations.md
~~~

Expected: no whitespace errors. Only the tab-shell overlay, shared clearance, snackbar lift, targeted tests, screenshot harness, and foundation specification change; routes, options, session/onboarding destinations, and OceBottomNav item semantics do not.

- [ ] **Step 7: Commit**

~~~bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/OceBottomNav.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/foundation/TabScreenScaffold.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreen.kt android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/root/MainTabsOverlayTest.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreenScreenshotTest.kt docs/ui/01-foundations.md
git commit -m "fix: overlay floating bottom navigation"
~~~

Expected: one commit containing overlay shell, clearance, tests, screenshot harness update, and foundation-spec correction. Do not stage Roborazzi PNGs.

## Self-Review

- **Spec coverage:** Covers prototype overlay, 104px clearance, three-tab scope, and snackbar relationship.
- **Placeholder scan:** No unfinished markers, deferred implementation, or unspecified tests remain.
- **Type consistency:** MainTabsOverlay receives NavHostController, supplies Modifier to its slot, and production gives that modifier to OceNavHost. Every clearance consumer uses OceBottomNavDefaults.overlayContentBottomPadding: Dp.

## Automatic Plan Review

Skipped: this plan has one independently testable task, so the writing-plans policy does not require a reviewer subagent.

## Execution Handoff

Plan complete and saved to docs/plans/2026-07-13-floating-bottom-nav-overlay.md. Two execution options:

1. **Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

2. **Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
