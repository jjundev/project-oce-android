package com.jjundev.oneclickeng.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 계수 자체(WEIGHT_*)는 Task 6 실측으로 바뀌는 값이므로 **숫자를 하드코딩하지 않는다.**
 * 여기서 고정하는 건 값이 바뀌어도 유지돼야 하는 계약이다: 성별 라우팅, 클램프, 단조성.
 */
class TtsSpeedCalibrationTest {
    @Test
    fun `male gender routes to the male weight and is case-insensitive`() {
        val lower = TtsSpeedCalibration.serverPlaybackSpeed(1.0f, "male")
        val upper = TtsSpeedCalibration.serverPlaybackSpeed(1.0f, "MALE")

        assertEquals(lower, upper, TOLERANCE)
        assertEquals(TtsSpeedCalibration.WEIGHT_SERVER_MALE, lower, TOLERANCE)
    }

    @Test
    fun `null and non-male genders route to the female weight`() {
        val expected = TtsSpeedCalibration.WEIGHT_SERVER_FEMALE

        assertEquals(expected, TtsSpeedCalibration.serverPlaybackSpeed(1.0f, null), TOLERANCE)
        assertEquals(expected, TtsSpeedCalibration.serverPlaybackSpeed(1.0f, "female"), TOLERANCE)
        assertEquals(expected, TtsSpeedCalibration.serverPlaybackSpeed(1.0f, "unknown"), TOLERANCE)
    }

    @Test
    fun `device rate at 1x is the device weight`() {
        assertEquals(TtsSpeedCalibration.WEIGHT_DEVICE, TtsSpeedCalibration.deviceSpeechRate(1.0f), TOLERANCE)
    }

    @Test
    fun `effective rate is clamped at both ends`() {
        assertEquals(
            TtsSpeedCalibration.MAX_EFFECTIVE_RATE,
            TtsSpeedCalibration.serverPlaybackSpeed(99f, null),
            TOLERANCE,
        )
        assertEquals(
            TtsSpeedCalibration.MIN_EFFECTIVE_RATE,
            TtsSpeedCalibration.serverPlaybackSpeed(0.001f, null),
            TOLERANCE,
        )
        assertEquals(TtsSpeedCalibration.MAX_EFFECTIVE_RATE, TtsSpeedCalibration.deviceSpeechRate(99f), TOLERANCE)
        assertEquals(TtsSpeedCalibration.MIN_EFFECTIVE_RATE, TtsSpeedCalibration.deviceSpeechRate(0.001f), TOLERANCE)
    }

    @Test
    fun `a faster slider never yields a slower effective rate`() {
        // 슬라이더 실사용 범위(0.5~1.5x)를 훑어 단조 증가를 확인한다.
        var previous = 0f
        var slider = 0.5f
        while (slider <= 1.5f) {
            val speed = TtsSpeedCalibration.serverPlaybackSpeed(slider, null)
            assertTrue("slider=$slider produced $speed after $previous", speed >= previous)
            previous = speed
            slider += 0.1f
        }
    }

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}
