package com.jjundev.oneclickeng.settings;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Synchronizes study time metrics with Firestore using user-scoped pending deltas. */
public final class LearningStudyTimeCloudRepository {
  private static final String COLLECTION_USERS = "users";
  private static final String COLLECTION_LEARNING_METRICS = "learning_metrics";
  private static final String DOCUMENT_STUDY_TIME = "study_time";

  private static final String FIELD_TOTAL_VISIBLE_MILLIS = "total_visible_millis";
  private static final String FIELD_TODAY_VISIBLE_MILLIS = "today_visible_millis";
  private static final String FIELD_TODAY_DAY_START_EPOCH_MS = "today_day_start_epoch_ms";
  private static final String FIELD_UPDATED_AT_EPOCH_MS = "updated_at_epoch_ms";
  private static final String FIELD_STUDY_DAY_KEYS = "study_day_keys";
  private static final String FIELD_TOTAL_STUDY_DAYS = "total_study_days";
  private static final String FIELD_STREAK_DAY_KEYS = "streak_day_keys";
  private static final String FIELD_TOTAL_STREAK_DAYS = "total_streak_days";

  private static final String KEY_PENDING_UID = "pending_uid";
  private static final String KEY_PENDING_TOTAL_DELTA_MILLIS = "pending_total_delta_millis";
  private static final String KEY_PENDING_TODAY_DELTA_MILLIS = "pending_today_delta_millis";
  private static final String KEY_PENDING_DAY_START_EPOCH_MS = "pending_day_start_epoch_ms";
  private static final String KEY_PENDING_STUDY_DAY_KEYS = "pending_study_day_keys";
  private static final String KEY_PENDING_STREAK_DAY_KEYS = "pending_streak_day_keys";

  @NonNull private final FirebaseAuth auth;
  @NonNull private final FirebaseFirestore firestore;
  @NonNull private final SharedPreferences preferences;
  @NonNull private final TimeProvider timeProvider;

  public LearningStudyTimeCloudRepository(@NonNull Context context) {
    this(
        FirebaseAuth.getInstance(),
        FirebaseFirestore.getInstance(),
        context.getSharedPreferences(LearningStudyTimeStore.PREF_NAME, Context.MODE_PRIVATE),
        new TimeProvider() {
          @Override
          public long currentTimeMillis() {
            return System.currentTimeMillis();
          }
        });
  }

  LearningStudyTimeCloudRepository(
      @NonNull FirebaseAuth auth,
      @NonNull FirebaseFirestore firestore,
      @NonNull SharedPreferences preferences,
      @NonNull TimeProvider timeProvider) {
    this.auth = auth;
    this.firestore = firestore;
    this.preferences = preferences;
    this.timeProvider = timeProvider;
  }

  public void recordIntervalForCurrentUser(long startEpochMs, long endEpochMs) {
    if (endEpochMs <= startEpochMs) {
      return;
    }
    FirebaseUser user = auth.getCurrentUser();
    if (user == null) {
      return;
    }

    long totalDeltaMillis = endEpochMs - startEpochMs;
    if (totalDeltaMillis <= 0L) {
      return;
    }

    long dayStart = resolveLocalDayStartEpochMs(endEpochMs);
    long todayDeltaMillis = Math.max(0L, endEpochMs - Math.max(startEpochMs, dayStart));
    Set<String> intervalStudyDayKeys = resolveDayKeysInInterval(startEpochMs, endEpochMs);
    appendPending(
        user.getUid(),
        new PendingDelta(
            totalDeltaMillis, todayDeltaMillis, dayStart, intervalStudyDayKeys, new HashSet<>()));
    flushPendingForCurrentUser();
  }

  public void recordAppEntryForCurrentUser(long epochMs) {
    FirebaseUser user = auth.getCurrentUser();
    if (user == null) {
      return;
    }
    long dayStart = resolveLocalDayStartEpochMs(epochMs);
    Set<String> streakDayKeys = new HashSet<>();
    streakDayKeys.add(formatLocalDayKey(dayStart));
    appendPending(
        user.getUid(), new PendingDelta(0L, 0L, dayStart, new HashSet<>(), streakDayKeys));
    flushPendingForCurrentUser();
  }

  public void applyManualBonusForCurrentUser(long bonusVisibleMillis, @NonNull String bonusDayKey) {
    FirebaseUser user = auth.getCurrentUser();
    if (user == null) {
      return;
    }

    long safeBonusVisibleMillis = Math.max(0L, bonusVisibleMillis);
    String normalizedBonusDayKey = bonusDayKey.trim();
    if (safeBonusVisibleMillis <= 0L || normalizedBonusDayKey.isEmpty()) {
      return;
    }

    long dayStart = resolveLocalDayStartEpochMs(timeProvider.currentTimeMillis());
    Set<String> bonusStudyDayKeys = new HashSet<>();
    bonusStudyDayKeys.add(normalizedBonusDayKey);
    Set<String> bonusStreakDayKeys = new HashSet<>();
    bonusStreakDayKeys.add(normalizedBonusDayKey);

    appendPending(
        user.getUid(),
        new PendingDelta(
            safeBonusVisibleMillis,
            safeBonusVisibleMillis,
            dayStart,
            bonusStudyDayKeys,
            bonusStreakDayKeys));
    flushPendingForCurrentUser();
  }

  public void applyTimeBonusForCurrentUser(long bonusVisibleMillis) {
    FirebaseUser user = auth.getCurrentUser();
    if (user == null) {
      return;
    }

    long safeBonusVisibleMillis = Math.max(0L, bonusVisibleMillis);
    if (safeBonusVisibleMillis <= 0L) {
      return;
    }

    long nowEpochMs = timeProvider.currentTimeMillis();
    long todayStartEpochMs = resolveLocalDayStartEpochMs(nowEpochMs);
    DocumentReference studyTimeRef =
        firestore
            .collection(COLLECTION_USERS)
            .document(user.getUid())
            .collection(COLLECTION_LEARNING_METRICS)
            .document(DOCUMENT_STUDY_TIME);

    firestore
        .runTransaction(
            transaction -> {
              DocumentSnapshot snapshot = transaction.get(studyTimeRef);
              TimeBonusMergeResult merged =
                  mergeTimeBonus(
                      getLong(snapshot, FIELD_TOTAL_VISIBLE_MILLIS),
                      getLong(snapshot, FIELD_TODAY_VISIBLE_MILLIS),
                      getLong(snapshot, FIELD_TODAY_DAY_START_EPOCH_MS),
                      safeBonusVisibleMillis,
                      todayStartEpochMs);

              Map<String, Object> updates = new HashMap<>();
              updates.put(FIELD_TOTAL_VISIBLE_MILLIS, merged.totalVisibleMillis);
              updates.put(FIELD_TODAY_VISIBLE_MILLIS, merged.todayVisibleMillis);
              updates.put(FIELD_TODAY_DAY_START_EPOCH_MS, merged.dayStartEpochMs);
              updates.put(FIELD_UPDATED_AT_EPOCH_MS, nowEpochMs);
              transaction.set(studyTimeRef, updates, SetOptions.merge());
              return null;
            })
        .addOnSuccessListener(unused -> {})
        .addOnFailureListener(error -> {});
  }

  public void resetMetricsForCurrentUser(@Nullable CompletionCallback callback) {
    FirebaseUser user = auth.getCurrentUser();
    if (user == null) {
      if (callback != null) {
        callback.onComplete(false);
      }
      return;
    }

    long nowEpochMs = timeProvider.currentTimeMillis();
    long dayStartEpochMs = resolveLocalDayStartEpochMs(nowEpochMs);
    Map<String, Object> updates = new HashMap<>();
    updates.put(FIELD_TOTAL_VISIBLE_MILLIS, 0L);
    updates.put(FIELD_TODAY_VISIBLE_MILLIS, 0L);
    updates.put(FIELD_TODAY_DAY_START_EPOCH_MS, dayStartEpochMs);
    updates.put(FIELD_STUDY_DAY_KEYS, new ArrayList<>());
    updates.put(FIELD_TOTAL_STUDY_DAYS, 0);
    updates.put(FIELD_STREAK_DAY_KEYS, new ArrayList<>());
    updates.put(FIELD_TOTAL_STREAK_DAYS, 0);
    updates.put(FIELD_UPDATED_AT_EPOCH_MS, nowEpochMs);

    firestore
        .collection(COLLECTION_USERS)
        .document(user.getUid())
        .collection(COLLECTION_LEARNING_METRICS)
        .document(DOCUMENT_STUDY_TIME)
        .set(updates, SetOptions.merge())
        .addOnSuccessListener(
            unused -> {
              clearPending();
              if (callback != null) {
                callback.onComplete(true);
              }
            })
        .addOnFailureListener(
            error -> {
              if (callback != null) {
                callback.onComplete(false);
              }
            });
  }

  public void flushPendingForCurrentUser() {
    flushPendingForCurrentUser(null);
  }

  public void flushPendingForCurrentUser(@Nullable CompletionCallback callback) {
    FirebaseUser user = auth.getCurrentUser();
    if (user == null) {
      if (callback != null) {
        callback.onComplete(false);
      }
      return;
    }

    PendingState pending = readPending();
    if (pending == null) {
      if (callback != null) {
        callback.onComplete(true);
      }
      return;
    }

    String currentUid = user.getUid();
    if (!currentUid.equals(pending.uid)) {
      clearPending();
      if (callback != null) {
        callback.onComplete(true);
      }
      return;
    }

    if (!pending.delta.hasAnyData()) {
      clearPending();
      if (callback != null) {
        callback.onComplete(true);
      }
      return;
    }

    firestore
        .collection(COLLECTION_USERS)
        .document(currentUid)
        .collection(COLLECTION_LEARNING_METRICS)
        .document(DOCUMENT_STUDY_TIME)
        .getFirestore()
        .runTransaction(
            transaction -> {
              DocumentSnapshot snapshot =
                  transaction.get(
                      firestore
                          .collection(COLLECTION_USERS)
                          .document(currentUid)
                          .collection(COLLECTION_LEARNING_METRICS)
                          .document(DOCUMENT_STUDY_TIME));

              long remoteTotal = getLong(snapshot, FIELD_TOTAL_VISIBLE_MILLIS);
              long remoteToday = getLong(snapshot, FIELD_TODAY_VISIBLE_MILLIS);
              long remoteDay = getLong(snapshot, FIELD_TODAY_DAY_START_EPOCH_MS);
              Set<String> remoteStudyDayKeys = readStudyDayKeys(snapshot, FIELD_STUDY_DAY_KEYS);
              Set<String> remoteStreakDayKeys = readStudyDayKeys(snapshot, FIELD_STREAK_DAY_KEYS);

              MergedMetrics merged =
                  mergeRemoteWithPending(
                      remoteTotal,
                      remoteToday,
                      remoteDay,
                      remoteStudyDayKeys,
                      remoteStreakDayKeys,
                      pending.delta.totalDeltaMillis,
                      pending.delta.todayDeltaMillis,
                      pending.delta.dayStartEpochMs,
                      pending.delta.studyDayKeys,
                      pending.delta.streakDayKeys);

              long updatedAt = timeProvider.currentTimeMillis();
              Map<String, Object> updates = new HashMap<>();
              updates.put(FIELD_TOTAL_VISIBLE_MILLIS, merged.totalVisibleMillis);
              updates.put(FIELD_TODAY_VISIBLE_MILLIS, merged.todayVisibleMillis);
              updates.put(FIELD_TODAY_DAY_START_EPOCH_MS, merged.dayStartEpochMs);
              updates.put(FIELD_STUDY_DAY_KEYS, new ArrayList<>(merged.studyDayKeys));
              updates.put(FIELD_TOTAL_STUDY_DAYS, merged.totalStudyDays);
              updates.put(FIELD_STREAK_DAY_KEYS, new ArrayList<>(merged.streakDayKeys));
              updates.put(FIELD_TOTAL_STREAK_DAYS, merged.totalStreakDays);
              updates.put(FIELD_UPDATED_AT_EPOCH_MS, updatedAt);

              transaction.set(
                  firestore
                      .collection(COLLECTION_USERS)
                      .document(currentUid)
                      .collection(COLLECTION_LEARNING_METRICS)
                      .document(DOCUMENT_STUDY_TIME),
                  updates,
                  SetOptions.merge());
              return null;
            })
        .addOnSuccessListener(
            unused -> {
              clearPending();
              if (callback != null) {
                callback.onComplete(true);
              }
            })
        .addOnFailureListener(
            error -> {
              if (callback != null) {
                callback.onComplete(false);
              }
            });
  }

  public void fetchCurrentUserSnapshot(@NonNull SnapshotCallback callback) {
    FirebaseUser user = auth.getCurrentUser();
    if (user == null) {
      callback.onNoUser();
      return;
    }

    firestore
        .collection(COLLECTION_USERS)
        .document(user.getUid())
        .collection(COLLECTION_LEARNING_METRICS)
        .document(DOCUMENT_STUDY_TIME)
        .get()
        .addOnSuccessListener(
            snapshot -> {
              long now = timeProvider.currentTimeMillis();
              long todayStart = resolveLocalDayStartEpochMs(now);

              long totalVisibleMillis = Math.max(0L, getLong(snapshot, FIELD_TOTAL_VISIBLE_MILLIS));
              long remoteTodayVisibleMillis =
                  Math.max(0L, getLong(snapshot, FIELD_TODAY_VISIBLE_MILLIS));
              long remoteDayStart = getLong(snapshot, FIELD_TODAY_DAY_START_EPOCH_MS);
              long todayVisibleMillis =
                  remoteDayStart == todayStart ? remoteTodayVisibleMillis : 0L;

              Set<String> studyDayKeys = readStudyDayKeys(snapshot, FIELD_STUDY_DAY_KEYS);
              Set<String> streakDayKeys = readStudyDayKeys(snapshot, FIELD_STREAK_DAY_KEYS);
              long storedTotalStudyDays = getLong(snapshot, FIELD_TOTAL_STUDY_DAYS);
              long storedTotalStreakDays = getLong(snapshot, FIELD_TOTAL_STREAK_DAYS);
              int totalStudyDays = resolveTotalDays(studyDayKeys, storedTotalStudyDays);
              int totalStreakDays = resolveTotalDays(streakDayKeys, storedTotalStreakDays);

              callback.onSuccess(
                  new LearningStudyTimeStore.StudyTimeSnapshot(
                      totalVisibleMillis,
                      todayVisibleMillis,
                      totalStudyDays,
                      totalStreakDays,
                      todayStart));
            })
        .addOnFailureListener(error -> callback.onFailure());
  }

  static MergedMetrics mergeRemoteWithPending(
      long remoteTotalMillis,
      long remoteTodayMillis,
      long remoteDayStartEpochMs,
      @NonNull Set<String> remoteStudyDayKeys,
      @NonNull Set<String> remoteStreakDayKeys,
      long pendingTotalMillis,
      long pendingTodayMillis,
      long pendingDayStartEpochMs,
      @NonNull Set<String> pendingStudyDayKeys,
      @NonNull Set<String> pendingStreakDayKeys) {
    long safeRemoteTotal = Math.max(0L, remoteTotalMillis);
    long safeRemoteToday = Math.max(0L, remoteTodayMillis);
    long safePendingTotal = Math.max(0L, pendingTotalMillis);
    long safePendingToday = Math.max(0L, pendingTodayMillis);

    long mergedTotal = safeAdd(safeRemoteTotal, safePendingTotal);
    long mergedToday =
        remoteDayStartEpochMs == pendingDayStartEpochMs
            ? safeAdd(safeRemoteToday, safePendingToday)
            : safePendingToday;

    Set<String> mergedStudyDayKeys = new HashSet<>(normalizeStringSet(remoteStudyDayKeys));
    mergedStudyDayKeys.addAll(normalizeStringSet(pendingStudyDayKeys));
    int totalStudyDays = mergedStudyDayKeys.size();
    Set<String> mergedStreakDayKeys = new HashSet<>(normalizeStringSet(remoteStreakDayKeys));
    mergedStreakDayKeys.addAll(normalizeStringSet(pendingStreakDayKeys));
    int totalStreakDays = mergedStreakDayKeys.size();

    return new MergedMetrics(
        mergedTotal,
        mergedToday,
        pendingDayStartEpochMs,
        totalStudyDays,
        mergedStudyDayKeys,
        totalStreakDays,
        mergedStreakDayKeys);
  }

  static TimeBonusMergeResult mergeTimeBonus(
      long remoteTotalMillis,
      long remoteTodayMillis,
      long remoteDayStartEpochMs,
      long bonusVisibleMillis,
      long bonusDayStartEpochMs) {
    long safeRemoteTotal = Math.max(0L, remoteTotalMillis);
    long safeRemoteToday = Math.max(0L, remoteTodayMillis);
    long safeBonusVisibleMillis = Math.max(0L, bonusVisibleMillis);
    long mergedTotalVisibleMillis = safeAdd(safeRemoteTotal, safeBonusVisibleMillis);
    long mergedTodayVisibleMillis =
        remoteDayStartEpochMs == bonusDayStartEpochMs
            ? safeAdd(safeRemoteToday, safeBonusVisibleMillis)
            : safeBonusVisibleMillis;
    return new TimeBonusMergeResult(
        mergedTotalVisibleMillis, mergedTodayVisibleMillis, bonusDayStartEpochMs);
  }

  private void appendPending(@NonNull String uid, @NonNull PendingDelta delta) {
    PendingState existing = readPending();
    PendingDelta mergedDelta = delta;

    if (existing != null && uid.equals(existing.uid)) {
      long mergedTotalDelta = safeAdd(existing.delta.totalDeltaMillis, delta.totalDeltaMillis);
      long mergedTodayDelta =
          existing.delta.dayStartEpochMs == delta.dayStartEpochMs
              ? safeAdd(existing.delta.todayDeltaMillis, delta.todayDeltaMillis)
              : delta.todayDeltaMillis;
      Set<String> mergedStudyDayKeys = new HashSet<>(existing.delta.studyDayKeys);
      mergedStudyDayKeys.addAll(delta.studyDayKeys);
      Set<String> mergedStreakDayKeys = new HashSet<>(existing.delta.streakDayKeys);
      mergedStreakDayKeys.addAll(delta.streakDayKeys);
      mergedDelta =
          new PendingDelta(
              mergedTotalDelta,
              mergedTodayDelta,
              delta.dayStartEpochMs,
              mergedStudyDayKeys,
              mergedStreakDayKeys);
    }

    preferences
        .edit()
        .putString(KEY_PENDING_UID, uid)
        .putLong(KEY_PENDING_TOTAL_DELTA_MILLIS, mergedDelta.totalDeltaMillis)
        .putLong(KEY_PENDING_TODAY_DELTA_MILLIS, mergedDelta.todayDeltaMillis)
        .putLong(KEY_PENDING_DAY_START_EPOCH_MS, mergedDelta.dayStartEpochMs)
        .putStringSet(KEY_PENDING_STUDY_DAY_KEYS, new HashSet<>(mergedDelta.studyDayKeys))
        .putStringSet(KEY_PENDING_STREAK_DAY_KEYS, new HashSet<>(mergedDelta.streakDayKeys))
        .apply();
  }

  @Nullable
  private PendingState readPending() {
    String uid = preferences.getString(KEY_PENDING_UID, null);
    if (uid == null || uid.trim().isEmpty()) {
      return null;
    }

    long totalDeltaMillis = preferences.getLong(KEY_PENDING_TOTAL_DELTA_MILLIS, 0L);
    long todayDeltaMillis = preferences.getLong(KEY_PENDING_TODAY_DELTA_MILLIS, 0L);
    long dayStartEpochMs = preferences.getLong(KEY_PENDING_DAY_START_EPOCH_MS, Long.MIN_VALUE);
    Set<String> studyDayKeys =
        normalizeStringSet(preferences.getStringSet(KEY_PENDING_STUDY_DAY_KEYS, null));
    Set<String> streakDayKeys =
        normalizeStringSet(preferences.getStringSet(KEY_PENDING_STREAK_DAY_KEYS, null));

    if (dayStartEpochMs == Long.MIN_VALUE) {
      if (totalDeltaMillis <= 0L
          && todayDeltaMillis <= 0L
          && studyDayKeys.isEmpty()
          && streakDayKeys.isEmpty()) {
        return null;
      }
      dayStartEpochMs = resolveLocalDayStartEpochMs(timeProvider.currentTimeMillis());
    }
    if (totalDeltaMillis <= 0L && studyDayKeys.isEmpty() && streakDayKeys.isEmpty()) {
      return null;
    }
    if (totalDeltaMillis > 0L && studyDayKeys.isEmpty()) {
      studyDayKeys.add(formatLocalDayKey(dayStartEpochMs));
    }

    return new PendingState(
        uid,
        new PendingDelta(
            Math.max(0L, totalDeltaMillis),
            Math.max(0L, todayDeltaMillis),
            dayStartEpochMs,
            studyDayKeys,
            streakDayKeys));
  }

  private void clearPending() {
    preferences
        .edit()
        .remove(KEY_PENDING_UID)
        .remove(KEY_PENDING_TOTAL_DELTA_MILLIS)
        .remove(KEY_PENDING_TODAY_DELTA_MILLIS)
        .remove(KEY_PENDING_DAY_START_EPOCH_MS)
        .remove(KEY_PENDING_STUDY_DAY_KEYS)
        .remove(KEY_PENDING_STREAK_DAY_KEYS)
        .apply();
  }

  @NonNull
  private static Set<String> readStudyDayKeys(
      @Nullable DocumentSnapshot snapshot, @NonNull String fieldName) {
    Set<String> result = new HashSet<>();
    if (snapshot == null || !snapshot.exists()) {
      return result;
    }
    Object raw = snapshot.get(fieldName);
    if (!(raw instanceof List)) {
      return result;
    }
    List<?> rawList = (List<?>) raw;
    for (Object item : rawList) {
      if (item instanceof String) {
        String normalized = ((String) item).trim();
        if (!normalized.isEmpty()) {
          result.add(normalized);
        }
      }
    }
    return result;
  }

  @NonNull
  private static Set<String> normalizeStringSet(@Nullable Set<String> raw) {
    Set<String> result = new HashSet<>();
    if (raw == null) {
      return result;
    }
    for (String item : raw) {
      if (item == null) {
        continue;
      }
      String normalized = item.trim();
      if (!normalized.isEmpty()) {
        result.add(normalized);
      }
    }
    return result;
  }

  private static int resolveTotalDays(@NonNull Set<String> dayKeys, long storedTotalDays) {
    int dayKeyCount = dayKeys.size();
    if (dayKeyCount > 0) {
      return dayKeyCount;
    }
    if (storedTotalDays <= 0L) {
      return 0;
    }
    if (storedTotalDays >= Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    return (int) storedTotalDays;
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

  private static long getLong(@Nullable DocumentSnapshot snapshot, @NonNull String field) {
    if (snapshot == null || !snapshot.exists()) {
      return 0L;
    }
    Long value = snapshot.getLong(field);
    return value == null ? 0L : value;
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

  public interface CompletionCallback {
    void onComplete(boolean success);
  }

  public interface SnapshotCallback {
    void onSuccess(@NonNull LearningStudyTimeStore.StudyTimeSnapshot snapshot);

    void onFailure();

    void onNoUser();
  }

  static final class MergedMetrics {
    final long totalVisibleMillis;
    final long todayVisibleMillis;
    final long dayStartEpochMs;
    final int totalStudyDays;
    @NonNull final Set<String> studyDayKeys;
    final int totalStreakDays;
    @NonNull final Set<String> streakDayKeys;

    MergedMetrics(
        long totalVisibleMillis,
        long todayVisibleMillis,
        long dayStartEpochMs,
        int totalStudyDays,
        @NonNull Set<String> studyDayKeys,
        int totalStreakDays,
        @NonNull Set<String> streakDayKeys) {
      this.totalVisibleMillis = Math.max(0L, totalVisibleMillis);
      this.todayVisibleMillis = Math.max(0L, todayVisibleMillis);
      this.dayStartEpochMs = dayStartEpochMs;
      this.totalStudyDays = Math.max(0, totalStudyDays);
      this.studyDayKeys = new HashSet<>(studyDayKeys);
      this.totalStreakDays = Math.max(0, totalStreakDays);
      this.streakDayKeys = new HashSet<>(streakDayKeys);
    }
  }

  static final class TimeBonusMergeResult {
    final long totalVisibleMillis;
    final long todayVisibleMillis;
    final long dayStartEpochMs;

    TimeBonusMergeResult(long totalVisibleMillis, long todayVisibleMillis, long dayStartEpochMs) {
      this.totalVisibleMillis = Math.max(0L, totalVisibleMillis);
      this.todayVisibleMillis = Math.max(0L, todayVisibleMillis);
      this.dayStartEpochMs = dayStartEpochMs;
    }
  }

  private static final class PendingDelta {
    final long totalDeltaMillis;
    final long todayDeltaMillis;
    final long dayStartEpochMs;
    @NonNull final Set<String> studyDayKeys;
    @NonNull final Set<String> streakDayKeys;

    private PendingDelta(
        long totalDeltaMillis,
        long todayDeltaMillis,
        long dayStartEpochMs,
        @NonNull Set<String> studyDayKeys,
        @NonNull Set<String> streakDayKeys) {
      this.totalDeltaMillis = Math.max(0L, totalDeltaMillis);
      this.todayDeltaMillis = Math.max(0L, todayDeltaMillis);
      this.dayStartEpochMs = dayStartEpochMs;
      this.studyDayKeys = new HashSet<>(normalizeStringSet(studyDayKeys));
      this.streakDayKeys = new HashSet<>(normalizeStringSet(streakDayKeys));
    }

    private boolean hasAnyData() {
      return totalDeltaMillis > 0L || !studyDayKeys.isEmpty() || !streakDayKeys.isEmpty();
    }
  }

  private static final class PendingState {
    @NonNull final String uid;
    @NonNull final PendingDelta delta;

    private PendingState(@NonNull String uid, @NonNull PendingDelta delta) {
      this.uid = uid;
      this.delta = delta;
    }
  }
}
