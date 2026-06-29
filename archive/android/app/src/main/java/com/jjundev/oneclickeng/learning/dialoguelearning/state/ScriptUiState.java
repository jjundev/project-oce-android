package com.jjundev.oneclickeng.learning.dialoguelearning.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.ScriptTurn;

public class ScriptUiState {
  private final int currentStep;
  private final int totalSteps;
  @NonNull private final String topic;
  @NonNull private final String opponentName;
  @NonNull private final String opponentGender;
  private final boolean finished;
  @Nullable private final ScriptTurn activeTurn;

  public ScriptUiState(
      int currentStep,
      int totalSteps,
      @NonNull String topic,
      @NonNull String opponentName,
      @NonNull String opponentGender,
      boolean finished,
      @Nullable ScriptTurn activeTurn) {
    this.currentStep = currentStep;
    this.totalSteps = totalSteps;
    this.topic = topic;
    this.opponentName = opponentName;
    this.opponentGender = opponentGender;
    this.finished = finished;
    this.activeTurn = activeTurn;
  }

  public static ScriptUiState empty() {
    return new ScriptUiState(0, 0, "", "", "female", false, null);
  }

  public int getCurrentStep() {
    return currentStep;
  }

  public int getTotalSteps() {
    return totalSteps;
  }

  @NonNull
  public String getTopic() {
    return topic;
  }

  @NonNull
  public String getOpponentName() {
    return opponentName;
  }

  @NonNull
  public String getOpponentGender() {
    return opponentGender;
  }

  public boolean isFinished() {
    return finished;
  }

  @Nullable
  public ScriptTurn getActiveTurn() {
    return activeTurn;
  }
}
