package com.jjundev.oneclickeng.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IQuizGenerationManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.QuizData;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import com.jjundev.oneclickeng.learning.quiz.DialogueQuizViewModel;
import com.jjundev.oneclickeng.learning.quiz.session.QuizStreamingSessionStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

public class DialogueQuizViewModelTest {

  @Rule public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

  @Test
  public void initialize_transitionsToReadyWhenFirstStreamingQuestionArrives() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");

    DialogueQuizViewModel.QuizUiState loadingState = viewModel.getUiState().getValue();
    assertNotNull(loadingState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.LOADING, loadingState.getStatus());

    manager.emitQuestion(0, buildChoiceQuestion("Q1", "A1"));

    DialogueQuizViewModel.QuizUiState readyState = viewModel.getUiState().getValue();
    assertNotNull(readyState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.READY, readyState.getStatus());
    assertEquals("Q1", readyState.getQuestionState().getQuestionMain());
    assertEquals(5, readyState.getTotalQuestions());
    assertEquals(1, readyState.getCurrentQuestionNumber());
  }

  @Test
  public void onPrimaryAction_waitsUntilNextQuestionArrives() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");
    manager.emitQuestion(0, buildChoiceQuestion("Q1", "A1"));

    viewModel.onChoiceSelected("A1");
    assertEquals(DialogueQuizViewModel.PrimaryActionResult.CHECKED, viewModel.onPrimaryAction());
    assertEquals(
        DialogueQuizViewModel.PrimaryActionResult.WAITING_NEXT_QUESTION,
        viewModel.onPrimaryAction());

    DialogueQuizViewModel.QuizUiState waitingState = viewModel.getUiState().getValue();
    assertNotNull(waitingState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.READY, waitingState.getStatus());
    assertEquals("Q1", waitingState.getQuestionState().getQuestionMain());
    assertTrue(waitingState.getQuestionState().isChecked());

    manager.emitQuestion(0, buildChoiceQuestion("Q2", "A2"));

    assertEquals(
        DialogueQuizViewModel.PrimaryActionResult.MOVED_TO_NEXT, viewModel.onPrimaryAction());
    DialogueQuizViewModel.QuizUiState secondState = viewModel.getUiState().getValue();
    assertNotNull(secondState);
    assertEquals("Q2", secondState.getQuestionState().getQuestionMain());
    assertEquals(2, secondState.getCurrentQuestionNumber());
  }

  @Test
  public void partialStreamCompletion_allowsCompletionWithBufferedQuestions() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");
    manager.emitQuestion(0, buildChoiceQuestion("Q1", "A1"));
    manager.emitQuestion(0, buildChoiceQuestion("Q2", "A2"));
    manager.completeRequest(0, "partial stream interrupted");

    DialogueQuizViewModel.QuizUiState completedStreamState = viewModel.getUiState().getValue();
    assertNotNull(completedStreamState);
    assertEquals(2, completedStreamState.getTotalQuestions());

    viewModel.onChoiceSelected("A1");
    assertEquals(DialogueQuizViewModel.PrimaryActionResult.CHECKED, viewModel.onPrimaryAction());
    assertEquals(
        DialogueQuizViewModel.PrimaryActionResult.MOVED_TO_NEXT, viewModel.onPrimaryAction());

    viewModel.onChoiceSelected("A2");
    assertEquals(DialogueQuizViewModel.PrimaryActionResult.CHECKED, viewModel.onPrimaryAction());
    assertEquals(DialogueQuizViewModel.PrimaryActionResult.COMPLETED, viewModel.onPrimaryAction());

    DialogueQuizViewModel.QuizUiState finalState = viewModel.getUiState().getValue();
    assertNotNull(finalState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.COMPLETED, finalState.getStatus());
    assertEquals(2, finalState.getTotalQuestions());
    assertEquals(2, finalState.getCorrectAnswerCount());
  }

  @Test
  public void streamFailureBeforeFirstQuestion_showsError() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");
    manager.failRequest(0, "network_error");

    DialogueQuizViewModel.QuizUiState errorState = viewModel.getUiState().getValue();
    assertNotNull(errorState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.ERROR, errorState.getStatus());
    assertEquals("network_error", errorState.getErrorMessage());
  }

  @Test
  public void retryLoad_ignoresStaleStreamingCallbacks() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");
    manager.emitQuestion(0, buildChoiceQuestion("OLD_Q1", "A1"));

    viewModel.retryLoad();
    assertEquals(2, manager.requestCount);

    manager.emitQuestion(0, buildChoiceQuestion("OLD_Q2", "A2"));
    manager.failRequest(0, "old_request_failed");

    DialogueQuizViewModel.QuizUiState loadingState = viewModel.getUiState().getValue();
    assertNotNull(loadingState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.LOADING, loadingState.getStatus());

    manager.emitQuestion(1, buildChoiceQuestion("NEW_Q1", "N1"));

    DialogueQuizViewModel.QuizUiState readyState = viewModel.getUiState().getValue();
    assertNotNull(readyState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.READY, readyState.getStatus());
    assertEquals("NEW_Q1", readyState.getQuestionState().getQuestionMain());
  }

  @Test
  public void initialize_withBufferedSessionQuestion_transitionsReadyWithoutFreshRequest() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    FakeQuizStreamingSessionStore sessionStore = new FakeQuizStreamingSessionStore();
    String sessionId =
        sessionStore.createSession(
            Arrays.asList(buildChoiceQuestion("S_Q1", "S_A1")), false, null, null);
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager, sessionStore);

    viewModel.initialize("{}", 5, sessionId);

    DialogueQuizViewModel.QuizUiState state = viewModel.getUiState().getValue();
    assertNotNull(state);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.READY, state.getStatus());
    assertEquals("S_Q1", state.getQuestionState().getQuestionMain());
    assertEquals(0, manager.requestCount);
    assertEquals(0, manager.initCacheCount);
  }

  @Test
  public void onPrimaryAction_waitingState_movesWhenSessionNextQuestionArrives() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    FakeQuizStreamingSessionStore sessionStore = new FakeQuizStreamingSessionStore();
    String sessionId =
        sessionStore.createSession(
            Arrays.asList(buildChoiceQuestion("S_Q1", "S_A1")), false, null, null);
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager, sessionStore);

    viewModel.initialize("{}", 5, sessionId);

    viewModel.onChoiceSelected("S_A1");
    assertEquals(DialogueQuizViewModel.PrimaryActionResult.CHECKED, viewModel.onPrimaryAction());
    assertEquals(
        DialogueQuizViewModel.PrimaryActionResult.WAITING_NEXT_QUESTION,
        viewModel.onPrimaryAction());

    sessionStore.emitQuestion(sessionId, buildChoiceQuestion("S_Q2", "S_A2"));

    assertEquals(
        DialogueQuizViewModel.PrimaryActionResult.MOVED_TO_NEXT, viewModel.onPrimaryAction());
    DialogueQuizViewModel.QuizUiState movedState = viewModel.getUiState().getValue();
    assertNotNull(movedState);
    assertEquals("S_Q2", movedState.getQuestionState().getQuestionMain());
  }

  @Test
  public void initialize_missingSession_fallsBackToFreshStreamingLoad() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    FakeQuizStreamingSessionStore sessionStore = new FakeQuizStreamingSessionStore();
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager, sessionStore);

    viewModel.initialize("{}", 5, "missing_session");

    assertEquals(1, manager.initCacheCount);
    assertEquals(1, manager.requestCount);
    DialogueQuizViewModel.QuizUiState loadingState = viewModel.getUiState().getValue();
    assertNotNull(loadingState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.LOADING, loadingState.getStatus());

    manager.emitQuestion(0, buildChoiceQuestion("F_Q1", "F_A1"));

    DialogueQuizViewModel.QuizUiState readyState = viewModel.getUiState().getValue();
    assertNotNull(readyState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.READY, readyState.getStatus());
    assertEquals("F_Q1", readyState.getQuestionState().getQuestionMain());
  }

  @Test
  public void sessionFailureBeforeFirstQuestion_showsError_andRetryStartsFreshLoad() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    FakeQuizStreamingSessionStore sessionStore = new FakeQuizStreamingSessionStore();
    String sessionId = sessionStore.createSession(new ArrayList<>(), false, null, null);
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager, sessionStore);

    viewModel.initialize("{}", 5, sessionId);
    sessionStore.failSession(sessionId, "session_failed");

    DialogueQuizViewModel.QuizUiState errorState = viewModel.getUiState().getValue();
    assertNotNull(errorState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.ERROR, errorState.getStatus());
    assertEquals("session_failed", errorState.getErrorMessage());

    viewModel.retryLoad();
    assertTrue(sessionStore.wasReleased(sessionId));
    assertEquals(1, manager.requestCount);

    manager.emitQuestion(0, buildChoiceQuestion("R_Q1", "R_A1"));
    DialogueQuizViewModel.QuizUiState readyState = viewModel.getUiState().getValue();
    assertNotNull(readyState);
    assertEquals(DialogueQuizViewModel.QuizUiState.Status.READY, readyState.getStatus());
    assertEquals("R_Q1", readyState.getQuestionState().getQuestionMain());
  }

  @Test
  public void questionMaterial_isPreservedWhenPresent_andNullWhenMissing() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}");
    manager.emitQuestion(0, buildChoiceQuestionWithMaterial("Q1", "Material-1", "A1"));

    DialogueQuizViewModel.QuizUiState firstState = viewModel.getUiState().getValue();
    assertNotNull(firstState);
    assertEquals("Q1", firstState.getQuestionState().getQuestionMain());
    assertEquals("Material-1", firstState.getQuestionState().getQuestionMaterial());

    viewModel.onChoiceSelected("A1");
    assertEquals(DialogueQuizViewModel.PrimaryActionResult.CHECKED, viewModel.onPrimaryAction());
    assertEquals(
        DialogueQuizViewModel.PrimaryActionResult.WAITING_NEXT_QUESTION,
        viewModel.onPrimaryAction());

    manager.emitQuestion(0, buildChoiceQuestion("Q2", "A2"));
    assertEquals(
        DialogueQuizViewModel.PrimaryActionResult.MOVED_TO_NEXT, viewModel.onPrimaryAction());

    DialogueQuizViewModel.QuizUiState secondState = viewModel.getUiState().getValue();
    assertNotNull(secondState);
    assertEquals("Q2", secondState.getQuestionState().getQuestionMain());
    assertNull(secondState.getQuestionState().getQuestionMaterial());
  }

  @Test
  public void choices_areShuffled_soAnswerIsNotAlwaysFirst() {
    FakeQuizGenerationManager manager = new FakeQuizGenerationManager();
    DialogueQuizViewModel viewModel = new DialogueQuizViewModel(manager);

    viewModel.initialize("{}", 10);
    for (int i = 1; i <= 10; i++) {
      String answer = "A" + i;
      manager.emitQuestion(0, buildFourChoiceQuestion("Q" + i, answer));
    }
    manager.completeRequest(0, null);

    boolean foundQuestionWithAnswerNotFirst = false;
    for (int i = 1; i <= 10; i++) {
      String answer = "A" + i;
      DialogueQuizViewModel.QuizUiState state = viewModel.getUiState().getValue();
      assertNotNull(state);
      assertEquals(DialogueQuizViewModel.QuizUiState.Status.READY, state.getStatus());
      assertEquals("Q" + i, state.getQuestionState().getQuestionMain());
      assertTrue(state.getQuestionState().getChoices().contains(answer));

      if (!answer.equals(state.getQuestionState().getChoices().get(0))) {
        foundQuestionWithAnswerNotFirst = true;
      }

      viewModel.onChoiceSelected(answer);
      assertEquals(DialogueQuizViewModel.PrimaryActionResult.CHECKED, viewModel.onPrimaryAction());
      if (i < 10) {
        assertEquals(
            DialogueQuizViewModel.PrimaryActionResult.MOVED_TO_NEXT, viewModel.onPrimaryAction());
      } else {
        assertEquals(
            DialogueQuizViewModel.PrimaryActionResult.COMPLETED, viewModel.onPrimaryAction());
      }
    }

    assertTrue(foundQuestionWithAnswerNotFirst);
  }

  @NonNull
  private static QuizData.QuizQuestion buildChoiceQuestion(
      @NonNull String question, @NonNull String answer) {
    return new QuizData.QuizQuestion(
        question, null, answer, Arrays.asList(answer, "Wrong-A", "Wrong-B"), null);
  }

  @NonNull
  private static QuizData.QuizQuestion buildChoiceQuestionWithMaterial(
      @NonNull String questionMain, @NonNull String questionMaterial, @NonNull String answer) {
    return new QuizData.QuizQuestion(
        questionMain, questionMaterial, answer, Arrays.asList(answer, "Wrong-A", "Wrong-B"), null);
  }

  @NonNull
  private static QuizData.QuizQuestion buildFourChoiceQuestion(
      @NonNull String questionMain, @NonNull String answer) {
    return new QuizData.QuizQuestion(
        questionMain, null, answer, Arrays.asList(answer, "Wrong-A", "Wrong-B", "Wrong-C"), null);
  }

  private static class FakeQuizGenerationManager implements IQuizGenerationManager {
    @NonNull
    private final List<IQuizGenerationManager.QuizStreamingCallback> streamingCallbacks =
        new ArrayList<>();

    @Nullable SummaryData lastSummaryData;
    int initCacheCount = 0;
    int requestCount = 0;

    @Override
    public void initializeCache(@NonNull InitCallback callback) {
      initCacheCount++;
      callback.onReady();
    }

    @Override
    public void generateQuizFromSummaryAsync(
        @NonNull SummaryData summaryData,
        int requestedQuestionCount,
        @NonNull QuizCallback callback) {
      callback.onFailure("one-shot path is not used in this test");
    }

    @Override
    public void generateQuizFromSummaryStreamingAsync(
        @NonNull SummaryData summaryData,
        int requestedQuestionCount,
        @NonNull QuizStreamingCallback callback) {
      requestCount++;
      lastSummaryData = summaryData;
      streamingCallbacks.add(callback);
    }

    void emitQuestion(int requestIndex, @NonNull QuizData.QuizQuestion question) {
      streamingCallbacks.get(requestIndex).onQuestion(question);
    }

    void completeRequest(int requestIndex, @Nullable String warning) {
      streamingCallbacks.get(requestIndex).onComplete(warning);
    }

    void failRequest(int requestIndex, @NonNull String error) {
      streamingCallbacks.get(requestIndex).onFailure(error);
    }
  }

  private static class FakeQuizStreamingSessionStore implements QuizStreamingSessionStore {
    private static class SessionState {
      @NonNull final List<QuizData.QuizQuestion> bufferedQuestions = new ArrayList<>();
      @NonNull final List<Listener> listeners = new ArrayList<>();
      boolean completed = false;
      @Nullable String warningMessage;
      @Nullable String failureMessage;
      boolean released = false;
    }

    @NonNull private final Map<String, SessionState> sessions = new HashMap<>();
    @NonNull private final List<String> releasedSessionIds = new ArrayList<>();
    private int nextId = 0;

    @Override
    @NonNull
    public String startSession(
        @NonNull IQuizGenerationManager manager,
        @NonNull SummaryData seed,
        int requestedQuestionCount) {
      String sessionId = "started_" + (++nextId);
      sessions.put(sessionId, new SessionState());
      manager.generateQuizFromSummaryStreamingAsync(
          seed,
          requestedQuestionCount,
          new IQuizGenerationManager.QuizStreamingCallback() {
            @Override
            public void onQuestion(@NonNull QuizData.QuizQuestion question) {
              emitQuestion(sessionId, question);
            }

            @Override
            public void onComplete(@Nullable String warningMessage) {
              completeSession(sessionId, warningMessage);
            }

            @Override
            public void onFailure(@NonNull String error) {
              failSession(sessionId, error);
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
      SessionState state = sessions.get(sessionId);
      if (state == null || state.released) {
        return null;
      }
      state.listeners.add(listener);
      return new Snapshot(
          state.bufferedQuestions, state.completed, state.warningMessage, state.failureMessage);
    }

    @Override
    public void detach(@Nullable String sessionId, @NonNull Listener listener) {
      if (sessionId == null) {
        return;
      }
      SessionState state = sessions.get(sessionId);
      if (state == null) {
        return;
      }
      state.listeners.remove(listener);
    }

    @Override
    public void release(@Nullable String sessionId) {
      if (sessionId == null) {
        return;
      }
      SessionState state = sessions.remove(sessionId);
      if (state == null) {
        return;
      }
      state.released = true;
      releasedSessionIds.add(sessionId);
    }

    @NonNull
    String createSession(
        @NonNull List<QuizData.QuizQuestion> bufferedQuestions,
        boolean completed,
        @Nullable String warningMessage,
        @Nullable String failureMessage) {
      String sessionId = "session_" + (++nextId);
      SessionState state = new SessionState();
      state.bufferedQuestions.addAll(bufferedQuestions);
      state.completed = completed;
      state.warningMessage = warningMessage;
      state.failureMessage = failureMessage;
      sessions.put(sessionId, state);
      return sessionId;
    }

    void emitQuestion(@NonNull String sessionId, @NonNull QuizData.QuizQuestion question) {
      SessionState state = sessions.get(sessionId);
      if (state == null || state.released || state.completed || state.failureMessage != null) {
        return;
      }
      state.bufferedQuestions.add(question);
      List<Listener> listeners = new ArrayList<>(state.listeners);
      for (Listener listener : listeners) {
        listener.onQuestion(question);
      }
    }

    void completeSession(@NonNull String sessionId, @Nullable String warningMessage) {
      SessionState state = sessions.get(sessionId);
      if (state == null || state.released || state.failureMessage != null) {
        return;
      }
      state.completed = true;
      state.warningMessage = warningMessage;
      List<Listener> listeners = new ArrayList<>(state.listeners);
      for (Listener listener : listeners) {
        listener.onComplete(warningMessage);
      }
    }

    void failSession(@NonNull String sessionId, @NonNull String errorMessage) {
      SessionState state = sessions.get(sessionId);
      if (state == null || state.released || state.completed) {
        return;
      }
      state.failureMessage = errorMessage;
      List<Listener> listeners = new ArrayList<>(state.listeners);
      for (Listener listener : listeners) {
        listener.onFailure(errorMessage);
      }
    }

    boolean wasReleased(@NonNull String sessionId) {
      return releasedSessionIds.contains(sessionId);
    }
  }
}
