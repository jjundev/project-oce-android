package com.jjundev.oneclickeng.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AudioMathTest {
    /** short 샘플 배열을 리틀엔디안 16-bit PCM 바이트로 인코딩. */
    private fun pcmOf(vararg samples: Int): ByteArray {
        val out = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s ->
            out[i * 2] = (s and 0xFF).toByte()
            out[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    @Test
    fun `normalizedRms of silence is zero`() {
        assertEquals(0f, AudioMath.normalizedRms(pcmOf(0, 0, 0, 0)), 0f)
    }

    @Test
    fun `normalizedRms of empty or odd buffer is zero`() {
        assertEquals(0f, AudioMath.normalizedRms(ByteArray(0)), 0f)
        assertEquals(0f, AudioMath.normalizedRms(ByteArray(1)), 0f)
    }

    @Test
    fun `normalizedRms of half-scale constant is one half`() {
        val half = 16384 // 32768 * 0.5
        val rms = AudioMath.normalizedRms(pcmOf(half, half, half, half))
        assertEquals(0.5f, rms, 1e-4f)
    }

    @Test
    fun `normalizedRms honors short-read length`() {
        // 뒤쪽 2 샘플은 큰 값이지만 length 로 앞 2 샘플(무음)만 계산 → 0.
        val buffer = pcmOf(0, 0, 16384, 16384)
        assertEquals(0f, AudioMath.normalizedRms(buffer, length = 4), 0f)
    }

    @Test
    fun `voicedFraction counts chunks above threshold`() {
        val rms = listOf(0.0f, 0.01f, 0.03f, 0.5f) // 2 of 4 above 0.02
        assertEquals(0.5f, AudioMath.voicedFraction(rms), 0f)
    }

    @Test
    fun `isTooQuiet true for all-silent chunks`() {
        assertTrue(AudioMath.isTooQuiet(listOf(0.0f, 0.001f, 0.0f)))
    }

    @Test
    fun `isTooQuiet true for empty input`() {
        assertTrue(AudioMath.isTooQuiet(emptyList()))
    }

    @Test
    fun `isTooQuiet false when enough voiced chunks and a real peak`() {
        // 40 chunks, all 0.3 → voicedFraction 1.0, peak 0.3 → not too quiet.
        val rms = List(40) { 0.3f }
        assertFalse(AudioMath.isTooQuiet(rms))
    }

    @Test
    fun `isTooQuiet true when peak below floor despite fraction`() {
        // fraction ok (>0.1) but every chunk under peak floor 0.05.
        val rms = List(40) { 0.03f }
        assertTrue(AudioMath.isTooQuiet(rms))
    }

    @Test
    fun `levelFrom applies gain and clamps to floor and ceiling`() {
        assertEquals(AudioMath.WAVEFORM_FLOOR, AudioMath.levelFrom(0f), 0f)
        assertEquals(AudioMath.WAVEFORM_CEIL, AudioMath.levelFrom(1f), 0f) // 1*3 clamps to 1
        assertEquals(0.3f, AudioMath.levelFrom(0.1f), 1e-6f) // 0.1*3 = 0.3
    }

    @Test
    fun `waveformFrame has bar count length and stays within bounds`() {
        val frame = AudioMath.waveformFrame(0.6f, Random(42))
        assertEquals(AudioMath.BAR_COUNT, frame.size)
        frame.forEach {
            assertTrue(it >= AudioMath.WAVEFORM_FLOOR)
            assertTrue(it <= AudioMath.WAVEFORM_CEIL)
        }
    }

    @Test
    fun `waveformFrame is deterministic for a fixed seed`() {
        val a = AudioMath.waveformFrame(0.5f, Random(7))
        val b = AudioMath.waveformFrame(0.5f, Random(7))
        assertTrue(a.contentEquals(b))
    }

    @Test
    fun `floorFrame is all floor`() {
        val frame = AudioMath.floorFrame()
        assertEquals(AudioMath.BAR_COUNT, frame.size)
        frame.forEach { assertEquals(AudioMath.WAVEFORM_FLOOR, it, 0f) }
    }
}
