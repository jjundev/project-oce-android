package com.jjundev.oneclickeng.learning.dialoguelearning.coordinator;

import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SentenceFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import com.jjundev.oneclickeng.learning.dialoguelearning.summary.BookmarkedParaphrase;
import com.jjundev.oneclickeng.learning.dialoguelearning.summary.SessionSummaryGenerator;
import com.jjundev.oneclickeng.learning.dialoguelearning.summary.SummaryFeatureBundle;
import java.util.ArrayList;
import java.util.List;

public final class DialogueSummaryCoordinator {

  private static final String STATE_FALLBACK_SUMMARY_JSON = "state_fallback_summary_json";
  private static final String STATE_SUMMARY_FEATURE_BUNDLE_JSON =
      "state_summary_feature_bundle_json";
  private static final long SUMMARY_OPEN_GUARD_MS = 1000L;
  private static final String TAG = "DialogueLearning";

  public interface LoggerDelegate {
    void trace(@NonNull String key);

    void gate(@NonNull String key);

    void ux(@NonNull String key, @Nullable String fields);
  }

  public interface SummarySeedDelegate {
    @NonNull
    List<SentenceFeedback> snapshotAccumulatedFeedbacks();

    @NonNull
    List<BookmarkedParaphrase> snapshotBookmarkedParaphrases();
  }

  public interface SummaryNavigationDelegate {
    boolean isHostActive();

    void stopPlayback(@NonNull String reason);

    void navigateToSummary(@NonNull String summaryJson, @Nullable String featureBundleJson);
  }

  @NonNull private final Handler mainHandler;
  @NonNull private final LoggerDelegate loggerDelegate;
  @NonNull private final SummarySeedDelegate summarySeedDelegate;
  @NonNull private final SummaryNavigationDelegate summaryNavigationDelegate;
  @NonNull private final Gson serializer = new Gson();

  @Nullable private SessionSummaryGenerator sessionSummaryGenerator;
  @Nullable private SummaryData fallbackSummaryData;
  @Nullable private SummaryFeatureBundle summaryFeatureBundle;
  private boolean isSummaryOpening;
  private long lastSummaryOpenRequestMs;
  @Nullable private Runnable summaryOpenResetRunnable;

  public DialogueSummaryCoordinator(
      @NonNull Handler mainHandler,
      @NonNull LoggerDelegate loggerDelegate,
      @NonNull SummarySeedDelegate summarySeedDelegate,
      @NonNull SummaryNavigationDelegate summaryNavigationDelegate) {
    this.mainHandler = mainHandler;
    this.loggerDelegate = loggerDelegate;
    this.summarySeedDelegate = summarySeedDelegate;
    this.summaryNavigationDelegate = summaryNavigationDelegate;
  }

  public void restoreState(@Nullable Bundle savedInstanceState, @NonNull Gson gson) {
    if (savedInstanceState == null) {
      return;
    }
    fallbackSummaryData =
        parseSummaryData(savedInstanceState.getString(STATE_FALLBACK_SUMMARY_JSON), gson);
    summaryFeatureBundle =
        parseSummaryFeatureBundle(
            savedInstanceState.getString(STATE_SUMMARY_FEATURE_BUNDLE_JSON), gson);
  }

  public void saveState(@NonNull Bundle outState, @NonNull Gson gson) {
    if (fallbackSummaryData != null) {
      outState.putString(STATE_FALLBACK_SUMMARY_JSON, gson.toJson(fallbackSummaryData));
    }
    if (summaryFeatureBundle != null) {
      outState.putString(STATE_SUMMARY_FEATURE_BUNDLE_JSON, gson.toJson(summaryFeatureBundle));
    }
  }

  public void ensureSummarySeedPrepared() {
    if (fallbackSummaryData != null && summaryFeatureBundle != null) {
      return;
    }
    if (sessionSummaryGenerator == null) {
      sessionSummaryGenerator = new SessionSummaryGenerator();
    }

    List<SentenceFeedback> feedbackSnapshot = summarySeedDelegate.snapshotAccumulatedFeedbacks();
    if (feedbackSnapshot == null) {
      feedbackSnapshot = new ArrayList<>();
    }
    List<BookmarkedParaphrase> bookmarkSnapshot =
        summarySeedDelegate.snapshotBookmarkedParaphrases();
    if (bookmarkSnapshot == null) {
      bookmarkSnapshot = new ArrayList<>();
    }

    SessionSummaryGenerator.GenerationSeed seed =
        sessionSummaryGenerator.buildSeed(feedbackSnapshot, bookmarkSnapshot);
    fallbackSummaryData = seed.getFallbackSummary();
    summaryFeatureBundle = seed.getFeatureBundle();
  }

  public void openSummary(@NonNull String reason) {
    if (!summaryNavigationDelegate.isHostActive()) {
      return;
    }

    long now = SystemClock.elapsedRealtime();
    if (isSummaryOpening || now - lastSummaryOpenRequestMs < SUMMARY_OPEN_GUARD_MS) {
      loggerDelegate.trace("TRACE_SUMMARY_TRIGGER reason=" + reason + "_blocked");
      loggerDelegate.ux("UX_SUMMARY_BLOCKED", "reason=" + reason);
      return;
    }

    isSummaryOpening = true;
    lastSummaryOpenRequestMs = now;

    if (summaryOpenResetRunnable != null) {
      mainHandler.removeCallbacks(summaryOpenResetRunnable);
    }
    summaryOpenResetRunnable =
        () -> {
          isSummaryOpening = false;
          summaryOpenResetRunnable = null;
        };
    mainHandler.postDelayed(summaryOpenResetRunnable, SUMMARY_OPEN_GUARD_MS);

    loggerDelegate.gate("M4_SUMMARY_OPEN");
    loggerDelegate.trace("TRACE_SUMMARY_OPEN");
    loggerDelegate.ux("UX_SUMMARY_OPEN", "reason=" + reason);

    ensureSummarySeedPrepared();

    SummaryData summaryData = fallbackSummaryData;
    if (summaryData != null) {
      loggerDelegate.gate("M4_SUMMARY_FALLBACK source=seed");
    }
    if (summaryData == null) {
      if (sessionSummaryGenerator == null) {
        sessionSummaryGenerator = new SessionSummaryGenerator();
      }
      summaryData = sessionSummaryGenerator.createEmptySummary();
      loggerDelegate.gate("M4_SUMMARY_FALLBACK source=empty");
    }

    summaryNavigationDelegate.stopPlayback("openSummaryFragment");

    String summaryJson = serializer.toJson(summaryData);
    String featureBundleJson =
        summaryFeatureBundle == null ? null : serializer.toJson(summaryFeatureBundle);
    summaryNavigationDelegate.navigateToSummary(summaryJson, featureBundleJson);
  }

  public void release() {
    if (summaryOpenResetRunnable != null) {
      mainHandler.removeCallbacks(summaryOpenResetRunnable);
      summaryOpenResetRunnable = null;
    }
    isSummaryOpening = false;
  }

  @Nullable
  private SummaryData parseSummaryData(@Nullable String json, @NonNull Gson gson) {
    if (json == null || json.trim().isEmpty()) {
      return null;
    }
    try {
      return gson.fromJson(json, SummaryData.class);
    } catch (Exception e) {
      Log.w(TAG, "Failed to parse summary JSON", e);
      return null;
    }
  }

  @Nullable
  private SummaryFeatureBundle parseSummaryFeatureBundle(
      @Nullable String json, @NonNull Gson gson) {
    if (json == null || json.trim().isEmpty()) {
      return null;
    }
    try {
      return gson.fromJson(json, SummaryFeatureBundle.class);
    } catch (Exception e) {
      Log.w(TAG, "Failed to parse summary feature bundle JSON", e);
      return null;
    }
  }
}
