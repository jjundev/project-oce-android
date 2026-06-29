package com.jjundev.oneclickeng.learning.dialoguelearning.state;

import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SentenceFeedback;

public class FeedbackUiState {
  private final long emissionId;
  private final boolean loading;
  @Nullable private final String sectionKey;
  @Nullable private final SentenceFeedback partialFeedback;
  @Nullable private final SentenceFeedback fullFeedback;
  private final boolean showNextButton;
  @Nullable private final String error;
  private final boolean completed;
  private final ExtraQuestionUiState extraQuestionUiState;

  public FeedbackUiState(
      long emissionId,
      boolean loading,
      @Nullable String sectionKey,
      @Nullable SentenceFeedback partialFeedback,
      @Nullable SentenceFeedback fullFeedback,
      boolean showNextButton,
      @Nullable String error,
      boolean completed,
      @Nullable ExtraQuestionUiState extraQuestionUiState) {
    this.emissionId = emissionId;
    this.loading = loading;
    this.sectionKey = sectionKey;
    this.partialFeedback = partialFeedback;
    this.fullFeedback = fullFeedback;
    this.showNextButton = showNextButton;
    this.error = error;
    this.completed = completed;
    this.extraQuestionUiState =
        extraQuestionUiState == null ? ExtraQuestionUiState.idle() : extraQuestionUiState;
  }

  public static FeedbackUiState idle() {
    return new FeedbackUiState(
        0L, false, null, null, null, true, null, false, ExtraQuestionUiState.idle());
  }

  public long getEmissionId() {
    return emissionId;
  }

  public boolean isLoading() {
    return loading;
  }

  @Nullable
  public String getSectionKey() {
    return sectionKey;
  }

  @Nullable
  public SentenceFeedback getPartialFeedback() {
    return partialFeedback;
  }

  @Nullable
  public SentenceFeedback getFullFeedback() {
    return fullFeedback;
  }

  public boolean isShowNextButton() {
    return showNextButton;
  }

  @Nullable
  public String getError() {
    return error;
  }

  public boolean isCompleted() {
    return completed;
  }

  public ExtraQuestionUiState getExtraQuestionUiState() {
    return extraQuestionUiState;
  }
}
