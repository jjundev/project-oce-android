package com.jjundev.oneclickeng.feature.settings

import com.jjundev.oneclickeng.feature.onboarding.google.LinkUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsFormatTest {
    @Test fun `no cards to purge uses dedicated message`() {
        assertEquals(SettingsMessage.NoCardsToPurge, purgeSelectionMessage(0))
    }

    @Test fun `positive purge count does not use no-cards message`() {
        assertEquals(null, purgeSelectionMessage(1))
    }

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

    @Test fun `google save loading stays true while credential flow is linking`() {
        assertEquals(true, googleSaveLoadingAfterLinkStateChange(previous = true, linkState = LinkUiState.Linking))
    }

    @Test fun `google save loading clears on success`() {
        assertEquals(false, googleSaveLoadingAfterLinkStateChange(previous = true, linkState = LinkUiState.Success))
    }

    @Test fun `google save loading clears on error`() {
        assertEquals(
            false,
            googleSaveLoadingAfterLinkStateChange(previous = true, linkState = LinkUiState.Error(afterSignIn = false)),
        )
    }

    @Test fun `google save loading clears on idle (credential picker cancelled)`() {
        assertEquals(false, googleSaveLoadingAfterLinkStateChange(previous = true, linkState = LinkUiState.Idle))
    }

    @Test fun `google save loading is not triggered by retry-merge linking alone`() {
        assertEquals(false, googleSaveLoadingAfterLinkStateChange(previous = false, linkState = LinkUiState.Linking))
    }
}
