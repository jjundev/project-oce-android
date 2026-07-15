package com.jjundev.oneclickeng.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelLengthTest {
    @Test
    fun `clampLength snaps to nearest even in 6 to 20`() {
        assertEquals(6, HomeViewModel.clampLength(5))
        assertEquals(6, HomeViewModel.clampLength(4))
        assertEquals(20, HomeViewModel.clampLength(21))
        assertEquals(12, HomeViewModel.clampLength(13)) // 13 → 12 (nearest even, round down on .5 boundary)
        assertEquals(10, HomeViewModel.clampLength(10))
    }
}
