package com.jjundev.oneclickeng.learning.dialoguelearning.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator.LearningSessionSnapshot;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.BottomSheetMode;

public class LearningBottomSheetRenderer {

  @Nullable private final View bottomSheet;
  @Nullable private final ViewGroup contentContainer;

  public LearningBottomSheetRenderer(@Nullable View bottomSheet, @Nullable View contentContainer) {
    this.bottomSheet = bottomSheet;
    this.contentContainer =
        contentContainer instanceof ViewGroup ? (ViewGroup) contentContainer : null;
  }

  public void showSheet(boolean visible) {
    if (bottomSheet == null) {
      return;
    }
    bottomSheet.setVisibility(visible ? View.VISIBLE : View.GONE);
  }

  public void hideSheet() {
    showSheet(false);
  }

  public void clearContent() {
    if (contentContainer == null) {
      return;
    }
    contentContainer.removeAllViews();
  }

  @Nullable
  public View getCurrentContent() {
    if (contentContainer == null || contentContainer.getChildCount() <= 0) {
      return null;
    }
    return contentContainer.getChildAt(0);
  }

  public int getVisibleHeight() {
    if (bottomSheet == null || contentContainer == null) {
      return 0;
    }
    if (bottomSheet.getVisibility() != View.VISIBLE) {
      return 0;
    }

    if (bottomSheet.getParent() instanceof View) {
      int parentHeight = ((View) bottomSheet.getParent()).getHeight();
      return Math.max(parentHeight - bottomSheet.getTop(), 0);
    }
    return 0;
  }

  public View replaceContent(@LayoutRes int layoutResId, LayoutInflater inflater) {
    if (contentContainer == null || inflater == null) {
      return null;
    }
    contentContainer.removeAllViews();
    View content = inflater.inflate(layoutResId, contentContainer, false);
    contentContainer.addView(content);
    return content;
  }

  public View changeContent(
      @LayoutRes int layoutResId, LayoutInflater inflater, Runnable onAfterChange) {
    View content = replaceContent(layoutResId, inflater);
    if (onAfterChange != null) {
      onAfterChange.run();
    }
    return content;
  }

  public boolean hasContentContainer() {
    return contentContainer != null;
  }

  public boolean isVisible() {
    return bottomSheet != null && bottomSheet.getVisibility() == View.VISIBLE;
  }

  public void showOrHideSheet(boolean visible) {
    showSheet(visible);
  }

  public void bindFromSnapshot(@Nullable BottomSheetMode mode) {
    if (mode == null) {
      return;
    }
    showOrHideSheet(true);
  }

  public void bindFromSnapshot(@Nullable LearningSessionSnapshot snapshot) {
    if (snapshot == null) {
      return;
    }
    bindFromSnapshot(snapshot.getBottomSheetMode());
  }

  public void changeContent(Runnable action) {
    if (action == null) {
      return;
    }
    if (contentContainer != null) {
      contentContainer.removeAllViews();
    }
    action.run();
  }
}
