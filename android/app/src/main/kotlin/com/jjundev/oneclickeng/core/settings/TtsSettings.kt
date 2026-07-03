package com.jjundev.oneclickeng.core.settings

/**
 * TTS user settings (tts.md §5). The UI to edit these lands in M3-09; M1-05 provides the
 * store + defaults and reads them during playback.
 *
 * - [quality]: the "음질 토글" reframed as quality. Default SERVER (natural, slightly
 *   slower) — this deliberately flips the archive default of "android" (tts.md:27).
 * - [speechRate]: best-effort speaking rate, clamped 0.5–1.5 (tts.md:12).
 * - [muted]: global mute — playback is skipped entirely.
 */
data class TtsSettings(
    val quality: TtsQuality = TtsQuality.SERVER,
    val speechRate: Float = DEFAULT_SPEECH_RATE,
    val muted: Boolean = false,
) {
    companion object {
        const val DEFAULT_SPEECH_RATE = 1.0f
        const val MIN_SPEECH_RATE = 0.5f
        const val MAX_SPEECH_RATE = 1.5f
    }
}

enum class TtsQuality {
    /** server (Gemini) synthesis — natural, slightly slower. */
    SERVER,

    /** on-device Android TTS — faster, lower quality. */
    DEVICE,
}
