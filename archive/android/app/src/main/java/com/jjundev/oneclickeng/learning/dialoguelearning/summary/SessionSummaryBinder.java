package com.jjundev.oneclickeng.learning.dialoguelearning.summary;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.core.widget.NestedScrollView;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.fragment.history.model.SavedCard;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Binds SummaryData to the Summary Fragment UI. */
public class SessionSummaryBinder {

  public static void bind(View rootView, SummaryData data) {
    if (data == null) return;

    // 1. Score
    TextView tvScore = rootView.findViewById(R.id.tv_total_score);
    tvScore.setText(String.valueOf(data.getTotalScore()));

    // 2. Highlights
    bindHighlights(rootView, data.getHighlights());

    // 3. Expressions (initial fallback; overwritten by LLM filter)
    bindExpressions(rootView, data.getExpressions());

    // 4. Liked Sentences
    bindSentences(rootView, data.getLikedSentences());
  }

  private static boolean isBlank(String text) {
    return text == null || text.trim().isEmpty();
  }

  public static void showExpressionsLoading(View rootView) {
    LinearLayout container = rootView.findViewById(R.id.layout_expression_container);
    ShimmerFrameLayout skeleton = rootView.findViewById(R.id.skeleton_expressions);
    TextView errorText = rootView.findViewById(R.id.tv_summary_expressions_error);
    View toggleBtn = rootView.findViewById(R.id.btn_expression_toggle);

    if (container != null) {
      container.setVisibility(View.GONE);
    }
    if (errorText != null) {
      errorText.setVisibility(View.GONE);
    }
    if (toggleBtn != null) {
      toggleBtn.setVisibility(View.GONE);
    }
    if (skeleton != null) {
      skeleton.setVisibility(View.VISIBLE);
      skeleton.startShimmer();
    }
  }

  public static void showExpressionsError(View rootView, String message) {
    LinearLayout container = rootView.findViewById(R.id.layout_expression_container);
    ShimmerFrameLayout skeleton = rootView.findViewById(R.id.skeleton_expressions);
    TextView errorText = rootView.findViewById(R.id.tv_summary_expressions_error);
    View toggleBtn = rootView.findViewById(R.id.btn_expression_toggle);

    if (container != null) {
      container.setVisibility(View.GONE);
    }
    if (skeleton != null) {
      skeleton.stopShimmer();
      skeleton.setVisibility(View.GONE);
    }
    if (toggleBtn != null) {
      toggleBtn.setVisibility(View.GONE);
    }
    if (errorText != null) {
      String finalMessage =
          isBlank(message)
              ? rootView.getContext().getString(R.string.summary_expressions_load_error)
              : message;
      errorText.setText(finalMessage);
      errorText.setVisibility(View.VISIBLE);
    }
  }

  private static void bindHighlights(View rootView, List<SummaryData.HighlightItem> items) {
    TextView titleView = rootView.findViewById(R.id.tv_summary_highlight_title);
    LinearLayout container = rootView.findViewById(R.id.layout_highlight_container);
    container.removeAllViews();
    if (items == null || items.isEmpty()) {
      if (titleView != null) {
        titleView.setVisibility(View.GONE);
      }
      container.setVisibility(View.GONE);
      return;
    }

    if (titleView != null) {
      titleView.setVisibility(View.VISIBLE);
    }
    container.setVisibility(View.VISIBLE);

    LayoutInflater inflater = LayoutInflater.from(rootView.getContext());
    for (SummaryData.HighlightItem item : items) {
      View itemView = inflater.inflate(R.layout.item_summary_highlight, container, false);
      ((TextView) itemView.findViewById(R.id.tv_highlight_english)).setText(item.getEnglish());
      ((TextView) itemView.findViewById(R.id.tv_highlight_korean)).setText(item.getKorean());
      ((TextView) itemView.findViewById(R.id.tv_highlight_reason)).setText(item.getReason());
      container.addView(itemView);
    }
  }

  public static void bindExpressions(View rootView, List<SummaryData.ExpressionItem> items) {
    LinearLayout container = rootView.findViewById(R.id.layout_expression_container);
    ShimmerFrameLayout skeleton = rootView.findViewById(R.id.skeleton_expressions);
    TextView errorText = rootView.findViewById(R.id.tv_summary_expressions_error);
    TextView toggleBtn = rootView.findViewById(R.id.btn_expression_toggle);

    if (container == null) {
      return;
    }

    if (skeleton != null) {
      skeleton.stopShimmer();
      skeleton.setVisibility(View.GONE);
    }
    if (errorText != null) {
      errorText.setVisibility(View.GONE);
    }

    int totalCount = items == null ? 0 : items.size();
    Integer requestedTag =
        toggleBtn == null
            ? null
            : toIntegerTag(toggleBtn.getTag(R.id.tag_expression_requested_visible_count));
    Integer lastTotalTag =
        toggleBtn == null
            ? null
            : toIntegerTag(toggleBtn.getTag(R.id.tag_expression_last_total_count));
    int requestedVisibleCount =
        ExpressionPaginationPolicy.resolveRequestedVisibleCount(
            requestedTag, lastTotalTag, totalCount);

    container.removeAllViews();
    container.setVisibility(View.VISIBLE);
    if (items == null || items.isEmpty()) {
      if (toggleBtn != null) {
        toggleBtn.setTag(R.id.tag_expression_requested_visible_count, 0);
        toggleBtn.setTag(R.id.tag_expression_last_total_count, 0);
        toggleBtn.setVisibility(View.GONE);
        toggleBtn.setOnClickListener(null);
      }
      return;
    }

    LayoutInflater inflater = LayoutInflater.from(rootView.getContext());
    for (int i = 0; i < items.size(); i++) {
      SummaryData.ExpressionItem item = items.get(i);
      View itemView = inflater.inflate(R.layout.item_summary_expression, container, false);
      TextView expressionTypeTextView = itemView.findViewById(R.id.tv_expression_type);
      ((TextView) itemView.findViewById(R.id.tv_expression_prompt)).setText(item.getKoreanPrompt());
      ((TextView) itemView.findViewById(R.id.tv_expression_before)).setText(item.getBefore());
      TextView afterTextView = itemView.findViewById(R.id.tv_expression_after);
      ((TextView) itemView.findViewById(R.id.tv_expression_explanation))
          .setText(item.getExplanation());
      boolean isPreciseType =
          applyExpressionTheme(itemView, expressionTypeTextView, item.getType());
      if (afterTextView != null) {
        afterTextView.setText(buildHighlightedAfterText(itemView, item, isPreciseType));
      }
      bindSaveButton(itemView, R.id.btn_save_expression, item);
      container.addView(itemView);
    }
    applyExpressionVisibility(container, requestedVisibleCount);

    if (toggleBtn != null) {
      toggleBtn.setTag(R.id.tag_expression_requested_visible_count, requestedVisibleCount);
      toggleBtn.setTag(R.id.tag_expression_last_total_count, totalCount);

      boolean hasToggle = updateExpressionToggleUi(toggleBtn, requestedVisibleCount, totalCount);
      if (!hasToggle) {
        toggleBtn.setOnClickListener(null);
      } else {
        toggleBtn.setOnClickListener(
            v -> {
              int currentTotal = container.getChildCount();
              Integer currentRequestedTag =
                  toIntegerTag(toggleBtn.getTag(R.id.tag_expression_requested_visible_count));
              Integer currentLastTotalTag =
                  toIntegerTag(toggleBtn.getTag(R.id.tag_expression_last_total_count));
              int currentRequested =
                  ExpressionPaginationPolicy.resolveRequestedVisibleCount(
                      currentRequestedTag, currentLastTotalTag, currentTotal);
              int nextRequested =
                  ExpressionPaginationPolicy.nextRequestedVisibleCountAfterClick(
                      currentRequested, currentTotal);
              boolean collapsedAfterClick = nextRequested < currentRequested;

              applyExpressionVisibility(container, nextRequested);
              toggleBtn.setTag(R.id.tag_expression_requested_visible_count, nextRequested);
              toggleBtn.setTag(R.id.tag_expression_last_total_count, currentTotal);
              updateExpressionToggleUi(toggleBtn, nextRequested, currentTotal);

              if (collapsedAfterClick) {
                // After collapsing, center the toggle button in the visible scroll area.
                NestedScrollView scrollView = rootView.findViewById(R.id.scroll_view_summary);
                if (scrollView != null) {
                  scrollView.post(
                      () -> {
                        View content = scrollView.getChildAt(0);
                        if (content == null) {
                          return;
                        }
                        int targetY =
                            calculateCenteredScrollTargetY(
                                toggleBtn.getTop(),
                                Math.max(toggleBtn.getHeight(), 0),
                                scrollView.getHeight(),
                                content.getHeight());
                        scrollView.smoothScrollTo(0, targetY);
                      });
                }
              }
            });
      }
    }
  }

  private static void applyExpressionVisibility(LinearLayout container, int visibleCount) {
    int safeVisibleCount = Math.max(0, visibleCount);
    for (int i = 0; i < container.getChildCount(); i++) {
      container.getChildAt(i).setVisibility(i < safeVisibleCount ? View.VISIBLE : View.GONE);
    }
  }

  static int calculateCenteredScrollTargetY(
      int toggleTop, int toggleHeight, int viewportHeight, int contentHeight) {
    int safeToggleHeight = Math.max(0, toggleHeight);
    int safeViewportHeight = Math.max(0, viewportHeight);
    int safeContentHeight = Math.max(0, contentHeight);

    int desiredY = toggleTop + (safeToggleHeight / 2) - (safeViewportHeight / 2);
    int scrollRange = Math.max(0, safeContentHeight - safeViewportHeight);
    return Math.max(0, Math.min(desiredY, scrollRange));
  }

  private static Integer toIntegerTag(Object value) {
    return value instanceof Integer ? (Integer) value : null;
  }

  private static boolean updateExpressionToggleUi(
      TextView toggleBtn, int requestedVisibleCount, int totalCount) {
    String toggleText =
        ExpressionPaginationPolicy.computeToggleText(requestedVisibleCount, totalCount);
    if (toggleText == null) {
      toggleBtn.setVisibility(View.GONE);
      toggleBtn.setOnClickListener(null);
      return false;
    }
    toggleBtn.setVisibility(View.VISIBLE);
    toggleBtn.setText(toggleText);
    return true;
  }

  private static boolean applyExpressionTheme(
      View itemView, TextView expressionTypeTextView, String expressionType) {
    String normalizedType = normalizeExpressionType(expressionType);
    boolean isPreciseType = isPreciseExpressionType(normalizedType);
    ImageView naturalIcon = itemView.findViewById(R.id.iv_expression_type_natural);
    ImageView preciseIcon = itemView.findViewById(R.id.iv_expression_type_precise);
    TextView afterLabel = itemView.findViewById(R.id.tv_expression_after_label);
    MaterialCardView afterCard = itemView.findViewById(R.id.card_expression_after);
    if (naturalIcon != null) {
      naturalIcon.setVisibility(isPreciseType ? View.GONE : View.VISIBLE);
    }
    if (preciseIcon != null) {
      preciseIcon.setVisibility(isPreciseType ? View.VISIBLE : View.GONE);
    }
    if (expressionTypeTextView != null) {
      expressionTypeTextView.setText(normalizedType);
      int typeColorRes =
          isPreciseType ? R.color.expression_precise_accent : R.color.expression_natural_accent;
      expressionTypeTextView.setTextColor(
          ContextCompat.getColor(itemView.getContext(), typeColorRes));
    }
    if (afterLabel != null) {
      int labelColorRes =
          isPreciseType ? R.color.expression_precise_accent : R.color.expression_natural_accent;
      afterLabel.setTextColor(ContextCompat.getColor(itemView.getContext(), labelColorRes));
    }
    if (afterCard != null) {
      int bgColorRes =
          isPreciseType
              ? R.color.summary_expression_precise_after_bg
              : R.color.summary_expression_natural_after_bg;
      afterCard.setCardBackgroundColor(ContextCompat.getColor(itemView.getContext(), bgColorRes));
    }
    return isPreciseType;
  }

  private static String normalizeExpressionType(String expressionType) {
    if (expressionType == null || expressionType.trim().isEmpty()) {
      return "\uC790\uC5F0\uC2A4\uB7EC\uC6B4 \uD45C\uD604";
    }
    return expressionType.trim();
  }

  private static boolean isPreciseExpressionType(String expressionType) {
    return expressionType.contains("\uC815\uD655")
        || expressionType.toLowerCase().contains("precise");
  }

  private static CharSequence buildHighlightedAfterText(
      View itemView, SummaryData.ExpressionItem item, boolean isPreciseType) {
    if (item == null) {
      return "";
    }
    String after = trimToNull(item.getAfter());
    if (after == null) {
      return "";
    }

    List<String> highlightPhrases = new ArrayList<>();
    if (item.getAfterHighlights() != null) {
      for (String phrase : item.getAfterHighlights()) {
        addUniquePhrase(highlightPhrases, phrase);
      }
    }
    if (highlightPhrases.isEmpty()) {
      String before = trimToNull(item.getBefore());
      if (before != null) {
        addUniquePhrase(highlightPhrases, inferDiffPhrase(before, after));
      }
    }
    if (highlightPhrases.isEmpty()) {
      return after;
    }

    SpannableStringBuilder builder = new SpannableStringBuilder(after);
    int highlightBgRes =
        isPreciseType
            ? R.color.summary_expression_precise_highlight_bg
            : R.color.summary_expression_natural_highlight_bg;
    int highlightBgColor = ContextCompat.getColor(itemView.getContext(), highlightBgRes);
    for (String phrase : highlightPhrases) {
      applyPhraseHighlight(builder, after, phrase, highlightBgColor);
    }
    return builder;
  }

  private static void applyPhraseHighlight(
      SpannableStringBuilder builder, String fullText, String phrase, int highlightBgColor) {
    String target = trimToNull(phrase);
    if (target == null) {
      return;
    }
    String textLower = fullText.toLowerCase();
    String targetLower = target.toLowerCase();
    int searchStart = 0;
    while (true) {
      int start = textLower.indexOf(targetLower, searchStart);
      if (start < 0) {
        break;
      }
      int end = start + target.length();
      builder.setSpan(
          new BackgroundColorSpan(highlightBgColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      searchStart = end;
    }
  }

  private static String inferDiffPhrase(String before, String after) {
    Set<String> beforeTokens = new HashSet<>();
    for (String token : before.split("\\s+")) {
      String normalized = normalizeToken(token);
      if (normalized != null) {
        beforeTokens.add(normalized);
      }
    }

    String best = null;
    StringBuilder current = new StringBuilder();
    for (String token : after.split("\\s+")) {
      String normalized = normalizeToken(token);
      boolean changed = normalized == null || !beforeTokens.contains(normalized);
      if (changed) {
        if (current.length() > 0) {
          current.append(' ');
        }
        current.append(token);
      } else if (current.length() > 0) {
        String phrase = trimToNull(current.toString());
        if (phrase != null && (best == null || phrase.length() > best.length())) {
          best = phrase;
        }
        current.setLength(0);
      }
    }
    if (current.length() > 0) {
      String phrase = trimToNull(current.toString());
      if (phrase != null && (best == null || phrase.length() > best.length())) {
        best = phrase;
      }
    }
    return best;
  }

  private static String normalizeToken(String token) {
    String trimmed = trimToNull(token);
    if (trimmed == null) {
      return null;
    }
    String stripped = trimmed.replaceAll("^[^\\p{L}\\p{N}']+|[^\\p{L}\\p{N}']+$", "");
    return stripped.isEmpty() ? null : stripped.toLowerCase();
  }

  private static void addUniquePhrase(List<String> phrases, String phrase) {
    String trimmed = trimToNull(phrase);
    if (trimmed == null) {
      return;
    }
    String normalized = trimmed.toLowerCase();
    for (String existing : phrases) {
      if (existing != null && existing.trim().toLowerCase().equals(normalized)) {
        return;
      }
    }
    phrases.add(trimmed);
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public static void showWordsSection(View rootView) {
    View section = rootView.findViewById(R.id.layout_words_section);
    if (section != null) {
      section.setVisibility(View.VISIBLE);
    }
  }

  public static void hideWordsSection(View rootView) {
    View section = rootView.findViewById(R.id.layout_words_section);
    if (section != null) {
      section.setVisibility(View.GONE);
    }

    LinearLayout container = rootView.findViewById(R.id.layout_word_container);
    ShimmerFrameLayout skeleton = rootView.findViewById(R.id.skeleton_summary_words);
    TextView errorText = rootView.findViewById(R.id.tv_summary_words_error);
    if (container != null) {
      container.removeAllViews();
      container.setVisibility(View.GONE);
    }
    if (skeleton != null) {
      skeleton.stopShimmer();
      skeleton.setVisibility(View.GONE);
    }
    if (errorText != null) {
      errorText.setVisibility(View.GONE);
    }
  }

  public static void bindWordsContent(View rootView, List<SummaryData.WordItem> items) {
    showWordsSection(rootView);
    LinearLayout container = rootView.findViewById(R.id.layout_word_container);
    ShimmerFrameLayout skeleton = rootView.findViewById(R.id.skeleton_summary_words);
    TextView errorText = rootView.findViewById(R.id.tv_summary_words_error);
    if (container == null) {
      return;
    }

    if (skeleton != null) {
      skeleton.stopShimmer();
      skeleton.setVisibility(View.GONE);
    }
    if (errorText != null) {
      errorText.setVisibility(View.GONE);
    }

    container.removeAllViews();
    if (items == null || items.isEmpty()) {
      container.setVisibility(View.GONE);
      return;
    }
    container.setVisibility(View.VISIBLE);

    LayoutInflater inflater = LayoutInflater.from(rootView.getContext());
    for (SummaryData.WordItem item : items) {
      View itemView = inflater.inflate(R.layout.item_summary_word, container, false);
      ((TextView) itemView.findViewById(R.id.tv_summary_word_en)).setText(item.getEnglish());
      ((TextView) itemView.findViewById(R.id.tv_summary_word_ko)).setText(item.getKorean());
      ((TextView) itemView.findViewById(R.id.tv_summary_word_example))
          .setText(item.getExampleEnglish());
      ((TextView) itemView.findViewById(R.id.tv_summary_word_example_ko))
          .setText(item.getExampleKorean());
      bindSaveButton(itemView, R.id.btn_save_word, item);
      container.addView(itemView);
    }
  }

  public static void showWordsLoading(View rootView) {
    showWordsSection(rootView);
    LinearLayout container = rootView.findViewById(R.id.layout_word_container);
    ShimmerFrameLayout skeleton = rootView.findViewById(R.id.skeleton_summary_words);
    TextView errorText = rootView.findViewById(R.id.tv_summary_words_error);

    if (container != null) {
      container.removeAllViews();
      container.setVisibility(View.GONE);
    }
    if (errorText != null) {
      errorText.setVisibility(View.GONE);
    }
    if (skeleton != null) {
      skeleton.setVisibility(View.VISIBLE);
      skeleton.startShimmer();
    }
  }

  public static void showWordsError(View rootView, String message) {
    showWordsSection(rootView);
    LinearLayout container = rootView.findViewById(R.id.layout_word_container);
    ShimmerFrameLayout skeleton = rootView.findViewById(R.id.skeleton_summary_words);
    TextView errorText = rootView.findViewById(R.id.tv_summary_words_error);

    if (container != null) {
      container.removeAllViews();
      container.setVisibility(View.GONE);
    }
    if (skeleton != null) {
      skeleton.stopShimmer();
      skeleton.setVisibility(View.GONE);
    }
    if (errorText != null) {
      String finalMessage =
          isBlank(message)
              ? rootView.getContext().getString(R.string.summary_words_load_error)
              : message;
      errorText.setText(finalMessage);
      errorText.setVisibility(View.VISIBLE);
    }
  }

  public static void showWordsEmpty(View rootView) {
    showWordsSection(rootView);
    LinearLayout container = rootView.findViewById(R.id.layout_word_container);
    ShimmerFrameLayout skeleton = rootView.findViewById(R.id.skeleton_summary_words);
    TextView errorText = rootView.findViewById(R.id.tv_summary_words_error);

    if (container != null) {
      container.removeAllViews();
      container.setVisibility(View.GONE);
    }
    if (skeleton != null) {
      skeleton.stopShimmer();
      skeleton.setVisibility(View.GONE);
    }
    if (errorText != null) {
      errorText.setText(rootView.getContext().getString(R.string.summary_words_no_new));
      errorText.setVisibility(View.VISIBLE);
    }
  }

  private static void bindSentences(View rootView, List<SummaryData.SentenceItem> items) {
    TextView titleView = rootView.findViewById(R.id.tv_summary_sentence_title);
    LinearLayout container = rootView.findViewById(R.id.layout_sentence_container);
    TextView emptyView = rootView.findViewById(R.id.tv_summary_sentence_empty);
    container.removeAllViews();
    if (items == null || items.isEmpty()) {
      if (titleView != null) {
        titleView.setVisibility(View.GONE);
      }
      container.setVisibility(View.GONE);
      if (emptyView != null) {
        emptyView.setVisibility(View.GONE);
      }
      return;
    }
    if (titleView != null) {
      titleView.setVisibility(View.VISIBLE);
    }
    container.setVisibility(View.VISIBLE);
    if (emptyView != null) {
      emptyView.setVisibility(View.GONE);
    }

    LayoutInflater inflater = LayoutInflater.from(rootView.getContext());
    for (SummaryData.SentenceItem item : items) {
      View itemView = inflater.inflate(R.layout.item_summary_sentence, container, false);
      ((TextView) itemView.findViewById(R.id.tv_summary_sentence_en)).setText(item.getEnglish());
      ((TextView) itemView.findViewById(R.id.tv_summary_sentence_ko)).setText(item.getKorean());
      bindSaveButton(itemView, R.id.btn_save_sentence, item);
      container.addView(itemView);
    }
  }

  private static void bindSaveButton(View itemView, int buttonResId, Object itemData) {
    ImageButton saveButton = itemView.findViewById(buttonResId);
    if (saveButton == null) {
      return;
    }

    updateSaveButtonState(saveButton, false);
    updateSaveStatusLabel(itemView, false);
    updateSavedCardBorder(itemView, false);

    Runnable toggleSaveState =
        () -> {
          playSaveWaveAnimation(itemView, saveButton);
          boolean isSaved = !saveButton.isSelected();
          saveButton.setSelected(isSaved);
          updateSaveButtonState(saveButton, isSaved);
          updateSaveStatusLabel(itemView, isSaved);
          updateSavedCardBorder(itemView, isSaved);

          if (isSaved) {
            saveCardToFirebase(itemView, itemData);
          } else {
            removeCardFromFirebase(itemData);
          }
        };

    saveButton.setOnClickListener(v -> toggleSaveState.run());
    itemView.setOnClickListener(v -> toggleSaveState.run());
  }

  private static String generateDeterministicId(Object itemData) {
    String uniqueString = "";
    if (itemData instanceof SummaryData.WordItem) {
      SummaryData.WordItem w = (SummaryData.WordItem) itemData;
      uniqueString = w.getEnglish() + "_" + w.getKorean();
    } else if (itemData instanceof SummaryData.SentenceItem) {
      SummaryData.SentenceItem s = (SummaryData.SentenceItem) itemData;
      uniqueString = s.getEnglish() + "_" + s.getKorean();
    } else if (itemData instanceof SummaryData.ExpressionItem) {
      SummaryData.ExpressionItem e = (SummaryData.ExpressionItem) itemData;
      uniqueString = e.getBefore() + "_" + e.getAfter();
    } else {
      uniqueString = UUID.randomUUID().toString();
    }
    return UUID.nameUUIDFromBytes(uniqueString.getBytes()).toString();
  }

  private static void saveCardToFirebase(View itemView, Object itemData) {
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) {
      Toast.makeText(itemView.getContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
      return;
    }

    SavedCard card = new SavedCard();
    card.setId(generateDeterministicId(itemData));
    card.setTimestamp(System.currentTimeMillis());

    if (itemData instanceof SummaryData.WordItem) {
      SummaryData.WordItem w = (SummaryData.WordItem) itemData;
      card.setCardType("WORD");
      card.setEnglish(w.getEnglish());
      card.setKorean(w.getKorean());
      card.setExampleEnglish(w.getExampleEnglish());
      card.setExampleKorean(w.getExampleKorean());
    } else if (itemData instanceof SummaryData.SentenceItem) {
      SummaryData.SentenceItem s = (SummaryData.SentenceItem) itemData;
      card.setCardType("SENTENCE");
      card.setEnglish(s.getEnglish());
      card.setKorean(s.getKorean());
    } else if (itemData instanceof SummaryData.ExpressionItem) {
      SummaryData.ExpressionItem e = (SummaryData.ExpressionItem) itemData;
      card.setCardType("EXPRESSION");
      card.setType(e.getType());
      card.setKoreanPrompt(e.getKoreanPrompt());
      card.setBefore(e.getBefore());
      card.setAfter(e.getAfter());
      card.setExplanation(e.getExplanation());
      card.setAfterHighlights(e.getAfterHighlights());
    }

    FirebaseFirestore.getInstance()
        .collection("users")
        .document(user.getUid())
        .collection("saved_cards")
        .document(card.getId())
        .set(card)
        .addOnSuccessListener(
            aVoid -> {
              // Toast.makeText(itemView.getContext(), "기록장에 저장되었습니다.",
              // Toast.LENGTH_SHORT).show();
            })
        .addOnFailureListener(
            e -> {
              // Toast.makeText(itemView.getContext(), "저장 실패: " + e.getMessage(),
              // Toast.LENGTH_SHORT).show();
            });
  }

  private static void removeCardFromFirebase(Object itemData) {
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) return;

    String cardId = generateDeterministicId(itemData);
    FirebaseFirestore.getInstance()
        .collection("users")
        .document(user.getUid())
        .collection("saved_cards")
        .document(cardId)
        .delete();
  }

  private static void updateSaveButtonState(ImageButton saveButton, boolean isSaved) {
    int iconRes = isSaved ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark_border;
    int tintColorRes = isSaved ? R.color.save_icon_gold : R.color.semantic_icon_secondary;
    int tintColor = ContextCompat.getColor(saveButton.getContext(), tintColorRes);
    saveButton.setImageResource(iconRes);
    ImageViewCompat.setImageTintList(saveButton, ColorStateList.valueOf(tintColor));
  }

  private static void updateSaveStatusLabel(View itemView, boolean isSaved) {
    TextView saveStatusText = itemView.findViewById(R.id.tv_save_status);
    if (saveStatusText == null) {
      return;
    }
    saveStatusText.setVisibility(isSaved ? View.VISIBLE : View.GONE);
  }

  private static void updateSavedCardBorder(View itemView, boolean isSaved) {
    if (!(itemView instanceof MaterialCardView)) {
      return;
    }

    MaterialCardView cardView = (MaterialCardView) itemView;
    int strokeWidthPx = isSaved ? dpToPx(itemView, 2) : 0;
    int strokeColor = ContextCompat.getColor(itemView.getContext(), R.color.save_icon_gold);
    cardView.setStrokeWidth(strokeWidthPx);
    cardView.setStrokeColor(strokeColor);
  }

  private static int dpToPx(View view, int dp) {
    float density = view.getResources().getDisplayMetrics().density;
    return (int) (dp * density);
  }

  private static void playSaveWaveAnimation(View cardView, View sourceView) {
    int[] cardLocation = new int[2];
    int[] sourceLocation = new int[2];
    cardView.getLocationOnScreen(cardLocation);
    sourceView.getLocationOnScreen(sourceLocation);

    final int centerX = sourceLocation[0] - cardLocation[0] + sourceView.getWidth() / 2;
    final int centerY = sourceLocation[1] - cardLocation[1] + sourceView.getHeight() / 2;

    final float maxRadius = (float) Math.hypot(cardView.getWidth(), cardView.getHeight());
    final int baseColor = ContextCompat.getColor(cardView.getContext(), R.color.save_icon_gold);

    final android.graphics.drawable.GradientDrawable cardFlash =
        new android.graphics.drawable.GradientDrawable();
    cardFlash.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
    cardFlash.setColor(baseColor);
    cardFlash.setAlpha(0);
    cardFlash.setBounds(0, 0, cardView.getWidth(), cardView.getHeight());

    final android.graphics.drawable.GradientDrawable wave =
        new android.graphics.drawable.GradientDrawable();
    wave.setShape(android.graphics.drawable.GradientDrawable.OVAL);
    wave.setColor(baseColor);
    wave.setAlpha(0);

    cardView.getOverlay().add(cardFlash);
    cardView.getOverlay().add(wave);

    ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
    animator.setDuration(680L);
    animator.setInterpolator(new DecelerateInterpolator());
    animator.addUpdateListener(
        animation -> {
          float progress = (float) animation.getAnimatedValue();
          float radius = maxRadius * progress;
          int waveAlpha = (int) (88 * (1f - progress));
          int cardAlpha = (int) (42 * (1f - progress));
          wave.setAlpha(Math.max(0, waveAlpha));
          cardFlash.setAlpha(Math.max(0, cardAlpha));
          wave.setBounds(
              (int) (centerX - radius),
              (int) (centerY - radius),
              (int) (centerX + radius),
              (int) (centerY + radius));
        });

    ObjectAnimator pressX = ObjectAnimator.ofFloat(cardView, View.SCALE_X, 1f, 0.985f, 1f);
    ObjectAnimator pressY = ObjectAnimator.ofFloat(cardView, View.SCALE_Y, 1f, 0.985f, 1f);
    pressX.setDuration(260L);
    pressY.setDuration(260L);
    pressX.setInterpolator(new DecelerateInterpolator());
    pressY.setInterpolator(new DecelerateInterpolator());

    AnimatorSet pressSet = new AnimatorSet();
    pressSet.playTogether(pressX, pressY);

    AnimatorSet fullSet = new AnimatorSet();
    fullSet.playTogether(animator, pressSet);
    fullSet.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            cardView.setScaleX(1f);
            cardView.setScaleY(1f);
            cardView.getOverlay().remove(wave);
            cardView.getOverlay().remove(cardFlash);
          }
        });

    fullSet.start();
  }

  public static SummaryData createDummyData() {
    SummaryData data = new SummaryData();
    data.setTotalScore(95);

    data.setHighlights(
        Arrays.asList(
            new SummaryData.HighlightItem(
                "I was wondering if you could help me with the project.",
                "프로젝트를 도와주실 수 있을지 여쭤봤어요.",
                "완곡한 표현인 'I was wondering if'를 사용해 정중하게 요청한 점이 좋았습니다.")));

    data.setExpressions(
        Arrays.asList(
            new SummaryData.ExpressionItem(
                "자연스러운 표현",
                "이 문장을 영어로 자연스럽게 바꿔보세요.",
                "Before: I am very waiting for the result.",
                "After: I'm really looking forward to the result.",
                "'wait for'보다 'look forward to'가 실제 대화에서 더 자연스럽게 들립니다."),
            new SummaryData.ExpressionItem(
                "정확한 표현",
                "의미가 정확하게 전달되도록 문장을 다듬어보세요.",
                "Before: The data are not enough reliable.",
                "After: The data are not sufficiently reliable.",
                "'enough reliable'은 문법적으로 어색하므로 'sufficiently reliable'로 바꾸는 것이 정확합니다.")));

    data.setWords(
        Arrays.asList(
            new SummaryData.WordItem(
                "Favorable",
                "유리한, 호의적인",
                "The conditions were favorable for the experiment.",
                "그 조건들은 실험에 유리했습니다."),
            new SummaryData.WordItem(
                "Incorporate",
                "포함하다, 통합하다",
                "We should incorporate your ideas into the plan.",
                "우리는 당신의 아이디어를 계획에 포함시켜야 합니다.")));

    data.setLikedSentences(
        Arrays.asList(
            new SummaryData.SentenceItem(
                "It's not just a matter of time; it's about priorities.",
                "그건 단순히 시간의 문제가 아니라 우선순위의 문제입니다."),
            new SummaryData.SentenceItem(
                "Small steps every day lead to big results in the long run.",
                "매일의 작은 실천이 장기적으로 큰 결과를 만듭니다.")));

    return data;
  }
}
