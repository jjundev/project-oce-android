package com.jjundev.oneclickeng.fragment.history;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LearningHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

  // Same logic as SessionSummaryBinder for save states and expressions
  // Duplicated some logic here or could refactor Binder later

  public interface OnItemClickListener {
    void onItemClick(HistoryItemWrapper item);
  }

  private List<HistoryItemWrapper> allItems = new ArrayList<>();
  private List<HistoryItemWrapper> filteredList = new ArrayList<>();
  private OnItemClickListener listener;

  public LearningHistoryAdapter(OnItemClickListener listener) {
    this.listener = listener;
  }

  public void submitList(List<HistoryItemWrapper> items, int tabPosition) {
    this.allItems = items;
    filter(tabPosition);
  }

  public HistoryItemWrapper getItem(int position) {
    if (position >= 0 && position < filteredList.size()) {
      return filteredList.get(position);
    }
    return null;
  }

  private void filter(int tabPosition) {
    filteredList.clear();
    int targetType = -1;
    switch (tabPosition) {
      case 0:
        targetType = HistoryItemWrapper.TYPE_EXPRESSION;
        break; // 표현
      case 1:
        targetType = HistoryItemWrapper.TYPE_WORD;
        break; // 단어
      case 2:
        targetType = HistoryItemWrapper.TYPE_SENTENCE;
        break; // 문장
    }
    if (targetType != -1) {
      for (HistoryItemWrapper item : allItems) {
        if (item.getType() == targetType) {
          filteredList.add(item);
        }
      }
    }
    notifyDataSetChanged();
  }

  @Override
  public int getItemViewType(int position) {
    return filteredList.get(position).getType();
  }

  @NonNull
  @Override
  public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    LayoutInflater inflater = LayoutInflater.from(parent.getContext());
    switch (viewType) {
      case HistoryItemWrapper.TYPE_WORD:
        return new WordViewHolder(inflater.inflate(R.layout.item_summary_word, parent, false));
      case HistoryItemWrapper.TYPE_EXPRESSION:
        return new ExpressionViewHolder(
            inflater.inflate(R.layout.item_summary_expression, parent, false));
      case HistoryItemWrapper.TYPE_SENTENCE:
        return new SentenceViewHolder(
            inflater.inflate(R.layout.item_summary_sentence, parent, false));
      default:
        throw new IllegalArgumentException("Unknown view type");
    }
  }

  @Override
  public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
    HistoryItemWrapper item = filteredList.get(position);
    switch (item.getType()) {
      case HistoryItemWrapper.TYPE_WORD:
        ((WordViewHolder) holder).bind((SummaryData.WordItem) item.getData(), listener, item);
        break;
      case HistoryItemWrapper.TYPE_EXPRESSION:
        ((ExpressionViewHolder) holder)
            .bind((SummaryData.ExpressionItem) item.getData(), listener, item);
        break;
      case HistoryItemWrapper.TYPE_SENTENCE:
        ((SentenceViewHolder) holder)
            .bind((SummaryData.SentenceItem) item.getData(), listener, item);
        break;
    }
  }

  @Override
  public int getItemCount() {
    return filteredList.size();
  }

  // ViewHolders
  static class WordViewHolder extends RecyclerView.ViewHolder {
    TextView en, ko, exEn, exKo;
    ImageButton saveBtn;

    WordViewHolder(@NonNull View itemView) {
      super(itemView);
      en = itemView.findViewById(R.id.tv_summary_word_en);
      ko = itemView.findViewById(R.id.tv_summary_word_ko);
      exEn = itemView.findViewById(R.id.tv_summary_word_example);
      exKo = itemView.findViewById(R.id.tv_summary_word_example_ko);
      saveBtn = itemView.findViewById(R.id.btn_save_word);
    }

    void bind(SummaryData.WordItem data, OnItemClickListener listener, HistoryItemWrapper wrapper) {
      en.setText(data.getEnglish());
      ko.setText(data.getKorean());
      exEn.setText(data.getExampleEnglish());
      exKo.setText(data.getExampleKorean());
      if (saveBtn != null) {
        saveBtn.setVisibility(View.GONE);
      }
    }
  }

  static class ExpressionViewHolder extends RecyclerView.ViewHolder {
    TextView type, prompt, before, after, explanation;
    ImageButton saveBtn;
    ImageView naturalIcon, preciseIcon;
    TextView afterLabel;
    MaterialCardView afterCard;

    ExpressionViewHolder(@NonNull View itemView) {
      super(itemView);
      type = itemView.findViewById(R.id.tv_expression_type);
      prompt = itemView.findViewById(R.id.tv_expression_prompt);
      before = itemView.findViewById(R.id.tv_expression_before);
      after = itemView.findViewById(R.id.tv_expression_after);
      explanation = itemView.findViewById(R.id.tv_expression_explanation);
      saveBtn = itemView.findViewById(R.id.btn_save_expression);

      naturalIcon = itemView.findViewById(R.id.iv_expression_type_natural);
      preciseIcon = itemView.findViewById(R.id.iv_expression_type_precise);
      afterLabel = itemView.findViewById(R.id.tv_expression_after_label);
      afterCard = itemView.findViewById(R.id.card_expression_after);
    }

    void bind(
        SummaryData.ExpressionItem data, OnItemClickListener listener, HistoryItemWrapper wrapper) {
      prompt.setText(data.getKoreanPrompt());
      before.setText(data.getBefore());
      explanation.setText(data.getExplanation());

      boolean isPreciseType = applyExpressionTheme(itemView, data.getType());
      if (after != null) {
        after.setText(buildHighlightedAfterText(itemView, data, isPreciseType));
      }

      if (saveBtn != null) {
        saveBtn.setVisibility(View.GONE);
      }
    }

    private boolean applyExpressionTheme(View itemView, String expressionType) {
      String normalizedType = normalizeExpressionType(expressionType);
      boolean isPreciseType = isPreciseExpressionType(normalizedType);

      if (naturalIcon != null) naturalIcon.setVisibility(isPreciseType ? View.GONE : View.VISIBLE);
      if (preciseIcon != null) preciseIcon.setVisibility(isPreciseType ? View.VISIBLE : View.GONE);

      if (type != null) {
        type.setText(normalizedType);
        int typeColorRes =
            isPreciseType ? R.color.expression_precise_accent : R.color.expression_natural_accent;
        type.setTextColor(ContextCompat.getColor(itemView.getContext(), typeColorRes));
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
  }

  static class SentenceViewHolder extends RecyclerView.ViewHolder {
    TextView en, ko;
    ImageButton saveBtn;

    SentenceViewHolder(@NonNull View itemView) {
      super(itemView);
      en = itemView.findViewById(R.id.tv_summary_sentence_en);
      ko = itemView.findViewById(R.id.tv_summary_sentence_ko);
      saveBtn = itemView.findViewById(R.id.btn_save_sentence);
    }

    void bind(
        SummaryData.SentenceItem data, OnItemClickListener listener, HistoryItemWrapper wrapper) {
      en.setText(data.getEnglish());
      ko.setText(data.getKorean());
      if (saveBtn != null) {
        saveBtn.setVisibility(View.GONE);
      }
    }
  }

  private static String normalizeExpressionType(String expressionType) {
    if (expressionType == null || expressionType.trim().isEmpty()) {
      return "자연스러운 표현";
    }
    return expressionType.trim();
  }

  private static boolean isPreciseExpressionType(String expressionType) {
    return expressionType.contains("정확") || expressionType.toLowerCase().contains("precise");
  }

  private static String trimToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static CharSequence buildHighlightedAfterText(
      View itemView, SummaryData.ExpressionItem item, boolean isPreciseType) {
    if (item == null) return "";
    String after = trimToNull(item.getAfter());
    if (after == null) return "";

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
    if (highlightPhrases.isEmpty()) return after;

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
    if (target == null) return;
    String textLower = fullText.toLowerCase();
    String targetLower = target.toLowerCase();
    int searchStart = 0;
    while (true) {
      int start = textLower.indexOf(targetLower, searchStart);
      if (start < 0) break;
      int end = start + target.length();
      builder.setSpan(
          new BackgroundColorSpan(highlightBgColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      searchStart = end;
    }
  }

  private static void addUniquePhrase(List<String> phrases, String phrase) {
    String trimmed = trimToNull(phrase);
    if (trimmed == null) return;
    String normalized = trimmed.toLowerCase();
    for (String existing : phrases) {
      if (existing != null && existing.trim().toLowerCase().equals(normalized)) {
        return;
      }
    }
    phrases.add(trimmed);
  }

  private static String inferDiffPhrase(String before, String after) {
    Set<String> beforeTokens = new HashSet<>();
    for (String token : before.split("\\s+")) {
      String normalized = normalizeToken(token);
      if (normalized != null) beforeTokens.add(normalized);
    }

    String best = null;
    StringBuilder current = new StringBuilder();
    for (String token : after.split("\\s+")) {
      String normalized = normalizeToken(token);
      boolean changed = normalized == null || !beforeTokens.contains(normalized);
      if (changed) {
        if (current.length() > 0) current.append(' ');
        current.append(token);
      } else if (current.length() > 0) {
        String phrase = trimToNull(current.toString());
        if (phrase != null && (best == null || phrase.length() > best.length())) best = phrase;
        current.setLength(0);
      }
    }
    if (current.length() > 0) {
      String phrase = trimToNull(current.toString());
      if (phrase != null && (best == null || phrase.length() > best.length())) best = phrase;
    }
    return best;
  }

  private static String normalizeToken(String token) {
    String trimmed = trimToNull(token);
    if (trimmed == null) return null;
    String stripped = trimmed.replaceAll("^[^\\p{L}\\p{N}']+|[^\\p{L}\\p{N}']+$", "");
    return stripped.isEmpty() ? null : stripped.toLowerCase();
  }
}
