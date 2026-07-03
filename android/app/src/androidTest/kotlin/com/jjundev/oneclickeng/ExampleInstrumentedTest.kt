package com.jjundev.oneclickeng

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 계측 테스트 스텁. 에뮬레이터/기기에서만 실행되며 CI 게이트에는 포함하지 않는다(로컬 스모크).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.jjundev.oneclickeng", context.packageName)
    }
}
