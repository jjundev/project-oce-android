package com.jjundev.oneclickeng.feature.session.turn

// The feature.session.turn package declares its OWN unrelated `DialogueTurn`
// (SampleDialogue.kt), so the network turn MUST be import-aliased — matching the
// sibling tests (GeneratedDialogueStateTest, OpponentSkeletonFloorTest, SessionTurnSnapshotTest).
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.jjundev.oneclickeng.core.network.DialogueTurn as NetworkDialogueTurn

class NextOpponentLineTest {
    private val turns =
        listOf(
            // 0 = opponent #0
            NetworkDialogueTurn(ko = "안녕", en = "Hello", role = "model"),
            // 1 = learner
            NetworkDialogueTurn(ko = "나 좋아", en = "I am good", role = "user"),
            // 2 = opponent #1
            NetworkDialogueTurn(ko = "잘가", en = "Goodbye", role = "model"),
            // 3 = learner
            NetworkDialogueTurn(ko = "응", en = "Bye", role = "user"),
        )

    @Test
    fun `ordinal 0 returns the first opponent line`() {
        assertEquals("Hello", nextOpponentEnglish(turns, 0))
    }

    @Test
    fun `ordinal 1 returns the second opponent line at index 2`() {
        assertEquals("Goodbye", nextOpponentEnglish(turns, 1))
    }

    @Test
    fun `out-of-range ordinal returns null`() {
        assertNull(nextOpponentEnglish(turns, 2)) // index 4 does not exist
    }

    @Test
    fun `null when the even-index turn is not a model line`() {
        val malformed = listOf(NetworkDialogueTurn(ko = "x", en = "y", role = "user"))
        assertNull(nextOpponentEnglish(malformed, 0))
    }

    @Test
    fun `null when the opponent line text is blank`() {
        val blank = listOf(NetworkDialogueTurn(ko = "x", en = "  ", role = "model"))
        assertNull(nextOpponentEnglish(blank, 0))
    }
}
