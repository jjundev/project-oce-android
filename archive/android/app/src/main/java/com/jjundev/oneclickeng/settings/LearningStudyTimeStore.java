package com.jjundev.oneclickeng.settings;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Stores and reads daily learning screen-visible time.
 *
 * <p>All calculations use local-day boundaries. Minutes are derived by floor(totalMillis / 60000).
 */
public final class LearningStudyTimeStore {
  static final String PREF_NAME = "learning_study_metrics";
  private static final String KEY_DAY_START_EPOCH_MS = "today_start_epoch_ms";
  private static final String KEY_TODAY_VISIBLE_MILLIS = "today_visible_millis";
  private static final String KEY_TOTAL_VISIBLE_MILLIS = "total_visible_millis";
  private static final String KEY_STUDY_DAY_KEYS = "study_day_keys";
  private static final String KEY_TOTAL_STUDY_DAYS = "total_study_days";
  private static final String KEY_STREAK_DAY_KEYS = "streak_day_keys";
  private static final String KEY_TOTAL_STREAK_DAYS = "total_streak_days";
  private static final long MILLIS_PER_MINUTE = 60_000L;

  @NonNull private final SharedPreferences preferences;
  @NonNull private final TimeProvider timeProvider;

  public LearningStudyTimeStore(@NonNull Context context) {
    this(
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
        new TimeProvider() {
          @Override
          public long currentTimeMillis() {
            return System.currentTimeMillis();
          }
        });
  }

  LearningStudyTimeStore(
      @NonNull SharedPreferences preferences, @NonNull TimeProvider timeProvider) {
    this.preferences = preferences;
    this.timeProvider = timeProvider;
  }

  /**
   * Records the visible interval and stores only the part that belongs to the interval end day.
   *
   * <p>Example: 23:50~00:10 records only 00:00~00:10 (10 min) for the end day.
   */
  public synchronized void recordVisibleInterval(long startEpochMs, long endEpochMs) {
    if (endEpochMs <= startEpochMs) {
      return;
    }

    long intervalMillis = endEpochMs - startEpochMs;
    if (intervalMillis <= 0L) {
      return;
    }

    long targetDayStart = resolveLocalDayStartEpochMs(endEpochMs);
    long effectiveStart = Math.max(startEpochMs, targetDayStart);
    long todayIntervalMillis = Math.max(0L, endEpochMs - effectiveStart);

    long storedDayStart = preferences.getLong(KEY_DAY_START_EPOCH_MS, Long.MIN_VALUE);
    long accumulatedMillis = preferences.getLong(KEY_TODAY_VISIBLE_MILLIS, 0L);
    long totalMillis = preferences.getLong(KEY_TOTAL_VISIBLE_MILLIS, 0L);
    Set<String> storedStudyDayKeys = readStringKeySet(KEY_STUDY_DAY_KEYS);
    Set<String> intervalStudyDayKeys = resolveDayKeysInInterval(startEpochMs, endEpochMs);
    storedStudyDayKeys.addAll(intervalStudyDayKeys);
    int totalStudyDays = storedStudyDayKeys.size();

    if (storedDayStart != targetDayStart) {
      accumulatedMillis = 0L;
    }
    if (accumulatedMillis < 0L) {
      accumulatedMillis = 0L;
    }
    if (totalMillis < 0L) {
      totalMillis = 0L;
    }

    long updatedTodayMillis = safeAdd(accumulatedMillis, todayIntervalMillis);
    long updatedTotalMillis = safeAdd(totalMillis, intervalMillis);
    preferences
        .edit()
        .putLong(KEY_DAY_START_EPOCH_MS, targetDayStart)
        .putLong(KEY_TODAY_VISIBLE_MILLIS, updatedTodayMillis)
        .putLong(KEY_TOTAL_VISIBLE_MILLIS, updatedTotalMillis)
        .putStringSet(KEY_STUDY_DAY_KEYS, new HashSet<>(storedStudyDayKeys))
        .putLong(KEY_TOTAL_STUDY_DAYS, totalStudyDays)
        .apply();
  }

  public synchronized int getTodayStudyMinutes() {
    long minutes = getLocalSnapshot().getTodayVisibleMillis() / MILLIS_PER_MINUTE;
    if (minutes >= Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    return (int) minutes;
  }

  public synchronized long getTotalStudyMillis() {
    return getLocalSnapshot().getTotalVisibleMillis();
  }

  public synchronized int getTotalStudyDays() {
    return getLocalSnapshot().getTotalStudyDays();
  }

  public synchronized int getTotalStreakDays() {
    return getLocalSnapshot().getTotalStreakDays();
  }

  public synchronized void recordAppEntry(long epochMs) {
    long dayStartEpochMs = resolveLocalDayStartEpochMs(epochMs);
    Set<String> streakDayKeys = readStringKeySet(KEY_STREAK_DAY_KEYS);
    boolean added = streakDayKeys.add(formatLocalDayKey(dayStartEpochMs));
    long storedTotalStreakDays = preferences.getLong(KEY_TOTAL_STREAK_DAYS, 0L);
    int totalStreakDays = streakDayKeys.size();
    if (!added && storedTotalStreakDays == totalStreakDays) {
      return;
    }
    preferences
        .edit()
        .putStringSet(KEY_STREAK_DAY_KEYS, new HashSet<>(streakDayKeys))
        .putLong(KEY_TOTAL_STREAK_DAYS, totalStreakDays)
        .apply();
  }

  public synchronized void applyTimeBonus(long bonusVisibleMillis) {
    long safeBonusVisibleMillis = Math.max(0L, bonusVisibleMillis);
    if (safeBonusVisibleMillis <= 0L) {
      return;
    }

    long todayStartEpochMs = resolveLocalDayStartEpochMs(timeProvider.currentTimeMillis());
    long storedDayStart = preferences.getLong(KEY_DAY_START_EPOCH_MS, Long.MIN_VALUE);
    long todayVisibleMillis = preferences.getLong(KEY_TODAY_VISIBLE_MILLIS, 0L);
    long totalVisibleMillis = preferences.getLong(KEY_TOTAL_VISIBLE_MILLIS, 0L);

    if (storedDayStart != todayStartEpochMs) {
      todayVisibleMillis = 0L;
      storedDayStart = todayStartEpochMs;
    }
    if (todayVisibleMillis < 0L) {
      todayVisibleMillis = 0L;
    }
    if (totalVisibleMillis < 0L) {
      totalVisibleMillis = 0L;
    }

    long updatedTodayVisibleMillis = safeAdd(todayVisibleMillis, safeBonusVisibleMillis);
    long updatedTotalVisibleMillis = safeAdd(totalVisibleMillis, safeBonusVisibleMillis);
    preferences
        .edit()
        .putLong(KEY_DAY_START_EPOCH_MS, storedDayStart)
        .putLong(KEY_TODAY_VISIBLE_MILLIS, updatedTodayVisibleMillis)
        .putLong(KEY_TOTAL_VISIBLE_MILLIS, updatedTotalVisibleMillis)
        .apply();
  }

  public synchronized void applyManualBonus(long bonusVisibleMillis, @NonNull String bonusDayKey) {
    long safeBonusVisibleMillis = Math.max(0L, bonusVisibleMillis);
    String normalizedBonusDayKey = bonusDayKey.trim();
    if (safeBonusVisibleMillis <= 0L || normalizedBonusDayKey.isEmpty()) {
      return;
    }

    long todayStartEpochMs = resolveLocalDayStartEpochMs(timeProvider.currentTimeMillis());
    long storedDayStart = preferences.getLong(KEY_DAY_START_EPOCH_MS, Long.MIN_VALUE);
    long todayVisibleMillis = preferences.getLong(KEY_TODAY_VISIBLE_MILLIS, 0L);
    long totalVisibleMillis = preferences.getLong(KEY_TOTAL_VISIBLE_MILLIS, 0L);
    Set<String> studyDayKeys = readStringKeySet(KEY_STUDY_DAY_KEYS);
    Set<String> streakDayKeys = readStringKeySet(KEY_STREAK_DAY_KEYS);

    if (storedDayStart != todayStartEpochMs) {
      todayVisibleMillis = 0L;
      storedDayStart = todayStartEpochMs;
    }
    if (todayVisibleMillis < 0L) {
      todayVisibleMillis = 0L;
    }
    if (totalVisibleMillis < 0L) {
      totalVisibleMillis = 0L;
    }

    long updatedTodayVisibleMillis = safeAdd(todayVisibleMillis, safeBonusVisibleMillis);
    long updatedTotalVisibleMillis = safeAdd(totalVisibleMillis, safeBonusVisibleMillis);
    studyDayKeys.add(normalizedBonusDayKey);
    streakDayKeys.add(normalizedBonusDayKey);

    preferences
        .edit()
        .putLong(KEY_DAY_START_EPOCH_MS, storedDayStart)
        .putLong(KEY_TODAY_VISIBLE_MILLIS, updatedTodayVisibleMillis)
        .putLong(KEY_TOTAL_VISIBLE_MILLIS, updatedTotalVisibleMillis)
        .putStringSet(KEY_STUDY_DAY_KEYS, new HashSet<>(studyDayKeys))
        .putLong(KEY_TOTAL_STUDY_DAYS, studyDayKeys.size())
        .putStringSet(KEY_STREAK_DAY_KEYS, new HashSet<>(streakDayKeys))
        .putLong(KEY_TOTAL_STREAK_DAYS, streakDayKeys.size())
        .apply();
  }

  public synchronized void resetAllMetrics() {
    preferences
        .edit()
        .remove(KEY_DAY_START_EPOCH_MS)
        .remove(KEY_TODAY_VISIBLE_MILLIS)
        .remove(KEY_TOTAL_VISIBLE_MILLIS)
        .remove(KEY_STUDY_DAY_KEYS)
        .remove(KEY_TOTAL_STUDY_DAYS)
        .remove(KEY_STREAK_DAY_KEYS)
        .remove(KEY_TOTAL_STREAK_DAYS)
        .apply();
  }

  @NonNull
  public synchronized StudyTimeSnapshot getLocalSnapshot() {
    long todayStart = resolveLocalDayStartEpochMs(timeProvider.currentTimeMillis());
    long storedDayStart = preferences.getLong(KEY_DAY_START_EPOCH_MS, Long.MIN_VALUE);
    long todayVisibleMillis = preferences.getLong(KEY_TODAY_VISIBLE_MILLIS, 0L);
    long totalVisibleMillis = preferences.getLong(KEY_TOTAL_VISIBLE_MILLIS, 0L);
    Set<String> studyDayKeys = readStringKeySet(KEY_STUDY_DAY_KEYS);
    Set<String> streakDayKeys = readStringKeySet(KEY_STREAK_DAY_KEYS);
    long storedTotalStudyDays = preferences.getLong(KEY_TOTAL_STUDY_DAYS, 0L);
    long storedTotalStreakDays = preferences.getLong(KEY_TOTAL_STREAK_DAYS, 0L);
    int totalStudyDays = studyDayKeys.size();
    int totalStreakDays = streakDayKeys.size();
    boolean shouldPersist = false;

    if (todayVisibleMillis < 0L) {
      todayVisibleMillis = 0L;
      shouldPersist = true;
    }
    if (totalVisibleMillis < 0L) {
      totalVisibleMillis = 0L;
      shouldPersist = true;
    }
    if (storedTotalStudyDays < 0L) {
      storedTotalStudyDays = 0L;
      shouldPersist = true;
    }
    if (storedTotalStreakDays < 0L) {
      storedTotalStreakDays = 0L;
      shouldPersist = true;
    }
    if (storedTotalStudyDays != totalStudyDays) {
      shouldPersist = true;
    }
    if (storedTotalStreakDays != totalStreakDays) {
      shouldPersist = true;
    }

    if (storedDayStart != todayStart) {
      todayVisibleMillis = 0L;
      storedDayStart = todayStart;
      shouldPersist = true;
    }

    if (shouldPersist) {
      preferences
          .edit()
          .putLong(KEY_DAY_START_EPOCH_MS, storedDayStart)
          .putLong(KEY_TODAY_VISIBLE_MILLIS, todayVisibleMillis)
          .putLong(KEY_TOTAL_VISIBLE_MILLIS, totalVisibleMillis)
          .putStringSet(KEY_STUDY_DAY_KEYS, new HashSet<>(studyDayKeys))
          .putStringSet(KEY_STREAK_DAY_KEYS, new HashSet<>(streakDayKeys))
          .putLong(KEY_TOTAL_STUDY_DAYS, totalStudyDays)
          .putLong(KEY_TOTAL_STREAK_DAYS, totalStreakDays)
          .apply();
    }

    return new StudyTimeSnapshot(
        totalVisibleMillis, todayVisibleMillis, totalStudyDays, totalStreakDays, storedDayStart);
  }

  private static long resolveLocalDayStartEpochMs(long epochMs) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(epochMs);
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
    return calendar.getTimeInMillis();
  }

  @NonNull
  private Set<String> readStringKeySet(@NonNull String key) {
    Set<String> stored = preferences.getStringSet(key, null);
    Set<String> result = new HashSet<>();
    if (stored == null) {
      return result;
    }
    for (String value : stored) {
      if (value == null) {
        continue;
      }
      String normalized = value.trim();
      if (!normalized.isEmpty()) {
        result.add(normalized);
      }
    }
    return result;
  }

  @NonNull
  private static Set<String> resolveDayKeysInInterval(long startEpochMs, long endEpochMs) {
    Set<String> result = new HashSet<>();
    if (endEpochMs <= startEpochMs) {
      return result;
    }

    long cursorDayStart = resolveLocalDayStartEpochMs(startEpochMs);
    while (cursorDayStart < endEpochMs) {
      long nextDayStart = resolveNextLocalDayStartEpochMs(cursorDayStart);
      long overlapStart = Math.max(startEpochMs, cursorDayStart);
      long overlapEnd = Math.min(endEpochMs, nextDayStart);
      if (overlapEnd > overlapStart) {
        result.add(formatLocalDayKey(cursorDayStart));
      }
      cursorDayStart = nextDayStart;
    }
    return result;
  }

  private static long resolveNextLocalDayStartEpochMs(long dayStartEpochMs) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(dayStartEpochMs);
    calendar.add(Calendar.DAY_OF_YEAR, 1);
    return calendar.getTimeInMillis();
  }

  @NonNull
  private static String formatLocalDayKey(long dayStartEpochMs) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(dayStartEpochMs);
    int year = calendar.get(Calendar.YEAR);
    int month = calendar.get(Calendar.MONTH) + 1;
    int day = calendar.get(Calendar.DAY_OF_MONTH);
    return String.format(Locale.US, "%04d-%02d-%02d", year, month, day);
  }

  private static long safeAdd(long base, long delta) {
    if (delta <= 0L) {
      return base;
    }
    if (base > Long.MAX_VALUE - delta) {
      return Long.MAX_VALUE;
    }
    return base + delta;
  }

  interface TimeProvider {
    long currentTimeMillis();
  }

  public static final class StudyTimeSnapshot {
    private final long totalVisibleMillis;
    private final long todayVisibleMillis;
    private final int totalStudyDays;
    private final int totalStreakDays;
    private final long dayStartEpochMs;

    public StudyTimeSnapshot(
        long totalVisibleMillis,
        long todayVisibleMillis,
        int totalStudyDays,
        int totalStreakDays,
        long dayStartEpochMs) {
      this.totalVisibleMillis = Math.max(0L, totalVisibleMillis);
      this.todayVisibleMillis = Math.max(0L, todayVisibleMillis);
      this.totalStudyDays = Math.max(0, totalStudyDays);
      this.totalStreakDays = Math.max(0, totalStreakDays);
      this.dayStartEpochMs = dayStartEpochMs;
    }

    public long getTotalVisibleMillis() {
      return totalVisibleMillis;
    }

    public long getTodayVisibleMillis() {
      return todayVisibleMillis;
    }

    public int getTotalStudyDays() {
      return totalStudyDays;
    }

    public int getTotalStreakDays() {
      return totalStreakDays;
    }

    public long getDayStartEpochMs() {
      return dayStartEpochMs;
    }
  }
}
