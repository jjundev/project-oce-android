package com.jjundev.oneclickeng.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class OceNavHostTest {
    @Test
    fun `settings visit counter increments only when route enters settings`() {
        var counter = 0

        counter = nextSettingsVisitCounter(OceTab.Home.route, OceTab.Settings.route, counter)
        assertEquals(1, counter)

        counter = nextSettingsVisitCounter(OceTab.Settings.route, OceTab.Settings.route, counter)
        assertEquals(1, counter)

        counter = nextSettingsVisitCounter(OceTab.Records.route, OceTab.Settings.route, counter)
        assertEquals(2, counter)
    }
}
