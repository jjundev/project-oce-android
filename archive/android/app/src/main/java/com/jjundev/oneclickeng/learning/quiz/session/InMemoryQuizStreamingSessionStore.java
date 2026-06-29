package com.jjundev.oneclickeng.learning.quiz.session;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IQuizGenerationManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.QuizData;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class InMemoryQuizStreamingSessionStore implements QuizStreamingSessionStore {

  private static final class SessionState {
    @NonNull private final List<QuizData.QuizQuestion> bufferedQuestions = new ArrayList<>();
    @NonNull private final Set<Listener> listeners = new HashSet<>();
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
      @NonNull IQuizGenerationManager manager,
      @NonNull SummaryData seed,
      int requestedQuestionCount) {
    String sessionId = UUID.randomUUID().toString();
    synchronized (lock) {
      sessions.put(sessionId, new SessionState());
    }
    manager.generateQuizFromSummaryStreamingAsync(
        seed,
        requestedQuestionCount,
        new IQuizGenerationManager.QuizStreamingCallback() {
          @Override
          public void onQuestion(@NonNull QuizData.QuizQuestion question) {
            dispatchQuestion(sessionId, question);
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
        return null;
      }
      state.listeners.add(listener);
      return new Snapshot(
          state.bufferedQuestions, state.completed, state.warningMessage, state.failureMessage);
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
    }
  }

  private void dispatchQuestion(
      @NonNull String sessionId, @NonNull QuizData.QuizQuestion question) {
    List<Listener> listeners;
    synchronized (lock) {
      SessionState state = sessions.get(sessionId);
      if (state == null || state.released || state.completed || state.failureMessage != null) {
        return;
      }
      state.bufferedQuestions.add(question);
      listeners = new ArrayList<>(state.listeners);
    }
    for (Listener listener : listeners) {
      listener.onQuestion(question);
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
    }
    for (Listener listener : listeners) {
      listener.onFailure(error);
    }
  }
}
