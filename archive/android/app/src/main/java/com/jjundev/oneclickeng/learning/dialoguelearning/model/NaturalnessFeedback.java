package com.jjundev.oneclickeng.learning.dialoguelearning.model;

import java.util.List;

/** Naturalness feedback with natural sentence, explanation, and reasons */
public class NaturalnessFeedback {
  private StyledSentence naturalSentence;
  private String naturalSentenceTranslation;
  private String explanation;
  private List<ReasonItem> reasons;

  public StyledSentence getNaturalSentence() {
    return naturalSentence;
  }

  public void setNaturalSentence(StyledSentence naturalSentence) {
    this.naturalSentence = naturalSentence;
  }

  public String getNaturalSentenceTranslation() {
    return naturalSentenceTranslation;
  }

  public void setNaturalSentenceTranslation(String naturalSentenceTranslation) {
    this.naturalSentenceTranslation = naturalSentenceTranslation;
  }

  public String getExplanation() {
    return explanation;
  }

  public void setExplanation(String explanation) {
    this.explanation = explanation;
  }

  public List<ReasonItem> getReasons() {
    return reasons;
  }

  public void setReasons(List<ReasonItem> reasons) {
    this.reasons = reasons;
  }
}
