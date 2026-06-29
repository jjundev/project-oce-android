package com.jjundev.oneclickeng.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import java.util.Random;

/** 사용자의 음성 loudness를 waveform으로 시각화하는 커스텀 View Gemini Audio API에서 받은 데이터 또는 더미 데이터로 그릴 수 있습니다. */
public class WaveformView extends View {

  private Paint barPaint;
  private Paint gradientPaint;
  private float[] amplitudes; // 0.0 ~ 1.0 사이의 진폭 값 배열
  private int barCount = 40; // 막대 개수
  private float barWidth;
  private float barSpacing = 4f; // 막대 사이 간격 (dp)
  private float cornerRadius = 4f; // 막대 모서리 둥글기 (dp)
  private RectF barRect;

  // 그라데이션 색상
  private int colorStart = Color.parseColor("#9E9E9E"); // 회색
  private int colorEnd = Color.parseColor("#757575"); // 진한 회색

  public WaveformView(Context context) {
    super(context);
    init();
  }

  public WaveformView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public WaveformView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    barRect = new RectF();

    // 초기 진폭 배열 설정
    amplitudes = new float[barCount];
    for (int i = 0; i < barCount; i++) {
      amplitudes[i] = 0.1f; // 최소 높이
    }

    // dp를 px로 변환
    float density = getResources().getDisplayMetrics().density;
    barSpacing = 4f * density;
    cornerRadius = 4f * density;
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);

    // 막대 너비 계산 (전체 너비에서 간격을 뺀 값을 막대 개수로 나눔)
    float totalSpacing = barSpacing * (barCount - 1);
    barWidth = (w - totalSpacing - getPaddingLeft() - getPaddingRight()) / barCount;

    // 그라데이션 설정
    LinearGradient gradient =
        new LinearGradient(0, h, 0, 0, colorStart, colorEnd, Shader.TileMode.CLAMP);
    gradientPaint.setShader(gradient);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    if (amplitudes == null || amplitudes.length == 0) return;

    int height = getHeight() - getPaddingTop() - getPaddingBottom();
    int startX = getPaddingLeft();
    int centerY = getHeight() / 2;

    for (int i = 0; i < barCount && i < amplitudes.length; i++) {
      float amplitude = amplitudes[i];
      float barHeight = Math.max(height * amplitude, cornerRadius * 2); // 최소 높이 보장

      float left = startX + i * (barWidth + barSpacing);
      float top = centerY - barHeight / 2;
      float right = left + barWidth;
      float bottom = centerY + barHeight / 2;

      barRect.set(left, top, right, bottom);
      canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, gradientPaint);
    }
  }

  /**
   * 진폭 값 배열을 업데이트하고 View를 다시 그립니다.
   *
   * @param newAmplitudes 0.0 ~ 1.0 사이의 진폭 값 배열
   */
  public void updateAmplitudes(float[] newAmplitudes) {
    if (newAmplitudes == null) return;

    // 배열 크기가 다르면 조정
    if (newAmplitudes.length != barCount) {
      amplitudes = new float[barCount];
      for (int i = 0; i < barCount; i++) {
        int sourceIndex = (int) ((float) i / barCount * newAmplitudes.length);
        sourceIndex = Math.min(sourceIndex, newAmplitudes.length - 1);
        amplitudes[i] = Math.max(0.05f, Math.min(1.0f, newAmplitudes[sourceIndex]));
      }
    } else {
      for (int i = 0; i < barCount; i++) {
        amplitudes[i] = Math.max(0.05f, Math.min(1.0f, newAmplitudes[i]));
      }
    }

    invalidate();
  }

  private Random random = new Random();

  /**
   * 단일 진폭 값을 기반으로 모든 막대에 변화를 줍니다. 중앙에서 지지직거리는 효과를 위해 모든 막대가 동시에 변화합니다.
   *
   * @param amplitude 0.0 ~ 1.0 사이의 기준 진폭 값
   */
  public void addAmplitude(float amplitude) {
    // 모든 막대에 랜덤한 변화 적용 (중앙에서 지지직거리는 효과)
    for (int i = 0; i < barCount; i++) {
      // 기준 진폭에 랜덤한 변화를 더함
      float variation = (random.nextFloat() - 0.5f) * 0.6f;
      amplitudes[i] = Math.max(0.05f, Math.min(1.0f, amplitude + variation));
    }
    invalidate();
  }

  /** 모든 진폭 값을 0으로 초기화합니다. */
  public void reset() {
    for (int i = 0; i < barCount; i++) {
      amplitudes[i] = 0.05f;
    }
    invalidate();
  }

  /** 막대 개수를 설정합니다. */
  public void setBarCount(int count) {
    this.barCount = count;
    amplitudes = new float[barCount];
    for (int i = 0; i < barCount; i++) {
      amplitudes[i] = 0.05f;
    }
    requestLayout();
    invalidate();
  }

  /** 그라데이션 색상을 설정합니다. */
  public void setGradientColors(int startColor, int endColor) {
    this.colorStart = startColor;
    this.colorEnd = endColor;

    if (getWidth() > 0 && getHeight() > 0) {
      LinearGradient gradient =
          new LinearGradient(0, getHeight(), 0, 0, colorStart, colorEnd, Shader.TileMode.CLAMP);
      gradientPaint.setShader(gradient);
      invalidate();
    }
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();
  }
}
