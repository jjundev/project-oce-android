package com.jjundev.oneclickeng.settings;

import static org.junit.Assert.assertEquals;

import android.content.SharedPreferences;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class LearningStudyTimeStoreTest {

  private SharedPreferences preferences;
  private MutableTimeProvider timeProvider;
  private LearningStudyTimeStore store;

  @Before
  public void setUp() {
    preferences = new InMemorySharedPreferences();
    timeProvider = new MutableTimeProvider(createLocalDateTimeEpochMs(2026, 2, 24, 10, 0, 0, 0));
    store = new LearningStudyTimeStore(preferences, timeProvider);
  }

  @Test
  public void sameDayAccumulation_persistsAcrossStoreRecreation() {
    long start = createLocalDateTimeEpochMs(2026, 2, 24, 9, 0, 0, 0);
    long end = createLocalDateTimeEpochMs(2026, 2, 24, 9, 2, 0, 0);

    store.recordVisibleInterval(start, end);
    assertEquals(2, store.getTodayStudyMinutes());
    assertEquals(120_000L, store.getTotalStudyMillis());
    assertEquals(1, store.getTotalStudyDays());
    assertEquals(0, store.getTotalStreakDays());

    LearningStudyTimeStore recreatedStore = new LearningStudyTimeStore(preferences, timeProvider);
    assertEquals(2, recreatedStore.getTodayStudyMinutes());
    assertEquals(120_000L, recreatedStore.getTotalStudyMillis());
    assertEquals(1, recreatedStore.getTotalStudyDays());
    assertEquals(0, recreatedStore.getTotalStreakDays());
  }

  @Test
  public void studyMinutes_floorByMinuteBoundary() {
    long firstStart = createLocalDateTimeEpochMs(2026, 2, 24, 10, 0, 0, 0);
    long firstEnd = createLocalDateTimeEpochMs(2026, 2, 24, 10, 0, 59, 0);
    store.recordVisibleInterval(firstStart, firstEnd);
    assertEquals(0, store.getTodayStudyMinutes());

    long secondStart = createLocalDateTimeEpochMs(2026, 2, 24, 10, 1, 0, 0);
    long secondEnd = createLocalDateTimeEpochMs(2026, 2, 24, 10, 1, 1, 0);
    store.recordVisibleInterval(secondStart, secondEnd);
    assertEquals(1, store.getTodayStudyMinutes());
    assertEquals(60_000L, store.getTotalStudyMillis());
    assertEquals(1, store.getTotalStudyDays());
    assertEquals(0, store.getTotalStreakDays());
  }

  @Test
  public void getTodayStudyMinutes_resetsWhenDateChanges() {
    long start = createLocalDateTimeEpochMs(2026, 2, 24, 9, 0, 0, 0);
    long end = createLocalDateTimeEpochMs(2026, 2, 24, 9, 10, 0, 0);

    store.recordVisibleInterval(start, end);
    assertEquals(10, store.getTodayStudyMinutes());
    assertEquals(600_000L, store.getTotalStudyMillis());
    assertEquals(1, store.getTotalStudyDays());

    timeProvider.setNowMs(createLocalDateTimeEpochMs(2026, 2, 25, 8, 0, 0, 0));
    assertEquals(0, store.getTodayStudyMinutes());
    assertEquals(600_000L, store.getTotalStudyMillis());
    assertEquals(1, store.getTotalStudyDays());
    assertEquals(0, store.getTotalStreakDays());
  }

  @Test
  public void midnightCrossing_recordsOnlyCurrentDayPortion() {
    long start = createLocalDateTimeEpochMs(2026, 2, 24, 23, 50, 0, 0);
    long end = createLocalDateTimeEpochMs(2026, 2, 25, 0, 10, 0, 0);

    store.recordVisibleInterval(start, end);
    timeProvider.setNowMs(createLocalDateTimeEpochMs(2026, 2, 25, 0, 20, 0, 0));

    assertEquals(10, store.getTodayStudyMinutes());
    assertEquals(1_200_000L, store.getTotalStudyMillis());
    assertEquals(2, store.getTotalStudyDays());
    assertEquals(0, store.getTotalStreakDays());
  }

  @Test
  public void localSnapshot_containsTotalAndTodayMillis() {
    long firstStart = createLocalDateTimeEpochMs(2026, 2, 24, 9, 0, 0, 0);
    long firstEnd = createLocalDateTimeEpochMs(2026, 2, 24, 9, 3, 0, 0);
    store.recordVisibleInterval(firstStart, firstEnd);

    LearningStudyTimeStore.StudyTimeSnapshot snapshot = store.getLocalSnapshot();
    assertEquals(180_000L, snapshot.getTodayVisibleMillis());
    assertEquals(180_000L, snapshot.getTotalVisibleMillis());
    assertEquals(1, snapshot.getTotalStudyDays());
    assertEquals(0, snapshot.getTotalStreakDays());
  }

  @Test
  public void recordAppEntry_sameDay_multipleEntries_keepsOneDay() {
    long morning = createLocalDateTimeEpochMs(2026, 2, 24, 8, 0, 0, 0);
    long night = createLocalDateTimeEpochMs(2026, 2, 24, 22, 30, 0, 0);

    store.recordAppEntry(morning);
    store.recordAppEntry(night);

    assertEquals(1, store.getTotalStreakDays());
  }

  @Test
  public void recordAppEntry_differentDays_increasesStreakDays() {
    long day1 = createLocalDateTimeEpochMs(2026, 2, 24, 8, 0, 0, 0);
    long day2 = createLocalDateTimeEpochMs(2026, 2, 25, 8, 0, 0, 0);

    store.recordAppEntry(day1);
    store.recordAppEntry(day2);

    assertEquals(2, store.getTotalStreakDays());
  }

  @Test
  public void recordAppEntry_andStudyInterval_areIndependent() {
    long appEntryDay1 = createLocalDateTimeEpochMs(2026, 2, 24, 8, 0, 0, 0);
    long appEntryDay2 = createLocalDateTimeEpochMs(2026, 2, 25, 8, 0, 0, 0);
    long studyStart = createLocalDateTimeEpochMs(2026, 2, 24, 10, 0, 0, 0);
    long studyEnd = createLocalDateTimeEpochMs(2026, 2, 24, 10, 5, 0, 0);

    store.recordAppEntry(appEntryDay1);
    store.recordVisibleInterval(studyStart, studyEnd);
    store.recordAppEntry(appEntryDay2);

    assertEquals(1, store.getTotalStudyDays());
    assertEquals(2, store.getTotalStreakDays());
  }

  @Test
  public void applyTimeBonus_once_increasesTimeOnly() {
    store.applyTimeBonus(600_000L);

    assertEquals(10, store.getTodayStudyMinutes());
    assertEquals(600_000L, store.getTotalStudyMillis());
    assertEquals(0, store.getTotalStudyDays());
    assertEquals(0, store.getTotalStreakDays());
  }

  @Test
  public void applyTimeBonus_twice_accumulatesTimeOnly() {
    store.applyTimeBonus(600_000L);
    store.applyTimeBonus(600_000L);

    assertEquals(20, store.getTodayStudyMinutes());
    assertEquals(1_200_000L, store.getTotalStudyMillis());
    assertEquals(0, store.getTotalStudyDays());
    assertEquals(0, store.getTotalStreakDays());
  }

  @Test
  public void applyManualBonus_once_increasesTimeAndBothDayCounts() {
    store.applyManualBonus(1_200_000L, "creator_bonus_day_1");

    assertEquals(20, store.getTodayStudyMinutes());
    assertEquals(1_200_000L, store.getTotalStudyMillis());
    assertEquals(1, store.getTotalStudyDays());
    assertEquals(1, store.getTotalStreakDays());
  }

  @Test
  public void applyManualBonus_uniqueKeys_accumulatesTimeAndBothDayCounts() {
    store.applyManualBonus(1_200_000L, "creator_bonus_day_1");
    store.applyManualBonus(1_200_000L, "creator_bonus_day_2");

    assertEquals(40, store.getTodayStudyMinutes());
    assertEquals(2_400_000L, store.getTotalStudyMillis());
    assertEquals(2, store.getTotalStudyDays());
    assertEquals(2, store.getTotalStreakDays());
  }

  @Test
  public void resetAllMetrics_clearsAllAccumulatedValues() {
    long appEntry = createLocalDateTimeEpochMs(2026, 2, 24, 8, 0, 0, 0);
    long studyStart = createLocalDateTimeEpochMs(2026, 2, 24, 10, 0, 0, 0);
    long studyEnd = createLocalDateTimeEpochMs(2026, 2, 24, 10, 5, 0, 0);
    store.recordAppEntry(appEntry);
    store.recordVisibleInterval(studyStart, studyEnd);

    store.resetAllMetrics();

    assertEquals(0, store.getTodayStudyMinutes());
    assertEquals(0L, store.getTotalStudyMillis());
    assertEquals(0, store.getTotalStudyDays());
    assertEquals(0, store.getTotalStreakDays());

    LearningStudyTimeStore recreatedStore = new LearningStudyTimeStore(preferences, timeProvider);
    assertEquals(0, recreatedStore.getTodayStudyMinutes());
    assertEquals(0L, recreatedStore.getTotalStudyMillis());
    assertEquals(0, recreatedStore.getTotalStudyDays());
    assertEquals(0, recreatedStore.getTotalStreakDays());
  }

  private static long createLocalDateTimeEpochMs(
      int year, int month, int day, int hour, int minute, int second, int millisecond) {
    Calendar calendar = Calendar.getInstance();
    calendar.set(Calendar.YEAR, year);
    calendar.set(Calendar.MONTH, month - 1);
    calendar.set(Calendar.DAY_OF_MONTH, day);
    calendar.set(Calendar.HOUR_OF_DAY, hour);
    calendar.set(Calendar.MINUTE, minute);
    calendar.set(Calendar.SECOND, second);
    calendar.set(Calendar.MILLISECOND, millisecond);
    return calendar.getTimeInMillis();
  }

  private static final class MutableTimeProvider implements LearningStudyTimeStore.TimeProvider {
    private long nowMs;

    private MutableTimeProvider(long nowMs) {
      this.nowMs = nowMs;
    }

    @Override
    public long currentTimeMillis() {
      return nowMs;
    }

    private void setNowMs(long nowMs) {
      this.nowMs = nowMs;
    }
  }

  private static final class InMemorySharedPreferences implements SharedPreferences {
    private final Map<String, Object> values = new HashMap<>();

    @Override
    public Map<String, ?> getAll() {
      return new HashMap<>(values);
    }

    @Override
    public String getString(String key, String defValue) {
      Object value = values.get(key);
      return value instanceof String ? (String) value : defValue;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
      Object value = values.get(key);
      if (value instanceof Set) {
        return new HashSet<>((Set<String>) value);
      }
      return defValues;
    }

    @Override
    public int getInt(String key, int defValue) {
      Object value = values.get(key);
      return value instanceof Integer ? (Integer) value : defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
      Object value = values.get(key);
      return value instanceof Long ? (Long) value : defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
      Object value = values.get(key);
      return value instanceof Float ? (Float) value : defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
      Object value = values.get(key);
      return value instanceof Boolean ? (Boolean) value : defValue;
    }

    @Override
    public boolean contains(String key) {
      return values.containsKey(key);
    }

    @Override
    public Editor edit() {
      return new EditorImpl();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(
        OnSharedPreferenceChangeListener listener) {}

    @Override
    public void unregisterOnSharedPreferenceChangeListener(
        OnSharedPreferenceChangeListener listener) {}

    private final class EditorImpl implements Editor {
      private final Map<String, Object> staged = new HashMap<>();
      private final Set<String> removals = new HashSet<>();
      private boolean clearRequested;

      @Override
      public Editor putString(String key, String value) {
        staged.put(key, value);
        removals.remove(key);
        return this;
      }

      @Override
      public Editor putStringSet(String key, Set<String> values) {
        staged.put(key, values == null ? null : new HashSet<>(values));
        removals.remove(key);
        return this;
      }

      @Override
      public Editor putInt(String key, int value) {
        staged.put(key, value);
        removals.remove(key);
        return this;
      }

      @Override
      public Editor putLong(String key, long value) {
        staged.put(key, value);
        removals.remove(key);
        return this;
      }

      @Override
      public Editor putFloat(String key, float value) {
        staged.put(key, value);
        removals.remove(key);
        return this;
      }

      @Override
      public Editor putBoolean(String key, boolean value) {
        staged.put(key, value);
        removals.remove(key);
        return this;
      }

      @Override
      public Editor remove(String key) {
        removals.add(key);
        staged.remove(key);
        return this;
      }

      @Override
      public Editor clear() {
        clearRequested = true;
        staged.clear();
        removals.clear();
        return this;
      }

      @Override
      public boolean commit() {
        if (clearRequested) {
          values.clear();
          clearRequested = false;
        }
        for (String key : removals) {
          values.remove(key);
        }
        for (Map.Entry<String, Object> entry : staged.entrySet()) {
          if (entry.getValue() == null) {
            values.remove(entry.getKey());
          } else {
            values.put(entry.getKey(), entry.getValue());
          }
        }
        removals.clear();
        staged.clear();
        return true;
      }

      @Override
      public void apply() {
        commit();
      }
    }
  }
}
