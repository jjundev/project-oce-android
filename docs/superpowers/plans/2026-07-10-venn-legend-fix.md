# 개념 브리지 벤 겹침 수정 (뜻→레전드) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 개념 브리지 벤다이어그램에서 원 안에 그리던 뜻(items·교집합)을 원 밖 읽기 쉬운 텍스트 레전드로 옮겨 긴 서술 문구가 겹쳐 그려지던 문제를 없애고, 아울러 백엔드 프롬프트가 items를 짧게 내도록 계약을 강화한다.

**Architecture:** 두 개의 독립 트랙. **Track A(클라이언트, Android 워크트리):** `VennDiagramCanvas`는 헤드워드만 그리는 프로토타입-최소형으로 되돌리고, 좌/우 고유 뜻·교집합 뜻은 `DeepFeedbackSections.ConceptualBridgeBlock` 아래에 Compose 텍스트 레전드로 렌더(임의 길이에도 줄바꿈되어 겹침 불가). **Track B(백엔드, `functions/`):** `FEEDBACK_DEEP_SYSTEM_PROMPT`의 venn `items` 지시를 길이 제약으로 강화하고 프롬프트 버전을 bump. 두 트랙은 상호 보완이지만 독립적으로 검증·머지 가능(백엔드 배포는 수동).

**Tech Stack:** Kotlin, Jetpack Compose, JUnit4 + Robolectric + Roborazzi(캡처 전용), detekt (Track A). TypeScript, Firebase Functions, Jest, ESLint (Track B).

## Global Constraints

- **Track A 검증은 반드시** `scripts/verify-android.sh`로 실행한다(워크트리 gradle 오염·google-services.json 부재 우회). 직접 `./gradlew` 금지. 새 코드는 detekt(`buildUponDefaultConfig`) 통과.
- `VennColorGuard`는 **변경 금지**(대비 불변식 NFR-8 유지). 원 위 텍스트는 이제 헤드워드(측면 원, `onSurface`=guard primary)뿐 — 교집합 렌즈 위 텍스트 없음.
- `VennData.toVennContentDescription()`(강화본, 좌/우 items+교집합 포함)은 **유지** — 시각 레전드와 별개로 A2 텍스트 대안을 계속 제공한다.
- Roborazzi는 커밋된 golden 베이스라인이 없다(capture-only, diff 게이트 없음). 스크린샷 단계는 육안 대조용이며 테스트를 실패시키지 않는다.
- 시트 적응형 높이(`heightIn(max)` + `weight(fill=false)`)는 이미 반영됨 — 회귀시키지 말 것.
- **Track B 검증**: `cd functions && npm ci && npm run build && npm run lint && npm test`. 프롬프트 텍스트/스키마 변경 시 `FEEDBACK_DEEP_PROMPT_VERSION`을 **반드시 bump**(cacheKey 무효화). **배포는 수동**(`npm run deploy` = `firebase deploy --only functions`, 리전 asia-northeast3) — 이 태스크는 배포하지 않는다. 사용자에게 배포 필요를 명시.
- 커밋 메시지: Track A는 한국어 `fix(ui)`/`fix(session)`, Track B는 `fix(functions)`, 말미에 Co-Authored-By 라인.

---

## Task 1: 벤 뜻을 원 밖 레전드로 이전 (Track A · 클라이언트)

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennDiagramCanvas.kt`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackSections.kt`
- Create (test): `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/ConceptualBridgeLegendTest.kt`

**Interfaces:**
- Consumes: `VennData(guide, left: VennCircle, right: VennCircle, intersectionItems: List<String>)`, `VennCircle(word, items: List<String>)`, `ConceptualBridge(literalTranslation, explanation, venn)`, `ToneStyle(defaultLevel, levels)`, `ToneLevel(level, sentence, sentenceTranslation)`, `Paraphrasing(items)`, `Paraphrase(level, label, sentence, sentenceTranslation)`, `DeepFeedbackState.Ready(conceptualBridge, toneStyle, paraphrasing)`, `DeepFeedbackRegion(state, onRetry, bookmarkedLevels, onToggleBookmark, modifier)` — all in `feature/session/feedback`. `OceTheme.{typography,colors,spacing}`, `MaterialTheme.colorScheme.{primary,onSurface,onSurfaceVariant}`.
- Produces: `VennDiagramCanvas(venn, modifier)` still public/unchanged signature, but now draws ONLY headwords inside circles. `toVennContentDescription()` unchanged. New private `VennMeaningLegend` composable inside DeepFeedbackSections.kt (not exported).

- [ ] **Step 1: 레전드 실패 테스트 작성 (긴 뜻이 legible Text 노드로 렌더되는지)**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/ConceptualBridgeLegendTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.session.feedback

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.jjundev.oneclickeng.ui.theme.OceTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 개념 브리지 회귀 가드: 실데이터의 긴 서술형 뜻은 원 안(canvas)이 아니라 다이어그램 아래 레전드에
 * **읽기 쉬운 Text 노드**로 렌더돼야 한다. canvas drawText 는 semantics Text 노드를 만들지 않으므로,
 * 아래 onNodeWithText 는 레전드가 실제 Composable 텍스트일 때만 통과한다(겹침 버그의 회귀 방지).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class ConceptualBridgeLegendTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val longLeft = "물건을 사고 받은 증명서를 건네줄 때 쓰는 표현"
    private val longRight = "어떤 것을 건네주며 상황을 전달할 때 쓰는 표현"

    private fun ready(): DeepFeedbackState.Ready =
        DeepFeedbackState.Ready(
            conceptualBridge =
                ConceptualBridge(
                    literalTranslation = "이 셔츠를 반품하고 싶어요.",
                    explanation = "의도는 맞지만 더 자연스럽게 말할 수 있어요.",
                    venn =
                        VennData(
                            guide = "두 표현의 차이를 볼까요?",
                            left = VennCircle(word = "receipt", items = listOf(longLeft)),
                            right = VennCircle(word = "here is", items = listOf(longRight)),
                            intersectionItems = listOf("상황 전달", "물건의 존재를 알림"),
                        ),
                ),
            toneStyle =
                ToneStyle(
                    defaultLevel = 0,
                    levels = listOf(ToneLevel(0, "Here is the receipt.", "여기 영수증입니다.")),
                ),
            paraphrasing =
                Paraphrasing(items = listOf(Paraphrase(1, "Beginner", "Here's the receipt.", "여기 영수증이요."))),
        )

    @Test
    fun meanings_render_as_legible_text_nodes_below_the_diagram() {
        composeRule.setContent {
            OceTheme {
                DeepFeedbackRegion(
                    state = ready(),
                    onRetry = {},
                    bookmarkedLevels = emptySet(),
                    onToggleBookmark = {},
                )
            }
        }
        // 헤드워드 + 좌/우 긴 뜻 + 교집합 뜻이 모두 실제 Text 노드로 존재해야 한다.
        composeRule.onNodeWithText("receipt", substring = true).assertExists()
        composeRule.onNodeWithText("here is", substring = true).assertExists()
        composeRule.onNodeWithText(longLeft, substring = true).assertExists()
        composeRule.onNodeWithText(longRight, substring = true).assertExists()
        composeRule.onNodeWithText("상황 전달", substring = true).assertExists()
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ConceptualBridgeLegendTest*'`
Expected: FAIL — `longLeft`/`longRight`가 현재 canvas drawText(비-semantics)로만 그려져 `onNodeWithText`가 찾지 못함(레전드 Text 노드 부재).

- [ ] **Step 3: `DeepFeedbackSections.kt`에 뜻 레전드 추가**

Add imports near the existing Compose imports (top of file):

```kotlin
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
```

Replace the `ConceptualBridgeBlock` composable (현재 lines ~190–214) with:

```kotlin
/** ④ 개념 브릿지 — 간극 설명(order·get 강조) + 벤(헤드워드만) + 뜻 레전드(좌/우 고유 뜻·공통). */
@Composable
private fun ConceptualBridgeBlock(value: ConceptualBridge) {
    val emphasis = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
        DeepSectionHeader(icon = OceIcon.Hub, label = "개념 브리지")
        Text(
            text = emphasizeWords(value.explanation, listOf(value.venn.left.word, value.venn.right.word), emphasis),
            style = OceTheme.typography.helper.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VennDiagramCanvas(venn = value.venn, modifier = Modifier.align(Alignment.CenterHorizontally))
        VennMeaningLegend(value.venn)
    }
}

/**
 * 벤 아래 뜻 레전드. 원 안에는 헤드워드만 그리고(긴 서술 문구가 원을 넘쳐 겹치던 문제 회피), 좌/우 고유 뜻과
 * 교집합 뜻은 여기 흐르는 Compose 텍스트로 노출한다 — 임의 길이에도 줄바꿈되어 겹치지 않는다. 좌=브랜드 블루,
 * 우=natural 그린 점으로 원과 시각적으로 대응시킨다(팔레트 폴백색 = VennColorGuard 시작색). 공통은 강조 라벨.
 */
@Composable
private fun VennMeaningLegend(venn: VennData) {
    val leftDot = MaterialTheme.colorScheme.primary
    val rightDot = OceTheme.colors.feedbackNaturalAccent
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs)) {
        VennLegendLine(leftDot, venn.left.word, venn.left.items)
        VennLegendLine(rightDot, venn.right.word, venn.right.items)
        if (venn.intersectionItems.isNotEmpty()) {
            Text(
                text =
                    buildAnnotatedString {
                        withStyle(
                            SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                        ) { append("공통: ") }
                        append(venn.intersectionItems.joinToString(", "))
                    },
                style = OceTheme.typography.helper,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 레전드 한 줄 = 색 점 + 굵은 단어(점 색) + 뜻 목록(보조색). 뜻이 비면 단어만 표시. */
@Composable
private fun VennLegendLine(
    dotColor: Color,
    word: String,
    meanings: List<String>,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(OceTheme.spacing.xs),
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 5.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
        )
        Text(
            text =
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = dotColor)) { append(word) }
                    if (meanings.isNotEmpty()) append("  ${meanings.joinToString(", ")}")
                },
            style = OceTheme.typography.helper,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ConceptualBridgeLegendTest*'`
Expected: PASS (레전드 Text 노드가 좌/우 헤드워드·긴 뜻·교집합을 모두 노출).

- [ ] **Step 5: `VennDiagramCanvas.kt`를 헤드워드-전용(프로토 최소형)으로 되돌리기**

In `VennDiagramCanvas.kt`, remove the now-unused import:

```kotlin
import androidx.compose.ui.graphics.drawscope.Stroke
```

Replace the item/stroke style vals (현재 lines ~61–72) — delete them so only `labelStyle` remains:

```kotlin
    val labelStyle =
        OceTheme.typography.sectionLabel.copy(color = MaterialTheme.colorScheme.onSurface)
```

(즉 `itemStyle`, `intersectionItemStyle`, `leftStroke`, `rightStroke` 4개 val 과 그 주석을 제거.)

Replace the `onDrawBehind` block (현재 lines ~93–103) with:

```kotlin
                    onDrawBehind {
                        drawCircle(color = leftColor, radius = r, center = leftCenter)
                        drawCircle(color = rightColor, radius = r, center = rightCenter)
                        clipPath(lens) { drawRect(color = intersectionColor) }
                        drawVennHeadwords(textMeasurer, venn, leftCenter, rightCenter, r, cy, labelStyle)
                    }
```

Replace `drawVennLabelsAndItems` + `drawItemColumn` (현재 lines ~108–176) with a headword-only helper (keep `clampedLeft` and `drawCenteredText` — `drawCenteredText` still uses `clampedLeft`):

```kotlin
/**
 * 헤드워드만 원 상단에 중앙 정렬로 그린다(웹 프로토 VennDiagram.jsx 정합 — 원 안엔 단어만). 뜻(items·교집합)은
 * 임의 길이의 서술 문구라 원 안에 넣으면 겹치므로, 상위 [ConceptualBridgeBlock]의 텍스트 레전드가 노출한다.
 */
@Suppress("LongParameterList")
private fun DrawScope.drawVennHeadwords(
    measurer: TextMeasurer,
    venn: VennData,
    leftCenter: Offset,
    rightCenter: Offset,
    r: Float,
    cy: Float,
    labelStyle: TextStyle,
) {
    // 원 상단(cy - r·0.5), 좌우 대칭으로 lobe 바깥쪽에 센터 앵커. 헤드워드가 길어도 캔버스 폭 안으로 clamp.
    drawCenteredText(measurer, venn.left.word, Offset(leftCenter.x - r * 0.30f, cy - r * 0.50f), labelStyle)
    drawCenteredText(measurer, venn.right.word, Offset(rightCenter.x + r * 0.30f, cy - r * 0.50f), labelStyle)
}

/** [desiredLeft]를 캔버스 폭 안으로 clamp 한다(긴 단어가 좌/우로 삐져나가 잘리는 것 방어). */
private fun DrawScope.clampedLeft(desiredLeft: Float, textWidth: Float): Float =
    desiredLeft.coerceIn(0f, (size.width - textWidth).coerceAtLeast(0f))

/** [center]를 텍스트의 시각 중앙으로 두고 그린다(측정 폭·높이 절반 보정, x 는 캔버스 폭 clamp). */
private fun DrawScope.drawCenteredText(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    style: TextStyle,
) {
    if (text.isEmpty()) return
    val layout = measurer.measure(text, style)
    val x = clampedLeft(center.x - layout.size.width / 2f, layout.size.width.toFloat())
    drawText(layout, topLeft = Offset(x, center.y - layout.size.height / 2f))
}
```

The KDoc on `VennDiagramCanvas` and `toVennContentDescription` stay unchanged (contentDescription still enriched). The `VennDiagramCanvasPreview` at the bottom stays as-is.

- [ ] **Step 6: 컴파일 + detekt + 레전드 테스트 재확인**

Run: `scripts/verify-android.sh :app:detekt :app:testDebugUnitTest --tests '*ConceptualBridgeLegendTest*' --tests '*VennContentDescriptionTest*'`
Expected: detekt PASS; `ConceptualBridgeLegendTest` PASS; `VennContentDescriptionTest` PASS(강화본 contentDescription 불변).

- [ ] **Step 7: 스크린샷 record + 육안(겹침 사라졌는지)**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionFlowScreenshotTest*' -Proborazzi.record`
Expected: PASS. `android/app/build/outputs/roborazzi/flow_deep_light.png`에서 원 안엔 헤드워드(order/get)만, 그 아래 색 점 + 단어 + 뜻 레전드가 겹침 없이 줄바꿈되어 나오는지 확인.

- [ ] **Step 8: 전체 검증 세트**

Run: `scripts/verify-android.sh`
Expected: detekt + compileDebugAndroidTestKotlin + testDebugUnitTest + testReleaseUnitTest 모두 BUILD SUCCESSFUL.

- [ ] **Step 9: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennDiagramCanvas.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackSections.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/ConceptualBridgeLegendTest.kt
git commit -m "$(cat <<'EOF'
fix(session): 개념 브리지 벤 뜻을 원 밖 레전드로 이전 — 긴 문구 겹침 해소

실데이터 items 는 원 안에 넣을 수 없는 긴 서술 문구라, 원 내부 렌더가 좌/우/교집합 텍스트를
겹쳐 그리던 문제가 있었다. VennDiagramCanvas 를 헤드워드-전용(프로토 최소형)으로 되돌리고,
좌/우 고유 뜻·교집합 뜻은 ConceptualBridgeBlock 아래 색 점 + 단어 + 뜻 레전드로 노출한다
(임의 길이에도 줄바꿈되어 겹침 불가). contentDescription·색가드는 불변.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: 딥 프롬프트의 venn items 길이 제약 강화 (Track B · 백엔드)

**Files:**
- Modify: `functions/src/providers/gemini.ts` (프롬프트 텍스트 ~lines 920–925, 버전 상수 line 901)

**Interfaces:**
- Consumes: 없음(문자열 상수 편집). `FEEDBACK_DEEP_SYSTEM_PROMPT`·`FEEDBACK_DEEP_PROMPT_VERSION`은 `src/llm/feedbackDeep.ts`가 import해 `system`/`cacheKey`로 사용(호출부 변경 없음).
- Produces: 동일 export 이름 유지, 값만 변경. 런타임 효력은 **배포 후**에만 발생(수동).

- [ ] **Step 1: venn `items` 지시를 길이 제약으로 강화**

In `functions/src/providers/gemini.ts`, the venn instruction inside `FEEDBACK_DEEP_SYSTEM_PROMPT` (현재 ~lines 922–925) reads:

```typescript
  "easy Korean. `venn`: compare the single most instructive vocabulary pair — `leftCircle.word` " +
  "(a word from the learner's sentence) vs `rightCircle.word` (the recommended word); `items` are " +
  "short Korean meaning notes; `intersection.items` are shared meanings; `guide` is a one-line " +
  "Korean hint. NO colors anywhere — words and items only.\n" +
```

Replace those four concatenated lines with:

```typescript
  "easy Korean. `venn`: compare the single most instructive vocabulary pair — `leftCircle.word` " +
  "(a word from the learner's sentence) vs `rightCircle.word` (the recommended word). `items` are " +
  "1-3 VERY SHORT Korean meaning notes: each MUST be a single word or a phrase of at most 4 words, " +
  "NEVER a full sentence or descriptive clause (good: \"주문하다\", \"격식 있는 표현\"; " +
  "bad: \"물건을 사고 받은 증명서를 건네줄 때 쓰는 표현\"). `intersection.items` are shared meanings under " +
  "the same length limit. `guide` is a one-line Korean hint. NO colors anywhere — words and items only.\n" +
```

- [ ] **Step 2: 프롬프트 버전 bump (cacheKey 무효화)**

In `functions/src/providers/gemini.ts` line 901, change:

```typescript
export const FEEDBACK_DEEP_PROMPT_VERSION = "2026-07-04";
```

to:

```typescript
export const FEEDBACK_DEEP_PROMPT_VERSION = "2026-07-10";
```

- [ ] **Step 3: 빌드·린트·테스트**

Run:
```bash
cd functions && npm ci && npm run build && npm run lint && npm test
```
Expected: `tsc` 컴파일 성공, ESLint 통과, Jest 전부 통과. (기존 deep 핸들러 테스트는 하드코딩된 짧은 venn 픽스처를 쓰므로 프롬프트 텍스트/버전 변경에 영향받지 않는다 — 문자열 상수 편집일 뿐 파싱/SSE 로직 불변.)

- [ ] **Step 4: 커밋 (배포는 수동 — 별도)**

```bash
cd "$(git rev-parse --show-toplevel)"
git add functions/src/providers/gemini.ts
git commit -m "$(cat <<'EOF'
fix(functions): 딥 venn items 길이 제약 강화(1-3개·≤4어절·문장 금지) + 프롬프트 버전 bump

LLM 이 "짧은 뜻 메모" 계약을 어기고 긴 서술 문구를 출력해 클라 벤 렌더가 깨지던 근본 원인을
프롬프트에서 명시 제약으로 막는다. 예시(good/bad) 포함. FEEDBACK_DEEP_PROMPT_VERSION 을 bump 해
프롬프트 캐시를 무효화한다. 효력은 배포 후 발생.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

> **배포(사용자 수동, 이 태스크 범위 밖):** `cd functions && npm run deploy`(= `firebase deploy --only functions`, 리전 asia-northeast3). 배포 전까지 런타임 프롬프트는 변경되지 않는다. Track A(클라 레전드)는 배포와 무관하게 긴 items에도 견고하므로 겹침은 배포 없이도 이미 해소된다.

---

## Self-Review

**1. Spec(결정 테이블) coverage:**
- #2 뜻→레전드 이전 → Task 1 Step 3/5.
- #3 레전드 소유=DeepFeedbackSections → Task 1 Step 3.
- #4 VennDiagramCanvas 헤드워드-전용(items·stroke 제거) → Task 1 Step 5.
- #5·#11 full 레전드(좌/우+공통) → `VennMeaningLegend` 3줄.
- #6 색가드 불변 → Global Constraints + Task 1 미변경.
- #7 contentDescription 강화본 유지 → Global Constraints + Step 6에서 `VennContentDescriptionTest` 재확인.
- #8·#9 헤드워드 clamp / 레전드 점 색(좌 블루·우 그린) → Step 5 / `VennLegendLine`.
- #10 스크린샷·테스트 → Step 7 + Step 1 legend 테스트.
- #12 백엔드 프롬프트 조임 + 버전 bump → Task 2. 배포 수동 명시.

**2. Placeholder scan:** 모든 코드 스텝에 실제 코드/명령. "TODO/적절히" 류 없음. Task 2는 문자열 상수 편집이라 완전.

**3. Type consistency:**
- `drawVennHeadwords(measurer, venn, leftCenter, rightCenter, r, cy, labelStyle)` — onDrawBehind 호출부 인자 순서 일치. `clampedLeft`/`drawCenteredText` 유지·호출 일치.
- `VennMeaningLegend(venn)` / `VennLegendLine(dotColor, word, meanings)` — 정의·호출 시그니처 일치.
- `DeepFeedbackState.Ready(conceptualBridge, toneStyle, paraphrasing)` + `ToneStyle`/`ToneLevel`/`Paraphrasing`/`Paraphrase` 생성자 — `DeepFeedbackState.kt` 정의와 일치(테스트 픽스처).
- Track B: `FEEDBACK_DEEP_SYSTEM_PROMPT`/`FEEDBACK_DEEP_PROMPT_VERSION` export 이름 불변, `feedbackDeep.ts` import 영향 없음.

이슈 없음 — 계획 확정.
