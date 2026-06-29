package com.jjundev.oneclickeng.settings;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.HashMap;
import java.util.Map;

public final class UserNicknameCloudRepository {
  private static final String COLLECTION_USERS = "users";
  private static final String FIELD_NICKNAME = "nickname";
  private static final String DEFAULT_NICKNAME = "학습자";

  @NonNull private final AppSettingsStore appSettingsStore;
  @NonNull private final FirebaseAuth auth;
  @NonNull private final FirebaseFirestore firestore;

  public UserNicknameCloudRepository(@NonNull Context context) {
    this(
        new AppSettingsStore(context.getApplicationContext()),
        FirebaseAuth.getInstance(),
        FirebaseFirestore.getInstance());
  }

  UserNicknameCloudRepository(
      @NonNull AppSettingsStore appSettingsStore,
      @NonNull FirebaseAuth auth,
      @NonNull FirebaseFirestore firestore) {
    this.appSettingsStore = appSettingsStore;
    this.auth = auth;
    this.firestore = firestore;
  }

  @NonNull
  public String getCachedOrFallbackNickname() {
    String cachedNickname = normalize(appSettingsStore.getSettings().getUserNickname());
    if (!cachedNickname.isEmpty()) {
      return cachedNickname;
    }
    FirebaseUser user = auth.getCurrentUser();
    String displayName = user != null ? user.getDisplayName() : null;
    return resolveBootstrapNickname(displayName);
  }

  public void fetchOrBootstrapForCurrentUser(@NonNull NicknameFetchCallback callback) {
    FirebaseUser user = auth.getCurrentUser();
    String fallbackNickname = getCachedOrFallbackNickname();
    if (user == null) {
      callback.onNoUser(fallbackNickname);
      return;
    }

    String uid = user.getUid();
    String displayName = user.getDisplayName();
    firestore
        .collection(COLLECTION_USERS)
        .document(uid)
        .get()
        .addOnSuccessListener(
            snapshot -> {
              String cloudNickname = normalize(snapshot.getString(FIELD_NICKNAME));
              if (!cloudNickname.isEmpty()) {
                appSettingsStore.setUserNickname(cloudNickname);
                callback.onSuccess(cloudNickname, false);
                return;
              }

              String bootstrapNickname = resolveBootstrapNickname(displayName);
              Map<String, Object> data = new HashMap<>();
              data.put(FIELD_NICKNAME, bootstrapNickname);
              firestore
                  .collection(COLLECTION_USERS)
                  .document(uid)
                  .set(data, SetOptions.merge())
                  .addOnSuccessListener(
                      unused -> {
                        appSettingsStore.setUserNickname(bootstrapNickname);
                        callback.onSuccess(bootstrapNickname, true);
                      })
                  .addOnFailureListener(error -> callback.onFailure(fallbackNickname));
            })
        .addOnFailureListener(error -> callback.onFailure(fallbackNickname));
  }

  public void saveForCurrentUser(
      @Nullable String requestedNickname, @NonNull NicknameSaveCallback callback) {
    FirebaseUser user = auth.getCurrentUser();
    String fallbackNickname = getCachedOrFallbackNickname();
    if (user == null) {
      callback.onNoUser(fallbackNickname);
      return;
    }

    String nicknameToPersist = resolveNicknameToPersist(requestedNickname, user.getDisplayName());
    Map<String, Object> updates = new HashMap<>();
    updates.put(FIELD_NICKNAME, nicknameToPersist);
    firestore
        .collection(COLLECTION_USERS)
        .document(user.getUid())
        .set(updates, SetOptions.merge())
        .addOnSuccessListener(
            unused -> {
              appSettingsStore.setUserNickname(nicknameToPersist);
              callback.onSuccess(nicknameToPersist);
            })
        .addOnFailureListener(error -> callback.onFailure(fallbackNickname));
  }

  @NonNull
  static String normalize(@Nullable String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? "" : trimmed;
  }

  @NonNull
  static String resolveBootstrapNickname(@Nullable String displayName) {
    String normalizedDisplayName = normalize(displayName);
    return normalizedDisplayName.isEmpty() ? DEFAULT_NICKNAME : normalizedDisplayName;
  }

  @NonNull
  static String resolveNicknameToPersist(
      @Nullable String requestedNickname, @Nullable String displayName) {
    String normalizedRequestedNickname = normalize(requestedNickname);
    if (!normalizedRequestedNickname.isEmpty()) {
      return normalizedRequestedNickname;
    }
    return resolveBootstrapNickname(displayName);
  }

  public interface NicknameFetchCallback {
    void onSuccess(@NonNull String nickname, boolean bootstrapped);

    void onFailure(@NonNull String fallbackNickname);

    void onNoUser(@NonNull String fallbackNickname);
  }

  public interface NicknameSaveCallback {
    void onSuccess(@NonNull String nickname);

    void onFailure(@NonNull String fallbackNickname);

    void onNoUser(@NonNull String fallbackNickname);
  }
}
