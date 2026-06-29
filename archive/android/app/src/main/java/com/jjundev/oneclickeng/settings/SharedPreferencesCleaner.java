package com.jjundev.oneclickeng.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import java.io.File;

public final class SharedPreferencesCleaner {
  private static final String TAG = "SharedPreferencesCleaner";
  private static final String SHARED_PREFS_DIR_NAME = "shared_prefs";
  private static final String SHARED_PREFS_XML_SUFFIX = ".xml";

  private SharedPreferencesCleaner() {}

  public static int clearAll(@NonNull Context context) {
    File dataDir = context.getDataDir();
    if (dataDir == null) {
      return 0;
    }

    File sharedPrefsDir = new File(dataDir, SHARED_PREFS_DIR_NAME);
    File[] sharedPrefsFiles = sharedPrefsDir.listFiles();
    if (sharedPrefsFiles == null || sharedPrefsFiles.length == 0) {
      return 0;
    }

    int clearedCount = 0;
    for (File sharedPrefsFile : sharedPrefsFiles) {
      String prefName = toPreferenceName(sharedPrefsFile);
      if (prefName == null) {
        continue;
      }

      try {
        SharedPreferences preferences =
            context.getSharedPreferences(prefName, Context.MODE_PRIVATE);
        boolean isCleared = preferences.edit().clear().commit();
        boolean isDeleted = context.deleteSharedPreferences(prefName);

        if (!isCleared) {
          Log.w(TAG, "Failed to commit clear() for SharedPreferences: " + prefName);
        }
        if (!isDeleted) {
          Log.w(TAG, "deleteSharedPreferences() returned false: " + prefName);
        }

        if (isCleared || isDeleted) {
          clearedCount++;
        }
      } catch (Exception e) {
        Log.e(TAG, "Failed to clear SharedPreferences: " + prefName, e);
      }
    }
    return clearedCount;
  }

  private static String toPreferenceName(@NonNull File sharedPrefsFile) {
    String fileName = sharedPrefsFile.getName();
    if (!fileName.endsWith(SHARED_PREFS_XML_SUFFIX)) {
      return null;
    }

    String prefName =
        fileName.substring(0, fileName.length() - SHARED_PREFS_XML_SUFFIX.length()).trim();
    if (prefName.isEmpty()) {
      return null;
    }
    return prefName;
  }
}
