package com.jjundev.oneclickeng.learning.dialoguelearning.session;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IDialogueGenerateManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface DialogueScriptStreamingSessionStore {

  final class ScriptMetadata {
    @NonNull private final String topic;
    @NonNull private final String opponentName;
    @NonNull private final String opponentGender;

    public ScriptMetadata(
        @NonNull String topic, @NonNull String opponentName, @NonNull String opponentGender) {
      this.topic = topic;
      this.opponentName = opponentName;
      this.opponentGender = opponentGender;
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
  }

  interface Listener {
    void onMetadata(@NonNull ScriptMetadata metadata);

    void onTurn(@NonNull IDialogueGenerateManager.ScriptTurnChunk turn);

    void onComplete(@Nullable String warningMessage);

    void onFailure(@NonNull String error);
  }

  @NonNull
  String startSession(
      @NonNull IDialogueGenerateManager manager,
      @NonNull String level,
      @NonNull String topic,
      @NonNull String format,
      int requestedLength);

  @Nullable
  Snapshot attach(@Nullable String sessionId, @NonNull Listener listener);

  void detach(@Nullable String sessionId, @NonNull Listener listener);

  void release(@Nullable String sessionId);

  final class Snapshot {
    @Nullable private final ScriptMetadata metadata;
    @NonNull private final List<IDialogueGenerateManager.ScriptTurnChunk> bufferedTurns;
    private final int requestedLength;
    @NonNull private final String requestedTopic;
    private final boolean completed;
    @Nullable private final String warningMessage;
    @Nullable private final String failureMessage;

    public Snapshot(
        @Nullable ScriptMetadata metadata,
        @NonNull List<IDialogueGenerateManager.ScriptTurnChunk> bufferedTurns,
        int requestedLength,
        @NonNull String requestedTopic,
        boolean completed,
        @Nullable String warningMessage,
        @Nullable String failureMessage) {
      this.metadata = metadata;
      this.bufferedTurns = Collections.unmodifiableList(new ArrayList<>(bufferedTurns));
      this.requestedLength = requestedLength;
      this.requestedTopic = requestedTopic;
      this.completed = completed;
      this.warningMessage = warningMessage;
      this.failureMessage = failureMessage;
    }

    @Nullable
    public ScriptMetadata getMetadata() {
      return metadata;
    }

    @NonNull
    public List<IDialogueGenerateManager.ScriptTurnChunk> getBufferedTurns() {
      return bufferedTurns;
    }

    public int getRequestedLength() {
      return requestedLength;
    }

    @NonNull
    public String getRequestedTopic() {
      return requestedTopic;
    }

    public boolean isCompleted() {
      return completed;
    }

    @Nullable
    public String getWarningMessage() {
      return warningMessage;
    }

    @Nullable
    public String getFailureMessage() {
      return failureMessage;
    }
  }
}
