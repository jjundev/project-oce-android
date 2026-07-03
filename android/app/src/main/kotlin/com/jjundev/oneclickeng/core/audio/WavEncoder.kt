package com.jjundev.oneclickeng.core.audio

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * raw PCM16 → 무압축 WAV(RIFF) 컨테이너 래퍼(순수 함수).
 *
 * 전송 계약(`/llm task=speaking`)은 16kHz·16bit·mono WAV 를 요구한다
 * ([docs/design/audio-pipeline.md] §9). 실제 전송은 M1-06 이 수행하고,
 * M1-04 는 래핑 유틸만 제공한다.
 */
object WavEncoder {
    private const val HEADER_SIZE = 44
    private const val BITS_PER_SAMPLE = 16
    private const val PCM_FORMAT = 1.toShort() // 1 = 무압축 PCM
    private const val MONO = 1.toShort()

    /** [pcm] 앞에 44바이트 WAV 헤더를 붙여 반환한다. */
    fun wrap(
        pcm: ByteArray,
        sampleRate: Int,
        channels: Int = MONO.toInt(),
    ): ByteArray {
        val byteRate = sampleRate * channels * BITS_PER_SAMPLE / 8
        val blockAlign = (channels * BITS_PER_SAMPLE / 8).toShort()
        val dataSize = pcm.size
        val riffSize = HEADER_SIZE - 8 + dataSize

        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.put('R'.code.toByte()).put('I'.code.toByte()).put('F'.code.toByte()).put('F'.code.toByte())
        header.putInt(riffSize)
        header.put('W'.code.toByte()).put('A'.code.toByte()).put('V'.code.toByte()).put('E'.code.toByte())
        header.put('f'.code.toByte()).put('m'.code.toByte()).put('t'.code.toByte()).put(' '.code.toByte())
        header.putInt(BITS_PER_SAMPLE) // fmt 청크 길이(16)
        header.putShort(PCM_FORMAT)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign)
        header.putShort(BITS_PER_SAMPLE.toShort())
        header.put('d'.code.toByte()).put('a'.code.toByte()).put('t'.code.toByte()).put('a'.code.toByte())
        header.putInt(dataSize)

        return ByteArrayOutputStream(HEADER_SIZE + dataSize).apply {
            write(header.array())
            write(pcm)
        }.toByteArray()
    }
}
