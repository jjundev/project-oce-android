# 리마인더 시간 시트 드래그 비활성화 + 홈 배너 신형 전환 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 설정 화면의 신형 "리마인더 시간" 시트(`ReminderTimeSheet`)를 드래그로 크기 조절할 수 없게 만들고, 홈 배너 "시간 바꾸기"가 열던 구형 시계 다이얼로그를 이 신형 시트로 전환한 뒤, 구형(`OneClickTimePickerDialog` + 죽은 `ReminderSettingRow`)을 코드베이스에서 완전히 제거한다.

**Architecture:** 신형 시트는 공용 프리미티브 `OneClickBottomSheet`(M3 `ModalBottomSheet` 래핑)를 재사용한다. Material3 1.3.1 의 `ModalBottomSheet` 에는 제스처 비활성화 플래그가 없고, **드래그 제스처는 콘텐츠까지 감싸는 시트 `Surface` 에 무조건 붙는 `.draggable(...)` + `.nestedScroll(ConsumeSwipe…)`** 로 구현된다(핸들 유무와 무관 — 소스 확인 완료). 따라서 `dragHandle = null` **만으로는** 리사이즈가 막히지 않는다(핸들만 사라지고 본문을 잡아 드래그하면 여전히 시트가 움직임). 사용자 결정 = **드래그 완전 차단(M3 시트 유지)**. 구현: 신형 시트만 `dragHandle = null`(핸들 어포던스 제거) + 콘텐츠를 감싸는 `Box` 에 (a) **no-op `Modifier.draggable(Vertical)`** — 세로 드래그를 소비해 상위 Surface 의 `.draggable` 로 전파 차단(시/분 휠은 더 깊은 노드라 자체 스크롤 유지), (b) **`Modifier.nestedScroll` leftover-소비 커넥션** — 휠 경계 오버스크롤/플링이 상위로 새어 시트를 끄는 것을 `onPostScroll`/`onPostFling`(자식→부모, innermost-first)에서 전량 소비해 차단. 기존 `skipPartiallyExpanded = true`(부분 detent 없음)는 유지. 결과: 시트의 **실질 콘텐츠 영역**(핸들 자리·헤더·휠·세그먼트·버튼·좌우/상단 여백) 어디를 드래그해도 안 움직이고, 닫기는 스크림 탭·뒤로가기·"설정" 버튼만(스와이프-투-디스미스는 의도적으로 제거됨). **문서화된 잔여 한계:** M3 프리미티브(`ModalBottomSheet`)가 시트 `Surface` 안쪽 **최하단에 두는 시스템 제스처 인셋 스트립**(~nav bar 높이, "설정" 버튼 아래 얇은 띠)은 우리 `Box` 바깥·Surface 안쪽이라 여전히 드래그에 반응한다. 이 스트립은 M3 레벨(`OneClickBottomSheet` 위)이라 `ReminderTimeSheet.kt` 안에서는 못 덮는다(프리미티브 무수정 제약). 실제 리사이즈 제스처는 이 시스템 제스처 존에서 시작되지 않으므로 수용 가능한 한계로 문서화한다. 홈 전환은 동일 시그니처(`initialHour, initialMinute, onConfirm(h,m), onDismiss`)라 콜사이트 교체다.

**Tech Stack:** Kotlin, Jetpack Compose (Material3 `ModalBottomSheet`, Compose BOM 2025.01.00 = Material3 1.3.1), Robolectric + Compose UI Test(`createComposeRule`), Roborazzi 스크린샷. 검증은 `scripts/verify-android.sh`.

## Global Constraints

- **워크트리·검증(재발 함정 회피):** 이 작업의 코드/브랜치는 현재 워크트리 `/Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/reminder-sheet-drag-fix-efeb8c`(브랜치 `claude/reminder-sheet-drag-fix-efeb8c`)에 있다. 모든 gradle 은 이 워크트리 루트에서 `scripts/verify-android.sh …` 로만 실행한다(직접 `./gradlew` 금지 — `google-services.json`·`GRADLE_USER_HOME` 격리, 공유 캐시 오염 회피). 새 test 파일이 컴파일에 안 잡히면 인자로 `--rerun-tasks` 를 덧붙인다.
- **인자 형식:** 특정 태스크만 돌릴 땐 `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReminderTimeSheetDragHandleTest*'` 처럼 넘긴다. 인자 없이 실행하면 기본 4종(`:app:detekt :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:testReleaseUnitTest`)이 돈다.
- **스코프: 신형 시트만 드래그 차단.** `dragHandle = null` + 드래그 차단 `Box`(no-op draggable + nestedScroll leftover 소비)는 `ReminderTimeSheet` 콜사이트에서만 적용한다. 프리미티브 `OneClickBottomSheet` 의 기본값(`BottomSheetDefaults.DragHandle()`)·`.draggable`/`.nestedScroll` 동작과 나머지 4개 시트(권한 프라이밍·리마인더 opt-in·주제 선택·Google 저장·설정 정리)·`SlimFeedbackSheet` 는 건드리지 않는다. 리마인더 opt-in 시트는 이미 커스텀 핸들(`ReminderOptInDragHandle`)을 쓰므로 무관.
- **완전 펼침 수정 유지:** 프리미티브의 `sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)` 는 그대로 둔다. 되돌리지 말 것. (이 값이 참이라 시트는 완전 펼침 = content-hug 로 열려, 시트 nestedScroll 의 `onPreScroll`/`onPreFling` 은 위로 여유가 없어 스스로 0 만 소비한다 — 그래서 pre 단계 누수는 없고, 차단은 post 단계만 하면 된다.)
- **닫기 경로:** 신형 시트에는 "취소" 버튼이 없다(확인 버튼 "설정"만). 드래그 완전 차단으로 스와이프-투-디스미스도 사라지므로, 남는 취소 경로는 **스크림 탭·뒤로가기**(둘 다 `onDismissRequest` 경로 — 드래그와 독립이라 유지됨) + "설정" 확정이다. `confirmValueChange` 로 Hidden 을 막는 방식은 스크림/뒤로가기 닫기까지 깨므로 **쓰지 않는다**.
- **홈 전환 시 UX 변화(의도됨):** 홈 "시간 바꾸기"가 구형 시계 `AlertDialog`(확인/취소)에서 신형 휠 바텀시트로 바뀐다. 명시적 "취소" 버튼은 사라지고 스크림/뒤로가기로 닫는다 — 이것이 "구형→신형 전환"의 정의다.
- 코틀린 detekt 통과(미사용 import/private 멤버는 빌드 실패). Roborazzi 골든은 gitignore(`build/`) — 커밋에 PNG 포함 금지, record-and-eyeball.
- **커밋 트레일러:** 각 커밋 메시지 끝에 다음 줄을 넣는다.
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`

---

## File Structure

**수정 — 신형 시트 드래그 완전 차단(이 시트만)**
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/ReminderTimeSheet.kt` — `ReminderTimeSheet` 의 `OneClickBottomSheet(...)` 호출을 (a) `dragHandle = null`, (b) `contentPadding = PaddingValues(0.dp)`(여백을 시트 내부 `Box` 로 이관 → 차단 모디파이어가 여백까지 덮음), (c) 콘텐츠를 감싸는 `Box`(no-op `draggable(Vertical)` + `nestedScroll` leftover 소비 커넥션 + 내부 `padding`) 로 바꾼다. leftover 소비 커넥션은 `rememberBlockSheetDragScrollConnection()` private 헬퍼로 둔다. `ReminderTimeSheetContent`(stateless 콘텐츠) 내부는 무변경.

**생성 — 드래그 차단 회귀 테스트**
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/ReminderTimeSheetDragHandleTest.kt` — 실제 `ReminderTimeSheet` 모달을 Robolectric 으로 렌더해 (1) **행위 테스트(핵심):** 헤더(비스크롤 영역)를 아래로 크게 스와이프해도 시트가 닫히거나 사라지지 않음("설정" 여전히 표시)을 단언 — 드래그가 실제로 막혔음을 검증. (2) **보조:** 드래그 핸들이 부여하는 `SemanticsActions.Dismiss` 노드가 없음(=`dragHandle = null` 적용됨).

**수정 — 릴리스 변이 테스트 제외**
- `android/app/build.gradle.kts:70-84` — 실제 모달 + NATIVE 그래픽 + `performTouchInput` 을 쓰는 테스트는 Release 변이에서 제외하는 기존 관례(`OneClickBottomSheetExpandTest` 등)를 따라 `"**/ReminderTimeSheetDragHandleTest*"` 를 exclude 목록에 추가.

**수정 — 홈 배너 신형 전환**
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt` — import 교체(`OneClickTimePickerDialog` → `ReminderTimeSheet`), 상태 변수 `timePickerVisible` → `timeSheetVisible` 리네임, `if (…) { OneClickTimePickerDialog(...) }` 블록을 `ReminderTimeSheet(...)` 로 교체.

**삭제 — 구형 완전 제거**
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickTimePicker.kt` — 파일 통째 삭제(구형 시계 다이얼로그 + 프리뷰).
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickReminderOptInSheet.kt` — 죽은 `ReminderSettingRow` 함수 + `ReminderSettingRowPreview` + 그로써 고아가 되는 private 상수(`REMINDER_ICON_BOX`·`REMINDER_ICON_BG_ALPHA`·`REMINDER_ROW_INSET`·`REMINDER_LABEL_GAP`·`ReminderTrimmedLineHeight`) + 미사용 import 제거. `REMINDER_DEFAULT_HOUR`/`REMINDER_DEFAULT_MINUTE`(HomeReminderViewModel 소비)·`OptInLabelGap`·opt-in 시트 본체는 보존.

---

## Task 1: 신형 `ReminderTimeSheet` 드래그 완전 차단(리사이즈 불가화)

M3 `ModalBottomSheet` 은 드래그 제스처를 콘텐츠까지 감싸는 시트 `Surface` 의 `.draggable`/`.nestedScroll` 로 구현하며 이를 끄는 파라미터가 없다(1.3.1). 그래서 (1) `dragHandle = null`(핸들 어포던스 제거) + (2) 콘텐츠를 감싸는 `Box` 에 no-op `draggable`(직접 드래그 소비) + leftover-소비 `nestedScroll` 커넥션(휠 오버스크롤 누수 차단)을 얹어 **시트가 어떤 드래그에도 움직이지 않게** 한다. 시/분 휠은 더 깊은 노드라 자체 스크롤을 유지한다. 회귀는 "헤더를 크게 스와이프해도 시트가 닫히지 않음"(행위) + "핸들 `Dismiss` 시맨틱 부재"(보조)로 단언한다.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/ReminderTimeSheet.kt:73-75`(시트 본체) + 상단 import 블록 + 파일 하단에 private 헬퍼 추가
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/ReminderTimeSheetDragHandleTest.kt`
- Modify: `android/app/build.gradle.kts:70-84` (Release 변이 테스트 exclude 목록)

**Interfaces:**
- Consumes: `OneClickBottomSheet(onDismissRequest, modifier, sheetState, contentPadding, dragHandle, content)` — 프리미티브(`ui/component/primitive/OneClickBottomSheet.kt:47`). `dragHandle: @Composable (() -> Unit)? = …` 에 `null` 을 넘기면 핸들 Box(+ `Dismiss` 시맨틱) 미렌더. `contentPadding` 에 `PaddingValues(0.dp)` 를 넘겨 여백을 시트 내부 `Box` 로 이관한다(차단 모디파이어가 여백까지 덮게).
- Consumes: `OceTheme.spacing.sheetPadding`(24dp), `OceTheme.spacing.sheetContentBottom`(24dp) — `ui/theme/OceSpacing.kt`.
- Produces: 시그니처 무변경 — `fun ReminderTimeSheet(initialHour: Int, initialMinute: Int, onConfirm: (hour: Int, minute: Int) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier)`. Task 2 가 홈에서 이 시그니처로 호출한다.

- [ ] **Step 1: 실패하는 테스트 작성**

`android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/ReminderTimeSheetDragHandleTest.kt` 신규 생성:

```kotlin
package com.jjundev.oneclickeng.feature.settings

import android.app.Application
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodes
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 회귀: 신형 [ReminderTimeSheet] 는 드래그로 크기 조절·이동이 불가능하다.
 * 핵심 행위 테스트 = 비스크롤 헤더를 크게 아래로 스와이프해도 시트가 닫히거나 화면 밖으로 밀리지 않는다
 * (닫혔다면 "설정" 버튼이 뷰포트를 벗어나 assertIsDisplayed 실패). 보조 = 드래그 핸들이 부여하는
 * [SemanticsActions.Dismiss] 시맨틱 부재(dragHandle = null 적용됨).
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class ReminderTimeSheetDragHandleTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun setSheet() {
        composeRule.setContent {
            OceTheme {
                ReminderTimeSheet(
                    initialHour = 20,
                    initialMinute = 0,
                    onConfirm = { _, _ -> },
                    onDismiss = {},
                )
            }
        }
        composeRule.waitForIdle()
    }

    // 핵심: 헤더를 아래로 크게 스와이프해도 드래그가 막혀 시트가 유지된다.
    @Test
    fun dragging_the_header_down_does_not_dismiss_or_move_the_sheet() {
        setSheet()
        composeRule.onNodeWithText("설정").assertIsDisplayed()

        // 헤더("리마인더 시간", 비스크롤 영역)에서 시작해 한참 아래로 스와이프.
        composeRule.onNodeWithText("리마인더 시간").performTouchInput {
            swipeDown(startY = centerY, endY = centerY + 1200f, durationMillis = 200)
        }
        composeRule.waitForIdle()

        // 드래그가 막혔으면 시트는 그대로 → 확인 버튼이 여전히 뷰포트에 보인다.
        composeRule.onNodeWithText("설정").assertIsDisplayed()
    }

    // 보조: 드래그 핸들 부재 → 핸들이 부여하는 Dismiss 시맨틱이 없다.
    @Test
    fun sheet_has_no_drag_handle_dismiss_action() {
        setSheet()
        composeRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .assertCountEquals(0)
    }
}
```

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReminderTimeSheetDragHandleTest*'`
Expected: 둘 다 FAIL. 현재 시트는 기본 핸들 + 드래그 가능한 Surface 라 (a) 헤더 스와이프가 시트를 dismiss/이동시켜 "설정" 이 뷰포트를 벗어나고, (b) 기본 핸들의 `Dismiss` 시맨틱이 존재해 개수가 0 이 아니다.

주의(Robolectric 제스처 신뢰성): 만약 `dragging_the_header_down_…` 이 이 단계(수정 전)에서 FAIL 이 아니라 통과해 버리면, Robolectric 이 스와이프를 dismiss 임계까지 시뮬레이트하지 못한 것이다 — 그 경우 스와이프 거리를 키우거나(`endY = centerY + 2000f`) `durationMillis` 를 조정해 **수정 전 반드시 RED 가 되게** 만든 뒤 진행한다(RED 를 못 만들면 이 테스트는 회귀를 못 잡으므로, 대신 Step 7 수동 검증을 1차 근거로 삼고 그 사실을 커밋 메시지에 남긴다).

- [ ] **Step 3: `ReminderTimeSheet` 에 드래그 차단 적용**

`ReminderTimeSheet.kt` 상단 import 블록에 다음을 추가한다(각자 알파벳/그룹 순 위치에):

```kotlin
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
```

주의: `Box` 는 이미 import 되어 있다(`androidx.compose.foundation.layout.Box`, WheelColumn 에서 사용). `fillMaxWidth`·`padding`·`remember` 도 이미 있다.

`ReminderTimeSheet` 본체(현재 73-75줄)를 다음으로 교체:

```kotlin
    OneClickBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        // 드래그 핸들 제거 — 핸들은 시트 Surface 의 드래그 어포던스다(신형 시트만).
        dragHandle = null,
        // 여백을 시트 내부 Box 로 이관 → 아래 드래그 차단 모디파이어가 좌우/상하 여백까지 덮는다.
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // 휠 경계 오버스크롤/플링이 상위 시트로 새어 시트를 끄는 것을 post 단계에서 전량 소비.
                    .nestedScroll(rememberBlockSheetDragScrollConnection())
                    // 세로 직접 드래그를 소비만 하고 아무 동작 안 함 → 상위 Surface 의 .draggable 로 전파 차단.
                    // (시/분 휠은 더 깊은 노드라 자체 스크롤 유지, 버튼/세그먼트 탭도 영향 없음.)
                    .draggable(
                        state = rememberDraggableState { /* no-op: 시트 이동 없음 */ },
                        orientation = Orientation.Vertical,
                    )
                    .padding(
                        start = OceTheme.spacing.sheetPadding,
                        end = OceTheme.spacing.sheetPadding,
                        top = OceTheme.spacing.sheetPadding,
                        bottom = OceTheme.spacing.sheetContentBottom,
                    ),
        ) {
            ReminderTimeSheetContent(initialHour = initialHour, initialMinute = initialMinute, onConfirm = onConfirm)
        }
    }
```

그리고 파일 하단(`WheelColumn` 아래, `mutableStateOfPeriod` 헬퍼 근처)에 private 헬퍼를 추가한다:

```kotlin
/**
 * 휠(내부 스크롤러)이 스크롤 경계를 넘길 때 남는 델타·플링이 상위 시트의 nestedScroll 로 새어나가
 * 시트를 드래그하는 것을 막는다. post 단계(자식→부모, innermost-first)에서 leftover 를 전량 소비한다.
 * pre 단계는 시트가 완전 펼침(content-hug)이라 위로 여유가 없어 시트 스스로 0 만 소비하므로 손대지 않는다.
 */
@Composable
private fun rememberBlockSheetDragScrollConnection(): NestedScrollConnection =
    remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                available

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }
```

- [ ] **Step 4: `ReminderTimeSheetDragHandleTest` 를 Release 변이에서 제외**

`android/app/build.gradle.kts` 의 `if (name.contains("Release", ignoreCase = true)) { exclude( … ) }` 목록(70-84줄)에 한 줄 추가(기존 `"**/OneClickBottomSheetExpandTest*",` 바로 아래):

```kotlin
            "**/ReminderTimeSheetDragHandleTest*",
```

이유: 이 테스트는 `OneClickBottomSheetExpandTest` 처럼 실제 `ModalBottomSheet` + `GraphicsMode.NATIVE` + `performTouchInput` 을 쓴다. 해당 계열은 Release 변이에서 제외하는 것이 기존 관례다(같은 exclude 목록에 이미 존재).

- [ ] **Step 5: 테스트 실행 → 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReminderTimeSheetDragHandleTest*'`
Expected: 두 테스트 모두 PASS. `dragging_the_header_down_…` 이 GREEN = 헤더 드래그가 no-op draggable 에 소비돼 시트가 유지됨. `sheet_has_no_drag_handle_dismiss_action` GREEN = `dragHandle = null` 적용됨.

- [ ] **Step 6: 기존 설정 스크린샷 골든 불변 확인**

`SettingsScreenScreenshotTest.reminder_time_sheet` 는 `ReminderTimeSheetContent`(모달 래핑·차단 Box 없는 순수 콘텐츠)만 렌더하므로 이번 변경(모달 래퍼의 `dragHandle`·`contentPadding`·`Box`)의 영향을 받지 않는다 — 골든 재기록 불필요.

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SettingsScreenScreenshotTest*'`
Expected: PASS(골든 불일치 없음). 불일치가 나면 콘텐츠(`ReminderTimeSheetContent`)를 잘못 건드린 것이니 Step 3 을 재검토(콘텐츠는 무변경이어야 함).

- [ ] **Step 7: 앱 실행으로 시각 확인(수동, 필수)**

설정 → "리마인더 시간" 시트를 연다. 확인 사항: (a) 상단 드래그 핸들 바가 없다, (b) **헤더/세그먼트/버튼 등 본문을 잡고 위·아래로 드래그해도 시트가 전혀 움직이지 않는다**, (c) **시/분 휠은 위·아래로 정상 스크롤된다**(휠을 끝까지 넘겨도 시트가 안 끌린다), (d) 세그먼트 탭·휠 항목 탭·"설정" 버튼 탭 모두 정상 동작(약간의 손가락 흔들림 있는 탭도 안 씹힘), (e) 스크림 탭·뒤로가기로 닫힌다(스와이프-다운으로는 안 닫힘 = 의도됨), (f) 상단 여백(제목까지 간격)이 라운드 코너에 대해 답답하지 않다. (f)가 어색하면 Step 3 의 `top` 값만 조정하고 Step 5-6 재실행.

(g) **잔여 한계 확인:** "설정" 버튼 아래 **최하단 시스템 제스처 인셋 띠**에서 드래그를 시작하면 시트가 미세하게 반응할 수 있다(Architecture 의 문서화된 한계). 이는 M3 프리미티브 구조상 `ReminderTimeSheet.kt` 로는 못 막는 부분이니, 실제로 그런지 한 번 확인하고 **그대로 수용**한다(핸들·본문 차단이 핵심 요구를 충족). 만약 이 띠까지 반드시 막아야 한다면 스코프 확장이 필요하다: `OneClickBottomSheet` 에 `contentWindowInsets` 오버라이드 파라미터(기본값=현행 `BottomSheetDefaults.windowInsets`, 다른 4개 시트 무영향)를 추가하고 nav 인셋 처리를 차단 `Box` 내부로 옮기는 별도 작업 — 이 플랜 범위 밖이며, 진행 전 사용자 확인 필요.

- [ ] **Step 8: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/ReminderTimeSheet.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/ReminderTimeSheetDragHandleTest.kt \
        android/app/build.gradle.kts
git commit -m "feat(settings): make reminder-time sheet fully non-draggable

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: 홈 배너 "시간 바꾸기" → 신형 `ReminderTimeSheet` 전환

홈 배너가 열던 구형 시계 다이얼로그(`OneClickTimePickerDialog`)를 Task 1 의 신형 시트로 교체한다. 시그니처가 동일하므로 콜사이트 + import + 상태 변수명만 바꾼다.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt:75`(import), `:148`(상태 변수), `:212`(트리거), `:214-224`(렌더 블록)

**Interfaces:**
- Consumes: `ReminderTimeSheet(initialHour, initialMinute, onConfirm, onDismiss, modifier)` — Task 1 이 소유(무변경 시그니처). `feature.settings` 패키지에 있음.
- Consumes(기존): `reminderState.hour`/`reminderState.minute`(`HomeReminderUiState`), `reminderViewModel.setReminderTime(hour, minute)`(`HomeReminderViewModel.kt:84`).

- [ ] **Step 1: import 교체**

`HomeScreen.kt:75` 의 다음 줄을 삭제:

```kotlin
import com.jjundev.oneclickeng.ui.component.OneClickTimePickerDialog
```

그리고 import 블록의 `feature.reminder` 그룹 근처(알파벳/패키지 순 유지)에 추가:

```kotlin
import com.jjundev.oneclickeng.feature.settings.ReminderTimeSheet
```

- [ ] **Step 2: 상태 변수 리네임 + 렌더 블록 교체**

`HomeScreen.kt:148` 의:

```kotlin
    var timePickerVisible by remember { mutableStateOf(false) }
```
을:
```kotlin
    var timeSheetVisible by remember { mutableStateOf(false) }
```
로 바꾼다.

`HomeScreen.kt:212` 의:
```kotlin
        onChangeReminderTime = { timePickerVisible = true },
```
을:
```kotlin
        onChangeReminderTime = { timeSheetVisible = true },
```
로 바꾼다.

`HomeScreen.kt:214-224` 의 블록:
```kotlin
    if (timePickerVisible) {
        OneClickTimePickerDialog(
            initialHour = reminderState.hour,
            initialMinute = reminderState.minute,
            onConfirm = { h, m ->
                reminderViewModel.setReminderTime(h, m)
                timePickerVisible = false
            },
            onDismiss = { timePickerVisible = false },
        )
    }
```
을 다음으로 교체:
```kotlin
    if (timeSheetVisible) {
        ReminderTimeSheet(
            initialHour = reminderState.hour,
            initialMinute = reminderState.minute,
            onConfirm = { h, m ->
                reminderViewModel.setReminderTime(h, m)
                timeSheetVisible = false
            },
            onDismiss = { timeSheetVisible = false },
        )
    }
```

- [ ] **Step 3: 컴파일 + 기존 배너 스크린샷 회귀 확인**

Run: `scripts/verify-android.sh :app:detekt :app:testDebugUnitTest --tests '*ReminderScreenshotTest*'`
Expected: PASS. `ReminderScreenshotTest.home_light_reminder_banner` 는 `HomeContent`(stateless, 배너 렌더)만 검증하며 시트 호스팅은 stateful `HomeScreen` 소관이라 이 변경의 영향 없음 — 골든 불변. detekt 는 `OneClickTimePickerDialog` import 제거·변수 리네임의 미사용 참조가 없음을 확인.

주의: `HomeScreen`(stateful)은 `hiltViewModel()` 의존이라 격리 단위 테스트 대상이 아니다. 시트 교체 자체의 행위 검증은 Step 4 수동 실행으로 한다.

- [ ] **Step 4: 앱 실행으로 시각 확인(수동)**

리마인더가 켜진 상태로 홈에 진입해 "리마인더를 켰어요" 배너의 "시간 바꾸기"를 탭한다. 확인 사항: (a) 구형 시계 다이얼로그가 아니라 신형 휠 바텀시트(오전/오후 세그먼트 + 시/분 휠 + "설정")가 뜬다, (b) 드래그 핸들이 없다(Task 1 결과 공유), (c) "설정" 확정 시 배너 시각이 갱신된다.

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/HomeScreen.kt
git commit -m "feat(home): open new reminder-time sheet from banner (replace legacy clock dialog)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: 구형(`OneClickTimePickerDialog` + 죽은 `ReminderSettingRow`) 완전 제거

Task 2 이후 구형 다이얼로그의 유일한 프로덕션 참조는 프리뷰에서만 쓰이는 죽은 `ReminderSettingRow` 뿐이다. 둘 다 제거해 "구형→신형 전환"을 코드베이스 수준에서 완결한다. 고아가 되는 private 상수·import 도 함께 정리한다.

**Files:**
- Delete: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickTimePicker.kt`(파일 전체)
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickReminderOptInSheet.kt`(`ReminderSettingRow`·프리뷰·고아 상수·미사용 import 삭제)

**Interfaces:**
- Produces: 없음(순수 삭제). `REMINDER_DEFAULT_HOUR`/`REMINDER_DEFAULT_MINUTE`(HomeReminderViewModel 소비)·`OptInLabelGap`·`OneClickReminderOptInSheet`/`OneClickReminderOptInSheetContent`(opt-in 시트 본체)는 보존한다.

- [ ] **Step 1: 삭제 전 참조 스냅샷(가드)**

Run:
```bash
grep -rn "OneClickTimePickerDialog\|ReminderSettingRow" android/app/src
```
Expected: `OneClickTimePickerDialog` 는 (a) `OneClickTimePicker.kt`(정의+프리뷰), (b) `OneClickReminderOptInSheet.kt`(`ReminderSettingRow` 내부 + KDoc), (c) `ReminderTimeSheet.kt:62`(단순 주석 문자열)에만 남아야 한다 — **HomeScreen 참조는 Task 2 로 사라졌어야 함**(없으면 안 됨). `ReminderSettingRow` 는 `OneClickReminderOptInSheet.kt`(정의+프리뷰+주석)에만 남아야 한다. HomeScreen 참조가 아직 있으면 Task 2 가 미완이니 중단.

- [ ] **Step 2: 구형 다이얼로그 파일 삭제**

```bash
git rm android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickTimePicker.kt
```

- [ ] **Step 3: 죽은 `ReminderSettingRow` + 프리뷰 삭제**

`OneClickReminderOptInSheet.kt` 에서 다음 두 선언을 통째로 삭제한다:
1. `ReminderSettingRow` 함수 — 그 위 KDoc 블록(`/** C19 리마인더 설정 행 = [OneClickSwitch] + 조건부 [OneClickTimePickerDialog](C10) … */`)부터 함수 닫는 `}`(내부의 `if (showPicker) { OneClickTimePickerDialog(...) }` 포함)까지 전부.
2. `ReminderSettingRowPreview` — `@Suppress("UnusedPrivateMember")` + `@Preview(...)` + `private fun ReminderSettingRowPreview() { … }` 전부.

- [ ] **Step 4: 고아 private 상수 + 스테일 주석 정리**

`OneClickReminderOptInSheet.kt` 상단 상수 블록에서 다음을 삭제한다(모두 `ReminderSettingRow` 전용이라 Step 3 이후 미사용):

```kotlin
/** 설정 카드 안 리마인더 행 선행 아이콘 시각 상수(SettingsScreen 정합). */
private val REMINDER_ICON_BOX = 40.dp
private const val REMINDER_ICON_BG_ALPHA = 0.10f
private val REMINDER_ROW_INSET = 68.dp
```
```kotlin
/** 제목↔보조 문구 세로 간격(프로토 실측 2~3dp) + lineHeight leading 제거(SettingsScreen 정합). */
private val REMINDER_LABEL_GAP = 2.dp
```
```kotlin
private val ReminderTrimmedLineHeight =
    LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.Both)
```

그리고 보존하는 `OptInLabelGap` 의 주석에서 삭제된 상수 참조를 제거한다. 다음:
```kotlin
/** opt-in 시트 제목→본문 간격(프로토 4px). ReminderSettingRow의 REMINDER_LABEL_GAP(2dp)과는 다른 맥락. */
private val OptInLabelGap = 4.dp
```
을:
```kotlin
/** opt-in 시트 제목→본문 간격(프로토 4px). */
private val OptInLabelGap = 4.dp
```
로 바꾼다.

- [ ] **Step 5: 고아 import 제거**

`OneClickReminderOptInSheet.kt` 에서 Step 3-4 이후 미사용이 되는 다음 import 를 삭제한다:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.text.style.LineHeightStyle
import com.jjundev.oneclickeng.ui.component.primitive.OneClickSwitch
import com.jjundev.oneclickeng.ui.foundation.OceIconSize
```

주의: `OceIcon`·`OneClickIcon`·`size`·`clip`·`background` import 는 opt-in 시트 본체(`OneClickReminderOptInSheetContent`)가 계속 쓰므로 **삭제하지 않는다**. 판단이 애매하면 다음 Step 의 detekt(`UnusedImports`) 결과를 근거로 정리한다.

- [ ] **Step 6: 전체 검증(컴파일 + detekt + 테스트)**

Run: `scripts/verify-android.sh`
Expected: 4종 태스크 모두 PASS. 특히 detekt 가 미사용 import/private 멤버를 잡지 않아야 한다. detekt 가 추가 미사용 import 를 지적하면(예: Step 5 목록 밖) 그 항목도 삭제 후 재실행. 컴파일 에러가 나면(보존해야 할 상수를 지웠을 가능성) 해당 상수를 복구.

주의(공유 워크트리 함정): 만약 전체 verify 의 빨간불이 본 변경과 무관한 외부 테스트(과거 사례: `MicDockTogglePositionTest` 등)라면 무시하되, **본 변경 파일이 유발한 실패가 아님을 `git diff` 로 확인**한다.

- [ ] **Step 7: 참조 잔존 확인(가드)**

Run:
```bash
grep -rn "OneClickTimePickerDialog\|ReminderSettingRow" android/app/src
```
Expected: **매치 0건**(단, `ReminderTimeSheet.kt:62` 의 대체 안내 주석 "…M3 시계 다이얼(OneClickTimePickerDialog) 대체…" 은 남아 있어도 무방 — 순수 문자열이며 컴파일 무관. 원하면 이 주석의 괄호 참조만 다듬어도 되나 필수는 아님).

- [ ] **Step 8: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickReminderOptInSheet.kt
git commit -m "chore(reminder): remove legacy time-picker dialog and dead settings row

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

(`git rm` 한 `OneClickTimePicker.kt` 삭제는 이 커밋에 함께 스테이징된다.)

---

## 검증 요약(전체 완료 후)

- [ ] `scripts/verify-android.sh` (인자 없이 = 기본 4종) 그린.
- [ ] 신형 시트: 드래그 핸들 없음 + 본문(핸들자리/헤더/휠/세그먼트/버튼/여백) 드래그로 시트 안 움직임 + 시/분 휠 스크롤 정상 + 스크림/뒤로/버튼 닫기 정상(스와이프-다운 닫기는 의도적 제거). 최하단 시스템 제스처 인셋 띠는 문서화된 잔여 한계로 수용(Task 1 Step 7 수동).
- [ ] 홈 배너 "시간 바꾸기" → 신형 휠 시트 노출(Task 2 Step 4 수동).
- [ ] `grep OneClickTimePickerDialog\|ReminderSettingRow` → 코드 참조 0건(주석 제외).
