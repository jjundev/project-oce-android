# 적응형 벤 레이아웃 (INSIDE/LEGEND) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 개념 브리지 벤에서 아이템(뜻)이 원 안에 겹침 없이 들어가면 원 안에 그리고(INSIDE, 아래 레전드 없음), 넘치면 헤드워드만 원 안에 두고 아래 레전드로 표현하도록(LEGEND, 현행) 실제 렌더 크기를 측정해 다이어그램 단위로 분기한다.

**Architecture:** 순수 분류기 `classifyVennLayout`(폭·높이·기하만 받는 Kotlin, Compose 무관)가 좌/우 lobe와 교집합 lens를 동일 원-기하 공식으로 per-row 스택 판정한다. `ConceptualBridgeBlock`이 아이템을 실제 렌더 문자열/스타일로 측정해 모드를 1회 계산하고, 같은 모드를 `VennDiagramCanvas`(INSIDE=원 안 items 복원 / LEGEND=헤드워드만)와 레전드 렌더에 공유한다. 경계는 LEGEND 보수 편향 + 8dp margin으로 안전하다.

**Tech Stack:** Kotlin, Jetpack Compose(Canvas·TextMeasurer), JUnit4(+Robolectric/Roborazzi for compose tests), detekt.

## Global Constraints

- 검증은 **반드시** `scripts/verify-android.sh`로 실행한다(워크트리 gradle 오염·google-services.json 부재 우회). 직접 `./gradlew` 금지. 새 코드는 detekt(`buildUponDefaultConfig`) 통과.
- `VennColorGuard`는 **변경 금지**(대비 불변식). INSIDE 아이템 색은 가드 검증 참조색과 일치: 측면=`onSurfaceVariant`(sub), 교집합=`onSurface`(primary).
- `VennData.toVennContentDescription()`(강화본, 좌/우 items+교집합)은 **양 모드에서 불변** — INSIDE에서도 A2 텍스트 대안 유지.
- INSIDE 렌더 코드의 복원 원본은 커밋 `1f7e008`(dual-style `drawItemColumn` + `Stroke(1.dp.toPx())` + `gap=2.dp`; `7e30ac0`은 삭제 커밋). `git show 1f7e008 -- android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennDiagramCanvas.kt`로 대조.
- 기하 상수(`VennSize` + `RADIUS_RATIO`/`CENTER_OFFSET_RATIO`/`CENTER_Y_RATIO`/`VIEWPORT_HEIGHT_RATIO`)는 파일-private → `internal`로 확대(분류기 래퍼가 동일 기하 참조). budget은 이 상수에서 파생(하드코딩 금지).
- 측정 문자열/스타일 = 렌더와 동일: 측면 아이템 `"• $item"`, 교집합 `item`, 둘 다 `OceTheme.typography.helper`.
- Roborazzi는 커밋된 golden 없음(capture-only, diff 게이트 없음). 신규 `createComposeRule` 테스트는 [android/app/build.gradle.kts:66-70](android/app/build.gradle.kts:66) Release 제외 glob에 **반드시** 등록(안 하면 `testReleaseUnitTest`가 `ComponentActivity` 못 찾아 실패).
- 턴 간 레이아웃 가변(INSIDE↔LEGEND)은 사용자가 요청한 적응형 동작의 본질(의도됨).
- 커밋 메시지: 한국어 `feat(ui)`/`feat(session)`, 말미에 Co-Authored-By 라인.

---

## File Structure

- **Create** `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennLayoutClassifier.kt` — 순수(Compose 무관): `VennLayoutMode` enum, `ItemBox`, `VennGeom` data class, `internal` 폭 공식 `availWidthSidePx`/`availWidthLensPx`, 순수 `classifyVennLayout`. (Task 1)
- **Create** `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennLayoutClassifierTest.kt` — 순수 JVM 단위테스트. (Task 1)
- **Modify** `.../ui/component/venn/VennDiagramCanvas.kt` — 기하 상수 `internal`화, `mode: VennLayoutMode` 파라미터, `when(mode)`로 INSIDE(items+stroke 복원)/LEGEND(헤드워드만) 분기, `@Composable rememberVennLayoutMode(...)` 래퍼 추가. (Task 2)
- **Modify** `.../feature/session/feedback/DeepFeedbackSections.kt` — `ConceptualBridgeBlock`에서 mode 계산 → canvas 전달 + `if(LEGEND) VennMeaningLegend`. (Task 3)
- **Create** `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/ConceptualBridgeInsideModeTest.kt` — 짧은 아이템 → INSIDE(레전드 Text 노드 부재) 검증. (Task 3)
- **Modify** `android/app/build.gradle.kts` — 신규 INSIDE 테스트를 Release 제외에 등록. (Task 3)
- **Unchanged**: `VennColorGuard.kt`, `VennContentDescriptionTest.kt`, `ConceptualBridgeLegendTest.kt`(긴 픽스처 → 여전히 LEGEND), `VennMeaningLegend`/`VennLegendLine`(LEGEND 전용).

---

## Task 1: 순수 분류기 `classifyVennLayout` + 기하 타입

**Files:**
- Create: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennLayoutClassifier.kt`
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennLayoutClassifierTest.kt`

**Interfaces:**
- Consumes: 없음(순수 Kotlin + `kotlin.math`).
- Produces: `enum class VennLayoutMode { INSIDE, LEGEND }`; `data class ItemBox(val widthPx: Float, val heightPx: Float)`; `data class VennGeom(...)`(아래 필드); `internal fun availWidthSidePx(geom: VennGeom, dyAbs: Float): Float`; `internal fun availWidthLensPx(geom: VennGeom, dyAbs: Float): Float`; `fun classifyVennLayout(left: List<ItemBox>, right: List<ItemBox>, intersection: List<ItemBox>, geom: VennGeom): VennLayoutMode`.

- [ ] **Step 1: 실패 테스트 작성**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennLayoutClassifierTest.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.component.venn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 순수 분류기 검증(Compose 무관). 기하는 density=1·VennSize=240 기준 px:
 * r=72, d=1.24·72=89.28, cy=88.8, sideStartY=74.4, interStartY=96, sideAnchorOffset=28.8,
 * sideVerticalRoom=86.4, lensVerticalRoom=49.32, gap=2, margin=8.
 */
class VennLayoutClassifierTest {
    private fun geom() =
        VennGeom(
            rPx = 72f,
            dPx = 89.28f,
            cyPx = 88.8f,
            sideStartYPx = 74.4f,
            interStartYPx = 96f,
            sideAnchorOffsetPx = 28.8f,
            sideVerticalRoomPx = 86.4f,
            lensVerticalRoomPx = 49.32f,
            gapPx = 2f,
            marginPx = 8f,
        )

    @Test
    fun `side and lens available-width formulas match geometry at center`() {
        // side: 2·(√(r²−0) − 0.4r) = 2·(72−28.8) = 86.4
        assertEquals(86.4f, availWidthSidePx(geom(), 0f), 0.1f)
        // lens: 2·(√(r²−0) − d/2) = 2·(72−44.64) = 54.72
        assertEquals(54.72f, availWidthLensPx(geom(), 0f), 0.1f)
    }

    @Test
    fun `lens width strictly narrows as vertical offset grows`() {
        assertTrue(availWidthLensPx(geom(), 40f) < availWidthLensPx(geom(), 0f))
    }

    @Test
    fun `all-short items fit inside`() {
        val mode =
            classifyVennLayout(
                left = listOf(ItemBox(50f, 18f)),
                right = listOf(ItemBox(50f, 18f)),
                intersection = listOf(ItemBox(30f, 18f)),
                geom = geom(),
            )
        assertEquals(VennLayoutMode.INSIDE, mode)
    }

    @Test
    fun `one wide side item forces legend`() {
        val mode =
            classifyVennLayout(
                left = listOf(ItemBox(200f, 18f)),
                right = listOf(ItemBox(40f, 18f)),
                intersection = emptyList(),
                geom = geom(),
            )
        assertEquals(VennLayoutMode.LEGEND, mode)
    }

    @Test
    fun `more than three items in a lane forces legend`() {
        val four = List(4) { ItemBox(20f, 18f) }
        val mode = classifyVennLayout(four, emptyList(), emptyList(), geom())
        assertEquals(VennLayoutMode.LEGEND, mode)
    }

    @Test
    fun `second intersection row overflows the narrowing lens`() {
        // row1 bottom dy≈25.2 → avail≈37.6 ≥30 ✓; row2 bottom dy≈45.2 → avail≈14.8 <30 → LEGEND.
        val mode =
            classifyVennLayout(
                left = emptyList(),
                right = emptyList(),
                intersection = listOf(ItemBox(30f, 18f), ItemBox(30f, 18f)),
                geom = geom(),
            )
        assertEquals(VennLayoutMode.LEGEND, mode)
    }

    @Test
    fun `empty lanes classify inside`() {
        assertEquals(
            VennLayoutMode.INSIDE,
            classifyVennLayout(emptyList(), emptyList(), emptyList(), geom()),
        )
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*VennLayoutClassifierTest*'`
Expected: FAIL — `Unresolved reference: VennGeom / classifyVennLayout / availWidthSidePx …`(컴파일 에러).

- [ ] **Step 3: 순수 분류기 구현**

Create `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennLayoutClassifier.kt`:

```kotlin
package com.jjundev.oneclickeng.ui.component.venn

import kotlin.math.abs
import kotlin.math.sqrt

/** 벤 아이템(뜻)을 원 안에 그릴지(INSIDE) 아래 레전드로 뺄지(LEGEND) 결정하는 레이아웃 모드. */
enum class VennLayoutMode { INSIDE, LEGEND }

/** 한 아이템의 측정된 렌더 크기(px). 측정은 실제 렌더 문자열/스타일과 동일해야 한다("• $item" 등). */
data class ItemBox(val widthPx: Float, val heightPx: Float)

/**
 * 고정 벤 기하를 px로 담은 값. VennDiagramCanvas 의 기하 상수(VennSize·비율)에서 density 로 파생한다.
 * - [rPx] 원 반지름, [dPx] 두 원 중심 거리(2·r·CENTER_OFFSET_RATIO), [cyPx] 원 중심 y.
 * - 측면 아이템은 x 앵커 = 원중심 − [sideAnchorOffsetPx](=0.4r)에 중앙 정렬, [sideStartYPx](=cy−0.2r)부터 스택.
 * - 교집합 아이템은 중앙선에 중앙 정렬, [interStartYPx](=cy+0.1r)부터 스택.
 * - [sideVerticalRoomPx](=1.2r)·[lensVerticalRoomPx](=0.685r) 세로 여유, [gapPx] 줄 간격, [marginPx] 보수 여백.
 */
data class VennGeom(
    val rPx: Float,
    val dPx: Float,
    val cyPx: Float,
    val sideStartYPx: Float,
    val interStartYPx: Float,
    val sideAnchorOffsetPx: Float,
    val sideVerticalRoomPx: Float,
    val lensVerticalRoomPx: Float,
    val gapPx: Float,
    val marginPx: Float,
)

/** 한 레인에 담을 수 있는 최대 아이템 수(넘으면 LEGEND). */
private const val MAX_ITEMS_PER_LANE = 3

/**
 * 측면 lobe 에서 세로 오프셋 [dyAbs](=|행 하단 − cy|)일 때 x앵커(원중심−0.4r) 중앙정렬 아이템의 가용 폭.
 * 구속은 외곽 원 경계: 2·(√(r²−dy²) − 0.4r). dy≥r 이면 음수(=담을 수 없음).
 */
internal fun availWidthSidePx(
    geom: VennGeom,
    dyAbs: Float,
): Float {
    val inner = geom.rPx * geom.rPx - dyAbs * dyAbs
    if (inner <= 0f) return -1f
    return 2f * (sqrt(inner) - geom.sideAnchorOffsetPx)
}

/**
 * 교집합 lens 에서 세로 오프셋 [dyAbs] 일 때 중앙정렬 아이템의 가용 폭 = 2·(√(r²−dy²) − d/2).
 * lens 는 √(r²−dy²) > d/2 인 구간에만 존재하므로 벗어나면 음수.
 */
internal fun availWidthLensPx(
    geom: VennGeom,
    dyAbs: Float,
): Float {
    val inner = geom.rPx * geom.rPx - dyAbs * dyAbs
    if (inner <= 0f) return -1f
    return 2f * (sqrt(inner) - geom.dPx / 2f)
}

/** 한 레인이 세로 스택으로 전부 들어가는지. 각 아이템은 하단 edge(cy에서 가장 먼 지점 → 가장 좁음)로 판정. */
private fun laneFits(
    items: List<ItemBox>,
    startYPx: Float,
    verticalRoomPx: Float,
    geom: VennGeom,
    availWidthAt: (dyAbs: Float) -> Float,
): Boolean {
    if (items.size > MAX_ITEMS_PER_LANE) return false
    var runningY = startYPx
    for (item in items) {
        val bottom = runningY + item.heightPx
        val dyAbs = abs(bottom - geom.cyPx)
        val avail = availWidthAt(dyAbs) - geom.marginPx
        if (item.widthPx > avail) return false
        if (bottom > startYPx + verticalRoomPx) return false
        runningY = bottom + geom.gapPx
    }
    return true
}

/**
 * 세 레인(좌/우 lobe, 교집합 lens)이 모두 겹침 없이 원 안에 들어가면 [VennLayoutMode.INSIDE], 하나라도
 * 넘치면 [VennLayoutMode.LEGEND](보수 편향). 측정은 실제 렌더 문자열/스타일과 동일한 [ItemBox] 여야 한다.
 */
fun classifyVennLayout(
    left: List<ItemBox>,
    right: List<ItemBox>,
    intersection: List<ItemBox>,
    geom: VennGeom,
): VennLayoutMode {
    val fits =
        laneFits(left, geom.sideStartYPx, geom.sideVerticalRoomPx, geom) { availWidthSidePx(geom, it) } &&
            laneFits(right, geom.sideStartYPx, geom.sideVerticalRoomPx, geom) { availWidthSidePx(geom, it) } &&
            laneFits(intersection, geom.interStartYPx, geom.lensVerticalRoomPx, geom) { availWidthLensPx(geom, it) }
    return if (fits) VennLayoutMode.INSIDE else VennLayoutMode.LEGEND
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:detekt :app:testDebugUnitTest --tests '*VennLayoutClassifierTest*'`
Expected: detekt PASS; 7 tests PASS.

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennLayoutClassifier.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennLayoutClassifierTest.kt
git commit -m "$(cat <<'EOF'
feat(ui): 벤 레이아웃 순수 분류기 classifyVennLayout 추가

좌/우 lobe·교집합 lens 를 동일 원-기하 공식으로 per-row 스택 판정해 아이템이 원 안에 겹침 없이
들어가면 INSIDE, 넘치면 LEGEND 를 반환한다. 하단 edge(가장 좁은 지점)·8dp margin 으로 보수 편향.
순수 Kotlin(Compose 무관) 이라 JVM 단위테스트로 기하 공식·경계를 검증한다.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: `VennDiagramCanvas` — 기하 노출 + mode 분기 + INSIDE 복원 + 측정 래퍼

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennDiagramCanvas.kt`

**Interfaces:**
- Consumes: `VennLayoutMode`, `ItemBox`, `VennGeom`, `classifyVennLayout` (Task 1). `VennColorGuard.resolve()`. `OceTheme.typography.helper/sectionLabel`, `MaterialTheme.colorScheme.{onSurface,onSurfaceVariant}`. `TextMeasurer`, `Density`.
- Produces: `@Composable fun VennDiagramCanvas(venn: VennData, mode: VennLayoutMode, modifier: Modifier = Modifier)` (시그니처 변경 — `mode` 추가). `@Composable fun rememberVennLayoutMode(venn: VennData, textMeasurer: TextMeasurer, density: Density): VennLayoutMode`. 기하 상수 `internal`. `toVennContentDescription()` 불변.

- [ ] **Step 1: 기하 상수 `internal` 확대**

In `VennDiagramCanvas.kt`, change the visibility of the size + ratio constants (현재 line 29 및 33-36; 정확 위치는 내용으로 매칭):

```kotlin
private val VennSize = 240.dp
```
→
```kotlin
internal val VennSize = 240.dp
```

and
```kotlin
private const val RADIUS_RATIO = 0.3f
private const val CENTER_OFFSET_RATIO = 0.62f
private const val CENTER_Y_RATIO = 0.37f
private const val VIEWPORT_HEIGHT_RATIO = 0.75f
```
→
```kotlin
internal const val RADIUS_RATIO = 0.3f
internal const val CENTER_OFFSET_RATIO = 0.62f
internal const val CENTER_Y_RATIO = 0.37f
internal const val VIEWPORT_HEIGHT_RATIO = 0.75f
```

- [ ] **Step 2: `mode` 파라미터 + INSIDE 렌더 복원**

Add imports (near existing venn imports):

```kotlin
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
```
(`Stroke` 는 이전 삭제로 없어졌으면 다시 추가. `padding` 은 이미 있으면 생략.)

Replace the `VennDiagramCanvas` composable signature + body (현재 46-88; 아래 "before" 블록으로 매칭) so it takes `mode` and branches drawing. Replace:

```kotlin
@Composable
fun VennDiagramCanvas(
    venn: VennData,
    modifier: Modifier = Modifier,
) {
    val colors = remember { VennColorGuard.resolve() }
    val leftColor = Color(colors.left).copy(alpha = VennColorGuard.SIDE_ALPHA / 255f)
    val rightColor = Color(colors.right).copy(alpha = VennColorGuard.SIDE_ALPHA / 255f)
    val intersectionColor =
        Color(colors.intersection).copy(alpha = VennColorGuard.INTERSECTION_ALPHA / 255f)

    val textMeasurer = rememberTextMeasurer()
    val labelStyle =
        OceTheme.typography.sectionLabel.copy(color = MaterialTheme.colorScheme.onSurface)

    val description = venn.toVennContentDescription()

    // drawWithCache: 크기 종속 기하·교집합 lens Path 를 크기 변경 시에만 재계산(리컴포지션 캐시, SoT §I4).
    Spacer(
        modifier =
            modifier
                .size(width = VennSize, height = VennSize * VIEWPORT_HEIGHT_RATIO)
                .semantics { contentDescription = description }
                .drawWithCache {
                    val r = size.width * RADIUS_RATIO
                    val cy = size.width * CENTER_Y_RATIO
                    val leftCenter = Offset(size.width / 2f - r * CENTER_OFFSET_RATIO, cy)
                    val rightCenter = Offset(size.width / 2f + r * CENTER_OFFSET_RATIO, cy)
                    val lens =
                        Path().apply {
                            val left = Path().apply { addOval(Rect(leftCenter, r)) }
                            val right = Path().apply { addOval(Rect(rightCenter, r)) }
                            op(left, right, PathOperation.Intersect)
                        }
                    onDrawBehind {
                        drawCircle(color = leftColor, radius = r, center = leftCenter)
                        drawCircle(color = rightColor, radius = r, center = rightCenter)
                        clipPath(lens) { drawRect(color = intersectionColor) }
                        drawVennHeadwords(textMeasurer, venn, leftCenter, rightCenter, r, cy, labelStyle)
                    }
                },
    )
}
```

with:

```kotlin
@Composable
fun VennDiagramCanvas(
    venn: VennData,
    mode: VennLayoutMode,
    modifier: Modifier = Modifier,
) {
    val colors = remember { VennColorGuard.resolve() }
    val leftColor = Color(colors.left).copy(alpha = VennColorGuard.SIDE_ALPHA / 255f)
    val rightColor = Color(colors.right).copy(alpha = VennColorGuard.SIDE_ALPHA / 255f)
    val intersectionColor =
        Color(colors.intersection).copy(alpha = VennColorGuard.INTERSECTION_ALPHA / 255f)
    val leftStroke = Color(colors.left)
    val rightStroke = Color(colors.right)

    val textMeasurer = rememberTextMeasurer()
    val labelStyle =
        OceTheme.typography.sectionLabel.copy(color = MaterialTheme.colorScheme.onSurface)
    // INSIDE 모드 아이템 스타일 — 측면=sub(onSurfaceVariant), 교집합=primary(onSurface). 가드 검증 참조색 정합.
    val itemStyle = OceTheme.typography.helper.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val intersectionItemStyle = OceTheme.typography.helper.copy(color = MaterialTheme.colorScheme.onSurface)

    val description = venn.toVennContentDescription()

    // drawWithCache: 크기 종속 기하·교집합 lens Path 를 크기 변경 시에만 재계산(리컴포지션 캐시, SoT §I4).
    Spacer(
        modifier =
            modifier
                .size(width = VennSize, height = VennSize * VIEWPORT_HEIGHT_RATIO)
                .semantics { contentDescription = description }
                .drawWithCache {
                    val r = size.width * RADIUS_RATIO
                    val cy = size.width * CENTER_Y_RATIO
                    val leftCenter = Offset(size.width / 2f - r * CENTER_OFFSET_RATIO, cy)
                    val rightCenter = Offset(size.width / 2f + r * CENTER_OFFSET_RATIO, cy)
                    val lens =
                        Path().apply {
                            val left = Path().apply { addOval(Rect(leftCenter, r)) }
                            val right = Path().apply { addOval(Rect(rightCenter, r)) }
                            op(left, right, PathOperation.Intersect)
                        }
                    onDrawBehind {
                        drawCircle(color = leftColor, radius = r, center = leftCenter)
                        drawCircle(color = rightColor, radius = r, center = rightCenter)
                        clipPath(lens) { drawRect(color = intersectionColor) }
                        drawVennHeadwords(textMeasurer, venn, leftCenter, rightCenter, r, cy, labelStyle)
                        // INSIDE: 좌/우 고유 뜻·교집합 뜻을 원 안에 렌더(원 경계 stroke 로 구분). LEGEND: 헤드워드만.
                        if (mode == VennLayoutMode.INSIDE) {
                            drawCircle(color = leftStroke, radius = r, center = leftCenter, style = Stroke(1.dp.toPx()))
                            drawCircle(color = rightStroke, radius = r, center = rightCenter, style = Stroke(1.dp.toPx()))
                            drawItemColumn(
                                textMeasurer, venn.left.items,
                                Offset(leftCenter.x - r * 0.40f, cy - r * 0.20f), itemStyle, bullet = true,
                            )
                            drawItemColumn(
                                textMeasurer, venn.right.items,
                                Offset(rightCenter.x + r * 0.40f, cy - r * 0.20f), itemStyle, bullet = true,
                            )
                            val mid = (leftCenter.x + rightCenter.x) / 2f
                            drawItemColumn(
                                textMeasurer, venn.intersectionItems,
                                Offset(mid, cy + r * 0.10f), intersectionItemStyle, bullet = false,
                            )
                        }
                    }
                },
    )
}
```

- [ ] **Step 3: `drawItemColumn` 복원 + `rememberVennLayoutMode` 래퍼 추가**

`drawVennHeadwords`/`clampedLeft`/`drawCenteredText`/`toVennContentDescription` 은 그대로 둔다. `drawCenteredText` 아래에 `drawItemColumn`(커밋 `1f7e008` 정합)과 측정 래퍼를 추가:

```kotlin
/** [top].x 를 중앙으로 아이템을 세로 누적한다(각 줄 측정 높이 + 2dp gap). INSIDE 모드에서만 호출. */
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
        val x = clampedLeft(top.x - layout.size.width / 2f, layout.size.width.toFloat())
        drawText(layout, topLeft = Offset(x, y))
        y += layout.size.height + gap
    }
}

/**
 * 아이템(뜻)을 원 안에 겹침 없이 그릴 수 있는지 측정해 [VennLayoutMode] 를 산출한다. 측정 문자열/스타일은
 * [drawItemColumn] 이 실제 그리는 것과 동일해야 한다: 측면 "• $item", 교집합 item, 둘 다 helper 스타일.
 * [VennGeom] 은 고정 기하 상수(VennSize·비율)에서 density 로 파생한다(budget = 기하 파생, 하드코딩 아님).
 */
@Composable
fun rememberVennLayoutMode(
    venn: VennData,
    textMeasurer: TextMeasurer,
    density: Density,
): VennLayoutMode {
    val itemStyle = OceTheme.typography.helper
    return remember(venn, density) {
        with(density) {
            val canvasW = VennSize.toPx()
            val r = canvasW * RADIUS_RATIO
            val geom =
                VennGeom(
                    rPx = r,
                    dPx = 2f * r * CENTER_OFFSET_RATIO,
                    cyPx = canvasW * CENTER_Y_RATIO,
                    sideStartYPx = canvasW * CENTER_Y_RATIO - r * 0.20f,
                    interStartYPx = canvasW * CENTER_Y_RATIO + r * 0.10f,
                    sideAnchorOffsetPx = r * 0.40f,
                    sideVerticalRoomPx = r * 1.2f,
                    lensVerticalRoomPx = r * 0.685f,
                    gapPx = 2.dp.toPx(),
                    marginPx = 8.dp.toPx(),
                )
            fun boxes(
                items: List<String>,
                bullet: Boolean,
            ): List<ItemBox> =
                items.map {
                    val layout = textMeasurer.measure(if (bullet) "• $it" else it, itemStyle)
                    ItemBox(layout.size.width.toFloat(), layout.size.height.toFloat())
                }
            classifyVennLayout(
                left = boxes(venn.left.items, bullet = true),
                right = boxes(venn.right.items, bullet = true),
                intersection = boxes(venn.intersectionItems, bullet = false),
                geom = geom,
            )
        }
    }
}
```

Update the `VennDiagramCanvasPreview` at the bottom of the file to pass a mode (short items → INSIDE):

```kotlin
        VennDiagramCanvas(
            venn =
                VennData(
                    guide = "두 단어의 의미 차이를 볼까요?",
                    left = VennCircle(word = "get", items = listOf("얻다", "받다")),
                    right = VennCircle(word = "order", items = listOf("주문하다")),
                    intersectionItems = listOf("받다"),
                ),
            mode = VennLayoutMode.INSIDE,
        )
```

**시그니처 변경 주의 — Task 2·3은 한 묶음으로 컴파일된다.** `VennDiagramCanvas`의 유일한 프로덕션 호출부는 `DeepFeedbackSections.kt`다(`grep -rn "VennDiagramCanvas(" android/app/src`로 확인 — 프리뷰 제외 1곳). `mode` 파라미터를 추가하면 그 호출부를 Task 3에서 갱신하기 전까지 `compileDebugKotlin`이 인자 불일치로 **실패한다**. 이는 **정상**이며 쫓을 회귀가 아니다. 따라서 Task 2는 단독 컴파일·커밋하지 않고, Task 3까지 마친 뒤 함께 검증·커밋한다(커밋은 Task 3 Step 8이 두 파일을 함께 담는다).

- [ ] **Step 4: 작성 확인(단독 컴파일 없음)**

이 태스크의 산출물은 `VennDiagramCanvas.kt`의 편집 완료다. 단독 `compileDebugKotlin`은 호출부 미갱신으로 실패가 예상되므로 여기서 돌리지 않는다. 대신 편집이 self-consistent한지 육안 확인한다: (a) `mode: VennLayoutMode` 파라미터 추가, (b) `when(mode)`/`if (mode == VennLayoutMode.INSIDE)` 분기, (c) `drawItemColumn`·`rememberVennLayoutMode` 추가, (d) `import` 3종 추가(`Stroke`·`Density` 등), (e) `VennDiagramCanvasPreview`가 `mode = VennLayoutMode.INSIDE`를 넘김. 전체 컴파일·detekt·테스트는 Task 3 Step 4·7에서 호출부와 함께 통과한다.

---

## Task 3: `ConceptualBridgeBlock` 배선 + INSIDE 테스트 + Release 제외 등록

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackSections.kt`
- Create (test): `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/ConceptualBridgeInsideModeTest.kt`
- Modify: `android/app/build.gradle.kts`

**Interfaces:**
- Consumes: `VennDiagramCanvas(venn, mode, modifier)`, `rememberVennLayoutMode(venn, textMeasurer, density)`, `VennLayoutMode` (Task 2). `rememberTextMeasurer()`, `LocalDensity.current`. `DeepFeedbackRegion`, `DeepFeedbackState.Ready`, `ConceptualBridge`, `VennData`, `VennCircle`, `ToneStyle`, `ToneLevel`, `Paraphrasing`, `Paraphrase`.
- Produces: `ConceptualBridgeBlock` now computes mode and conditionally renders `VennMeaningLegend` (LEGEND only). No new public API.

- [ ] **Step 1: INSIDE 실패 테스트 작성**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/ConceptualBridgeInsideModeTest.kt`:

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
 * 짧은 뜻은 원 안(INSIDE)에 그려지고 아래 레전드는 렌더되지 않아야 한다. INSIDE 아이템은 canvas drawText
 * (비-semantics)라 Text 노드가 아니다 → 짧은 뜻이 Text 노드로 존재하지 않으면 레전드 미표시(INSIDE) 확정.
 * (긴 뜻 → LEGEND 는 ConceptualBridgeLegendTest 가 커버.)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel5, application = Application::class)
class ConceptualBridgeInsideModeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun ready(): DeepFeedbackState.Ready =
        DeepFeedbackState.Ready(
            conceptualBridge =
                ConceptualBridge(
                    literalTranslation = "커피 하나요.",
                    explanation = "조금 더 공손하게 표현할 수 있어요.",
                    venn =
                        VennData(
                            guide = "두 단어의 차이를 볼까요?",
                            left = VennCircle(word = "get", items = listOf("얻다")),
                            right = VennCircle(word = "order", items = listOf("주문")),
                            intersectionItems = listOf("받다"),
                        ),
                ),
            toneStyle =
                ToneStyle(
                    defaultLevel = 0,
                    levels = listOf(ToneLevel(0, "Can I get a coffee?", "커피 한 잔 주세요.")),
                ),
            paraphrasing =
                Paraphrasing(items = listOf(Paraphrase(1, "Beginner", "A coffee, please.", "커피 한 잔이요."))),
        )

    @Test
    fun short_meanings_render_inside_not_as_legend_text() {
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
        // 짧은 뜻은 canvas(INSIDE)로만 그려지므로 legend Text 노드로 존재하지 않는다.
        composeRule.onNodeWithText("얻다", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("주문", substring = true).assertDoesNotExist()
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*ConceptualBridgeInsideModeTest*'`
Expected: FAIL — 실행 순서상 Task 2가 이미 `VennDiagramCanvas` 시그니처를 바꿔 뒀으므로 **1차로는 컴파일 에러**(`DeepFeedbackSections.kt` 호출부가 `mode` 인자 없음)가 난다. 이는 정상이며, 다음 Step 3에서 호출부를 갱신하면 해소된다. (Step 3 적용 후 이 테스트를 다시 돌리면, 그때는 어서션 실패로 바뀐다 — 현재 `ConceptualBridgeBlock`이 무조건 `VennMeaningLegend`를 렌더해 "얻다"/"주문"이 Text 노드로 존재 → `assertDoesNotExist` 실패. 그 어서션 실패가 진짜 RED 이고, Step 3의 `if (mode == LEGEND)` 분기가 GREEN 으로 만든다.)

- [ ] **Step 3: `ConceptualBridgeBlock`에서 mode 계산 + 레전드 분기**

In `DeepFeedbackSections.kt`, add imports:

```kotlin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import com.jjundev.oneclickeng.ui.component.venn.VennLayoutMode
import com.jjundev.oneclickeng.ui.component.venn.rememberVennLayoutMode
```

Replace the `ConceptualBridgeBlock` composable (현재 192-206, 현행 = 무조건 canvas + VennMeaningLegend; "before" 블록으로 매칭) with:

```kotlin
/** ④ 개념 브릿지 — 간극 설명 + 벤. 짧은 뜻이면 원 안(INSIDE)에, 넘치면 헤드워드만 + 아래 레전드(LEGEND). */
@Composable
private fun ConceptualBridgeBlock(value: ConceptualBridge) {
    val emphasis = MaterialTheme.colorScheme.onSurface
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val mode = rememberVennLayoutMode(value.venn, measurer, density)
    Column(verticalArrangement = Arrangement.spacedBy(OceTheme.spacing.sm)) {
        DeepSectionHeader(icon = OceIcon.Hub, label = "개념 브리지")
        Text(
            text = emphasizeWords(value.explanation, listOf(value.venn.left.word, value.venn.right.word), emphasis),
            style = OceTheme.typography.helper.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VennDiagramCanvas(
            venn = value.venn,
            mode = mode,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        // INSIDE 면 뜻이 원 안에 있으므로 레전드 생략. LEGEND 면 원 밖 레전드로 노출(겹침 방지).
        if (mode == VennLayoutMode.LEGEND) {
            VennMeaningLegend(value.venn)
        }
    }
}
```

(`VennMeaningLegend`/`VennLegendLine` 은 그대로 둔다.)

- [ ] **Step 4: 테스트 통과 확인**

Run: `scripts/verify-android.sh :app:detekt :app:testDebugUnitTest --tests '*ConceptualBridgeInsideModeTest*' --tests '*ConceptualBridgeLegendTest*'`
Expected: detekt PASS; `ConceptualBridgeInsideModeTest`(짧음→INSIDE, 레전드 없음) PASS; `ConceptualBridgeLegendTest`(긴→LEGEND, 레전드 존재) PASS.

- [ ] **Step 5: 신규 INSIDE 테스트를 Release 제외에 등록**

In `android/app/build.gradle.kts`, the release-variant exclusion (현재 lines ~66-70) currently reads:

```kotlin
    if (name.contains("Release", ignoreCase = true)) {
        exclude("**/*ScreenshotTest*", "**/SlimFeedbackSheetTest*", "**/ConceptualBridgeLegendTest*")
    }
```

Add the new test class to the glob list:

```kotlin
    if (name.contains("Release", ignoreCase = true)) {
        exclude(
            "**/*ScreenshotTest*",
            "**/SlimFeedbackSheetTest*",
            "**/ConceptualBridgeLegendTest*",
            "**/ConceptualBridgeInsideModeTest*",
        )
    }
```

(정확한 현재 glob 목록은 파일에서 확인 후, `ConceptualBridgeInsideModeTest` 항목만 추가한다. 컨벤션 주석 "새 createComposeRule 테스트를 추가하면 여기에도 등록할 것" 준수.)

- [ ] **Step 6: 스크린샷 record + 육안(INSIDE 원 안 렌더)**

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*SessionFlowScreenshotTest*' -Proborazzi.record`
Expected: PASS. `android/app/build/outputs/roborazzi/flow_deep_light.png`에서 짧은 뜻 픽스처(order/get, 격식 있는 주문/구어체 등)가 **원 안에** 렌더되고 아래 레전드가 없는지 확인(길이에 따라 INSIDE/LEGEND가 갈릴 수 있음 — 겹치지만 않으면 통과).

- [ ] **Step 7: 전체 검증 세트**

Run: `scripts/verify-android.sh`
Expected: detekt + compileDebugAndroidTestKotlin + testDebugUnitTest + testReleaseUnitTest 모두 BUILD SUCCESSFUL. (Release 제외 등록으로 신규 compose 테스트가 release 변이에서 스킵됨.)

- [ ] **Step 8: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/feedback/DeepFeedbackSections.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/feedback/ConceptualBridgeInsideModeTest.kt \
        android/app/build.gradle.kts \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/venn/VennDiagramCanvas.kt
git commit -m "$(cat <<'EOF'
feat(session): 개념 브리지 벤을 적응형(INSIDE/LEGEND)으로 배선

ConceptualBridgeBlock 이 rememberVennLayoutMode 로 모드를 1회 계산해 canvas 와 레전드에 공유한다.
짧은 뜻이면 원 안(INSIDE)에 그리고 레전드 생략, 넘치면 헤드워드만 + 아래 레전드(LEGEND). 신규
createComposeRule 테스트를 Release 제외에 등록. 긴 픽스처의 ConceptualBridgeLegendTest 는 불변.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
EOF
)"
```

(Task 2의 canvas 변경을 단독 커밋하지 않았다면 여기서 함께 커밋된다.)

---

## Self-Review

**1. Spec coverage (확정 설계 15 결정):**
- #1 다이어그램 단위 all-or-nothing → `classifyVennLayout`(Task 1).
- #2 INSIDE=원 안 items+교집합, 레전드 없음 → Task 2 Step 2 + Task 3 Step 3(`if LEGEND`).
- #3 LEGEND=헤드워드+레전드 → Task 3 Step 3.
- #4 모드 1회 계산·공유 → Task 3 Step 3(mode → canvas + 레전드).
- #5 `VennDiagramCanvas(venn, mode, modifier)` + enum → Task 2.
- #6 측면 sub/교집합 primary → Task 2 Step 2(itemStyle/intersectionItemStyle).
- #7 contentDescription 불변 → Task 2(미변경) + `VennContentDescriptionTest` 불변.
- #8 복원 소스 `1f7e008` → Global Constraints + Task 2.
- #9/#9b 통합 per-lane 스택 + 세로room + 하단edge dy → Task 1 `laneFits`.
- #10 테스트(분류 단위 + 기존 레전드 + INSIDE) → Task 1 Step 1, Task 3 Step 1.
- #11 측정폭 vs 기하 파생 budget, strict `>` → `laneFits`(`item.widthPx > avail`).
- #12/#12b LEGEND 보수 + 8dp margin(양 레인) → `VennGeom.marginPx` + `laneFits`.
- #13 측정 문자열/스타일 정합 → Task 2 `rememberVennLayoutMode.boxes`.
- #14 `VennSize`+비율 `internal` → Task 2 Step 1.
- #15 신규 테스트 Release 제외 → Task 3 Step 5.

**2. Placeholder scan:** 모든 코드 스텝에 실제 코드/명령. Task 2 Step 4는 시그니처 맞물림을 명시(플레이스홀더 아님, 실제 컴파일 순서 안내).

**3. Type consistency:**
- `VennLayoutMode`/`ItemBox`/`VennGeom`/`classifyVennLayout`/`availWidthSidePx`/`availWidthLensPx` — Task 1 정의, Task 2 소비, 테스트 동일 명칭·필드.
- `VennGeom` 필드명(`rPx,dPx,cyPx,sideStartYPx,interStartYPx,sideAnchorOffsetPx,sideVerticalRoomPx,lensVerticalRoomPx,gapPx,marginPx`) — Task 1 정의 = Task 2 `rememberVennLayoutMode` 생성 = 테스트 생성 일치.
- `VennDiagramCanvas(venn, mode, modifier)` — Task 2 정의 = Task 3 호출 = Preview 호출 일치.
- `rememberVennLayoutMode(venn, textMeasurer, density)` — Task 2 정의 = Task 3 호출 일치.

이슈 없음 — 계획 확정.
