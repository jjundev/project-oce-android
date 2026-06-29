package com.jjundev.oneclickeng.learning.dialoguelearning.state;

import androidx.annotation.Nullable;

public class ExtraQuestionUiState {
  private final long emissionId;
  private final boolean loading;
  @Nullable private final String question;
  @Nullable private final String response;
  @Nullable private final String error;

  public ExtraQuestionUiState(
      long emissionId,
      boolean loading,
      @Nullable String question,
      @Nullable String response,
      @Nullable String error) {
    this.emissionId = emissionId;
    this.loading = loading;
    this.question = question;
    this.response = response;
    this.error = error;
  }

  public static ExtraQuestionUiState idle() {
    return new ExtraQuestionUiState(0L, false, null, null, null);
  }

  public long getEmissionId() {
    return emissionId;
  }

  public boolean isLoading() {
    return loading;
  }

  @Nullable
  public String getQuestion() {
    return question;
  }

  @Nullable
  public String getResponse() {
    return response;
  }

  @Nullable
  public String getError() {
    return error;
  }
}
