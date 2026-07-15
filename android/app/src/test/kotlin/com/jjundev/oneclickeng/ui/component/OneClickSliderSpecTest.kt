package com.jjundev.oneclickeng.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class OneClickSliderSpecTest {
    @Test
    fun `even 6 to 20 step 2 yields 8 stops so 6 intermediate steps`() {
        val spec = steppedSliderSpec(6..20, 2)
        assertEquals(6f, spec.valueRange.start)
        assertEquals(20f, spec.valueRange.endInclusive)
        assertEquals(6, spec.steps) // stops = steps + 2 = 8
    }

    @Test
    fun `level 0 to 4 step 1 yields 5 stops so 3 intermediate steps`() {
        val spec = steppedSliderSpec(0..4, 1)
        assertEquals(0f, spec.valueRange.start)
        assertEquals(4f, spec.valueRange.endInclusive)
        assertEquals(3, spec.steps)
    }

    @Test
    fun `single stop range never yields negative steps`() {
        assertEquals(0, steppedSliderSpec(10..10, 2).steps)
    }
}
