package com.jjundev.oneclickeng.settings;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;

/** SharedPreferences-backed gate for one-time in-app review prompt opportunities. */
public final class InAppReviewPromptStore {
  static final String PREF_NAME = "in_app_review_prompt";
  private static final String KEY_DIALOGUE_SUMMARY_FINISH_REVIEW_ATTEMPTED =
      "dialogue_summary_finish_review_attempted";

  @NonNull private final SharedPreferences preferences;

  public InAppReviewPromptStore(@NonNull Context context) {
    this(context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE));
  }

  InAppReviewPromptStore(@NonNull SharedPreferences preferences) {
    this.preferences = preferences;
  }

  public synchronized boolean consumeDialogueSummaryFinishReviewOpportunity() {
    if (preferences.getBoolean(KEY_DIALOGUE_SUMMARY_FINISH_REVIEW_ATTEMPTED, false)) {
      return false;
    }
    preferences.edit().putBoolean(KEY_DIALOGUE_SUMMARY_FINISH_REVIEW_ATTEMPTED, true).apply();
    return true;
  }
}
