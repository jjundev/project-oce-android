package com.jjundev.oneclickeng.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/** 시트 세로 리듬 토큰 정본(grill 확정: 핸들갭 12dp / 하단 24dp). 값 변경 시 이 가드가 먼저 깨진다. */
class OceSpacingTokensTest {
    private val tokens = OceSpacingTokens

    @Test
    fun sheetHandleGap_is_12dp() {
        assertEquals(12.dp, tokens.sheetHandleGap)
    }

    @Test
    fun sheetContentBottom_is_24dp() {
        assertEquals(24.dp, tokens.sheetContentBottom)
    }

    @Test
    fun sheetPadding_horizontal_stays_24dp() {
        assertEquals(24.dp, tokens.sheetPadding)
    }
}
