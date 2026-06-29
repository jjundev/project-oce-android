package com.jjundev.oneclickeng.learning.dialoguelearning.controller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.DialogueScript;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.ScriptTurn;
import com.jjundev.oneclickeng.learning.dialoguelearning.parser.DialogueScriptParser;
import java.util.ArrayList;

public class ScriptFlowController {

  public static class NextTurnResult {
    public enum Type {
      EMPTY,
      TURN,
      WAITING,
      FINISHED
    }

    @NonNull private final Type type;
    @Nullable private final ScriptTurn turn;
    private final int currentStep;
    private final int totalSteps;

    public NextTurnResult(
        @NonNull Type type, @Nullable ScriptTurn turn, int currentStep, int totalSteps) {
      this.type = type;
      this.turn = turn;
      this.currentStep = currentStep;
      this.totalSteps = totalSteps;
    }

    @NonNull
    public Type getType() {
      return type;
    }

    @Nullable
    public ScriptTurn getTurn() {
      return turn;
    }

    public int getCurrentStep() {
      return currentStep;
    }

    public int getTotalSteps() {
      return totalSteps;
    }
  }

  private final DialogueScriptParser parser;
  @Nullable private DialogueScript script;
  private int currentIndex = -1;
  private boolean streamCompleted = false;

  public ScriptFlowController(@NonNull DialogueScriptParser parser) {
    this.parser = parser;
  }

  public void loadScript(@NonNull String scriptJson) throws Exception {
    this.script = parser.parse(scriptJson);
    this.currentIndex = -1;
    this.streamCompleted = true;
  }

  public void startStreaming(
      @NonNull String topic,
      @NonNull String opponentName,
      @NonNull String opponentRole,
      @NonNull String opponentGender) {
    this.script =
        new DialogueScript(topic, opponentName, opponentRole, opponentGender, new ArrayList<>());
    this.currentIndex = -1;
    this.streamCompleted = false;
  }

  public void updateStreamMetadata(
      @NonNull String topic,
      @NonNull String opponentName,
      @NonNull String opponentRole,
      @NonNull String opponentGender) {
    if (script == null) {
      startStreaming(topic, opponentName, opponentRole, opponentGender);
      return;
    }
    script.updateMetadata(topic, opponentName, opponentRole, opponentGender);
  }

  public void appendStreamTurn(@NonNull ScriptTurn turn) {
    if (script == null) {
      startStreaming("영어 연습", "AI Coach", "Partner", "female");
    }
    if (script != null) {
      script.appendTurn(turn);
    }
  }

  public void markStreamCompleted() {
    streamCompleted = true;
  }

  public boolean isStreamCompleted() {
    return streamCompleted;
  }

  @Nullable
  public DialogueScript getScript() {
    return script;
  }

  public int getCurrentIndex() {
    return currentIndex;
  }

  public int getTotalSteps() {
    return script == null ? 0 : script.size();
  }

  @NonNull
  public NextTurnResult moveToNextTurn() {
    if (script == null || script.size() == 0) {
      if (streamCompleted) {
        return new NextTurnResult(NextTurnResult.Type.EMPTY, null, 0, 0);
      }
      return new NextTurnResult(NextTurnResult.Type.WAITING, null, 0, 0);
    }

    int nextIndex = currentIndex + 1;
    if (nextIndex >= script.size()) {
      if (streamCompleted) {
        currentIndex = script.size();
        return new NextTurnResult(NextTurnResult.Type.FINISHED, null, script.size(), script.size());
      }
      return new NextTurnResult(NextTurnResult.Type.WAITING, null, script.size(), script.size());
    }

    currentIndex = nextIndex;
    ScriptTurn turn = script.getTurns().get(currentIndex);
    return new NextTurnResult(NextTurnResult.Type.TURN, turn, currentIndex + 1, script.size());
  }
}
