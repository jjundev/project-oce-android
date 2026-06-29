package com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts;

import androidx.annotation.NonNull;

public interface IExtraQuestionManager {
  interface StreamingResponseCallback {
    void onTextChunk(@NonNull String text);

    void onComplete();

    void onError(@NonNull String error);
  }

  void askExtraQuestionStreaming(
      @NonNull String originalSentence,
      @NonNull String userSentence,
      @NonNull String userQuestion,
      @NonNull StreamingResponseCallback callback);
}
