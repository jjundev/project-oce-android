package com.jjundev.oneclickeng.learning.dialoguelearning.session;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IDialogueGenerateManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class InMemoryDialogueScriptStreamingSessionStore
    implements DialogueScriptStreamingSessionStore {
  private static final String TAG = "ScriptStreamSessionStore";

  private static final class SessionState {
    @Nullable private ScriptMetadata metadata;

    @NonNull
    private final List<IDialogueGenerateManager.ScriptTurnChunk> bufferedTurns = new ArrayList<>();

    @NonNull private final Set<Listener> listeners = new HashSet<>();
    private int requestedLength = 0;
    @NonNull private String requestedTopic = "";
    private boolean completed = false;
    @Nullable private String warningMessage;
    @Nullable private String failureMessage;
    private boolean released = false;
  }

  @NonNull private final Object lock = new Object();
  @NonNull private final Map<String, SessionState> sessions = new HashMap<>();

  @Override
  @NonNull
  public String startSession(
      @NonNull IDialogueGenerateManager manager,
      @NonNull String level,
      @NonNull String topic,
      @NonNull String format,
      int requestedLength) {
    String sessionId = UUID.randomUUID().toString();
    SessionState state = new SessionState();
    state.requestedLength = Math.max(1, requestedLength);
    state.requestedTopic = topic;
    synchronized (lock) {
      sessions.put(sessionId, state);
    }
    logStream(
        "session start: id="
            + shortSession(sessionId)
            + ", topic="
            + safeText(topic)
            + ", requestedLength="
            + state.requestedLength);

    manager.generateScriptStreamingAsync(
        level,
        topic,
        format,
        requestedLength,
        new IDialogueGenerateManager.ScriptStreamingCallback() {
          @Override
          public void onMetadata(
              @NonNull String metadataTopic,
              @NonNull String opponentName,
              @NonNull String opponentGender) {
            dispatchMetadata(
                sessionId, new ScriptMetadata(metadataTopic, opponentName, opponentGender));
          }

          @Override
          public void onTurn(@NonNull IDialogueGenerateManager.ScriptTurnChunk turn) {
            dispatchTurn(sessionId, turn);
          }

          @Override
          public void onComplete(@Nullable String warningMessage) {
            dispatchComplete(sessionId, warningMessage);
          }

          @Override
          public void onFailure(@NonNull String error) {
            dispatchFailure(sessionId, error);
          }
        });
    return sessionId;
  }

  @Override
  @Nullable
  public Snapshot attach(@Nullable String sessionId, @NonNull Listener listener) {
    if (sessionId == null) {
      return null;
    }
    synchronized (lock) {
      SessionState state = sessions.get(sessionId);
      if (state == null || state.released) {
        logStream("attach failed: id=" + shortSession(sessionId));
        return null;
      }
      state.listeners.add(listener);
      logStream(
          "attach: id="
              + shortSession(sessionId)
              + ", listeners="
              + state.listeners.size()
              + ", bufferedTurns="
              + state.bufferedTurns.size()
              + ", completed="
              + state.completed
              + ", failure="
              + (state.failureMessage != null));
      return new Snapshot(
          state.metadata,
          state.bufferedTurns,
          state.requestedLength,
          state.requestedTopic,
          state.completed,
          state.warningMessage,
          state.failureMessage);
    }
  }

  @Override
  public void detach(@Nullable String sessionId, @NonNull Listener listener) {
    if (sessionId == null) {
      return;
    }
    synchronized (lock) {
      SessionState state = sessions.get(sessionId);
      if (state == null) {
        return;
      }
      state.listeners.remove(listener);
      logStream("detach: id=" + shortSession(sessionId) + ", listeners=" + state.listeners.size());
    }
  }

  @Override
  public void release(@Nullable String sessionId) {
    if (sessionId == null) {
      return;
    }
    synchronized (lock) {
      SessionState state = sessions.remove(sessionId);
      if (state == null) {
        return;
      }
      state.released = true;
      state.listeners.clear();
      logStream("release: id=" + shortSession(sessionId));
    }
  }

  private void dispatchMetadata(@NonNull String sessionId, @NonNull ScriptMetadata metadata) {
    List<Listener> listeners;
    synchronized (lock) {
      SessionState state = sessions.get(sessionId);
      if (state == null || state.released || state.completed || state.failureMessage != null) {
        return;
      }
      state.metadata = metadata;
      listeners = new ArrayList<>(state.listeners);
      logStream(
          "metadata: id="
              + shortSession(sessionId)
              + ", topic="
              + safeText(metadata.getTopic())
              + ", listeners="
              + listeners.size());
    }
    for (Listener listener : listeners) {
      listener.onMetadata(metadata);
    }
  }

  private void dispatchTurn(
      @NonNull String sessionId, @NonNull IDialogueGenerateManager.ScriptTurnChunk turn) {
    List<Listener> listeners;
    synchronized (lock) {
      SessionState state = sessions.get(sessionId);
      if (state == null || state.released || state.completed || state.failureMessage != null) {
        return;
      }
      state.bufferedTurns.add(turn);
      listeners = new ArrayList<>(state.listeners);
      logStream(
          "turn: id="
              + shortSession(sessionId)
              + ", count="
              + state.bufferedTurns.size()
              + "/"
              + state.requestedLength
              + ", listeners="
              + listeners.size()
              + ", role="
              + safeText(turn.getRole()));
    }
    for (Listener listener : listeners) {
      listener.onTurn(turn);
    }
  }

  private void dispatchComplete(@NonNull String sessionId, @Nullable String warningMessage) {
    List<Listener> listeners;
    synchronized (lock) {
      SessionState state = sessions.get(sessionId);
      if (state == null || state.released || state.failureMessage != null) {
        return;
      }
      state.completed = true;
      state.warningMessage = warningMessage;
      listeners = new ArrayList<>(state.listeners);
      logStream(
          "complete: id="
              + shortSession(sessionId)
              + ", bufferedTurns="
              + state.bufferedTurns.size()
              + ", warning="
              + safeText(warningMessage)
              + ", listeners="
              + listeners.size());
    }
    for (Listener listener : listeners) {
      listener.onComplete(warningMessage);
    }
  }

  private void dispatchFailure(@NonNull String sessionId, @NonNull String error) {
    List<Listener> listeners;
    synchronized (lock) {
      SessionState state = sessions.get(sessionId);
      if (state == null || state.released || state.completed) {
        return;
      }
      state.failureMessage = error;
      listeners = new ArrayList<>(state.listeners);
      logStream(
          "failure: id="
              + shortSession(sessionId)
              + ", bufferedTurns="
              + state.bufferedTurns.size()
              + ", error="
              + safeText(error)
              + ", listeners="
              + listeners.size());
    }
    for (Listener listener : listeners) {
      listener.onFailure(error);
    }
  }

  private void logStream(@NonNull String message) {
    Log.d(TAG, "[DL_STREAM] " + message);
  }

  @NonNull
  private static String shortSession(@Nullable String sessionId) {
    if (sessionId == null) {
      return "-";
    }
    String trimmed = sessionId.trim();
    if (trimmed.length() <= 8) {
      return trimmed;
    }
    return trimmed.substring(0, 8);
  }

  @NonNull
  private static String safeText(@Nullable String value) {
    if (value == null) {
      return "-";
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return "-";
    }
    if (trimmed.length() <= 32) {
      return trimmed;
    }
    return trimmed.substring(0, 32) + "...";
  }
}
