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

    @Test
    fun `side item exactly at width boundary fits, one px over does not`() {
        // 측면 첫 행은 sideStartY=74.4 에서 시작해 cy=88.8 을 가로지른다: bottom=74.4+18=92.4.
        // dyAbs = max(|74.4−88.8|, |92.4−88.8|) = max(14.4, 3.6) = 14.4 (상단이 더 멀다 → 상단으로 판정).
        // avail_side(dy=14.4) = 2·(√(72²−14.4²) − 28.8) = 2·(√4976.64 − 28.8) ≈ 2·(70.545 − 28.8) ≈ 83.49.
        // margin 8 을 빼면 avail ≈ 75.49. 이 폭과 같으면(strict > 이므로 동등은 fits) INSIDE, +0.1 이면 LEGEND.
        val g = geom()
        val dyAbs = maxOf(kotlin.math.abs(g.sideStartYPx - g.cyPx), kotlin.math.abs(g.sideStartYPx + 18f - g.cyPx))
        val boundaryWidth = availWidthSidePx(g, dyAbs) - g.marginPx

        val fitsMode =
            classifyVennLayout(
                left = listOf(ItemBox(boundaryWidth, 18f)),
                right = emptyList(),
                intersection = emptyList(),
                geom = g,
            )
        assertEquals(VennLayoutMode.INSIDE, fitsMode)

        val overflowMode =
            classifyVennLayout(
                left = listOf(ItemBox(boundaryWidth + 0.1f, 18f)),
                right = emptyList(),
                intersection = emptyList(),
                geom = g,
            )
        assertEquals(VennLayoutMode.LEGEND, overflowMode)
    }
}
