package com.jjundev.oneclickeng.learning.dialoguelearning.coordinator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.learning.dialoguelearning.controller.ScriptFlowController;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.ScriptTurn;
import com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator.LearningSessionOrchestrator;
import com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator.TurnPlaybackCoordinator;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.ChatAdapter;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.ChatMessage;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.LearningBottomSheetController;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.LearningChatRenderer;
import java.util.List;

public final class DialogueTurnCoordinator {

  public interface LoggerDelegate {
    void trace(@NonNull String key);

    void gate(@NonNull String key);

    void ux(@NonNull String key, @Nullable String fields);
  }

  public interface TurnDataDelegate {
    @Nullable
    LearningSessionOrchestrator.TurnDecision moveToNextTurnDecision();

    void emitScrollChatToBottom();
  }

  public interface TurnActionDelegate {
    boolean isHostActive();

    void clearFeedbackBinding();

    void requestDefaultInputSceneOnceAfterDelayIfFirstTime(@Nullable String sentenceToTranslate);

    void requestLearningFinishedSceneOnceAfterDelay();

    void requestScriptTtsPlayback(@NonNull String text, @NonNull Runnable onDone);
  }

  @NonNull private final LoggerDelegate loggerDelegate;
  @NonNull private final TurnDataDelegate turnDataDelegate;
  @NonNull private final TurnActionDelegate turnActionDelegate;

  @NonNull
  private final TurnPlaybackCoordinator turnPlaybackCoordinator = new TurnPlaybackCoordinator();

  @Nullable private List<ChatMessage> messageList;
  @Nullable private ChatAdapter chatAdapter;
  @Nullable private LearningChatRenderer chatRenderer;
  @Nullable private LearningBottomSheetController bottomSheetController;

  private boolean hasStartedTurnFlow;

  public DialogueTurnCoordinator(
      @NonNull LoggerDelegate loggerDelegate,
      @NonNull TurnDataDelegate turnDataDelegate,
      @NonNull TurnActionDelegate turnActionDelegate) {
    this.loggerDelegate = loggerDelegate;
    this.turnDataDelegate = turnDataDelegate;
    this.turnActionDelegate = turnActionDelegate;
  }

  public void bindChatComponents(
      @NonNull List<ChatMessage> messageList,
      @Nullable ChatAdapter chatAdapter,
      @Nullable LearningChatRenderer chatRenderer,
      @Nullable LearningBottomSheetController bottomSheetController) {
    this.messageList = messageList;
    this.chatAdapter = chatAdapter;
    this.chatRenderer = chatRenderer;
    this.bottomSheetController = bottomSheetController;
  }

  public void tryStartTurnFlow(boolean isScriptLoaded, boolean isTtsInitialized) {
    if (hasStartedTurnFlow || !isScriptLoaded || !isTtsInitialized) {
      if (hasStartedTurnFlow) {
        loggerDelegate.trace("TRACE_TRY_START_FLOW skip reason=already_started");
      } else if (!isScriptLoaded) {
        loggerDelegate.trace("TRACE_TRY_START_FLOW skip reason=script_not_loaded");
      } else if (!isTtsInitialized) {
        loggerDelegate.trace("TRACE_TRY_START_FLOW skip reason=tts_not_ready");
      }
      return;
    }

    hasStartedTurnFlow = true;
    loggerDelegate.trace("TRACE_TRY_START_FLOW start");
    processNextScriptStep();
  }

  public void advanceFromNextButton(@Nullable String userMessage, @Nullable byte[] audioData) {
    loggerDelegate.trace(
        "TRACE_NEXT_STEP_SEQUENCE msgLen="
            + (userMessage == null ? 0 : userMessage.length())
            + " audioLen="
            + (audioData == null ? 0 : audioData.length));
    turnActionDelegate.clearFeedbackBinding();

    if (bottomSheetController != null) {
      bottomSheetController.clearContent();
    }

    addUserMessage(userMessage, audioData);

    if (chatAdapter != null) {
      if (bottomSheetController != null) {
        bottomSheetController.setAutoScrollRequested(true);
      }
    }
    if (chatRenderer != null) {
      chatRenderer.scrollToBottom();
    }
    turnDataDelegate.emitScrollChatToBottom();

    loggerDelegate.gate("M3_TURN_ADVANCE source=next_button");
    loggerDelegate.ux("UX_TURN_ADVANCE", "source=next_button");
    processNextScriptStep();
  }

  public void processNextScriptStep() {
    if (!turnActionDelegate.isHostActive()) {
      return;
    }

    LearningSessionOrchestrator.TurnDecision turnDecision =
        turnDataDelegate.moveToNextTurnDecision();
    if (turnDecision == null) {
      loggerDelegate.trace("TRACE_TURN_PATH reason=viewModel_null");
      return;
    }

    ScriptFlowController.NextTurnResult turnResult = turnDecision.getNextTurnResult();

    if (turnDecision.shouldOpenSummary()) {
      loggerDelegate.trace("TRACE_TURN_PATH action=summary");
      loggerDelegate.trace("TRACE_SUMMARY_TRIGGER reason=finished");
      turnActionDelegate.requestLearningFinishedSceneOnceAfterDelay();
      return;
    }

    if (turnResult.getType() == ScriptFlowController.NextTurnResult.Type.EMPTY) {
      loggerDelegate.trace("TRACE_TURN_PATH action=empty_turn");
      return;
    }
    if (turnResult.getType() == ScriptFlowController.NextTurnResult.Type.WAITING) {
      loggerDelegate.trace("TRACE_TURN_PATH action=waiting_turn");
      return;
    }

    ScriptTurn turn = turnResult.getTurn();
    if (turn == null) {
      loggerDelegate.trace("TRACE_TURN_PATH action=turn_null");
      return;
    }
    loggerDelegate.trace(
        "TRACE_TURN_PATH turnType="
            + (turn.isOpponentTurn() ? "opponent" : "user")
            + " step="
            + turnResult.getCurrentStep()
            + "/"
            + turnResult.getTotalSteps()
            + " shouldPlayTts="
            + turnDecision.shouldPlayScriptTts());
    loggerDelegate.ux(
        "UX_TURN_ENTER",
        "role="
            + (turn.isOpponentTurn() ? "opponent" : "user")
            + " step="
            + turnResult.getCurrentStep()
            + "/"
            + turnResult.getTotalSteps());
    loggerDelegate.gate(
        "M3_TURN_ENTER role="
            + (turn.isOpponentTurn() ? "opponent" : "user")
            + " step="
            + turnResult.getCurrentStep()
            + "/"
            + turnResult.getTotalSteps());

    if (turn.isOpponentTurn() || turnDecision.shouldPlayScriptTts()) {
      handleOpponentTurn(turn);
      return;
    }
    handleUserTurn(turn);
  }

  public void release() {
    turnPlaybackCoordinator.clear();
    hasStartedTurnFlow = false;
    messageList = null;
    chatAdapter = null;
    chatRenderer = null;
    bottomSheetController = null;
  }

  private void handleOpponentTurn(@NonNull ScriptTurn turn) {
    loggerDelegate.trace(
        "TRACE_TURN_HANDLER type=opponent sentenceLen="
            + (turn.getKorean() == null ? 0 : turn.getKorean().length()));
    if (bottomSheetController != null) {
      bottomSheetController.setVisible(true);
      bottomSheetController.clearContent();
    }
    scheduleOpponentReply(turn);
  }

  private void handleUserTurn(@NonNull ScriptTurn turn) {
    loggerDelegate.trace(
        "TRACE_TURN_HANDLER type=user sentenceLen="
            + (turn.getKorean() == null ? 0 : turn.getKorean().length()));
    if (bottomSheetController != null) {
      bottomSheetController.setVisible(true);
    }
    turnActionDelegate.requestDefaultInputSceneOnceAfterDelayIfFirstTime(turn.getKorean());
  }

  private void scheduleOpponentReply(@NonNull ScriptTurn turn) {
    turnPlaybackCoordinator.scheduleOpponentReply(
        turn,
        new TurnPlaybackCoordinator.Callback() {
          @Override
          public boolean isActive() {
            return turnActionDelegate.isHostActive();
          }

          @Override
          public void onShowSkeleton() {
            addSkeletonMessage();
          }

          @Override
          public void onRenderOpponentMessage(@NonNull ScriptTurn turn) {
            removeSkeletonMessage();
            addOpponentMessage(turn.getEnglish(), turn.getKorean());
          }

          @Override
          public void onPlayScriptTts(@NonNull String text, @NonNull Runnable onDone) {
            turnActionDelegate.requestScriptTtsPlayback(text, onDone);
          }

          @Override
          public void onAdvanceToNextTurn() {
            loggerDelegate.gate("M3_TURN_ADVANCE source=opponent_reply_done");
            loggerDelegate.ux("UX_TURN_ADVANCE", "source=opponent_reply_done");
            processNextScriptStep();
          }
        });
  }

  private void addOpponentMessage(String eng, String ko) {
    if (messageList == null) {
      return;
    }
    ChatMessage chatMessage = new ChatMessage(eng, ko, ChatMessage.TYPE_AI);
    messageList.add(chatMessage);
    if (chatAdapter != null) {
      chatAdapter.notifyItemInserted(messageList.size() - 1);
      if (chatRenderer != null) {
        chatRenderer.scrollToBottom();
      }
    }
  }

  private void addUserMessage(String message, byte[] audioData) {
    if (messageList == null) {
      return;
    }
    ChatMessage chatMessage = new ChatMessage(message, ChatMessage.TYPE_USER, audioData);
    messageList.add(chatMessage);
    if (chatAdapter != null) {
      chatAdapter.notifyItemInserted(messageList.size() - 1);
      if (chatRenderer != null) {
        chatRenderer.scrollToBottom();
      }
    }
  }

  private void addSkeletonMessage() {
    if (messageList == null) {
      return;
    }
    ChatMessage skeletonMsg = new ChatMessage("", ChatMessage.TYPE_SKELETON);
    messageList.add(skeletonMsg);
    if (chatAdapter != null) {
      chatAdapter.notifyItemInserted(messageList.size() - 1);
      if (chatRenderer != null) {
        chatRenderer.scrollToBottom();
      }
    }
  }

  private void removeSkeletonMessage() {
    if (messageList == null || messageList.isEmpty()) {
      return;
    }
    int lastIndex = messageList.size() - 1;
    if (messageList.get(lastIndex).getType() == ChatMessage.TYPE_SKELETON) {
      messageList.remove(lastIndex);
      if (chatAdapter != null) {
        chatAdapter.notifyItemRemoved(lastIndex);
      }
    }
  }
}
