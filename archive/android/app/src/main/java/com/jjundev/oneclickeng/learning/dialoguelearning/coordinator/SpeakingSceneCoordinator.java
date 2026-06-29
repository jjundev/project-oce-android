package com.jjundev.oneclickeng.learning.dialoguelearning.coordinator;

import android.os.SystemClock;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.learning.dialoguelearning.controller.RecordedAudioSilenceDetector;
import com.jjundev.oneclickeng.learning.dialoguelearning.controller.SpeakingFlowController;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.BottomSheetSceneRenderer;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.LearningScene;
import com.jjundev.oneclickeng.tool.AudioRecorder;
import com.jjundev.oneclickeng.view.WaveformView;
import java.io.ByteArrayOutputStream;
import java.util.Locale;

public final class SpeakingSceneCoordinator {

  public interface Host {
    boolean isMicPermissionGranted();

    boolean onWhileSpeakingPermissionCheck(
        @NonNull String sentenceToTranslate, boolean hasPermission);

    void onRecordingStarted();

    void onRecordingStopRequestedOrAnalyzeFallback(
        @NonNull String sentenceToTranslate, @NonNull byte[] audioData);

    boolean isSpeaking();

    boolean isRecordingActive();

    void stopSpeakingSessionState(@NonNull String reason);

    void safeStopPlayback(@NonNull String reason);

    void safeStopRecording(@NonNull String reason);

    @Nullable
    AudioRecorder getAudioRecorderManager();

    @NonNull
    android.os.Handler getMainHandler();

    @Nullable
    ByteArrayOutputStream getAudioAccumulator();

    @Nullable
    StringBuilder getTranscriptBuilder();

    void onAudioChunk(@NonNull byte[] audioData);

    void setLastRecordedAudio(@NonNull byte[] audioData);

    void renderBottomSheetScene(
        @LayoutRes int layoutResId, @Nullable BottomSheetSceneRenderer.SheetBinder binder);

    void markRenderedScene(@NonNull LearningScene scene, @Nullable String sentenceToRender);

    void renderWhileSpeakingContent(@NonNull View content, @NonNull String sentenceToTranslate);

    void startProgressAnimation();

    void stopProgressAnimation();

    void startRippleAnimation();

    void stopRippleAnimation();

    void onSpeakingViewsBound(
        @Nullable ImageButton btnMicCircle,
        @Nullable ProgressBar progressRing,
        @Nullable ProgressBar loadingSpinner,
        @Nullable TextView tvListeningStatus,
        @Nullable WaveformView waveformView,
        @Nullable View ripple1,
        @Nullable View ripple2,
        @Nullable View ripple3);

    void onSpeakingViewsCleared();

    long getLastWaveformUpdateMs();

    void setLastWaveformUpdateMs(long value);

    void trace(@NonNull String key);

    void gate(@NonNull String key);

    void ux(@NonNull String key, @Nullable String fields);

    boolean tryPresentBeforeSpeakingSceneAfterNoAudio(@NonNull String sentenceToTranslate);
  }

  private static final long WAVEFORM_UPDATE_INTERVAL_MS = 80L;
  private static final float NO_AUDIO_RMS_THRESHOLD = 0.03f;
  private static final long NO_AUDIO_RETURN_DELAY_MS = 1000L;

  @NonNull private final Host host;

  @Nullable private ImageButton btnMicCircle;
  @Nullable private ProgressBar progressRing;
  @Nullable private ProgressBar loadingSpinner;
  @Nullable private TextView tvListeningStatus;
  @Nullable private WaveformView waveformView;
  @Nullable private View ripple1;
  @Nullable private View ripple2;
  @Nullable private View ripple3;
  @Nullable private Runnable pendingNoAudioBeforeSpeakingPresentation;

  public SpeakingSceneCoordinator(@NonNull Host host) {
    this.host = host;
  }

  public void presentWhileSpeakingScene(@NonNull String sentenceToTranslate) {
    cancelPendingNoAudioBeforeSpeakingPresentation();
    if (host.isRecordingActive() || host.isSpeaking()) {
      return;
    }
    host.trace("TRACE_SCENE_TRANSITION scene=WHILE_SPEAKING");
    boolean hasPermission = host.isMicPermissionGranted();
    if (!host.onWhileSpeakingPermissionCheck(sentenceToTranslate, hasPermission)) {
      return;
    }
    host.trace("TRACE_RECORD_PERMISSION_GRANTED scene=WHILE_SPEAKING");
    host.onRecordingStarted();

    host.safeStopPlayback("presentWhileSpeakingScene_start");

    host.renderBottomSheetScene(
        R.layout.bottom_sheet_content_while_speaking,
        content -> {
          host.markRenderedScene(LearningScene.WHILE_SPEAKING, sentenceToTranslate);

          btnMicCircle = content.findViewById(R.id.btn_mic_circle);
          progressRing = content.findViewById(R.id.progress_ring);
          loadingSpinner = content.findViewById(R.id.loading_spinner);
          tvListeningStatus = content.findViewById(R.id.tv_listening_status);
          TextView tvOriginalSentence = content.findViewById(R.id.tv_original_sentence);
          waveformView = content.findViewById(R.id.waveform_view);

          ripple1 = content.findViewById(R.id.ripple_1);
          ripple2 = content.findViewById(R.id.ripple_2);
          ripple3 = content.findViewById(R.id.ripple_3);
          host.onSpeakingViewsBound(
              btnMicCircle,
              progressRing,
              loadingSpinner,
              tvListeningStatus,
              waveformView,
              ripple1,
              ripple2,
              ripple3);

          if (tvOriginalSentence != null) {
            tvOriginalSentence.setText(sentenceToTranslate);
          }

          if (tvListeningStatus != null) {
            tvListeningStatus.setText("답변이 끝나면 마이크 버튼을 다시 누르세요");
            tvListeningStatus.setTextColor(
                ContextCompat.getColor(
                    tvListeningStatus.getContext(), R.color.state_listening_prompt));
          }

          if (btnMicCircle != null) {
            btnMicCircle.setBackgroundResource(R.drawable.mic_button_recording);
            btnMicCircle.setImageResource(R.drawable.ic_stop_square);
          }
          if (progressRing != null) {
            progressRing.setVisibility(View.VISIBLE);
            progressRing.setProgress(0);
          }
          if (loadingSpinner != null) {
            loadingSpinner.setVisibility(View.GONE);
          }

          if (waveformView != null) {
            waveformView.reset();
          }
          host.setLastWaveformUpdateMs(0L);

          ByteArrayOutputStream audioAccumulator = host.getAudioAccumulator();
          if (audioAccumulator != null) {
            audioAccumulator.reset();
          }

          host.startProgressAnimation();
          host.startRippleAnimation();

          host.renderWhileSpeakingContent(content, sentenceToTranslate);

          AudioRecorder audioRecorderManager = host.getAudioRecorderManager();
          if (audioRecorderManager == null) {
            if (tvListeningStatus != null) {
              tvListeningStatus.setText("녹음 매니저를 준비할 수 없습니다.");
              tvListeningStatus.setTextColor(
                  ContextCompat.getColor(tvListeningStatus.getContext(), R.color.state_error));
            }
            host.stopSpeakingSessionState("audio_recorder_unavailable");
            return;
          }

          audioRecorderManager.startRecording(
              new AudioRecorder.AudioCallback() {
                @Override
                public void onAudioData(byte[] audioData) {
                  if (!host.isSpeaking() || audioData == null || audioData.length == 0) {
                    return;
                  }

                  host.onAudioChunk(audioData);

                  ByteArrayOutputStream accumulator = host.getAudioAccumulator();
                  if (accumulator != null) {
                    try {
                      accumulator.write(audioData);
                    } catch (Exception e) {
                      e.printStackTrace();
                    }
                  }

                  if (waveformView != null) {
                    long now = SystemClock.elapsedRealtime();
                    if (now - host.getLastWaveformUpdateMs() >= WAVEFORM_UPDATE_INTERVAL_MS) {
                      host.setLastWaveformUpdateMs(now);
                      final float amplitude = SpeakingFlowController.calculateAmplitude(audioData);
                      host.getMainHandler()
                          .post(
                              () -> {
                                if (waveformView != null) {
                                  waveformView.addAmplitude(amplitude);
                                }
                              });
                    }
                  }
                }

                @Override
                public void onError(String error) {
                  host.getMainHandler()
                      .post(
                          () -> {
                            if (tvListeningStatus != null) {
                              tvListeningStatus.setText("녹음 오류: " + error);
                              tvListeningStatus.setTextColor(
                                  ContextCompat.getColor(
                                      tvListeningStatus.getContext(), R.color.state_error));
                            }
                          });
                }
              });

          if (btnMicCircle != null) {
            btnMicCircle.setOnClickListener(
                v -> {
                  if (!host.isSpeaking()) {
                    return;
                  }

                  host.stopProgressAnimation();
                  host.stopRippleAnimation();

                  host.safeStopRecording("mic_button_stop");
                  if (waveformView != null) {
                    waveformView.reset();
                    waveformView.setVisibility(View.GONE);
                  }

                  transitionToAnalyzingState(sentenceToTranslate);
                });
          }
        });
  }

  public void transitionToAnalyzingState(@NonNull String sentenceToTranslate) {
    ByteArrayOutputStream audioAccumulator = host.getAudioAccumulator();
    byte[] audioData = audioAccumulator == null ? new byte[0] : audioAccumulator.toByteArray();
    host.setLastRecordedAudio(audioData.clone());

    float normalizedRms = RecordedAudioSilenceDetector.calculateNormalizedRms(audioData);
    if (RecordedAudioSilenceDetector.isSilent(audioData, NO_AUDIO_RMS_THRESHOLD)) {
      String rmsText = String.format(Locale.US, "%.4f", normalizedRms);
      host.trace(
          "TRACE_NO_AUDIO_DETECTED audioLen="
              + audioData.length
              + " rms="
              + rmsText
              + " threshold=0.03");
      host.ux("UX_RECORD_EMPTY", "audioLen=" + audioData.length + " rms=" + rmsText);

      if (btnMicCircle != null) {
        btnMicCircle.setEnabled(true);
      }
      if (progressRing != null) {
        progressRing.setVisibility(View.GONE);
      }
      if (loadingSpinner != null) {
        loadingSpinner.setVisibility(View.GONE);
      }
      if (tvListeningStatus != null) {
        tvListeningStatus.setText("녹음된 소리가 없어요");
        tvListeningStatus.setTextColor(
            ContextCompat.getColor(tvListeningStatus.getContext(), R.color.state_error));
      }

      stopSpeakingSession();
      scheduleBeforeSpeakingAfterNoAudio(sentenceToTranslate);
      return;
    }

    if (btnMicCircle != null) {
      btnMicCircle.setBackgroundResource(R.drawable.mic_button_analyzing);
      btnMicCircle.setImageDrawable(null);
      btnMicCircle.setEnabled(false);
    }
    if (progressRing != null) {
      progressRing.setVisibility(View.GONE);
    }
    if (loadingSpinner != null) {
      loadingSpinner.setVisibility(View.VISIBLE);
    }
    if (tvListeningStatus != null) {
      tvListeningStatus.setText("채점 중...");
      tvListeningStatus.setTextColor(
          ContextCompat.getColor(tvListeningStatus.getContext(), R.color.state_analyzing_soft));
    }

    host.onRecordingStopRequestedOrAnalyzeFallback(sentenceToTranslate, audioData);
  }

  public void stopSpeakingSession() {
    cancelPendingNoAudioBeforeSpeakingPresentation();
    host.safeStopRecording("stopSpeakingSession");
    host.stopSpeakingSessionState("stopSpeakingSession");
    host.setLastWaveformUpdateMs(0L);

    host.stopProgressAnimation();
    host.stopRippleAnimation();

    if (waveformView != null) {
      waveformView.reset();
    }

    ByteArrayOutputStream audioAccumulator = host.getAudioAccumulator();
    if (audioAccumulator != null) {
      audioAccumulator.reset();
    }
  }

  public void release() {
    cancelPendingNoAudioBeforeSpeakingPresentation();
    btnMicCircle = null;
    progressRing = null;
    loadingSpinner = null;
    tvListeningStatus = null;
    waveformView = null;
    ripple1 = null;
    ripple2 = null;
    ripple3 = null;
    host.onSpeakingViewsCleared();
    host.setLastWaveformUpdateMs(0L);
  }

  private void scheduleBeforeSpeakingAfterNoAudio(@NonNull String sentenceToTranslate) {
    cancelPendingNoAudioBeforeSpeakingPresentation();

    pendingNoAudioBeforeSpeakingPresentation =
        () -> {
          pendingNoAudioBeforeSpeakingPresentation = null;
          boolean presented = host.tryPresentBeforeSpeakingSceneAfterNoAudio(sentenceToTranslate);
          if (presented) {
            host.trace("TRACE_NO_AUDIO_RETURN_BEFORE execute");
            return;
          }
          host.trace("TRACE_NO_AUDIO_RETURN_BEFORE skip reason=host_rejected");
        };

    host.getMainHandler()
        .postDelayed(pendingNoAudioBeforeSpeakingPresentation, NO_AUDIO_RETURN_DELAY_MS);
    host.trace("TRACE_NO_AUDIO_RETURN_BEFORE scheduled delayMs=" + NO_AUDIO_RETURN_DELAY_MS);
  }

  private void cancelPendingNoAudioBeforeSpeakingPresentation() {
    if (pendingNoAudioBeforeSpeakingPresentation == null) {
      return;
    }
    host.getMainHandler().removeCallbacks(pendingNoAudioBeforeSpeakingPresentation);
    pendingNoAudioBeforeSpeakingPresentation = null;
  }
}
