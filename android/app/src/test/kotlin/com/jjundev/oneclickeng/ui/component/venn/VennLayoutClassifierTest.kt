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
