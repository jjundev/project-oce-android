package com.jjundev.oneclickeng.core.audio

/**
 * Plays a fully-decoded PCM buffer. Abstracted so the playback coordinator stays free of
 * Android framework types and is unit-testable with a fake.
 */
interface PcmPlayer {
    /**
     * Play 16-bit mono PCM at [sampleRateHz], suspending until playback completes.
     * The caller passes the server-declared sample rate (never a hardcoded 24kHz —
     * Gemini declares it per response, plan #4/#9). Throws on playback failure.
     */
    suspend fun play(
        pcm: ByteArray,
        sampleRateHz: Int,
    )

    /** Cancel any in-flight playback immediately. */
    fun stop()
}
