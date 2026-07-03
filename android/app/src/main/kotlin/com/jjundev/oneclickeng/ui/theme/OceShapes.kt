package com.jjundev.oneclickeng.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * 코너 반경 스케일 8단. 값 정본: design-tokens.md §4.3.
 * 앱 컴포넌트는 OceTheme.shapes 만 소비한다(예: radius18 채팅 말풍선은 M3 large 금지, 여기서 직접 read).
 */
@Immutable
data class OceShapes(
    val radius4: RoundedCornerShape = RoundedCornerShape(4.dp),
    val radius8: RoundedCornerShape = RoundedCornerShape(8.dp),
    val radius12: RoundedCornerShape = RoundedCornerShape(12.dp),
    val radius14: RoundedCornerShape = RoundedCornerShape(14.dp),
    val radius16: RoundedCornerShape = RoundedCornerShape(16.dp),
    val radius18: RoundedCornerShape = RoundedCornerShape(18.dp),
    val radius24: RoundedCornerShape = RoundedCornerShape(24.dp),
    val pill: RoundedCornerShape = RoundedCornerShape(100.dp),
)

internal val OceShapesTokens = OceShapes()

val LocalOceShapes = staticCompositionLocalOf { OceShapesTokens }

// M3 제공 컴포넌트(ModalBottomSheet 등) 전용 브릿지. small12 / medium16 / large24.
internal val OceM3Shapes =
    Shapes(
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
    )
