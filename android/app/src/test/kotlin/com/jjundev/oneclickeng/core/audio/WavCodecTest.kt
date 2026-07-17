package com.jjundev.oneclickeng.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WavCodecTest {
    @Test
    fun `encode then parse round-trips the samples and rate`() {
        val samples = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06)

        val parsed = parseWavPcm16(encodeWavPcm16(samples, 24_000))!!

        assertArrayEquals(samples, parsed.pcm)
        assertEquals(24_000, parsed.sampleRateHz)
    }

    @Test
    fun `parse skips unknown chunks before data`() {
        // Android TTS 가 쓰는 WAV 에는 LIST/fact 같은 청크가 끼어들 수 있다.
        val samples = byteArrayOf(0x11, 0x22)
        val base = encodeWavPcm16(samples, 16_000)
        val listChunk =
            "LIST".toByteArray(Charsets.US_ASCII) +
                byteArrayOf(4, 0, 0, 0) + byteArrayOf(9, 9, 9, 9)
        // fmt 청크(12..35) 뒤, data 청크(36..) 앞에 끼워 넣는다.
        val withChunk = base.copyOfRange(0, 36) + listChunk + base.copyOfRange(36, base.size)

        val parsed = parseWavPcm16(withChunk)!!

        assertArrayEquals(samples, parsed.pcm)
        assertEquals(16_000, parsed.sampleRateHz)
    }

    @Test
    fun `parse rejects a non-RIFF buffer`() {
        assertNull(parseWavPcm16("not a wav file at all".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `parse rejects a truncated header`() {
        assertNull(parseWavPcm16(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `parse recovers a data chunk whose declared size overruns the buffer`() {
        // 엔진이 스트리밍으로 쓰면 헤더의 길이가 실제보다 클 수 있다 — 남은 바이트를 그대로 쓴다.
        val encoded = encodeWavPcm16(byteArrayOf(1, 2, 3, 4), 16_000)
        val truncated = encoded.copyOfRange(0, encoded.size - 2)

        val parsed = parseWavPcm16(truncated)!!

        assertArrayEquals(byteArrayOf(1, 2), parsed.pcm)
    }
}
