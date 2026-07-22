package com.jjundev.oneclickeng.feature.session.turn

import com.jjundev.oneclickeng.R
import org.junit.Assert.assertEquals
import org.junit.Test

class OpponentAvatarResourceTest {
    @Test
    fun `male speaker resolves the male avatar drawable`() {
        assertEquals(
            R.drawable.profile_opponent_male,
            opponentAvatarResource(Speaker(name = "Liam", gender = "male")),
        )
    }

    @Test
    fun `female speaker resolves the female avatar drawable`() {
        assertEquals(
            R.drawable.profile_opponent_female,
            opponentAvatarResource(Speaker(name = "Emma", gender = "female")),
        )
    }
}
