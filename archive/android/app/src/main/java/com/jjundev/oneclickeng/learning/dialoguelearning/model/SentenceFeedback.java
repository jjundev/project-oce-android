package com.jjundev.oneclickeng.learning.dialoguelearning.model;

import java.util.List;

/**
 * API response model for Sentence Feedback Contains writing score, grammar, conceptual bridge,
 * naturalness, tone style, and paraphrasing data
 */
public class SentenceFeedback {
  private WritingScore writingScore;
  private GrammarFeedback grammar;
  private ConceptualBridge conceptualBridge;
  private NaturalnessFeedback naturalness;
  private ToneStyle toneStyle;
  private List<ParaphrasingLevel> paraphrasing;
  private String userSentence;
  private String originalSentence;

  public WritingScore getWritingScore() {
    return writingScore;
  }

  public void setWritingScore(WritingScore writingScore) {
    this.writingScore = writingScore;
  }

  public GrammarFeedback getGrammar() {
    return grammar;
  }

  public void setGrammar(GrammarFeedback grammar) {
    this.grammar = grammar;
  }

  public ConceptualBridge getConceptualBridge() {
    return conceptualBridge;
  }

  public void setConceptualBridge(ConceptualBridge conceptualBridge) {
    this.conceptualBridge = conceptualBridge;
  }

  public NaturalnessFeedback getNaturalness() {
    return naturalness;
  }

  public void setNaturalness(NaturalnessFeedback naturalness) {
    this.naturalness = naturalness;
  }

  public ToneStyle getToneStyle() {
    return toneStyle;
  }

  public void setToneStyle(ToneStyle toneStyle) {
    this.toneStyle = toneStyle;
  }

  public List<ParaphrasingLevel> getParaphrasing() {
    return paraphrasing;
  }

  public void setParaphrasing(List<ParaphrasingLevel> paraphrasing) {
    this.paraphrasing = paraphrasing;
  }

  public String getUserSentence() {
    return userSentence;
  }

  public void setUserSentence(String userSentence) {
    this.userSentence = userSentence;
  }

  public String getOriginalSentence() {
    return originalSentence;
  }

  public void setOriginalSentence(String originalSentence) {
    this.originalSentence = originalSentence;
  }
}
