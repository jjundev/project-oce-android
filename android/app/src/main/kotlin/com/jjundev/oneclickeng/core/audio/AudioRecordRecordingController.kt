package com.jjundev.oneclickeng.core.audio

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * [RecordingController] 의 AudioRecord 구현.
 *
 * [Dispatchers.IO] 단일 캡처 루프에서 1024B 청크를 읽어 PCM 을 누적하고,
 * 청크별 정규화 RMS 로 [waveform] 을 갱신한다. 20초 캡 도달 시 캡처를 자동 종료하며
 * (버퍼 동결), 실제 결과 확정은 [stop] 이 소유한다. 라이프사이클 정리 시 mic 를 해제한다.
 *
 * 근거: [docs/design/audio-pipeline.md] §5·§10, 레거시 `AudioRecorder.java`.
 */
@Singleton
class AudioRecordRecordingController
    @Inject
    constructor() : RecordingController {
        private val _waveform = MutableStateFlow(AudioMath.floorFrame())
        override val waveform: StateFlow<FloatArray> = _waveform.asStateFlow()

        private val _state = MutableStateFlow(RecordingState.Idle)
        override val state: StateFlow<RecordingState> = _state.asStateFlow()

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val pcmStream = ByteArrayOutputStream()
        private val chunkRms = mutableListOf<Float>()
        private val rng: Random = Random.Default

        @Volatile private var capturing = false

        @Volatile private var readError: Int? = null
        private var recordJob: Job? = null
        private var audioRecord: AudioRecord? = null

        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        override suspend fun start() {
            releaseInternal()
            val record = createRecord()
            audioRecord = record
            pcmStream.reset()
            chunkRms.clear()
            readError = null
            _waveform.value = AudioMath.floorFrame()

            try {
                record.startRecording()
            } catch (e: IllegalStateException) {
                record.release()
                audioRecord = null
                throw AudioCaptureException(AudioError.AudioInitError, e)
            }

            capturing = true
            _state.value = RecordingState.Recording
            recordJob = scope.launch { captureLoop(record) }
        }

        override suspend fun stop(): RecordingResult {
            capturing = false
            recordJob?.join()
            recordJob = null
            releaseRecord()
            _state.value = RecordingState.Idle
            _waveform.value = AudioMath.floorFrame()

            val error = readError
            return when {
                error != null -> RecordingResult.Failed(AudioError.ReadError(error))
                AudioMath.isTooQuiet(chunkRms) -> RecordingResult.TooQuiet
                else -> {
                    val pcm = pcmStream.toByteArray()
                    val durationMs = pcm.size.toLong() * MILLIS_PER_SECOND / (SAMPLE_RATE * BYTES_PER_SAMPLE)
                    RecordingResult.Captured(pcm, SAMPLE_RATE, durationMs)
                }
            }
        }

        private fun CoroutineScope.captureLoop(record: AudioRecord) {
            val buffer = ByteArray(CHUNK_SIZE)
            var capturedBytes = 0
            while (capturing && isActive) {
                val read = record.read(buffer, 0, CHUNK_SIZE)
                when {
                    read > 0 -> {
                        pcmStream.write(buffer, 0, read)
                        capturedBytes += read
                        val rms = AudioMath.normalizedRms(buffer, read)
                        chunkRms.add(rms)
                        _waveform.value = AudioMath.waveformFrame(AudioMath.levelFrom(rms), rng)
                        if (capturedBytes >= MAX_CAPTURE_BYTES) capturing = false
                    }
                    read < 0 -> {
                        readError = read
                        capturing = false
                    }
                    else -> Unit // 0바이트: 다음 루프
                }
            }
        }

        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
        private fun createRecord(): AudioRecord {
            val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (minBuffer == AudioRecord.ERROR_BAD_VALUE || minBuffer == AudioRecord.ERROR) {
                audioInitError()
            }
            val bufferSize = maxOf(minBuffer * 2, CHUNK_SIZE * 4)
            val record =
                try {
                    AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize,
                    )
                } catch (e: IllegalArgumentException) {
                    audioInitError(e)
                }
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                audioInitError()
            }
            return record
        }

        private fun audioInitError(cause: Throwable? = null): Nothing {
            throw AudioCaptureException(AudioError.AudioInitError, cause)
        }

        private suspend fun releaseInternal() {
            capturing = false
            recordJob?.join()
            recordJob = null
            releaseRecord()
        }

        @Suppress("SwallowedException")
        private fun releaseRecord() {
            val record = audioRecord ?: return
            audioRecord = null
            try {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            } catch (e: IllegalStateException) {
                // 이미 정지/미초기화 상태 — 해제만 보장하면 되므로 무시한다.
            }
            record.release()
        }

        private companion object {
            const val SAMPLE_RATE = 16_000
            const val CHUNK_SIZE = 1024
            const val BYTES_PER_SAMPLE = 2
            const val MAX_RECORDING_SECONDS = 20
            const val MILLIS_PER_SECOND = 1000
            const val MAX_CAPTURE_BYTES = SAMPLE_RATE * BYTES_PER_SAMPLE * MAX_RECORDING_SECONDS
            val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
            val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        }
    }
