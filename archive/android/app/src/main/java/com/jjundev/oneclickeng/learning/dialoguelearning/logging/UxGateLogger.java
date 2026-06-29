package com.jjundev.oneclickeng.learning.dialoguelearning.logging;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.BuildConfig;

public final class UxGateLogger {
  private static final String TAG = "DialogueLearningUX";

  private final long sessionId;
  private long seq = 0L;

  public UxGateLogger(long sessionId) {
    this.sessionId = sessionId;
  }

  public void log(@NonNull String key) {
    log(key, null);
  }

  public void log(@NonNull String key, @Nullable String fields) {
    String safeKey = key == null ? "" : key.trim();
    if (safeKey.isEmpty()) {
      return;
    }

    long nextSeq = ++seq;
    String base = safeKey + " session=" + sessionId + " seq=" + nextSeq;
    if (fields == null) {
      Log.d(TAG, base);
      return;
    }

    String safeFields = fields.trim();
    if (safeFields.isEmpty()) {
      Log.d(TAG, base);
      return;
    }

    Log.d(TAG, base + " " + safeFields);
  }
}
