package com.jjundev.oneclickeng.ui.component.venn

import com.jjundev.oneclickeng.feature.session.feedback.VennCircle
import com.jjundev.oneclickeng.feature.session.feedback.VennData
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 정보 강화 벤(결정 #18): 텍스트 대안이 두 단어의 고유 뜻과 공통 의미를 모두 노출하는지 검증한다.
 * 색 단독 신호 금지(A2) — semantics contentDescription 이 다이어그램 시각의 완전한 텍스트 대안이어야 한다.
 */
class VennContentDescriptionTest {
    @Test
    fun `content description lists both words with their items and the shared meaning`() {
        val venn =
            VennData(
                guide = "두 단어의 의미 차이를 볼까요?",
                left = VennCircle(word = "get", items = listOf("얻다", "받다")),
                right = VennCircle(word = "order", items = listOf("주문하다")),
                intersectionItems = listOf("받다"),
            )
        assertEquals(
            "get(얻다, 받다)와 order(주문하다)의 공통 의미: 받다",
            venn.toVennContentDescription(),
        )
    }

    @Test
    fun `content description tolerates empty item lists`() {
        val venn =
            VennData(
                guide = "",
                left = VennCircle(word = "get", items = emptyList()),
                right = VennCircle(word = "order", items = emptyList()),
                intersectionItems = emptyList(),
            )
        assertEquals("get와 order의 공통 의미: ", venn.toVennContentDescription())
    }
}
