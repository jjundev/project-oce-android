package com.jjundev.oneclickeng.core.audio

/**
 * 최소 WAV(RIFF/PCM16 모노) 리더·라이터 — 발화 속도 계측 프로브 전용(`TtsCalibrationReceiver`).
 *
 * 왜 필요한가: 온디바이스 TTS 는 `synthesizeToFile` 로 **WAV** 를 내놓고(길이를 재려면 파싱해야 한다),
 * 서버는 헤더 없는 **raw PCM** 을 준다(사람이 들어 보려면 헤더를 씌워야 한다). 양방향이 다 필요하다.
 * 범용 코덱이 아니다 — 16-bit 모노만 다룬다.
 */

private const val HEADER_SIZE = 44
private const val PCM_FORMAT = 1
private const val CHANNELS = 1
private const val BITS_PER_SAMPLE = 16
private const val BYTES_PER_FRAME = 2

/** 파싱된 WAV 본문 — raw PCM16 과 그 샘플레이트. */
internal data class WavPcm(
    val pcm: ByteArray,
    val sampleRateHz: Int,
) {
    // ByteArray 를 든 data class 는 equals/hashCode 가 참조 비교라 직접 구현한다(detekt ArrayPrimitive 관례).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WavPcm) return false
        return sampleRateHz == other.sampleRateHz && pcm.contentEquals(other.pcm)
    }

    override fun hashCode(): Int = 31 * pcm.contentHashCode() + sampleRateHz
}

/**
 * RIFF 청크를 훑어 `fmt ` 의 샘플레이트와 `data` 본문을 뽑는다. WAV 가 아니거나 둘 중 하나가
 * 없으면 null. 마지막 `data` 청크의 선언 길이가 실제 버퍼를 넘으면(엔진이 스트리밍으로 쓴 파일)
 * 남은 바이트를 그대로 본문으로 쓴다.
 */
@Suppress("ReturnCount")
internal fun parseWavPcm16(bytes: ByteArray): WavPcm? {
    if (bytes.size < 12) return null
    if (ascii(bytes, 0) != "RIFF" || ascii(bytes, 8) != "WAVE") return null

    var sampleRate = 0
    var data: ByteArray? = null
    var pos = 12
    while (pos + 8 <= bytes.size) {
        val id = ascii(bytes, pos)
        val declared = readLeInt(bytes, pos + 4)
        val body = pos + 8
        if (declared < 0 || body + declared > bytes.size) {
            if (id == "data") data = bytes.copyOfRange(body, bytes.size)
            break
        }
        when (id) {
            // fmt 레이아웃: audioFormat(2) channels(2) sampleRate(4) …
            "fmt " -> if (body + 8 <= bytes.size) sampleRate = readLeInt(bytes, body + 4)
            "data" -> data = bytes.copyOfRange(body, body + declared)
        }
        pos = body + declared + (declared and 1) // 청크는 워드 정렬 — 홀수 길이면 패딩 1바이트
    }

    val pcm = data ?: return null
    if (sampleRate <= 0) return null
    return WavPcm(pcm, sampleRate)
}

/** raw PCM16 모노에 44바이트 표준 헤더를 씌운다(사람이 들어 볼 수 있게). */
internal fun encodeWavPcm16(
    pcm: ByteArray,
    sampleRateHz: Int,
): ByteArray {
    require(sampleRateHz > 0) { "sampleRateHz must be positive, was $sampleRateHz" }
    val byteRate = sampleRateHz * CHANNELS * BYTES_PER_FRAME
    val out = ByteArray(HEADER_SIZE + pcm.size)

    writeAscii(out, 0, "RIFF")
    writeLeInt(out, 4, HEADER_SIZE - 8 + pcm.size)
    writeAscii(out, 8, "WAVE")
    writeAscii(out, 12, "fmt ")
    writeLeInt(out, 16, 16) // PCM fmt 청크 길이
    writeLeShort(out, 20, PCM_FORMAT)
    writeLeShort(out, 22, CHANNELS)
    writeLeInt(out, 24, sampleRateHz)
    writeLeInt(out, 28, byteRate)
    writeLeShort(out, 32, CHANNELS * BYTES_PER_FRAME) // block align
    writeLeShort(out, 34, BITS_PER_SAMPLE)
    writeAscii(out, 36, "data")
    writeLeInt(out, 40, pcm.size)
    pcm.copyInto(out, HEADER_SIZE)
    return out
}

private fun ascii(
    bytes: ByteArray,
    at: Int,
): String = String(bytes, at, 4, Charsets.US_ASCII)

private fun readLeInt(
    bytes: ByteArray,
    at: Int,
): Int =
    (bytes[at].toInt() and 0xFF) or
        ((bytes[at + 1].toInt() and 0xFF) shl 8) or
        ((bytes[at + 2].toInt() and 0xFF) shl 16) or
        ((bytes[at + 3].toInt() and 0xFF) shl 24)

private fun writeAscii(
    out: ByteArray,
    at: Int,
    value: String,
) {
    value.toByteArray(Charsets.US_ASCII).copyInto(out, at)
}

private fun writeLeInt(
    out: ByteArray,
    at: Int,
    value: Int,
) {
    for (i in 0 until 4) out[at + i] = ((value shr (8 * i)) and 0xFF).toByte()
}

private fun writeLeShort(
    out: ByteArray,
    at: Int,
    value: Int,
) {
    for (i in 0 until 2) out[at + i] = ((value shr (8 * i)) and 0xFF).toByte()
}
