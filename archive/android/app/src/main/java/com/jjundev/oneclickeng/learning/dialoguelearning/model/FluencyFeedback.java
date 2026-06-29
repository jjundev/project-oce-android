package com.jjundev.oneclickeng.learning.dialoguelearning.model;

import androidx.annotation.NonNull;

public class FluencyFeedback {
  private final int fluency;
  private final int confidence;
  private final int hesitations;
  @NonNull private final String transcript;
  @NonNull private final String feedbackMessage;

  public FluencyFeedback(
      int fluency,
      int confidence,
      int hesitations,
      @NonNull String transcript,
      @NonNull String feedbackMessage) {
    this.fluency = fluency;
    this.confidence = confidence;
    this.hesitations = hesitations;
    this.transcript = transcript;
    this.feedbackMessage = feedbackMessage;
  }

  public int getFluency() {
    return fluency;
  }

  public int getConfidence() {
    return confidence;
  }

  public int getHesitations() {
    return hesitations;
  }

  @NonNull
  public String getTranscript() {
    return transcript;
  }

  @NonNull
  public String getFeedbackMessage() {
    return feedbackMessage;
  }
}
