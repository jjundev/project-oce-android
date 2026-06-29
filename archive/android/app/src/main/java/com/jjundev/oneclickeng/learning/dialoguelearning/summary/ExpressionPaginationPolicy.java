package com.jjundev.oneclickeng.learning.dialoguelearning.summary;

import androidx.annotation.Nullable;

/** Stateless pagination policy for expression cards in DialogueSummary. */
public final class ExpressionPaginationPolicy {

  public static final int PAGE_SIZE = 3;

  private ExpressionPaginationPolicy() {}

  public static int resolveRequestedVisibleCount(
      @Nullable Integer requestedTag, @Nullable Integer lastTotalTag, int totalCount) {
    int safeTotal = Math.max(0, totalCount);
    int defaultRequested = Math.min(PAGE_SIZE, safeTotal);
    if (safeTotal == 0) {
      return 0;
    }

    if (lastTotalTag != null && safeTotal < Math.max(0, lastTotalTag)) {
      return defaultRequested;
    }

    int requested = requestedTag == null ? defaultRequested : requestedTag;
    if (requested <= 0) {
      requested = defaultRequested;
    }
    if (requested > safeTotal) {
      requested = safeTotal;
    }
    return requested;
  }

  public static int nextRequestedVisibleCountAfterClick(int currentRequested, int totalCount) {
    int safeTotal = Math.max(0, totalCount);
    if (safeTotal == 0) {
      return 0;
    }

    int safeCurrent = Math.max(0, currentRequested);
    if (safeCurrent < safeTotal) {
      return Math.min(safeCurrent + PAGE_SIZE, safeTotal);
    }
    return Math.min(PAGE_SIZE, safeTotal);
  }

  @Nullable
  public static String computeToggleText(int requestedVisibleCount, int totalCount) {
    int safeTotal = Math.max(0, totalCount);
    int safeRequested = Math.min(Math.max(0, requestedVisibleCount), safeTotal);
    if (safeRequested < safeTotal) {
      int nextBatch = Math.min(PAGE_SIZE, safeTotal - safeRequested);
      if (nextBatch > 0) {
        return "더 보기 (" + nextBatch + ")";
      }
      return null;
    }
    if (safeTotal > PAGE_SIZE) {
      return "접기";
    }
    return null;
  }
}
