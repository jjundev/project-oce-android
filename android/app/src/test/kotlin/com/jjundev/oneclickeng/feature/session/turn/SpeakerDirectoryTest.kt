package com.jjundev.oneclickeng.feature.session.turn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakerDirectoryTest {
    @Test
    fun `pool holds dozens of entries with unique non-blank names`() {
        val entries = SpeakerDirectory.ENTRIES
        assertTrue("expected dozens of speakers, got ${entries.size}", entries.size >= 30)
        assertTrue("names must be non-blank", entries.all { it.name.isNotBlank() })
        assertEquals("names must be unique", entries.size, entries.map { it.name }.toSet().size)
    }

    @Test
    fun `every entry gender is male or female (selectGenderVoice contract)`() {
        assertTrue(SpeakerDirectory.ENTRIES.all { it.gender == "male" || it.gender == "female" })
    }

    @Test
    fun `pool contains both genders`() {
        assertTrue(SpeakerDirectory.ENTRIES.any { it.gender == "male" })
        assertTrue(SpeakerDirectory.ENTRIES.any { it.gender == "female" })
    }

    @Test
    fun `assign is deterministic for the same sessionId`() {
        val a = SpeakerDirectory.assign("session-abc-123")
        val b = SpeakerDirectory.assign("session-abc-123")
        assertEquals(a, b)
    }

    @Test
    fun `assign always returns a pool member`() {
        listOf("s1", "s2", "another-session", "", "服务器", "x").forEach {
            assertTrue(SpeakerDirectory.assign(it) in SpeakerDirectory.ENTRIES)
        }
    }

    @Test
    fun `assign spreads distinct sessions across more than one speaker`() {
        val distinct = (0 until 200).map { SpeakerDirectory.assign("session-$it") }.toSet()
        assertTrue("expected variety across sessions, got ${distinct.size}", distinct.size > 1)
    }

}
