package com.jjundev.oneclickeng.core.update

import com.google.android.play.core.install.model.UpdateAvailability
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayUpdateAvailabilityTest {
    @Test
    fun `update available plus immediate allowed is required`() {
        assertTrue(
            isImmediateUpdateAvailable(
                availability = UpdateAvailability.UPDATE_AVAILABLE,
                isImmediateAllowed = true,
            ),
        )
    }

    @Test
    fun `update available but immediate not allowed is not required`() {
        assertFalse(
            isImmediateUpdateAvailable(
                availability = UpdateAvailability.UPDATE_AVAILABLE,
                isImmediateAllowed = false,
            ),
        )
    }

    @Test
    fun `no update available is not required regardless of allowed flag`() {
        assertFalse(
            isImmediateUpdateAvailable(
                availability = UpdateAvailability.UPDATE_NOT_AVAILABLE,
                isImmediateAllowed = true,
            ),
        )
    }

    @Test
    fun `developer triggered update in progress is reported as in-progress`() {
        assertTrue(isImmediateUpdateInProgress(UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS))
    }

    @Test
    fun `update available is not reported as in-progress`() {
        assertFalse(isImmediateUpdateInProgress(UpdateAvailability.UPDATE_AVAILABLE))
    }
}
