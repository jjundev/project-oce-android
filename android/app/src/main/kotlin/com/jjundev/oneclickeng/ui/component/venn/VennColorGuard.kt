package com.jjundev.oneclickeng.ui.component.venn

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

// 대비 수식(WCAG)·HSV 변환 헬퍼가 많은 순수 색 유틸 — android.graphics.Color 대체(JVM 테스트 가능).

/**
 * 벤다이어그램 런타임 대비 가드(I4) — 레거시 `VennColorContrastGuardTest`/`VennDiagramView` 알고리즘의
 * **순수 Kotlin 포팅**. 렌더와 분리되어(테스트 가능, 03-signature-interactions.md §I4) `android.graphics.Color`
 * 에 의존하지 않는다 → JVM 단위 테스트로 대비 불변식(NFR-8)을 검증할 수 있다.
 *
 * v1 은 모델이 색을 출력하지 않으므로(feedback-deep.md:8) 입력은 고정 팔레트 + 라이트/다크 테마 참조색뿐이다.
 * 가드는 좌/우/교집합 색을 **라이트·다크 양쪽에서 동시에** 주요텍스트 대비 ≥4.5·보조 ≥3.0, 좌우 색거리 ≥50,
 * 교집합-측면 거리 ≥40 을 만족하도록 후보 루프로 산출한다. 모든 값은 불투명 ARGB [Int]; 렌더 시 측면 alpha 128·
 * 교집합 체감 alpha 180 을 얹는다.
 */
@Suppress("TooManyFunctions")
object VennColorGuard {
    // 측면/교집합 렌더 alpha (레거시 VennDiagramView:28-29). 가드는 이 alpha 로 배경 블렌드 후 대비를 검증한다.
    const val SIDE_ALPHA = 128
    const val INTERSECTION_ALPHA = 180

    private const val MIN_PRIMARY_CONTRAST_SIDE = 4.5
    private const val MIN_SUB_CONTRAST_SIDE = 3.0
    private const val MIN_PRIMARY_CONTRAST_INTERSECTION = 4.5

    private const val MIN_SIDE_COLOR_DISTANCE = 50.0
    private const val MIN_INTERSECTION_COLOR_DISTANCE = 40.0

    // 고정 팔레트(모델 색 부재 시 시작점). 프로토 정합: 좌=브랜드 파랑(vennLeft: brand-primary),
    // 우=자연스러움 초록(vennRight: feedback-natural-accent). 불투명 ARGB.
    private const val FALLBACK_LEFT = 0xFF448DEB.toInt()
    private const val FALLBACK_RIGHT = 0xFF439B79.toInt()
    private const val FALLBACK_INTERSECTION = 0xFFB869F7.toInt()

    /**
     * 라이트/다크 대비를 검증한 좌·우·교집합 기본색(불투명 ARGB)을 산출한다. [refs] 는 테마 참조색으로,
     * 기본값은 레거시 폴백 상수(라이트 배경/텍스트 vs 다크 배경/텍스트)다.
     */
    fun resolve(refs: VennThemeRefs = VennThemeRefs.DEFAULT): VennColors {
        val left = chooseSideColor(FALLBACK_LEFT, FALLBACK_LEFT, avoid = null, refs)
        var right = chooseSideColor(FALLBACK_RIGHT, FALLBACK_RIGHT, avoid = left, refs)
        // 좌우가 너무 가까우면 오른쪽을 폴백에서 다시 고른다(구분 가능성 보장).
        if (colorDistance(left, right) < MIN_SIDE_COLOR_DISTANCE) {
            right = chooseSideColor(FALLBACK_RIGHT, FALLBACK_RIGHT, avoid = left, refs)
        }
        val intersection = chooseIntersectionColor(FALLBACK_INTERSECTION, left, right, refs)
        return VennColors(left = left, right = right, intersection = intersection)
    }

    private fun chooseSideColor(
        preferred: Int,
        fallback: Int,
        avoid: Int?,
        refs: VennThemeRefs,
    ): Int {
        val candidates =
            listOf(
                preferred,
                fallback,
                adjustLightness(preferred, 0.85f),
                adjustLightness(preferred, 1.15f),
                adjustHue(preferred, 20f),
                adjustHue(preferred, -20f),
                adjustHue(fallback, 20f),
                FALLBACK_LEFT,
                FALLBACK_RIGHT,
            )
        return candidates.firstOrNull { c ->
            passesSideContrast(c, refs) &&
                (avoid == null || colorDistance(c, avoid) >= MIN_SIDE_COLOR_DISTANCE)
        } ?: fallback
    }

    private fun chooseIntersectionColor(
        preferred: Int,
        left: Int,
        right: Int,
        refs: VennThemeRefs,
    ): Int {
        val candidates =
            listOf(
                preferred,
                FALLBACK_INTERSECTION,
                adjustHue(preferred, 24f),
                adjustHue(preferred, -24f),
                adjustLightness(preferred, 0.9f),
                adjustLightness(preferred, 1.1f),
            )
        // 대비 + 좌우 거리를 모두 만족하는 첫 후보; 없으면 대비만 만족하는 첫 후보; 최후엔 폴백.
        return candidates.firstOrNull { c ->
            passesIntersectionContrast(c, refs) &&
                colorDistance(c, left) >= MIN_INTERSECTION_COLOR_DISTANCE &&
                colorDistance(c, right) >= MIN_INTERSECTION_COLOR_DISTANCE
        }
            ?: candidates.firstOrNull { passesIntersectionContrast(it, refs) }
            ?: FALLBACK_INTERSECTION
    }

    private fun passesSideContrast(
        color: Int,
        refs: VennThemeRefs,
    ): Boolean {
        val lightFill = blendWithBackground(color, refs.lightBg, SIDE_ALPHA)
        val darkFill = blendWithBackground(color, refs.darkBg, SIDE_ALPHA)
        val minPrimary =
            min(
                contrastRatio(refs.lightPrimary, lightFill),
                contrastRatio(refs.darkPrimary, darkFill),
            )
        val minSub =
            min(
                contrastRatio(refs.lightSub, lightFill),
                contrastRatio(refs.darkSub, darkFill),
            )
        return minPrimary >= MIN_PRIMARY_CONTRAST_SIDE && minSub >= MIN_SUB_CONTRAST_SIDE
    }

    private fun passesIntersectionContrast(
        color: Int,
        refs: VennThemeRefs,
    ): Boolean {
        val lightFill = blendWithBackground(color, refs.lightBg, INTERSECTION_ALPHA)
        val darkFill = blendWithBackground(color, refs.darkBg, INTERSECTION_ALPHA)
        val minPrimary =
            min(
                contrastRatio(refs.lightPrimary, lightFill),
                contrastRatio(refs.darkPrimary, darkFill),
            )
        return minPrimary >= MIN_PRIMARY_CONTRAST_INTERSECTION
    }

    // --- pure color math (no android.graphics.Color) ---

    private fun red(c: Int) = (c ushr 16) and 0xFF

    private fun green(c: Int) = (c ushr 8) and 0xFF

    private fun blue(c: Int) = c and 0xFF

    private fun rgb(
        r: Int,
        g: Int,
        b: Int,
    ): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun colorDistance(
        a: Int,
        b: Int,
    ): Double {
        val dr = (red(a) - red(b)).toDouble()
        val dg = (green(a) - green(b)).toDouble()
        val db = (blue(a) - blue(b)).toDouble()
        return sqrt(dr * dr + dg * dg + db * db)
    }

    /** foreground(circle) over background at [alpha]/255 → opaque blended color. */
    private fun blendWithBackground(
        fg: Int,
        bg: Int,
        alpha: Int,
    ): Int {
        val r = (red(fg) * alpha + red(bg) * (255 - alpha)) / 255
        val g = (green(fg) * alpha + green(bg) * (255 - alpha)) / 255
        val b = (blue(fg) * alpha + blue(bg) * (255 - alpha)) / 255
        return rgb(r, g, b)
    }

    private fun contrastRatio(
        textColor: Int,
        backgroundColor: Int,
    ): Double {
        val textLum = relativeLuminance(textColor)
        val bgLum = relativeLuminance(backgroundColor)
        val lighter = max(textLum, bgLum)
        val darker = min(textLum, bgLum)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(color: Int): Double {
        val r = linearize(red(color) / 255.0)
        val g = linearize(green(color) / 255.0)
        val b = linearize(blue(color) / 255.0)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun linearize(channel: Double): Double {
        return if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
    }

    private fun adjustHue(
        color: Int,
        hueDelta: Float,
    ): Int {
        val hsv = rgbToHsv(color)
        hsv[0] = (hsv[0] + hueDelta + 360f) % 360f
        return hsvToRgb(hsv)
    }

    private fun adjustLightness(
        color: Int,
        factor: Float,
    ): Int {
        val hsv = rgbToHsv(color)
        hsv[2] = (hsv[2] * factor).coerceIn(0.25f, 0.95f)
        return hsvToRgb(hsv)
    }

    /** RGB→HSV (h in [0,360), s/v in [0,1]) — replaces android.graphics.Color.colorToHSV. */
    private fun rgbToHsv(color: Int): FloatArray {
        val r = red(color) / 255f
        val g = green(color) / 255f
        val b = blue(color) / 255f
        val cMax = max(r, max(g, b))
        val cMin = min(r, min(g, b))
        val delta = cMax - cMin
        val h =
            when {
                delta == 0f -> 0f
                cMax == r -> 60f * (((g - b) / delta) % 6f)
                cMax == g -> 60f * (((b - r) / delta) + 2f)
                else -> 60f * (((r - g) / delta) + 4f)
            }
        val hue = if (h < 0f) h + 360f else h
        val s = if (cMax == 0f) 0f else delta / cMax
        return floatArrayOf(hue, s, cMax)
    }

    /** HSV→RGB (opaque ARGB) — replaces android.graphics.Color.HSVToColor. */
    private fun hsvToRgb(hsv: FloatArray): Int {
        val h = ((hsv[0] % 360f) + 360f) % 360f
        val s = hsv[1].coerceIn(0f, 1f)
        val v = hsv[2].coerceIn(0f, 1f)
        val c = v * s
        val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
        val m = v - c
        val (r1, g1, b1) =
            when {
                h < 60f -> Triple(c, x, 0f)
                h < 120f -> Triple(x, c, 0f)
                h < 180f -> Triple(0f, c, x)
                h < 240f -> Triple(0f, x, c)
                h < 300f -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
        return rgb(
            ((r1 + m) * 255f).toInt().coerceIn(0, 255),
            ((g1 + m) * 255f).toInt().coerceIn(0, 255),
            ((b1 + m) * 255f).toInt().coerceIn(0, 255),
        )
    }
}

/** 좌·우 원 + 교집합 기본색(불투명 ARGB [Int]). 렌더가 측면/교집합 alpha 를 얹는다. */
data class VennColors(
    val left: Int,
    val right: Int,
    val intersection: Int,
)

/**
 * 대비 검증에 쓰는 라이트/다크 테마 참조색(불투명 ARGB). 기본값은 레거시 폴백 상수 — 배경/주요텍스트/보조텍스트
 * 각각 라이트·다크. 렌더 화면은 실제 테마 토큰을 주입할 수 있으나, 가드는 양모드를 동시에 검증하므로 참조색만으로
 * 단일 팔레트를 산출한다.
 */
data class VennThemeRefs(
    val lightBg: Int,
    val darkBg: Int,
    val lightPrimary: Int,
    val darkPrimary: Int,
    val lightSub: Int,
    val darkSub: Int,
) {
    companion object {
        val DEFAULT =
            VennThemeRefs(
                lightBg = 0xFFFFFFFF.toInt(),
                darkBg = 0xFF1A1B20.toInt(),
                lightPrimary = 0xFF353C45.toInt(),
                darkPrimary = 0xFFF2F3F5.toInt(),
                lightSub = 0xFF676B73.toInt(),
                darkSub = 0xFFA9ADB6.toInt(),
            )
    }
}
