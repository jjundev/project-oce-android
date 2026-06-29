package com.jjundev.oneclickeng.learning.dialoguelearning.state;

import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.FluencyFeedback;

public class SpeakingUiState {
  private final long emissionId;
  private final long requestId;
  private final boolean recording;
  private final boolean analyzing;
  private final float amplitude;
  @Nullable private final byte[] lastRecordedAudio;
  @Nullable private final FluencyFeedback fluencyResult;
  @Nullable private final String originalSentence;
  @Nullable private final String recognizedText;
  @Nullable private final String error;

  public SpeakingUiState(
      long emissionId,
      long requestId,
      boolean recording,
      boolean analyzing,
      float amplitude,
      @Nullable byte[] lastRecordedAudio,
      @Nullable FluencyFeedback fluencyResult,
      @Nullable String originalSentence,
      @Nullable String recognizedText,
      @Nullable String error) {
    this.emissionId = emissionId;
    this.requestId = requestId;
    this.recording = recording;
    this.analyzing = analyzing;
    this.amplitude = amplitude;
    this.lastRecordedAudio = lastRecordedAudio;
    this.fluencyResult = fluencyResult;
    this.originalSentence = originalSentence;
    this.recognizedText = recognizedText;
    this.error = error;
  }

  public static SpeakingUiState idle() {
    return new SpeakingUiState(0L, 0L, false, false, 0f, null, null, null, null, null);
  }

  public long getEmissionId() {
    return emissionId;
  }

  public long getRequestId() {
    return requestId;
  }

  public boolean isRecording() {
    return recording;
  }

  public boolean isAnalyzing() {
    return analyzing;
  }

  public float getAmplitude() {
    return amplitude;
  }

  @Nullable
  public byte[] getLastRecordedAudio() {
    return lastRecordedAudio;
  }

  @Nullable
  public FluencyFeedback getFluencyResult() {
    return fluencyResult;
  }

  @Nullable
  public String getOriginalSentence() {
    return originalSentence;
  }

  @Nullable
  public String getRecognizedText() {
    return recognizedText;
  }

  @Nullable
  public String getError() {
    return error;
  }
}
