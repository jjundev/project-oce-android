package com.jjundev.oneclickeng.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.view.View;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class SlotMachineTextGroupViewTest {

  @Test
  public void animateText_hoursMinutes_splitsDigitsByPlace() {
    SlotMachineTextGroupView view =
        new SlotMachineTextGroupView(RuntimeEnvironment.getApplication());

    view.animateText("1시간 38분", 0L, 0L);

    assertEquals(Arrays.asList("1", "시", "간", " ", "3", "8", "분"), extractChildTexts(view));
    assertEquals("1시간 38분", view.getText().toString());
  }

  @Test
  public void animateText_minutesOnly_splitsOnlyDigits() {
    SlotMachineTextGroupView view =
        new SlotMachineTextGroupView(RuntimeEnvironment.getApplication());

    view.animateText("38분", 0L, 0L);

    assertEquals(Arrays.asList("3", "8", "분"), extractChildTexts(view));
    assertEquals("38분", view.getText().toString());
  }

  @Test
  public void animateText_streakWithEmoji_keepsEmojiIntact() {
    SlotMachineTextGroupView view =
        new SlotMachineTextGroupView(RuntimeEnvironment.getApplication());

    view.animateText("12일째 열공 중 🔥", 0L, 0L);

    assertEquals(
        Arrays.asList("1", "2", "일", "째", " ", "열", "공", " ", "중", " ", "🔥"),
        extractChildTexts(view));
    assertEquals("12일째 열공 중 🔥", view.getText().toString());
  }

  @Test
  public void animateText_points_splitsAllDigits() {
    SlotMachineTextGroupView view =
        new SlotMachineTextGroupView(RuntimeEnvironment.getApplication());

    view.animateText("150XP", 0L, 0L);

    assertEquals(Arrays.asList("1", "5", "0", "X", "P"), extractChildTexts(view));
    assertEquals("150XP", view.getText().toString());
  }

  @Test
  public void animateText_points_smallerDeltaDigit_stabilizesEarlier() throws Exception {
    SlotMachineTextGroupView view =
        new SlotMachineTextGroupView(RuntimeEnvironment.getApplication());
    ShadowLooper mainLooper = ShadowLooper.shadowMainLooper();
    view.setText("000XP");

    view.animateText("150XP", 900L, 0L);
    mainLooper.idleFor(Duration.ofMillis(350));

    SlotMachineTextView hundreds = (SlotMachineTextView) view.getChildAt(0);
    SlotMachineTextView tens = (SlotMachineTextView) view.getChildAt(1);
    SlotMachineTextView ones = (SlotMachineTextView) view.getChildAt(2);

    assertTrue(isSpinCompleted(hundreds));
    assertFalse(isSpinCompleted(tens));
    assertTrue(isSpinCompleted(ones));
    assertEquals("1", hundreds.getText().toString());
    assertEquals("0", ones.getText().toString());

    mainLooper.idleFor(Duration.ofSeconds(3));
    assertTrue(isSpinCompleted(tens));
    assertEquals("5", tens.getText().toString());
  }

  @Test
  public void animateText_zeroDuration_updatesImmediatelyWithoutScheduling() throws Exception {
    SlotMachineTextGroupView view =
        new SlotMachineTextGroupView(RuntimeEnvironment.getApplication());
    view.setText("000XP");

    view.animateText("150XP", 0L, 0L);

    for (int i = 0; i < 3; i++) {
      SlotMachineTextView digitView = (SlotMachineTextView) view.getChildAt(i);
      assertTrue(isSpinCompleted(digitView));
    }
    assertEquals("150XP", view.getText().toString());
  }

  @Test
  public void animateText_sameString_doesNotRebuildChildren() {
    SlotMachineTextGroupView view =
        new SlotMachineTextGroupView(RuntimeEnvironment.getApplication());

    view.animateText("38분", 0L, 0L);
    View firstBefore = view.getChildAt(0);
    int childCountBefore = view.getChildCount();

    view.animateText("38분", 0L, 0L);

    assertSame(firstBefore, view.getChildAt(0));
    assertEquals(childCountBefore, view.getChildCount());
  }

  @Test
  public void cancelAnimation_stopsFurtherUpdates() {
    SlotMachineTextGroupView view =
        new SlotMachineTextGroupView(RuntimeEnvironment.getApplication());
    ShadowLooper mainLooper = ShadowLooper.shadowMainLooper();
    view.setText("000XP");
    view.animateText("150XP", 900L, 0L);
    mainLooper.idleFor(Duration.ofMillis(240));

    List<String> beforeCancel = extractChildTexts(view);
    view.cancelAnimation();
    mainLooper.idleFor(Duration.ofSeconds(3));
    List<String> afterCancel = extractChildTexts(view);

    assertEquals(beforeCancel, afterCancel);
  }

  private static List<String> extractChildTexts(SlotMachineTextGroupView view) {
    List<String> texts = new ArrayList<>();
    for (int i = 0; i < view.getChildCount(); i++) {
      View child = view.getChildAt(i);
      if (child instanceof TextView) {
        texts.add(((TextView) child).getText().toString());
      }
    }
    return texts;
  }

  @SuppressWarnings("unchecked")
  private static boolean isSpinCompleted(SlotMachineTextView textView) throws Exception {
    Field intervalsField = SlotMachineTextView.class.getDeclaredField("spinIntervalsMs");
    intervalsField.setAccessible(true);
    List<Integer> intervals = (List<Integer>) intervalsField.get(textView);

    Field indexField = SlotMachineTextView.class.getDeclaredField("spinIntervalIndex");
    indexField.setAccessible(true);
    int index = (int) indexField.get(textView);
    return intervals == null || intervals.isEmpty() || index >= intervals.size();
  }
}
