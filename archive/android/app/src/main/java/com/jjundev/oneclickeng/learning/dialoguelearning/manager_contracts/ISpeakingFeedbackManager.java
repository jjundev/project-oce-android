package com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts;

import androidx.annotation.NonNull;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.FluencyFeedback;

public interface ISpeakingFeedbackManager {
  interface InitCallback {
    void onReady();

    void onError(@NonNull String error);
  }

  interface AnalysisCallback {
    void onSuccess(@NonNull FluencyFeedback result);

    void onError(@NonNull String error);
  }

  void initializeCache(@NonNull InitCallback callback);

  void analyzeAudio(@NonNull byte[] audioData, @NonNull AnalysisCallback callback);
}
