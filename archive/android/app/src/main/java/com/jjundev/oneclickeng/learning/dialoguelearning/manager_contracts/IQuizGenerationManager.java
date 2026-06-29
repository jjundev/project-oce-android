package com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.QuizData;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import java.util.List;

public interface IQuizGenerationManager {
  interface QuizCallback {
    void onSuccess(@NonNull List<QuizData.QuizQuestion> questions);

    void onFailure(@NonNull String error);
  }

  interface InitCallback {
    void onReady();

    void onError(@NonNull String error);
  }

  interface QuizStreamingCallback {
    void onQuestion(@NonNull QuizData.QuizQuestion question);

    void onComplete(@Nullable String warningMessage);

    void onFailure(@NonNull String error);
  }

  void initializeCache(@NonNull InitCallback callback);

  void generateQuizFromSummaryAsync(
      @NonNull SummaryData summaryData, int requestedQuestionCount, @NonNull QuizCallback callback);

  default void generateQuizFromSummaryAsync(
      @NonNull SummaryData summaryData, @NonNull QuizCallback callback) {
    generateQuizFromSummaryAsync(summaryData, 5, callback);
  }

  default void generateQuizFromSummaryStreamingAsync(
      @NonNull SummaryData summaryData,
      int requestedQuestionCount,
      @NonNull QuizStreamingCallback callback) {
    generateQuizFromSummaryAsync(
        summaryData,
        requestedQuestionCount,
        new QuizCallback() {
          @Override
          public void onSuccess(@NonNull List<QuizData.QuizQuestion> questions) {
            for (QuizData.QuizQuestion question : questions) {
              if (question != null) {
                callback.onQuestion(question);
              }
            }
            callback.onComplete(null);
          }

          @Override
          public void onFailure(@NonNull String error) {
            callback.onFailure(error);
          }
        });
  }
}
