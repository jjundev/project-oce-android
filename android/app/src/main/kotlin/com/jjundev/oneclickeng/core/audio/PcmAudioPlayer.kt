package com.jjundev.oneclickeng.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
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
            speed: Float,
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
                // 마커는 *소스 프레임* 기준이라 배속을 걸어도 그대로 유효하다(벽시계만 짧아진다).
                audioTrack.setNotificationMarkerPosition(pcm.size / 2)
                applySpeed(audioTrack, speed)
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

        /**
         * 재생 배속 적용. [android.media.PlaybackParams] 는 피치를 보존하는 시간축 신축(Sonic)이라
         * 목소리 톤이 변하지 않는다. 기기가 지원하지 않는 값이면 IllegalArgumentException 을 던지므로,
         * 실패하면 배속 없이(1.0x) 재생을 이어간다 — 속도가 조금 틀린 게 무음보다 낫다.
         */
        private fun applySpeed(
            track: AudioTrack,
            speed: Float,
        ) {
            if (speed == 1.0f) return
            runCatching { track.playbackParams = track.playbackParams.setSpeed(speed) }
                .onFailure { Log.w(TAG, "device rejected playback speed $speed — falling back to 1.0x", it) }
        }

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

        private companion object {
            const val TAG = "PcmAudioPlayer"
        }
    }
