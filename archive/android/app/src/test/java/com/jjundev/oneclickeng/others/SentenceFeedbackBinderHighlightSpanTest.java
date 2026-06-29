package com.jjundev.oneclickeng.others;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.GrammarFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.NaturalnessFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SentenceFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.StyledSentence;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.TextSegment;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
public class SentenceFeedbackBinderHighlightSpanTest {

  @Test
  public void bindGrammar_highlightAppliesBackgroundAndForegroundInLightMode() {
    Context context = RuntimeEnvironment.getApplication();
    FrameLayout root = createRoot(context, true);
    SentenceFeedbackBinder binder = new SentenceFeedbackBinder(root);

    SentenceFeedback feedback = new SentenceFeedback();
    GrammarFeedback grammarFeedback = new GrammarFeedback();
    grammarFeedback.setCorrectedSentence(
        styledSentence(
            segment("I ", TextSegment.TYPE_NORMAL),
            segment("really", TextSegment.TYPE_HIGHLIGHT),
            segment(" like it.", TextSegment.TYPE_NORMAL)));
    feedback.setGrammar(grammarFeedback);

    binder.bind(feedback);

    TextView tv = root.findViewById(R.id.tv_grammar_example);
    assertNotNull(tv);
    Spanned text = (Spanned) tv.getText();
    int start = tv.getText().toString().indexOf("really");
    int end = start + "really".length();

    int expectedBg = ContextCompat.getColor(context, R.color.color_feedback_highlight_bg);
    int expectedFg = ContextCompat.getColor(context, R.color.color_feedback_highlight_text);

    assertHasBackgroundColor(text, start, end, expectedBg);
    assertHasForegroundColor(text, start, end, expectedFg);
  }

  @Test
  @Config(qualifiers = "night")
  public void bindGrammar_highlightUsesNightVariantColorsInNightMode() {
    Context context = RuntimeEnvironment.getApplication();
    FrameLayout root = createRoot(context, true);
    SentenceFeedbackBinder binder = new SentenceFeedbackBinder(root);

    SentenceFeedback feedback = new SentenceFeedback();
    GrammarFeedback grammarFeedback = new GrammarFeedback();
    grammarFeedback.setCorrectedSentence(
        styledSentence(segment("night", TextSegment.TYPE_HIGHLIGHT)));
    feedback.setGrammar(grammarFeedback);

    binder.bind(feedback);

    TextView tv = root.findViewById(R.id.tv_grammar_example);
    assertNotNull(tv);
    Spanned text = (Spanned) tv.getText();
    int start = 0;
    int end = "night".length();

    int expectedBg = ContextCompat.getColor(context, R.color.color_feedback_highlight_bg);
    int expectedFg = ContextCompat.getColor(context, R.color.color_feedback_highlight_text);

    assertEquals(Color.parseColor("#6B4E00"), expectedBg);
    assertEquals(Color.parseColor("#FFF3CC"), expectedFg);
    assertHasBackgroundColor(text, start, end, expectedBg);
    assertHasForegroundColor(text, start, end, expectedFg);
  }

  @Test
  public void bindNaturalness_highlightAppliesBackgroundAndForeground() {
    Context context = RuntimeEnvironment.getApplication();
    FrameLayout root = createRoot(context, true);
    SentenceFeedbackBinder binder = new SentenceFeedbackBinder(root);

    SentenceFeedback feedback = new SentenceFeedback();
    NaturalnessFeedback naturalnessFeedback = new NaturalnessFeedback();
    naturalnessFeedback.setNaturalSentence(
        styledSentence(
            segment("This is ", TextSegment.TYPE_NORMAL),
            segment("way better", TextSegment.TYPE_HIGHLIGHT)));
    feedback.setNaturalness(naturalnessFeedback);

    binder.bind(feedback);

    TextView tv = root.findViewById(R.id.tv_natural_sentence_example);
    assertNotNull(tv);
    Spanned text = (Spanned) tv.getText();
    int start = tv.getText().toString().indexOf("way better");
    int end = start + "way better".length();

    int expectedBg = ContextCompat.getColor(context, R.color.color_feedback_highlight_bg);
    int expectedFg = ContextCompat.getColor(context, R.color.color_feedback_highlight_text);

    assertHasBackgroundColor(text, start, end, expectedBg);
    assertHasForegroundColor(text, start, end, expectedFg);
  }

  @Test
  public void bindGrammar_keepsIncorrectAndCorrectionStyling() {
    Context context = RuntimeEnvironment.getApplication();
    FrameLayout root = createRoot(context, true);
    SentenceFeedbackBinder binder = new SentenceFeedbackBinder(root);

    SentenceFeedback feedback = new SentenceFeedback();
    GrammarFeedback grammarFeedback = new GrammarFeedback();
    grammarFeedback.setCorrectedSentence(
        styledSentence(
            segment("I ", TextSegment.TYPE_NORMAL),
            segment("go", TextSegment.TYPE_INCORRECT),
            segment(" went", TextSegment.TYPE_CORRECTION)));
    feedback.setGrammar(grammarFeedback);

    binder.bind(feedback);

    TextView tv = root.findViewById(R.id.tv_grammar_example);
    assertNotNull(tv);
    Spanned text = (Spanned) tv.getText();
    String fullText = tv.getText().toString();

    int incorrectStart = fullText.indexOf("go");
    int incorrectEnd = incorrectStart + "go".length();
    int correctionStart = fullText.indexOf("went");
    int correctionEnd = correctionStart + "went".length();

    assertHasForegroundColor(text, incorrectStart, incorrectEnd, Color.parseColor("#F44336"));
    assertHasStrikethrough(text, incorrectStart, incorrectEnd);
    assertHasForegroundColor(text, correctionStart, correctionEnd, Color.parseColor("#4CAF50"));
    assertHasBold(text, correctionStart, correctionEnd);
  }

  private static FrameLayout createRoot(Context context, boolean includeNaturalnessViews) {
    FrameLayout root = new FrameLayout(context);
    root.addView(newTextView(context, R.id.tv_grammar_example));
    if (includeNaturalnessViews) {
      root.addView(newTextView(context, R.id.tv_natural_sentence_example));
    }
    return root;
  }

  private static TextView newTextView(Context context, int id) {
    TextView tv = new TextView(context);
    tv.setId(id);
    return tv;
  }

  private static StyledSentence styledSentence(TextSegment... segments) {
    StyledSentence styledSentence = new StyledSentence();
    styledSentence.setSegments(Arrays.asList(segments));
    return styledSentence;
  }

  private static TextSegment segment(String text, String type) {
    TextSegment textSegment = new TextSegment();
    textSegment.setText(text);
    textSegment.setType(type);
    return textSegment;
  }

  private static void assertHasBackgroundColor(
      Spanned text, int start, int end, int expectedColor) {
    BackgroundColorSpan[] spans = text.getSpans(start, end, BackgroundColorSpan.class);
    for (BackgroundColorSpan span : spans) {
      if (text.getSpanStart(span) <= start
          && text.getSpanEnd(span) >= end
          && span.getBackgroundColor() == expectedColor) {
        return;
      }
    }
    throw new AssertionError("Expected BackgroundColorSpan not found in target range.");
  }

  private static void assertHasForegroundColor(
      Spanned text, int start, int end, int expectedColor) {
    ForegroundColorSpan[] spans = text.getSpans(start, end, ForegroundColorSpan.class);
    for (ForegroundColorSpan span : spans) {
      if (text.getSpanStart(span) <= start
          && text.getSpanEnd(span) >= end
          && span.getForegroundColor() == expectedColor) {
        return;
      }
    }
    throw new AssertionError("Expected ForegroundColorSpan not found in target range.");
  }

  private static void assertHasStrikethrough(Spanned text, int start, int end) {
    StrikethroughSpan[] spans = text.getSpans(start, end, StrikethroughSpan.class);
    for (StrikethroughSpan span : spans) {
      if (text.getSpanStart(span) <= start && text.getSpanEnd(span) >= end) {
        return;
      }
    }
    throw new AssertionError("Expected StrikethroughSpan not found in target range.");
  }

  private static void assertHasBold(Spanned text, int start, int end) {
    StyleSpan[] spans = text.getSpans(start, end, StyleSpan.class);
    for (StyleSpan span : spans) {
      if (text.getSpanStart(span) <= start
          && text.getSpanEnd(span) >= end
          && span.getStyle() == Typeface.BOLD) {
        return;
      }
    }
    throw new AssertionError("Expected bold StyleSpan not found in target range.");
  }
}
