package com.jjundev.oneclickeng.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavEncoderTest {
    private val sampleRate = 16_000
    private val pcm = ByteArray(640) { (it % 256).toByte() }
    private val wav = WavEncoder.wrap(pcm, sampleRate)
    private val buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

    private fun ascii(
        offset: Int,
        len: Int,
    ): String = String(wav.copyOfRange(offset, offset + len), Charsets.US_ASCII)

    @Test
    fun `total length is 44 byte header plus pcm`() {
        assertEquals(44 + pcm.size, wav.size)
    }

    @Test
    fun `riff and wave and fmt and data markers present`() {
        assertEquals("RIFF", ascii(0, 4))
        assertEquals("WAVE", ascii(8, 4))
        assertEquals("fmt ", ascii(12, 4))
        assertEquals("data", ascii(36, 4))
    }

    @Test
    fun `riff chunk size is 36 plus data size`() {
        assertEquals(36 + pcm.size, buf.getInt(4))
    }

    @Test
    fun `fmt chunk describes 16k mono pcm16`() {
        assertEquals(16, buf.getInt(16)) // fmt chunk length
        assertEquals(1.toShort(), buf.getShort(20)) // PCM
        assertEquals(1.toShort(), buf.getShort(22)) // mono
        assertEquals(sampleRate, buf.getInt(24))
        assertEquals(sampleRate * 2, buf.getInt(28)) // byteRate = rate * 1ch * 16bit/8
        assertEquals(2.toShort(), buf.getShort(32)) // blockAlign
        assertEquals(16.toShort(), buf.getShort(34)) // bits per sample
    }

    @Test
    fun `data chunk size equals pcm size and payload is preserved`() {
        assertEquals(pcm.size, buf.getInt(40))
        assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
    }
}
