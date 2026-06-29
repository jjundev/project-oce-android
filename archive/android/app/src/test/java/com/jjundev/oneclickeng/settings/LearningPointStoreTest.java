package com.jjundev.oneclickeng.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class LearningPointStoreTest {
  private SharedPreferences preferences;
  private Type pendingAwardListType;
  private LearningPointStore store;

  @Before
  public void setUp() {
    preferences = new InMemorySharedPreferences();
    pendingAwardListType = new TypeToken<List<LearningPointStore.PendingPointAward>>() {}.getType();
    store = new LearningPointStore(preferences, new Gson(), pendingAwardListType);
  }

  @Test
  public void awardSessionIfNeeded_newSession_increasesPointsAndQueuesPending() {
    boolean awarded =
        store.awardSessionIfNeeded(
            "session-1",
            new LearningPointAwardSpec("dialogue_learning", "advanced", 50, 1_700_000_000_000L));

    assertTrue(awarded);
    assertEquals(50, store.getTotalPoints());
    assertTrue(store.hasAwardedSession("session-1"));
    List<LearningPointStore.PendingPointAward> pendingAwards = store.getPendingAwards();
    assertEquals(1, pendingAwards.size());
    assertEquals("session-1", pendingAwards.get(0).getSessionId());
    assertEquals("dialogue_learning", pendingAwards.get(0).getModeId());
    assertEquals("advanced", pendingAwards.get(0).getDifficulty());
    assertEquals(50, pendingAwards.get(0).getPoints());
  }

  @Test
  public void awardSessionIfNeeded_sameSession_onlyAwardsOnce() {
    boolean firstAward =
        store.awardSessionIfNeeded(
            "session-dup",
            new LearningPointAwardSpec(
                "dialogue_learning", "intermediate", 20, 1_700_000_000_000L));
    boolean secondAward =
        store.awardSessionIfNeeded(
            "session-dup",
            new LearningPointAwardSpec(
                "dialogue_learning", "intermediate", 20, 1_700_000_100_000L));

    assertTrue(firstAward);
    assertFalse(secondAward);
    assertEquals(20, store.getTotalPoints());
    assertEquals(1, store.getPendingAwards().size());
  }

  @Test
  public void statePersistsAcrossStoreRecreation() {
    store.awardSessionIfNeeded(
        "session-recreate",
        new LearningPointAwardSpec(
            "dialogue_learning", "upper-intermediate", 35, 1_700_000_000_000L));

    LearningPointStore recreatedStore =
        new LearningPointStore(preferences, new Gson(), pendingAwardListType);
    assertEquals(35, recreatedStore.getTotalPoints());
    assertTrue(recreatedStore.hasAwardedSession("session-recreate"));
    assertEquals(1, recreatedStore.getPendingAwards().size());
  }

  @Test
  public void removePendingAwardsBySessionIds_removesOnlyTargetSessions() {
    store.awardSessionIfNeeded(
        "session-a",
        new LearningPointAwardSpec("dialogue_learning", "beginner", 5, 1_700_000_000_000L));
    store.awardSessionIfNeeded(
        "session-b",
        new LearningPointAwardSpec("dialogue_learning", "advanced", 50, 1_700_000_010_000L));

    Set<String> sessionsToRemove = new HashSet<>();
    sessionsToRemove.add("session-a");
    store.removePendingAwardsBySessionIds(sessionsToRemove);

    List<LearningPointStore.PendingPointAward> remaining = store.getPendingAwards();
    assertEquals(1, remaining.size());
    assertEquals("session-b", remaining.get(0).getSessionId());
  }

  @Test
  public void mergeCloudTotalPoints_neverDecreasesLocalTotal() {
    store.awardSessionIfNeeded(
        "session-1",
        new LearningPointAwardSpec("dialogue_learning", "intermediate", 20, 1_700_000_000_000L));

    assertEquals(20, store.mergeCloudTotalPoints(10));
    assertEquals(45, store.mergeCloudTotalPoints(45));
    assertEquals(45, store.getTotalPoints());
  }

  @Test
  public void resetAllPoints_clearsTotalAwardsAndPendingQueue() {
    store.awardSessionIfNeeded(
        "session-reset",
        new LearningPointAwardSpec("dialogue_learning", "advanced", 50, 1_700_000_000_000L));

    store.resetAllPoints();

    assertEquals(0, store.getTotalPoints());
    assertFalse(store.hasAwardedSession("session-reset"));
    assertTrue(store.getPendingAwards().isEmpty());

    LearningPointStore recreatedStore =
        new LearningPointStore(preferences, new Gson(), pendingAwardListType);
    assertEquals(0, recreatedStore.getTotalPoints());
    assertFalse(recreatedStore.hasAwardedSession("session-reset"));
    assertTrue(recreatedStore.getPendingAwards().isEmpty());
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
