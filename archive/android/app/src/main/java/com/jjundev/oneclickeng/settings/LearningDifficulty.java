package com.jjundev.oneclickeng.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Locale;

/** Canonical learning difficulty values used for point calculation and persistence. */
public enum LearningDifficulty {
  BEGINNER("beginner", 5),
  ELEMENTARY("elementary", 10),
  INTERMEDIATE("intermediate", 20),
  UPPER_INTERMEDIATE("upper-intermediate", 35),
  ADVANCED("advanced", 50);

  @NonNull private final String key;
  private final int basePoints;

  LearningDifficulty(@NonNull String key, int basePoints) {
    this.key = key;
    this.basePoints = Math.max(0, basePoints);
  }

  @NonNull
  public String getKey() {
    return key;
  }

  public int getBasePoints() {
    return basePoints;
  }

  @NonNull
  public static LearningDifficulty fromRaw(@Nullable String raw) {
    String normalized = normalizeRaw(raw);
    switch (normalized) {
      case "beginner":
        return BEGINNER;
      case "elementary":
        return ELEMENTARY;
      case "upper-intermediate":
        return UPPER_INTERMEDIATE;
      case "advanced":
        return ADVANCED;
      case "intermediate":
      default:
        return INTERMEDIATE;
    }
  }

  @NonNull
  public static String normalizeOrDefault(@Nullable String raw) {
    return fromRaw(raw).getKey();
  }

  @NonNull
  private static String normalizeRaw(@Nullable String raw) {
    if (raw == null) {
      return INTERMEDIATE.getKey();
    }
    String value = raw.trim().toLowerCase(Locale.US);
    if (value.isEmpty()) {
      return INTERMEDIATE.getKey();
    }
    value = value.replace('_', '-');
    value = value.replace(' ', '-');
    if ("upperintermediate".equals(value)) {
      return "upper-intermediate";
    }
    return value;
  }
}
