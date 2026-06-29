package com.jjundev.oneclickeng.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class LearningPointCloudRepositoryLogicTest {
  private SharedPreferences preferences;
  private Type pendingAwardListType;
  private LearningPointStore pointStore;

  @Before
  public void setUp() {
    preferences = new InMemorySharedPreferences();
    pendingAwardListType = new TypeToken<List<LearningPointStore.PendingPointAward>>() {}.getType();
    pointStore = new LearningPointStore(preferences, new Gson(), pendingAwardListType);
  }

  @Test
  public void computeFlushResult_newSession_addsPointsAndMarksSession() {
    List<LearningPointStore.PendingPointAward> pendingAwards = new ArrayList<>();
    pendingAwards.add(
        new LearningPointStore.PendingPointAward(
            "session-1", "dialogue_learning", "advanced", 50, 1_700_000_000_000L));

    LearningPointCloudRepository.FlushComputation result =
        LearningPointCloudRepository.computeFlushResult(100L, new HashSet<>(), pendingAwards);

    assertEquals(150L, result.mergedTotalPoints);
    assertEquals(1, result.sessionIdsToClear.size());
    assertTrue(result.sessionIdsToClear.contains("session-1"));
    assertTrue(result.newlyAwardedSessionIds.contains("session-1"));
  }

  @Test
  public void computeFlushResult_existingSession_doesNotAddDuplicatePoints() {
    Set<String> existingSessions = new HashSet<>();
    existingSessions.add("session-dup");
    List<LearningPointStore.PendingPointAward> pendingAwards = new ArrayList<>();
    pendingAwards.add(
        new LearningPointStore.PendingPointAward(
            "session-dup", "dialogue_learning", "intermediate", 20, 1_700_000_000_000L));

    LearningPointCloudRepository.FlushComputation result =
        LearningPointCloudRepository.computeFlushResult(80L, existingSessions, pendingAwards);

    assertEquals(80L, result.mergedTotalPoints);
    assertEquals(1, result.sessionIdsToClear.size());
    assertTrue(result.sessionIdsToClear.contains("session-dup"));
    assertEquals(0, result.newlyAwardedSessionIds.size());
  }

  @Test
  public void retryAfterFailure_keepsConsistencyAfterEventuallySuccessfulFlush() {
    pointStore.awardSessionIfNeeded(
        "session-retry",
        new LearningPointAwardSpec("dialogue_learning", "intermediate", 20, 1_700_000_000_000L));
    assertEquals(1, pointStore.getPendingAwards().size());
    assertEquals(20, pointStore.getTotalPoints());

    // First attempt fails: pending queue should remain as-is.
    assertEquals(1, pointStore.getPendingAwards().size());

    LearningPointCloudRepository.FlushComputation retryResult =
        LearningPointCloudRepository.computeFlushResult(
            0L, new HashSet<>(), pointStore.getPendingAwards());
    pointStore.removePendingAwardsBySessionIds(retryResult.sessionIdsToClear);
    pointStore.mergeCloudTotalPoints((int) retryResult.mergedTotalPoints);

    assertEquals(20, pointStore.getTotalPoints());
    assertTrue(pointStore.getPendingAwards().isEmpty());
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
