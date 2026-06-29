package com.jjundev.oneclickeng.fragment.shorts;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.others.EnglishShortsItem;
import java.util.ArrayList;
import java.util.List;

/**
 * Repository that fetches English Shorts items from Firestore. Listens to the {@code
 * english_shorts} collection in real time using a snapshot listener.
 */
public class EnglishShortsRepository {
  private static final String TAG = "EnglishShortsRepository";
  private static final String COLLECTION = "english_shorts";

  @NonNull private final FirebaseFirestore firestore;

  @NonNull
  private final MutableLiveData<List<EnglishShortsItem>> shortsItems = new MutableLiveData<>();

  @NonNull private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(true);
  @NonNull private final MutableLiveData<String> errorMessage = new MutableLiveData<>(null);

  private ListenerRegistration listenerRegistration;

  public EnglishShortsRepository() {
    this.firestore = FirebaseFirestore.getInstance();
  }

  @NonNull
  public LiveData<List<EnglishShortsItem>> getShortsItems() {
    return shortsItems;
  }

  @NonNull
  public LiveData<Boolean> getIsLoading() {
    return isLoading;
  }

  @NonNull
  public LiveData<String> getErrorMessage() {
    return errorMessage;
  }

  /** Starts listening for real-time updates from Firestore. */
  public void startListening() {
    isLoading.setValue(true);
    errorMessage.setValue(null);

    listenerRegistration =
        firestore
            .collection(COLLECTION)
            .whereEqualTo("isActive", true)
            .addSnapshotListener(
                (value, error) -> {
                  if (error != null) {
                    logDebug("Firestore listen error: " + error.getMessage());
                    errorMessage.setValue("콘텐츠를 불러올 수 없어요.");
                    isLoading.setValue(false);
                    return;
                  }

                  if (value != null) {
                    List<EnglishShortsItem> items = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : value) {
                      EnglishShortsItem item = doc.toObject(EnglishShortsItem.class);
                      item.setDocumentId(doc.getId());
                      items.add(item);
                    }
                    shortsItems.setValue(items);
                    logDebug("Loaded " + items.size() + " shorts items from Firestore.");
                  }
                  isLoading.setValue(false);
                });
  }

  /** Stops listening for Firestore updates. */
  public void stopListening() {
    if (listenerRegistration != null) {
      listenerRegistration.remove();
      listenerRegistration = null;
    }
  }

  private void logDebug(@NonNull String message) {
    Log.d(TAG, message);
  }
}
