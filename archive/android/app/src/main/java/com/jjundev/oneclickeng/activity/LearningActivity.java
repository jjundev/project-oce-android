package com.jjundev.oneclickeng.activity;

import android.os.Bundle;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.jjundev.oneclickeng.settings.LearningPointAwardSpec;
import com.jjundev.oneclickeng.settings.LearningPointCloudRepository;
import com.jjundev.oneclickeng.settings.LearningPointStore;
import com.jjundev.oneclickeng.settings.LearningStudyTimeCloudRepository;
import com.jjundev.oneclickeng.settings.LearningStudyTimeStore;
import java.util.UUID;

/** Base activity for learning screens that should contribute to daily visible study time. */
public abstract class LearningActivity extends AppCompatActivity {
  private static final long UNSET = -1L;
  private static final String STATE_LEARNING_SESSION_ID = "state_learning_session_id";
  private static final String STATE_SESSION_COMPLETION_NOTIFIED =
      "state_session_completion_notified";

  @Nullable private LearningStudyTimeStore learningStudyTimeStore;
  @Nullable private LearningStudyTimeCloudRepository learningStudyTimeCloudRepository;
  @Nullable private LearningPointStore learningPointStore;
  @Nullable private LearningPointCloudRepository learningPointCloudRepository;
  @NonNull private String learningSessionId = UUID.randomUUID().toString();
  private boolean learningSessionCompletionNotified = false;
  private long visibleStartElapsedRealtimeMs = UNSET;
  private long visibleStartWallClockMs = UNSET;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    if (savedInstanceState == null) {
      return;
    }

    String restoredSessionId = savedInstanceState.getString(STATE_LEARNING_SESSION_ID);
    if (restoredSessionId != null && !restoredSessionId.trim().isEmpty()) {
      learningSessionId = restoredSessionId.trim();
    }
    learningSessionCompletionNotified =
        savedInstanceState.getBoolean(STATE_SESSION_COMPLETION_NOTIFIED, false);
  }

  @Override
  protected void onSaveInstanceState(@NonNull Bundle outState) {
    outState.putString(STATE_LEARNING_SESSION_ID, learningSessionId);
    outState.putBoolean(STATE_SESSION_COMPLETION_NOTIFIED, learningSessionCompletionNotified);
    super.onSaveInstanceState(outState);
  }

  @Nullable
  protected abstract LearningPointAwardSpec buildPointAwardSpecOnSessionCompleted();

  protected final void notifyLearningSessionCompleted() {
    if (learningSessionCompletionNotified) {
      return;
    }

    LearningPointAwardSpec pointAwardSpec = buildPointAwardSpecOnSessionCompleted();
    if (pointAwardSpec == null || pointAwardSpec.getPoints() <= 0) {
      return;
    }
    learningSessionCompletionNotified = true;

    boolean newlyAwarded =
        getLearningPointStore().awardSessionIfNeeded(getLearningSessionId(), pointAwardSpec);
    if (newlyAwarded) {
      getLearningPointCloudRepository().flushPendingForCurrentUser();
      return;
    }

    // Flush can still be needed when an already-awarded session has pending cloud sync.
    getLearningPointCloudRepository().flushPendingForCurrentUser();
  }

  @NonNull
  protected final String getLearningSessionId() {
    return learningSessionId;
  }

  @Override
  protected void onStart() {
    super.onStart();
    visibleStartElapsedRealtimeMs = SystemClock.elapsedRealtime();
    visibleStartWallClockMs = System.currentTimeMillis();
  }

  @Override
  protected void onStop() {
    recordVisibleIntervalIfNeeded();
    super.onStop();
  }

  private void recordVisibleIntervalIfNeeded() {
    if (visibleStartElapsedRealtimeMs < 0L || visibleStartWallClockMs <= 0L) {
      resetVisibleStartState();
      return;
    }

    long endElapsedRealtimeMs = SystemClock.elapsedRealtime();
    long visibleDurationMs = endElapsedRealtimeMs - visibleStartElapsedRealtimeMs;
    if (visibleDurationMs <= 0L) {
      resetVisibleStartState();
      return;
    }

    long endWallClockMs = visibleStartWallClockMs + visibleDurationMs;
    if (endWallClockMs < visibleStartWallClockMs) {
      endWallClockMs = System.currentTimeMillis();
    }

    LearningStudyTimeStore localStore = getLearningStudyTimeStore();
    localStore.recordVisibleInterval(visibleStartWallClockMs, endWallClockMs);
    getLearningStudyTimeCloudRepository()
        .recordIntervalForCurrentUser(visibleStartWallClockMs, endWallClockMs);
    resetVisibleStartState();
  }

  @NonNull
  private LearningStudyTimeStore getLearningStudyTimeStore() {
    if (learningStudyTimeStore == null) {
      learningStudyTimeStore = new LearningStudyTimeStore(getApplicationContext());
    }
    return learningStudyTimeStore;
  }

  @NonNull
  private LearningStudyTimeCloudRepository getLearningStudyTimeCloudRepository() {
    if (learningStudyTimeCloudRepository == null) {
      learningStudyTimeCloudRepository =
          new LearningStudyTimeCloudRepository(getApplicationContext());
    }
    return learningStudyTimeCloudRepository;
  }

  @NonNull
  private LearningPointStore getLearningPointStore() {
    if (learningPointStore == null) {
      learningPointStore = new LearningPointStore(getApplicationContext());
    }
    return learningPointStore;
  }

  @NonNull
  private LearningPointCloudRepository getLearningPointCloudRepository() {
    if (learningPointCloudRepository == null) {
      learningPointCloudRepository =
          new LearningPointCloudRepository(getApplicationContext(), getLearningPointStore());
    }
    return learningPointCloudRepository;
  }

  private void resetVisibleStartState() {
    visibleStartElapsedRealtimeMs = UNSET;
    visibleStartWallClockMs = UNSET;
  }
}
