package com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts;

import androidx.annotation.NonNull;
import com.jjundev.oneclickeng.learning.dialoguelearning.summary.SummaryFeatureBundle;
import java.util.List;

public interface ISessionSummaryLlmManager {

  interface WordExtractionCallback {
    void onSuccess(@NonNull List<ExtractedWord> words);

    void onFailure(@NonNull String error);
  }

  interface ExpressionFilterCallback {
    void onExpressionReceived(@NonNull FilteredExpression expression);

    void onComplete();

    void onFailure(@NonNull String error);
  }

  final class ExtractedWord {
    @NonNull private final String en;
    @NonNull private final String ko;
    @NonNull private final String exampleEn;
    @NonNull private final String exampleKo;

    public ExtractedWord(
        @NonNull String en,
        @NonNull String ko,
        @NonNull String exampleEn,
        @NonNull String exampleKo) {
      this.en = en;
      this.ko = ko;
      this.exampleEn = exampleEn;
      this.exampleKo = exampleKo;
    }

    @NonNull
    public String getEn() {
      return en;
    }

    @NonNull
    public String getKo() {
      return ko;
    }

    @NonNull
    public String getExampleEn() {
      return exampleEn;
    }

    @NonNull
    public String getExampleKo() {
      return exampleKo;
    }
  }

  final class FilteredExpression {
    @NonNull private final String type;
    @NonNull private final String koreanPrompt;
    @NonNull private final String before;
    @NonNull private final String after;
    @NonNull private final String explanation;

    public FilteredExpression(
        @NonNull String type,
        @NonNull String koreanPrompt,
        @NonNull String before,
        @NonNull String after,
        @NonNull String explanation) {
      this.type = type;
      this.koreanPrompt = koreanPrompt;
      this.before = before;
      this.after = after;
      this.explanation = explanation;
    }

    @NonNull
    public String getType() {
      return type;
    }

    @NonNull
    public String getKoreanPrompt() {
      return koreanPrompt;
    }

    @NonNull
    public String getBefore() {
      return before;
    }

    @NonNull
    public String getAfter() {
      return after;
    }

    @NonNull
    public String getExplanation() {
      return explanation;
    }
  }

  void extractWordsFromSentencesAsync(
      @NonNull List<String> words,
      @NonNull List<String> sentences,
      @NonNull List<String> userOriginalSentences,
      @NonNull WordExtractionCallback callback);

  void filterExpressionsAsync(
      @NonNull SummaryFeatureBundle bundle, @NonNull ExpressionFilterCallback callback);
}
