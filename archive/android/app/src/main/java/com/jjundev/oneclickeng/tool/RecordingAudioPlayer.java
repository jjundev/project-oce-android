package com.jjundev.oneclickeng.tool;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** 16kHz, 16-bit, Mono PCM 녹음 재생 전용 클래스 AudioTrack MODE_STATIC 사용 (전체 오디오가 메모리에 있으므로) */
public class RecordingAudioPlayer {

  private static final String TAG = "RecordingAudioPlayer";
  private static final int DEFAULT_SAMPLE_RATE = 16000;
  private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO;
  private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

  private volatile AudioTrack audioTrack;
  private volatile boolean isPlaying = false;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final Object trackLock = new Object();
  private volatile PlaybackCallback playbackCallback;
  private long playbackToken = 0L;

  public interface PlaybackCallback {
    void onPlaybackCompleted();

    void onPlaybackError(String error);
  }

  /** PCM 오디오 재생 시작 (기존 재생 중이면 자동 중지) */
  public void play(byte[] pcmData, PlaybackCallback callback) {
    play(pcmData, DEFAULT_SAMPLE_RATE, callback);
  }

  /** PCM 오디오 재생 시작 (샘플레이트 지정 가능) */
  public void play(byte[] pcmData, int sampleRateHz, PlaybackCallback callback) {
    final PlaybackCallback resolvedCallback = callback;
    final long requestToken;
    final int resolvedSampleRate = sampleRateHz > 0 ? sampleRateHz : DEFAULT_SAMPLE_RATE;

    // Stop previous playback first so the new request token remains valid.
    stop();
    synchronized (trackLock) {
      requestToken = ++playbackToken;
      playbackCallback = resolvedCallback;
    }

    if (pcmData == null || pcmData.length == 0) {
      if (resolvedCallback != null) {
        resolvedCallback.onPlaybackError("오디오 데이터 없음");
      }
      return;
    }

    try {
      int bufferSize = pcmData.length;

      audioTrack =
          new AudioTrack.Builder()
              .setAudioAttributes(
                  new AudioAttributes.Builder()
                      .setUsage(AudioAttributes.USAGE_MEDIA)
                      .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                      .build())
              .setAudioFormat(
                  new AudioFormat.Builder()
                      .setEncoding(AUDIO_FORMAT)
                      .setSampleRate(resolvedSampleRate)
                      .setChannelMask(CHANNEL_CONFIG)
                      .build())
              .setBufferSizeInBytes(bufferSize)
              .setTransferMode(AudioTrack.MODE_STATIC)
              .build();

      audioTrack.write(pcmData, 0, pcmData.length);

      audioTrack.setNotificationMarkerPosition(pcmData.length / 2); // 2 bytes per sample
      audioTrack.setPlaybackPositionUpdateListener(
          new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onMarkerReached(AudioTrack track) {
              mainHandler.post(
                  () -> {
                    synchronized (trackLock) {
                      if (requestToken != playbackToken) {
                        return;
                      }
                    }
                    isPlaying = false;
                    releaseTrack();
                    synchronized (trackLock) {
                      PlaybackCallback current = playbackCallback;
                      if (requestToken == playbackToken && current != null) {
                        current.onPlaybackCompleted();
                        playbackCallback = null;
                      }
                    }
                  });
            }

            @Override
            public void onPeriodicNotification(AudioTrack track) {}
          });

      audioTrack.play();
      synchronized (trackLock) {
        isPlaying = true;
      }

    } catch (Exception e) {
      Log.e(TAG, "재생 오류", e);
      isPlaying = false;
      releaseTrack();
      synchronized (trackLock) {
        if (requestToken == playbackToken && resolvedCallback != null) {
          resolvedCallback.onPlaybackError(e.getMessage());
        }
      }
    }
  }

  /** 재생 중지 */
  public void stop() {
    synchronized (trackLock) {
      playbackToken++;
      isPlaying = false;
      playbackCallback = null;
    }
    releaseTrack();
  }

  /** 재생 상태 확인 */
  public boolean isPlaying() {
    return isPlaying;
  }

  private void releaseTrack() {
    AudioTrack trackToRelease;
    synchronized (trackLock) {
      trackToRelease = audioTrack;
      audioTrack = null;
    }

    if (trackToRelease != null) {
      try {
        if (trackToRelease.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
          trackToRelease.stop();
        }
        trackToRelease.setPlaybackPositionUpdateListener(null);
        trackToRelease.release();
      } catch (Exception e) {
        Log.e(TAG, "AudioTrack 해제 오류", e);
      }
    }
  }
}
