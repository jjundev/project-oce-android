package com.jjundev.oneclickeng.learning.dialoguelearning.model;

/** Grammar feedback with corrected sentence and explanation */
public class GrammarFeedback {
  private StyledSentence correctedSentence;
  private String explanation;

  public StyledSentence getCorrectedSentence() {
    return correctedSentence;
  }

  public void setCorrectedSentence(StyledSentence correctedSentence) {
    this.correctedSentence = correctedSentence;
  }

  public String getExplanation() {
    return explanation;
  }

  public void setExplanation(String explanation) {
    this.explanation = explanation;
  }
}
