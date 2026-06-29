package com.jjundev.oneclickeng.fragment.shorts;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.jjundev.oneclickeng.others.EnglishShortsItem;
import java.util.List;

/**
 * ViewModel for English Shorts. Delegates to {@link EnglishShortsRepository} and exposes LiveData
 * for loading, error, and data states.
 */
public class EnglishShortsViewModel extends ViewModel {

  @NonNull private final EnglishShortsRepository repository;

  public EnglishShortsViewModel() {
    this.repository = new EnglishShortsRepository();
    repository.startListening();
  }

  @NonNull
  public LiveData<List<EnglishShortsItem>> getShortsItems() {
    return repository.getShortsItems();
  }

  @NonNull
  public LiveData<Boolean> getIsLoading() {
    return repository.getIsLoading();
  }

  @NonNull
  public LiveData<String> getErrorMessage() {
    return repository.getErrorMessage();
  }

  /** Manually retry fetching data. */
  public void retry() {
    repository.stopListening();
    repository.startListening();
  }

  @Override
  protected void onCleared() {
    super.onCleared();
    repository.stopListening();
  }
}
