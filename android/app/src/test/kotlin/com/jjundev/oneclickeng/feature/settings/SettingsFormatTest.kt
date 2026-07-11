package com.jjundev.oneclickeng.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsFormatTest {
    @Test fun `evening 20_00 formats as 오후 8_00`() {
        assertEquals("오후 8:00", reminderTimeLabel(20, 0))
    }

    @Test fun `midnight 0_05 formats as 오전 12_05`() {
        assertEquals("오전 12:05", reminderTimeLabel(0, 5))
    }

    @Test fun `noon 12_30 formats as 오후 12_30`() {
        assertEquals("오후 12:30", reminderTimeLabel(12, 30))
    }

    @Test fun `morning 9_00 formats as 오전 9_00`() {
        assertEquals("오전 9:00", reminderTimeLabel(9, 0))
    }
}
