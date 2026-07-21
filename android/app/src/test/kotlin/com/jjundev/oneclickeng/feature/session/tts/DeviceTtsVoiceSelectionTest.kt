package com.jjundev.oneclickeng.feature.session.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [pickGenderVoiceName] — 아카이브 DialoguePlaybackCoordinator.setTtsVoice 의 4단계 폴백 이식 검증.
 * 실기기 Google TTS 보이스는 이름에 "male"/"female" 리터럴이 없고 iol/sfg/tpf 같은 품질 코드를 쓰므로,
 * 리터럴(1) → 엔진 코드(2) → 반대 성별 회피(3) → 아무 en 보이스(4) 순으로 골라야 한다.
 */
class DeviceTtsVoiceSelectionTest {
    private fun v(
        name: String,
        lang: String = "en",
        country: String = "US",
    ) = VoiceInfo(name = name, language = lang, country = country)

    @Test
    fun `pass1 picks a female literal name for female`() {
        val voices = listOf(v("en-US-language-female"))
        assertEquals("en-US-language-female", pickGenderVoiceName(voices, "female", "en", "US"))
    }

    @Test
    fun `pass1 picks a male literal name and excludes female literal for male`() {
        val voices = listOf(v("en-US-female-a"), v("en-US-male-b"))
        assertEquals("en-US-male-b", pickGenderVoiceName(voices, "male", "en", "US"))
    }

    @Test
    fun `pass2 matches google male voice code iol`() {
        val voices =
            listOf(v("en-us-x-iol-local"), v("en-us-x-sfg-local"), v("en-us-x-tpf-network"))
        assertEquals("en-us-x-iol-local", pickGenderVoiceName(voices, "male", "en", "US"))
    }

    @Test
    fun `pass2 matches the first google female voice code in order`() {
        val voices = listOf(v("en-us-x-iol-local"), v("en-us-x-tpf-local"), v("en-us-x-sfg-local"))
        // First voice matching ANY female code (by input-list order) wins — here tpf precedes sfg.
        assertEquals("en-us-x-tpf-local", pickGenderVoiceName(voices, "female", "en", "US"))
    }

    @Test
    fun `male and female resolve to different voices on a mixed pool`() {
        val voices = listOf(v("en-us-x-iol-local"), v("en-us-x-sfg-local"))
        val male = pickGenderVoiceName(voices, "male", "en", "US")
        val female = pickGenderVoiceName(voices, "female", "en", "US")
        assertEquals("en-us-x-iol-local", male)
        assertEquals("en-us-x-sfg-local", female)
        assertTrue(male != female)
    }

    @Test
    fun `pass3 avoidance skips opposite-gender markers and prefers matching country`() {
        // No literal/code male match; two unmarked voices, one GB one US → US preferred.
        val voices =
            listOf(
                v("en-us-x-aaa-local", country = "GB"),
                v("en-us-x-bbb-local", country = "US"),
            )
        assertEquals("en-us-x-bbb-local", pickGenderVoiceName(voices, "male", "en", "US"))
    }

    @Test
    fun `pass3 male avoids female-coded voices`() {
        // Only unmarked + female-coded present; male must NOT pick the sfg one.
        val voices = listOf(v("en-us-x-sfg-local"), v("en-us-x-zzz-local"))
        assertEquals("en-us-x-zzz-local", pickGenderVoiceName(voices, "male", "en", "US"))
    }

    @Test
    fun `language filter ignores non-english voices`() {
        val voices = listOf(v("fr-FR-voix", lang = "fr", country = "FR"), v("en-us-x-sfg-local"))
        assertEquals("en-us-x-sfg-local", pickGenderVoiceName(voices, "female", "en", "US"))
    }

    @Test
    fun `pass4 falls back to any english voice when no gender signal exists`() {
        val voices = listOf(v("en-us-x-neutral-local"))
        assertEquals("en-us-x-neutral-local", pickGenderVoiceName(voices, "male", "en", "US"))
    }

    @Test
    fun `pass3 keeps the last non-opposite candidate when no country matches`() {
        // Two unmarked en voices, neither country == target US → pass3 overwrites each iteration,
        // never breaks on a country match, so the LAST candidate wins (order-sensitive tie-break).
        val voices =
            listOf(
                v("en-us-x-aaa-local", country = "GB"),
                v("en-us-x-bbb-local", country = "CA"),
            )
        assertEquals("en-us-x-bbb-local", pickGenderVoiceName(voices, "male", "en", "US"))
    }

    @Test
    fun `pass4 accepts an opposite-gender voice as the true last resort`() {
        // Every en voice is female-coded; a male request exhausts pass1-3 (all skipped in pass3),
        // so pass4 must fall back and accept the first en voice regardless of its gender code.
        val voices = listOf(v("en-us-x-sfg-local"), v("en-us-x-tpf-local"))
        assertEquals("en-us-x-sfg-local", pickGenderVoiceName(voices, "male", "en", "US"))
    }

    @Test
    fun `returns null when no english voice is available`() {
        val voices = listOf(v("fr-FR-voix", lang = "fr", country = "FR"))
        assertNull(pickGenderVoiceName(voices, "female", "en", "US"))
    }

    @Test
    fun `null gender is treated as female`() {
        val voices = listOf(v("en-us-x-iol-local"), v("en-us-x-sfg-local"))
        assertEquals(
            pickGenderVoiceName(voices, "female", "en", "US"),
            pickGenderVoiceName(voices, null, "en", "US"),
        )
    }

    @Test
    fun `empty target country still resolves via language-only pass4`() {
        val voices = listOf(v("en-x-neutral", country = ""))
        assertNotNull(pickGenderVoiceName(voices, "male", "en", ""))
    }
}
