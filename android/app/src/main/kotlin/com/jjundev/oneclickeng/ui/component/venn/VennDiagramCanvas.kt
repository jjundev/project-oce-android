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
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.feature.session.feedback.VennCircle
import com.jjundev.oneclickeng.feature.session.feedback.VennData
import com.jjundev.oneclickeng.ui.theme.OceTheme

private val VennSize = 240.dp

// I4 geometry (03-signature-interactions.md:54): r = size·0.3, centers x = size/2 ∓ r·0.62,
// y = size·0.37, viewport height 0.75× → the canvas is 240dp wide × 180dp tall (dp fixed, A7).
private const val RADIUS_RATIO = 0.3f
private const val CENTER_OFFSET_RATIO = 0.62f
private const val CENTER_Y_RATIO = 0.37f
private const val VIEWPORT_HEIGHT_RATIO = 0.75f

/**
 * 벤다이어그램(I4) — Compose Canvas 재구현. 좌/우 원 + 교집합을 [VennColorGuard] 가 산출한 대비-보정 색으로
 * 그린다(모델은 색 미출력 — feedback-deep.md:8). 측면 alpha 128, 교집합 체감 alpha 180.
 *
 * **접근성(A2):** 색 단독 신호 금지 — 시각은 장식이며, 두 단어와 공통 의미를 [Modifier.semantics] 의
 * `contentDescription` 으로 텍스트 대안 노출한다(03-signature-interactions.md §I4 "텍스트 대안(필수)";
 * 웹 스펙의 figcaption/aria-label 을 Compose semantics 로 번역). 상위 블록이 guide·직역·설명을 별도 텍스트로 노출한다.
 */
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
    // 아이템(뜻 목록)은 보조 텍스트급. 색은 가드가 검증하는 sub 참조색(onSurfaceVariant)과 일치시켜
    // 측면 원 위 대비 ≥3.0 불변식(VennColorGuard.MIN_SUB_CONTRAST_SIDE)을 그대로 만족한다.
    val itemStyle =
        OceTheme.typography.helper.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    // 원 경계 stroke(정보 강화 시 뜻 목록과 원 배경 경계를 명료화 — 레거시 strokePaint 정합). 불투명 side 색.
    val leftStroke = Color(colors.left)
    val rightStroke = Color(colors.right)

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
                        drawCircle(color = leftStroke, radius = r, center = leftCenter, style = Stroke(1.dp.toPx()))
                        drawCircle(color = rightStroke, radius = r, center = rightCenter, style = Stroke(1.dp.toPx()))
                        drawVennLabelsAndItems(
                            textMeasurer, venn, leftCenter, rightCenter, r, cy, labelStyle, itemStyle,
                        )
                    }
                },
    )
}

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
    drawItemColumn(
        measurer, venn.left.items, Offset(leftCenter.x - r * 0.40f, cy - r * 0.20f), itemStyle, bullet = true,
    )
    drawItemColumn(
        measurer, venn.right.items, Offset(rightCenter.x + r * 0.40f, cy - r * 0.20f), itemStyle, bullet = true,
    )
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
        )
    }
}
