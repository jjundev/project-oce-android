package com.jjundev.oneclickeng.learning.dialoguelearning.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class DialogueUiEvent {

  public static final class RequestMicPermission extends DialogueUiEvent {
    private final String sentenceToTranslate;

    public RequestMicPermission(@NonNull String sentenceToTranslate) {
      this.sentenceToTranslate = sentenceToTranslate;
    }

    @NonNull
    public String getSentenceToTranslate() {
      return sentenceToTranslate;
    }
  }

  public static final class ShowToast extends DialogueUiEvent {
    private final String message;

    public ShowToast(@NonNull String message) {
      this.message = message;
    }

    @NonNull
    public String getMessage() {
      return message;
    }
  }

  public static final class ScrollChatToBottom extends DialogueUiEvent {}

  public static final class PlayScriptTts extends DialogueUiEvent {
    private final String text;

    public PlayScriptTts(@NonNull String text) {
      this.text = text;
    }

    @NonNull
    public String getText() {
      return text;
    }
  }

  public static final class OpenSummary extends DialogueUiEvent {}

  public static final class AdvanceTurn extends DialogueUiEvent {}

  public static final class AbortLearning extends DialogueUiEvent {
    @Nullable private final String message;

    public AbortLearning(@Nullable String message) {
      this.message = message;
    }

    @Nullable
    public String getMessage() {
      return message;
    }
  }
}
