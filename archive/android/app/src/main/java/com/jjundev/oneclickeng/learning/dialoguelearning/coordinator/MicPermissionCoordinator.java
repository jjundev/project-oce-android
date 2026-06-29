package com.jjundev.oneclickeng.learning.dialoguelearning.coordinator;

import android.os.Bundle;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class MicPermissionCoordinator {

  private static final String STATE_MIC_PERMISSION_REQUESTED_BEFORE =
      "state_mic_permission_requested_before";
  private static final String STATE_MIC_PERMISSION_DENIED_COUNT =
      "state_mic_permission_denied_count";
  private static final String STATE_MIC_PERMISSION_AWAIT_SETTINGS =
      "state_mic_permission_await_settings";
  private static final String STATE_MIC_PERMISSION_PENDING_SENTENCE =
      "state_mic_permission_pending_sentence";
  private static final String STATE_MIC_PERMISSION_FLOW_SOURCE = "state_mic_permission_flow_source";
  private static final long MIC_PERMISSION_REQUEST_THROTTLE_MS = 500L;
  private static final int SETTINGS_HINT_DENIAL_THRESHOLD = 2;

  public interface Host {
    boolean isHostActive();

    boolean isMicPermissionGranted();

    boolean hasMicPermissionRationale();

    void requestPermissionLaunch();

    void onNeedOpenSettingsDialog(@NonNull String source);

    void onPermissionDeniedToast();

    void onStartSpeakingAfterPermission(@NonNull String sentenceToTranslate);

    void onShowBeforeSpeakingAfterPermission(@NonNull String sentenceToTranslate);

    @Nullable
    String onConsumePendingSentenceFromSpeakingCoordinator();

    void onSetPendingSentenceToSpeakingCoordinator(@NonNull String sentenceToTranslate);

    void trace(@NonNull String key);

    void gate(@NonNull String key);

    void ux(@NonNull String key, @Nullable String fields);
  }

  @NonNull private final Host host;

  private boolean micPermissionRequestedBefore;
  private int micPermissionDeniedCount;
  private boolean isMicPermissionRequestInFlight;
  private boolean isAwaitingMicPermissionSettings;
  private long lastMicPermissionRequestMs;
  @Nullable private String pendingMicSentenceToRequest;
  @Nullable private String micPermissionFlowSource;

  public MicPermissionCoordinator(@NonNull Host host) {
    this.host = host;
  }

  public void restoreFrom(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState == null) {
      return;
    }
    micPermissionRequestedBefore =
        savedInstanceState.getBoolean(STATE_MIC_PERMISSION_REQUESTED_BEFORE, false);
    micPermissionDeniedCount = savedInstanceState.getInt(STATE_MIC_PERMISSION_DENIED_COUNT, 0);
    isAwaitingMicPermissionSettings =
        savedInstanceState.getBoolean(STATE_MIC_PERMISSION_AWAIT_SETTINGS, false);
    pendingMicSentenceToRequest =
        savedInstanceState.getString(STATE_MIC_PERMISSION_PENDING_SENTENCE);
    micPermissionFlowSource = savedInstanceState.getString(STATE_MIC_PERMISSION_FLOW_SOURCE);
    isMicPermissionRequestInFlight = false;
  }

  public void saveTo(@NonNull Bundle outState) {
    outState.putBoolean(STATE_MIC_PERMISSION_REQUESTED_BEFORE, micPermissionRequestedBefore);
    outState.putInt(STATE_MIC_PERMISSION_DENIED_COUNT, micPermissionDeniedCount);
    outState.putBoolean(STATE_MIC_PERMISSION_AWAIT_SETTINGS, isAwaitingMicPermissionSettings);
    outState.putString(STATE_MIC_PERMISSION_PENDING_SENTENCE, pendingMicSentenceToRequest);
    outState.putString(STATE_MIC_PERMISSION_FLOW_SOURCE, micPermissionFlowSource);
  }

  public void request(@Nullable String sentenceToTranslate, @NonNull String source) {
    String safeSentence = sentenceToTranslate == null ? "" : sentenceToTranslate;
    pendingMicSentenceToRequest = safeSentence;
    micPermissionFlowSource = source;
    micPermissionRequestedBefore = true;

    if (sentenceToTranslate != null) {
      host.onSetPendingSentenceToSpeakingCoordinator(safeSentence);
    }

    if (host.isMicPermissionGranted()) {
      continueMicFlowIfPermissionGranted();
      return;
    }

    long now = SystemClock.elapsedRealtime();
    if (isMicPermissionRequestInFlight
        || now - lastMicPermissionRequestMs < MIC_PERMISSION_REQUEST_THROTTLE_MS) {
      host.trace(
          "TRACE_PERMISSION_REQUEST_THROTTLED source="
              + source
              + " sentenceLen="
              + safeSentence.length());
      return;
    }

    host.gate("M1_PERMISSION_REQUEST source=" + source);
    host.trace(
        "TRACE_ACTION_PERMISSION_REQUEST source="
            + source
            + " sentenceLen="
            + safeSentence.length());
    if (shouldOpenAppSettingsForMic()) {
      host.gate("M1_PERMISSION_DEFERRED_TO_SETTINGS source=" + source);
      micPermissionFlowSource = source;
      isAwaitingMicPermissionSettings = true;
      isMicPermissionRequestInFlight = false;
      host.onNeedOpenSettingsDialog(source);
      return;
    }

    isMicPermissionRequestInFlight = true;
    lastMicPermissionRequestMs = now;
    host.requestPermissionLaunch();
  }

  public void onPermissionResult(boolean isGranted) {
    isMicPermissionRequestInFlight = false;

    if (!isGranted) {
      micPermissionDeniedCount++;
      micPermissionRequestedBefore = true;
      host.gate("M1_TOAST_SHOWN source=permission_denied");
      host.ux("UX_PERMISSION_DENIED", "source=activity_result");
      host.onPermissionDeniedToast();
      if (shouldOpenAppSettingsForMic()) {
        isAwaitingMicPermissionSettings = true;
        host.onNeedOpenSettingsDialog("permission_result");
      }
      return;
    }

    micPermissionDeniedCount = 0;
    micPermissionRequestedBefore = true;
    continueMicFlowIfPermissionGranted();
  }

  public void onResume() {
    if (!host.isHostActive()) {
      return;
    }

    if (isAwaitingMicPermissionSettings && host.isMicPermissionGranted()) {
      host.gate("M1_PERMISSION_SETTINGS_RETURN source=" + getFlowSourceOrUnknown());
      showBeforeSpeakingAfterSettingsReturnIfPossible();
      return;
    }

    if (isAwaitingMicPermissionSettings) {
      host.ux("UX_PERMISSION_STILL_DENIED", "source=" + getFlowSourceOrUnknown());
      return;
    }

    if (pendingMicSentenceToRequest != null && host.isMicPermissionGranted()) {
      host.gate("M1_PERMISSION_SETTINGS_RETURN source=resume_pending");
      continueMicFlowIfPermissionGranted();
    }
  }

  public void onSettingsDialogCancelled() {
    isAwaitingMicPermissionSettings = false;
  }

  public void markSettingsOpened() {
    isAwaitingMicPermissionSettings = true;
  }

  public boolean isRequestInFlight() {
    return isMicPermissionRequestInFlight;
  }

  @NonNull
  public String getFlowSourceOrUnknown() {
    return micPermissionFlowSource == null ? "unknown" : micPermissionFlowSource;
  }

  private void continueMicFlowIfPermissionGranted() {
    if (!host.isMicPermissionGranted()) {
      return;
    }

    String targetSentence = host.onConsumePendingSentenceFromSpeakingCoordinator();
    if (isBlank(targetSentence)) {
      targetSentence = pendingMicSentenceToRequest;
    }
    if (isBlank(targetSentence)) {
      return;
    }

    pendingMicSentenceToRequest = null;
    micPermissionFlowSource = null;
    isAwaitingMicPermissionSettings = false;
    isMicPermissionRequestInFlight = false;
    host.onStartSpeakingAfterPermission(targetSentence);
  }

  private void showBeforeSpeakingAfterSettingsReturnIfPossible() {
    String targetSentence = host.onConsumePendingSentenceFromSpeakingCoordinator();
    if (isBlank(targetSentence)) {
      targetSentence = pendingMicSentenceToRequest;
    }
    if (!isBlank(targetSentence)) {
      host.onShowBeforeSpeakingAfterPermission(targetSentence);
    }

    pendingMicSentenceToRequest = null;
    micPermissionFlowSource = null;
    isAwaitingMicPermissionSettings = false;
    isMicPermissionRequestInFlight = false;
  }

  private boolean shouldOpenAppSettingsForMic() {
    return micPermissionRequestedBefore
        && micPermissionDeniedCount >= SETTINGS_HINT_DENIAL_THRESHOLD
        && !host.isMicPermissionGranted()
        && !host.hasMicPermissionRationale();
  }

  private boolean isBlank(@Nullable String text) {
    return text == null || text.trim().isEmpty();
  }
}
