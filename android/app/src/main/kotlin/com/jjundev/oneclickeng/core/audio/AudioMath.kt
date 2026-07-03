package com.jjundev.oneclickeng.core.audio

import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 오디오 파형·무음 게이트의 순수 계산(디바이스 API 무관 → JVM 유닛 테스트 대상).
 *
 * 정본: [docs/design/audio-pipeline.md] §5·§6, 레거시 `RecordedAudioSilenceDetector`·`WaveformView`.
 * 임계값(0.02/0.1/0.05)·gain(3.0)·floor(0.05)는 단말 편차로 운영 보정하는 상수다.
 */
object AudioMath {
    /** 파형 막대 개수(crackle 40바). */
    const val BAR_COUNT = 40

    /** 막대 최소 진폭(floor). */
    const val WAVEFORM_FLOOR = 0.05f

    /** 막대 최대 진폭. */
    const val WAVEFORM_CEIL = 1.0f

    /** RMS → 표시 레벨 게인. */
    const val WAVEFORM_GAIN = 3.0f

    /** 막대별 crackle 지터 폭: ±(rand-0.5)*JITTER_SPAN = ±0.3. */
    const val JITTER_SPAN = 0.6f

    /** voiced 로 간주하는 청크 RMS 임계. */
    const val VOICED_RMS_THRESHOLD = 0.02f

    /** TooQuiet 판정: voiced 청크 비율 하한. */
    const val MIN_VOICED_FRACTION = 0.1f

    /** TooQuiet 판정: 피크 청크 RMS 하한. */
    const val MIN_PEAK_RMS = 0.05f

    private const val PCM16_FULL_SCALE = 32768.0
    private const val BYTES_PER_SAMPLE = 2

    /**
     * 리틀엔디안 16-bit PCM 청크의 정규화 RMS([0,1]).
     *
     * @param chunk PCM16 바이트
     * @param length 유효 바이트 길이(read() short-read 대비 — 기본 전체)
     */
    fun normalizedRms(
        chunk: ByteArray,
        length: Int = chunk.size,
    ): Float {
        val usable = length.coerceIn(0, chunk.size)
        val sampleCount = usable / BYTES_PER_SAMPLE
        if (sampleCount <= 0) return 0f

        var sumSquares = 0.0
        var i = 0
        while (i + 1 < usable) {
            val sample = ((chunk[i].toInt() and 0xFF) or (chunk[i + 1].toInt() shl 8)).toShort()
            val normalized = sample / PCM16_FULL_SCALE
            sumSquares += normalized * normalized
            i += BYTES_PER_SAMPLE
        }
        return sqrt(sumSquares / sampleCount).toFloat()
    }

    /** RMS > [threshold] 인 청크 비율([0,1]). */
    fun voicedFraction(
        chunkRms: List<Float>,
        threshold: Float = VOICED_RMS_THRESHOLD,
    ): Float {
        if (chunkRms.isEmpty()) return 0f
        val voiced = chunkRms.count { it > threshold }
        return voiced.toFloat() / chunkRms.size
    }

    /**
     * 무음 게이트(정지 후). 전체-버퍼 평균이 아닌 청크 통계 사용(선·후행 무음 희석 방지).
     * voiced 비율이 낮거나 피크가 없으면 TooQuiet.
     */
    fun isTooQuiet(chunkRms: List<Float>): Boolean {
        if (chunkRms.isEmpty()) return true
        val maxRms = chunkRms.max()
        return voicedFraction(chunkRms) < MIN_VOICED_FRACTION || maxRms < MIN_PEAK_RMS
    }

    /** 청크 RMS → 표시 레벨: gain 적용 후 [floor, 1] clamp. */
    fun levelFrom(rms: Float): Float = (rms * WAVEFORM_GAIN).coerceIn(WAVEFORM_FLOOR, WAVEFORM_CEIL)

    /**
     * crackle 한 프레임([BAR_COUNT] 값). 전 막대를 현재 [level] 로 세팅하고
     * 막대별 독립 지터 ±(rand-0.5)*[JITTER_SPAN] 를 더해 [floor,1] clamp(비스크롤).
     *
     * @param rng 테스트 결정성을 위해 주입 가능(기본 [Random.Default]).
     */
    fun waveformFrame(
        level: Float,
        rng: Random = Random.Default,
    ): FloatArray =
        FloatArray(BAR_COUNT) {
            val variation = (rng.nextFloat() - 0.5f) * JITTER_SPAN
            (level + variation).coerceIn(WAVEFORM_FLOOR, WAVEFORM_CEIL)
        }

    /** 정적/초기 프레임(전 막대 = floor). */
    fun floorFrame(): FloatArray = FloatArray(BAR_COUNT) { WAVEFORM_FLOOR }
}
