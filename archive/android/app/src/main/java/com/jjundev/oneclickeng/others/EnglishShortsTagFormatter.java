package com.jjundev.oneclickeng.others;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;

/** Normalizes and limits English Shorts tags for display chips. */
public final class EnglishShortsTagFormatter {

  private EnglishShortsTagFormatter() {}

  @NonNull
  public static List<String> buildDisplayTags(@Nullable List<String> rawTags, int maxCount) {
    if (rawTags == null || rawTags.isEmpty() || maxCount <= 0) {
      return new ArrayList<>();
    }

    List<String> displayTags = new ArrayList<>();
    Set<String> dedupeKeys = new LinkedHashSet<>();
    for (String rawTag : rawTags) {
      if (displayTags.size() >= maxCount) {
        break;
      }
      if (rawTag == null) {
        continue;
      }

      String trimmed = rawTag.trim();
      if (trimmed.isEmpty()) {
        continue;
      }

      String normalized = trimmed.startsWith("#") ? trimmed : "#" + trimmed;
      String dedupeKey = normalized.toLowerCase(Locale.US);
      if (dedupeKeys.add(dedupeKey)) {
        displayTags.add(normalized);
      }
    }
    return displayTags;
  }
}
