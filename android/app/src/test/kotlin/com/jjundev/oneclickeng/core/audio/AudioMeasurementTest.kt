package com.jjundev.oneclickeng.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

private const val RATE = 16_000

/** [frames] 프레임짜리 PCM16 LE 모노 버퍼. [amplitude] 가 0 이면 무음. */
private fun pcm(
    frames: Int,
    amplitude: Int,
): ByteArray {
    val out = ByteArray(frames * 2)
    for (f in 0 until frames) {
        out[f * 2] = (amplitude and 0xFF).toByte()
        out[f * 2 + 1] = ((amplitude shr 8) and 0xFF).toByte()
    }
    return out
}

class AudioMeasurementTest {
    @Test
    fun `duration counts only the speech region between the silent padding`() {
        // 0.5s 무음 + 1.0s 발화 + 0.25s 무음 → 발화 1.0s 만 세야 한다.
        val buffer = pcm(RATE / 2, 0) + pcm(RATE, 8000) + pcm(RATE / 4, 0)

        assertEquals(1.0, trimmedDurationSeconds(buffer, RATE), 0.02)
    }

    @Test
    fun `an all-silent buffer measures zero`() {
        assertEquals(0.0, trimmedDurationSeconds(pcm(RATE, 0), RATE), 1e-9)
    }

    @Test
    fun `low-level room tone below the threshold is treated as silence`() {
        // 진폭 100 은 임계(≈1% full-scale) 아래 → 발화로 세지 않는다.
        assertEquals(0.0, trimmedDurationSeconds(pcm(RATE, 100), RATE), 1e-9)
    }

    @Test
    fun `an empty buffer measures zero`() {
        assertEquals(0.0, trimmedDurationSeconds(ByteArray(0), RATE), 1e-9)
    }

    @Test
    fun `negative samples count as speech`() {
        // -8000 은 상위 바이트가 부호 확장된다 — 부호 처리를 틀리면 여기서 잡힌다.
        val buffer = pcm(RATE / 2, 0) + pcm(RATE, -8000 and 0xFFFF) + pcm(RATE / 2, 0)

        assertEquals(1.0, trimmedDurationSeconds(buffer, RATE), 0.02)
    }

    @Test
    fun `a slower source yields a weight above one`() {
        // 기준보다 오래 걸린다 = 더 느리다 → 가속해야 하므로 w > 1.
        assertEquals(1.25f, calibrationWeight(sourceSeconds = 2.5, referenceSeconds = 2.0), 1e-4f)
    }

    @Test
    fun `a faster source yields a weight below one`() {
        assertEquals(0.8f, calibrationWeight(sourceSeconds = 1.6, referenceSeconds = 2.0), 1e-4f)
    }
}
