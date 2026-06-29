package com.jjundev.oneclickeng.learning.dialoguelearning.ui;

import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class LearningBottomSheetActionRouter
    implements BottomSheetSceneRenderer.BottomSheetActionHandler {

  public interface ActionDelegate {
    void hideKeyboard();

    void presentBeforeSpeakingScene(@Nullable String sentenceToTranslate);

    void requestDefaultInputSceneOnceAfterDelayIfFirstTime(@Nullable String sentenceToTranslate);

    void handleBeforeSpeakToRecord(@Nullable String sentenceToTranslate);

    void stopSpeakingSession();

    void invalidatePendingSpeakingAnalysis();

    void presentFeedbackSceneForDefaultInput(
        @Nullable String originalSentence, @Nullable String translatedSentence);

    void handleNextStepSequence(@Nullable String userMessage, @Nullable byte[] audioData);

    void clearLastRecordedAudio();

    void emitOpenSummaryOrFallback();

    void playRecordedAudio(@Nullable byte[] audio, @Nullable ImageView speakerButton);

    void requestMicPermission(@Nullable String sentenceToTranslate, @NonNull String source);

    void stopRippleAnimation();
  }

  public interface LoggerDelegate {
    void trace(@NonNull String key);

    void gate(@NonNull String key);

    void ux(@NonNull String key, @Nullable String fields);
  }

  @NonNull private final ActionDelegate actionDelegate;
  @NonNull private final LoggerDelegate loggerDelegate;

  public LearningBottomSheetActionRouter(
      @NonNull ActionDelegate actionDelegate, @NonNull LoggerDelegate loggerDelegate) {
    this.actionDelegate = actionDelegate;
    this.loggerDelegate = loggerDelegate;
  }

  @Override
  public void onDefaultMicClicked(String sentenceToTranslate) {
    loggerDelegate.trace("TRACE_ACTION_DEFAULT_MIC sentenceLen=" + safeLength(sentenceToTranslate));
    actionDelegate.hideKeyboard();
    actionDelegate.presentBeforeSpeakingScene(sentenceToTranslate);
  }

  @Override
  public void onDefaultSendClicked(String translatedSentence, String originalSentence) {
    loggerDelegate.trace(
        "TRACE_ACTION_DEFAULT_SEND translatedLen="
            + safeLength(translatedSentence)
            + " originalLen="
            + safeLength(originalSentence));
    actionDelegate.hideKeyboard();
    if (translatedSentence == null || translatedSentence.trim().isEmpty()) {
      return;
    }
    actionDelegate.presentFeedbackSceneForDefaultInput(originalSentence, translatedSentence);
  }

  @Override
  public void onBeforeSpeakToRecord(String sentenceToTranslate) {
    loggerDelegate.trace(
        "TRACE_ACTION_BEFORE_RECORD sentenceLen=" + safeLength(sentenceToTranslate));
    actionDelegate.handleBeforeSpeakToRecord(sentenceToTranslate);
  }

  @Override
  public void onBeforeSpeakBack(String sentenceToTranslate) {
    loggerDelegate.trace("TRACE_ACTION_BEFORE_BACK sentenceLen=" + safeLength(sentenceToTranslate));
    actionDelegate.stopRippleAnimation();
    actionDelegate.requestDefaultInputSceneOnceAfterDelayIfFirstTime(sentenceToTranslate);
  }

  @Override
  public void onWhileSpeakBack(String sentenceToTranslate) {
    loggerDelegate.trace("TRACE_ACTION_WHILE_BACK sentenceLen=" + safeLength(sentenceToTranslate));
    actionDelegate.stopSpeakingSession();
    actionDelegate.invalidatePendingSpeakingAnalysis();
    actionDelegate.presentBeforeSpeakingScene(sentenceToTranslate);
  }

  @Override
  public void onFeedbackRetry(String sentenceToTranslate, boolean isSpeakingMode) {
    loggerDelegate.trace(
        "TRACE_ACTION_FEEDBACK_RETRY isSpeakingMode="
            + isSpeakingMode
            + " sentenceLen="
            + safeLength(sentenceToTranslate));
    if (isSpeakingMode) {
      actionDelegate.presentBeforeSpeakingScene(sentenceToTranslate);
    } else {
      actionDelegate.requestDefaultInputSceneOnceAfterDelayIfFirstTime(sentenceToTranslate);
    }
  }

  @Override
  public void onFeedbackNext(String userMessage, byte[] recordedAudio, boolean isSpeakingMode) {
    loggerDelegate.trace(
        "TRACE_ACTION_FEEDBACK_NEXT isSpeakingMode="
            + isSpeakingMode
            + " messageLen="
            + safeLength(userMessage)
            + " audioLen="
            + (recordedAudio == null ? 0 : recordedAudio.length));
    actionDelegate.handleNextStepSequence(userMessage, isSpeakingMode ? recordedAudio : null);
    if (isSpeakingMode) {
      actionDelegate.clearLastRecordedAudio();
    }
  }

  @Override
  public void onOpenSummary() {
    loggerDelegate.trace("TRACE_ACTION_OPEN_SUMMARY");
    actionDelegate.emitOpenSummaryOrFallback();
  }

  @Override
  public void onPlayRecordedAudio(byte[] audio, ImageView speakerButton) {
    loggerDelegate.trace(
        "TRACE_ACTION_PLAY_RECORDED audioLen=" + (audio == null ? 0 : audio.length));
    if (audio == null || speakerButton == null) {
      return;
    }
    actionDelegate.playRecordedAudio(audio, speakerButton);
  }

  @Override
  public void onNeedPermission(String sentenceToTranslate) {
    loggerDelegate.trace(
        "TRACE_ACTION_NEED_PERMISSION sentenceLen=" + safeLength(sentenceToTranslate));
    String safeSentence = sentenceToTranslate == null ? "" : sentenceToTranslate;
    actionDelegate.requestMicPermission(safeSentence, "bottom_sheet");
  }

  private int safeLength(@Nullable String text) {
    return text == null ? 0 : text.length();
  }
}
