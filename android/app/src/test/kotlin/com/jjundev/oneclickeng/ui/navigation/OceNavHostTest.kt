package com.jjundev.oneclickeng.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class OceNavHostTest {
    @Test
    fun `tab visit counter increments only when route enters target tab`() {
        var counter = 0

        counter = nextTabVisitCounter(OceTab.Home.route, OceTab.Settings.route, OceTab.Settings.route, counter)
        assertEquals(1, counter)

        counter = nextTabVisitCounter(OceTab.Settings.route, OceTab.Settings.route, OceTab.Settings.route, counter)
        assertEquals(1, counter)

        counter = nextTabVisitCounter(OceTab.Records.route, OceTab.Settings.route, OceTab.Settings.route, counter)
        assertEquals(2, counter)

        counter = nextTabVisitCounter(OceTab.Settings.route, OceTab.Home.route, OceTab.Home.route, counter)
        assertEquals(3, counter)
    }
}
