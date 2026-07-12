package com.jjundev.oneclickeng.feature.session.turn

import org.junit.Assert.assertEquals
import org.junit.Test

class LearnerClipRenderingTest {
    private val messages =
        listOf(
            DialogueMessage.Opponent("hi"), // index 0
            DialogueMessage.Learner("one"), // index 1 -> learner ordinal 0
            DialogueMessage.Opponent("ok"), // index 2
            DialogueMessage.Learner("two"), // index 3 -> learner ordinal 1
            DialogueMessage.Learner("three"), // index 4 -> learner ordinal 2
        )

    @Test
    fun `learner ordinal counts learner bubbles strictly before the index`() {
        assertEquals(0, learnerOrdinalAt(messages, 1))
        assertEquals(1, learnerOrdinalAt(messages, 3))
        assertEquals(2, learnerOrdinalAt(messages, 4))
    }

    @Test
    fun `first learner bubble is ordinal zero`() {
        assertEquals(0, learnerOrdinalAt(listOf(DialogueMessage.Learner("solo")), 0))
    }
}
