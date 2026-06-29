package com.jjundev.oneclickeng.notification;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppNotificationStore {
  static final String ANONYMOUS_UID = "__ANONYMOUS__";

  private static final String PREF_NAME = "app_notification_store";
  private static final String KEY_ENTRIES_JSON = "entries_json";
  private static final int MAX_ENTRIES_PER_USER = 100;

  @NonNull
  private static final Type ENTRY_LIST_TYPE =
      new TypeToken<List<AppNotificationEntry>>() {}.getType();

  @NonNull private final SharedPreferences preferences;
  @NonNull private final Gson gson;

  public AppNotificationStore(@NonNull Context context) {
    this.preferences =
        context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    this.gson = new Gson();
  }

  public synchronized void append(@NonNull AppNotificationEntry entry) {
    List<AppNotificationEntry> all = readAllUnlocked();
    all.add(sanitizeEntry(entry));
    all.sort(Comparator.comparingLong(AppNotificationEntry::getReceivedAtEpochMs).reversed());
    writeAllUnlocked(pruneByUserLimit(all));
  }

  @NonNull
  public synchronized List<AppNotificationEntry> getEntriesForCurrentUser() {
    return getEntriesForUid(resolveCurrentUserUid());
  }

  @NonNull
  public synchronized List<AppNotificationEntry> getEntriesForUid(@Nullable String uid) {
    String targetUid = normalizeUid(uid);
    List<AppNotificationEntry> all = readAllUnlocked();
    List<AppNotificationEntry> filtered = new ArrayList<>();
    for (AppNotificationEntry entry : all) {
      if (targetUid.equals(normalizeUid(entry.getUid()))) {
        filtered.add(sanitizeEntry(entry));
      }
    }
    filtered.sort(Comparator.comparingLong(AppNotificationEntry::getReceivedAtEpochMs).reversed());
    if (filtered.size() <= MAX_ENTRIES_PER_USER) {
      return filtered;
    }
    return new ArrayList<>(filtered.subList(0, MAX_ENTRIES_PER_USER));
  }

  @NonNull
  private List<AppNotificationEntry> readAllUnlocked() {
    String rawJson = preferences.getString(KEY_ENTRIES_JSON, null);
    if (rawJson == null || rawJson.trim().isEmpty()) {
      return new ArrayList<>();
    }
    try {
      List<AppNotificationEntry> parsed = gson.fromJson(rawJson, ENTRY_LIST_TYPE);
      if (parsed == null) {
        return new ArrayList<>();
      }
      List<AppNotificationEntry> sanitized = new ArrayList<>();
      for (AppNotificationEntry entry : parsed) {
        if (entry == null) {
          continue;
        }
        sanitized.add(sanitizeEntry(entry));
      }
      return sanitized;
    } catch (Exception ignored) {
      return new ArrayList<>();
    }
  }

  private void writeAllUnlocked(@NonNull List<AppNotificationEntry> entries) {
    preferences.edit().putString(KEY_ENTRIES_JSON, gson.toJson(entries)).apply();
  }

  @NonNull
  private List<AppNotificationEntry> pruneByUserLimit(@NonNull List<AppNotificationEntry> entries) {
    Map<String, Integer> countByUid = new HashMap<>();
    List<AppNotificationEntry> pruned = new ArrayList<>();
    for (AppNotificationEntry entry : entries) {
      String uid = normalizeUid(entry.getUid());
      int currentCount = countByUid.containsKey(uid) ? countByUid.get(uid) : 0;
      if (currentCount >= MAX_ENTRIES_PER_USER) {
        continue;
      }
      countByUid.put(uid, currentCount + 1);
      pruned.add(sanitizeEntry(entry));
    }
    return pruned;
  }

  @NonNull
  private AppNotificationEntry sanitizeEntry(@NonNull AppNotificationEntry raw) {
    AppNotificationEntry safe = new AppNotificationEntry();
    safe.setId(raw.getId());
    safe.setTitle(raw.getTitle());
    safe.setBody(raw.getBody());
    safe.setSource(raw.getSource());
    safe.setChannelId(raw.getChannelId());
    safe.setReceivedAtEpochMs(Math.max(0L, raw.getReceivedAtEpochMs()));
    safe.setPostedToSystem(raw.isPostedToSystem());
    safe.setUid(raw.getUid());
    return safe;
  }

  @NonNull
  public static String resolveCurrentUserUid() {
    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
    if (currentUser == null) {
      return ANONYMOUS_UID;
    }
    return normalizeUid(currentUser.getUid());
  }

  @NonNull
  static String normalizeUid(@Nullable String uid) {
    if (uid == null) {
      return ANONYMOUS_UID;
    }
    String trimmed = uid.trim();
    return trimmed.isEmpty() ? ANONYMOUS_UID : trimmed;
  }
}
