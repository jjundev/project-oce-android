package com.jjundev.oneclickeng.learning.dialoguelearning.model;

/** A tone level with optional label and sentence */
public class ToneLevel {
  private int level;
  private String label;
  private String sentence;
  private String sentenceTranslation;

  public int getLevel() {
    return level;
  }

  public void setLevel(int level) {
    this.level = level;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getSentence() {
    return sentence;
  }

  public void setSentence(String sentence) {
    this.sentence = sentence;
  }

  public String getSentenceTranslation() {
    return sentenceTranslation;
  }

  public void setSentenceTranslation(String sentenceTranslation) {
    this.sentenceTranslation = sentenceTranslation;
  }
}
