package com.jjundev.oneclickeng.learning.dialoguelearning.controller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISpeakingFeedbackManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.FluencyFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SpeakingAnalysisResult;
import com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator.AsyncRequestTracker;

public class SpeakingFlowController {

  public interface Callback {
    void onSuccess(@NonNull SpeakingAnalysisResult result);

    void onError(long requestId, @NonNull String error);
  }

  public interface SpeakingAnalyzer {
    void analyzeAudio(byte[] audioData, ISpeakingFeedbackManager.AnalysisCallback callback);
  }

  public static class ManagerSpeakingAnalyzer implements SpeakingAnalyzer {
    private final ISpeakingFeedbackManager manager;

    public ManagerSpeakingAnalyzer(@NonNull ISpeakingFeedbackManager manager) {
      this.manager = manager;
    }

    @Override
    public void analyzeAudio(byte[] audioData, ISpeakingFeedbackManager.AnalysisCallback callback) {
      manager.analyzeAudio(audioData, callback);
    }
  }

  private final SpeakingAnalyzer speakingAnalyzer;
  private final AsyncRequestTracker requestTracker;
  private final String requestChannel;

  public SpeakingFlowController(@NonNull SpeakingAnalyzer speakingAnalyzer) {
    this(speakingAnalyzer, new AsyncRequestTracker(), AsyncRequestTracker.CHANNEL_SPEAKING);
  }

  public SpeakingFlowController(
      @NonNull SpeakingAnalyzer speakingAnalyzer, @NonNull AsyncRequestTracker requestTracker) {
    this(speakingAnalyzer, requestTracker, AsyncRequestTracker.CHANNEL_SPEAKING);
  }

  public SpeakingFlowController(
      @NonNull SpeakingAnalyzer speakingAnalyzer,
      @NonNull AsyncRequestTracker requestTracker,
      @NonNull String requestChannel) {
    this.speakingAnalyzer = speakingAnalyzer;
    this.requestTracker = requestTracker;
    this.requestChannel = requestChannel;
  }

  public long analyzeSpeaking(
      @NonNull String originalSentence,
      @NonNull byte[] audioData,
      @Nullable String fallbackRecognizedText,
      @NonNull Callback callback) {
    long requestId = requestTracker.nextRequestId(requestChannel);

    speakingAnalyzer.analyzeAudio(
        audioData,
        new ISpeakingFeedbackManager.AnalysisCallback() {
          @Override
          public void onSuccess(@NonNull FluencyFeedback result) {
            if (!requestTracker.isLatest(requestChannel, requestId)) {
              return;
            }

            String recognized =
                (result.getTranscript() != null && !result.getTranscript().isEmpty())
                    ? result.getTranscript()
                    : (fallbackRecognizedText == null ? "" : fallbackRecognizedText);

            callback.onSuccess(
                new SpeakingAnalysisResult(
                    requestId, originalSentence, recognized, result, audioData.clone()));
          }

          @Override
          public void onError(@NonNull String error) {
            if (!requestTracker.isLatest(requestChannel, requestId)) {
              return;
            }
            callback.onError(requestId, error == null ? "Unknown analysis error" : error);
          }
        });

    return requestId;
  }

  public void invalidate() {
    requestTracker.invalidate(requestChannel);
  }

  public static float calculateAmplitude(@NonNull byte[] audioData) {
    double sum = 0;
    int readSize = audioData.length;
    for (int i = 0; i < readSize; i += 2) {
      if (i + 1 < readSize) {
        short sample = (short) ((audioData[i] & 0xFF) | (audioData[i + 1] << 8));
        sum += sample * sample;
      }
    }

    if (readSize <= 0) {
      return 0f;
    }

    double rms = Math.sqrt(sum / (readSize / 2.0));
    float amplitude = (float) ((rms / 32768.0) * 3.0);
    return Math.min(Math.max(amplitude, 0f), 1f);
  }
}
