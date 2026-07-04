package com.jjundev.oneclickeng.ui.component.venn

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * NFR-8 대비 가드 검증 — [VennColorGuard] 가 산출한 팔레트가 라이트/다크 양쪽에서 WCAG 대비 불변식을 만족함을
 * **독립 계산**으로 확인한다(가드 내부 헬퍼가 아니라 테스트가 자체 WCAG 수식으로 재검증 → 순환 검증 회피).
 * 레거시 `VennColorContrastGuardTest` 의 Compose 포팅.
 */
class VennColorGuardTest {
    private val refs = VennThemeRefs.DEFAULT

    @Test
    fun `side colors meet primary 4point5 and sub 3point0 minimum in both light and dark`() {
        val colors = VennColorGuard.resolve(refs)
        for (side in listOf(colors.left, colors.right)) {
            val lightFill = blend(side, refs.lightBg, VennColorGuard.SIDE_ALPHA)
            val darkFill = blend(side, refs.darkBg, VennColorGuard.SIDE_ALPHA)
            val minPrimary =
                min(contrast(refs.lightPrimary, lightFill), contrast(refs.darkPrimary, darkFill))
            val minSub =
                min(contrast(refs.lightSub, lightFill), contrast(refs.darkSub, darkFill))
            assertTrue("primary contrast $minPrimary >= 4.5", minPrimary >= 4.5)
            assertTrue("sub contrast $minSub >= 3.0", minSub >= 3.0)
        }
    }

    @Test
    fun `intersection color meets primary 4point5 minimum in both light and dark`() {
        val colors = VennColorGuard.resolve(refs)
        val lightFill = blend(colors.intersection, refs.lightBg, VennColorGuard.INTERSECTION_ALPHA)
        val darkFill = blend(colors.intersection, refs.darkBg, VennColorGuard.INTERSECTION_ALPHA)
        val minPrimary =
            min(contrast(refs.lightPrimary, lightFill), contrast(refs.darkPrimary, darkFill))
        assertTrue("intersection primary contrast $minPrimary >= 4.5", minPrimary >= 4.5)
    }

    @Test
    fun `left and right stay distinguishable (color distance at least 50)`() {
        val colors = VennColorGuard.resolve(refs)
        assertTrue(distance(colors.left, colors.right) >= 50.0)
    }

    @Test
    fun `intersection stays distinct from both sides (color distance at least 40)`() {
        val colors = VennColorGuard.resolve(refs)
        assertTrue("intersection vs left", distance(colors.intersection, colors.left) >= 40.0)
        assertTrue("intersection vs right", distance(colors.intersection, colors.right) >= 40.0)
    }

    @Test
    fun `resolve is deterministic`() {
        assertTrue(VennColorGuard.resolve(refs) == VennColorGuard.resolve(refs))
    }

    // --- independent WCAG math ---

    private fun red(c: Int) = (c ushr 16) and 0xFF

    private fun green(c: Int) = (c ushr 8) and 0xFF

    private fun blue(c: Int) = c and 0xFF

    private fun blend(
        fg: Int,
        bg: Int,
        a: Int,
    ): Int {
        val r = (red(fg) * a + red(bg) * (255 - a)) / 255
        val g = (green(fg) * a + green(bg) * (255 - a)) / 255
        val b = (blue(fg) * a + blue(bg) * (255 - a)) / 255
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun contrast(
        fg: Int,
        bg: Int,
    ): Double {
        val l1 = luminance(fg)
        val l2 = luminance(bg)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun luminance(c: Int): Double {
        val r = lin(red(c) / 255.0)
        val g = lin(green(c) / 255.0)
        val b = lin(blue(c) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun lin(x: Double): Double = if (x <= 0.04045) x / 12.92 else ((x + 0.055) / 1.055).pow(2.4)

    private fun distance(
        a: Int,
        b: Int,
    ): Double {
        val dr = (red(a) - red(b)).toDouble()
        val dg = (green(a) - green(b)).toDouble()
        val db = (blue(a) - blue(b)).toDouble()
        return sqrt(dr * dr + dg * dg + db * db)
    }
}
