package com.jjundev.oneclickeng.feature.session.tts

/**
 * Playback state of the opponent's current turn. Exhaustive so the UI's `when` is
 * total (plan #12).
 */
enum class PlaybackState {
    /** nothing playing. */
    IDLE,

    /** synthesizing / waiting on the server (before audio starts). */
    LOADING,

    /** audio is playing. */
    PLAYING,

    /**
     * English voice data missing/unsupported on device — no audio possible. Show the
     * line as text with a retry affordance; does NOT auto-advance (tts.md §4).
     */
    ERROR_TEXT_ONLY,

    /**
     * Generic playback failure (server + device both failed, or a watchdog fired). The
     * line stays visible as text and the session auto-advances (FR-7) so it never stalls.
     */
    FAILED,
}
