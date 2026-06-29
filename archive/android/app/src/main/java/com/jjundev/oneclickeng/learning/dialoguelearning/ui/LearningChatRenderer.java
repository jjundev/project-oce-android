package com.jjundev.oneclickeng.learning.dialoguelearning.ui;

import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator.LearningSessionSnapshot;
import com.jjundev.oneclickeng.view.WaveformView;

public class LearningChatRenderer {

  private final RecyclerView recyclerView;
  private final WaveformView waveformView;

  public LearningChatRenderer(
      @Nullable RecyclerView recyclerView, @Nullable WaveformView waveformView) {
    this.recyclerView = recyclerView;
    this.waveformView = waveformView;
  }

  public void resetWaveform() {
    if (waveformView != null) {
      waveformView.reset();
    }
  }

  public void scrollToBottom() {
    if (recyclerView == null) {
      return;
    }
    recyclerView.post(
        () -> {
          int count =
              recyclerView.getAdapter() == null ? 0 : recyclerView.getAdapter().getItemCount();
          if (count > 0) {
            recyclerView.scrollToPosition(count - 1);
          }
        });
  }

  public boolean isAtBottom() {
    if (recyclerView == null) {
      return false;
    }
    int total = recyclerView.getAdapter() == null ? 0 : recyclerView.getAdapter().getItemCount();
    if (total == 0) {
      return true;
    }

    androidx.recyclerview.widget.LinearLayoutManager layoutManager =
        (androidx.recyclerview.widget.LinearLayoutManager) recyclerView.getLayoutManager();
    if (layoutManager == null) {
      return true;
    }

    int lastVisibleItem = layoutManager.findLastVisibleItemPosition();
    return lastVisibleItem >= total - 1;
  }

  public void setFooterHeight(int height) {
    if (height < 0) {
      return;
    }
    if (recyclerView == null || recyclerView.getAdapter() == null) {
      return;
    }

    int index = -1;
    if (recyclerView.getAdapter() instanceof ChatAdapterHeightAdjuster) {
      ((ChatAdapterHeightAdjuster) recyclerView.getAdapter()).setFooterHeight(height);
    }
  }

  public void clearChatState() {
    resetWaveform();
    scrollToBottom();
  }

  public void syncFooterWithBottomSheet(@Nullable View bottomSheet) {
    if (recyclerView == null || bottomSheet == null) {
      return;
    }

    if (bottomSheet.getParent() instanceof View) {
      int parentHeight = ((View) bottomSheet.getParent()).getHeight();
      int visibleHeight = Math.max(parentHeight - bottomSheet.getTop(), 0);
      setFooterHeight(visibleHeight);
    }
  }

  public interface ChatAdapterHeightAdjuster {
    void setFooterHeight(int height);
  }

  public void bindAdapterIfPossible(@Nullable Object adapter) {
    if (adapter == null) {
      return;
    }
    if (adapter instanceof ChatAdapterHeightAdjuster) {
      ViewGroup.MarginLayoutParams params = null;
      if (recyclerView != null
          && recyclerView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
        params = (ViewGroup.MarginLayoutParams) recyclerView.getLayoutParams();
        params.bottomMargin = 0;
      }
      if (params != null) {
        params.bottomMargin = 0;
        recyclerView.requestLayout();
      }
    }
  }

  public static void setWaveformAmplitude(@NonNull WaveformView waveformView, float amplitude) {
    waveformView.addAmplitude(amplitude);
  }

  public void bindFromSnapshot(@Nullable LearningSessionSnapshot snapshot) {
    if (snapshot == null) {
      return;
    }
    // Chat rendering is event-driven for now; keep this entry as stable snapshot hook.
  }
}
