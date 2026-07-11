# 대화 입력 토글 위치 통일 Hotfix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 대화 학습 도크에서 입력 모드 전환 어피던스("채팅으로 입력하기"·"마이크로 말하기")가 두 모드에서 화면 하단으로부터 **동일한 위치**에 오도록, 두 토글의 스타일을 하나의 공용 컴포저블로 통일한다.

**Architecture:** 두 토글은 이미 같은 바텀-정착 도크([MicDock] Column, `padding(lg)`)의 **마지막 자식**이라 도크 하단으로부터의 거리는 같아야 하지만, 내부 스타일이 달라 어긋난다: 마이크 모드의 `ChatInputToggle` 은 `heightIn(min=48dp)`+`verticalAlignment=Top`(잉여 높이를 아래로 흡수 — KDoc 명시)이고, 텍스트 모드의 마이크-복귀 Row 는 `Center`+min-height 없음+`vertical=4dp`. 이 차이가 "채팅으로 입력하기"를 "마이크로 말하기"보다 위로 띄운다. 두 토글을 단일 `InputModeToggle`(48dp min · Center · radius8 리플 · 동일 패딩)로 대체해 텍스트가 도크 하단에서 같은 오프셋에 오게 한다.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Roborazzi(Robolectric) 스크린샷, Compose UI test(bounds 측정), Gradle.

## Global Constraints

- 검증은 반드시 `scripts/verify-android.sh` 경유(워크트리 캐시 오염·google-services.json 부재 함정 우회). 스크린샷 기록은 `-Proborazzi.record`.
- 문구·색·아이콘은 현행 유지: "채팅으로 입력하기"(키보드 아이콘), "마이크로 말하기"(마이크 아이콘), 색 `OceTheme.colors.textTertiary`, 아이콘 18dp, 텍스트 `OceTheme.typography.helper` SemiBold.
- 접근성 최소 터치 타깃 48dp(`MinTouchTarget`) 유지 — 통일 후 **양쪽 모두** 48dp min.
- 스코프: `MicDock.kt` 의 두 토글만. 마이크 버튼·입력 필드·프롬프트 카드·"다음" 버튼 등 다른 도크 요소는 무변경. 다른 화면 무관.
- 골든 PNG 는 gitignore(`build/`) — 커밋엔 소스/테스트 `.kt` 만 포함.
- 의도된 시각 트레이드오프: 마이크 모드에서 상태 문구↔토글 간격이 Top-정렬 제거로 살짝 넓어짐(잉여 48dp 높이가 위아래로 분산). 위치 통일을 위한 수용된 부작용.

---

## Task 1: 두 입력 모드 토글을 공용 `InputModeToggle` 로 통일

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDock.kt` (신규 `InputModeToggle`, `ChatInputToggle` 삭제, 두 호출부 교체)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockTogglePositionTest.kt` (create — bounds 패리티 테스트)
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionFlowScreenshotTest.kt` (텍스트 모드 골든 캡처 추가 + 기존 도크 골든 재기록)

**Interfaces:**
- Consumes: 기존 `MicDock`(internal), `MinTouchTarget`(48.dp, private val), `OceIcon.Keyboard`/`OceIcon.Mic`, `OneClickIcon`, `OceTheme` 토큰 — 모두 파일 내 기존 심볼.
- Produces: `private fun InputModeToggle(icon: OceIcon, label: String, onClick: () -> Unit)` (MicDock.kt 내부). 외부 노출 없음.

- [ ] **Step 1: 실패 테스트 작성 (위치 패리티)**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockTogglePositionTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.turn

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.jjundev.oneclickeng.ui.audio.MicState
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "채팅으로 입력하기"(마이크 모드)와 "마이크로 말하기"(텍스트 모드) 토글이 화면 하단에서 동일한 위치에 오는지
 * 검증. 두 도크를 같은 높이(600dp)의 바텀-정착 박스에 나란히 렌더하면, 두 토글은 각 도크의 마지막 자식이므로
 * 스타일이 통일되면 root 기준 bottom Y 가 같아야 한다. 통일 전엔 마이크 모드 토글이 Top-정렬+48dp 라 위로 떠
 * 값이 어긋난다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class MicDockTogglePositionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val task = ScaffoldTask("라떼 한 잔을 주문해보세요")
    private val waveform = MutableStateFlow(FloatArray(0))

    @Test
    fun input_mode_toggles_share_bottom_offset() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(false, true).forEach { textMode ->
                        Box(
                            modifier = Modifier.weight(1f).height(600.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            MicDock(
                                task = task,
                                micState = MicState.Ready,
                                waveform = waveform,
                                textMode = textMode,
                                textValue = "",
                                retryHint = null,
                                permanentlyDenied = false,
                                reduceMotion = true,
                                onMicTap = {},
                                onAdvance = {},
                                onToggleTextMode = {},
                                onTextChange = {},
                                onSubmitText = {},
                            )
                        }
                    }
                }
            }
        }

        val chatBottom = composeRule.onNodeWithText("채팅으로 입력하기").getUnclippedBoundsInRoot().bottom
        val micBottom = composeRule.onNodeWithText("마이크로 말하기").getUnclippedBoundsInRoot().bottom
        // 같은 높이·바텀 정착 박스의 마지막 자식 → 통일되면 두 토글의 root 기준 하단 Y 가 일치해야 한다.
        assertEquals(chatBottom.value, micBottom.value, 0.5f)
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*MicDockTogglePositionTest*'`
Expected: FAIL — `assertEquals` 가 두 값 불일치로 실패(마이크 모드 토글이 Top-정렬+48dp 라 텍스트 모드 토글보다 ~18dp 위). (첫 gradle 실행은 의존성 프로비저닝으로 느릴 수 있음 — 정상.)

- [ ] **Step 3: 공용 `InputModeToggle` 추가**

In `MicDock.kt`, `ChatInputToggle`(현재 300-329행) 자리에 다음 컴포저블을 둔다(기존 `ChatInputToggle` 과 그 KDoc 300-305행은 Step 5에서 삭제):

```kotlin
/**
 * 입력 모드 전환 어피던스(마이크↔채팅 공용). 두 모드에서 **동일 스타일**(48dp 터치타깃 · 중앙정렬 ·
 * radius8 리플 · tertiary 회색)이라, 각 도크의 마지막 자식으로서 화면 하단에서 같은 위치에 온다.
 * 마이크 모드: 키보드 아이콘 + "채팅으로 입력하기". 텍스트 모드: 마이크 아이콘 + "마이크로 말하기".
 */
@Composable
private fun InputModeToggle(
    icon: OceIcon,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .padding(top = OceTheme.spacing.md)
                .clip(OceTheme.shapes.radius8)
                .clickable(onClick = onClick)
                .heightIn(min = MinTouchTarget)
                .padding(horizontal = OceTheme.spacing.sm, vertical = OceTheme.spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OneClickIcon(
            icon = icon,
            contentDescription = null,
            tint = OceTheme.colors.textTertiary,
            size = 18.dp,
        )
        Text(
            text = label,
            style = OceTheme.typography.helper.copy(fontWeight = FontWeight.SemiBold),
            color = OceTheme.colors.textTertiary,
        )
    }
}
```

- [ ] **Step 4: 마이크 모드 호출부 교체**

In `MicDock.kt` `MicColumn`, 현재 247행:
```kotlin
                ChatInputToggle(onClick = { onToggleTextMode(true) })
```
을 다음으로 교체:
```kotlin
                InputModeToggle(
                    icon = OceIcon.Keyboard,
                    label = "채팅으로 입력하기",
                    onClick = { onToggleTextMode(true) },
                )
```

- [ ] **Step 5: 텍스트 모드 호출부 교체 + 기존 `ChatInputToggle` 삭제**

In `MicDock.kt` `TextInputDock`, 현재 마이크-복귀 Row(368-390행, 주석 "// 마이크 복귀 어피던스 …" 포함)를 다음으로 교체:
```kotlin
        // 마이크 복귀 어피던스 — 채팅 토글과 동일 위치/스타일(InputModeToggle 공용).
        InputModeToggle(
            icon = OceIcon.Mic,
            label = "마이크로 말하기",
            onClick = { onToggleTextMode(false) },
        )
```
그리고 기존 `ChatInputToggle` 컴포저블 전체(KDoc 300-305행 + 함수 306-329행)를 삭제한다(더 이상 호출부 없음).

- [ ] **Step 6: 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*MicDockTogglePositionTest*'`
Expected: PASS — 두 토글의 root 기준 하단 Y 가 0.5f 이내로 일치.

- [ ] **Step 7: 전체 검증(detekt·컴파일·단위테스트)**

Run: `scripts/verify-android.sh`
Expected: BUILD SUCCESSFUL. `ChatInputToggle` 삭제로 미사용 심볼 없음, import 변화 없음(`heightIn`·`clip`·`Arrangement`·`Alignment` 모두 기존 사용 중).

- [ ] **Step 8: 텍스트 모드 골든 캡처 추가 + 기존 도크 골든 재기록**

In `SessionFlowScreenshotTest.kt`, `captureDock` 헬퍼는 `textMode = false` 고정이라 텍스트 모드를 못 잡는다. 텍스트 모드 전용 캡처 테스트를 추가한다(기존 `flow_wrong_light` @Test 아래, 125행 이후):

```kotlin
    @Test
    fun flow_text_input_light() {
        composeRule.setContent {
            OceTheme(darkTheme = false) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DialogueTurnContent(
                        messages = opponent,
                        turnPhase = TurnPhase.LearnerTurn,
                        sessionPhase = SessionPhase.InTurn,
                        currentTask = task,
                        listState = rememberLazyListState(),
                        onSubmitStub = {},
                        onViewSummary = {},
                        header = header,
                        dock = { t ->
                            MicDock(
                                task = t,
                                micState = MicState.Ready,
                                waveform = waveform,
                                textMode = true,
                                textValue = "",
                                retryHint = null,
                                permanentlyDenied = false,
                                reduceMotion = true,
                                onMicTap = {},
                                onAdvance = {},
                                onToggleTextMode = {},
                                onTextChange = {},
                                onSubmitText = {},
                            )
                        },
                    )
                }
            }
        }
        composeRule.onRoot().captureRoboImage("build/outputs/roborazzi/flow_text_input_light.png")
    }
```

그런 다음 도크 골든을 재기록:
Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionFlowScreenshotTest*' -Proborazzi.record`
Expected: BUILD SUCCESSFUL. `flow_recording_light`·`flow_analyzing_light`·`flow_wrong_light`(마이크 모드 — 채팅 토글 위치/높이 미세 변화 반영) + 신규 `flow_text_input_light` 기록.

- [ ] **Step 9: 육안 대조**

`android/app/build/outputs/roborazzi/` 에서 확인:
- `flow_text_input_light.png`: 입력 필드 아래 "마이크로 말하기" 토글이 도크 하단에 안정 배치.
- `flow_recording_light.png`(또는 wrong): "채팅으로 입력하기" 토글이 이전보다 상태 문구에서 약간 더 떨어지고(중앙정렬), 도크 하단 기준으로 텍스트 모드 토글과 같은 오프셋.
두 모드 토글이 화면 하단에서 같은 높이에 오는지 확인(테스트가 이미 수치로 보장, 스크린샷은 시각 확인).

- [ ] **Step 10: 커밋**

```bash
cd /Users/hyunjun_macbook_pro/Documents/Project/project-oce-android/.claude/worktrees/silly-colden-660906
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDock.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/MicDockTogglePositionTest.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/turn/SessionFlowScreenshotTest.kt
git commit -m "fix(dialogue): unify input-mode toggle position across mic/text modes

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage:** 요구("채팅으로 입력하기"·"마이크로 말하기" 위치 동일) → 공용 `InputModeToggle`(동일 높이·정렬·패딩) + 두 호출부 교체(Step 3-5) + 수치 패리티 테스트(Step 1,6) + 시각 확인(Step 8-9). ✓

**Placeholder scan:** 모든 스텝에 실제 코드·경로·명령. 삭제 대상(ChatInputToggle 300-329)과 교체 대상(MicColumn 247, TextInputDock 368-390) 행 번호 명시. TBD/모호 표현 없음.

**Type consistency:** `InputModeToggle(icon: OceIcon, label: String, onClick: () -> Unit)` 시그니처가 Step 3 정의·Step 4·5 호출과 일치. 테스트의 `MicDock(...)` 파라미터가 실제 `internal fun MicDock` 시그니처(task/micState/waveform/textMode/textValue/retryHint/permanentlyDenied/reduceMotion + 5 콜백)와 일치. `onNodeWithText` 문구가 실제 라벨과 일치.

## Termination
단일 태스크. Converged — 구현자가 추가 설계 판단 없이 착수 가능. (단일 태스크라 자동 플랜-리뷰 라운드는 생략.)
