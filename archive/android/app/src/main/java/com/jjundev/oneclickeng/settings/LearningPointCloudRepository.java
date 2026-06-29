package com.jjundev.oneclickeng.settings;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Synchronizes awarded learning points with Firestore using a local pending queue. */
public final class LearningPointCloudRepository {
  private static final String COLLECTION_USERS = "users";
  private static final String COLLECTION_LEARNING_METRICS = "learning_metrics";
  private static final String DOCUMENT_POINTS = "points";
  private static final String COLLECTION_SESSIONS = "sessions";

  private static final String FIELD_TOTAL_POINTS = "total_points";
  private static final String FIELD_UPDATED_AT_EPOCH_MS = "updated_at_epoch_ms";

  private static final String FIELD_MODE_ID = "mode_id";
  private static final String FIELD_DIFFICULTY = "difficulty";
  private static final String FIELD_POINTS = "points";
  private static final String FIELD_AWARDED_AT_EPOCH_MS = "awarded_at_epoch_ms";

  @NonNull private final FirebaseAuth auth;
  @NonNull private final FirebaseFirestore firestore;
  @NonNull private final LearningPointStore pointStore;
  @NonNull private final TimeProvider timeProvider;

  public LearningPointCloudRepository(@NonNull Context context) {
    this(context, new LearningPointStore(context.getApplicationContext()));
  }

  public LearningPointCloudRepository(
      @NonNull Context context, @NonNull LearningPointStore pointStore) {
    this(
        FirebaseAuth.getInstance(),
        FirebaseFirestore.getInstance(),
        pointStore,
        new TimeProvider() {
          @Override
          public long currentTimeMillis() {
            return System.currentTimeMillis();
          }
        });
  }

  LearningPointCloudRepository(
      @NonNull FirebaseAuth auth,
      @NonNull FirebaseFirestore firestore,
      @NonNull LearningPointStore pointStore,
      @NonNull TimeProvider timeProvider) {
    this.auth = auth;
    this.firestore = firestore;
    this.pointStore = pointStore;
    this.timeProvider = timeProvider;
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

    List<LearningPointStore.PendingPointAward> pendingAwards = pointStore.getPendingAwards();
    if (pendingAwards.isEmpty()) {
      if (callback != null) {
        callback.onComplete(true);
      }
      return;
    }

    DocumentReference pointsRef = resolvePointsDocument(user.getUid());
    firestore
        .runTransaction(
            transaction -> {
              DocumentSnapshot pointSnapshot = transaction.get(pointsRef);
              long remoteTotalPoints = Math.max(0L, getLong(pointSnapshot, FIELD_TOTAL_POINTS));

              long pointsToAdd = 0L;
              Set<String> sessionsToClear = new HashSet<>();
              Set<String> seenSessionIds = new HashSet<>();
              Set<String> newlyAwardedSessionIds = new HashSet<>();

              for (LearningPointStore.PendingPointAward item : pendingAwards) {
                LearningPointStore.PendingPointAward award =
                    LearningPointStore.PendingPointAward.normalizedCopy(item);
                if (award == null) {
                  continue;
                }
                String sessionId = award.getSessionId();
                if (!seenSessionIds.add(sessionId)) {
                  sessionsToClear.add(sessionId);
                  continue;
                }

                DocumentReference sessionRef =
                    pointsRef.collection(COLLECTION_SESSIONS).document(sessionId);
                DocumentSnapshot sessionSnapshot = transaction.get(sessionRef);
                sessionsToClear.add(sessionId);
                if (sessionSnapshot.exists()) {
                  continue;
                }

                pointsToAdd = safeAdd(pointsToAdd, award.getPoints());
                newlyAwardedSessionIds.add(sessionId);
                Map<String, Object> sessionPayload = new HashMap<>();
                sessionPayload.put(FIELD_MODE_ID, award.getModeId());
                sessionPayload.put(FIELD_DIFFICULTY, award.getDifficulty());
                sessionPayload.put(FIELD_POINTS, award.getPoints());
                sessionPayload.put(FIELD_AWARDED_AT_EPOCH_MS, award.getAwardedAtEpochMs());
                transaction.set(sessionRef, sessionPayload, SetOptions.merge());
              }

              long mergedTotalPoints = safeAdd(remoteTotalPoints, pointsToAdd);
              if (!newlyAwardedSessionIds.isEmpty()) {
                Map<String, Object> pointsPayload = new HashMap<>();
                pointsPayload.put(FIELD_TOTAL_POINTS, mergedTotalPoints);
                pointsPayload.put(FIELD_UPDATED_AT_EPOCH_MS, timeProvider.currentTimeMillis());
                transaction.set(pointsRef, pointsPayload, SetOptions.merge());
              }

              return new FlushTransactionResult(mergedTotalPoints, sessionsToClear);
            })
        .addOnSuccessListener(
            result -> {
              pointStore.removePendingAwardsBySessionIds(result.sessionIdsToClear);
              pointStore.mergeCloudTotalPoints(safeLongToInt(result.totalPoints));
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

  public void resetTotalPointsForCurrentUser(@Nullable CompletionCallback callback) {
    FirebaseUser user = auth.getCurrentUser();
    if (user == null) {
      if (callback != null) {
        callback.onComplete(false);
      }
      return;
    }

    Map<String, Object> updates = new HashMap<>();
    updates.put(FIELD_TOTAL_POINTS, 0L);
    updates.put(FIELD_UPDATED_AT_EPOCH_MS, timeProvider.currentTimeMillis());

    resolvePointsDocument(user.getUid())
        .set(updates, SetOptions.merge())
        .addOnSuccessListener(
            unused -> {
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

  public void fetchCurrentUserTotalPoints(@NonNull TotalPointsCallback callback) {
    FirebaseUser user = auth.getCurrentUser();
    if (user == null) {
      callback.onNoUser();
      return;
    }

    resolvePointsDocument(user.getUid())
        .get()
        .addOnSuccessListener(
            snapshot ->
                callback.onSuccess(
                    safeLongToInt(Math.max(0L, getLong(snapshot, FIELD_TOTAL_POINTS)))))
        .addOnFailureListener(error -> callback.onFailure());
  }

  @NonNull
  static FlushComputation computeFlushResult(
      long remoteTotalPoints,
      @NonNull Set<String> existingRemoteSessionIds,
      @NonNull List<LearningPointStore.PendingPointAward> pendingAwards) {
    long safeRemoteTotal = Math.max(0L, remoteTotalPoints);
    long mergedTotal = safeRemoteTotal;
    Set<String> safeExistingSessionIds = normalizeSessionIds(existingRemoteSessionIds);
    Set<String> sessionsToClear = new HashSet<>();
    Set<String> newlyAwardedSessionIds = new HashSet<>();

    for (LearningPointStore.PendingPointAward item : pendingAwards) {
      LearningPointStore.PendingPointAward award =
          LearningPointStore.PendingPointAward.normalizedCopy(item);
      if (award == null) {
        continue;
      }
      String sessionId = award.getSessionId();
      if (!sessionsToClear.add(sessionId)) {
        continue;
      }
      if (safeExistingSessionIds.contains(sessionId)) {
        continue;
      }
      mergedTotal = safeAdd(mergedTotal, award.getPoints());
      newlyAwardedSessionIds.add(sessionId);
    }

    return new FlushComputation(mergedTotal, sessionsToClear, newlyAwardedSessionIds);
  }

  @NonNull
  private DocumentReference resolvePointsDocument(@NonNull String uid) {
    return firestore
        .collection(COLLECTION_USERS)
        .document(uid)
        .collection(COLLECTION_LEARNING_METRICS)
        .document(DOCUMENT_POINTS);
  }

  @NonNull
  private static Set<String> normalizeSessionIds(@NonNull Set<String> rawSessionIds) {
    Set<String> normalized = new HashSet<>();
    for (String item : rawSessionIds) {
      if (item == null) {
        continue;
      }
      String sessionId = item.trim();
      if (!sessionId.isEmpty()) {
        normalized.add(sessionId);
      }
    }
    return normalized;
  }

  private static long getLong(@Nullable DocumentSnapshot snapshot, @NonNull String fieldName) {
    if (snapshot == null || !snapshot.exists()) {
      return 0L;
    }
    Long value = snapshot.getLong(fieldName);
    return value == null ? 0L : value;
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

  private static int safeLongToInt(long value) {
    if (value <= 0L) {
      return 0;
    }
    if (value >= Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    return (int) value;
  }

  interface TimeProvider {
    long currentTimeMillis();
  }

  public interface CompletionCallback {
    void onComplete(boolean success);
  }

  public interface TotalPointsCallback {
    void onSuccess(int totalPoints);

    void onFailure();

    void onNoUser();
  }

  static final class FlushComputation {
    final long mergedTotalPoints;
    @NonNull final Set<String> sessionIdsToClear;
    @NonNull final Set<String> newlyAwardedSessionIds;

    FlushComputation(
        long mergedTotalPoints,
        @NonNull Set<String> sessionIdsToClear,
        @NonNull Set<String> newlyAwardedSessionIds) {
      this.mergedTotalPoints = Math.max(0L, mergedTotalPoints);
      this.sessionIdsToClear = new HashSet<>(sessionIdsToClear);
      this.newlyAwardedSessionIds = new HashSet<>(newlyAwardedSessionIds);
    }
  }

  private static final class FlushTransactionResult {
    final long totalPoints;
    @NonNull final Set<String> sessionIdsToClear;

    FlushTransactionResult(long totalPoints, @NonNull Set<String> sessionIdsToClear) {
      this.totalPoints = Math.max(0L, totalPoints);
      this.sessionIdsToClear = new HashSet<>(sessionIdsToClear);
    }
  }
}
