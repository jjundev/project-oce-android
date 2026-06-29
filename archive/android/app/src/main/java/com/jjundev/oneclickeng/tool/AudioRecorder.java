package com.jjundev.oneclickeng.tool;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

/** 오디오 녹음 관리 클래스 16-bit PCM, 16kHz, mono 포맷으로 녹음 */
public class AudioRecorder {

  private static final String TAG = "AudioRecorder";

  // 오디오 설정 (Gemini Live API 요구사항)
  private static final int SAMPLE_RATE = 16000; // 16kHz
  private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
  private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
  private static final int CHUNK_SIZE = 1024; // 청크당 바이트 수

  private AudioRecord audioRecord;
  private volatile boolean isRecording = false;
  private Thread recordingThread;
  private volatile AudioCallback callback;

  /** 오디오 데이터 콜백 인터페이스 */
  public interface AudioCallback {
    void onAudioData(byte[] audioData);

    void onError(String error);
  }

  /** 권한 확인 */
  public static boolean hasRecordPermission(Fragment fragment) {
    return ContextCompat.checkSelfPermission(
            fragment.requireContext(), Manifest.permission.RECORD_AUDIO)
        == PackageManager.PERMISSION_GRANTED;
  }

  /** 녹음 시작 */
  public synchronized void startRecording(AudioCallback callback) {
    stopRecording();
    this.callback = callback;

    int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
    if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
      Log.e(TAG, "버퍼 크기 계산 실패");
      if (callback != null) {
        callback.onError("오디오 초기화 실패");
      }
      return;
    }

    // 버퍼 크기를 최소값보다 크게 설정
    int bufferSize = Math.max(minBufferSize * 2, CHUNK_SIZE * 4);

    try {
      audioRecord =
          new AudioRecord(
              MediaRecorder.AudioSource.VOICE_RECOGNITION,
              SAMPLE_RATE,
              CHANNEL_CONFIG,
              AUDIO_FORMAT,
              bufferSize);

      if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
        Log.e(TAG, "AudioRecord 초기화 실패");
        if (callback != null) {
          callback.onError("마이크 초기화 실패");
        }
        return;
      }

      audioRecord.startRecording();
      isRecording = true;

      // 별도 스레드에서 녹음 처리
      recordingThread = new Thread(this::recordLoop, "AudioRecordThread");
      recordingThread.start();

      Log.d(TAG, "녹음 시작됨");

    } catch (SecurityException e) {
      Log.e(TAG, "권한 없음", e);
      if (callback != null) {
        callback.onError("마이크 권한이 필요합니다");
      }
    } catch (Exception e) {
      Log.e(TAG, "녹음 시작 실패", e);
      if (callback != null) {
        callback.onError("녹음 시작 실패: " + e.getMessage());
      }
    }
  }

  /** 녹음 루프 (별도 스레드에서 실행) */
  private void recordLoop() {
    byte[] buffer = new byte[CHUNK_SIZE];

    while (isRecording) {
      AudioRecord currentRecord = audioRecord;
      if (currentRecord == null) {
        break;
      }
      int bytesRead = currentRecord.read(buffer, 0, CHUNK_SIZE);

      if (bytesRead > 0) {
        AudioCallback currentCallback = callback;
        if (currentCallback != null) {
          // 읽은 만큼만 복사
          byte[] audioData = new byte[bytesRead];
          System.arraycopy(buffer, 0, audioData, 0, bytesRead);
          currentCallback.onAudioData(audioData);
        }
      } else if (bytesRead == AudioRecord.ERROR_INVALID_OPERATION
          || bytesRead == AudioRecord.ERROR_BAD_VALUE) {
        Log.e(TAG, "오디오 읽기 오류: " + bytesRead);
        AudioCallback currentCallback = callback;
        if (currentCallback != null) {
          currentCallback.onError("오디오 읽기 오류: " + bytesRead);
        }
        break;
      }
    }

    Log.d(TAG, "녹음 루프 종료");
  }

  /** 녹음 중지 */
  public synchronized void stopRecording() {
    isRecording = false;
    Thread threadToJoin = recordingThread;
    AudioRecord recordToRelease = audioRecord;
    callback = null;
    recordingThread = null;
    audioRecord = null;

    if (threadToJoin != null) {
      try {
        threadToJoin.join(1000); // 최대 1초 대기
      } catch (InterruptedException e) {
        Log.w(TAG, "녹음 스레드 대기 중 인터럽트됨");
        Thread.currentThread().interrupt();
      }
    }

    if (recordToRelease != null) {
      try {
        if (recordToRelease.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
          recordToRelease.stop();
        }
        recordToRelease.release();
      } catch (Exception e) {
        Log.e(TAG, "AudioRecord 해제 중 오류", e);
      }
    }

    Log.d(TAG, "녹음 중지됨");
  }

  /** 녹음 상태 확인 */
  public boolean isRecording() {
    return isRecording;
  }
}
