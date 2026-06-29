package com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts;

import androidx.annotation.NonNull;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SentenceFeedback;

public interface ISentenceFeedbackManager {
  interface InitCallback {
    void onReady();

    void onError(@NonNull String error);
  }

  interface StreamingFeedbackCallback {
    void onSectionReady(@NonNull String sectionName, @NonNull SentenceFeedback partialFeedback);

    void onComplete(@NonNull SentenceFeedback fullFeedback);

    void onError(@NonNull String error);
  }

  void initializeCache(@NonNull InitCallback callback);

  void analyzeSentenceStreaming(
      @NonNull String originalSentence,
      @NonNull String userSentence,
      @NonNull StreamingFeedbackCallback callback);
}
