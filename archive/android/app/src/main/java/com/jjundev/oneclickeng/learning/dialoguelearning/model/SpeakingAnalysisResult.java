package com.jjundev.oneclickeng.learning.dialoguelearning.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class SpeakingAnalysisResult {
  private final long requestId;
  @NonNull private final String originalSentence;
  @NonNull private final String recognizedText;
  @NonNull private final FluencyFeedback fluencyResult;
  @Nullable private final byte[] recordedAudio;

  public SpeakingAnalysisResult(
      long requestId,
      @NonNull String originalSentence,
      @NonNull String recognizedText,
      @NonNull FluencyFeedback fluencyResult,
      @Nullable byte[] recordedAudio) {
    this.requestId = requestId;
    this.originalSentence = originalSentence;
    this.recognizedText = recognizedText;
    this.fluencyResult = fluencyResult;
    this.recordedAudio = recordedAudio;
  }

  public long getRequestId() {
    return requestId;
  }

  @NonNull
  public String getOriginalSentence() {
    return originalSentence;
  }

  @NonNull
  public String getRecognizedText() {
    return recognizedText;
  }

  @NonNull
  public FluencyFeedback getFluencyResult() {
    return fluencyResult;
  }

  @Nullable
  public byte[] getRecordedAudio() {
    return recordedAudio;
  }
}
