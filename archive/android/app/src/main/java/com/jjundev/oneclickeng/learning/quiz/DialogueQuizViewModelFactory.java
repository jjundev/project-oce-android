package com.jjundev.oneclickeng.learning.quiz;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IQuizGenerationManager;
import com.jjundev.oneclickeng.learning.quiz.session.QuizStreamingSessionStore;

public class DialogueQuizViewModelFactory implements ViewModelProvider.Factory {

  private final IQuizGenerationManager quizGenerationManager;
  private final QuizStreamingSessionStore quizStreamingSessionStore;

  public DialogueQuizViewModelFactory(
      @NonNull IQuizGenerationManager quizGenerationManager,
      @NonNull QuizStreamingSessionStore quizStreamingSessionStore) {
    this.quizGenerationManager = quizGenerationManager;
    this.quizStreamingSessionStore = quizStreamingSessionStore;
  }

  @NonNull
  @Override
  @SuppressWarnings("unchecked")
  public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
    if (!modelClass.isAssignableFrom(DialogueQuizViewModel.class)) {
      throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
    return (T) new DialogueQuizViewModel(quizGenerationManager, quizStreamingSessionStore);
  }
}
