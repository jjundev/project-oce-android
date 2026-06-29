package com.jjundev.oneclickeng.learning.dialoguelearning.model;

import java.util.ArrayList;
import java.util.List;

/** Aggregate model for quiz request seed and normalized quiz payload. */
public class QuizData {

  public static class QuizSeed {
    private List<QuizSeedExpression> expressions;
    private List<QuizSeedWord> words;

    public QuizSeed() {
      this(new ArrayList<>(), new ArrayList<>());
    }

    public QuizSeed(List<QuizSeedExpression> expressions, List<QuizSeedWord> words) {
      this.expressions = expressions;
      this.words = words;
    }

    public List<QuizSeedExpression> getExpressions() {
      return expressions;
    }

    public void setExpressions(List<QuizSeedExpression> expressions) {
      this.expressions = expressions;
    }

    public List<QuizSeedWord> getWords() {
      return words;
    }

    public void setWords(List<QuizSeedWord> words) {
      this.words = words;
    }
  }

  public static class QuizSeedExpression {
    private String before;
    private String after;
    private String koreanPrompt;
    private String explanation;

    public QuizSeedExpression() {}

    public QuizSeedExpression(
        String before, String after, String koreanPrompt, String explanation) {
      this.before = before;
      this.after = after;
      this.koreanPrompt = koreanPrompt;
      this.explanation = explanation;
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

    public String getKoreanPrompt() {
      return koreanPrompt;
    }

    public void setKoreanPrompt(String koreanPrompt) {
      this.koreanPrompt = koreanPrompt;
    }

    public String getExplanation() {
      return explanation;
    }

    public void setExplanation(String explanation) {
      this.explanation = explanation;
    }
  }

  public static class QuizSeedWord {
    private String english;
    private String korean;
    private String exampleEnglish;
    private String exampleKorean;

    public QuizSeedWord() {}

    public QuizSeedWord(
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

  public static class QuizQuestion {
    private String questionMain;
    private String questionMaterial;
    private String answer;
    private List<String> choices;
    private String explanation;

    public QuizQuestion() {}

    public QuizQuestion(String questionMain, String answer) {
      this(questionMain, null, answer, null, null);
    }

    public QuizQuestion(
        String questionMain,
        String questionMaterial,
        String answer,
        List<String> choices,
        String explanation) {
      this.questionMain = questionMain;
      this.questionMaterial = questionMaterial;
      this.answer = answer;
      this.choices = choices;
      this.explanation = explanation;
    }

    public String getQuestionMain() {
      return questionMain;
    }

    public void setQuestionMain(String questionMain) {
      this.questionMain = questionMain;
    }

    public String getQuestionMaterial() {
      return questionMaterial;
    }

    public void setQuestionMaterial(String questionMaterial) {
      this.questionMaterial = questionMaterial;
    }

    public String getAnswer() {
      return answer;
    }

    public void setAnswer(String answer) {
      this.answer = answer;
    }

    public List<String> getChoices() {
      return choices;
    }

    public void setChoices(List<String> choices) {
      this.choices = choices;
    }

    public String getExplanation() {
      return explanation;
    }

    public void setExplanation(String explanation) {
      this.explanation = explanation;
    }
  }
}
