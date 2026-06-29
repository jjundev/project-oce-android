package com.jjundev.oneclickeng.learning.dialoguelearning.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RecordingUiController {
  private final int maxRecordingSeconds;

  @Nullable private ProgressBar progressRing;
  @Nullable private View ripple1;
  @Nullable private View ripple2;
  @Nullable private View ripple3;

  @Nullable private ObjectAnimator progressAnimator;
  @Nullable private AnimatorSet rippleAnimatorSet;

  public RecordingUiController(int maxRecordingSeconds) {
    this.maxRecordingSeconds = maxRecordingSeconds;
  }

  public void bindViews(
      @Nullable ProgressBar progressRing,
      @Nullable View ripple1,
      @Nullable View ripple2,
      @Nullable View ripple3) {
    this.progressRing = progressRing;
    this.ripple1 = ripple1;
    this.ripple2 = ripple2;
    this.ripple3 = ripple3;
  }

  public void startProgressAnimation() {
    if (progressRing == null) {
      return;
    }

    if (progressAnimator != null) {
      progressAnimator.cancel();
    }
    progressRing.setProgress(0);
    progressAnimator = ObjectAnimator.ofInt(progressRing, "progress", 0, 100);
    progressAnimator.setDuration(maxRecordingSeconds * 1000L);
    progressAnimator.setInterpolator(new LinearInterpolator());
    progressAnimator.setRepeatCount(android.animation.ValueAnimator.INFINITE);
    progressAnimator.start();
  }

  public void stopProgressAnimation() {
    if (progressAnimator != null) {
      progressAnimator.cancel();
      progressAnimator = null;
    }
  }

  public void startRippleAnimation() {
    if (ripple1 == null || ripple2 == null || ripple3 == null) {
      return;
    }

    if (rippleAnimatorSet != null) {
      rippleAnimatorSet.cancel();
      rippleAnimatorSet = null;
    }
    ripple1.setVisibility(View.VISIBLE);
    ripple2.setVisibility(View.VISIBLE);
    ripple3.setVisibility(View.VISIBLE);
    ripple1.setScaleX(1f);
    ripple1.setScaleY(1f);
    ripple1.setAlpha(0.85f);
    ripple2.setScaleX(1f);
    ripple2.setScaleY(1f);
    ripple2.setAlpha(0.85f);
    ripple3.setScaleX(1f);
    ripple3.setScaleY(1f);
    ripple3.setAlpha(0.85f);

    AnimatorSet ripple1Anim = createSingleRippleAnimation(ripple1);
    AnimatorSet ripple2Anim = createSingleRippleAnimation(ripple2);
    AnimatorSet ripple3Anim = createSingleRippleAnimation(ripple3);

    rippleAnimatorSet = new AnimatorSet();
    ripple1Anim.setStartDelay(0);
    ripple2Anim.setStartDelay(500);
    ripple3Anim.setStartDelay(1000);

    rippleAnimatorSet.playTogether(ripple1Anim, ripple2Anim, ripple3Anim);
    rippleAnimatorSet.start();
  }

  public void stopRippleAnimation() {
    if (rippleAnimatorSet != null) {
      rippleAnimatorSet.cancel();
      rippleAnimatorSet = null;
    }

    if (ripple1 != null) {
      ripple1.setVisibility(View.GONE);
      ripple1.setScaleX(1f);
      ripple1.setScaleY(1f);
      ripple1.setAlpha(0f);
    }
    if (ripple2 != null) {
      ripple2.setVisibility(View.GONE);
      ripple2.setScaleX(1f);
      ripple2.setScaleY(1f);
      ripple2.setAlpha(0f);
    }
    if (ripple3 != null) {
      ripple3.setVisibility(View.GONE);
      ripple3.setScaleX(1f);
      ripple3.setScaleY(1f);
      ripple3.setAlpha(0f);
    }
  }

  private AnimatorSet createSingleRippleAnimation(@NonNull View rippleView) {
    ObjectAnimator scaleX = ObjectAnimator.ofFloat(rippleView, "scaleX", 1f, 1.5f);
    ObjectAnimator scaleY = ObjectAnimator.ofFloat(rippleView, "scaleY", 1f, 1.5f);
    ObjectAnimator alpha = ObjectAnimator.ofFloat(rippleView, "alpha", 0.8f, 0f);

    AnimatorSet animatorSet = new AnimatorSet();
    animatorSet.playTogether(scaleX, scaleY, alpha);
    animatorSet.setDuration(1500);
    animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
    animatorSet.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            rippleView.setScaleX(1f);
            rippleView.setScaleY(1f);
            rippleView.setAlpha(0f);
            if (rippleAnimatorSet != null) {
              animatorSet.start();
            }
          }
        });

    return animatorSet;
  }

  public void clear() {
    stopProgressAnimation();
    stopRippleAnimation();
    progressRing = null;
    ripple1 = null;
    ripple2 = null;
    ripple3 = null;
  }
}
