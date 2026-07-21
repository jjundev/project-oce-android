package com.jjundev.oneclickeng.core.analytics

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26], application = Application::class)
class FirebaseAnalyticsSinkBundleTest {
    @Test
    fun `toAnalyticsBundle maps each supported type to the right Bundle slot`() {
        val bundle =
            mapOf(
                "s" to "text",
                "b" to true,
                "i" to 3,
                "l" to 7L,
                "d" to 1.5,
            ).toAnalyticsBundle()

        assertEquals("text", bundle.getString("s"))
        assertEquals(true, bundle.getBoolean("b"))
        assertEquals(3L, bundle.getLong("i")) // Int is widened to Long (GA4 numeric)
        assertEquals(7L, bundle.getLong("l"))
        assertEquals(1.5, bundle.getDouble("d"), 0.0)
    }

    @Test
    fun `empty map yields an empty bundle`() {
        assertEquals(0, emptyMap<String, Any>().toAnalyticsBundle().size())
    }
}
