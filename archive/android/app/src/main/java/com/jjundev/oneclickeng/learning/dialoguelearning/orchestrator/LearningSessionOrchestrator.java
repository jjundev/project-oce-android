package com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.learning.dialoguelearning.controller.ExtraQuestionFlowController;
import com.jjundev.oneclickeng.learning.dialoguelearning.controller.FeedbackFlowController;
import com.jjundev.oneclickeng.learning.dialoguelearning.controller.ScriptFlowController;
import com.jjundev.oneclickeng.learning.dialoguelearning.controller.SpeakingFlowController;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.ScriptTurn;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.BottomSheetMode;

public class LearningSessionOrchestrator {

  @NonNull private final ScriptFlowController scriptFlowController;
  @NonNull private final SpeakingFlowController speakingFlowController;
  @NonNull private final FeedbackFlowController feedbackFlowController;
  @NonNull private final ExtraQuestionFlowController extraQuestionFlowController;
  @Nullable private String lastRequestedSentence;
  @Nullable private TurnDecision lastTurnDecision;

  public static final class TurnDecision {
    @NonNull private final ScriptFlowController.NextTurnResult nextTurnResult;
    @NonNull private final BottomSheetMode proposedBottomSheetMode;
    private final boolean shouldPlayScriptTts;
    private final boolean shouldOpenSummary;
    @Nullable private final String scriptTextForTts;

    public TurnDecision(
        @NonNull ScriptFlowController.NextTurnResult nextTurnResult,
        @NonNull BottomSheetMode proposedBottomSheetMode,
        boolean shouldPlayScriptTts,
        boolean shouldOpenSummary,
        @Nullable String scriptTextForTts) {
      this.nextTurnResult = nextTurnResult;
      this.proposedBottomSheetMode = proposedBottomSheetMode;
      this.shouldPlayScriptTts = shouldPlayScriptTts;
      this.shouldOpenSummary = shouldOpenSummary;
      this.scriptTextForTts = scriptTextForTts;
    }

    @NonNull
    public ScriptFlowController.NextTurnResult getNextTurnResult() {
      return nextTurnResult;
    }

    @NonNull
    public BottomSheetMode getProposedBottomSheetMode() {
      return proposedBottomSheetMode;
    }

    public boolean shouldPlayScriptTts() {
      return shouldPlayScriptTts;
    }

    public boolean shouldOpenSummary() {
      return shouldOpenSummary;
    }

    @Nullable
    public String getScriptTextForTts() {
      return scriptTextForTts;
    }
  }

  public LearningSessionOrchestrator(
      @NonNull ScriptFlowController scriptFlowController,
      @NonNull SpeakingFlowController speakingFlowController,
      @NonNull FeedbackFlowController feedbackFlowController,
      @NonNull ExtraQuestionFlowController extraQuestionFlowController) {
    this.scriptFlowController = scriptFlowController;
    this.speakingFlowController = speakingFlowController;
    this.feedbackFlowController = feedbackFlowController;
    this.extraQuestionFlowController = extraQuestionFlowController;
  }

  public void loadScript(@NonNull String scriptJson) throws Exception {
    clearRequestsForTurnBoundary();
    scriptFlowController.loadScript(scriptJson);
  }

  @NonNull
  public TurnDecision moveToNextTurnDecision() {
    ScriptFlowController.NextTurnResult nextTurnResult = scriptFlowController.moveToNextTurn();
    clearRequestsForTurnBoundary();

    if (nextTurnResult.getType() == ScriptFlowController.NextTurnResult.Type.EMPTY) {
      lastTurnDecision =
          new TurnDecision(nextTurnResult, BottomSheetMode.DEFAULT_INPUT, false, false, null);
      return lastTurnDecision;
    }

    if (nextTurnResult.getType() == ScriptFlowController.NextTurnResult.Type.WAITING) {
      lastTurnDecision =
          new TurnDecision(nextTurnResult, BottomSheetMode.DEFAULT_INPUT, false, false, null);
      return lastTurnDecision;
    }

    if (nextTurnResult.getType() == ScriptFlowController.NextTurnResult.Type.FINISHED) {
      lastTurnDecision =
          new TurnDecision(nextTurnResult, BottomSheetMode.FINISHED, false, true, null);
      return lastTurnDecision;
    }

    ScriptTurn nextTurn = nextTurnResult.getTurn();
    if (nextTurn != null && nextTurn.isOpponentTurn()) {
      String ttsText = nextTurn.getEnglish();
      lastTurnDecision =
          new TurnDecision(nextTurnResult, BottomSheetMode.BEFORE_SPEAKING, true, false, ttsText);
    } else {
      lastTurnDecision =
          new TurnDecision(nextTurnResult, BottomSheetMode.DEFAULT_INPUT, false, false, null);
    }

    return lastTurnDecision;
  }

  @NonNull
  public ScriptFlowController.NextTurnResult moveToNextTurn() {
    return moveToNextTurnDecision().getNextTurnResult();
  }

  public long analyzeSpeaking(
      @NonNull String originalSentence,
      @NonNull byte[] audioData,
      String fallbackRecognizedText,
      @NonNull SpeakingFlowController.Callback callback) {
    return speakingFlowController.analyzeSpeaking(
        originalSentence, audioData, fallbackRecognizedText, callback);
  }

  public long startSentenceFeedback(
      @NonNull String originalSentence,
      @NonNull String userTranscript,
      @NonNull FeedbackFlowController.Callback callback) {
    return feedbackFlowController.startSentenceFeedback(originalSentence, userTranscript, callback);
  }

  public void askExtraQuestion(
      @NonNull String originalSentence,
      @NonNull String userSentence,
      @NonNull String question,
      @NonNull ExtraQuestionFlowController.Callback callback) {
    extraQuestionFlowController.ask(originalSentence, userSentence, question, callback);
  }

  public void invalidateSpeakingRequest() {
    speakingFlowController.invalidate();
  }

  public void clearRequests() {
    speakingFlowController.invalidate();
    feedbackFlowController.invalidate();
    extraQuestionFlowController.invalidate();
  }

  public void clearRequestsForTurnBoundary() {
    clearRequests();
  }

  public void onTurnStarted() {
    clearRequestsForTurnBoundary();
  }

  @Nullable
  public TurnDecision getLastTurnDecision() {
    return lastTurnDecision;
  }

  public int getCurrentScriptIndex() {
    return scriptFlowController.getCurrentIndex();
  }

  @Nullable
  public String getLastRequestedSentence() {
    return lastRequestedSentence;
  }

  public void onRecordingRequested(@NonNull String sentenceToTranslate) {
    lastRequestedSentence = sentenceToTranslate;
    clearRequestsForTurnBoundary();
  }

  public void onRecordingStarted() {
    clearRequestsForTurnBoundary();
  }

  public void onAudioChunk(@NonNull byte[] audioData) {
    // audio streaming remains in ViewModel for now.
  }
}
