package com.jjundev.oneclickeng.learning.dialoguelearning.di;

import android.util.Log;
import androidx.annotation.NonNull;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISentenceFeedbackManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISpeakingFeedbackManager;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LearningManagerInitializer {

  private static final String TAG = "LearningManagerInit";

  private final ISpeakingFeedbackManager speakingFeedbackManager;
  private final ISentenceFeedbackManager sentenceFeedbackManager;
  private final boolean enableSentenceCaching;
  private final AtomicBoolean initialized = new AtomicBoolean(false);

  public LearningManagerInitializer(
      @NonNull ISpeakingFeedbackManager speakingFeedbackManager,
      @NonNull ISentenceFeedbackManager sentenceFeedbackManager,
      boolean enableSentenceCaching) {
    this.speakingFeedbackManager = speakingFeedbackManager;
    this.sentenceFeedbackManager = sentenceFeedbackManager;
    this.enableSentenceCaching = enableSentenceCaching;
  }

  public void initializeCaches() {
    if (!initialized.compareAndSet(false, true)) {
      return;
    }

    speakingFeedbackManager.initializeCache(
        new ISpeakingFeedbackManager.InitCallback() {
          @Override
          public void onReady() {
            Log.d(TAG, "Speaking cache ready");
          }

          @Override
          public void onError(@NonNull String error) {
            Log.w(TAG, "Speaking cache init failed: " + error);
          }
        });

    if (!enableSentenceCaching) {
      return;
    }

    sentenceFeedbackManager.initializeCache(
        new ISentenceFeedbackManager.InitCallback() {
          @Override
          public void onReady() {
            Log.d(TAG, "Sentence cache ready");
          }

          @Override
          public void onError(@NonNull String error) {
            Log.w(TAG, "Sentence cache init failed, continue without cache: " + error);
          }
        });
  }
}
