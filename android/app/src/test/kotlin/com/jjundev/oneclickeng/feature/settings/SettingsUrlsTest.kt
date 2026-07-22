package com.jjundev.oneclickeng.feature.settings

import android.app.Application
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SettingsUrlsTest {
    @Test
    fun policyUrlsUseThePublishedFirebaseHostingPaths() {
        assertEquals("https", Uri.parse(SettingsUrls.PRIVACY).scheme)
        assertEquals("oce-v1.web.app", Uri.parse(SettingsUrls.PRIVACY).host)
        assertEquals("/privacy", Uri.parse(SettingsUrls.PRIVACY).path)
        assertEquals("https", Uri.parse(SettingsUrls.TERMS).scheme)
        assertEquals("oce-v1.web.app", Uri.parse(SettingsUrls.TERMS).host)
        assertEquals("/terms", Uri.parse(SettingsUrls.TERMS).path)
    }
}
