package com.jjundev.oneclickeng.learning.dialoguelearning.summary;

import java.util.ArrayList;
import java.util.List;

/** Compact feature set for one-shot summary LLM generation. */
public class SummaryFeatureBundle {
  private int totalScore;
  private List<HighlightCandidate> highlightCandidates = new ArrayList<>();
  private List<ExpressionCandidate> expressionCandidates = new ArrayList<>();
  private List<WordCandidate> wordCandidates = new ArrayList<>();
  private List<String> sentenceCandidates = new ArrayList<>();
  private List<String> userOriginalSentences = new ArrayList<>();

  public int getTotalScore() {
    return totalScore;
  }

  public void setTotalScore(int totalScore) {
    this.totalScore = totalScore;
  }

  public List<HighlightCandidate> getHighlightCandidates() {
    return highlightCandidates;
  }

  public void setHighlightCandidates(List<HighlightCandidate> highlightCandidates) {
    this.highlightCandidates = highlightCandidates;
  }

  public List<ExpressionCandidate> getExpressionCandidates() {
    return expressionCandidates;
  }

  public void setExpressionCandidates(List<ExpressionCandidate> expressionCandidates) {
    this.expressionCandidates = expressionCandidates;
  }

  public List<WordCandidate> getWordCandidates() {
    return wordCandidates;
  }

  public void setWordCandidates(List<WordCandidate> wordCandidates) {
    this.wordCandidates = wordCandidates;
  }

  public List<String> getSentenceCandidates() {
    return sentenceCandidates;
  }

  public void setSentenceCandidates(List<String> sentenceCandidates) {
    this.sentenceCandidates = sentenceCandidates;
  }

  public List<String> getUserOriginalSentences() {
    return userOriginalSentences;
  }

  public void setUserOriginalSentences(List<String> userOriginalSentences) {
    this.userOriginalSentences = userOriginalSentences;
  }

  public static class HighlightCandidate {
    private String english;
    private String korean;
    private String reason;

    public HighlightCandidate() {}

    public HighlightCandidate(String english, String korean, String reason) {
      this.english = english;
      this.korean = korean;
      this.reason = reason;
    }

    public String getEnglish() {
      return english;
    }

    public void setEnglish(String english) {
      this.english = english;
    }

    public String getKorean() {
      return korean;
    }

    public void setKorean(String korean) {
      this.korean = korean;
    }

    public String getReason() {
      return reason;
    }

    public void setReason(String reason) {
      this.reason = reason;
    }
  }

  public static class ExpressionCandidate {
    private String type;
    private String koreanPrompt;
    private String before;
    private String after;
    private String explanation;

    public ExpressionCandidate() {}

    public ExpressionCandidate(
        String type, String koreanPrompt, String before, String after, String explanation) {
      this.type = type;
      this.koreanPrompt = koreanPrompt;
      this.before = before;
      this.after = after;
      this.explanation = explanation;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getKoreanPrompt() {
      return koreanPrompt;
    }

    public void setKoreanPrompt(String koreanPrompt) {
      this.koreanPrompt = koreanPrompt;
    }

    public String getBefore() {
      return before;
    }

    public void setBefore(String before) {
      this.before = before;
    }

    public String getAfter() {
      return after;
    }

    public void setAfter(String after) {
      this.after = after;
    }

    public String getExplanation() {
      return explanation;
    }

    public void setExplanation(String explanation) {
      this.explanation = explanation;
    }
  }

  public static class WordCandidate {
    private String english;
    private String korean;
    private String exampleEnglish;
    private String exampleKorean;

    public WordCandidate() {}

    public WordCandidate(
        String english, String korean, String exampleEnglish, String exampleKorean) {
      this.english = english;
      this.korean = korean;
      this.exampleEnglish = exampleEnglish;
      this.exampleKorean = exampleKorean;
    }

    public String getEnglish() {
      return english;
    }

    public void setEnglish(String english) {
      this.english = english;
    }

    public String getKorean() {
      return korean;
    }

    public void setKorean(String korean) {
      this.korean = korean;
    }

    public String getExampleEnglish() {
      return exampleEnglish;
    }

    public void setExampleEnglish(String exampleEnglish) {
      this.exampleEnglish = exampleEnglish;
    }

    public String getExampleKorean() {
      return exampleKorean;
    }

    public void setExampleKorean(String exampleKorean) {
      this.exampleKorean = exampleKorean;
    }
  }
}
