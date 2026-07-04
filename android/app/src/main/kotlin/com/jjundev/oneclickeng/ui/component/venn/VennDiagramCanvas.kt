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
    val itemStyle =
        OceTheme.typography.helper.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

    val description = venn.toContentDescription()

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
                        drawVennText(
                            textMeasurer, venn, leftCenter, rightCenter, r, cy, labelStyle, itemStyle,
                        )
                    }
                },
    )
}

/** Draw the two circle words (off-center) and the shared-meaning items in the lens. */
@Suppress("LongParameterList")
private fun DrawScope.drawVennText(
    measurer: TextMeasurer,
    venn: VennData,
    leftCenter: Offset,
    rightCenter: Offset,
    r: Float,
    cy: Float,
    labelStyle: TextStyle,
    itemStyle: TextStyle,
) {
    drawText(measurer, venn.left.word, topLeft = Offset(leftCenter.x - r * 0.7f, cy - r * 0.55f), style = labelStyle)
    drawText(measurer, venn.right.word, topLeft = Offset(rightCenter.x - r * 0.1f, cy - r * 0.55f), style = labelStyle)
    val shared = venn.intersectionItems.joinToString("\n")
    if (shared.isNotBlank()) {
        val mid = (leftCenter.x + rightCenter.x) / 2f
        drawText(measurer, shared, topLeft = Offset(mid - r * 0.35f, cy + r * 0.1f), style = itemStyle)
    }
}

/** 색 단독 신호 금지(A2): "<left>와 <right>의 공통 의미: <intersection items>" 텍스트 대안. */
private fun VennData.toContentDescription(): String {
    val shared = intersectionItems.joinToString(", ")
    return "${left.word}와 ${right.word}의 공통 의미: $shared"
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
