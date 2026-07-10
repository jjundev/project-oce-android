# 턴 피드백 시트 (PR B) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 턴 피드백 시트에서 딥(깊은 분석)을 슬림 정착 시 백그라운드로 이거-프리페치하고, `더 보기`를 고정 풋터에서 스크롤 콘텐츠 최하단으로 옮겨 "1차 노출은 자연스러운 표현까지, 바닥 스크롤 시에만 더 보기"를 실현하며, 벤다이어그램을 레거시식(원 내부 뜻 목록 + 교집합)으로 정보 강화한다.

**Architecture:** UI 2파일 + VM 1파일의 국소 변경. (1) `VennDiagramCanvas`의 draw 헬퍼만 레거시 `VennDiagramView` 좌표로 재작성(기하·색가드·시그니처 불변). (2) `SlimFeedbackContent`에서 `MoreToggleButton`을 풋터→스크롤 콘텐츠 끝으로 이동, 풋터는 `다음`만. (3) `GeneratedDialogueSession`의 `onFeedbackState`에서 슬림 `Active` 정착 시 `deep.start`를 이거 호출(코디네이터의 `Idle`-가드 idempotency에 기대 `expandDeep`의 기존 호출과 안전 공존).

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, kotlinx.coroutines, JUnit4 + Robolectric + Roborazzi(캡처 전용), detekt.

## Global Constraints

- 검증은 **반드시** `scripts/verify-android.sh`로 실행한다(워크트리 gradle 오염·`google-services.json` 부재 우회). 직접 `./gradlew` 금지. (CLAUDE.md)
- 기본 검증 세트 = `:app:detekt :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:testReleaseUnitTest`. `ktlintMainSourceSetCheck`는 master 선존재 위반으로 제외됨(스크립트 기본).
- JDK 17 타깃(`jvmToolchain(17)`), Compose. detekt는 `buildUponDefaultConfig` — 새 코드도 통과해야 함(예: `LongParameterList`는 `@Suppress`로 기존 관례 따름).
- Roborazzi 스크린샷은 **커밋된 golden 베이스라인이 없다** — `captureRoboImage`는 `build/outputs/roborazzi/*.png`(gitignore)로 캡처만 하며 diff 게이트가 없다. 따라서 레이아웃 변경이 스크린샷 테스트를 **실패시키지 않는다**. 스크린샷 단계는 "record 후 프로토타입 육안 대조"용이다(프로토타입 정합은 수동 루프).
- `VennColorGuard`는 **변경 금지** — 대비 불변식(NFR-8)을 그대로 유지. 벤 내부 텍스트 색은 가드가 검증하는 참조색과 일치시켜야 한다: 헤드워드=`onSurface`(=guard primary `0xFF353C45`/`0xFFF2F3F5`), 아이템=`onSurfaceVariant`(=guard sub `0xFF676B73`/`0xFFA9ADB6`).
- 딥은 이제 온디맨드가 아니라 **이거-프리페치**(슬림 정착 시). 비용/캡 영향은 기존 `DeepFeedbackState.QuotaBlocked` 경로가 흡수한다(추가 UI 없음).
- 커밋 메시지는 저장소 관례(한국어 `feat(session)`/`fix(session)`/`feat(ui)`) + 말미에 Co-Authored-By 라인.

---

## File Structure

- **Modify** `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennDiagramCanvas.kt`
  - draw 헬퍼(`drawVennText`)를 레거시식 `drawVennLabelsAndItems`로 교체(헤드워드 센터링 + 좌/우 `• items` + 교집합 items + 얇은 stroke). `toContentDescription`(private)→`toVennContentDescription`(internal, 아이템 포함으로 강화). 공개 시그니처 `VennDiagramCanvas(venn, modifier)`·기하 상수·색가드 호출은 불변.
- **Modify** `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackSheet.kt`
  - `SlimFeedbackContent`: `MoreToggleButton`을 스크롤 `Column`의 `Active` 분기 끝(섹션 아래)으로 이동. `SlimFooter`는 `NextButton`만. 풋터에 `testTag("slim_footer")` 부여.
- **Modify** `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt`
  - `onFeedbackState`: 슬림 `Active` 정착 시 `deep.start` 이거 호출. `deepParams` 주석 갱신.
- **Create** `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennContentDescriptionTest.kt` — 강화된 텍스트 대안 단위 테스트(순수 JVM).
- **Create** `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackSheetTest.kt` — `더 보기`가 풋터가 아닌 스크롤 콘텐츠에 있음을 검증(Robolectric compose).
- **Unchanged (의존)** `DeepFeedbackSections.kt`(캡션 `공통:` 유지, 결정 #21), `DeepFeedbackCoordinator.kt`(idempotency `start`가 이거+확장 이중호출을 흡수 — 기존 테스트가 이미 커버), `VennColorGuard.kt`.

---

## Task 1: 벤다이어그램 정보 강화(레거시식) + 텍스트 대안 강화

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennDiagramCanvas.kt`
- Create (test): `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennContentDescriptionTest.kt`

**Interfaces:**
- Consumes: `VennData(guide, left: VennCircle, right: VennCircle, intersectionItems: List<String>)`, `VennCircle(word, items: List<String>)` — `feature/session/feedback/DeepFeedbackState.kt`. `VennColorGuard.resolve()` → `VennColors(left, right, intersection)`(불투명 ARGB Int). `OceTheme.typography.{sectionLabel,helper}`, `MaterialTheme.colorScheme.{onSurface,onSurfaceVariant}`.
- Produces: `internal fun VennData.toVennContentDescription(): String` (동일 파일·패키지). 공개 `@Composable fun VennDiagramCanvas(venn: VennData, modifier: Modifier)` 시그니처 불변.

- [ ] **Step 1: 강화된 텍스트 대안 실패 테스트 작성**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennContentDescriptionTest.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.component.venn

import com.jjundev.oneclickeng.feature.session.feedback.VennCircle
import com.jjundev.oneclickeng.feature.session.feedback.VennData
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 정보 강화 벤(결정 #18): 텍스트 대안이 두 단어의 고유 뜻과 공통 의미를 모두 노출하는지 검증한다.
 * 색 단독 신호 금지(A2) — semantics contentDescription 이 다이어그램 시각의 완전한 텍스트 대안이어야 한다.
 */
class VennContentDescriptionTest {
    @Test
    fun `content description lists both words with their items and the shared meaning`() {
        val venn =
            VennData(
                guide = "두 단어의 의미 차이를 볼까요?",
                left = VennCircle(word = "get", items = listOf("얻다", "받다")),
                right = VennCircle(word = "order", items = listOf("주문하다")),
                intersectionItems = listOf("받다"),
            )
        assertEquals(
            "get(얻다, 받다)와 order(주문하다)의 공통 의미: 받다",
            venn.toVennContentDescription(),
        )
    }

    @Test
    fun `content description tolerates empty item lists`() {
        val venn =
            VennData(
                guide = "",
                left = VennCircle(word = "get", items = emptyList()),
                right = VennCircle(word = "order", items = emptyList()),
                intersectionItems = emptyList(),
            )
        assertEquals("get와 order의 공통 의미: ", venn.toVennContentDescription())
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*VennContentDescriptionTest*'`
Expected: FAIL — `Unresolved reference: toVennContentDescription` (컴파일 에러; 함수가 private `toContentDescription`이라 접근 불가).

- [ ] **Step 3: `toContentDescription`을 internal 로 승격·강화**

In `VennDiagramCanvas.kt`, replace the existing private helper (현재 lines ~110–114):

```kotlin
/** 색 단독 신호 금지(A2): "<left>와 <right>의 공통 의미: <intersection items>" 텍스트 대안. */
private fun VennData.toContentDescription(): String {
    val shared = intersectionItems.joinToString(", ")
    return "${left.word}와 ${right.word}의 공통 의미: $shared"
}
```

with:

```kotlin
/**
 * 색 단독 신호 금지(A2)의 텍스트 대안. 정보 강화(결정 #18)로 각 단어의 고유 뜻(items)까지 노출해
 * 다이어그램 내부 시각(좌/우 • items + 교집합)의 완전한 텍스트 대안이 되게 한다. items 가 비면 괄호를 생략한다.
 */
internal fun VennData.toVennContentDescription(): String {
    fun withItems(circle: VennCircle) =
        if (circle.items.isEmpty()) circle.word else "${circle.word}(${circle.items.joinToString(", ")})"
    val shared = intersectionItems.joinToString(", ")
    return "${withItems(left)}와 ${withItems(right)}의 공통 의미: $shared"
}
```

Then update the caller inside `VennDiagramCanvas` (현재 line ~61):

```kotlin
    val description = venn.toContentDescription()
```

to:

```kotlin
    val description = venn.toVennContentDescription()
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*VennContentDescriptionTest*'`
Expected: PASS (2 tests).

- [ ] **Step 5: draw 헬퍼를 레거시식 라벨+아이템으로 재작성**

In `VennDiagramCanvas.kt`, add imports near the existing Compose imports:

```kotlin
import androidx.compose.ui.graphics.drawscope.Stroke
```

In the composable body, add an item text style alongside the existing `labelStyle` (현재 line ~58–59). Replace:

```kotlin
    val labelStyle =
        OceTheme.typography.sectionLabel.copy(color = MaterialTheme.colorScheme.onSurface)
```

with:

```kotlin
    val labelStyle =
        OceTheme.typography.sectionLabel.copy(color = MaterialTheme.colorScheme.onSurface)
    // 아이템(뜻 목록)은 보조 텍스트급. 색은 가드가 검증하는 sub 참조색(onSurfaceVariant)과 일치시켜
    // 측면 원 위 대비 ≥3.0 불변식(VennColorGuard.MIN_SUB_CONTRAST_SIDE)을 그대로 만족한다.
    val itemStyle =
        OceTheme.typography.helper.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    // 원 경계 stroke(정보 강화 시 뜻 목록과 원 배경 경계를 명료화 — 레거시 strokePaint 정합). 불투명 side 색.
    val leftStroke = Color(colors.left)
    val rightStroke = Color(colors.right)
```

In the `onDrawBehind` block (현재 lines ~80–87), replace:

```kotlin
                    onDrawBehind {
                        drawCircle(color = leftColor, radius = r, center = leftCenter)
                        drawCircle(color = rightColor, radius = r, center = rightCenter)
                        clipPath(lens) { drawRect(color = intersectionColor) }
                        drawVennText(
                            textMeasurer, venn, leftCenter, rightCenter, r, cy, labelStyle,
                        )
                    }
```

with:

```kotlin
                    onDrawBehind {
                        drawCircle(color = leftColor, radius = r, center = leftCenter)
                        drawCircle(color = rightColor, radius = r, center = rightCenter)
                        clipPath(lens) { drawRect(color = intersectionColor) }
                        drawCircle(color = leftStroke, radius = r, center = leftCenter, style = Stroke(1.dp.toPx()))
                        drawCircle(color = rightStroke, radius = r, center = rightCenter, style = Stroke(1.dp.toPx()))
                        drawVennLabelsAndItems(
                            textMeasurer, venn, leftCenter, rightCenter, r, cy, labelStyle, itemStyle,
                        )
                    }
```

Then replace the whole `drawVennText` function (현재 lines ~92–108) with:

```kotlin
/**
 * 헤드워드(원 상단, 중앙 정렬) + 좌/우 고유 뜻(• items, 비겹침 lobe) + 교집합 뜻(렌즈 중앙)을 그린다.
 * 레거시 [VennDiagramView.drawLabelsAndItems] 좌표를 Compose 로 이식(정보 강화, 결정 #18). 모든 텍스트는
 * 측정 후 x 중앙 정렬하고 캔버스 폭 안으로 clamp 해 긴 단어 클리핑을 방어한다(결정 #15).
 */
@Suppress("LongParameterList")
private fun DrawScope.drawVennLabelsAndItems(
    measurer: TextMeasurer,
    venn: VennData,
    leftCenter: Offset,
    rightCenter: Offset,
    r: Float,
    cy: Float,
    labelStyle: TextStyle,
    itemStyle: TextStyle,
) {
    // 헤드워드: 원 상단(cy - r·0.5), 좌우 대칭으로 lobe 바깥쪽에 센터 앵커(레거시 labelOffset·0.3 정합).
    drawCenteredText(measurer, venn.left.word, Offset(leftCenter.x - r * 0.30f, cy - r * 0.50f), labelStyle)
    drawCenteredText(measurer, venn.right.word, Offset(rightCenter.x + r * 0.30f, cy - r * 0.50f), labelStyle)
    // 좌/우 고유 뜻: 각 lobe 비겹침 영역에 세로 누적(• 접두).
    drawItemColumn(measurer, venn.left.items, Offset(leftCenter.x - r * 0.40f, cy - r * 0.20f), itemStyle, bullet = true)
    drawItemColumn(measurer, venn.right.items, Offset(rightCenter.x + r * 0.40f, cy - r * 0.20f), itemStyle, bullet = true)
    // 교집합 뜻: 렌즈 중앙(두 중심의 중점), 접두 없음.
    val mid = (leftCenter.x + rightCenter.x) / 2f
    drawItemColumn(measurer, venn.intersectionItems, Offset(mid, cy + r * 0.10f), itemStyle, bullet = false)
}

/** [center]를 텍스트의 시각 중앙으로 두고 그린다(측정 폭·높이 절반 보정, x 는 캔버스 폭 clamp). */
private fun DrawScope.drawCenteredText(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    style: TextStyle,
) {
    if (text.isEmpty()) return
    val layout = measurer.measure(text, style)
    val x = (center.x - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
    drawText(layout, topLeft = Offset(x, center.y - layout.size.height / 2f))
}

/** [top].x 를 중앙으로 아이템을 세로 누적한다(각 줄 측정 높이 + 2dp gap). */
private fun DrawScope.drawItemColumn(
    measurer: TextMeasurer,
    items: List<String>,
    top: Offset,
    style: TextStyle,
    bullet: Boolean,
) {
    var y = top.y
    val gap = 2.dp.toPx()
    items.forEach { item ->
        val label = if (bullet) "• $item" else item
        val layout = measurer.measure(label, style)
        val x = (top.x - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
        drawText(layout, topLeft = Offset(x, y))
        y += layout.size.height + gap
    }
}
```

Note: `drawText(TextLayoutResult, topLeft)` overload is already available via the existing `import androidx.compose.ui.text.drawText`.

- [ ] **Step 6: 프리뷰 데이터로 다중 아이템 lobe 확인(캡처 없음, 컴파일만)**

기존 `VennDiagramCanvasPreview`(현재 line ~116)는 이미 좌 `items=["얻다","받다"]`, 우 `items=["주문하다"]`, 교집합 `["받다"]`를 넘긴다 — 변경 불필요. 재작성이 컴파일되는지 확인:

Run: `scripts/verify-android.sh :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: 스크린샷 record 후 프로토타입 육안 대조(게이트 아님)**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionFlowScreenshotTest*' -Proborazzi.record`
Expected: PASS. `android/app/build/outputs/roborazzi/flow_deep_light.png`를 열어 벤 원 안에 좌(order: • 격식 있는 주문 / • 메뉴 지정)·우(get: • 구어체 / • 가볍게) 뜻과 교집합 뜻(주문하다)이 보이고 원 밖으로 넘치지 않는지 확인. (기대 단어는 실제 픽스처 `SessionFlowScreenshotTest.kt:281-283` 기준 — 프리뷰 데이터와 다름. diff 게이트 없음 — 육안 확인만.)

- [ ] **Step 8: 전체 검증**

Run: `scripts/verify-android.sh :app:detekt :app:testDebugUnitTest --tests '*Venn*'`
Expected: detekt PASS, `VennContentDescriptionTest`·`VennColorGuardTest` PASS.

- [ ] **Step 9: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennDiagramCanvas.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennContentDescriptionTest.kt
git commit -m "$(cat <<'EOF'
feat(ui): 벤다이어그램 정보 강화 — 원 내부 뜻 목록·교집합 렌더 + 텍스트 대안 확장

레거시 VennDiagramView 좌표를 Compose 로 이식해 좌/우 고유 뜻(• items)과 교집합 뜻을
원 안에 그린다. 헤드워드는 측정 후 중앙 정렬·캔버스 폭 clamp. contentDescription 도
각 단어의 뜻을 포함해 완전한 텍스트 대안(A2)이 되게 한다. 색가드·기하·시그니처 불변.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: `더 보기` 스크롤-리빌(풋터 → 콘텐츠 최하단)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackSheet.kt`
- Create (test): `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackSheetTest.kt`

**Interfaces:**
- Consumes: `SlimFeedbackState.Active(header, writingScore, grammar, natural)`(+`nextEnabled`), `SectionState.Ready`, `WritingScore`, `Grammar`, `NaturalExpression`, `Reason`, `RecapHeader`, `RichSegment.Normal` — `feature/session/feedback`. `OceTheme`.
- Produces: 동작 계약 — `Active`·`!deepExpanded`에서 `더 보기`는 스크롤 콘텐츠 끝(자연 섹션 아래)에, `다음`은 `testTag("slim_footer")` 풋터에 렌더. `deepExpanded`면 `더 보기` 미노출(딥이 인라인). 공개 시그니처(`SlimFeedbackSheet`, `SlimFeedbackContent`) 불변.

- [ ] **Step 1: 풋터에 testTag 부여 + 실패 테스트 작성**

먼저 `SlimFooter`(현재 line ~307–320)에 testTag 를 단다. Add import:

```kotlin
import androidx.compose.ui.platform.testTag
```

Modify `SlimFooter`:

```kotlin
@Composable
private fun SlimFooter(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("slim_footer")
                // 시스템 내비게이션 바 인셋만큼 하단을 비워 "다음" 버튼이 제스처 바/버튼 바에 잘리지 않게 한다.
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm),
        content = content,
    )
}
```

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackSheetTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.feedback

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.jjundev.oneclickeng.ui.component.RichSegment
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 스크롤-리빌 계약(결정 #2,#3): `더 보기`는 스크롤 콘텐츠에, `다음`만 고정 풋터에 있어야 한다.
 * 슬림 시트는 프로덕션에서 별도 윈도가 아니므로 [SlimFeedbackContent] 를 직접 렌더해 노드 트리를 검증한다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class SlimFeedbackSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun active(): SlimFeedbackState.Active =
        SlimFeedbackState.Active(
            header = RecapHeader(koreanPrompt = "라떼 한 잔을 주문해보세요", userText = "Can I get a latte?"),
            writingScore = SectionState.Ready(WritingScore(score = 92, encouragement = "좋아요")),
            grammar = SectionState.Ready(Grammar(listOf(RichSegment.Normal("Can I get a latte?")), "정확해요")),
            natural =
                SectionState.Ready(
                    NaturalExpression(listOf(RichSegment.Normal("Can I get a latte?")), Reason("자연스러움", "이미 자연스러워요")),
                ),
        )

    @Test
    fun more_toggle_lives_in_scroll_content_and_footer_holds_only_next() {
        composeRule.setContent {
            OceTheme { SlimFeedbackContent(state = active(), onRetry = {}, onSkip = {}, onNext = {}) }
        }

        // 두 어포던스 모두 컴포즈됨(더 보기는 스크롤 콘텐츠에 존재).
        composeRule.onNodeWithText("더 보기").assertExists()
        composeRule.onNodeWithText("다음").assertIsDisplayed()

        // 고정 풋터는 "다음"만 — "더 보기"는 풋터 밖.
        composeRule.onNode(hasTestTag("slim_footer") and hasAnyDescendant(hasText("다음"))).assertExists()
        composeRule.onNode(hasTestTag("slim_footer") and hasAnyDescendant(hasText("더 보기"))).assertDoesNotExist()
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SlimFeedbackSheetTest*'`
Expected: FAIL — `더 보기`가 아직 풋터 안에 있어 `hasTestTag("slim_footer") and hasAnyDescendant(hasText("더 보기"))` 노드가 존재해 `assertDoesNotExist()`가 실패.

- [ ] **Step 3: `MoreToggleButton`을 스크롤 콘텐츠 끝으로 이동, 풋터는 `NextButton`만**

In `SlimFeedbackContent`, 스크롤 `Column`의 `Active` 분기(현재 lines ~236–271). `if (deepExpanded && ...) DeepFeedbackRegion(...)`를 감싸는 안쪽 `Column(spacedBy(sectionGap))` **뒤**에 인라인 토글을 추가한다. 분기를 아래로 교체:

```kotlin
                is SlimFeedbackState.Active -> {
                    Text(
                        text = "턴 피드백",
                        style = OceTheme.typography.summaryHeadline,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    RecapHeaderBlock(state.header)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    // 섹션 간 간격을 넓혀(sectionGap=24) 작문·문법·자연을 뚜렷이 구분한다.
                    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sectionGap)) {
                        SlimSectionBlock(OceIcon.EditNote, "작문 점수", MaterialTheme.colorScheme.primary) {
                            SectionSlot(SlimSection.WritingScore, state.writingScore, onRetry, onSkip) {
                                WritingScoreContent(it)
                            }
                        }
                        SlimSectionBlock(OceIcon.Spellcheck, "문법", MaterialTheme.colorScheme.onSurfaceVariant) {
                            SectionSlot(SlimSection.Grammar, state.grammar, onRetry, onSkip) { GrammarContent(it) }
                        }
                        SlimSectionBlock(OceIcon.AutoAwesome, "자연스러운 표현", OceTheme.colors.feedbackNaturalAccent) {
                            SectionSlot(SlimSection.NaturalExpression, state.natural, onRetry, onSkip) {
                                NaturalContent(it)
                            }
                        }
                        // 심화(더 보기)는 턴 피드백의 연장 — 같은 섹션 리스트에 이어 붙어 슬림과 동일한 섹션 간격(24)을
                        // 공유한다(별도 구분선 없음). 딥 블록 내부 간격/헤더도 슬림 섹션과 같은 디자인 시스템을 쓴다.
                        if (deepExpanded && deepState !is DeepFeedbackState.Idle) {
                            DeepFeedbackRegion(
                                state = deepState,
                                onRetry = onRetryDeep,
                                bookmarkedLevels = bookmarkedLevels,
                                onToggleBookmark = onToggleBookmark,
                                modifier = Modifier.bringIntoViewRequester(deepReveal),
                            )
                        }
                    }
                    // "더 보기"를 고정 풋터가 아니라 스크롤 콘텐츠 끝(자연 섹션 아래)에 둔다 — 1차 노출은
                    // 자연스러운 표현까지고, 바닥까지 스크롤해야 드러난다(결정 #2). 펼쳐지면 토글은 사라지고
                    // 딥이 위 섹션 리스트에 인라인으로 이어진다(결정 #6). 게이트는 nextEnabled 재사용("다음"과 동일).
                    if (!deepExpanded) {
                        MoreToggleButton(
                            expanded = false,
                            enabled = state.nextEnabled,
                            onClick = onExpandDeep,
                        )
                    }
                }
```

이제 하단 고정 풋터 `when(state)`(현재 lines ~284–302)를 `NextButton`만 남기게 교체한다. 현재 블록:

```kotlin
        // 하단 고정 버튼 풋터.
        when (state) {
            is SlimFeedbackState.Active ->
                SlimFooter {
                    // 더 보기 게이트 = nextEnabled 재사용(모두 settled) — "다음"과 동일 술어(A4/A5).
                    // 펼쳐진 뒤에는 토글을 아예 없앤다(접기 버튼 미노출) — "다음"만 남긴다.
                    if (!deepExpanded) {
                        MoreToggleButton(
                            expanded = deepExpanded,
                            enabled = state.nextEnabled,
                            onClick = onExpandDeep,
                        )
                    }
                    NextButton(enabled = state.nextEnabled, onNext = onNext)
                }
            is SlimFeedbackState.QuotaBlocked ->
                SlimFooter { NextButton(enabled = true, onNext = onNext) } // 캡 거부 → "다음"만
            is SlimFeedbackState.Idle -> Unit
        }
```

교체 후:

```kotlin
        // 하단 고정 버튼 풋터 — "다음"만(항상 도달 가능한 진행/탈출). "더 보기"는 스크롤 콘텐츠로 이동(결정 #3).
        when (state) {
            is SlimFeedbackState.Active ->
                SlimFooter { NextButton(enabled = state.nextEnabled, onNext = onNext) }
            is SlimFeedbackState.QuotaBlocked ->
                SlimFooter { NextButton(enabled = true, onNext = onNext) } // 캡 거부 → "다음"만
            is SlimFeedbackState.Idle -> Unit
        }
```

`MoreToggleButton`의 KDoc 은 그대로 두되(시그니처 불변), 클래스 상단 KDoc 의 "시트 하단 [더 보기/접기]" 서술은 그대로 유효(위치만 콘텐츠 끝으로 이동). `onCollapseDeep` 미사용 seam·`@Suppress("UnusedParameter")`는 유지.

- [ ] **Step 4: 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SlimFeedbackSheetTest*'`
Expected: PASS.

- [ ] **Step 5: 스크린샷 record 후 육안 대조(게이트 아님)**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionFlowScreenshotTest*' -Proborazzi.record`
Expected: PASS. `flow_feedback_light.png`에서 `더 보기`가 자연 섹션 아래(콘텐츠 흐름)에 있고, `다음`이 별도 하단 풋터에 있는지 확인.

- [ ] **Step 6: 전체 검증(detekt + 컴파일 + 단위)**

Run: `scripts/verify-android.sh :app:detekt :app:testDebugUnitTest --tests '*Slim*'`
Expected: detekt PASS, `SlimFeedbackSheetTest`·`SlimFeedbackCoordinatorTest` PASS.

- [ ] **Step 7: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackSheet.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/SlimFeedbackSheetTest.kt
git commit -m "$(cat <<'EOF'
feat(session): 턴 피드백 "더 보기"를 고정 풋터→스크롤 콘텐츠 끝으로 이동(스크롤 리빌)

1차 노출은 자연스러운 표현까지, 바닥까지 스크롤해야 "더 보기"가 드러난다. "다음"만
고정 풋터에 남겨 항상 도달 가능한 진행/탈출 수단으로 유지한다. 풋터에 testTag 부여 +
노드 트리 검증 테스트 추가.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: 딥 이거-프리페치(슬림 정착 시 백그라운드 개시)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt`

**Interfaces:**
- Consumes: `SlimFeedbackState`(`Active`/`QuotaBlocked`/`Idle`), `DeepFeedbackCoordinator.start(sessionId, turnIndex, koreanPrompt, userEnglish, referenceEnglish, level)`(이미 turn당 1회 `Idle`-가드 idempotent — `DeepFeedbackCoordinatorTest`의 `start is a no-op once past Idle` 로 검증됨), 기존 `deepParams: DeepParams?`, `pendingTurn: PendingTurn?`.
- Produces: 관측 가능한 동작 — 슬림 3섹션이 종결(Ready/Failed/Skipped)되는 즉시(사용자 `더 보기` 탭 이전) 딥 SSE 가 개시된다. `expandDeep`의 기존 `deep.start`는 변경 없이 fallback(no-op)으로 공존.

**참고 — 자동 단위 테스트 없음(사유 명시):** `GeneratedDialogueSessionViewModel`은 `src/test`에 단위 테스트 하네스가 없다(8개 협력자 + `SavedStateHandle` 생성 비용이 3줄 배선 변경에 비해 과대). 이 변경이 기대는 불변식(이거 개시 + `expandDeep`의 이중 `start` 안전성)은 이미 `DeepFeedbackCoordinatorTest`가 커버한다. 따라서 이 태스크는 **수동/계측 검증**으로 게이트한다(아래 Step 3). 배선 자체는 컴파일·detekt 로 확인한다.

- [ ] **Step 1: `onFeedbackState`에서 슬림 `Active` 정착 시 딥 이거 개시**

In `GeneratedDialogueSession.kt`, replace `onFeedbackState`(현재 lines ~498–510):

```kotlin
        private fun onFeedbackState(state: SlimFeedbackState) {
            val pending = pendingTurn ?: return
            val resolved =
                when (state) {
                    is SlimFeedbackState.Active ->
                        state.writingScore !is SectionState.Loading &&
                            state.grammar !is SectionState.Loading &&
                            state.natural !is SectionState.Loading
                    is SlimFeedbackState.QuotaBlocked -> true
                    SlimFeedbackState.Idle -> false
                }
            if (resolved) recordTurn(pending)
        }
```

with:

```kotlin
        private fun onFeedbackState(state: SlimFeedbackState) {
            val pending = pendingTurn ?: return
            val resolved =
                when (state) {
                    is SlimFeedbackState.Active ->
                        state.writingScore !is SectionState.Loading &&
                            state.grammar !is SectionState.Loading &&
                            state.natural !is SectionState.Loading
                    is SlimFeedbackState.QuotaBlocked -> true
                    SlimFeedbackState.Idle -> false
                }
            if (resolved) {
                recordTurn(pending)
                // 딥 이거-프리페치: 슬림 3섹션이 종결되는 즉시 딥을 백그라운드로 개시해, 사용자가 바닥까지
                // 스크롤해 "더 보기"를 누를 때 대기 없이 즉시 펼쳐지게 한다(온디맨드→이거, 결정 #17/#19).
                // recordTurn 이 pendingTurn 을 비우므로 이 블록은 정착 emission 1회에만 실행된다.
                // 캡 거부(QuotaBlocked)면 딥도 동일 세션 캡에 걸리므로 개시하지 않는다(불필요 왕복 회피, 결정 #20).
                // start()는 Idle 이 아니면 no-op 이라 이후 [expandDeep]의 재호출과 안전하게 공존한다(P3).
                if (state is SlimFeedbackState.Active) {
                    deepParams?.let { p ->
                        deep.start(p.sessionId, p.turnIndex, p.koreanPrompt, p.userText, p.referenceEnglish, p.level)
                    }
                }
            }
        }
```

- [ ] **Step 2: `deepParams` 주석을 이거-프리페치로 갱신**

`deepParams` 선언 주석(현재 lines ~254–256)을 교체:

```kotlin
        // deep("더 보기")는 사용자가 시트에서 확장할 때 개시되므로, 현재 턴의 start 파라미터를 stash 해 두었다가
        // [expandDeep] 에서 [DeepFeedbackCoordinator.start] 로 넘긴다(턴 전환 시 [onAdvance] 가 비운다).
        private var deepParams: DeepParams? = null
```

with:

```kotlin
        // deep("더 보기") start 파라미터 stash. 이제 슬림 정착 시 [onFeedbackState] 가 이거-프리페치로 개시하고,
        // [expandDeep] 는 fallback 재호출(no-op)로 공존한다. 턴 전환 시 [onAdvance] 가 비운다.
        private var deepParams: DeepParams? = null
```

- [ ] **Step 3: 계측 수동 검증(게이트) + 컴파일/detekt**

먼저 배선 컴파일·정적분석:

Run: `scripts/verify-android.sh :app:detekt :app:compileDebugKotlin`
Expected: detekt PASS, BUILD SUCCESSFUL.

그다음 실제 이거-프리페치 동작을 계측으로 확인한다(자동 게이트 없음). `verify` 스킬 또는 앱 실행으로 한 턴을 진행하고, **`더 보기`를 누르기 전에** 딥 SSE 요청이 나가는지 확인한다(예: `DeepFeedbackStream` 호출 로그/네트워크). 확인 포인트:
- 슬림 3섹션이 Ready 로 정착한 직후 `feedbackDeep` 요청이 1회 발생.
- 바닥 스크롤 후 `더 보기` 탭 시 로딩 시머 없이(또는 최소로) 딥이 즉시 표시.
- 다음 턴 진행(`onAdvance`) 후 `deep.reset()`으로 이전 딥이 폐기되고, 새 턴 정착 시 새 요청 1회.
Expected: 위 3개 관측 성립.

- [ ] **Step 4: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt
git commit -m "$(cat <<'EOF'
feat(session): 딥(깊은 분석) 이거-프리페치 — 슬림 정착 시 백그라운드 개시

온디맨드→이거로 전환. 슬림 3섹션이 종결되면 "더 보기" 탭 이전에 feedbackDeep 를
백그라운드로 개시해, 바닥 스크롤 후 즉시 펼쳐지게 한다. 캡 거부 시엔 개시하지 않고,
start() 의 Idle-가드 idempotency 로 expandDeep 의 기존 호출과 안전 공존한다.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

## Final Verification

- [ ] **전체 기본 검증 세트 실행**

Run: `scripts/verify-android.sh`
Expected: `:app:detekt`, `:app:compileDebugAndroidTestKotlin`, `:app:testDebugUnitTest`, `:app:testReleaseUnitTest` 모두 PASS. (스크린샷 테스트는 capture-only 라 실패 게이트 없음. androidTest `GeneratedDialogueSessionContentTest`는 계측 테스트라 여기선 **컴파일만** 검증됨 — 버튼 위치 참조가 있으면 컴파일 단계에서 드러난다.)

---

## Self-Review

**1. Spec coverage (요청 대비):**
- "전 항목 미리 로드" (딥 이거-프리페치, 결정 #17/#19) → **Task 3**.
- "자연스러운 표현까지만 1차 노출 + 하단 스크롤 시에만 더 보기" (결정 #2/#3) → **Task 2**.
- "다이어그램 보완" (정보 강화, 결정 #18) → **Task 1**.
- 캡션 유지(#21)·색가드 불변·프로토 정합 육안 루프 → Global Constraints + Task 1 Step 7 / Task 2 Step 5.
- 비용/캡(#20) → Task 3 코드 주석의 QuotaBlocked 가드 + 기존 경로 흡수(추가 작업 없음).

**2. Placeholder scan:** 모든 코드 스텝에 실제 Kotlin 코드·정확한 파일/명령. Task 3의 "자동 테스트 없음"은 은폐가 아니라 사유를 명시한 의도적 수동 게이트(하네스 부재 + 이미 커버되는 불변식). "TODO/적절히 처리" 류 없음.

**3. Type consistency:**
- `toVennContentDescription()` — Task 1에서 정의(internal)·같은 파일 caller·Step 1 테스트 모두 동일 명칭.
- `drawVennLabelsAndItems(measurer, venn, leftCenter, rightCenter, r, cy, labelStyle, itemStyle)` — `onDrawBehind` 호출부 인자 순서 일치. 헬퍼 `drawCenteredText`/`drawItemColumn` 시그니처 일치.
- `testTag("slim_footer")` — Task 2 Step 1(부여)·Step 1 테스트(`hasTestTag("slim_footer")`) 동일 문자열.
- `deep.start(sessionId, turnIndex, koreanPrompt, userEnglish, referenceEnglish, level)` — Task 3 호출부가 `DeepFeedbackCoordinator.start` 및 기존 `expandDeep` 호출과 동일 시그니처(`DeepParams` 필드명 `userText`→`userEnglish` 파라미터 매핑은 기존 `expandDeep`와 동일).
- `MoreToggleButton(expanded, enabled, onClick)` — Task 2에서 `expanded = false` 명시(콘텐츠에서 !deepExpanded 시에만 렌더). 시그니처 불변.

이슈 없음 — 계획 확정.
