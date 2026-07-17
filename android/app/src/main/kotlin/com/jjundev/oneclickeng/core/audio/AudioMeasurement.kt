package com.jjundev.oneclickeng.core.audio

import kotlin.math.abs

/**
 * 발화 속도 보정 계수를 실측하기 위한 오디오 계측(디버그 프로브 전용 — `TtsCalibrationReceiver`).
 *
 * 속도를 "듣지 않고" 재는 방법: **같은 문장**을 중립 속도로 합성해 길이(초)를 비교한다. 같은 텍스트라면
 * 길이는 발화 속도에 정확히 반비례하므로, 길이의 비가 곧 속도의 비다. 단, 원시 길이는 엔진이 붙이는
 * 앞뒤 무음 패딩까지 세므로 반드시 트림한 뒤 비교해야 한다.
 */

/** 발화로 칠 최소 진폭 — 16-bit full-scale 의 약 1%. 이 아래는 룸톤/패딩으로 본다. */
private const val SILENCE_THRESHOLD = 328

/** 트림 판정 윈도우(ms). 샘플 하나의 스파이크로 무음이 깨지지 않게 묶어서 본다. */
private const val WINDOW_MS = 10

private const val MS_PER_SECOND = 1000

/**
 * [pcm](16-bit LE 모노)에서 앞뒤 무음을 뺀 실제 발화 길이(초).
 * 전부 무음이거나 비었으면 0.0.
 */
@Suppress("ReturnCount")
internal fun trimmedDurationSeconds(
    pcm: ByteArray,
    sampleRateHz: Int,
): Double {
    require(sampleRateHz > 0) { "sampleRateHz must be positive, was $sampleRateHz" }
    val frames = pcm.size / 2
    if (frames == 0) return 0.0

    val window = (sampleRateHz * WINDOW_MS / MS_PER_SECOND).coerceAtLeast(1)
    var firstVoiced = -1
    var lastVoiced = -1
    var frame = 0
    while (frame < frames) {
        val end = minOf(frame + window, frames)
        if (peakAmplitude(pcm, frame, end) >= SILENCE_THRESHOLD) {
            if (firstVoiced < 0) firstVoiced = frame
            lastVoiced = end
        }
        frame = end
    }
    if (firstVoiced < 0) return 0.0
    return (lastVoiced - firstVoiced).toDouble() / sampleRateHz
}

/** `[fromFrame, toFrame)` 구간의 최대 절대 진폭. */
private fun peakAmplitude(
    pcm: ByteArray,
    fromFrame: Int,
    toFrame: Int,
): Int {
    var peak = 0
    for (frame in fromFrame until toFrame) {
        // little-endian s16: 하위 바이트는 부호 없이, 상위 바이트는 부호 확장해서 합친다.
        val low = pcm[frame * 2].toInt() and 0xFF
        val high = pcm[frame * 2 + 1].toInt()
        val magnitude = abs((high shl 8) or low)
        if (magnitude > peak) peak = magnitude
    }
    return peak
}

/**
 * 보정 계수 `w = D_source / D_reference`.
 *
 * 기준보다 느린 소스는 같은 문장에 더 오래 걸리므로 `w > 1` 이 되고, 재생이 그만큼 빨라져 기준에 붙는다.
 * 두 길이 모두 **같은 문장**을 **중립 속도**로 합성해 [trimmedDurationSeconds] 로 잰 값이어야 한다.
 */
internal fun calibrationWeight(
    sourceSeconds: Double,
    referenceSeconds: Double,
): Float {
    require(sourceSeconds > 0.0) { "sourceSeconds must be positive, was $sourceSeconds" }
    require(referenceSeconds > 0.0) { "referenceSeconds must be positive, was $referenceSeconds" }
    return (sourceSeconds / referenceSeconds).toFloat()
}
