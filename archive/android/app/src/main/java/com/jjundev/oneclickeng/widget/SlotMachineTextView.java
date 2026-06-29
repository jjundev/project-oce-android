package com.jjundev.oneclickeng.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 슬롯머신 스타일의 숫자 카운팅 애니메이션을 제공하는 커스텀 TextView. */
public class SlotMachineTextView extends AppCompatTextView {

  private static final long FAST_PHASE_BASE_DURATION_MS = 800L;
  private static final long TOTAL_BASE_DURATION_MS = 1260L; // 800 + (60 + 80 + 120 + 200)
  private static final int[] FAST_INTERVAL_PATTERN_MS = {40, 45, 50, 55, 60};
  private static final int[] DECELERATE_INTERVAL_PATTERN_MS = {60, 80, 120, 200};
  private static final float SNAP_START_SCALE_Y = 0.92f;

  @NonNull private final Handler mainHandler = new Handler(Looper.getMainLooper());
  @Nullable private Runnable startRunnable;
  @Nullable private Runnable spinRunnable;
  @Nullable private SpringAnimation snapScaleYAnimation;

  @NonNull private List<Integer> spinIntervalsMs = Collections.emptyList();
  private int spinIntervalIndex;
  private int spinDigit;
  private int spinDirection = 1;
  private int targetValue;
  @NonNull private String suffix = "";

  public SlotMachineTextView(Context context) {
    super(context);
  }

  public SlotMachineTextView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public SlotMachineTextView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  /**
   * 0에서 targetValue까지 슬롯머신 스타일로 숫자를 애니메이션합니다.
   *
   * @param targetValue 최종 표시할 숫자
   * @param suffix 숫자 뒤에 붙는 접미사 (예: "분", "XP", "일째 열공 중 🔥")
   * @param duration 애니메이션 지속 시간 (ms)
   * @param startDelay 애니메이션 시작 딜레이 (ms)
   */
  public void animateValue(int targetValue, String suffix, long duration, long startDelay) {
    animateValue(0, targetValue, suffix, duration, startDelay);
  }

  /**
   * startValue에서 targetValue까지 슬롯머신 스타일로 숫자를 애니메이션합니다.
   *
   * @param startValue 시작 표시 숫자
   * @param targetValue 최종 표시할 숫자
   * @param suffix 숫자 뒤에 붙는 접미사 (예: "분", "XP", "일째 열공 중 🔥")
   * @param duration 애니메이션 지속 시간 (ms)
   * @param startDelay 애니메이션 시작 딜레이 (ms)
   */
  public void animateValue(
      int startValue, int targetValue, String suffix, long duration, long startDelay) {
    cancelAnimation();

    int safeStart = Math.max(0, startValue);
    int safeTarget = Math.max(0, targetValue);
    String safeSuffix = suffix == null ? "" : suffix;

    this.targetValue = safeTarget;
    this.suffix = safeSuffix;
    this.spinDigit = Math.floorMod(safeStart, 10);
    this.spinDirection = safeTarget >= safeStart ? 1 : -1;
    setScaleY(1f);
    setText(safeStart + safeSuffix);

    if (safeStart == safeTarget || duration <= 0L) {
      setText(safeTarget + safeSuffix);
      return;
    }

    spinIntervalsMs = buildSpinIntervals(duration);
    if (spinIntervalsMs.isEmpty()) {
      setText(safeTarget + safeSuffix);
      startSnapAnimation();
      return;
    }

    Runnable start =
        () -> {
          startRunnable = null;
          spinIntervalIndex = 0;
          scheduleNextSpinTick(spinIntervalsMs.get(0));
        };
    startRunnable = start;
    long safeStartDelay = Math.max(0L, startDelay);
    if (safeStartDelay <= 0L) {
      mainHandler.post(start);
    } else {
      mainHandler.postDelayed(start, safeStartDelay);
    }
  }

  private void scheduleNextSpinTick(long delayMs) {
    Runnable runnable =
        () -> {
          spinRunnable = null;
          spinDigit = nextSpinDigit(spinDigit, spinDirection);
          setText(spinDigit + suffix);
          spinIntervalIndex++;

          if (spinIntervalIndex < spinIntervalsMs.size()) {
            scheduleNextSpinTick(spinIntervalsMs.get(spinIntervalIndex));
            return;
          }
          finishSpinAndSnap();
        };
    spinRunnable = runnable;
    mainHandler.postDelayed(runnable, Math.max(1L, delayMs));
  }

  private void finishSpinAndSnap() {
    spinIntervalIndex = spinIntervalsMs.size();
    setText(targetValue + suffix);
    startSnapAnimation();
  }

  private void startSnapAnimation() {
    if (snapScaleYAnimation != null) {
      snapScaleYAnimation.cancel();
      snapScaleYAnimation = null;
    }

    setScaleY(SNAP_START_SCALE_Y);
    SpringAnimation animation = new SpringAnimation(this, DynamicAnimation.SCALE_Y, 1f);
    SpringForce springForce = new SpringForce(1f);
    springForce.setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY);
    springForce.setStiffness(SpringForce.STIFFNESS_LOW);
    animation.setSpring(springForce);
    animation.addEndListener(
        (anim, canceled, value, velocity) -> {
          setScaleY(1f);
          if (snapScaleYAnimation == anim) {
            snapScaleYAnimation = null;
          }
        });
    snapScaleYAnimation = animation;
    animation.start();
  }

  private static int nextSpinDigit(int currentDigit, int direction) {
    int step = direction >= 0 ? 1 : -1;
    return Math.floorMod(currentDigit + step, 10);
  }

  @NonNull
  private static List<Integer> buildSpinIntervals(long durationMs) {
    long safeDuration = Math.max(1L, durationMs);
    long fastBudget =
        Math.round((safeDuration * FAST_PHASE_BASE_DURATION_MS) / (double) TOTAL_BASE_DURATION_MS);
    fastBudget = Math.max(0L, Math.min(safeDuration, fastBudget));
    long decelerateBudget = Math.max(0L, safeDuration - fastBudget);

    List<Integer> intervals = new ArrayList<>();
    intervals.addAll(buildFastIntervals(fastBudget));
    intervals.addAll(scalePatternIntervals(DECELERATE_INTERVAL_PATTERN_MS, decelerateBudget));
    if (intervals.isEmpty()) {
      intervals.add((int) safeDuration);
    }
    return intervals;
  }

  @NonNull
  private static List<Integer> buildFastIntervals(long budgetMs) {
    List<Integer> intervals = new ArrayList<>();
    if (budgetMs <= 0L) {
      return intervals;
    }

    double scale = budgetMs / (double) FAST_PHASE_BASE_DURATION_MS;
    int[] scaledPattern = new int[FAST_INTERVAL_PATTERN_MS.length];
    for (int i = 0; i < FAST_INTERVAL_PATTERN_MS.length; i++) {
      scaledPattern[i] = (int) Math.max(1L, Math.round(FAST_INTERVAL_PATTERN_MS[i] * scale));
    }

    long remaining = budgetMs;
    int index = 0;
    while (remaining > 0L) {
      int interval = scaledPattern[index % scaledPattern.length];
      if (interval > remaining) {
        interval = (int) remaining;
      }
      intervals.add(Math.max(1, interval));
      remaining -= interval;
      index++;
    }
    return intervals;
  }

  @NonNull
  private static List<Integer> scalePatternIntervals(@NonNull int[] basePattern, long budgetMs) {
    List<Integer> intervals = new ArrayList<>();
    if (budgetMs <= 0L || basePattern.length == 0) {
      return intervals;
    }

    long baseSum = 0L;
    for (int base : basePattern) {
      baseSum += Math.max(0, base);
    }
    if (baseSum <= 0L) {
      intervals.add((int) budgetMs);
      return intervals;
    }

    long consumed = 0L;
    for (int i = 0; i < basePattern.length; i++) {
      long remaining = budgetMs - consumed;
      if (remaining <= 0L) {
        break;
      }

      int interval;
      if (i == basePattern.length - 1) {
        interval = (int) remaining;
      } else {
        long raw = Math.round((budgetMs * basePattern[i]) / (double) baseSum);
        interval = (int) Math.max(1L, raw);
        long stagesLeft = basePattern.length - i - 1L;
        long maxAllowed = remaining - stagesLeft;
        if (maxAllowed <= 0L) {
          maxAllowed = remaining;
        }
        if (interval > maxAllowed) {
          interval = (int) maxAllowed;
        }
      }

      if (interval <= 0) {
        continue;
      }
      intervals.add(interval);
      consumed += interval;
    }

    if (consumed < budgetMs) {
      int remainder = (int) (budgetMs - consumed);
      if (intervals.isEmpty()) {
        intervals.add(remainder);
      } else {
        int lastIndex = intervals.size() - 1;
        intervals.set(lastIndex, intervals.get(lastIndex) + remainder);
      }
    }
    return intervals;
  }

  /** 진행 중인 애니메이션을 취소합니다. */
  public void cancelAnimation() {
    if (startRunnable != null) {
      mainHandler.removeCallbacks(startRunnable);
      startRunnable = null;
    }
    if (spinRunnable != null) {
      mainHandler.removeCallbacks(spinRunnable);
      spinRunnable = null;
    }
    if (snapScaleYAnimation != null) {
      snapScaleYAnimation.cancel();
      snapScaleYAnimation = null;
    }
    spinIntervalsMs = Collections.emptyList();
    spinIntervalIndex = 0;
    setScaleY(1f);
  }

  @Override
  protected void onDetachedFromWindow() {
    cancelAnimation();
    super.onDetachedFromWindow();
  }
}
