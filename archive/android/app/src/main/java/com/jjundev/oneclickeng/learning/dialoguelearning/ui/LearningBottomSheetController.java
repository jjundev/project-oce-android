package com.jjundev.oneclickeng.learning.dialoguelearning.ui;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.BottomSheetMode;

public class LearningBottomSheetController {

  private static final String TAG = "LearningBottomSheet";
  private static final float BOTTOM_SHEET_MAX_HEIGHT_RATIO = 0.8f;
  private static final float BOTTOM_SHEET_MIN_HEIGHT_RATIO = 0.2f;

  @NonNull private final LearningBottomSheetRenderer bottomSheetRenderer;
  @NonNull private final LearningChatRenderer chatRenderer;
  @Nullable private final LearningControlsRenderer controlsRenderer;

  @Nullable private BottomSheetBehavior<View> bottomSheetBehavior;
  @Nullable private Runnable onRequestChatScroll;
  @Nullable private Runnable onSlideCollapsedAction;
  @Nullable private View bottomSheet;
  @Nullable private View pendingFooterSyncSheet;
  @Nullable private View.OnLayoutChangeListener pendingFooterLayoutListener;

  private boolean isAutoScrollingToBottom;
  private int footerSyncGeneration;

  private final BottomSheetBehavior.BottomSheetCallback sheetCallback =
      new BottomSheetBehavior.BottomSheetCallback() {
        @Override
        public void onStateChanged(@NonNull View bottomSheet, int newState) {
          LearningBottomSheetController.this.onSlideStateChanged(newState);
        }

        @Override
        public void onSlide(@NonNull View bottomSheet, float slideOffset) {
          int visibleHeight = calculateVisibleHeight(bottomSheet);
          LearningBottomSheetController.this.onBottomSheetSlide(
              bottomSheet, slideOffset, visibleHeight, isAutoScrollingToBottom);
        }
      };

  public LearningBottomSheetController(
      @NonNull LearningBottomSheetRenderer bottomSheetRenderer,
      @NonNull LearningChatRenderer chatRenderer,
      @Nullable LearningControlsRenderer controlsRenderer) {
    this.bottomSheetRenderer = bottomSheetRenderer;
    this.chatRenderer = chatRenderer;
    this.controlsRenderer = controlsRenderer;
  }

  public void setup(
      @NonNull View root,
      @NonNull View bottomSheet,
      @NonNull View contentContainer,
      @NonNull Runnable onRequestChatScroll) {
    this.bottomSheet = bottomSheet;
    this.onRequestChatScroll = onRequestChatScroll;
    bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);

    bottomSheetBehavior.setFitToContents(true);
    bottomSheetBehavior.setHideable(false);

    root.getViewTreeObserver()
        .addOnGlobalLayoutListener(
            new ViewTreeObserver.OnGlobalLayoutListener() {
              @Override
              public void onGlobalLayout() {
                root.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                int fragmentHeight = root.getHeight();
                int maxHeight = (int) (fragmentHeight * BOTTOM_SHEET_MAX_HEIGHT_RATIO);
                int minHeight = (int) (fragmentHeight * BOTTOM_SHEET_MIN_HEIGHT_RATIO);

                ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                bottomSheet.setLayoutParams(params);
                logTrace(
                    "TRACE_BS_SETUP_HEIGHT wrap_content=true maxHeight="
                        + maxHeight
                        + " minHeight="
                        + minHeight);

                bottomSheetBehavior.setPeekHeight(minHeight);
                bottomSheetBehavior.setMaxHeight(maxHeight);
                chatRenderer.setFooterHeight(minHeight);
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

                setVisible(false);
              }
            });

    bottomSheetBehavior.addBottomSheetCallback(sheetCallback);
  }

  public void setVisible(boolean visible) {
    bottomSheetRenderer.showSheet(visible);
    if (visible) {
      scheduleFooterSync(true, "setVisible");
      return;
    }
    footerSyncGeneration++;
    clearPendingFooterLayoutListener();
    isAutoScrollingToBottom = false;
    chatRenderer.setFooterHeight(0);
  }

  public View replaceOrReuseContent(
      @LayoutRes int layoutResId,
      @NonNull BottomSheetSceneRenderer renderer,
      @NonNull Runnable renderBinder) {
    View content = bottomSheetRenderer.getCurrentContent();
    if (content == null
        || !(content.getTag() instanceof Integer)
        || ((Integer) content.getTag()) != layoutResId) {
      if (bottomSheet == null) {
        return null;
      }
      android.view.LayoutInflater inflater =
          android.view.LayoutInflater.from(bottomSheet.getContext());
      if (inflater != null) {
        content = bottomSheetRenderer.replaceContent(layoutResId, inflater);
      }
      if (content != null) {
        content.setTag(layoutResId);
      }
    }

    if (content == null) {
      return null;
    }

    if (controlsRenderer != null) {
      controlsRenderer.syncBottomSheetContent(content, isAutoScrollingToBottom);
    }

    renderBinder.run();
    return content;
  }

  public View getCurrentContent() {
    return bottomSheetRenderer.getCurrentContent();
  }

  public void clearContent() {
    bottomSheetRenderer.clearContent();
    View sheet = bottomSheet;
    if (sheet == null || sheet.getVisibility() != View.VISIBLE) {
      footerSyncGeneration++;
      clearPendingFooterLayoutListener();
      isAutoScrollingToBottom = false;
      chatRenderer.setFooterHeight(0);
      return;
    }
    scheduleFooterSync(false, "clearContent");
  }

  public void changeContent(@NonNull Runnable action) {
    changeContent(action, false);
  }

  public void changeContent(@NonNull Runnable action, boolean skipStateAnimation) {
    if (bottomSheetBehavior == null) {
      action.run();
      normalizeSheetHeightToWrapContent("changeContent_no_behavior");
      scheduleFooterSync(true, "changeContent_no_behavior");
      return;
    }

    if (skipStateAnimation) {
      action.run();
      normalizeSheetHeightToWrapContent("changeContent_no_anim");
      scheduleFooterSync(true, "changeContent_no_anim");
      return;
    }

    if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
      action.run();
      normalizeSheetHeightToWrapContent("changeContent_collapsed_immediate");
      bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    } else {
      onSlideCollapsedAction =
          () -> {
            action.run();
            normalizeSheetHeightToWrapContent("changeContent_collapsed_callback");
          };
      bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
    }
  }

  public void onDestroy() {
    footerSyncGeneration++;
    clearPendingFooterLayoutListener();
    if (bottomSheetBehavior != null) {
      bottomSheetBehavior.removeBottomSheetCallback(sheetCallback);
      bottomSheetBehavior = null;
    }
    pendingFooterSyncSheet = null;
    onRequestChatScroll = null;
    onSlideCollapsedAction = null;
  }

  public void onSlideStateChanged(int newState) {
    if (bottomSheetBehavior == null) {
      return;
    }

    if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
      if (onSlideCollapsedAction != null) {
        onSlideCollapsedAction.run();
        onSlideCollapsedAction = null;
      }
      if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_COLLAPSED) {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
      }
      return;
    }

    if (newState == BottomSheetBehavior.STATE_EXPANDED) {
      if (isAutoScrollingToBottom && onRequestChatScroll != null) {
        onRequestChatScroll.run();
      }
      scheduleFooterSync(true, "state_expanded");
    }
  }

  public void onBottomSheetSlide(
      @NonNull View bottomSheet, float slideOffset, int visibleHeight, boolean canAutoScroll) {
    isAutoScrollingToBottom = canAutoScroll;
    chatRenderer.setFooterHeight(visibleHeight);

    if (canAutoScroll || chatRenderer.isAtBottom()) {
      chatRenderer.scrollToBottom();
    }

    if (controlsRenderer != null) {
      controlsRenderer.syncBottomSheetContent(getCurrentContent(), canAutoScroll);
    }
  }

  public void setAutoScrollRequested(boolean autoScrollRequested) {
    isAutoScrollingToBottom = autoScrollRequested;
  }

  public void bindFromSnapshot(@Nullable BottomSheetMode mode) {
    bottomSheetRenderer.bindFromSnapshot(mode);
    if (mode != null) {
      normalizeSheetHeightToWrapContent("bind_snapshot");
      scheduleFooterSync(true, "bind_snapshot");
    }
  }

  private void normalizeSheetHeightToWrapContent(@NonNull String reason) {
    View sheet = bottomSheet;
    if (sheet == null) {
      return;
    }
    ViewGroup.LayoutParams params = sheet.getLayoutParams();
    if (params == null) {
      return;
    }

    params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
    sheet.setLayoutParams(params);
    sheet.requestLayout();
    logTrace("TRACE_BS_HEIGHT_NORMALIZE reason=" + reason);
  }

  private void logTrace(@NonNull String message) {
    Log.d(TAG, message);
  }

  private int calculateVisibleHeight(@NonNull View bottomSheet) {
    int visibleHeight = 0;
    if (bottomSheet.getVisibility() != View.VISIBLE) {
      return visibleHeight;
    }

    if (bottomSheet.getParent() instanceof View) {
      int parentHeight = ((View) bottomSheet.getParent()).getHeight();
      visibleHeight = Math.max(parentHeight - bottomSheet.getTop(), 0);
    }

    return visibleHeight;
  }

  private void scheduleFooterSync(boolean allowConditionalScroll, @NonNull String reason) {
    View sheet = bottomSheet;
    footerSyncGeneration++;
    int generationToken = footerSyncGeneration;
    clearPendingFooterLayoutListener();

    if (sheet == null || sheet.getVisibility() != View.VISIBLE) {
      chatRenderer.setFooterHeight(0);
      isAutoScrollingToBottom = false;
      return;
    }

    sheet.post(
        () -> {
          if (generationToken != footerSyncGeneration) {
            return;
          }

          if (sheet.getVisibility() != View.VISIBLE) {
            chatRenderer.setFooterHeight(0);
            isAutoScrollingToBottom = false;
            return;
          }

          int visibleHeight = calculateVisibleHeight(sheet);
          if (visibleHeight <= 0) {
            attachOneShotFooterLayoutListener(
                sheet, allowConditionalScroll, reason, generationToken);
            return;
          }

          applyFooterSyncResult(visibleHeight, allowConditionalScroll);
        });
  }

  private void applyFooterSyncResult(int visibleHeight, boolean allowConditionalScroll) {
    chatRenderer.setFooterHeight(visibleHeight);

    boolean shouldScroll =
        allowConditionalScroll && (isAutoScrollingToBottom || chatRenderer.isAtBottom());
    if (shouldScroll) {
      chatRenderer.scrollToBottom();
    }
    if (allowConditionalScroll) {
      isAutoScrollingToBottom = false;
    }
  }

  private void attachOneShotFooterLayoutListener(
      @NonNull View sheet,
      boolean allowConditionalScroll,
      @NonNull String reason,
      int generationToken) {
    clearPendingFooterLayoutListener();
    pendingFooterSyncSheet = sheet;
    pendingFooterLayoutListener =
        new View.OnLayoutChangeListener() {
          @Override
          public void onLayoutChange(
              View v,
              int left,
              int top,
              int right,
              int bottom,
              int oldLeft,
              int oldTop,
              int oldRight,
              int oldBottom) {
            v.removeOnLayoutChangeListener(this);
            pendingFooterLayoutListener = null;
            pendingFooterSyncSheet = null;
            if (generationToken != footerSyncGeneration) {
              return;
            }
            scheduleFooterSync(allowConditionalScroll, reason + "_layout");
          }
        };
    sheet.addOnLayoutChangeListener(pendingFooterLayoutListener);
  }

  private void clearPendingFooterLayoutListener() {
    if (pendingFooterSyncSheet != null && pendingFooterLayoutListener != null) {
      pendingFooterSyncSheet.removeOnLayoutChangeListener(pendingFooterLayoutListener);
    }
    pendingFooterSyncSheet = null;
    pendingFooterLayoutListener = null;
  }
}
