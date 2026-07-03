package com.jjundev.oneclickeng.core.audio

/**
 * [RecordingController.stop] 의 종결 결과.
 *
 * - [Captured]: 유효 발화. raw PCM16 을 그대로 보관하며, 전송용 WAV 래핑은
 *   호출부가 [WavEncoder.wrap] 로 필요 시 수행한다(실제 전송은 M1-06).
 * - [TooQuiet]: 무음 게이트 탈락. 전송·업로드 없이 재시도 신호로 쓰인다.
 *   화면에 보이는 "다시 말해볼까요" 재시도 UX 는 M1-08(MicState=Ready) 소관.
 * - [Failed]: 마이크 초기화/읽기 실패.
 */
sealed interface RecordingResult {
    data class Captured(
        val pcm: ByteArray,
        val sampleRate: Int,
        val durationMs: Long,
    ) : RecordingResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Captured) return false
            return sampleRate == other.sampleRate &&
                durationMs == other.durationMs &&
                pcm.contentEquals(other.pcm)
        }

        override fun hashCode(): Int {
            var result = pcm.contentHashCode()
            result = 31 * result + sampleRate
            result = 31 * result + durationMs.hashCode()
            return result
        }
    }

    data object TooQuiet : RecordingResult

    data class Failed(val error: AudioError) : RecordingResult
}
