package com.jjundev.oneclickeng.feature.session.turn

import org.junit.Assert.assertEquals
import org.junit.Test

class DialogueLevelLabelTest {
    @Test
    fun `maps 5 tiers to korean label plus turns`() {
        assertEquals("매우 쉬움 · 6턴", dialogueLevelLabelForTest("starter", 6))
        assertEquals("중간 · 10턴", dialogueLevelLabelForTest("normal", 10))
        assertEquals("매우 어려움 · 20턴", dialogueLevelLabelForTest("expert", 20))
    }
}
