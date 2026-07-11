# 리마인더 opt-in 시트 프로토타입 여백 재대조 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `OneClickReminderOptInSheet`(🔥 스트릭 넛지 opt-in 시트)의 여백을 프로토타입과 재대조해 어긋난 부분(드래그 핸들 처리·상/하단 패딩)만 이 시트에 국한해 바로잡는다. 이미 적용된 완전 펼침 수정(`skipPartiallyExpanded=true`)은 유지한다.

**Architecture:** 프로토타입(`prototype/Prototype Flow (standalone).html`, ADR-0006 정합 SoT)의 이 시트는 `padding:12px 24px 26px` + 탑라운드24 컨테이너에, 커스텀 핸들(36×4 bar, 컨테이너 `padding-bottom:16px`, 위 12px는 시트 top 패딩)을 둔다. 현재 Android 구현은 `OneClickBottomSheet` 프리미티브의 **M3 기본 드래그 핸들**(32×4 + 내장 22dp 세로 패딩)을 써서 핸들→콘텐츠 간격이 ~34dp(프로토 16dp)로 벌어지고, 하단이 24dp(프로토 26dp)다. 내부 리듬(🔥→제목 8 · 제목→본문 4 · 묶음→버튼 20 · 버튼 12 · 🔥박스 60/글리프 30 · 좌우 24)은 이미 프로토와 일치하므로 손대지 않는다. 수정은 **이 시트에만** 적용한다(사용자 결정): 프리미티브에 `dragHandle` 슬롯을 추가(기본값=현행 M3 기본, 나머지 4개 시트 무변경)하고, 리마인더 시트가 프로토 정합 커스텀 핸들 + `contentPadding` 오버라이드를 넘긴다.

**Tech Stack:** Kotlin, Jetpack Compose (Material3 `ModalBottomSheet`), Roborazzi(Robolectric) 스크린샷. 검증은 `scripts/verify-android.sh`.

## Global Constraints

- **워크트리·검증(재발 함정 회피):** 이 작업의 코드/브랜치는 `/Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/silly-colden-660906`(브랜치 `claude/sheet-spacing-policy-80bc78`)에 있다. **셸 CWD 는 다른 워크트리(sleepy-edison)이므로 `git rev-parse`/`cd` 로 워크트리를 잡지 말고 위 절대경로(`SC`)를 명시**한다. 모든 gradle 은 `cd "$SC" && scripts/verify-android.sh …` 로만 실행(직접 `./gradlew` 금지 — google-services.json·GRADLE_USER_HOME 격리). 새 test 파일이 컴파일에 안 잡히면 `--rerun-tasks --no-daemon` 로 강제.
- **공유 워크트리 주의:** silly-colden 은 다른 세션도 사용 중이다(예: `MicDockTogglePositionTest` 가 Release 스위트에서 실패 — 본 작업과 무관). 전체 verify 의 빨간불이 이 외부 테스트 때문이면 무시하되, **본 변경 파일이 유발한 실패가 아님을 diff 로 확인**한다.
- **완전 펼침 수정 유지:** `OneClickBottomSheet.kt` 의 `sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)`(커밋 09efcbb)은 그대로 둔다. 되돌리지 말 것.
- **스코프: 이 시트만.** `OceSheetDefaults.contentPadding`(공용 기본)·나머지 4개 시트(권한·Google·설정·주제)·`SlimFeedbackSheet` 는 건드리지 않는다. 프리미티브의 `dragHandle` 파라미터는 기본값이 현행 동작이라 다른 콜러에 영향 없어야 한다.
- **프로토타입 정합 목표값(이 시트):** 시트 패딩 `top=12 / 좌우=24 / bottom=26`; 드래그 핸들 bar `36dp×4dp`, pill, 색 `OceTheme.colors.borderStrong`(proto `--border-strong` 정확 매핑 — `outlineVariant`는 hairline 이라 더 옅음, 사용 금지), 핸들 컨테이너 `top=12 / bottom=16`; 내부 리듬은 현행 유지(8/4/20/12, 🔥박스 60/글리프 30). 하단은 `navigationBarsPadding()` 위에 26 가산.
- 코틀린 detekt 통과. Roborazzi 골든은 gitignore(build/) — 커밋에 PNG 포함 금지, record-and-eyeball.

---

## File Structure

**수정 — 프리미티브(안전한 기본값, 동작 보존)**
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/primitive/OneClickBottomSheet.kt` — `dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() }` 파라미터 추가 후 `ModalBottomSheet(dragHandle = dragHandle, …)` 로 포워드. 기존 콜러 무변경(기본값이 현행 M3 기본 핸들).

**수정 — 리마인더 시트(이 시트만 프로토 정합)**
- `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickReminderOptInSheet.kt` — `OneClickReminderOptInSheet` 의 `OneClickBottomSheet(...)` 호출에 (a) 커스텀 `dragHandle`(프로토 36×4·12/16 패딩), (b) `contentPadding = PaddingValues(start=24,end=24,top=0,bottom=26)` 전달. 콘텐츠 컴포저블(`OneClickReminderOptInSheetContent`) 내부 리듬은 무변경.

**수정 — 검증(스크린샷 재현부)**
- `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ui/ReminderScreenshotTest.kt` — `reminder_optin` 재현부만 새 핸들/패딩을 반영(공용 `captureSheet` 를 쓰면 reminder_priming 과 분리 필요 — Task 3 참조). 골든 재기록.

---

## Task 1: 프리미티브에 `dragHandle` 슬롯 추가(동작 보존)

`OneClickBottomSheet` 가 드래그 핸들을 파라미터로 받게 해, 특정 시트가 프로토 정합 커스텀 핸들을 넘길 수 있게 한다. 기본값은 현행 M3 기본 핸들이라 기존 5개 콜러 동작은 그대로다.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/primitive/OneClickBottomSheet.kt`

**Interfaces:**
- Consumes: 없음.
- Produces: `OneClickBottomSheet(onDismissRequest, modifier, sheetState, contentPadding, dragHandle = { BottomSheetDefaults.DragHandle() }, content)` — 새 파라미터 `dragHandle: @Composable (() -> Unit)?`. Task 2 가 소비.

- [ ] **Step 1: import 추가**

`OneClickBottomSheet.kt` 상단 import 블록에 추가(알파벳 위치):
```kotlin
import androidx.compose.material3.BottomSheetDefaults
```

- [ ] **Step 2: 파라미터 추가 + 포워드**

현재 시그니처(파라미터 순서 유지)와 `ModalBottomSheet` 호출을 다음으로 바꾼다. `sheetState` 줄(완전 펼침 수정)은 **그대로 유지**:
```kotlin
fun OneClickBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    // 콘텐츠-hug 시트라 항상 완전 펼침으로 연다. (커밋 09efcbb — 유지)
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    contentPadding: PaddingValues = OceSheetDefaults.contentPadding,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = OceTheme.shapes.radius24,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = dragHandle,
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
(`sheetState` 위의 완전 펼침 주석/기존 KDoc 주석 블록은 유지. `@file:Suppress("MatchingDeclarationName")` 도 유지.)

- [ ] **Step 3: 컴파일 확인 — 기존 콜러 무변경**

Run:
```
SC=/Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/silly-colden-660906
cd "$SC" && scripts/verify-android.sh :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL. 5개 기존 콜러(권한·리마인더·Google·설정·주제)는 `dragHandle` 미전달 → 기본값(M3 기본 핸들)으로 이전과 동일 렌더.

- [ ] **Step 4: 커밋**

```bash
cd "$SC"
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/primitive/OneClickBottomSheet.kt
git commit -m "feat(sheet): expose dragHandle slot on OneClickBottomSheet (default unchanged)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: 리마인더 opt-in 시트를 프로토 정합으로

리마인더 시트가 프로토 정합 커스텀 핸들 + `contentPadding` 오버라이드를 넘겨 핸들→콘텐츠 간격(34→16dp)·하단(24→26dp)을 프로토와 맞춘다. 콘텐츠 내부 리듬은 무변경.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickReminderOptInSheet.kt`

**Interfaces:**
- Consumes: Task 1 의 `dragHandle`·기존 `contentPadding` 파라미터.
- Produces: 시각 변경만 — 다른 태스크가 소비하는 심볼 없음.

- [ ] **Step 1: import 추가**

`OneClickReminderOptInSheet.kt` import 블록에 추가(중복 시 생략):
```kotlin
import androidx.compose.foundation.layout.PaddingValues
```
(`Box`, `size`, `clip`, `background`, `fillMaxWidth`, `padding`, `height`, `MaterialTheme`, `OceTheme`, `dp` 는 이미 존재 — 확인만.)

- [ ] **Step 2: 커스텀 핸들 컴포저블 추가**

파일 하단 private 헬퍼 영역(예: `SheetGhostHeight` 정의 부근)에 추가:
```kotlin
/** 프로토 정합 드래그 핸들 — 36×4 pill, 위 12dp(시트 top)·아래 16dp. M3 기본(32×4·내장 22dp)과 다름. */
@Composable
private fun ReminderOptInDragHandle() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(OceTheme.shapes.pill)
                    // proto `--border-strong` 정확 매핑. outlineVariant(hairline)은 더 옅어 사용 금지.
                    .background(OceTheme.colors.borderStrong),
        )
    }
}
```
(`Alignment` import 이미 존재 — 확인.)

- [ ] **Step 3: 호출부에 핸들 + contentPadding 전달**

`OneClickReminderOptInSheet` 의 `OneClickBottomSheet(...)` 호출(현재 `OneClickBottomSheet(onDismissRequest = onLater, modifier = modifier) { … }`)을 다음으로:
```kotlin
    OneClickBottomSheet(
        onDismissRequest = onLater,
        modifier = modifier,
        // 프로토: 시트 padding 12/24/26. 상단 12 + 핸들 16 은 커스텀 핸들이 소유하므로 top=0.
        contentPadding = PaddingValues(
            start = OceTheme.spacing.sheetPadding,
            end = OceTheme.spacing.sheetPadding,
            top = 0.dp,
            bottom = 26.dp,
        ),
        dragHandle = { ReminderOptInDragHandle() },
    ) {
        OneClickReminderOptInSheetContent(
            onOptIn = onOptIn,
            onLater = onLater,
            headerFocus = headerFocus,
        )
    }
```
(`OneClickReminderOptInSheetContent` 내부 Column·간격(8/4/20/12)·🔥 박스는 **무변경**.)

- [ ] **Step 4: 컴파일 + detekt**

Run:
```
cd "$SC" && scripts/verify-android.sh :app:compileDebugKotlin :app:detekt
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
cd "$SC"
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickReminderOptInSheet.kt
git commit -m "fix(reminder): match opt-in sheet handle/padding to prototype (12/24/26, 36x4 handle, 16dp gap)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: 스크린샷 재현부 갱신 + 골든 재기록 + 육안 대조

`ReminderScreenshotTest` 의 `reminder_optin` 재현부가 실제 시트의 새 핸들/패딩을 반영하도록 갱신하고 골든을 재기록해 프로토와 육안 대조한다. 현재 `captureSheet` 헬퍼는 `reminder_optin`·`reminder_priming` 둘이 공유하므로, opt-in 만 다른 핸들/패딩을 갖도록 분리한다.

**Files:**
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ui/ReminderScreenshotTest.kt`

**Interfaces:**
- Consumes: `OceSheetDefaults`(기존), 새 값(핸들 36×4·top12/bottom16, contentPadding top0/bottom26).
- Produces: 갱신된 골든 `reminder_optin_light.png`(비커밋, gitignore).

- [ ] **Step 1: opt-in 전용 재현 경로 분리**

`reminder_optin_light` 테스트가 공용 `captureSheet` 대신, 실제 시트 프레젠테이션을 반영한 재현을 쓰도록 바꾼다: 핸들 Box 를 `padding(top=12.dp, bottom=16.dp)` + bar `36×4`(`OceTheme.colors.borderStrong`)로, 콘텐츠 래퍼 padding 을 `PaddingValues(start=24,end=24,top=0,bottom=26)` 로. `reminder_priming_light`(권한 시트)는 기존 `captureSheet`(M3 기본 핸들 근사) 유지.

구체: `reminder_optin_light` 를 다음처럼(핸들·패딩만 opt-in 값으로):
```kotlin
    @Test
    fun reminder_optin_light() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        HomeContent(
                            state = sampleHomeState.copy(),
                            onStartLearning = {},
                            onResumeContinue = {},
                            onResumeStartNew = {},
                            onViewRecords = {},
                            onOfflineBlocked = {},
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize().background(Color(SCRIM)))
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column {
                            // 프로토 정합 핸들(36×4, 위12/아래16)
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(36.dp)
                                            .height(4.dp)
                                            .clip(OceTheme.shapes.pill)
                                            .background(OceTheme.colors.borderStrong),
                                )
                            }
                            Box(
                                modifier =
                                    Modifier.padding(
                                        PaddingValues(start = 24.dp, end = 24.dp, top = 0.dp, bottom = 26.dp),
                                    ),
                            ) {
                                OneClickReminderOptInSheetContent(onOptIn = {}, onLater = {})
                            }
                        }
                    }
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/reminder_optin_light.png")
    }
```
필요 import 확인/추가: `androidx.compose.foundation.layout.width`, `androidx.compose.foundation.layout.height`, `androidx.compose.foundation.layout.PaddingValues`, `androidx.compose.foundation.shape.RoundedCornerShape`, `com.jjundev.oneclickeng.ui.component.OneClickReminderOptInSheetContent`.

- [ ] **Step 2: 컴파일**

Run:
```
cd "$SC" && scripts/verify-android.sh :app:compileDebugUnitTestKotlin
```
Expected: BUILD SUCCESSFUL. (신규 컴파일 미인식 시 `--rerun-tasks --no-daemon` 추가.)

- [ ] **Step 3: 골든 재기록**

Run:
```
cd "$SC" && scripts/verify-android.sh :app:testDebugUnitTest --tests '*ReminderScreenshotTest*' -Proborazzi.record
```
Expected: BUILD SUCCESSFUL. `android/app/build/outputs/roborazzi/reminder_optin_light.png` 갱신.

- [ ] **Step 4: 육안 대조**

`reminder_optin_light.png` 를 Read(이미지 렌더)로 열어 확인: (a) 핸들→🔥 간격이 좁아짐(≈16dp), (b) 핸들 bar 36×4, (c) 하단 26dp, (d) 내부 리듬(🔥→제목→본문→버튼) 프로토와 동일. 프로토(`prototype/Prototype Flow (standalone).html` 의 스트릭 넛지 시트)와 시각 대조.

- [ ] **Step 5: 커밋(테스트 파일만 — PNG gitignore)**

```bash
cd "$SC"
git add android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/reminder/ui/ReminderScreenshotTest.kt
git commit -m "test(reminder): reconstruct opt-in golden with prototype handle/padding

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

- **Spec coverage:** 프로토 대비 어긋난 항목(핸들 간격 34→16, 핸들 36×4/색, 하단 24→26)이 Task 1·2 로 커버. 내부 리듬(8/4/20/12·60/30·좌우24)은 이미 일치라 무변경(명시). 완전 펼침 수정 유지(Global Constraints). 검증/골든은 Task 3. ✓
- **Placeholder scan:** 모든 값 구체(12/16/24/26/36×4). 색은 `OceTheme.colors.borderStrong`(proto `--border-strong` 정확 매핑) 명시 — `outlineVariant`(hairline)는 사용 금지.
- **Type consistency:** Task 1 이 `dragHandle: @Composable (() -> Unit)?` 정의 → Task 2 가 `dragHandle = { ReminderOptInDragHandle() }` 로 소비. `contentPadding: PaddingValues` 기존 파라미터 재사용.

## Assumptions / needs you

| # | 결정(질문) | 가정값 | 근거·확인 필요 |
|---|---|---|---|
| A2 | 핸들 불일치(이 시트만 36×4 tight·borderStrong, 나머지 4개는 M3 기본 32×4) 허용 | 허용(사용자 "이 시트만" 결정) | 시트 간 핸들이 달라짐 — 추후 전 시트 핸들 통일을 원하면 별도 작업으로 프리미티브 기본 핸들 교체(팔로업). 지금은 스코프 밖. |

(핸들 색 결정은 리뷰로 확정: `OceTheme.colors.borderStrong` = proto `--border-strong` 정확 매핑. 더 이상 가정 아님.)

## Termination
Converged. 구현자가 추가 설계 질문 없이 착수 가능(A1·A2 는 확인/스코프 노트).

---
To flip a decision, re-invoke with `#<n>=<value>`.
