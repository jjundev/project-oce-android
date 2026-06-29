package com.jjundev.oneclickeng.learning.dialoguelearning.summary;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISessionSummaryLlmManager;

public class DialogueSummaryViewModelFactory implements ViewModelProvider.Factory {

  private final ISessionSummaryLlmManager sessionSummaryLlmManager;

  public DialogueSummaryViewModelFactory(
      @NonNull ISessionSummaryLlmManager sessionSummaryLlmManager) {
    this.sessionSummaryLlmManager = sessionSummaryLlmManager;
  }

  @NonNull
  @Override
  @SuppressWarnings("unchecked")
  public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
    if (!modelClass.isAssignableFrom(DialogueSummaryViewModel.class)) {
      throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
    return (T) new DialogueSummaryViewModel(sessionSummaryLlmManager);
  }
}
