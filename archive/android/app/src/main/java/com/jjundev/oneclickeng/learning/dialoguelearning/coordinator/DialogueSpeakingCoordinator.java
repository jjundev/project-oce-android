package com.jjundev.oneclickeng.learning.dialoguelearning.coordinator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class DialogueSpeakingCoordinator {

  public interface LoggerDelegate {
    void trace(@NonNull String key);

    void gate(@NonNull String key);

    void ux(@NonNull String key, @Nullable String fields);
  }

  public interface PermissionActionDelegate {
    void requestPermissionLauncher();

    boolean requestPermissionViaViewModel(@NonNull String sentenceToTranslate);
  }

  public interface AnalysisDelegate {
    void analyzeSpeaking(
        @NonNull String sentenceToTranslate,
        @NonNull byte[] audioData,
        @NonNull String recognizedText);
  }

  @NonNull private final LoggerDelegate loggerDelegate;
  @NonNull private final PermissionActionDelegate permissionActionDelegate;
  @NonNull private final AnalysisDelegate analysisDelegate;

  @Nullable private String pendingSentenceToTranslate;
  private boolean isSpeaking;
  private boolean isRecordingActive;
  private boolean isWaitingForAnalysis;
  private long currentAnalysisRequestId;

  public DialogueSpeakingCoordinator(
      @NonNull LoggerDelegate loggerDelegate,
      @NonNull PermissionActionDelegate permissionActionDelegate,
      @NonNull AnalysisDelegate analysisDelegate) {
    this.loggerDelegate = loggerDelegate;
    this.permissionActionDelegate = permissionActionDelegate;
    this.analysisDelegate = analysisDelegate;
  }

  public boolean onBeforeSpeakRecordRequested(
      @Nullable String sentenceToTranslate, boolean hasPermission) {
    if (hasPermission) {
      return true;
    }
    String safeSentence = sentenceToTranslate == null ? "" : sentenceToTranslate;
    pendingSentenceToTranslate = safeSentence;
    loggerDelegate.trace(
        "TRACE_ACTION_PERMISSION_REQUEST source=before_speak sentenceLen=" + safeSentence.length());
    loggerDelegate.gate("M1_PERMISSION_REQUEST source=before_speak");
    loggerDelegate.ux("UX_PERMISSION_REQUESTED", "source=before_speak");
    permissionActionDelegate.requestPermissionLauncher();
    return false;
  }

  public boolean onWhileSpeakingPermissionCheck(
      @Nullable String sentenceToTranslate, boolean hasPermission) {
    if (hasPermission) {
      return true;
    }
    String safeSentence = sentenceToTranslate == null ? "" : sentenceToTranslate;
    pendingSentenceToTranslate = safeSentence;
    loggerDelegate.trace("TRACE_RECORD_PERMISSION_DENIED scene=WHILE_SPEAKING");
    boolean delegated = permissionActionDelegate.requestPermissionViaViewModel(safeSentence);
    if (!delegated) {
      loggerDelegate.ux("UX_PERMISSION_REQUESTED", "source=while_speaking_direct");
    }
    return false;
  }

  public void onUiEventMicPermissionRequested(@Nullable String sentenceToTranslate) {
    String safeSentence = sentenceToTranslate == null ? "" : sentenceToTranslate;
    pendingSentenceToTranslate = safeSentence;
    loggerDelegate.trace(
        "TRACE_ACTION_PERMISSION_REQUEST source=ui_event sentenceLen=" + safeSentence.length());
    loggerDelegate.gate("M1_PERMISSION_REQUEST source=ui_event");
    loggerDelegate.ux("UX_PERMISSION_REQUESTED", "source=ui_event");
    permissionActionDelegate.requestPermissionLauncher();
  }

  public void onPermissionResult(boolean granted) {
    if (granted) {
      loggerDelegate.trace("TRACE_PERMISSION_RESULT granted");
      loggerDelegate.ux("UX_PERMISSION_GRANTED", "source=activity_result");
      return;
    }
    loggerDelegate.trace("TRACE_PERMISSION_RESULT denied");
    loggerDelegate.ux("UX_PERMISSION_DENIED", "source=activity_result");
    pendingSentenceToTranslate = null;
  }

  @Nullable
  public String consumePendingSentenceToTranslate() {
    String sentence = pendingSentenceToTranslate;
    pendingSentenceToTranslate = null;
    return sentence;
  }

  public void setPendingSentenceToTranslate(@Nullable String sentenceToTranslate) {
    pendingSentenceToTranslate = sentenceToTranslate == null ? null : sentenceToTranslate;
  }

  public void onRecordingStarted() {
    isSpeaking = true;
    isRecordingActive = true;
    isWaitingForAnalysis = false;
    currentAnalysisRequestId = 0L;
    loggerDelegate.gate("M2_RECORDING_START");
    loggerDelegate.trace("TRACE_RECORD_START");
    loggerDelegate.ux("UX_RECORD_START", "source=while_speaking");
  }

  public void onRecordingStopRequested(
      @Nullable String sentenceToTranslate,
      @Nullable byte[] audioData,
      @Nullable String transcriptText) {
    String safeSentence = sentenceToTranslate == null ? "" : sentenceToTranslate;
    byte[] safeAudio = audioData == null ? new byte[0] : audioData;
    String recognizedText = transcriptText == null ? "" : transcriptText.trim();
    if (recognizedText.isEmpty()) {
      recognizedText = "(녹음 내용이 없습니다)";
    }

    loggerDelegate.trace("TRACE_RECORD_STOP source=while_speaking");
    loggerDelegate.ux("UX_RECORD_STOP", "source=while_speaking");
    loggerDelegate.trace("TRACE_ANALYSIS_BEGIN sentenceLen=" + safeSentence.length());
    loggerDelegate.gate("M2_ANALYSIS_BEGIN");
    loggerDelegate.ux(
        "UX_ANALYSIS_BEGIN",
        "sentenceLen=" + safeSentence.length() + " audioLen=" + safeAudio.length);

    isSpeaking = false;
    isRecordingActive = false;
    isWaitingForAnalysis = true;
    currentAnalysisRequestId = System.currentTimeMillis();
    analysisDelegate.analyzeSpeaking(safeSentence, safeAudio, recognizedText);
  }

  public void onSpeakingStateSuccess(long requestId, int originalLen, int recognizedLen) {
    loggerDelegate.ux(
        "UX_ANALYSIS_SUCCESS",
        "requestId="
            + requestId
            + " originalLen="
            + Math.max(0, originalLen)
            + " recognizedLen="
            + Math.max(0, recognizedLen));
  }

  public void onSpeakingStateError(long requestId, int messageLen) {
    loggerDelegate.ux(
        "UX_ANALYSIS_ERROR", "requestId=" + requestId + " msgLen=" + Math.max(0, messageLen));
  }

  public void onRecordingHardwareStopped() {
    isRecordingActive = false;
  }

  public void stopSession(@NonNull String reason) {
    isSpeaking = false;
    isRecordingActive = false;
    isWaitingForAnalysis = false;
    currentAnalysisRequestId = 0L;
  }

  public void release() {
    pendingSentenceToTranslate = null;
    stopSession("release");
  }

  public boolean isSpeaking() {
    return isSpeaking;
  }

  public boolean isRecordingActive() {
    return isRecordingActive;
  }

  public boolean isWaitingForAnalysis() {
    return isWaitingForAnalysis;
  }

  public long getCurrentAnalysisRequestId() {
    return currentAnalysisRequestId;
  }
}
