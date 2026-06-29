package com.jjundev.oneclickeng.learning.dialoguelearning.model;

/**
 * A segment of text with style type Types: - normal: regular text (black) - incorrect: wrong part
 * (red + strikethrough) - correction: correct expression (green + bold) - highlight: highlighted
 * part (background highlight)
 */
public class TextSegment {
  public static final String TYPE_NORMAL = "normal";
  public static final String TYPE_INCORRECT = "incorrect";
  public static final String TYPE_CORRECTION = "correction";
  public static final String TYPE_HIGHLIGHT = "highlight";

  private String text;
  private String type;

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public boolean isNormal() {
    return TYPE_NORMAL.equals(type);
  }

  public boolean isIncorrect() {
    return TYPE_INCORRECT.equals(type);
  }

  public boolean isCorrection() {
    return TYPE_CORRECTION.equals(type);
  }

  public boolean isHighlight() {
    return TYPE_HIGHLIGHT.equals(type);
  }
}
