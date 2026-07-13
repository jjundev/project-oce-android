# 뒤로가기 확인 시트 (앱 종료 · 대화 중단) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 뒤로가기를 눌렀을 때 두 화면에서 확인 시트를 띄운다 — 메인(홈 탭)에서는 "앱을 종료할까요?", 학습(대화) 화면에서는 프로토타입의 "대화를 그만할까요?" 경고 시트.

**Architecture:** 재사용 가능한 확인 시트 컴포넌트(`OneClickConfirmSheet`)를 만들고, 각 화면에 얇은 "back guard" 컴포저블(`AppExitGuard`, `DialogueExitGuard`)을 얹는다. 가드는 시스템 뒤로가기(`BackHandler`)와 화면 헤더 뒤로가기를 모두 가로채 시트 표시 여부(boolean)만 소유한다. 실제 출구(액티비티 종료 / 대화 나가기)는 콜백으로 주입해 테스트 가능하게 한다.

**Tech Stack:** Kotlin, Jetpack Compose, Material3 `ModalBottomSheet`(기존 `OneClickBottomSheet` 프리미티브 래핑), `androidx.activity.compose.BackHandler`, Robolectric + Compose UI test(`createComposeRule` / `createAndroidComposeRule`).

## Global Constraints

- **작업 디렉터리:** 모든 경로는 워크트리 루트 `/Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/grill-yourself-settings-ui-92d845` 기준이며, 안드로이드 모듈은 `android/` 아래에 있다.
- **검증은 반드시 `scripts/verify-android.sh`로 돌린다** — 워크트리 gradle 검증은 이 스크립트만 신뢰할 수 있다(공유 `~/.gradle` 캐시·데몬 오염, `google-services.json` 부재 우회). 직접 `./gradlew`를 호출하지 말 것.
- **컴파일 검증 태스크:** `scripts/verify-android.sh :app:compileDebugKotlin` (빠른 컴파일 확인).
- **단위/컴포넌트 테스트 태스크:** `scripts/verify-android.sh :app:testDebugUnitTest --tests '<FQCN>'` (본 계획의 모든 테스트는 `app/src/test/` Robolectric 소스셋에 둔다 — 에뮬레이터 불필요).
- **테스트 러너 규약(반드시 준수):** 모든 Compose 테스트 클래스는 `@RunWith(RobolectricTestRunner::class)` + `@Config(sdk = [34], qualifiers = ..., application = Application::class)`. `BackHandler`를 발화하는 테스트는 `LocalOnBackPressedDispatcherOwner`가 필요하므로 **반드시 `createAndroidComposeRule<ComponentActivity>()`** 를 쓴다(`createComposeRule`는 디스패처가 없어 `BackHandler`에서 크래시).
- **⚠️ Release 변이 제외 등록(반드시 준수):** `createComposeRule`/`createAndroidComposeRule`를 쓰는 새 테스트는 **반드시** `android/app/build.gradle.kts`의 `tasks.withType<Test>().configureEach { ... if (name.contains("Release", ...)) exclude(...) }` 블록(70~93행)에 파일명 글롭으로 등록해야 한다. `compose-ui-test-manifest`가 `ComponentActivity`를 **debug 매니페스트에만** 병합하므로, 등록하지 않으면 `scripts/verify-android.sh`(무인자 = `:app:testReleaseUnitTest` 포함)의 release 단위테스트가 `ComponentActivity` 미해소로 실패한다. 이 계획의 세 테스트 클래스(`OneClickConfirmSheetTest`, `DialogueExitGuardTest`, `AppExitGuardTest`)는 각 Task에서 추가 즉시 이 목록에 등록한다.
- **색·간격·타이포는 하드코딩 금지** — `OceTheme.colors/spacing/shapes/typography` 토큰만 소비한다(기존 시트 컨벤션).
- **시트 룩:** 새 시트는 기존 `OneClickBottomSheet` 프리미티브를 `draggable = false`로 재사용한다(스크림 탭·뒤로가기로 닫힘 유지, 장식 핸들바 유지 — 프로토 "card-purge" 비드래그 시트 룩 정합).
- **프로토 정합 카피(대화 중단 시트, 변경 금지):** 제목 `대화를 그만할까요?` · 본문 `지금 나가면 이 대화는 저장되지 않아요. 상황은 다시 고를 수 있어요.` · 주버튼(primary) `계속 이어하기` · 고스트버튼 `그만하기`. 프로토의 `C · 차단 확인` 주석 배지는 실제 앱에 복제하지 않는다(기존 시트들도 프로토 주석 배지를 생략함).
- **신규 카피(앱 종료 시트 — 프로토에 없음, 본 계획의 결정값):** 제목 `앱을 종료할까요?` · 본문 `학습 기록은 저장돼요. 다음에 이어서 시작할 수 있어요.` · 주버튼(primary) `계속 사용하기` · 고스트버튼 `종료`.
- **버튼 역할 규약(양 시트 공통):** primary(강조) 버튼 = "머무르기(stay)" 안전 동작, ghost 버튼 = "떠나기(leave/exit)" 동작. 스크림 탭·시트 뒤로가기 = stay(취소)로 수렴. 이는 프로토 대화 중단 시트(`계속 이어하기`=primary, `그만하기`=ghost)와 일치한다.

### 범위 결정(가정 — 코드/스펙으로 확정 불가한 항목을 여기서 못박는다)

- **"메인 화면" = 홈(학습) 탭.** 3탭 셸에서 뒤로가기는 안드로이드 관례를 따른다: 기록/설정 탭에서 뒤로가기는 (기존 NavHost 기본 동작대로) 홈 탭으로 복귀하고, **홈 탭(시작 목적지)에서만** 뒤로가기가 종료 시트를 띄운다. 이를 위해 종료 가드의 `BackHandler`는 현재 목적지가 홈일 때만 `enabled`가 된다.
  - **홈 탭 판별 방식(주의):** `backStackEntry?.destination?.route == OceTab.Home.route` 직접 비교를 쓴다. 홈 탭이 현재 중첩 `NavHost`를 두지 않으므로 오늘 기준 정확하다. 만약 향후 홈 탭 아래 중첩 그래프가 생기면 이 == 비교가 조용히 불일치할 수 있으니(그 경우 `OceBottomNav.kt`처럼 `destination.hierarchy.any { it.route == OceTab.Home.route }`로 바꿔야 함), 그때 함께 검토한다.
- **종료 동작:** 단일 Activity 앱이므로 `종료` = `Activity.finish()`.

---

### Task 1: `OneClickConfirmSheet` 재사용 확인 시트 컴포넌트

두 화면이 공유할 제목·본문·primary/ghost 2버튼 비드래그 확인 시트. 스테이트리스 `...Content`를 분리해 테스트 가능하게 한다(기존 `OneClickReminderOptInSheet` 선례).

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickConfirmSheet.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/OneClickConfirmSheetTest.kt`

**Interfaces:**
- Consumes: 기존 `OneClickBottomSheet(onDismissRequest, draggable, content)` 프리미티브(`com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet`), 기존 `internal val SheetPrimaryHeight = 52.dp` / `SheetGhostHeight = 48.dp`(같은 패키지 `ui.component`의 `OneClickReminderOptInSheet.kt`에 선언됨 — 재사용).
- Produces:
  - `fun OneClickConfirmSheet(title: String, stayLabel: String, leaveLabel: String, onStay: () -> Unit, onLeave: () -> Unit, modifier: Modifier = Modifier, message: String? = null)` — Task 2·3이 호출.
  - `internal fun OneClickConfirmSheetContent(title: String, message: String?, stayLabel: String, leaveLabel: String, onStay: () -> Unit, onLeave: () -> Unit, modifier: Modifier = Modifier)` — 스크린샷/테스트 seam.

- [ ] **Step 1: 실패하는 테스트 작성**

`android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/OneClickConfirmSheetTest.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.component

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-560dpi", application = Application::class)
class OneClickConfirmSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersTitleMessageLabels_andStayInvokesOnStayOnly() {
        var stayCount = 0
        var leaveCount = 0
        composeRule.setContent {
            OceTheme {
                OneClickConfirmSheetContent(
                    title = "대화를 그만할까요?",
                    message = "지금 나가면 이 대화는 저장되지 않아요. 상황은 다시 고를 수 있어요.",
                    stayLabel = "계속 이어하기",
                    leaveLabel = "그만하기",
                    onStay = { stayCount++ },
                    onLeave = { leaveCount++ },
                )
            }
        }

        composeRule.onNodeWithText("대화를 그만할까요?").assertIsDisplayed()
        composeRule
            .onNodeWithText("지금 나가면 이 대화는 저장되지 않아요. 상황은 다시 고를 수 있어요.")
            .assertIsDisplayed()

        composeRule.onNodeWithText("계속 이어하기").performClick()
        assertEquals(1, stayCount)
        assertEquals(0, leaveCount)
    }

    @Test
    fun leaveLabel_invokesOnLeave() {
        var leaveCount = 0
        composeRule.setContent {
            OceTheme {
                OneClickConfirmSheetContent(
                    title = "앱을 종료할까요?",
                    message = null,
                    stayLabel = "계속 사용하기",
                    leaveLabel = "종료",
                    onStay = {},
                    onLeave = { leaveCount++ },
                )
            }
        }

        composeRule.onNodeWithText("종료").performClick()
        assertEquals(1, leaveCount)
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.ui.component.OneClickConfirmSheetTest'`
Expected: FAIL — 컴파일 에러(`OneClickConfirmSheetContent` unresolved reference).

- [ ] **Step 3: 최소 구현 작성**

`android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickConfirmSheet.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.component.primitive.OneClickBottomSheet
import com.jjundev.oneclickeng.ui.theme.OceTheme

/** 제목→본문 간격(리마인더 opt-in 시트와 동일 4dp 리듬). */
private val ConfirmLabelGap = 4.dp

/**
 * 재사용 확인 시트(비드래그 [OneClickBottomSheet] 재사용). 제목 + 선택 본문 + primary(머무르기)/ghost(떠나기)
 * 2버튼. 버튼 역할은 프로토 대화 중단 시트 정합: primary = 안전한 "머무르기"([stayLabel]/[onStay]),
 * ghost = "떠나기"([leaveLabel]/[onLeave]). 스크림 탭·시트 뒤로가기(onDismissRequest)도 [onStay]로 수렴한다.
 *
 * 콜러: 앱 종료 시트([AppExitGuard]) · 대화 중단 시트([DialogueExitGuard]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneClickConfirmSheet(
    title: String,
    stayLabel: String,
    leaveLabel: String,
    onStay: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    OneClickBottomSheet(
        onDismissRequest = onStay,
        modifier = modifier,
        draggable = false,
    ) {
        OneClickConfirmSheetContent(
            title = title,
            message = message,
            stayLabel = stayLabel,
            leaveLabel = leaveLabel,
            onStay = onStay,
            onLeave = onLeave,
        )
    }
}

/** 시트 콘텐츠(stateless) — ModalBottomSheet 래핑 없이 렌더하는 스크린샷·테스트 seam. */
@Composable
internal fun OneClickConfirmSheetContent(
    title: String,
    message: String?,
    stayLabel: String,
    leaveLabel: String,
    onStay: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        Text(
            text = title,
            style = OceTheme.typography.dialogHeader,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (message != null) {
            Text(
                text = message,
                style = OceTheme.typography.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = ConfirmLabelGap),
            )
        }
        Spacer(modifier = Modifier.height(OceTheme.spacing.xl))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
        ) {
            Button(
                onClick = onStay,
                modifier = Modifier.fillMaxWidth().height(SheetPrimaryHeight),
                shape = OceTheme.shapes.radius12,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Text(text = stayLabel, style = OceTheme.typography.sectionLabel)
            }
            TextButton(
                onClick = onLeave,
                modifier = Modifier.fillMaxWidth().height(SheetGhostHeight),
            ) {
                Text(
                    text = leaveLabel,
                    style = OceTheme.typography.sectionLabel,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
```

> 참고: `SheetPrimaryHeight`(52dp) / `SheetGhostHeight`(48dp)는 같은 패키지 `com.jjundev.oneclickeng.ui.component`의 `OneClickReminderOptInSheet.kt`에 이미 `internal val`로 선언돼 있어 import 없이 그대로 참조된다. 새로 선언하지 말 것(중복 선언 컴파일 에러).

- [ ] **Step 4: 테스트가 통과하는지 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.ui.component.OneClickConfirmSheetTest'`
Expected: PASS (2 tests).

- [ ] **Step 5: Release 변이 제외 목록에 등록**

`android/app/build.gradle.kts`의 release 제외 `exclude(...)` 블록(70~93행)에 아래 한 줄을 추가한다(기존 `"**/OneClickBottomSheetExpandTest*",` 바로 아래 등 목록 아무 곳):

```kotlin
            "**/OneClickConfirmSheetTest*",
```

- [ ] **Step 6: Release 변이 단위테스트도 통과하는지 확인**

Run: `scripts/verify-android.sh :app:testReleaseUnitTest --tests 'com.jjundev.oneclickeng.ui.component.OneClickConfirmSheetTest'`
Expected: 테스트가 release 변이에서 **제외**되어 조용히 skip(에러 없이 BUILD SUCCESSFUL). 등록 누락 시 `ComponentActivity` 미해소로 FAIL 하므로, 이 단계가 등록을 검증한다.

- [ ] **Step 7: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickConfirmSheet.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/OneClickConfirmSheetTest.kt \
        android/app/build.gradle.kts
git commit -m "feat(component): add reusable OneClickConfirmSheet (stay/leave)"
```

---

### Task 2: 학습(대화) 화면 뒤로가기 → 대화 중단 시트

대화 화면의 시스템 뒤로가기·헤더 뒤로가기가 곧장 나가지 않고 프로토 "대화를 그만할까요?" 경고 시트를 띄우게 한다. `그만하기`만 실제로 나간다.

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueExitGuard.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt` (뒤로가기 배선 교체 — 아래 정확한 위치)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueExitGuardTest.kt`

**Interfaces:**
- Consumes: Task 1의 `OneClickConfirmSheet(...)`.
- Produces: `internal fun DialogueExitGuard(onExit: () -> Unit, content: @Composable (onBackRequest: () -> Unit) -> Unit)` — Route가 콘텐츠를 이 가드로 감싼다. `onBackRequest`는 헤더 뒤로가기 화살표에 연결한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueExitGuardTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-560dpi", application = Application::class)
class DialogueExitGuardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun pressBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun systemBack_showsAbortSheet() {
        composeRule.setContent {
            OceTheme {
                DialogueExitGuard(onExit = {}) { _ -> Text("대화내용") }
            }
        }
        composeRule.onNodeWithText("대화를 그만할까요?").assertDoesNotExist()

        pressBack()

        composeRule.onNodeWithText("대화를 그만할까요?").assertIsDisplayed()
    }

    @Test
    fun leave_invokesOnExit_andClosesSheet() {
        var exited = 0
        composeRule.setContent {
            OceTheme {
                DialogueExitGuard(onExit = { exited++ }) { _ -> Text("대화내용") }
            }
        }
        pressBack()

        composeRule.onNodeWithText("그만하기").performClick()
        composeRule.waitForIdle()

        assertEquals(1, exited)
        composeRule.onNodeWithText("대화를 그만할까요?").assertDoesNotExist()
    }

    @Test
    fun stay_closesSheet_withoutExit() {
        var exited = 0
        composeRule.setContent {
            OceTheme {
                DialogueExitGuard(onExit = { exited++ }) { _ -> Text("대화내용") }
            }
        }
        pressBack()

        composeRule.onNodeWithText("계속 이어하기").performClick()
        composeRule.waitForIdle()

        assertEquals(0, exited)
        composeRule.onNodeWithText("대화를 그만할까요?").assertDoesNotExist()
    }

    @Test
    fun headerBackRequest_showsAbortSheet() {
        composeRule.setContent {
            OceTheme {
                DialogueExitGuard(onExit = {}) { onBackRequest ->
                    Button(onClick = onBackRequest) { Text("헤더뒤로") }
                }
            }
        }
        composeRule.onNodeWithText("헤더뒤로").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("대화를 그만할까요?").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.session.turn.DialogueExitGuardTest'`
Expected: FAIL — 컴파일 에러(`DialogueExitGuard` unresolved reference).

- [ ] **Step 3: `DialogueExitGuard` 구현**

`android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueExitGuard.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.turn

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jjundev.oneclickeng.ui.component.OneClickConfirmSheet

/** 프로토 대화 중단 시트 [C] 카피(변경 금지 — realization-SoT `prototype/Prototype Flow` 정합). */
private const val ABORT_TITLE = "대화를 그만할까요?"
private const val ABORT_MESSAGE = "지금 나가면 이 대화는 저장되지 않아요. 상황은 다시 고를 수 있어요."
private const val ABORT_STAY = "계속 이어하기"
private const val ABORT_LEAVE = "그만하기"

/**
 * 대화 화면 나가기 가드. 시스템 뒤로가기와 헤더 뒤로가기 화살표([content]에 넘기는 `onBackRequest`)를 모두
 * 가로채 곧장 나가지 않고 프로토 "대화를 그만할까요?" 경고 시트를 띄운다. `계속 이어하기`(primary)는 시트만
 * 닫고, `그만하기`(ghost)만 실제 나가기([onExit])를 수행한다.
 *
 * 시트가 떠 있는 동안 이 가드의 [BackHandler]는 비활성(`enabled = !abortVisible`)이라, 뒤로가기는
 * [OneClickConfirmSheet](ModalBottomSheet) 자체의 back(→ onStay)이 처리해 시트를 닫는다.
 */
@Composable
internal fun DialogueExitGuard(
    onExit: () -> Unit,
    content: @Composable (onBackRequest: () -> Unit) -> Unit,
) {
    var abortVisible by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = !abortVisible) { abortVisible = true }

    content { abortVisible = true }

    if (abortVisible) {
        OneClickConfirmSheet(
            title = ABORT_TITLE,
            message = ABORT_MESSAGE,
            stayLabel = ABORT_STAY,
            leaveLabel = ABORT_LEAVE,
            onStay = { abortVisible = false },
            onLeave = {
                abortVisible = false
                onExit()
            },
        )
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.session.turn.DialogueExitGuardTest'`
Expected: PASS (4 tests).

- [ ] **Step 4b: Release 변이 제외 목록에 등록**

`android/app/build.gradle.kts`의 release 제외 `exclude(...)` 블록(70~93행)에 아래 한 줄을 추가한다:

```kotlin
            "**/DialogueExitGuardTest*",
```

- [ ] **Step 5: Route를 가드로 감싸도록 배선 교체**

`GeneratedDialogueSession.kt`의 `GeneratedDialogueSessionRoute`를 수정한다. **세 곳**을 바꾼다.

(a) 기존 시스템 뒤로가기 핸들러 삭제. 현재 코드(83행 부근):

```kotlin
    // 대화 중 시스템 뒤로가기는 시트를 닫거나 턴을 전진시키지 않고 대화 자체를 나간다(요구). 시트가 떠 있을 땐
    // 모달 자체 back 이 onDismiss(→onExit)로, 아닐 땐 이 핸들러가 처리해 뒤로가기는 항상 "대화 나가기"로 수렴한다.
    BackHandler { onExit() }
```

위 두 줄 주석과 `BackHandler { onExit() }` 를 **삭제**한다(가드가 뒤로가기를 소유). 파일 상단의 `import androidx.activity.compose.BackHandler` 도 **삭제**한다(이 파일에서 더 이상 쓰지 않음 — detekt UnusedImports 방지).

(b) 콘텐츠 + 피드백 시트를 `DialogueExitGuard`로 감싼다. 현재 코드(130행 부근 `GeneratedDialogueSessionContent(...)` 호출부터 파일 끝 `SlimFeedbackSheet(...)` 닫는 괄호까지):

```kotlin
    GeneratedDialogueSessionContent(
        state = state,
        onViewSummary = { onViewSummary(viewModel.sessionId().orEmpty()) },
        modifier = modifier,
        header = header,
        // 헤더 뒤로가기 화살표도 "대화 나가기"로 수렴(시스템 back·시트 dismiss 와 동일 출구).
        onBack = onExit,
        dock = { task ->
            MicSessionDock(
                task = task,
                viewModel = viewModel,
                reduceMotion = reduceMotion,
            )
        },
        onReplay = { text -> viewModel.replayOpponent(text) },
        opponentSpeaker = viewModel.opponentSpeaker?.name ?: "Emma",
        learnerClipIndices = viewModel.learnerClipIndices,
        onPlayLearnerClip = { index -> viewModel.playLearnerClip(index) },
    )

    // 턴 피드백 시트는 ... (주석 유지)
    SlimFeedbackSheet(
        state = feedbackState,
        onRetry = viewModel::retryFeedback,
        onSkip = viewModel::skipFeedback,
        onNext = { viewModel.onAdvance() },
        deepState = deepState,
        deepExpanded = viewModel.deepExpanded,
        onExpandDeep = viewModel::expandDeep,
        onCollapseDeep = viewModel::collapseDeep,
        onRetryDeep = viewModel::retryDeep,
        bookmarkedLevels = bookmarkedLevels,
        onToggleBookmark = viewModel::toggleBookmark,
    )
```

이 전체 블록을 아래로 교체한다(들여쓰기 유지, `onBack = onExit` → `onBack = onBackRequest`):

```kotlin
    DialogueExitGuard(onExit = onExit) { onBackRequest ->
        GeneratedDialogueSessionContent(
            state = state,
            onViewSummary = { onViewSummary(viewModel.sessionId().orEmpty()) },
            modifier = modifier,
            header = header,
            // 헤더 뒤로가기 화살표는 시스템 back 과 동일하게 "대화 중단 시트"를 띄운다(가드가 소유).
            onBack = onBackRequest,
            dock = { task ->
                MicSessionDock(
                    task = task,
                    viewModel = viewModel,
                    reduceMotion = reduceMotion,
                )
            },
            onReplay = { text -> viewModel.replayOpponent(text) },
            // 상대 발화자 이름을 말풍선에 반영. 미배정(초기·sessionId 미도착)이면 "Emma" 폴백.
            opponentSpeaker = viewModel.opponentSpeaker?.name ?: "Emma",
            // 자기 녹음 재생: 어떤 학습자 말풍선에 버튼을 띄울지 + 탭 시 그 순번 클립 재생.
            learnerClipIndices = viewModel.learnerClipIndices,
            onPlayLearnerClip = { index -> viewModel.playLearnerClip(index) },
        )

        // 턴 피드백 시트는 드래그 없는 고정 오버레이라 대화 콘텐츠의 형제로 얹는다. Idle 이면 스스로 아무것도
        // 렌더하지 않아(early return) 턴 사이엔 숨는다. 시트는 스와이프/탭으로 줄이거나 닫을 수 없고,
        // "다음"(onNext)으로 전진하거나 시스템 뒤로가기(가드 → 대화 중단 시트)로만 벗어난다.
        SlimFeedbackSheet(
            state = feedbackState,
            onRetry = viewModel::retryFeedback,
            onSkip = viewModel::skipFeedback,
            onNext = { viewModel.onAdvance() },
            deepState = deepState,
            deepExpanded = viewModel.deepExpanded,
            onExpandDeep = viewModel::expandDeep,
            onCollapseDeep = viewModel::collapseDeep,
            onRetryDeep = viewModel::retryDeep,
            bookmarkedLevels = bookmarkedLevels,
            onToggleBookmark = viewModel::toggleBookmark,
        )
    }
```

> 주의: `GeneratedDialogueSessionContent`와 `SlimFeedbackSheet` 사이에 있던 다른 `LaunchedEffect`들(생성 상태 반영·자동발화 등, 115~128행 부근)은 **가드 밖, `DialogueExitGuard` 호출보다 위에 그대로 둔다**. 위 교체는 "콘텐츠 렌더 + 피드백 시트" 두 컴포저블만 가드 람다 안으로 옮기는 것이다.

- [ ] **Step 6: 컴파일 확인**

Run: `scripts/verify-android.sh :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (미사용 `BackHandler` import 제거로 detekt 통과).

- [ ] **Step 7: 기존 대화 콘텐츠 테스트가 여전히 통과하는지 확인(회귀 가드)**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.session.turn.*'`
Expected: PASS — `DialogueExitGuardTest`(신규) + 기존 `turn` 패키지 테스트 모두 통과.

- [ ] **Step 8: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueExitGuard.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/DialogueExitGuardTest.kt \
        android/app/build.gradle.kts
git commit -m "feat(session): confirm sheet before leaving dialogue on back"
```

---

### Task 3: 메인(홈 탭) 뒤로가기 → 앱 종료 시트

3탭 셸에서 홈 탭일 때만 뒤로가기가 "앱을 종료할까요?" 시트를 띄우게 한다. `종료`만 실제로 Activity를 종료한다.

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppExitGuard.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt` (`MainTabsScaffold`가 셸 콘텐츠를 가드로 감싼다)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/root/AppExitGuardTest.kt`

**Interfaces:**
- Consumes: Task 1의 `OneClickConfirmSheet(...)`.
- Produces: `internal fun AppExitGuard(enabled: Boolean, onExitApp: () -> Unit, content: @Composable () -> Unit)` — `enabled`는 "현재 홈 탭인가"이고, `onExitApp`은 종료 콜백(실사용 = `Activity.finish()`).

- [ ] **Step 1: 실패하는 테스트 작성**

`android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/root/AppExitGuardTest.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.root

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-560dpi", application = Application::class)
class AppExitGuardTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun pressBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun backOnHome_showsExitSheet() {
        composeRule.setContent {
            OceTheme {
                AppExitGuard(enabled = true, onExitApp = {}) { Text("탭내용") }
            }
        }
        composeRule.onNodeWithText("앱을 종료할까요?").assertDoesNotExist()

        pressBack()

        composeRule.onNodeWithText("앱을 종료할까요?").assertIsDisplayed()
    }

    @Test
    fun exit_invokesOnExitApp_andClosesSheet() {
        var exited = 0
        composeRule.setContent {
            OceTheme {
                AppExitGuard(enabled = true, onExitApp = { exited++ }) { Text("탭내용") }
            }
        }
        pressBack()

        composeRule.onNodeWithText("종료").performClick()
        composeRule.waitForIdle()

        assertEquals(1, exited)
        composeRule.onNodeWithText("앱을 종료할까요?").assertDoesNotExist()
    }

    @Test
    fun stay_closesSheet_withoutExit() {
        var exited = 0
        composeRule.setContent {
            OceTheme {
                AppExitGuard(enabled = true, onExitApp = { exited++ }) { Text("탭내용") }
            }
        }
        pressBack()

        composeRule.onNodeWithText("계속 사용하기").performClick()
        composeRule.waitForIdle()

        assertEquals(0, exited)
        composeRule.onNodeWithText("앱을 종료할까요?").assertDoesNotExist()
    }

    @Test
    fun backWhenDisabled_passesThroughAndDoesNotShowSheet() {
        // enabled=false(홈 탭 아님) → 가드는 뒤로가기를 소비하지 않고, 바깥의 폴백 핸들러로 넘어간다.
        // 폴백보다 나중에 컴포즈되는 가드가 우선순위가 높으므로(disabled 라 스킵), 폴백이 발화한다.
        var fallback = 0
        composeRule.setContent {
            OceTheme {
                BackHandler(enabled = true) { fallback++ }
                AppExitGuard(enabled = false, onExitApp = {}) { Text("탭내용") }
            }
        }
        pressBack()

        assertEquals(1, fallback)
        composeRule.onNodeWithText("앱을 종료할까요?").assertDoesNotExist()
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.ui.root.AppExitGuardTest'`
Expected: FAIL — 컴파일 에러(`AppExitGuard` unresolved reference).

- [ ] **Step 3: `AppExitGuard` 구현**

`android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppExitGuard.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.root

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.jjundev.oneclickeng.ui.component.OneClickConfirmSheet

/** 앱 종료 확인 시트 카피(프로토에 없는 신규 — 05-open-decisions 정합값). */
private const val EXIT_TITLE = "앱을 종료할까요?"
private const val EXIT_MESSAGE = "학습 기록은 저장돼요. 다음에 이어서 시작할 수 있어요."
private const val EXIT_STAY = "계속 사용하기"
private const val EXIT_LEAVE = "종료"

/**
 * 3탭 셸 종료 가드. [enabled]=true(=홈 탭 = 시작 목적지)일 때만 시스템 뒤로가기를 가로채 "앱을 종료할까요?"
 * 시트를 띄운다. `계속 사용하기`(primary)는 시트만 닫고, `종료`(ghost)만 [onExitApp]으로 실제 종료한다.
 *
 * [enabled]=false(기록/설정 탭)면 [BackHandler]가 비활성이라 뒤로가기가 기존 NavHost 기본 동작(홈 탭 복귀)으로
 * 그대로 흐른다. 시트가 떠 있는 동안엔 가드 핸들러가 비활성이라 뒤로가기는 시트 자체 back(→ onStay)이 닫는다.
 */
@Composable
internal fun AppExitGuard(
    enabled: Boolean,
    onExitApp: () -> Unit,
    content: @Composable () -> Unit,
) {
    var exitVisible by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = enabled && !exitVisible) { exitVisible = true }

    content()

    if (exitVisible) {
        OneClickConfirmSheet(
            title = EXIT_TITLE,
            message = EXIT_MESSAGE,
            stayLabel = EXIT_STAY,
            leaveLabel = EXIT_LEAVE,
            onStay = { exitVisible = false },
            onLeave = {
                exitVisible = false
                onExitApp()
            },
        )
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.ui.root.AppExitGuardTest'`
Expected: PASS (4 tests).

- [ ] **Step 4b: Release 변이 제외 목록에 등록**

`android/app/build.gradle.kts`의 release 제외 `exclude(...)` 블록(70~93행)에 아래 한 줄을 추가한다:

```kotlin
            "**/AppExitGuardTest*",
```

- [ ] **Step 5: `MainTabsScaffold`를 가드로 감싸도록 배선**

`AppRoot.kt`의 `private fun MainTabsScaffold(...)`를 수정한다. 현재 코드(153행 부근):

```kotlin
@Composable
private fun MainTabsScaffold(
    isOnline: Boolean,
    onStartSession: (promptSeed: String, topicLabel: String, topicEmoji: String, level: String, length: Int) -> Unit,
    onResume: () -> Unit,
    pendingNav: String?,
    onNavConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    LaunchedEffect(pendingNav) {
        if (pendingNav == MainActivity.NAV_HOME) {
            navController.navigate(OceTab.Home.route) {
                launchSingleTop = true
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
            }
            onNavConsumed()
        }
    }
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
}
```

아래로 교체한다(홈 탭 판별 + 종료 콜백 추가, 셸 콘텐츠를 `AppExitGuard`로 감쌈):

```kotlin
@Composable
private fun MainTabsScaffold(
    isOnline: Boolean,
    onStartSession: (promptSeed: String, topicLabel: String, topicEmoji: String, level: String, length: Int) -> Unit,
    onResume: () -> Unit,
    pendingNav: String?,
    onNavConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    // 뒤로가기 종료 시트는 시작 목적지(홈 탭)에서만 뜬다. 기록/설정 탭 뒤로가기는 NavHost 기본 동작대로
    // 홈 탭으로 복귀한다(가드 BackHandler 는 그때 비활성).
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onHomeTab = backStackEntry?.destination?.route == OceTab.Home.route
    val activity = LocalContext.current as? Activity
    LaunchedEffect(pendingNav) {
        if (pendingNav == MainActivity.NAV_HOME) {
            navController.navigate(OceTab.Home.route) {
                launchSingleTop = true
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
            }
            onNavConsumed()
        }
    }
    AppExitGuard(
        enabled = onHomeTab,
        onExitApp = { activity?.finish() },
    ) {
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
    }
}
```

그리고 `AppRoot.kt` 상단 import 블록에 아래 4개를 **추가**한다(알파벳/기존 정렬 규칙에 맞춰 삽입):

```kotlin
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.currentBackStackEntryAsState
```

> `androidx.compose.runtime.getValue`는 `AppRoot.kt`에 이미 import 돼 있다(기존 `by ... collectAsStateWithLifecycle` 사용). `by backStackEntry` 위임에 그대로 재사용된다 — 중복 추가하지 말 것. `rememberNavController`·`OceTab`·`LaunchedEffect`·`MainActivity`도 이미 import 돼 있다.

- [ ] **Step 6: 컴파일 확인**

Run: `scripts/verify-android.sh :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 셸 네비게이션 회귀 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.ui.root.*'`
Expected: PASS — `AppExitGuardTest`(신규) + 기존 `MainTabsOverlayTest`·`BootStateTest`·`AppViewModelTest` 통과(가드는 기존 오버레이 레이아웃/네비 계약을 바꾸지 않는다).

- [ ] **Step 8: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppExitGuard.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/root/AppRoot.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/root/AppExitGuardTest.kt \
        android/app/build.gradle.kts
git commit -m "feat(root): confirm sheet before exiting app on home-tab back"
```

---

### Task 4: 전체 검증

- [ ] **Step 1: 기본 검증 세트 실행**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL — 컴파일 · detekt · `:app:testDebugUnitTest` 전부 통과. 신규 3개 테스트 클래스(`OneClickConfirmSheetTest`, `DialogueExitGuardTest`, `AppExitGuardTest`) 포함.

- [ ] **Step 2: (선택) 기기/에뮬레이터에서 수동 확인**

앱을 실행해 두 흐름을 눈으로 확인한다:
1. 홈(학습) 탭에서 시스템 뒤로가기 → "앱을 종료할까요?" 시트 → `계속 사용하기`(닫힘) / `종료`(앱 종료). 기록·설정 탭에서 뒤로가기 → 홈 탭 복귀(시트 없음).
2. 대화 화면에서 시스템 뒤로가기 **및** 좌측 상단 뒤로가기 화살표 → "대화를 그만할까요?" 시트 → `계속 이어하기`(닫힘) / `그만하기`(대화 나가기, 3탭 셸 복귀).

## 범위 밖(Out of scope)

- 종료/중단 시트의 **Roborazzi 골든 스크린샷** 대조는 이 계획에 포함하지 않는다(시각 일관성은 기존 `OneClickBottomSheet`·버튼 토큰 재사용으로 확보). 프로토 픽셀 정합 골든이 필요하면 후속 작업으로 분리한다.
- 온보딩 그래프의 대화 세션은 요구에 없어 손대지 않는다(온보딩도 `GeneratedDialogueSessionRoute`를 재사용하므로 Task 2 변경이 자동 적용되지만, 온보딩 전용 뒤로가기 정책은 별도 검토 대상).
