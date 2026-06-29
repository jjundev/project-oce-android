package com.jjundev.oneclickeng.widget;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 문자열에서 숫자(0~9)만 자릿수 단위 슬롯머신 애니메이션을 적용하는 텍스트 그룹 뷰입니다. 비숫자 문자는 고정 텍스트로 표시합니다. */
public class SlotMachineTextGroupView extends LinearLayout {

  @NonNull private String renderedText = "";
  @NonNull private List<Integer> previousDigits = Collections.emptyList();

  private int resolvedTextColor;
  private float resolvedTextSizePx;
  @Nullable private Typeface resolvedTypeface;
  private float resolvedLetterSpacing;
  private boolean includeFontPadding;
  private float lineSpacingExtra;
  private float lineSpacingMultiplier;
  private int resolvedGravity;

  public SlotMachineTextGroupView(@NonNull Context context) {
    this(context, null);
  }

  public SlotMachineTextGroupView(@NonNull Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public SlotMachineTextGroupView(
      @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    initFromTemplate(context, attrs, defStyleAttr);
  }

  private void initFromTemplate(
      @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
    setOrientation(HORIZONTAL);
    setBaselineAligned(true);

    AppCompatTextView template = new AppCompatTextView(context, attrs, defStyleAttr);
    resolvedTextColor = template.getCurrentTextColor();
    resolvedTextSizePx = template.getTextSize();
    resolvedTypeface = template.getTypeface();
    resolvedLetterSpacing = template.getLetterSpacing();
    includeFontPadding = template.getIncludeFontPadding();
    lineSpacingExtra = template.getLineSpacingExtra();
    lineSpacingMultiplier = template.getLineSpacingMultiplier();
    resolvedGravity = template.getGravity();

    CharSequence initialText = template.getText();
    renderText(initialText == null ? "" : initialText.toString(), false, 0L, 0L);
  }

  public void setText(@Nullable CharSequence text) {
    String safeText = text == null ? "" : text.toString();
    if (safeText.equals(renderedText) && getChildCount() > 0) {
      return;
    }
    renderText(safeText, false, 0L, 0L);
  }

  public void animateText(@Nullable CharSequence text, long duration, long startDelay) {
    String safeText = text == null ? "" : text.toString();
    if (safeText.equals(renderedText)) {
      return;
    }
    renderText(safeText, true, duration, startDelay);
  }

  @NonNull
  public CharSequence getText() {
    return renderedText;
  }

  public void cancelAnimation() {
    for (int i = 0; i < getChildCount(); i++) {
      View child = getChildAt(i);
      if (child instanceof SlotMachineTextView) {
        ((SlotMachineTextView) child).cancelAnimation();
      }
    }
  }

  private void renderText(
      @NonNull String text, boolean animateDigits, long duration, long startDelay) {
    cancelAnimation();
    removeAllViews();

    List<Integer> targetDigits = extractAsciiDigits(text);
    int maxDelta = resolveMaxDigitDelta(previousDigits, targetDigits);
    boolean shouldAnimate = animateDigits && duration > 0L && maxDelta > 0;

    int digitIndex = 0;

    for (int offset = 0; offset < text.length(); ) {
      int codePoint = text.codePointAt(offset);
      offset += Character.charCount(codePoint);

      if (isAsciiDigit(codePoint)) {
        int targetDigit = codePoint - '0';
        int startDigit = digitIndex < previousDigits.size() ? previousDigits.get(digitIndex) : 0;
        int delta = Math.abs(targetDigit - startDigit);

        SlotMachineTextView digitView = new SlotMachineTextView(getContext());
        applyBaseTextStyle(digitView);
        if (shouldAnimate && delta > 0) {
          long digitDuration = Math.max(1L, Math.round(duration * (delta / (double) maxDelta)));
          digitView.animateValue(startDigit, targetDigit, "", digitDuration, startDelay);
        } else {
          digitView.setText(String.valueOf(targetDigit));
        }
        addView(digitView);

        digitIndex++;
        continue;
      }

      AppCompatTextView staticTextView = new AppCompatTextView(getContext());
      applyBaseTextStyle(staticTextView);
      staticTextView.setText(new String(Character.toChars(codePoint)));
      addView(staticTextView);
    }

    renderedText = text;
    previousDigits = targetDigits;
    setContentDescription(renderedText);
  }

  private void applyBaseTextStyle(@NonNull AppCompatTextView textView) {
    textView.setLayoutParams(
        new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    textView.setTextColor(resolvedTextColor);
    textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resolvedTextSizePx);
    if (resolvedTypeface != null) {
      textView.setTypeface(resolvedTypeface);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      textView.setLetterSpacing(resolvedLetterSpacing);
    }
    textView.setIncludeFontPadding(includeFontPadding);
    textView.setLineSpacing(lineSpacingExtra, lineSpacingMultiplier);
    textView.setSingleLine(true);
    textView.setGravity(resolvedGravity);
    textView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  private static boolean isAsciiDigit(int codePoint) {
    return codePoint >= '0' && codePoint <= '9';
  }

  @NonNull
  private static List<Integer> extractAsciiDigits(@NonNull String text) {
    List<Integer> digits = new ArrayList<>();
    for (int offset = 0; offset < text.length(); ) {
      int codePoint = text.codePointAt(offset);
      offset += Character.charCount(codePoint);
      if (isAsciiDigit(codePoint)) {
        digits.add(codePoint - '0');
      }
    }
    return digits;
  }

  private static int resolveMaxDigitDelta(
      @NonNull List<Integer> previousDigits, @NonNull List<Integer> targetDigits) {
    int maxDelta = 0;
    for (int i = 0; i < targetDigits.size(); i++) {
      int startDigit = i < previousDigits.size() ? previousDigits.get(i) : 0;
      int delta = Math.abs(targetDigits.get(i) - startDigit);
      if (delta > maxDelta) {
        maxDelta = delta;
      }
    }
    return maxDelta;
  }

  @Override
  protected void onDetachedFromWindow() {
    cancelAnimation();
    super.onDetachedFromWindow();
  }
}
