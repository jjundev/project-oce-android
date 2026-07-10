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
@Suppress("ReturnCount")
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
