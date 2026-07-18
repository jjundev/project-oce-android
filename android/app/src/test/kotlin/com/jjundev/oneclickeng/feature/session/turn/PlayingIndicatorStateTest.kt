package com.jjundev.oneclickeng.feature.session.turn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayingIndicatorStateTest {
    @Test
    fun `starts with nothing playing`() {
        val state = PlayingIndicatorState()
        assertNull(state.opponentText)
        assertNull(state.learnerOrdinal)
    }

    @Test
    fun `startOpponent sets the opponent text and clears any learner ordinal`() {
        val state = PlayingIndicatorState()
        state.startLearner(2)
        state.startOpponent("Hello")
        assertTrue(state.isOpponentPlaying("Hello"))
        assertNull(state.learnerOrdinal)
    }

    @Test
    fun `startLearner sets the ordinal and clears any opponent text`() {
        val state = PlayingIndicatorState()
        state.startOpponent("Hello")
        state.startLearner(3)
        assertTrue(state.isLearnerPlaying(3))
        assertNull(state.opponentText)
    }

    @Test
    fun `isOpponentPlaying matches only the currently playing text`() {
        val state = PlayingIndicatorState()
        state.startOpponent("Hello")
        assertTrue(state.isOpponentPlaying("Hello"))
        assertFalse(state.isOpponentPlaying("Other"))
    }

    @Test
    fun `isLearnerPlaying matches only the currently playing ordinal`() {
        val state = PlayingIndicatorState()
        state.startLearner(1)
        assertTrue(state.isLearnerPlaying(1))
        assertFalse(state.isLearnerPlaying(2))
    }

    @Test
    fun `clear resets both fields`() {
        val state = PlayingIndicatorState()
        state.startOpponent("Hello")
        state.clear()
        assertNull(state.opponentText)
        assertFalse(state.isOpponentPlaying("Hello"))
    }

    @Test
    fun `nothing is considered playing before anything starts`() {
        val state = PlayingIndicatorState()
        assertFalse(state.isOpponentPlaying("Hello"))
        assertFalse(state.isLearnerPlaying(0))
    }
}
