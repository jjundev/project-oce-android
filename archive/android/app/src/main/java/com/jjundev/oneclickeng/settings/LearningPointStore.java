package com.jjundev.oneclickeng.settings;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Local point store with idempotent session award and pending cloud-sync queue. */
public final class LearningPointStore {
  static final String PREF_NAME = "learning_point_metrics";
  private static final String KEY_TOTAL_POINTS = "total_points";
  private static final String KEY_AWARDED_SESSION_IDS = "awarded_session_ids";
  private static final String KEY_PENDING_AWARDS_JSON = "pending_awards_json";

  @NonNull private final SharedPreferences preferences;
  @NonNull private final Gson gson;
  @NonNull private final Type pendingAwardListType;

  public LearningPointStore(@NonNull Context context) {
    this(
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
        new Gson(),
        new TypeToken<List<PendingPointAward>>() {}.getType());
  }

  LearningPointStore(
      @NonNull SharedPreferences preferences,
      @NonNull Gson gson,
      @NonNull Type pendingAwardListType) {
    this.preferences = preferences;
    this.gson = gson;
    this.pendingAwardListType = pendingAwardListType;
  }

  public synchronized int getTotalPoints() {
    long totalPoints = Math.max(0L, preferences.getLong(KEY_TOTAL_POINTS, 0L));
    return safeLongToInt(totalPoints);
  }

  public synchronized boolean hasAwardedSession(@NonNull String sessionId) {
    String normalizedSessionId = normalizeSessionId(sessionId);
    if (normalizedSessionId.isEmpty()) {
      return false;
    }
    return readAwardedSessionIds().contains(normalizedSessionId);
  }

  public synchronized boolean awardSessionIfNeeded(
      @NonNull String sessionId, @NonNull LearningPointAwardSpec awardSpec) {
    String normalizedSessionId = normalizeSessionId(sessionId);
    if (normalizedSessionId.isEmpty()) {
      return false;
    }
    if (awardSpec.getPoints() <= 0) {
      return false;
    }

    Set<String> awardedSessionIds = readAwardedSessionIds();
    if (awardedSessionIds.contains(normalizedSessionId)) {
      return false;
    }

    int updatedTotalPoints = safeAddPoints(getTotalPoints(), awardSpec.getPoints());
    awardedSessionIds.add(normalizedSessionId);
    List<PendingPointAward> pendingAwards = readPendingAwardsInternal();
    pendingAwards.add(
        new PendingPointAward(
            normalizedSessionId,
            normalizeModeId(awardSpec.getModeId()),
            LearningDifficulty.normalizeOrDefault(awardSpec.getDifficulty()),
            Math.max(0, awardSpec.getPoints()),
            Math.max(0L, awardSpec.getAwardedAtEpochMs())));
    persistState(updatedTotalPoints, awardedSessionIds, pendingAwards);
    return true;
  }

  public synchronized int mergeCloudTotalPoints(int cloudTotalPoints) {
    int localTotal = getTotalPoints();
    int safeCloudTotal = Math.max(0, cloudTotalPoints);
    int merged = Math.max(localTotal, safeCloudTotal);
    if (merged == localTotal) {
      return localTotal;
    }
    preferences.edit().putLong(KEY_TOTAL_POINTS, merged).apply();
    return merged;
  }

  public synchronized void resetAllPoints() {
    preferences
        .edit()
        .putLong(KEY_TOTAL_POINTS, 0L)
        .remove(KEY_AWARDED_SESSION_IDS)
        .remove(KEY_PENDING_AWARDS_JSON)
        .apply();
  }

  @NonNull
  public synchronized List<PendingPointAward> getPendingAwards() {
    return new ArrayList<>(readPendingAwardsInternal());
  }

  public synchronized void removePendingAwardsBySessionIds(@NonNull Set<String> sessionIds) {
    Set<String> normalizedIds = normalizeSessionIds(sessionIds);
    if (normalizedIds.isEmpty()) {
      return;
    }

    List<PendingPointAward> current = readPendingAwardsInternal();
    List<PendingPointAward> remaining = new ArrayList<>();
    for (PendingPointAward item : current) {
      if (!normalizedIds.contains(item.getSessionId())) {
        remaining.add(item);
      }
    }
    if (remaining.size() == current.size()) {
      return;
    }
    persistPendingAwards(remaining);
  }

  @NonNull
  private Set<String> readAwardedSessionIds() {
    Set<String> raw = preferences.getStringSet(KEY_AWARDED_SESSION_IDS, null);
    Set<String> normalized = new HashSet<>();
    if (raw == null) {
      return normalized;
    }
    for (String item : raw) {
      String sessionId = normalizeSessionId(item);
      if (!sessionId.isEmpty()) {
        normalized.add(sessionId);
      }
    }
    return normalized;
  }

  @NonNull
  private List<PendingPointAward> readPendingAwardsInternal() {
    String rawJson = preferences.getString(KEY_PENDING_AWARDS_JSON, null);
    if (rawJson == null || rawJson.trim().isEmpty()) {
      return new ArrayList<>();
    }

    try {
      List<PendingPointAward> parsed = gson.fromJson(rawJson, pendingAwardListType);
      if (parsed == null || parsed.isEmpty()) {
        return new ArrayList<>();
      }
      Map<String, PendingPointAward> unique = new LinkedHashMap<>();
      for (PendingPointAward item : parsed) {
        PendingPointAward normalized = PendingPointAward.normalizedCopy(item);
        if (normalized == null) {
          continue;
        }
        unique.put(normalized.getSessionId(), normalized);
      }
      return new ArrayList<>(unique.values());
    } catch (Exception ignored) {
      return new ArrayList<>();
    }
  }

  private void persistState(
      int totalPoints,
      @NonNull Set<String> awardedSessionIds,
      @NonNull List<PendingPointAward> pendingAwards) {
    preferences
        .edit()
        .putLong(KEY_TOTAL_POINTS, Math.max(0, totalPoints))
        .putStringSet(KEY_AWARDED_SESSION_IDS, new HashSet<>(awardedSessionIds))
        .putString(KEY_PENDING_AWARDS_JSON, gson.toJson(pendingAwards, pendingAwardListType))
        .apply();
  }

  private void persistPendingAwards(@NonNull List<PendingPointAward> pendingAwards) {
    preferences
        .edit()
        .putString(KEY_PENDING_AWARDS_JSON, gson.toJson(pendingAwards, pendingAwardListType))
        .apply();
  }

  private static int safeAddPoints(int base, int delta) {
    if (delta <= 0) {
      return base;
    }
    if (base > Integer.MAX_VALUE - delta) {
      return Integer.MAX_VALUE;
    }
    return base + delta;
  }

  private static int safeLongToInt(long value) {
    if (value <= 0L) {
      return 0;
    }
    if (value >= Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    return (int) value;
  }

  @NonNull
  private static String normalizeSessionId(@Nullable String sessionId) {
    return sessionId == null ? "" : sessionId.trim();
  }

  @NonNull
  private static Set<String> normalizeSessionIds(@NonNull Set<String> sessionIds) {
    Set<String> normalized = new HashSet<>();
    for (String item : sessionIds) {
      String sessionId = normalizeSessionId(item);
      if (!sessionId.isEmpty()) {
        normalized.add(sessionId);
      }
    }
    return normalized;
  }

  @NonNull
  private static String normalizeModeId(@Nullable String modeId) {
    if (modeId == null) {
      return "unknown";
    }
    String trimmed = modeId.trim();
    if (trimmed.isEmpty()) {
      return "unknown";
    }
    return trimmed.toLowerCase(Locale.US);
  }

  /** Pending award entry awaiting cloud synchronization. */
  public static final class PendingPointAward {
    @Nullable private String sessionId;
    @Nullable private String modeId;
    @Nullable private String difficulty;
    private int points;
    private long awardedAtEpochMs;

    public PendingPointAward() {}

    public PendingPointAward(
        @NonNull String sessionId,
        @NonNull String modeId,
        @NonNull String difficulty,
        int points,
        long awardedAtEpochMs) {
      this.sessionId = sessionId;
      this.modeId = modeId;
      this.difficulty = difficulty;
      this.points = points;
      this.awardedAtEpochMs = awardedAtEpochMs;
    }

    @NonNull
    public String getSessionId() {
      return normalizeSessionId(sessionId);
    }

    @NonNull
    public String getModeId() {
      return normalizeModeId(modeId);
    }

    @NonNull
    public String getDifficulty() {
      return LearningDifficulty.normalizeOrDefault(difficulty);
    }

    public int getPoints() {
      return Math.max(0, points);
    }

    public long getAwardedAtEpochMs() {
      return Math.max(0L, awardedAtEpochMs);
    }

    @Nullable
    static PendingPointAward normalizedCopy(@Nullable PendingPointAward source) {
      if (source == null) {
        return null;
      }
      String sessionId = normalizeSessionId(source.sessionId);
      if (sessionId.isEmpty()) {
        return null;
      }
      int safePoints = Math.max(0, source.points);
      if (safePoints <= 0) {
        return null;
      }
      return new PendingPointAward(
          sessionId,
          normalizeModeId(source.modeId),
          LearningDifficulty.normalizeOrDefault(source.difficulty),
          safePoints,
          Math.max(0L, source.awardedAtEpochMs));
    }
  }
}
