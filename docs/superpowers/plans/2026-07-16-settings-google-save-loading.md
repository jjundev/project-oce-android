# Settings Google 저장 카드 로딩 애니메이션 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 설정 화면에서 "Google로 진도 저장" 카드를 탭한 뒤 Google 계정 선택 시트가 뜨기까지(~2초) 화면에 아무 반응이 없던 문제를, 그 카드에만 로딩 스피너 + 클릭 비활성화를 붙여 해소한다.

**Architecture:** 로딩 상태의 출처는 이미 존재하는 `GoogleLinkViewModel.uiState`(`LinkUiState.Linking`)다. `SettingsScreen`은 이미 이 Flow를 구독하고 있으므로, 새 상태 저장소를 만들지 않고 (a) `SettingsContent`/`AccountSection`에 `isGoogleSaveLoading: Boolean` prop을 흘려보내 "Google로 진도 저장" 행의 trailing을 스피너로 바꾸고 클릭을 막는 렌더링 레이어와, (b) 탭 시점에 로컬 플래그를 켜고 `linkState`가 더 이상 `Linking`이 아니면 끄는 순수 함수 기반 상태 갱신 레이어로 나눈다. (b)를 별도 상태로 두는 이유: "진도 다시 옮기기"(retryMerge) 행도 동일한 `linkState`를 `Linking`으로 만들 수 있는데, 사용자는 "그 카드에만"(Google 저장 카드에만) 로딩 효과를 요청했으므로 retryMerge 탭이 Google 저장 카드에 스피너를 잘못 띄우면 안 된다.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, JUnit4, Robolectric + Compose UI test (`createComposeRule`), Roborazzi(스크린샷).

## Global Constraints

- 검증은 반드시 `scripts/verify-android.sh` 로 돌린다 — 워크트리 전용 `GRADLE_USER_HOME` 격리 없이는 캐시 오염으로 컴파일 에러도 통과된 것처럼 보일 수 있다(`docs/agents/android-verification.md`).
- `ktlintMainSourceSetCheck`는 기본 검증 세트에서 이미 제외되어 있다(master 선존재 위반) — 이 플랜의 태스크들도 이를 별도로 걷어낼 필요 없다.
- 로딩 효과는 **"Google로 진도 저장" 카드에만** 적용한다 — "진도 다시 옮기기"(retryMerge) 행은 이번 스코프에 포함하지 않는다(사용자 지시).
- 새 로딩 스피너는 기존 온보딩 시트(`GoogleSaveActions`, `feature/onboarding/google/GoogleSavePromptSheet.kt:162-167`)의 인라인 스피너 컨벤션(20dp, strokeWidth 2dp)을 따른다 — 새로운 시각 언어를 만들지 않는다.

---

### Task 1: `SettingsContent`/`AccountSection`에 Google 저장 로딩 렌더링 추가

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt`

**Interfaces:**
- Consumes: 기존 `SettingsNavRow(icon, title, modifier, desc, titleColor, iconTint, iconBg, onClick, trailing)` (`feature/settings/SettingsRows.kt:46-106`, 미변경). 파일에 이미 있는 `OceIcon`, `OceIconSize`, `OneClickIcon`, `OceTheme` import.
- Produces: `internal fun SettingsContent(..., isGoogleSaveLoading: Boolean = false, ...)`, `private fun AccountSection(..., isGoogleSaveLoading: Boolean = false, ...)`, `internal const val GOOGLE_SAVE_LOADING_TAG`. Task 2가 `SettingsScreen`에서 이 `isGoogleSaveLoading` 파라미터로 실제 `linkState` 파생값을 흘려보낸다.

- [ ] **Step 1: 실패하는 테스트 작성**

`android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt`의 import 블록(현재 1-26줄)에 다음을 추가한다:

```kotlin
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
```

(기존 `import androidx.compose.ui.test.onRoot` 다음 줄들에 알파벳 순으로 끼워 넣는다. `performClick`은 아래 `accountSection_showsChevron_andAllowsClick_whenGoogleSaveNotLoading` 테스트에서 쓴다 — 빠뜨리면 Step 4에서 `unresolved reference: performClick` 컴파일 에러가 난다.)

파일 맨 아래(`reminder_time_sheet` 테스트 다음, 클래스 닫는 `}` 직전)에 다음 두 테스트를 추가한다:

```kotlin
    @Test
    fun accountSection_showsLoadingSpinner_andDisablesClick_whenGoogleSaveLoading() {
        var clicked = 0
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsContent(
                        state = SettingsUiState(loading = false, nickname = "준영", isGuest = true),
                        versionLabel = "1.0.0 (1)",
                        notificationsBlocked = false,
                        onNicknameChange = {},
                        onQualityChange = {},
                        onSpeedChange = {},
                        onMuteChange = {},
                        onReminderToggle = {},
                        onReminderTimeClick = {},
                        onOpenNotificationSettings = {},
                        onPurgeClick = {},
                        onResetClick = {},
                        onGoogleSave = { clicked += 1 },
                        onLogoutClick = {},
                        onDeleteClick = {},
                        onRetryMerge = {},
                        onPrivacy = {},
                        onTerms = {},
                        isGoogleSaveLoading = true,
                        reduceMotion = true,
                    )
                }
            }
        }

        composeRule.onNodeWithTag(GOOGLE_SAVE_LOADING_TAG, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Google로 진도 저장").assertHasNoClickAction()
        assertEquals(0, clicked)
    }

    @Test
    fun accountSection_showsChevron_andAllowsClick_whenGoogleSaveNotLoading() {
        var clicked = 0
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsContent(
                        state = SettingsUiState(loading = false, nickname = "준영", isGuest = true),
                        versionLabel = "1.0.0 (1)",
                        notificationsBlocked = false,
                        onNicknameChange = {},
                        onQualityChange = {},
                        onSpeedChange = {},
                        onMuteChange = {},
                        onReminderToggle = {},
                        onReminderTimeClick = {},
                        onOpenNotificationSettings = {},
                        onPurgeClick = {},
                        onResetClick = {},
                        onGoogleSave = { clicked += 1 },
                        onLogoutClick = {},
                        onDeleteClick = {},
                        onRetryMerge = {},
                        onPrivacy = {},
                        onTerms = {},
                        isGoogleSaveLoading = false,
                        reduceMotion = true,
                    )
                }
            }
        }

        composeRule.onNodeWithTag(GOOGLE_SAVE_LOADING_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Google로 진도 저장").assertHasClickAction()
        composeRule.onNodeWithText("Google로 진도 저장").performClick()
        composeRule.waitForIdle()
        assertEquals(1, clicked)
    }
```

- [ ] **Step 2: 테스트 실행 → 실패(컴파일 에러) 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SettingsScreenScreenshotTest*'`
Expected: FAIL — 컴파일 에러. `SettingsContent(...)` 호출에 `isGoogleSaveLoading` 파라미터가 없어 `No value passed for parameter 'reduceMotion'` 류가 아니라 `unresolved reference: isGoogleSaveLoading`(또는 초과 인자 에러), 그리고 `unresolved reference: GOOGLE_SAVE_LOADING_TAG`.

- [ ] **Step 3: `SettingsScreen.kt`에 로딩 렌더링 구현**

import 블록(3-92줄)에 두 줄 추가:

```kotlin
import androidx.compose.material3.CircularProgressIndicator
```
→ 기존 `import androidx.compose.material3.HorizontalDivider` 바로 위(알파벳 순, C < H)에 삽입.

```kotlin
import androidx.compose.ui.platform.testTag
```
→ 기존 `import androidx.compose.ui.platform.LocalContext` 바로 아래(알파벳 순, LocalContext < testTag)에 삽입.

`SettingsContent` 시그니처(330-353줄)에서 `onGoogleSave: () -> Unit,` 바로 다음 줄에 파라미터를 추가한다:

```kotlin
@Suppress("LongMethod", "LongParameterList")
@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    versionLabel: String,
    notificationsBlocked: Boolean,
    onNicknameChange: (String) -> Unit,
    onQualityChange: (TtsQuality) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onMuteChange: (Boolean) -> Unit,
    onReminderToggle: (Boolean) -> Unit,
    onReminderTimeClick: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onPurgeClick: () -> Unit,
    onResetClick: () -> Unit,
    onGoogleSave: () -> Unit,
    onLogoutClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRetryMerge: () -> Unit,
    onPrivacy: () -> Unit,
    onTerms: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
    isGoogleSaveLoading: Boolean = false,
) {
```

두 `AccountSection(...)` 호출부(게스트 분기 ~369-378줄, 회원 분기 ~479-489줄)에 각각 `isGoogleSaveLoading = isGoogleSaveLoading,` 라인을 추가한다. 게스트 분기:

```kotlin
            if (state.isGuest) {
                item(key = "account") {
                    AccountSection(
                        state = state,
                        onGoogleSave = onGoogleSave,
                        onRetryMerge = onRetryMerge,
                        onLogoutClick = onLogoutClick,
                        onDeleteClick = onDeleteClick,
                        isGoogleSaveLoading = isGoogleSaveLoading,
                        modifier = Modifier.staggerReveal(0, entrance),
                    )
                }
            }
```

회원 분기:

```kotlin
            if (!state.isGuest) {
                item(key = "account") {
                    AccountSection(
                        state = state,
                        onGoogleSave = onGoogleSave,
                        onRetryMerge = onRetryMerge,
                        onLogoutClick = onLogoutClick,
                        onDeleteClick = onDeleteClick,
                        isGoogleSaveLoading = isGoogleSaveLoading,
                        modifier = Modifier.staggerReveal(5, entrance),
                    )
                }
            }
```

`AccountSection` 전체(663-734줄)를 다음으로 교체한다(시그니처에 `isGoogleSaveLoading` 추가, Google 저장 행의 `onClick`/`trailing` 분기, 새 `GoogleSaveTrailing` 헬퍼 추가):

```kotlin
/** 계정 섹션(적응형) — 헤더 + 배지, 게스트는 Google 저장(+선택 이관 재시도) tinted 행, 회원은 로그아웃/삭제 행. */
@Composable
private fun AccountSection(
    state: SettingsUiState,
    onGoogleSave: () -> Unit,
    onRetryMerge: () -> Unit,
    onLogoutClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    isGoogleSaveLoading: Boolean = false,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
            modifier = Modifier.padding(start = 4.dp),
        ) {
            SettingsSectionHeader(
                title = stringResource(R.string.settings_section_account),
                modifier = Modifier.padding(start = 0.dp),
            )
            SettingsAccountBadge(isGuest = state.isGuest)
        }
        OneClickCard(modifier = Modifier.fillMaxWidth(), shape = OceTheme.shapes.radius24) {
            if (state.isGuest) {
                SettingsNavRow(
                    icon = OceIcon.CloudSync,
                    title = stringResource(R.string.settings_account_google_save),
                    desc = stringResource(R.string.settings_account_google_save_desc),
                    titleColor = MaterialTheme.colorScheme.primary,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    onClick = if (isGoogleSaveLoading) null else onGoogleSave,
                    trailing = { GoogleSaveTrailing(isGoogleSaveLoading) },
                )
                if (state.showRetryMerge) {
                    SettingsCardDivider()
                    SettingsNavRow(
                        icon = OceIcon.SyncProblem,
                        title = stringResource(R.string.settings_account_retry_merge),
                        desc = stringResource(R.string.settings_account_retry_merge_desc),
                        iconTint = OceTheme.colors.gameSaveGold,
                        iconBg = OceTheme.colors.gameSaveGold.copy(alpha = 0.12f),
                        onClick = onRetryMerge,
                    )
                }
            } else {
                SettingsNavRow(
                    icon = OceIcon.Logout,
                    title = stringResource(R.string.settings_account_logout),
                    onClick = onLogoutClick,
                )
                SettingsCardDivider()
                SettingsNavRow(
                    icon = OceIcon.DeleteForever,
                    title = stringResource(R.string.settings_account_delete),
                    desc = stringResource(R.string.settings_account_delete_desc),
                    titleColor = MaterialTheme.colorScheme.error,
                    iconTint = MaterialTheme.colorScheme.error,
                    iconBg = OceTheme.colors.feedbackCorrectBg,
                    onClick = onDeleteClick,
                )
            }
        }
        if (state.isGuest) {
            Text(
                text = stringResource(R.string.settings_account_guest_footnote),
                style = OceTheme.typography.helper.copy(fontSize = 12.sp),
                color = OceTheme.colors.textTertiary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * Google 저장 행 trailing — 로딩 중엔 인라인 스피너(자격증명 시트가 뜨기 전 무반응 구간을 메운다), 평시엔 기본
 * chevron. [SettingsNavRow]의 기본 chevron 람다를 그대로 복제한다(trailing을 조건부로 통째로 바꿔야 해서
 * 기본값 재사용이 불가능하다).
 */
@Composable
private fun GoogleSaveTrailing(isLoading: Boolean) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(GoogleSaveLoadingIndicatorSize).testTag(GOOGLE_SAVE_LOADING_TAG),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
        )
    } else {
        OneClickIcon(
            icon = OceIcon.ChevronRight,
            contentDescription = null,
            tint = OceTheme.colors.textTertiary,
            size = OceIconSize.ListDisclosure,
        )
    }
}
```

파일 맨 아래 상수 블록(현재 898-916줄, `private const val DISABLED_ALPHA = 0.38f` 부터 `private const val LINK_SESSION_ID = "settings"` 까지) 다음에 추가:

```kotlin
/** Google 저장 행 로딩 스피너 지름 — 온보딩 시트 primary 버튼 인라인 스피너와 동일 컨벤션(20dp/2dp stroke). */
private val GoogleSaveLoadingIndicatorSize = 20.dp

/** 설정 화면 Google 저장 로딩 스피너 testTag(컴포즈/스크린샷 테스트 seam). */
internal const val GOOGLE_SAVE_LOADING_TAG = "google_save_loading"
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SettingsScreenScreenshotTest*'`
Expected: PASS — `accountSection_showsLoadingSpinner_andDisablesClick_whenGoogleSaveLoading`, `accountSection_showsChevron_andAllowsClick_whenGoogleSaveNotLoading` 포함 전체 통과.

- [ ] **Step 5: 로딩 상태 스크린샷 베이스라인 추가**

`SettingsScreenScreenshotTest.kt`의 `renderSettings` 헬퍼(현재 76-105줄)를 다음으로 교체한다(새 `isGoogleSaveLoading` 파라미터를 기본값 `false`로 추가하고 `SettingsContent` 호출에 전달):

```kotlin
    private fun renderSettings(
        state: SettingsUiState,
        dark: Boolean,
        blocked: Boolean,
        name: String,
        isGoogleSaveLoading: Boolean = false,
    ) {
        composeRule.setContent {
            OceTheme(darkTheme = dark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    SettingsContent(
                        state = state,
                        versionLabel = "1.0.0 (1)",
                        notificationsBlocked = blocked,
                        onNicknameChange = {},
                        onQualityChange = {},
                        onSpeedChange = {},
                        onMuteChange = {},
                        onReminderToggle = {},
                        onReminderTimeClick = {},
                        onOpenNotificationSettings = {},
                        onPurgeClick = {},
                        onResetClick = {},
                        onGoogleSave = {},
                        onLogoutClick = {},
                        onDeleteClick = {},
                        onRetryMerge = {},
                        onPrivacy = {},
                        onTerms = {},
                        reduceMotion = true,
                        isGoogleSaveLoading = isGoogleSaveLoading,
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }
```

`settings_light_guest` 테스트(현재 107-113줄) 바로 다음에 새 테스트를 추가한다:

```kotlin
    @Test fun settings_light_guest_google_saving() =
        renderSettings(
            SettingsUiState(loading = false, nickname = "준영", isGuest = true),
            dark = false,
            blocked = false,
            name = "settings_light_guest_google_saving",
            isGoogleSaveLoading = true,
        )
```

- [ ] **Step 6: 전체 스크린샷 테스트 재실행 → 통과 + 신규 이미지 생성 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SettingsScreenScreenshotTest*'`
Expected: PASS. `android/app/build/outputs/roborazzi/settings_light_guest_google_saving.png` 생성 확인:

Run: `ls android/app/build/outputs/roborazzi/settings_light_guest_google_saving.png`
Expected: 파일 존재.

- [ ] **Step 7: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt
git add android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt
git commit -m "feat(settings): render loading spinner on Google save card while linking"
```

---

### Task 2: `SettingsScreen`에서 실제 `linkState`로 로딩 플래그 배선

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsFormatTest.kt`

**Interfaces:**
- Consumes: `LinkUiState`(`feature/onboarding/google/GoogleLinkViewModel.kt:16-30`, SettingsScreen.kt에 이미 import됨), Task 1의 `SettingsContent(..., isGoogleSaveLoading: Boolean = false, ...)`.
- Produces: `internal fun googleSaveLoadingAfterLinkStateChange(previous: Boolean, linkState: LinkUiState): Boolean` — 이 파일 밖에서는 쓰이지 않지만 순수 함수라 별도 계층 없이 직접 단위테스트한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsFormatTest.kt` 상단 import에 추가:

```kotlin
import com.jjundev.oneclickeng.feature.onboarding.google.LinkUiState
```

클래스 마지막 테스트(`morning 9_00 formats as 오전 9_00`) 다음, 닫는 `}` 직전에 추가:

```kotlin
    @Test fun `google save loading stays true while credential flow is linking`() {
        assertEquals(true, googleSaveLoadingAfterLinkStateChange(previous = true, linkState = LinkUiState.Linking))
    }

    @Test fun `google save loading clears on success`() {
        assertEquals(false, googleSaveLoadingAfterLinkStateChange(previous = true, linkState = LinkUiState.Success))
    }

    @Test fun `google save loading clears on error`() {
        assertEquals(
            false,
            googleSaveLoadingAfterLinkStateChange(previous = true, linkState = LinkUiState.Error(afterSignIn = false)),
        )
    }

    @Test fun `google save loading clears on idle (credential picker cancelled)`() {
        assertEquals(false, googleSaveLoadingAfterLinkStateChange(previous = true, linkState = LinkUiState.Idle))
    }

    @Test fun `google save loading is not triggered by retry-merge linking alone`() {
        assertEquals(false, googleSaveLoadingAfterLinkStateChange(previous = false, linkState = LinkUiState.Linking))
    }
```

- [ ] **Step 2: 테스트 실행 → 실패(컴파일 에러) 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SettingsFormatTest*'`
Expected: FAIL — `unresolved reference: googleSaveLoadingAfterLinkStateChange`.

- [ ] **Step 3: 순수 함수 + 화면 배선 구현**

`SettingsScreen.kt` 맨 아래, Task 1에서 추가한 `GOOGLE_SAVE_LOADING_TAG` 상수 다음에 추가:

```kotlin
/**
 * 설정 화면 Google 저장 카드 로딩 유지 여부(순수). 탭 시점엔 `true`로 직접 세팅하고, 이후 `linkState`가
 * 바뀔 때마다 이 함수로 갱신한다 — [LinkUiState.Linking]이 계속되면 유지, 그 외([LinkUiState.Success]/
 * [LinkUiState.Error]/[LinkUiState.Idle])면 해제. [previous]가 false(예: "진도 다시 옮기기"로 시작된
 * Linking)면 이 카드는 애초에 로딩 표시 대상이 아니었으므로 계속 false를 유지한다.
 */
internal fun googleSaveLoadingAfterLinkStateChange(
    previous: Boolean,
    linkState: LinkUiState,
): Boolean = previous && linkState is LinkUiState.Linking
```

`SettingsScreen` 컴포저블 안, `var snackbarBounds by remember { mutableStateOf<Rect?>(null) }` 바로 다음 줄(113-118줄 근방)에 상태 변수를 추가한다:

```kotlin
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showPurgeSheet by rememberSaveable { mutableStateOf(false) }
    var showTimeSheet by rememberSaveable { mutableStateOf(false) }
    var snackbarBounds by remember { mutableStateOf<Rect?>(null) }
    // rememberSaveable: 프로세스 재생성으로 true 가 복원돼도 linkState 는 ViewModel 재생성으로 Idle 부터
    // 다시 시작하므로, 아래 LaunchedEffect(linkState) 가 즉시 false 로 되돌려 스피너가 끼지 않는다.
    var googleSaveLoading by rememberSaveable { mutableStateOf(false) }
```

기존 `// Google 연결 성공 → 계정 분기 갱신` 주석 위(140줄 근방)에 새 효과를 추가한다:

```kotlin
    // Google 저장 카드 로딩 — Linking 이 끝나면(성공/실패/취소) 해제. retryMerge 로 시작된 Linking 은 무시.
    LaunchedEffect(linkState) {
        googleSaveLoading = googleSaveLoadingAfterLinkStateChange(googleSaveLoading, linkState)
    }

    // Google 연결 성공 → 계정 분기 갱신(게스트 CTA → 로그아웃/삭제).
    LaunchedEffect(linkState) { if (linkState is LinkUiState.Success) viewModel.refreshAccount() }
```

`onGoogleSave` 정의(189-205줄)를 다음으로 교체한다(탭 즉시 로딩 플래그 on):

```kotlin
    val onGoogleSave = {
        googleSaveLoading = true
        scope.launch {
            linkViewModel.onCredentialFlowStarted()
            val token =
                try {
                    GoogleCredentialProvider.getGoogleIdToken(context)
                } catch (e: GetCredentialCancellationException) {
                    linkViewModel.onCredentialCancelled()
                    return@launch
                } catch (e: Exception) {
                    linkViewModel.onCredentialFailed(LINK_SESSION_ID)
                    return@launch
                }
            linkViewModel.linkGoogle(token, LINK_SESSION_ID)
        }
        Unit
    }
```

`SettingsContent(...)` 호출부(224-247줄)에서 `onGoogleSave = { onGoogleSave() },` 다음 줄에 추가:

```kotlin
        SettingsContent(
            state = state,
            versionLabel = appVersionLabel(context),
            notificationsBlocked = notificationsBlocked,
            onNicknameChange = viewModel::onNicknameChange,
            onQualityChange = viewModel::onQualityChange,
            onSpeedChange = viewModel::onSpeedChange,
            onMuteChange = viewModel::onMuteChange,
            onReminderToggle = onReminderToggle,
            onReminderTimeClick = { showTimeSheet = true },
            onOpenNotificationSettings = { openAppNotificationSettings(context) },
            onPurgeClick = {
                viewModel.loadPurgeCounts()
                showPurgeSheet = true
            },
            onResetClick = { showResetDialog = true },
            onGoogleSave = { onGoogleSave() },
            isGoogleSaveLoading = googleSaveLoading,
            onLogoutClick = { showLogoutDialog = true },
            onDeleteClick = { showDeleteDialog = true },
            onRetryMerge = { linkViewModel.retryMerge(LINK_SESSION_ID) },
            onPrivacy = { openUrl(context, SettingsUrls.PRIVACY) },
            onTerms = { openUrl(context, SettingsUrls.TERMS) },
            reduceMotion = rememberReduceMotion(),
        )
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SettingsFormatTest*'`
Expected: PASS — 5개 신규 케이스 포함 전체 통과.

- [ ] **Step 5: 전체 회귀 검증**

Run: `scripts/verify-android.sh`
Expected: `detekt`, `compileDebugAndroidTestKotlin`, `testDebugUnitTest`, `testReleaseUnitTest` 전부 `BUILD SUCCESSFUL`.

- [ ] **Step 6: 수동 확인(선택, 자동화 불가 — 실제 Credential Manager 타이밍 확인용)**

Google Play 서비스가 있는 연결된 기기/에뮬레이터가 있다면:

Run: `scripts/verify-android.sh :app:installDebug`

앱에서: 설정 탭 → 게스트 상태 확인 → "Google로 진도 저장" 탭 → 탭 즉시 그 행의 chevron이 스피너로 바뀌고 행 탭이 무반응(비활성)이 되는지 확인 → Google 계정 선택 시트가 뜰 때까지 스피너가 유지되는지 확인 → 계정 선택/취소 후 스피너가 원래 chevron으로 복귀하는지 확인. "진도 다시 옮기기" 행이 보이는 상태라면 그 행을 탭해도 "Google로 진도 저장" 행에는 스피너가 뜨지 않는지 함께 확인한다.

- [ ] **Step 7: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt
git add android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsFormatTest.kt
git commit -m "feat(settings): wire Google save card loading flag to credential-flow link state"
```
