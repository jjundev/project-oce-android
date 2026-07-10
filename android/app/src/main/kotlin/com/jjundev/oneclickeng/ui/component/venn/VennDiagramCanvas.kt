package com.jjundev.oneclickeng.ui.component.venn

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.feature.session.feedback.VennCircle
import com.jjundev.oneclickeng.feature.session.feedback.VennData
import com.jjundev.oneclickeng.ui.theme.OceTheme

internal val VennSize = 240.dp

// I4 geometry (03-signature-interactions.md:54): r = size·0.3, centers x = size/2 ∓ r·0.62,
// y = size·0.37, viewport height 0.75× → the canvas is 240dp wide × 180dp tall (dp fixed, A7).
internal const val RADIUS_RATIO = 0.3f
internal const val CENTER_OFFSET_RATIO = 0.62f
internal const val CENTER_Y_RATIO = 0.37f
internal const val VIEWPORT_HEIGHT_RATIO = 0.75f

/**
 * 벤다이어그램(I4) — Compose Canvas 재구현. 좌/우 원 + 교집합을 [VennColorGuard] 가 산출한 대비-보정 색으로
 * 그린다(모델은 색 미출력 — feedback-deep.md:8). 측면 alpha 128, 교집합 체감 alpha 180.
 *
 * **접근성(A2):** 색 단독 신호 금지 — 시각은 장식이며, 두 단어와 공통 의미를 [Modifier.semantics] 의
 * `contentDescription` 으로 텍스트 대안 노출한다(03-signature-interactions.md §I4 "텍스트 대안(필수)";
 * 웹 스펙의 figcaption/aria-label 을 Compose semantics 로 번역). 상위 블록이 guide·직역·설명을 별도 텍스트로 노출한다.
 *
 * @param mode [VennLayoutMode.INSIDE] 면 좌/우 뜻·교집합 items 를 원 안에 함께 그린다. [VennLayoutMode.LEGEND]
 * 면 헤드워드만 그리고, 뜻은 호출부가 별도 레전드(예: `VennMeaningLegend`)로 노출해야 한다. 호출부는
 * [rememberVennLayoutMode] 로 아이템이 원 안에서 겹치는지 측정해 이 값을 산출한다.
 */
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
                            val stroke = Stroke(1.dp.toPx())
                            drawCircle(color = leftStroke, radius = r, center = leftCenter, style = stroke)
                            drawCircle(color = rightStroke, radius = r, center = rightCenter, style = stroke)
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

/**
 * 두 헤드워드만 원 상단에 중앙 정렬로 그린다(웹 프로토 VennDiagram.jsx 정합 — 원 안엔 단어만, [VennLayoutMode]
 * 와 무관하게 항상 호출). 뜻(items·교집합)은 이 함수가 그리지 않는다 — [VennLayoutMode.INSIDE] 면
 * [VennDiagramCanvas] 가 이어서 [drawItemColumn] 으로 원 안에 그리고, [VennLayoutMode.LEGEND] 면(아이템이
 * 원 안에서 겹칠 만큼 길 때 [rememberVennLayoutMode] 가 선택) 상위 호출부의 텍스트 레전드로 옮겨 노출한다.
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

/**
 * 색 단독 신호 금지(A2)의 텍스트 대안. 정보 강화(결정 #18)로 각 단어의 고유 뜻(items)까지 노출해
 * 다이어그램(헤드워드)과 그 아래 뜻 레전드(좌/우 items + 교집합)를 아우르는 완전한 텍스트 대안이 되게
 * 한다. items 가 비면 괄호를 생략한다.
 */
internal fun VennData.toVennContentDescription(): String {
    fun withItems(circle: VennCircle) =
        if (circle.items.isEmpty()) circle.word else "${circle.word}(${circle.items.joinToString(", ")})"
    val shared = intersectionItems.joinToString(", ")
    return "${withItems(left)}와 ${withItems(right)}의 공통 의미: $shared"
}

@Suppress("UnusedPrivateMember")
@Preview(showBackground = true, widthDp = 280)
@Composable
private fun VennDiagramCanvasPreview() {
    OceTheme {
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
    }
}
