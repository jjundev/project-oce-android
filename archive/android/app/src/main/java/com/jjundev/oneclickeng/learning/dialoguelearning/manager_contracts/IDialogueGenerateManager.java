package com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface IDialogueGenerateManager {

  interface InitCallback {
    void onReady();

    void onError(String error);
  }

  interface ScriptGenerationCallback {
    void onSuccess(String jsonResult);

    void onError(Throwable t);
  }

  final class ScriptTurnChunk {
    @NonNull private final String korean;
    @NonNull private final String english;
    @NonNull private final String role;

    public ScriptTurnChunk(@NonNull String korean, @NonNull String english, @NonNull String role) {
      this.korean = korean;
      this.english = english;
      this.role = role;
    }

    @NonNull
    public String getKorean() {
      return korean;
    }

    @NonNull
    public String getEnglish() {
      return english;
    }

    @NonNull
    public String getRole() {
      return role;
    }
  }

  interface ScriptStreamingCallback {
    void onMetadata(
        @NonNull String topic, @NonNull String opponentName, @NonNull String opponentGender);

    void onTurn(@NonNull ScriptTurnChunk turn);

    void onComplete(@Nullable String warningMessage);

    void onFailure(@NonNull String error);
  }

  void initializeCache(@NonNull InitCallback callback);

  void generateScript(
      @NonNull String level,
      @NonNull String topic,
      @NonNull String format,
      int length,
      @NonNull ScriptGenerationCallback callback);

  void generateScriptStreamingAsync(
      @NonNull String level,
      @NonNull String topic,
      @NonNull String format,
      int length,
      @NonNull ScriptStreamingCallback callback);
}
