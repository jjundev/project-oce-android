package com.jjundev.oneclickeng.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class SharedPreferencesCleanerTest {

  private Context appContext;

  @Before
  public void setUp() {
    appContext = RuntimeEnvironment.getApplication();
  }

  @Test
  public void clearAll_clearsEverySharedPreferenceFile() {
    String suffix = String.valueOf(System.nanoTime());
    String firstPrefName = "sp_cleaner_test_first_" + suffix;
    String secondPrefName = "sp_cleaner_test_second_" + suffix;

    appContext
        .getSharedPreferences(firstPrefName, Context.MODE_PRIVATE)
        .edit()
        .putString("first_key", "first_value")
        .commit();
    appContext
        .getSharedPreferences(secondPrefName, Context.MODE_PRIVATE)
        .edit()
        .putInt("second_key", 2)
        .commit();

    int clearedCount = SharedPreferencesCleaner.clearAll(appContext);

    assertTrue(
        appContext.getSharedPreferences(firstPrefName, Context.MODE_PRIVATE).getAll().isEmpty());
    assertTrue(
        appContext.getSharedPreferences(secondPrefName, Context.MODE_PRIVATE).getAll().isEmpty());
    assertTrue(clearedCount >= 2);
  }

  @Test
  public void clearAll_canRunTwiceWithoutException() {
    String prefName = "sp_cleaner_test_idempotent_" + System.nanoTime();
    appContext
        .getSharedPreferences(prefName, Context.MODE_PRIVATE)
        .edit()
        .putBoolean("enabled", true)
        .commit();

    int firstCount = SharedPreferencesCleaner.clearAll(appContext);
    int secondCount = SharedPreferencesCleaner.clearAll(appContext);

    assertTrue(appContext.getSharedPreferences(prefName, Context.MODE_PRIVATE).getAll().isEmpty());
    assertTrue(firstCount >= 1);
    assertTrue(secondCount >= 0);
  }

  @Test
  public void clearAll_whenSharedPrefsDirectoryEmpty_returnsZero() {
    java.io.File isolatedDataDir =
        new java.io.File(appContext.getCacheDir(), "sp_cleaner_empty_case_" + System.nanoTime());
    isolatedDataDir.mkdirs();

    Context isolatedContext =
        new ContextWrapper(appContext) {
          @Override
          public java.io.File getDataDir() {
            return isolatedDataDir;
          }
        };

    int clearedCount = SharedPreferencesCleaner.clearAll(isolatedContext);

    assertEquals(0, clearedCount);
    deleteRecursively(isolatedDataDir);
  }

  private static void deleteRecursively(java.io.File target) {
    if (target == null || !target.exists()) {
      return;
    }
    if (target.isDirectory()) {
      java.io.File[] children = target.listFiles();
      if (children != null) {
        for (java.io.File child : children) {
          deleteRecursively(child);
        }
      }
    }
    target.delete();
  }
}
