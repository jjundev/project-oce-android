package com.jjundev.oneclickeng.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * AudioTrack MODE_STATIC player — Kotlin/coroutine port of the archived
 * `RecordingAudioPlayer`. Unlike the archive (whose default rate was 16kHz, wrong for
 * TTS), the sample rate is always supplied by the caller from the server response, so
 * there is no rate default to misfire (plan #9). Completion is driven by AudioTrack's
 * notification marker; a monotonic token guards against a stale marker resuming a newer
 * playback.
 */
@Singleton
class PcmAudioPlayer
    @Inject
    constructor() : PcmPlayer {
        private val lock = Any()
        private var track: AudioTrack? = null
        private val playbackToken = AtomicLong(0)

        @Suppress("TooGenericExceptionCaught")
        override suspend fun play(
            pcm: ByteArray,
            sampleRateHz: Int,
        ) {
            stop()
            val token = playbackToken.incrementAndGet()
            require(pcm.isNotEmpty()) { "empty PCM buffer" }

            suspendCancellableCoroutine { cont ->
                val audioTrack =
                    try {
                        buildTrack(pcm, sampleRateHz)
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                        return@suspendCancellableCoroutine
                    }

                synchronized(lock) { track = audioTrack }

                // 2 bytes per 16-bit sample — marker at the last frame signals completion.
                audioTrack.setNotificationMarkerPosition(pcm.size / 2)
                audioTrack.setPlaybackPositionUpdateListener(
                    object : AudioTrack.OnPlaybackPositionUpdateListener {
                        override fun onMarkerReached(t: AudioTrack) {
                            if (token == playbackToken.get() && cont.isActive) {
                                releaseTrack()
                                cont.resume(Unit)
                            }
                        }

                        override fun onPeriodicNotification(t: AudioTrack) = Unit
                    },
                )

                try {
                    audioTrack.write(pcm, 0, pcm.size)
                    audioTrack.play()
                } catch (e: Exception) {
                    releaseTrack()
                    if (cont.isActive) cont.resumeWithException(e)
                    return@suspendCancellableCoroutine
                }

                cont.invokeOnCancellation {
                    playbackToken.incrementAndGet()
                    releaseTrack()
                }
            }
        }

        override fun stop() {
            playbackToken.incrementAndGet()
            releaseTrack()
        }

        private fun buildTrack(
            pcm: ByteArray,
            sampleRateHz: Int,
        ): AudioTrack =
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRateHz)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(pcm.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

        private fun releaseTrack() {
            val toRelease: AudioTrack?
            synchronized(lock) {
                toRelease = track
                track = null
            }
            toRelease?.runCatching {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) stop()
                setPlaybackPositionUpdateListener(null)
                release()
            }
        }
    }
