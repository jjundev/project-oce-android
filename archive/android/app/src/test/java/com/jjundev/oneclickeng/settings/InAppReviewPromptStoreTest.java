package com.jjundev.oneclickeng.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class InAppReviewPromptStoreTest {
  private SharedPreferences preferences;
  private InAppReviewPromptStore store;

  @Before
  public void setUp() {
    preferences = new InMemorySharedPreferences();
    store = new InAppReviewPromptStore(preferences);
  }

  @Test
  public void consumeDialogueSummaryFinishReviewOpportunity_firstCallTrue_thenFalse() {
    assertTrue(store.consumeDialogueSummaryFinishReviewOpportunity());
    assertFalse(store.consumeDialogueSummaryFinishReviewOpportunity());
  }

  @Test
  public void consumeDialogueSummaryFinishReviewOpportunity_persistsAcrossStoreRecreation() {
    assertTrue(store.consumeDialogueSummaryFinishReviewOpportunity());

    InAppReviewPromptStore recreatedStore = new InAppReviewPromptStore(preferences);
    assertFalse(recreatedStore.consumeDialogueSummaryFinishReviewOpportunity());
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
