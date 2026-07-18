# Onboarding Google Reauth Entry Point Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Google로 로그인" entry point to the top of the onboarding level-question screen so a user who just logged out (or reinstalled) can restore their existing Google-linked account and skip redundant onboarding, instead of being forced through the level/topic questions again.

**Architecture:** After `AccountRepository.signOut()`, `AppViewModel` re-runs `bootstrap()` with a fresh anonymous UID that has no `profile.level`, so `AppRoot` always mounts the onboarding graph starting at `LevelQuestionScreen` — there is no reliable client-side signal to distinguish "genuinely first install" from "just logged out" (both are a brand-new anonymous UID with no level), so the entry point is shown unconditionally on that screen rather than gated behind a flag. Tapping it opens a new bottom sheet that reuses the existing `GoogleAccountLinker`/`GoogleCredentialProvider` machinery (already built for the end-of-first-session "Google로 진도 저장" prompt). On success, instead of navigating within the onboarding nav graph, the new code calls `AccountResetBus.signal()` — the exact mechanism `AccountRepository.signOut()` already uses. That collapses `AppViewModel.uiState` back to `BootState.Loading`, which makes `AppRoot` drop its entire outer `NavHost` (per `AppRoot.kt:83-96`, `resolvedStart == null` short-circuits before the `NavHost` is composed) and re-run `bootstrap()`. Bootstrap then re-reads `profile.level` for whichever UID is now current: a brand-new Google account (FR-3a `Promoted`, no prior level) lands back in `NeedsOnboarding` and continues onboarding signed-in; an existing Google account (FR-3b `Merged`, prior level present) resolves to `MainReady` and the user lands straight on the 3-tab home. This is a strictly safer choice than duplicating the level-presence check with a raw `navController.navigate(MAIN_TABS_ROUTE)` call, because it reuses the one code path (`AppViewModel.bootstrap()`) that is already the single source of truth for "does this UID need onboarding."

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Firebase Auth/Firestore/Functions, Robolectric + Roborazzi (screenshot tests), JUnit4.

## Global Constraints

- All gradle verification in this worktree MUST run through `scripts/verify-android.sh` (never bare `./gradlew`) — it isolates `GRADLE_USER_HOME` per worktree and copies `google-services.json`, both of which silently break correctness otherwise (`docs/agents/android-verification.md`).
- Repo test convention: no mockk. Fakes are hand-written classes implementing the real interface (see `FakeLinker`, `FakeAuth`, `RecordingAnalytics` in the existing test files).
- All user-facing strings are Korean, matching the existing onboarding copy tone (reassuring, not evaluative — see the level-card subtitles).
- Reuse existing design tokens for spacing/shape: `OceTheme.spacing.*`, `OceTheme.shapes.*`, `SheetPrimaryHeight`/`SheetGhostHeight` (`ui/component/OneClickReminderOptInSheet.kt:160-161`). For typography, use `OceTheme.typography.*` styles as-is, or `.copy(fontSize = N.sp)` only when mirroring an already-established prototype-parity override in the same file (e.g. `LevelCard`'s existing `sectionLabel.copy(fontSize = 17.sp)` title / `11.sp` badge) — do not invent a new sp value with no such precedent. A bare dp literal is acceptable only when it mirrors an existing sibling value 1:1 (e.g. the Google logo's `18.dp`, already used by `GoogleSaveActions`/`GoogleLogoSize`) — do not introduce a new, unprecedented dp value.
- `ktlintMainSourceSetCheck` is intentionally excluded from `scripts/verify-android.sh`'s default task set (pre-existing unrelated violation) — do not add it back.

---

## Task 1: `GoogleLinkViewModel` — reauth-scoped link methods + `AccountResetBus` signal

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/OnboardingAnalytics.kt:11-62`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/google/GoogleLinkViewModel.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/google/GoogleLinkViewModelTest.kt`

**Interfaces:**
- Consumes: `GoogleAccountLinker.linkGuest(googleIdToken: String): LinkOutcome`, `GoogleAccountLinker.retryPendingMerge(): LinkOutcome` (`core/auth/GoogleAccountLinker.kt:67-76`, unchanged); `AccountResetBus.signal(): Unit` (`core/auth/AccountResetBus.kt:26-28`, unchanged, already `@Singleton @Inject constructor()` so Hilt resolves it with no new binding).
- Produces: `GoogleLinkViewModel.linkGoogleForReauth(googleIdToken: String): Unit`, `GoogleLinkViewModel.retryMergeForReauth(): Unit`, `GoogleLinkViewModel.onCredentialFailedForReauth(): Unit` — all consumed by Task 2's `GoogleReauthPromptSheet`. `GoogleLinkViewModel`'s constructor gains a third parameter `accountResetBus: AccountResetBus` (Hilt auto-injects; no manual wiring needed at any call site).

This task does **not** touch `linkGoogle(googleIdToken, sessionId)`, `retryMerge(sessionId)`, or `onCredentialFailed(sessionId)` — those back the existing end-of-first-session "Google로 진도 저장" sheet (`GoogleSavePromptSheet.kt`) and must keep navigating via their existing `onLinked` callback, unchanged. Reusing them here (e.g. by passing an empty-string `sessionId`) would be a placeholder value smuggled into real analytics data, so this task adds sibling methods instead.

- [ ] **Step 1: Write the failing tests**

Add to `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/google/GoogleLinkViewModelTest.kt`:

Replace the `vm(...)` helper (lines 39-42) with:

```kotlin
    private fun vm(
        linker: GoogleAccountLinker,
        analytics: OnboardingAnalytics = RecordingAnalytics(),
        resetBus: AccountResetBus = AccountResetBus(),
    ) = GoogleLinkViewModel(linker, analytics, resetBus)
```

Add the import (alongside the existing `com.jjundev.oneclickeng.core.auth.*` imports at the top of the file):

```kotlin
import com.jjundev.oneclickeng.core.auth.AccountResetBus
```

Add `import org.junit.Assert.assertTrue` and `import kotlinx.coroutines.launch` next to the existing `org.junit.Assert.assertEquals` / `kotlinx.coroutines.*` imports.

Add these test methods (place after the existing `retryMerge still-failing stays post-sign-in error` test, before the `credential cancel` test):

```kotlin
    @Test
    fun `reauth link promotion sets Success, logs reauth_succeeded, and signals account reset`() =
        runTest(dispatcher) {
            val analytics = RecordingAnalytics()
            val resetBus = AccountResetBus()
            var signaled = false
            val collector = launch { resetBus.events.collect { signaled = true } }
            val model = vm(FakeLinker(LinkOutcome.Promoted), analytics, resetBus)

            model.linkGoogleForReauth("token")
            advanceUntilIdle()

            assertEquals(LinkUiState.Success, model.uiState.value)
            assertEquals(listOf("reauth_succeeded"), analytics.events)
            assertTrue(signaled)
            collector.cancel()
        }

    @Test
    fun `reauth link merge sets Success, logs reauth_merged, and signals account reset`() =
        runTest(dispatcher) {
            val analytics = RecordingAnalytics()
            val resetBus = AccountResetBus()
            var signaled = false
            val collector = launch { resetBus.events.collect { signaled = true } }
            val model = vm(FakeLinker(LinkOutcome.Merged), analytics, resetBus)

            model.linkGoogleForReauth("token")
            advanceUntilIdle()

            assertEquals(LinkUiState.Success, model.uiState.value)
            assertEquals(listOf("reauth_merged"), analytics.events)
            assertTrue(signaled)
            collector.cancel()
        }

    @Test
    fun `reauth link failure before sign-in is a retryable guest error and does not signal reset`() =
        runTest(dispatcher) {
            val analytics = RecordingAnalytics()
            val resetBus = AccountResetBus()
            var signaled = false
            val collector = launch { resetBus.events.collect { signaled = true } }
            val model = vm(FakeLinker(LinkOutcome.FailedAsGuest), analytics, resetBus)

            model.linkGoogleForReauth("token")
            advanceUntilIdle()

            assertEquals(LinkUiState.Error(afterSignIn = false), model.uiState.value)
            assertEquals(listOf("reauth_failed"), analytics.events)
            assertEquals(false, signaled)
            collector.cancel()
        }

    @Test
    fun `reauth merge failure after sign-in is a post-sign-in error and does not signal reset`() =
        runTest(dispatcher) {
            val resetBus = AccountResetBus()
            var signaled = false
            val collector = launch { resetBus.events.collect { signaled = true } }
            val model = vm(FakeLinker(LinkOutcome.FailedAfterSignIn), resetBus = resetBus)

            model.linkGoogleForReauth("token")
            advanceUntilIdle()

            assertEquals(LinkUiState.Error(afterSignIn = true), model.uiState.value)
            assertEquals(false, signaled)
            collector.cancel()
        }

    @Test
    fun `retryMergeForReauth success sets Success and signals account reset`() =
        runTest(dispatcher) {
            val resetBus = AccountResetBus()
            var signaled = false
            val collector = launch { resetBus.events.collect { signaled = true } }
            val model = vm(FakeLinker(retry = LinkOutcome.Merged), resetBus = resetBus)

            model.retryMergeForReauth()
            advanceUntilIdle()

            assertEquals(LinkUiState.Success, model.uiState.value)
            assertTrue(signaled)
            collector.cancel()
        }

    @Test
    fun `retryMergeForReauth still-failing stays post-sign-in error without signaling`() =
        runTest(dispatcher) {
            val resetBus = AccountResetBus()
            var signaled = false
            val collector = launch { resetBus.events.collect { signaled = true } }
            val model = vm(FakeLinker(retry = LinkOutcome.FailedAfterSignIn), resetBus = resetBus)

            model.retryMergeForReauth()
            advanceUntilIdle()

            assertEquals(LinkUiState.Error(afterSignIn = true), model.uiState.value)
            assertEquals(false, signaled)
            collector.cancel()
        }
```

Also extend the private `RecordingAnalytics` class (lines 136-163) with the three new interface members (see Step 3 below for the interface shape):

```kotlin
        override fun reauthLinkSucceeded() {
            events += "reauth_succeeded"
        }

        override fun reauthLinkConflictMerged() {
            events += "reauth_merged"
        }

        override fun reauthLinkFailed() {
            events += "reauth_failed"
        }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.feature.onboarding.google.GoogleLinkViewModelTest"`
Expected: FAIL — compile error, `unresolved reference: linkGoogleForReauth` / `retryMergeForReauth` / `reauthLinkSucceeded` (the methods and interface members don't exist yet).

- [ ] **Step 3: Extend `OnboardingAnalytics`**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/OnboardingAnalytics.kt`, add three new members to the `OnboardingAnalytics` interface (after `googleLinkFailed(sessionId: String)`, i.e. after line 37):

```kotlin

    /** 재인증(로그아웃 후 복귀) 흐름 — FR-3a 인플레이스 승격 성공. 세션 문맥이 없어 sessionId 를 받지 않는다. */
    fun reauthLinkSucceeded()

    /** 재인증 흐름 — FR-3b 충돌 후 mergeGuestData 이관 성공. */
    fun reauthLinkConflictMerged()

    /** 재인증 흐름 — 연결/이관 실패(취소 제외). */
    fun reauthLinkFailed()
```

Add the matching no-op overrides to `NoOpOnboardingAnalytics` (after `override fun googleLinkFailed(sessionId: String) = Unit`, i.e. after line 61):

```kotlin

        override fun reauthLinkSucceeded() = Unit

        override fun reauthLinkConflictMerged() = Unit

        override fun reauthLinkFailed() = Unit
```

- [ ] **Step 4: Add the reauth methods to `GoogleLinkViewModel`**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/google/GoogleLinkViewModel.kt`, add the import (after line 6, `import com.jjundev.oneclickeng.core.auth.LinkOutcome`):

```kotlin
import com.jjundev.oneclickeng.core.auth.AccountResetBus
```

Change the constructor (lines 38-43) to:

```kotlin
class GoogleLinkViewModel
    @Inject
    constructor(
        private val linker: GoogleAccountLinker,
        private val analytics: OnboardingAnalytics,
        private val accountResetBus: AccountResetBus,
    ) : ViewModel() {
```

Add these methods after `retryMerge` (after line 108, before the closing class brace):

```kotlin

        /**
         * 자격증명 취득 자체가 실패한 재인증 흐름(취소 제외) — signIn 이전이므로 게스트 유지. sessionId 없는
         * 재인증 문맥 버전([onCredentialFailed] 은 세션 완주 흐름 전용).
         */
        fun onCredentialFailedForReauth() {
            analytics.reauthLinkFailed()
            _uiState.value = LinkUiState.Error(afterSignIn = false)
        }

        /**
         * raw Google ID 토큰으로 재인증(로그아웃 후 복귀) 흐름을 수행한다. 성공([LinkOutcome.Promoted]/
         * [LinkOutcome.Merged]) 시 [AccountResetBus.signal] 로 앱 전역 부트 게이트를 재평가시킨다 — 새로
         * 로그인된 UID 의 `profile.level` 유무에 따라 [com.jjundev.oneclickeng.ui.root.AppViewModel] 이
         * 온보딩 계속/홈 진입을 스스로 가른다(이 뷰모델은 어느 쪽인지 알 필요가 없다).
         */
        fun linkGoogleForReauth(googleIdToken: String) {
            _uiState.value = LinkUiState.Linking
            viewModelScope.launch {
                when (linker.linkGuest(googleIdToken)) {
                    LinkOutcome.Promoted -> {
                        analytics.reauthLinkSucceeded()
                        _uiState.value = LinkUiState.Success
                        accountResetBus.signal()
                    }
                    LinkOutcome.Merged -> {
                        analytics.reauthLinkConflictMerged()
                        _uiState.value = LinkUiState.Success
                        accountResetBus.signal()
                    }
                    LinkOutcome.FailedAsGuest -> {
                        analytics.reauthLinkFailed()
                        _uiState.value = LinkUiState.Error(afterSignIn = false)
                    }
                    LinkOutcome.FailedAfterSignIn -> {
                        analytics.reauthLinkFailed()
                        _uiState.value = LinkUiState.Error(afterSignIn = true)
                    }
                }
            }
        }

        /** signIn 후 merge 실패 상태에서의 재시도(재인증 문맥, sessionId 없음). 성공 시 부트 게이트 재평가. */
        fun retryMergeForReauth() {
            _uiState.value = LinkUiState.Linking
            viewModelScope.launch {
                when (linker.retryPendingMerge()) {
                    LinkOutcome.Merged -> {
                        analytics.reauthLinkConflictMerged()
                        _uiState.value = LinkUiState.Success
                        accountResetBus.signal()
                    }
                    else -> {
                        analytics.reauthLinkFailed()
                        _uiState.value = LinkUiState.Error(afterSignIn = true)
                    }
                }
            }
        }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.feature.onboarding.google.GoogleLinkViewModelTest"`
Expected: PASS — all existing tests plus the 6 new ones green.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/OnboardingAnalytics.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/google/GoogleLinkViewModel.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/google/GoogleLinkViewModelTest.kt
git commit -m "feat(onboarding): add reauth-scoped Google link methods that signal account reset"
```

---

## Task 2: `GoogleReauthPromptSheet.kt` — reauth sheet UI

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/google/GoogleReauthPromptSheet.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/google/GoogleReauthActionsScreenshotTest.kt`

**Interfaces:**
- Consumes: `GoogleLinkViewModel.linkGoogleForReauth`, `.retryMergeForReauth`, `.onCredentialFailedForReauth`, `.onCredentialFlowStarted`, `.onCredentialCancelled`, `.uiState: StateFlow<LinkUiState>` (Task 1); `GoogleCredentialProvider.getGoogleIdToken(context: Context): String` (`core/auth/GoogleCredentialProvider.kt`, unchanged, already consumed the same way by `GoogleSavePromptSheet.kt:84`); `OneClickBottomSheet` (`ui/component/primitive/OneClickBottomSheet.kt:55-65`, unchanged); `SheetPrimaryHeight`/`SheetGhostHeight` (`ui/component/OneClickReminderOptInSheet.kt:160-161`, unchanged).
- Produces: `GoogleReauthPromptSheet(onDismiss: () -> Unit, modifier: Modifier = Modifier)` composable (no `onLinked` callback — success routes through `AccountResetBus`, see Task 1) — consumed by Task 3's `LevelQuestionScreen`. `GoogleReauthActions(linking: Boolean, primaryLabel: String, onPrimary: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier)` — the stateless 2-button seam, consumed by this task's own screenshot test.

**Why no automated test for `GoogleReauthPromptSheet` itself:** its sibling `GoogleSavePromptSheet` (same package) has zero automated test coverage today — only a manual `@Preview` (`GoogleSavePromptSheet.kt:207-226`) that renders `GoogleSaveActions` directly, bypassing the `hiltViewModel()`-owning wrapper. This plan follows that established precedent for the VM-owning wrapper, but goes one step further than the precedent by screenshot-testing the stateless `GoogleReauthActions` composable (mirroring how `LevelQuestionContent` — the stateless seam in `LevelQuestionScreen.kt` — already has a Roborazzi test).

- [ ] **Step 1: Write the failing screenshot test**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/google/GoogleReauthActionsScreenshotTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.onboarding.google

import android.app.Application
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.jjundev.oneclickeng.ui.component.primitive.OceSheetDefaults
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 재인증 시트 버튼 군 스크린샷 캡처. [GoogleReauthActions] 를 VM 없이 강제 렌더한다
 * ([com.jjundev.oneclickeng.feature.onboarding.level.LevelQuestionScreenshotTest] 와 동일 패턴).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class GoogleReauthActionsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, dark: Boolean) {
        composeRule.setContent {
            OceTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    GoogleReauthActions(
                        linking = false,
                        primaryLabel = "Google로 로그인",
                        onPrimary = {},
                        onCancel = {},
                        modifier = Modifier.padding(OceSheetDefaults.contentPadding),
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    @Test
    fun google_reauth_actions_light() = capture(name = "google_reauth_actions_light", dark = false)

    @Test
    fun google_reauth_actions_dark() = capture(name = "google_reauth_actions_dark", dark = true)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.feature.onboarding.google.GoogleReauthActionsScreenshotTest"`
Expected: FAIL — compile error, `unresolved reference: GoogleReauthActions` (the file doesn't exist yet).

- [ ] **Step 3: Create `GoogleReauthPromptSheet.kt`**

```kotlin
package com.jjundev.oneclickeng.feature.onboarding.google

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jjundev.oneclickeng.core.auth.GoogleCredentialProvider
import com.jjundev.oneclickeng.ui.component.primitive.OceSheetDefaults
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.component.SheetGhostHeight
import com.jjundev.oneclickeng.ui.component.SheetPrimaryHeight
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.launch

/**
 * 로그아웃 후 재인증 시트(레벨 화면 진입점). [GoogleSavePromptSheet] 와 자격증명 취득 흐름은 동일하지만,
 * 성공 시 이 화면 안에서 다음 목적지로 navigate 하지 않는다 — [GoogleLinkViewModel.linkGoogleForReauth] 가
 * 성공 시 `AccountResetBus` 를 울려 앱 전역 부트 게이트를 재평가시키고, 그 결과([AppRoot] 의 outer NavHost
 * 재구성)가 이 시트를 포함한 전체 온보딩 그래프를 함께 unmount 한다. 그래서 `onLinked` 콜백이 없다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("TooGenericExceptionCaught", "SwallowedException")
@Composable
fun GoogleReauthPromptSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    linkViewModel: GoogleLinkViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val linkState by linkViewModel.uiState.collectAsStateWithLifecycle()

    val linking = linkState is LinkUiState.Linking
    val error = linkState as? LinkUiState.Error

    val onPrimary = {
        if (error?.afterSignIn == true) {
            linkViewModel.retryMergeForReauth()
        } else {
            scope.launch {
                linkViewModel.onCredentialFlowStarted()
                val token =
                    try {
                        GoogleCredentialProvider.getGoogleIdToken(context)
                    } catch (e: GetCredentialCancellationException) {
                        linkViewModel.onCredentialCancelled()
                        return@launch
                    } catch (e: Exception) {
                        linkViewModel.onCredentialFailedForReauth()
                        return@launch
                    }
                linkViewModel.linkGoogleForReauth(token)
            }
            Unit
        }
    }

    OneClickBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
        ) {
            Text(
                text = "Google 계정으로 로그인",
                style = OceTheme.typography.dialogHeader,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "이전에 사용하던 계정을 연결하면 저장된 레벨과 학습 기록을 그대로 불러와요.",
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = OceTheme.spacing.sm),
            )
            if (error != null) {
                Text(
                    text = "연결에 실패했어요. 잠시 후 다시 시도해 주세요.",
                    style = OceTheme.typography.body,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            GoogleReauthActions(
                linking = linking,
                primaryLabel = if (error?.afterSignIn == true) "로그인 다시 시도" else "Google로 로그인",
                onPrimary = onPrimary,
                onCancel = onDismiss,
            )
        }
    }
}

/** 재인증 시트의 액션 군 — [GoogleSaveActions] 와 같은 primary 52dp / ghost 48dp 리듬, 2버튼(저장 시트는 3버튼). */
@Composable
internal fun GoogleReauthActions(
    linking: Boolean,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
    ) {
        Button(
            onClick = onPrimary,
            enabled = !linking,
            modifier = Modifier.fillMaxWidth().height(SheetPrimaryHeight),
            shape = OceTheme.shapes.radius12,
        ) {
            if (linking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(com.google.android.gms.base.R.drawable.googleg_standard_color_18),
                        contentDescription = null,
                        modifier = Modifier.size(GoogleReauthLogoSize).testTag(GOOGLE_REAUTH_LOGO_TAG),
                    )
                    Text(text = primaryLabel, style = OceTheme.typography.sectionLabel)
                }
            }
        }
        TextButton(
            onClick = onCancel,
            enabled = !linking,
            modifier = Modifier.fillMaxWidth().height(SheetGhostHeight),
        ) {
            Text(
                text = "취소",
                style = OceTheme.typography.sectionLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal const val GOOGLE_REAUTH_LOGO_TAG = "google_reauth_logo"
private val GoogleReauthLogoSize = 18.dp

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 360, heightDp = 280)
@Composable
private fun GoogleReauthPromptPreview() {
    OceTheme {
        Column(
            modifier = Modifier.fillMaxWidth().padding(OceSheetDefaults.contentPadding),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
        ) {
            Text("Google 계정으로 로그인", style = OceTheme.typography.dialogHeader)
            GoogleReauthActions(
                linking = false,
                primaryLabel = "Google로 로그인",
                onPrimary = {},
                onCancel = {},
            )
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.feature.onboarding.google.GoogleReauthActionsScreenshotTest"`
Expected: FAIL on first run with a Roborazzi "reference image not found" diff error — this is expected for a brand-new screenshot test (there is no golden PNG yet).

- [ ] **Step 5: Record the golden screenshots**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.feature.onboarding.google.GoogleReauthActionsScreenshotTest" -Proborazzi.record`
Expected: PASS, and `android/app/build/outputs/roborazzi/google_reauth_actions_light.png` / `google_reauth_actions_dark.png` are created. Re-run Step 4's command (without `-Proborazzi.record`) once more to confirm it now passes against the recorded goldens.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/google/GoogleReauthPromptSheet.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/google/GoogleReauthActionsScreenshotTest.kt \
        android/app/build/outputs/roborazzi/google_reauth_actions_light.png \
        android/app/build/outputs/roborazzi/google_reauth_actions_dark.png
git commit -m "feat(onboarding): add Google reauth prompt sheet"
```

---

## Task 3: Wire the entry banner into `LevelQuestionScreen`

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/level/LevelQuestionScreen.kt`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/level/LevelQuestionScreenshotTest.kt`

**Interfaces:**
- Consumes: `GoogleReauthPromptSheet(onDismiss: () -> Unit)` (Task 2).
- Produces: `LevelQuestionContent(onLevelSelected: (String) -> Unit, modifier: Modifier = Modifier, reduceMotion: Boolean = false, onReauthTapped: () -> Unit = {})` — the `onReauthTapped` parameter is new; the default keeps every other existing caller (there are none besides the screen itself and the test) source-compatible.

- [ ] **Step 1: Update the screenshot test to exercise the new banner**

In `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/level/LevelQuestionScreenshotTest.kt`, replace the `LevelQuestionContent(...)` call (line 32) with:

```kotlin
                    LevelQuestionContent(onLevelSelected = {}, onReauthTapped = {}, reduceMotion = true)
```

- [ ] **Step 2: Run the test to verify it fails against the stale golden images**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.feature.onboarding.level.LevelQuestionScreenshotTest"`
Expected: FAIL — compile error first (`onReauthTapped` doesn't exist on `LevelQuestionContent` yet). Once Step 3 lands, re-running this same command should then fail on an *image diff* instead (the recorded `onboarding_level_light.png`/`onboarding_level_dark.png` don't show the new banner yet) — both are expected red states for this step, in that order.

- [ ] **Step 3: Add the banner and wire the sheet**

In `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/level/LevelQuestionScreen.kt`, add these imports (alongside the existing ones):

```kotlin
import androidx.compose.foundation.Image
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import com.jjundev.oneclickeng.feature.onboarding.google.GoogleReauthPromptSheet
```

Replace the `LevelQuestionScreen` function (lines 48-65) with:

```kotlin
@Composable
fun LevelQuestionScreen(
    onLevelSelected: (level: String) -> Unit,
    modifier: Modifier = Modifier,
    isReturning: Boolean = false,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.onOnboardingStarted(isReturning) }
    var showReauthSheet by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        LevelQuestionContent(
            onLevelSelected = { value ->
                viewModel.onLevelSelected(value)
                onLevelSelected(value)
            },
            onReauthTapped = { showReauthSheet = true },
            reduceMotion = rememberReduceMotion(),
        )
        if (showReauthSheet) {
            GoogleReauthPromptSheet(onDismiss = { showReauthSheet = false })
        }
    }
}
```

Replace the `LevelQuestionContent` function signature and body **together with** the old stagger-offset constant right after it (lines 71-112 — this range deliberately includes the existing `private const val LEVEL_CARD_STAGGER_OFFSET = 3` and its doc comment at lines 111-112, not just the function body, so the block below's own `LEVEL_CARD_STAGGER_OFFSET = 4` replaces it in place rather than duplicating it — leaving the old `= 3` declaration in place would be a Kotlin "conflicting declarations" compile error) with:

```kotlin
@Composable
internal fun LevelQuestionContent(
    onLevelSelected: (level: String) -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    onReauthTapped: () -> Unit = {},
) {
    val entrance = rememberScreenEntrance(reduceMotion)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(OceTheme.spacing.sheetPadding),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
    ) {
        OnboardingStepBar(step = 1, total = 2, modifier = Modifier.staggerReveal(0, entrance))
        GoogleReauthEntryLink(
            onClick = onReauthTapped,
            modifier = Modifier.staggerReveal(1, entrance),
        )
        Text(
            text = "먼저, 오늘 연습을 맞춰볼게요",
            // 온보딩 H1 은 프로토 정합상 ExtraBold·24sp → homeTitle(800·25sp) 재사용(±1sp, 공용 screenTitle 과 구분).
            style = OceTheme.typography.homeTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.staggerReveal(2, entrance).semantics { heading() },
        )
        Text(
            text = "첫 대화는 쉽게 시작하고, 선택한 난이도는 다음 대화부터 반영돼요.",
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.staggerReveal(3, entrance).padding(bottom = OceTheme.spacing.md),
        )
        LEVEL_OPTIONS.forEachIndexed { index, option ->
            Box(modifier = Modifier.staggerReveal(index + LEVEL_CARD_STAGGER_OFFSET, entrance)) {
                LevelCard(
                    option = option,
                    onClick = { onLevelSelected(option.value) },
                )
            }
        }
    }
}

/** Column 직계 자식 중 스텝바/재인증 배너/제목/부제(0~3) 다음, LevelCard 스태거 인덱스 시작 오프셋. */
private const val LEVEL_CARD_STAGGER_OFFSET = 4

/**
 * 레벨 화면 상단 재인증 진입점 — 로그아웃 후 재부트된 사용자는 새 익명 UID·레벨 없음으로 이 화면에 떨어지고,
 * 클라이언트는 "진짜 첫 설치"와 구분할 신뢰 가능한 신호가 없다(둘 다 레벨 없는 새 익명 UID). 그래서 조건부
 * 노출 대신 항상 노출한다 — 진짜 신규 사용자는 그냥 무시하고 카드로 넘어가면 된다.
 */
@Composable
private fun GoogleReauthEntryLink(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(OceTheme.shapes.radius12)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = ICON_BG_ALPHA))
                .clickable(onClick = onClick)
                .padding(horizontal = OceTheme.spacing.lg, vertical = OceTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
    ) {
        Image(
            painter = painterResource(com.google.android.gms.base.R.drawable.googleg_standard_color_18),
            contentDescription = null,
            // 18dp — GoogleSaveActions/GoogleReauthActions 의 GoogleLogoSize/GoogleReauthLogoSize 와 동일 값(신규 아님).
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs)) {
            Text(
                text = "이미 계정이 있으신가요?",
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Google로 로그인",
                style = OceTheme.typography.sectionLabel,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        OneClickIcon(
            icon = OceIcon.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            size = OceIconSize.ListDisclosure,
        )
    }
}
```

(`ICON_BG_ALPHA` is the existing `private const val ICON_BG_ALPHA = 0.12f` at line 221 — unchanged, now reused by both `LevelCard` and `GoogleReauthEntryLink`.)

- [ ] **Step 4: Run the test to verify it still fails (on the image diff, not a compile error)**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.feature.onboarding.level.LevelQuestionScreenshotTest"`
Expected: FAIL — Roborazzi image-diff failure against the stale `onboarding_level_light.png`/`onboarding_level_dark.png` (the recorded goldens don't have the banner yet). This confirms the new banner actually renders and changes the screen's pixels.

- [ ] **Step 5: Record the updated golden screenshots**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests "com.jjundev.oneclickeng.feature.onboarding.level.LevelQuestionScreenshotTest" -Proborazzi.record`
Expected: PASS, and `onboarding_level_light.png`/`onboarding_level_dark.png` are overwritten with the banner visible below the step bar. Re-run Step 4's command (without `-Proborazzi.record`) to confirm it now passes.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/level/LevelQuestionScreen.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/onboarding/level/LevelQuestionScreenshotTest.kt \
        android/app/build/outputs/roborazzi/onboarding_level_light.png \
        android/app/build/outputs/roborazzi/onboarding_level_dark.png
git commit -m "feat(onboarding): show Google reauth entry banner on the level question screen"
```

---

## Task 4: Full verification pass

**Files:** none (verification only).

**Interfaces:** none — this task runs the project's standard gate over everything Tasks 1-3 touched.

- [ ] **Step 1: Run the full default verification set**

Run: `scripts/verify-android.sh`
Expected: PASS — `:app:detekt`, `:app:compileDebugAndroidTestKotlin`, `:app:testDebugUnitTest`, `:app:testReleaseUnitTest` all succeed. Pay particular attention to detekt: the new `try`/`catch (e: Exception)` block in `GoogleReauthPromptSheet.kt`'s `onPrimary` lambda is covered by the file-level `@Suppress("TooGenericExceptionCaught", "SwallowedException")` already added in Task 2 Step 3 — if detekt still flags it, the suppression annotation placement needs to move from the function to match wherever detekt attributes the finding (compare against how `GoogleSavePromptSheet.kt:53-54` scopes the same suppressions).

- [ ] **Step 2: Manually walk the flow in a running app (release-mode gate, not part of the task graph above)**

This step has no pass/fail command — it's a human sanity check before merging, since Roborazzi screenshots confirm pixels but not the live navigation teardown described in this plan's Architecture section (`AccountResetBus.signal()` unmounting the whole onboarding `NavHost`). Install a debug build, log out from Settings (or clear app data to simulate a fresh anonymous UID with no level), land on the level question screen, tap "Google로 로그인":
  - Signing in with an account that has no prior `profile.level` should land back on the level question screen, now signed in (not anonymous).
  - Signing in with an account that already has a saved level should skip straight to the 3-tab home with no visible onboarding flash beyond the existing boot splash.

- [ ] **Step 3: Commit (only if Step 1 required fixes)**

If Step 1 was clean on the first run, there is nothing to commit here — Tasks 1-3 already captured every change. If detekt or a test required a fix, stage exactly those files and commit:

```bash
git add -A android/app
git commit -m "fix(onboarding): satisfy detekt/tests for the Google reauth entry point"
```
