package com.jjundev.oneclickeng.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public abstract class SwipeHelper extends ItemTouchHelper.SimpleCallback {

  int buttonWidth;
  private RecyclerView recyclerView;
  private List<UnderlayButton> buttons;
  private GestureDetector gestureDetector;
  private int swipedPos = -1;
  private float swipeThreshold = 0.5f;
  private Map<Integer, List<UnderlayButton>> buttonsBuffer;
  private Queue<Integer> recoverQueue;

  private GestureDetector.SimpleOnGestureListener gestureListener =
      new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
          for (UnderlayButton button : buttons) {
            if (button.onClick(e.getX(), e.getY())) break;
          }
          return true;
        }
      };

  @SuppressLint("ClickableViewAccessibility")
  private View.OnTouchListener onTouchListener =
      new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent e) {
          if (swipedPos < 0) return false;
          Point point = new Point((int) e.getRawX(), (int) e.getRawY());

          RecyclerView.ViewHolder swipedViewHolder =
              recyclerView.findViewHolderForAdapterPosition(swipedPos);
          View swipedItem = swipedViewHolder != null ? swipedViewHolder.itemView : null;
          Rect rect = new Rect();
          if (swipedItem != null) {
            swipedItem.getGlobalVisibleRect(rect);
            if (e.getAction() == MotionEvent.ACTION_DOWN
                || e.getAction() == MotionEvent.ACTION_UP
                || e.getAction() == MotionEvent.ACTION_MOVE) {
              if (rect.top < point.y && rect.bottom > point.y) {
                gestureDetector.onTouchEvent(e);
              } else {
                recoverQueue.add(swipedPos);
                swipedPos = -1;
                recoverSwipedItem();
              }
            }
          }
          return false;
        }
      };

  public SwipeHelper(Context context, RecyclerView recyclerView) {
    super(0, ItemTouchHelper.LEFT);
    this.recyclerView = recyclerView;
    this.buttons = new ArrayList<>();
    this.gestureDetector = new GestureDetector(context, gestureListener);
    this.recyclerView.setOnTouchListener(onTouchListener);
    buttonsBuffer = new HashMap<>();
    recoverQueue = new LinkedList<Integer>();

    attachSwipe();

    // DP to PX for button width
    buttonWidth = (int) (80 * context.getResources().getDisplayMetrics().density);
  }

  private void attachSwipe() {
    ItemTouchHelper itemTouchHelper = new ItemTouchHelper(this);
    itemTouchHelper.attachToRecyclerView(recyclerView);
  }

  private synchronized void recoverSwipedItem() {
    while (!recoverQueue.isEmpty()) {
      int pos = recoverQueue.poll();
      if (pos > -1) {
        recyclerView.getAdapter().notifyItemChanged(pos);
      }
    }
  }

  private void drawButtons(
      Canvas c, View itemView, List<UnderlayButton> buffer, int pos, float dX) {
    float right = itemView.getRight();
    float dButtonWidth = (-1) * dX / buffer.size();

    for (UnderlayButton button : buffer) {
      float left = right - dButtonWidth;
      button.onDraw(c, new RectF(left, itemView.getTop(), right, itemView.getBottom()), pos);
      right = left;
    }
  }

  @Override
  public boolean onMove(
      @NonNull RecyclerView recyclerView,
      @NonNull RecyclerView.ViewHolder viewHolder,
      @NonNull RecyclerView.ViewHolder target) {
    return false;
  }

  @Override
  public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
    int pos = viewHolder.getAdapterPosition();

    if (swipedPos != pos) recoverQueue.add(swipedPos);

    swipedPos = pos;

    if (buttonsBuffer.containsKey(swipedPos)) buttons = buttonsBuffer.get(swipedPos);
    else buttons.clear();

    buttonsBuffer.clear();
    swipeThreshold = 0.5f * buttons.size() * buttonWidth;
    recoverSwipedItem();
  }

  @Override
  public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
    return swipeThreshold;
  }

  @Override
  public float getSwipeEscapeVelocity(float defaultValue) {
    return 0.1f * defaultValue;
  }

  @Override
  public float getSwipeVelocityThreshold(float defaultValue) {
    return 5.0f * defaultValue;
  }

  @Override
  public void onChildDraw(
      @NonNull Canvas c,
      @NonNull RecyclerView recyclerView,
      @NonNull RecyclerView.ViewHolder viewHolder,
      float dX,
      float dY,
      int actionState,
      boolean isCurrentlyActive) {
    int pos = viewHolder.getAdapterPosition();
    float translationX = dX;
    View itemView = viewHolder.itemView;

    if (pos < 0) {
      swipedPos = pos;
      return;
    }

    if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
      if (dX < 0) { // 왼쪽으로 밀기
        List<UnderlayButton> buffer = new ArrayList<>();

        if (!buttonsBuffer.containsKey(pos)) {
          instantiateUnderlayButton(viewHolder, buffer);
          buttonsBuffer.put(pos, buffer);
        } else {
          buffer = buttonsBuffer.get(pos);
        }

        translationX = dX * buffer.size() * buttonWidth / itemView.getWidth();
        drawButtons(c, itemView, buffer, pos, translationX);
      }
    }

    super.onChildDraw(
        c, recyclerView, viewHolder, translationX, dY, actionState, isCurrentlyActive);
  }

  public abstract void instantiateUnderlayButton(
      RecyclerView.ViewHolder viewHolder, List<UnderlayButton> underlayButtons);

  public static class UnderlayButton {
    private String text;
    private int colorRes;
    private Drawable icon;
    private UnderlayButtonClickListener clickListener;
    private Context context;
    private int pos;
    private RectF clickRegion;

    public UnderlayButton(
        Context context,
        String text,
        Drawable icon,
        int colorRes,
        UnderlayButtonClickListener clickListener) {
      this.context = context;
      this.text = text;
      this.icon = icon;
      this.colorRes = colorRes;
      this.clickListener = clickListener;
    }

    public boolean onClick(float x, float y) {
      if (clickRegion != null && clickRegion.contains(x, y)) {
        clickListener.onClick(pos);
        return true;
      }
      return false;
    }

    public void onDraw(Canvas c, RectF rect, int pos) {
      Paint p = new Paint();

      // Draw background
      p.setColor(ContextCompat.getColor(context, colorRes));
      c.drawRect(rect, p);

      // Draw Icon
      if (icon != null) {
        icon.setTint(Color.WHITE);
        int iconMargin = (int) rect.height() / 2 - icon.getIntrinsicHeight() / 2;
        int iconTop = (int) rect.top + iconMargin;
        int iconBottom = iconTop + icon.getIntrinsicHeight();
        int iconLeft = (int) rect.centerX() - icon.getIntrinsicWidth() / 2;
        int iconRight = iconLeft + icon.getIntrinsicWidth();

        icon.setBounds(iconLeft, iconTop, iconRight, iconBottom);
        icon.draw(c);
      }

      // Draw Text if requested
      if (text != null && !text.isEmpty()) {
        p.setColor(Color.WHITE);
        p.setTextSize(context.getResources().getDisplayMetrics().density * 12);
        p.setTextAlign(Paint.Align.CENTER);
        Rect r = new Rect();
        p.getTextBounds(text, 0, text.length(), r);
        float x = rect.width() / 2f - r.width() / 2f - r.left;
        float y = rect.height() / 2f + r.height() / 2f - r.bottom;
        float yOffset = icon != null ? icon.getIntrinsicHeight() / 2 + 10 : 0;
        c.drawText(text, rect.left + x, rect.top + y + yOffset, p);
      }

      clickRegion = rect;
      this.pos = pos;
    }
  }

  public interface UnderlayButtonClickListener {
    void onClick(int pos);
  }
}
