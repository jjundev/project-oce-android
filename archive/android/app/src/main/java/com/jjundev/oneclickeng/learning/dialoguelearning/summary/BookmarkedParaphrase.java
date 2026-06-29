package com.jjundev.oneclickeng.learning.dialoguelearning.summary;

/** Session-scoped bookmarked paraphrasing sentence. */
public class BookmarkedParaphrase {
  private int level;
  private String label;
  private String sentence;
  private String sentenceTranslation;
  private long bookmarkedAt;

  public BookmarkedParaphrase() {}

  public BookmarkedParaphrase(
      int level, String label, String sentence, String sentenceTranslation, long bookmarkedAt) {
    this.level = level;
    this.label = label;
    this.sentence = sentence;
    this.sentenceTranslation = sentenceTranslation;
    this.bookmarkedAt = bookmarkedAt;
  }

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

  public long getBookmarkedAt() {
    return bookmarkedAt;
  }

  public void setBookmarkedAt(long bookmarkedAt) {
    this.bookmarkedAt = bookmarkedAt;
  }
}
