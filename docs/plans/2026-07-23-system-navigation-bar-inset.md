# System Navigation Bar Inset Handling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent the Android 3-button system navigation bar from covering the bottom of the permanent-tab screens while preserving the current gesture-mode rendering.

**Architecture:** Keep `enableEdgeToEdge()` and the existing floating `OceBottomNav` overlay. Extend the permanent-tab shell boundary in `MainTabsOverlay` with a bottom inset derived from `WindowInsets.navigationBars`, so both the tab content viewport and floating navigation are laid out above the system navigation bar. Preserve the existing `OceBottomNavDefaults.overlayContentBottomPadding` clearance, which handles overlap with the app's own floating navigation rather than the OS bar.

**Tech Stack:** Kotlin, Jetpack Compose Foundation window insets, Navigation Compose, Robolectric Compose UI tests, `scripts/verify-android.sh`

## Global Constraints

- Gesture mode must retain the current layout; when the system navigation-bar inset is zero/minimal, no additional visible bottom gap should be introduced.
- Navigation mode must keep the lowest app content and the floating `OceBottomNav` above the OS navigation bar; the OS bar must not occlude either layer.
- Apply the fix only to the permanent three-tab shell (`Home`, `Records`, and `Settings`). Onboarding, session, sheets, summary, gates, and other navigation-free flows remain unchanged.
- Preserve the existing edge-to-edge setup, tab routes/semantics, floating-nav geometry, and 104dp app-navigation clearance.
- Do not add dependencies or raw color literals. Use the existing `WindowInsets` APIs, `Dp` seam, and `OceTheme`/existing layout constants.
- Verify Android changes with `scripts/verify-android.sh`; do not commit generated build or screenshot output.

---

## File Structure

| File | Responsibility after this change |
|---|---|
| `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt` | Read the OS navigation-bar inset at the permanent-tab shell boundary and reserve it below the tab content/nav overlay. |
| `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/root/MainTabsOverlayTest.kt` | Verify the existing overlay contract, top inset behavior, and the new bottom system-inset contract with deterministic injected dimensions. |
| `docs/ui/01-foundations.md` | Document that the edge-to-edge permanent-tab shell consumes the OS navigation-bar inset in addition to the app's 104dp floating-nav clearance. |

## Decision Checkpoint

No unresolved execution-level decision remains. The current code already owns permanent-tab composition in `MainTabsOverlay`, already exposes a deterministic top-inset test seam, and already uses `WindowInsets.statusBars`; extending that seam to `WindowInsets.navigationBars` keeps the change local and avoids modifying every screen or the Activity edge-to-edge policy.

### Task 1: Reserve the OS navigation-bar inset in the permanent-tab overlay

**Files:**

- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt:287-314`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/root/MainTabsOverlayTest.kt:61-94`
- Modify: `docs/ui/01-foundations.md` in the F8 layout-foundation decision

**Interfaces:**

- Consumes: `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()` and the existing `MainTabsOverlay` content slot `@Composable (Modifier) -> Unit`.
- Produces: `MainTabsOverlay(..., contentBottomInset: Dp = ...)`, where the supplied bottom inset reduces the layout area available to both `OceNavHost` and the aligned `OceBottomNav` without changing the public screen APIs.

- [ ] **Step 1: Add a failing deterministic bottom-inset test**

Append this test to `MainTabsOverlayTest` after `tab_content_preserves_status_bar_top_inset`. It injects a 48dp OS inset, representing a three-button navigation bar, and asserts that the content slot ends exactly above that inset. The existing test tag is reused so no production-only test marker is introduced.

```kotlin
    @Test
    fun tab_content_reserves_navigation_bar_bottom_inset() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                val navController = rememberNavController()
                MainTabsOverlay(
                    navController = navController,
                    isOnline = true,
                    contentBottomInset = 48.dp,
                ) { contentModifier ->
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

        val rootBottom = composeRule.onRoot().getUnclippedBoundsInRoot().bottom
        val contentBottom =
            composeRule.onNodeWithTag(MAIN_TABS_CONTENT_TAG).getUnclippedBoundsInRoot().bottom

        assertEquals(
            "Tab content must stop above the system navigation-bar inset",
            (rootBottom - 48.dp).value,
            contentBottom.value,
            0.5f,
        )
    }
```

- [ ] **Step 2: Run the new test and verify it fails before implementation**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.ui.root.MainTabsOverlayTest'
```

Expected: `FAIL` during test compilation with an unresolved `contentBottomInset` argument, because `MainTabsOverlay` does not yet expose the new seam. If the test compiles due to an unexpected existing parameter, the runtime assertion must fail because the current overlay does not reserve the injected 48dp.

- [ ] **Step 3: Add the bottom-inset seam and apply it at the overlay boundary**

In `AppRoot.kt`, keep the existing status-bar default and add a navigation-bar default immediately after it:

```kotlin
    contentTopInset: Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
    contentBottomInset: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
    content: @Composable (Modifier) -> Unit,
```

Change the `MainTabsOverlay` root `Box` from:

```kotlin
    Box(modifier = modifier.fillMaxSize()) {
```

to:

```kotlin
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .padding(bottom = contentBottomInset),
    ) {
```

This keeps the outer edge-to-edge root full size while constraining its children to the area above the OS navigation bar. The existing `Column` top padding remains unchanged, and the existing 104dp `contentPadding` consumers continue to clear the floating app nav inside that smaller viewport. Update the `MainTabsScaffold`/`MainTabsOverlay` KDoc to state that the shell consumes the OS navigation-bar inset before aligning `OceBottomNav`.

- [ ] **Step 4: Run the focused regression tests**

Run:

```bash
scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.ui.root.MainTabsOverlayTest' --tests 'com.jjundev.oneclickeng.ui.navigation.AppNavigationTest'
```

Expected: `BUILD SUCCESSFUL`; all `MainTabsOverlayTest` cases pass, including the existing full-height overlay and status-bar tests, and `AppNavigationTest` still renders and switches all three tabs. The 104dp app-nav clearance remains owned by `OceBottomNavDefaults` and is not duplicated here.

- [ ] **Step 5: Document the two-layer bottom-inset policy**

In the F8 decision in `docs/ui/01-foundations.md`, extend the existing bottom-navigation rule with this exact paragraph immediately after the sentence describing `overlayContentBottomPadding = 104dp`:

```markdown
엣지투엣지 Activity에서 3버튼 시스템 내비게이션 모드가 앱 콘텐츠를 가리지 않도록 `MainTabsOverlay`가 `WindowInsets.navigationBars` 하단 인셋도 소비한다. 제스처 모드처럼 해당 인셋이 0dp인 경우에는 기존 플로팅 레이아웃을 유지한다. OS 내비게이션 바 인셋과 앱 플로팅 내비게이션의 104dp 콘텐츠 클리어런스는 서로 다른 레이어의 여유이므로 하나로 합치지 않는다.
```

- [ ] **Step 6: Run the project verification and perform device-mode QA**

Run:

```bash
scripts/verify-android.sh
git diff --check
git status --short
```

Expected: the Android verification script completes successfully (apart from any pre-existing, explicitly reported repository lint limitation), `git diff --check` produces no output, and only the three planned files plus this plan file are modified.

On an Android emulator or physical device running the app, check the same permanent-tab screens in both system settings:

- Gesture mode: the bottom composition remains at the current visual position, with no new visible gap beyond the existing floating-nav spacing.
- Three-button navigation mode: the lowest scrollable content can be scrolled above the floating nav, and the floating nav itself sits above the OS navigation bar instead of being covered by it.
- Switch between Home, Records, and Settings, including Records' scroll FAB and Settings' snackbar, to ensure their existing 104dp app-nav clearance is still correct.
- Enter one navigation-free flow (for example dialogue or summary) and confirm this permanent-tab-only inset change does not add a new bottom offset there.

- [ ] **Step 7: Commit the independently reviewable fix**

```bash
git add \
  android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt \
  android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/root/MainTabsOverlayTest.kt \
  docs/ui/01-foundations.md \
  docs/plans/2026-07-23-system-navigation-bar-inset.md
git commit -m "fix: respect system navigation bar inset in tab shell"
```

## Self-Review

**Spec coverage:** The navigation-mode occlusion is addressed by the `WindowInsets.navigationBars` default and deterministic 48dp test; gesture-mode preservation is covered by the zero/minimal-inset constraint and device QA; the permanent-tab scope and navigation-free-flow non-regression are explicit; existing app-nav clearance is preserved and separately documented.

**Placeholder scan:** No TODO/TBD/incomplete implementation instructions are used. Every code-changing step includes the exact Kotlin signature or replacement body, and every verification step includes the command and expected result.

**Type consistency:** `contentBottomInset` is declared as `Dp`, uses the existing `Dp` import, is passed as a `Dp` test value, and is consumed by `Modifier.padding(bottom = ...)`. The test's `getUnclippedBoundsInRoot()` values are compared as the same `Dp`-backed type. The content slot and existing `MainTabsOverlay` call sites remain unchanged because the new parameter has a default value.

**Review note:** This plan intentionally contains one implementation task because the production change, regression test, and foundation-policy documentation form one atomic layout contract; splitting the documentation or verification into a separately reviewable task would not produce independently useful software.
