# PR D — 공용 시트/스페이싱 폴리시 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 시트 세로 리듬(핸들→제목 갭·최하단 버튼 하단 패딩)을 `OneClickBottomSheet` 프리미티브가 단일 소스로 소유하고, 엣지투엣지 풀스크린 형제 화면에 상태바 인셋을 통일 적용한다.

**Architecture:** 이 앱은 Compose 단일 Activity + `enableEdgeToEdge()`(MainActivity.kt:28)라 View 시스템의 `fitsSystemWindows="true"` 속성이 존재하지 않는다. 세 가지 요청을 Compose 이디엄으로 번역한다: (1) `fitsSystemWindows` → 풀스크린 형제 화면 루트에 `Modifier.statusBarsPadding()`, (2)·(3) 시트 핸들→제목 갭과 최하단 하단 패딩 → 공용 `OceSheetDefaults.contentPadding`(PaddingValues) 단일 상수를 프리미티브가 적용하고, Robolectric 스크린샷이 프리미티브를 우회해 시트를 손수 재현하므로 같은 상수를 스크린샷 재현부도 재사용한다.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Hilt, Roborazzi(Robolectric NATIVE 그래픽) 스크린샷 대조, Gradle. 검증은 `scripts/verify-android.sh`(워크트리 격리 GRADLE_USER_HOME).

## Global Constraints

- 값 정본: 핸들→첫 제목 갭 `sheetHandleGap = 12.dp`, 최하단 버튼 하단 패딩 `sheetContentBottom = 24.dp`(둘 다 grill-yourself에서 사용자 확정). 가로 거터는 기존 `sheetPadding = 24.dp` 재사용.
- 시트 세로 리듬의 **단일 소스**는 `OceSheetDefaults.contentPadding` 하나뿐 — 콜러/스크린샷이 각자 하드코딩하지 않는다.
- 검증 명령은 반드시 `scripts/verify-android.sh` 경유(직접 `./gradlew` 금지 — 워크트리 캐시 오염·google-services.json 부재 함정). 스크린샷 기록은 `-Proborazzi.record` 프로퍼티를 붙인다.
- 기본 검증 세트: `:app:detekt :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:testReleaseUnitTest` (인자 없이 `scripts/verify-android.sh`).
- 코틀린 코드는 detekt 통과. import는 알파벳 정렬(단 `ktlintMainSourceSetCheck`는 master 선존재 위반으로 기본 세트에서 제외됨).
- **스코프 밖(무변경)**: `SlimFeedbackSheet`(raw ModalBottomSheet 커스텀 오버레이 — 스크린샷 정합 사유로 자체 `navigationBarsPadding()+bottom=22` 보존), `DialogueGeneratingScreen`의 `ReadyBottomSheet`(인-컴포지션 커스텀 오버레이, SlimFeedback류), 3탭 화면 상단 인셋(전역 `Scaffold` innerPadding이 이미 흡수 — AppRoot.kt:140-144), `DialogueTurnScreen`(이미 `DialogueHeader(Modifier.statusBarsPadding())` 처리 — DialogueTurnScreen.kt:157), 홈 히어로.

---

## File Structure

**신규 파일**
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/theme/OceSpacingTokensTest.kt` — 신규 토큰 값 회귀 가드(red-green 앵커).

**수정 파일 — 코어 프리미티브·토큰**
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/theme/OceSpacing.kt` — `sheetHandleGap`·`sheetContentBottom` 시맨틱 별칭 2개 추가.
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/primitive/OneClickBottomSheet.kt` — `OceSheetDefaults` object 추가 + 프리미티브가 content를 padding Column(+ `navigationBarsPadding()`)으로 감싸 세로 리듬 소유. 정책 KDoc.

**수정 파일 — 시트 콜러 5종(자체 외곽 padding 제거)**
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickReminderOptInSheet.kt:98-104`
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickPermissionPrimingSheet.kt:110-115`
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/google/GoogleSavePromptSheet.kt:98-102`
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/topic/TopicSelectScreen.kt:100-104`
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt:461`

**수정 파일 — 스크린샷 재현부(프리미티브 우회 → 공용 상수 재적용)**
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ui/ReminderScreenshotTest.kt:104-151`(`captureSheet` 헬퍼)
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/topic/TopicSelectScreenshotTest.kt:112-116`

**수정 파일 — 풀스크린 형제 상단 인셋(fitsSystemWindows 번역)**
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/level/LevelQuestionScreen.kt:71-76`
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/topic/TopicQuestionScreen.kt:69-75`
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryScreen.kt:85, 132`
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreen.kt:135-136`

**수정 파일 — 문서**
- `docs/adr/` — 짧은 ADR 노트(시트 세로 리듬 정책 + 풀스크린 statusBarsPadding 규칙).

---

## Task 1: 공용 시트 스페이싱 토큰 추가

신규 시맨틱 토큰 2개를 추가하고 값 회귀 테스트로 못박는다. 순수 추가라 동작 변화 없음 — 안전한 독립 커밋.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/theme/OceSpacing.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/theme/OceSpacingTokensTest.kt` (create)

**Interfaces:**
- Consumes: 없음.
- Produces: `OceSpacing.sheetHandleGap: Dp`(= 12.dp), `OceSpacing.sheetContentBottom: Dp`(= 24.dp). Task 2가 `OceSheetDefaults`에서 소비.

- [ ] **Step 1: 실패 테스트 작성**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/theme/OceSpacingTokensTest.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** 시트 세로 리듬 토큰 정본(grill 확정: 핸들갭 12dp / 하단 24dp). 값 변경 시 이 가드가 먼저 깨진다. */
class OceSpacingTokensTest {
    private val tokens = OceSpacingTokens

    @Test
    fun sheetHandleGap_is_12dp() {
        assertEquals(12.dp, tokens.sheetHandleGap)
    }

    @Test
    fun sheetContentBottom_is_24dp() {
        assertEquals(24.dp, tokens.sheetContentBottom)
    }

    @Test
    fun sheetPadding_horizontal_stays_24dp() {
        assertEquals(24.dp, tokens.sheetPadding)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*OceSpacingTokensTest*'`
Expected: 컴파일 실패 — `Unresolved reference: sheetHandleGap` / `sheetContentBottom` (아직 토큰 미정의).

- [ ] **Step 3: 토큰 추가**

Modify `OceSpacing.kt` — 시맨틱 별칭 블록(현재 `sheetPadding`/`sectionGap`/`actionGap`/`loadingPadding` 아래)에 2줄 추가:

```kotlin
    // 시맨틱 별칭
    val sheetPadding: Dp = 24.dp,
    val sectionGap: Dp = 24.dp,
    val actionGap: Dp = 12.dp,
    val loadingPadding: Dp = 40.dp,
    // 시트 세로 리듬(공용 OneClickBottomSheet 소유) — grill 확정값.
    val sheetHandleGap: Dp = 12.dp,
    val sheetContentBottom: Dp = 24.dp,
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*OceSpacingTokensTest*'`
Expected: PASS (3 tests).

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/theme/OceSpacing.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/theme/OceSpacingTokensTest.kt
git commit -m "feat(theme): add sheet vertical-rhythm tokens (handleGap 12 / contentBottom 24)"
```

---

## Task 2: 프리미티브가 세로 리듬 소유 + 콜러 5종 이관

`OceSheetDefaults.contentPadding` 상수를 세우고, 프리미티브가 content를 padding Column(+ `navigationBarsPadding()`)으로 감싸게 한 뒤, 5개 시트 콜러의 자체 외곽 padding을 제거한다. **원자적 커밋** — 프리미티브만 바꾸고 콜러를 안 바꾸면 이중 패딩(24+24), 반대면 무패딩이 되므로 한 커밋에 함께 간다.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/primitive/OneClickBottomSheet.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickReminderOptInSheet.kt:98-104`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickPermissionPrimingSheet.kt:110-115`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/google/GoogleSavePromptSheet.kt:98-102`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/topic/TopicSelectScreen.kt:100-104`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt:461`

**Interfaces:**
- Consumes: `OceSpacing.sheetHandleGap`·`sheetContentBottom`·`sheetPadding` (Task 1).
- Produces: `com.jjundev.oneclickeng.ui.component.primitive.OceSheetDefaults` (object), 프로퍼티 `contentPadding: PaddingValues` (@Composable getter). `OneClickBottomSheet(onDismissRequest, modifier, sheetState, contentPadding = OceSheetDefaults.contentPadding, content)` — 새 파라미터 `contentPadding`. Task 3 스크린샷이 `OceSheetDefaults.contentPadding` 소비.

- [ ] **Step 1: 프리미티브 rework — `OneClickBottomSheet.kt` 전체 교체**

Replace the entire file body (imports + `OneClickBottomSheet`) with:

```kotlin
package com.jjundev.oneclickeng.ui.component.primitive

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.jjundev.oneclickeng.ui.theme.OceTheme

/**
 * 공용 시트 스페이싱 정책(PR D). 값은 [OceSheetDefaults] 하나가 소유 — 콜러/스크린샷이 하드코딩하지 않는다.
 *  - 핸들→첫 제목 갭 = `sheetHandleGap`(12dp). M3 드래그 핸들 자체 여백 위에 얹힌다.
 *  - 최하단 콘텐츠 하단 = `navigationBarsPadding()`(제스처바 인셋) + `sheetContentBottom`(24dp).
 *  - 가로 거터 = `sheetPadding`(24dp).
 * ModalBottomSheet 는 기본 `windowInsets` 가 top 만 처리하므로 하단 nav bar 는 여기서 명시 흡수한다.
 */
object OceSheetDefaults {
    val contentPadding: PaddingValues
        @Composable get() =
            PaddingValues(
                start = OceTheme.spacing.sheetPadding,
                end = OceTheme.spacing.sheetPadding,
                top = OceTheme.spacing.sheetHandleGap,
                bottom = OceTheme.spacing.sheetContentBottom,
            )
}

/**
 * 비파일럿 프리미티브 = M3 [ModalBottomSheet] 얇은 래핑 + 토큰. 상단 `radius.24`·`surface`·핸들 유지.
 * content 는 [OceSheetDefaults.contentPadding] + `navigationBarsPadding()` 을 두른 [Column] 안에 렌더돼
 * 모든 시트가 동일 세로 리듬을 갖는다. 특수 시트는 [contentPadding] 를 넘겨 오버라이드할 수 있다.
 * C13(권한 프라이밍)·C19(리마인더)·주제 선택·Google 저장·설정 정리 시트가 이 프리미티브를 재사용한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneClickBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    contentPadding: PaddingValues = OceSheetDefaults.contentPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = OceTheme.shapes.radius24,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(contentPadding),
            content = content,
        )
    }
}
```

- [ ] **Step 2: 콜러 이관 — Reminder**

In `OneClickReminderOptInSheet.kt`, `OneClickReminderOptInSheetContent` 의 Column(98-104)에서 `.padding(OceTheme.spacing.sheetPadding)` 줄을 제거:

```kotlin
    Column(
        modifier =
            modifier
                .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
```

- [ ] **Step 3: 콜러 이관 — Permission**

In `OneClickPermissionPrimingSheet.kt`, Column(110-118)에서 modifier 체인의 `.padding(OceTheme.spacing.sheetPadding)` 줄만 제거한다. 리딩 주석·`horizontalAlignment = Alignment.Start`·`verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)` 는 그대로 유지:

```kotlin
    Column(
        // 프로토 priming 시트: 콘텐츠 **좌측 정렬**(넛지 시트와 달리 중앙 아님), 아이콘도 좌측.
        modifier =
            modifier
                .fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
    ) {
```

- [ ] **Step 4: 콜러 이관 — Google**

In `GoogleSavePromptSheet.kt`, Column(98-104)에서 `.padding(OceTheme.spacing.sheetPadding)` 줄 제거 (`verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap)` 유지):

```kotlin
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
        ) {
```

- [ ] **Step 5: 콜러 이관 — TopicSelect**

In `TopicSelectScreen.kt`, `TopicSelectSheetContent` 의 Column(100-106)에서 `.padding(OceTheme.spacing.sheetPadding)` 줄 제거 (`verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.lg)` 유지):

```kotlin
    Column(
        modifier =
            modifier
                .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.lg),
    ) {
```

- [ ] **Step 6: 콜러 이관 — Settings CardPurgeSheet**

In `SettingsScreen.kt`, `CardPurgeSheet` 의 Column(461): `.padding(OceTheme.spacing.xl)` 를 제거해 공용 리듬(가로 24)에 맡긴다:

```kotlin
        Column(modifier = Modifier.fillMaxWidth()) {
```

- [ ] **Step 7: Google 시트 `@Preview` 정합(프리뷰 전용)**

In `GoogleSavePromptSheet.kt`, `GoogleSavePromptPreview`(163-178) 의 Column(169)은 프리미티브를 우회한 하드코딩 `.padding(OceTheme.spacing.sheetPadding)` 라 새 공용 리듬과 어긋난다(프리뷰 표면만, 골든·프로덕션 무관). 공용 상수로 맞춘다 — import `com.jjundev.oneclickeng.ui.component.primitive.OceSheetDefaults` 추가 후:

```kotlin
        Column(
            modifier = Modifier.fillMaxWidth().padding(OceSheetDefaults.contentPadding),
            verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
        ) {
```

- [ ] **Step 8: 미사용 import 정리**

각 콜러 파일에서 `padding`/`OceTheme` 등이 다른 곳에도 쓰이면 유지, 아니면 detekt/컴파일 경고에 따라 제거. `import androidx.compose.foundation.layout.padding` 는 Reminder(133·142행)·Permission·Google·TopicSelect·Settings 내 다른 `.padding(...)` 호출이 남아 있으면 유지된다 — 파일별로 확인 후 미사용만 제거.

- [ ] **Step 9: 컴파일·기존 테스트 통과 확인**

Run: `scripts/verify-android.sh`
Expected: detekt·컴파일·`testDebugUnitTest`·`testReleaseUnitTest` PASS. (스크린샷 골든은 아직 갱신 전이라 이 단계에서 하드 게이트가 아님 — 이 리포는 record-and-eyeball 방식이며 캡처 테스트는 PNG를 기록만 하고 자동 실패시키지 않는다.)

- [ ] **Step 10: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/primitive/OneClickBottomSheet.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickReminderOptInSheet.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickPermissionPrimingSheet.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/google/GoogleSavePromptSheet.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/home/topic/TopicSelectScreen.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt
git commit -m "feat(sheet): OneClickBottomSheet owns shared vertical rhythm; migrate 5 callers"
```

---

## Task 3: 시트 스크린샷 재현부 공용 상수 적용 + 골든 재기록

Robolectric 은 `ModalBottomSheet`(별도 윈도)를 `onRoot` 로 못 잡아, 스크린샷 테스트가 시트를 손수 재현하며 `*Content` 를 bare 로 렌더한다. Task 2 로 `*Content` 가 자체 padding 을 잃었으니 재현부가 `OceSheetDefaults.contentPadding` 를 다시 둘러야 프로토 대조가 유효하다. 그 뒤 시트 골든을 재기록·육안 확인한다.

**Files:**
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ui/ReminderScreenshotTest.kt:104-151`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/topic/TopicSelectScreenshotTest.kt:112-116`

**Interfaces:**
- Consumes: `OceSheetDefaults.contentPadding`(Task 2). 스크린샷에는 시스템바가 없으므로 `navigationBarsPadding()` 는 재현하지 않는다(0dp).
- Produces: 갱신된 골든 PNG `reminder_optin_light`·`reminder_priming_light`·`topic_select_light`.

- [ ] **Step 1: Reminder `captureSheet` 헬퍼에 공용 padding 적용**

In `ReminderScreenshotTest.kt`, `captureSheet`(104-151) 의 핸들 Box 다음 `content()` 호출을 padding 래퍼로 감싼다. import 추가는 `com.jjundev.oneclickeng.ui.component.primitive.OceSheetDefaults` 하나뿐 — `androidx.compose.foundation.layout.padding`(10행)·`Box` 는 이미 있다. 130-145행의 `Column { … content() }` 를 다음으로:

```kotlin
                        Column {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(32.dp)
                                            .height(4.dp)
                                            .clip(OceTheme.shapes.pill)
                                            .background(MaterialTheme.colorScheme.outlineVariant),
                                )
                            }
                            Box(modifier = Modifier.padding(OceSheetDefaults.contentPadding)) {
                                content()
                            }
                        }
```

- [ ] **Step 2: TopicSelect 재현부에 공용 padding 적용**

In `TopicSelectScreenshotTest.kt`(112-116), `TopicSelectSheetContent` 의 modifier 에 공용 padding 을 추가. import 추가는 `com.jjundev.oneclickeng.ui.component.primitive.OceSheetDefaults` 하나뿐 — `androidx.compose.foundation.layout.padding`(10행) 은 이미 있다:

```kotlin
                            TopicSelectSheetContent(
                                onTopicChosen = { _, _ -> },
                                onDismiss = {},
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .padding(OceSheetDefaults.contentPadding),
                            )
```

- [ ] **Step 3: 컴파일 확인(기록 전)**

Run: `scripts/verify-android.sh :app:compileDebugUnitTestKotlin`
Expected: PASS (스크린샷 테스트 컴파일 성공).

- [ ] **Step 4: 시트 골든 재기록**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReminderScreenshotTest*' --tests '*TopicSelectScreenshotTest*' -Proborazzi.record`
Expected: 통과, `android/app/build/outputs/roborazzi/` 에 `reminder_optin_light.png`·`reminder_priming_light.png`·`topic_select_light.png` 갱신 기록.

- [ ] **Step 5: 육안 대조**

`android/app/build/outputs/roborazzi/` PNG를 열어 확인: 핸들→첫 제목 갭이 이전보다 타이트(12dp 리듬), 최하단 버튼이 시트 하단에서 24dp, 좌우 거터 24dp 유지. 프로토타입 시트와 시각 정합. 어긋나면 값이 아니라 재현부 구조를 점검(핸들 Box 세로 padding은 test-only 근사라 무관).

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ui/ReminderScreenshotTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/home/topic/TopicSelectScreenshotTest.kt \
        android/app/build/outputs/roborazzi/reminder_optin_light.png \
        android/app/build/outputs/roborazzi/reminder_priming_light.png \
        android/app/build/outputs/roborazzi/topic_select_light.png
git commit -m "test(sheet): re-record sheet goldens against shared content padding"
```

> 참고: 골든 PNG가 gitignore 대상이면(`android/app/build/` 는 통상 무시) 커밋에서 PNG 경로를 빼고 테스트 파일만 커밋한다 — `git status` 로 확인 후 조정.

---

## Task 4: 풀스크린 형제 상단 상태바 인셋 통일 (fitsSystemWindows 번역)

엣지투엣지에서 3탭 밖 풀스크린 형제 화면은 전역 `Scaffold` 인셋을 못 받아 상태바 밑으로 콘텐츠가 깔린다. 각 루트에 `Modifier.statusBarsPadding()` 를 통일 적용하고, 하단에 엣지-고정 CTA가 있는 Summary 풋터엔 `navigationBarsPadding()` 을 더한다.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/level/LevelQuestionScreen.kt:71-76`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/topic/TopicQuestionScreen.kt:69-75`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryScreen.kt:85, 132`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreen.kt:135-136`

**Interfaces:**
- Consumes: `androidx.compose.foundation.layout.statusBarsPadding`·`navigationBarsPadding`.
- Produces: UI 인셋만 — 다른 태스크가 소비하는 심볼 없음.

- [ ] **Step 1: LevelQuestion 상단 인셋**

In `LevelQuestionScreen.kt`(71-76), import `androidx.compose.foundation.layout.statusBarsPadding` 추가 후 Column modifier 를:

```kotlin
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(OceTheme.spacing.sheetPadding),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
    ) {
```

- [ ] **Step 2: TopicQuestion 상단 인셋**

In `TopicQuestionScreen.kt`(69-75), import `statusBarsPadding` 추가 후 (verticalScroll 앞에) Column modifier 를:

```kotlin
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(OceTheme.spacing.sheetPadding),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.actionGap),
    ) {
```

- [ ] **Step 3: Summary 상단 인셋 + 풋터 하단 인셋**

In `SummaryScreen.kt`: import `statusBarsPadding`·`navigationBarsPadding` 추가.

(a) 85행 바깥 Column 에 상단 인셋:

```kotlin
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
```

(b) 132행 `SummaryDoneFooter` 의 Column 에 하단 인셋(엣지-고정 CTA가 제스처바를 클리어):

```kotlin
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .background(MaterialTheme.colorScheme.surface),
    ) {
```

- [ ] **Step 4: DialogueGenerating 상단 인셋**

In `DialogueGeneratingScreen.kt`(135-136), import `statusBarsPadding` 추가 후 내부 Column modifier 를:

```kotlin
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = OceTheme.spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
```

- [ ] **Step 5: 컴파일·detekt·단위테스트**

Run: `scripts/verify-android.sh`
Expected: PASS.

- [ ] **Step 6: 형제 화면 골든 재기록**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*LevelQuestionScreenshotTest*' --tests '*TopicQuestionScreenshotTest*' --tests '*SummaryScreenshotTest*' --tests '*DialogueGeneratingScreenshotTest*' -Proborazzi.record`
Expected: 통과. Robolectric 컴포지션은 실제 시스템바 인셋이 0dp인 경우가 많아 `statusBarsPadding()` 이 골든에서 no-op일 수 있다 — 그럼 PNG 무변화가 정상(프로덕션 기기에서만 인셋 적용). Summary 풋터 `navigationBarsPadding()` 도 동일.

- [ ] **Step 7: 육안 대조**

PNG가 변했다면 상단/하단 여백이 상태바/내비바만큼 늘었는지, 안 변했다면(Robolectric 0-inset) 레이아웃이 이전과 동일한지 확인.

- [ ] **Step 8: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/level/LevelQuestionScreen.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/onboarding/topic/TopicQuestionScreen.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/summary/SummaryScreen.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/dialogue/DialogueGeneratingScreen.kt
git commit -m "feat(insets): apply statusBarsPadding to full-screen sibling roots (edge-to-edge)"
```

---

## Task 5: 스페이싱 폴리시 문서화

향후 신규 시트/화면 재발 방지용 짧은 ADR 노트. 규칙: (1) 시트 세로 리듬은 `OneClickBottomSheet`/`OceSheetDefaults` 가 소유 — 콜러는 자체 top/bottom padding 금지, (2) 3탭 밖 풀스크린 형제는 루트에 `statusBarsPadding()`, 엣지-고정 하단 CTA는 `navigationBarsPadding()`.

**Files:**
- Create: `docs/adr/ADR-00NN-sheet-spacing-policy.md` (번호는 `docs/adr/` 최신+1)

**Interfaces:** 문서만.

- [ ] **Step 1: 기존 ADR 번호 확인**

Run: `ls docs/adr/`
Expected: 기존 ADR 목록 확인 → 다음 번호 결정(예: 마지막이 ADR-0006 이면 ADR-0007).

- [ ] **Step 2: ADR 작성**

Create `docs/adr/ADR-0007-sheet-spacing-policy.md`(번호는 Step 1 결과로 치환):

```markdown
# ADR-0007: 공용 시트/스페이싱 폴리시 (Compose 인셋)

## Status
Accepted (2026-07-11, PR D)

## Context
앱은 Compose 단일 Activity + `enableEdgeToEdge()`. View 시스템 `fitsSystemWindows` 는 없다.
시트마다 핸들→제목 갭(24/20/6)·최하단 패딩이 제각각이었고, 3탭 밖 풀스크린 형제는 상태바
밑으로 콘텐츠가 깔렸다.

## Decision
1. 시트 세로 리듬의 단일 소스는 `OceSheetDefaults.contentPadding`
   (top=`sheetHandleGap`=12dp, horizontal=`sheetPadding`=24dp, bottom=`sheetContentBottom`=24dp)
   이며 `OneClickBottomSheet` 프리미티브가 `navigationBarsPadding()` 과 함께 적용한다.
   콜러는 자체 외곽 top/bottom padding 을 두지 않는다. 특수 시트만 `contentPadding` 오버라이드.
2. 3탭 밖 풀스크린 형제 화면 루트는 `Modifier.statusBarsPadding()` 로 상태바 인셋을 통일 적용.
   엣지-고정 하단 CTA(예: Summary 완료 풋터)는 `navigationBarsPadding()` 추가.
3. 3탭 화면은 전역 Scaffold(AppRoot) innerPadding 이 이미 systemBars 를 흡수하므로 제외.

## Exceptions
- `SlimFeedbackSheet`·`DialogueGeneratingScreen.ReadyBottomSheet`: raw/인-컴포지션 커스텀 오버레이
  (Robolectric 별도-윈도 스크린샷 정합 사유). 자체 `navigationBarsPadding()` 유지.
- `DialogueTurnScreen`: 자체 Scaffold + `DialogueHeader(Modifier.statusBarsPadding())` 로 이미 처리.

## Consequences
신규 시트는 프리미티브만 쓰면 자동으로 정합. 신규 풀스크린 형제는 루트 `statusBarsPadding()` 를
잊지 말 것. Robolectric 스크린샷은 프리미티브를 우회 재현하므로 `OceSheetDefaults.contentPadding`
를 재적용해야 한다.
```

- [ ] **Step 3: 커밋**

```bash
git add docs/adr/ADR-0007-sheet-spacing-policy.md
git commit -m "docs(adr): record shared sheet spacing + edge-to-edge inset policy"
```

---

## Self-Review

**Spec coverage:**
- ① "모든 최상단 뷰 `fitsSystemWindows` → 상태바 자동 패딩" → Task 4 (풀스크린 형제 4곳 `statusBarsPadding()`; 3탭·DialogueTurn 은 이미 처리라 제외 — Global Constraints 명시). ✓
- ② "시트 핸들바–첫 제목 마진 통일 (OneClickBottomSheet.kt 공용)" → Task 1(토큰) + Task 2(`OceSheetDefaults` + 프리미티브 소유 + 콜러 이관). ✓
- ③ "최하단 버튼 하단 패딩 통일" → Task 2(프리미티브 `navigationBarsPadding()` + `sheetContentBottom`) + Task 4(Summary 풋터). ✓
- 스크린샷 정합 → Task 3. 문서화 → Task 5. ✓

**Placeholder scan:** "TBD"/"적절히"/"등" 류 없음 — 모든 코드 스텝에 실제 코드·경로·명령. Step 7(import 정리)은 파일별 조건부지만 판단 기준(다른 `.padding` 잔존 여부)을 명시. ADR 번호만 Step 1에서 실측 후 치환(런타임 확인 필요 항목).

**Type consistency:** `OceSheetDefaults.contentPadding`(PaddingValues, @Composable getter) — Task 2 정의, Task 3 소비 일치. `sheetHandleGap`/`sheetContentBottom` — Task 1 정의, Task 2 소비 일치. `OneClickBottomSheet` 신규 파라미터 `contentPadding` 기본값 = `OceSheetDefaults.contentPadding` 일치.

---

## Termination
5 tasks, 순차 의존(1→2→3, 4는 2와 독립이나 같은 PR). 컨버전스: 구현자가 추가 설계 질문 없이 착수 가능.
