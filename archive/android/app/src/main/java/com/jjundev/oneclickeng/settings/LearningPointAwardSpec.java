package com.jjundev.oneclickeng.settings;

import androidx.annotation.NonNull;

/** Point award payload produced by each learning mode when a session is completed. */
public final class LearningPointAwardSpec {
  @NonNull private final String modeId;
  @NonNull private final String difficulty;
  private final int points;
  private final long awardedAtEpochMs;

  public LearningPointAwardSpec(
      @NonNull String modeId, @NonNull String difficulty, int points, long awardedAtEpochMs) {
    this.modeId = normalizeOrDefault(modeId, "unknown");
    this.difficulty = LearningDifficulty.normalizeOrDefault(difficulty);
    this.points = Math.max(0, points);
    this.awardedAtEpochMs = Math.max(0L, awardedAtEpochMs);
  }

  @NonNull
  public String getModeId() {
    return modeId;
  }

  @NonNull
  public String getDifficulty() {
    return difficulty;
  }

  public int getPoints() {
    return points;
  }

  public long getAwardedAtEpochMs() {
    return awardedAtEpochMs;
  }

  @NonNull
  private static String normalizeOrDefault(@NonNull String value, @NonNull String fallback) {
    String normalized = value.trim();
    return normalized.isEmpty() ? fallback : normalized;
  }
}
