package com.jjundev.oneclickeng.feature.session.tts

/**
 * On-device Android TextToSpeech seam (the "fast" quality / fallback path). Abstracted so
 * the coordinator is testable without the framework engine.
 */
interface DeviceTts {
    /**
     * Speak [text] with a gender-matched voice at [speechRate], suspending until the
     * utterance terminates. Never throws — every outcome maps to a [DeviceTtsResult] so
     * the coordinator can branch exhaustively. [onStart] is invoked once when the
     * utterance's audio actually begins (after engine init); it never fires for
     * LANGUAGE_MISSING or an init error.
     */
    suspend fun speak(
        text: String,
        gender: String?,
        speechRate: Float,
        onStart: () -> Unit = {},
    ): DeviceTtsResult

    /** Stop any in-flight utterance. */
    fun stop()
}

/** Terminal outcomes of a device-TTS utterance (plan #12 — exhaustive). */
enum class DeviceTtsResult {
    /** utterance completed normally. */
    COMPLETED,

    /** English voice data not installed/supported (LANG_MISSING_DATA / LANG_NOT_SUPPORTED). */
    LANGUAGE_MISSING,

    /** engine not initialized, synthesis error, or any other failure. */
    ERROR,
}
