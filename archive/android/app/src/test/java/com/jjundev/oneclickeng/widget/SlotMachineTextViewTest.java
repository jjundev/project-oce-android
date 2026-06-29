package com.jjundev.oneclickeng.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import androidx.dynamicanimation.animation.SpringAnimation;
import java.lang.reflect.Field;
import java.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class SlotMachineTextViewTest {

  @Test
  public void animateValue_startDelay_keepsStartValueUntilDelayEnds() {
    SlotMachineTextView view = new SlotMachineTextView(RuntimeEnvironment.getApplication());
    ShadowLooper mainLooper = ShadowLooper.shadowMainLooper();

    view.animateValue(0, 5, "", 500L, 200L);
    assertEquals("0", view.getText().toString());

    mainLooper.idleFor(Duration.ofMillis(180));
    assertEquals("0", view.getText().toString());

    mainLooper.idleFor(Duration.ofMillis(80));
    assertNotEquals("0", view.getText().toString());
  }

  @Test
  public void animateValue_completion_setsFinalTargetAndSuffix() {
    SlotMachineTextView view = new SlotMachineTextView(RuntimeEnvironment.getApplication());
    ShadowLooper mainLooper = ShadowLooper.shadowMainLooper();

    view.animateValue(0, 5, "XP", 900L, 0L);
    mainLooper.idleFor(Duration.ofSeconds(4));

    assertEquals("5XP", view.getText().toString());
  }

  @Test
  public void animateValue_zeroDuration_updatesImmediately() {
    SlotMachineTextView view = new SlotMachineTextView(RuntimeEnvironment.getApplication());

    view.animateValue(0, 7, "분", 0L, 0L);

    assertEquals("7분", view.getText().toString());
    assertEquals(1f, view.getScaleY(), 0f);
  }

  @Test
  public void cancelAnimation_stopsFurtherTextUpdates() {
    SlotMachineTextView view = new SlotMachineTextView(RuntimeEnvironment.getApplication());
    ShadowLooper mainLooper = ShadowLooper.shadowMainLooper();

    view.animateValue(0, 9, "", 900L, 0L);
    mainLooper.idleFor(Duration.ofMillis(220));
    String beforeCancel = view.getText().toString();

    view.cancelAnimation();
    mainLooper.idleFor(Duration.ofSeconds(3));

    assertEquals(beforeCancel, view.getText().toString());
    assertEquals(1f, view.getScaleY(), 0f);
  }

  @Test
  public void snapAnimation_returnsScaleYToOne() throws Exception {
    SlotMachineTextView view = new SlotMachineTextView(RuntimeEnvironment.getApplication());
    ShadowLooper mainLooper = ShadowLooper.shadowMainLooper();

    view.animateValue(0, 1, "", 120L, 0L);
    mainLooper.idleFor(Duration.ofSeconds(2));
    SpringAnimation springAnimation = getSnapScaleYAnimation(view);
    if (springAnimation != null) {
      springAnimation.skipToEnd();
    }

    assertEquals("1", view.getText().toString());
    assertEquals(1f, view.getScaleY(), 0.001f);
  }

  private static SpringAnimation getSnapScaleYAnimation(SlotMachineTextView textView)
      throws Exception {
    Field field = SlotMachineTextView.class.getDeclaredField("snapScaleYAnimation");
    field.setAccessible(true);
    return (SpringAnimation) field.get(textView);
  }
}
