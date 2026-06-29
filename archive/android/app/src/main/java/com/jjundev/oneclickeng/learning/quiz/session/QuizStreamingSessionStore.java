package com.jjundev.oneclickeng.learning.quiz.session;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IQuizGenerationManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.QuizData;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public interface QuizStreamingSessionStore {

  interface Listener {
    void onQuestion(@NonNull QuizData.QuizQuestion question);

    void onComplete(@Nullable String warningMessage);

    void onFailure(@NonNull String error);
  }

  @NonNull
  String startSession(
      @NonNull IQuizGenerationManager manager,
      @NonNull SummaryData seed,
      int requestedQuestionCount);

  @Nullable
  Snapshot attach(@Nullable String sessionId, @NonNull Listener listener);

  void detach(@Nullable String sessionId, @NonNull Listener listener);

  void release(@Nullable String sessionId);

  final class Snapshot {
    @NonNull private final List<QuizData.QuizQuestion> bufferedQuestions;
    private final boolean completed;
    @Nullable private final String warningMessage;
    @Nullable private final String failureMessage;

    public Snapshot(
        @NonNull List<QuizData.QuizQuestion> bufferedQuestions,
        boolean completed,
        @Nullable String warningMessage,
        @Nullable String failureMessage) {
      this.bufferedQuestions = Collections.unmodifiableList(new ArrayList<>(bufferedQuestions));
      this.completed = completed;
      this.warningMessage = warningMessage;
      this.failureMessage = failureMessage;
    }

    @NonNull
    public List<QuizData.QuizQuestion> getBufferedQuestions() {
      return bufferedQuestions;
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
