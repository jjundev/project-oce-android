package com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface IGeminiTtsManager {

  interface SynthesisCallback {
    void onSuccess(@NonNull GeminiTtsAudio audio);

    void onError(@NonNull String errorMessage);
  }

  void synthesize(
      @NonNull String text,
      @Nullable String localeTag,
      float speechRate,
      @Nullable String voiceName,
      @NonNull SynthesisCallback callback);

  void cancelActiveRequest();
}
