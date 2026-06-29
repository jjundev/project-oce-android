package com.jjundev.oneclickeng.learning.dialoguelearning.ui;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import com.google.android.material.button.MaterialButton;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator.LearningSessionSnapshot;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.FeedbackUiState;
import com.jjundev.oneclickeng.view.WaveformView;

public class LearningControlsRenderer {

  @Nullable private final Handler mainHandler;

  public LearningControlsRenderer() {
    this.mainHandler = new Handler(Looper.getMainLooper());
  }

  public void hideKeyboardIfRequested(@NonNull Runnable hideAction) {
    if (hideAction != null) {
      hideAction.run();
    }
  }

  public void resetWaveform(@Nullable WaveformView waveformView) {
    if (waveformView != null) {
      waveformView.reset();
    }
  }

  public void updateSendButtonState(@Nullable ImageButton btnSend, @Nullable String text) {
    if (btnSend == null) {
      return;
    }

    String safeText = text == null ? "" : text;
    if (safeText.trim().isEmpty()) {
      btnSend.setEnabled(false);
      btnSend.setBackgroundResource(
          com.jjundev.oneclickeng.R.drawable.send_button_background_disabled);
    } else {
      btnSend.setEnabled(true);
      btnSend.setBackgroundResource(com.jjundev.oneclickeng.R.drawable.send_button_background);
    }
  }

  public void runOnMain(@NonNull Runnable action) {
    if (mainHandler == null) {
      return;
    }
    mainHandler.post(action);
  }

  public void stopHandlerCallbacks() {
    if (mainHandler != null) {
      mainHandler.removeCallbacksAndMessages(null);
    }
  }

  public void syncBottomSheetContent(@Nullable View content, boolean isAutoScrolling) {
    if (content == null || !isAutoScrolling) {
      return;
    }
    if (content instanceof NestedScrollView) {
      ((NestedScrollView) content)
          .post(() -> ((NestedScrollView) content).fullScroll(View.FOCUS_DOWN));
    }
  }

  public void bindFeedbackControls(@Nullable View content, boolean showNextButton) {
    if (content == null) {
      return;
    }
    MaterialButton btnNext = content.findViewById(R.id.btn_next);
    if (btnNext != null) {
      btnNext.setVisibility(showNextButton ? View.VISIBLE : View.GONE);
    }
  }

  public void bindFromSnapshot(@Nullable View content, @Nullable LearningSessionSnapshot snapshot) {
    if (snapshot == null) {
      return;
    }
    FeedbackUiState feedbackUiState = snapshot.getFeedbackUiState();
    bindFeedbackControls(content, feedbackUiState != null && feedbackUiState.isShowNextButton());
  }
}
