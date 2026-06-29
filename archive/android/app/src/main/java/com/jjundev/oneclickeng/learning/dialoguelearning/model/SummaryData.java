package com.jjundev.oneclickeng.learning.dialoguelearning.model;

import java.util.List;

/** Aggregate model for summary screen data. */
public class SummaryData {
  private int totalScore;
  private List<HighlightItem> highlights;
  private List<ExpressionItem> expressions;
  private List<WordItem> words;
  private List<SentenceItem> likedSentences;

  public int getTotalScore() {
    return totalScore;
  }

  public void setTotalScore(int totalScore) {
    this.totalScore = totalScore;
  }

  public List<HighlightItem> getHighlights() {
    return highlights;
  }

  public void setHighlights(List<HighlightItem> highlights) {
    this.highlights = highlights;
  }

  public List<ExpressionItem> getExpressions() {
    return expressions;
  }

  public void setExpressions(List<ExpressionItem> expressions) {
    this.expressions = expressions;
  }

  public List<WordItem> getWords() {
    return words;
  }

  public void setWords(List<WordItem> words) {
    this.words = words;
  }

  public List<SentenceItem> getLikedSentences() {
    return likedSentences;
  }

  public void setLikedSentences(List<SentenceItem> likedSentences) {
    this.likedSentences = likedSentences;
  }

  public static class HighlightItem {
    private String english;
    private String korean;
    private String reason;

    public HighlightItem(String english, String korean, String reason) {
      this.english = english;
      this.korean = korean;
      this.reason = reason;
    }

    public String getEnglish() {
      return english;
    }

    public String getKorean() {
      return korean;
    }

    public String getReason() {
      return reason;
    }
  }

  public static class ExpressionItem {
    private String type;
    private String koreanPrompt;
    private String before;
    private String after;
    private String explanation;
    private List<String> afterHighlights;

    public ExpressionItem(
        String type, String koreanPrompt, String before, String after, String explanation) {
      this(type, koreanPrompt, before, after, explanation, null);
    }

    public ExpressionItem(
        String type,
        String koreanPrompt,
        String before,
        String after,
        String explanation,
        List<String> afterHighlights) {
      this.type = type;
      this.koreanPrompt = koreanPrompt;
      this.before = before;
      this.after = after;
      this.explanation = explanation;
      this.afterHighlights = afterHighlights;
    }

    public String getType() {
      return type;
    }

    public String getKoreanPrompt() {
      return koreanPrompt;
    }

    public String getBefore() {
      return before;
    }

    public String getAfter() {
      return after;
    }

    public String getExplanation() {
      return explanation;
    }

    public List<String> getAfterHighlights() {
      return afterHighlights;
    }

    public void setAfterHighlights(List<String> afterHighlights) {
      this.afterHighlights = afterHighlights;
    }
  }

  public static class WordItem {
    private String english;
    private String korean;
    private String exampleEnglish;
    private String exampleKorean;

    public WordItem(String english, String korean, String exampleEnglish, String exampleKorean) {
      this.english = english;
      this.korean = korean;
      this.exampleEnglish = exampleEnglish;
      this.exampleKorean = exampleKorean;
    }

    public String getEnglish() {
      return english;
    }

    public String getKorean() {
      return korean;
    }

    public String getExampleEnglish() {
      return exampleEnglish;
    }

    public String getExampleKorean() {
      return exampleKorean;
    }
  }

  public static class SentenceItem {
    private String english;
    private String korean;

    public SentenceItem(String english, String korean) {
      this.english = english;
      this.korean = korean;
    }

    public String getEnglish() {
      return english;
    }

    public String getKorean() {
      return korean;
    }
  }
}
