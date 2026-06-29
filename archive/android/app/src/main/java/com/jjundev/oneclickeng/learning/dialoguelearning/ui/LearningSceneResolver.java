package com.jjundev.oneclickeng.learning.dialoguelearning.ui;

import androidx.annotation.NonNull;
import com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator.LearningSessionSnapshot;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.BottomSheetMode;

public final class LearningSceneResolver {

  @NonNull
  public LearningScene resolve(@NonNull LearningSessionSnapshot snapshot) {
    if (snapshot.isFinished()) {
      return LearningScene.FINISHED;
    }

    BottomSheetMode mode = snapshot.getBottomSheetMode();
    if (mode == null) {
      return LearningScene.DEFAULT_INPUT;
    }

    switch (mode) {
      case BEFORE_SPEAKING:
        return LearningScene.BEFORE_SPEAKING;
      case WHILE_SPEAKING:
        return LearningScene.WHILE_SPEAKING;
      case FEEDBACK:
        return LearningScene.FEEDBACK;
      case FINISHED:
        return LearningScene.FINISHED;
      case DEFAULT_INPUT:
      default:
        return LearningScene.DEFAULT_INPUT;
    }
  }
}
