package com.jjundev.oneclickeng.learning.dialoguelearning.model;

import androidx.annotation.NonNull;

public class ScriptTurn {
  @NonNull private final String korean;
  @NonNull private final String english;
  @NonNull private final String role;

  public ScriptTurn(@NonNull String korean, @NonNull String english, @NonNull String role) {
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

  public boolean isOpponentTurn() {
    return "model".equalsIgnoreCase(role) || "opponent".equalsIgnoreCase(role);
  }
}
