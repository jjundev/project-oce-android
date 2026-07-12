package com.jjundev.oneclickeng.feature.session.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 성별→피치 매핑 순수 로직 검증. 기기 TextToSpeech 는 표준 성별 API 가 없어(Voice 에 gender 필드 없음)
 * 이름 매칭이 실기기 보이스명과 안 맞아 항상 기본(여성) 보이스로 떨어졌다. 이를 보완하려 남성은 피치를
 * 낮춰 귀로 구분되게 한다. 프레임워크 [android.speech.tts.TextToSpeech.setPitch] 호출 자체는 이식 글루라
 * 유닛테스트 대상이 아니고, 결정 로직만 [pitchForGender] 로 분리해 여기서 반증가능하게 잠근다.
 */
class DeviceTtsPitchTest {
    @Test
    fun `male gender lowers pitch below female`() {
        assertTrue(pitchForGender("male") < pitchForGender("female"))
    }

    @Test
    fun `male matching is case-insensitive`() {
        assertEquals(pitchForGender("male"), pitchForGender("MALE"), 0f)
    }

    @Test
    fun `null female and unknown all use the default female pitch`() {
        assertEquals(FEMALE_PITCH, pitchForGender(null), 0f)
        assertEquals(FEMALE_PITCH, pitchForGender("female"), 0f)
        assertEquals(FEMALE_PITCH, pitchForGender("other"), 0f)
    }

    @Test
    fun `male pitch is a positive value below the female pitch`() {
        val male = pitchForGender("male")
        assertTrue("male pitch must stay positive", male > 0f)
        assertTrue("male pitch must be below female", male < FEMALE_PITCH)
    }
}
