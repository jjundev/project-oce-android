package com.jjundev.oneclickeng.learning.dialoguelearning.model;

/**
 * Writing score with encouragement message Score range: 0-100 Color coding: 80+ green (#4CAF50),
 * 60-79 orange (#FF9800), 0-59 red (#F44336)
 */
public class WritingScore {
  private int score;
  private String encouragementMessage;

  public int getScore() {
    return score;
  }

  public void setScore(int score) {
    this.score = score;
  }

  public String getEncouragementMessage() {
    return encouragementMessage;
  }

  public void setEncouragementMessage(String encouragementMessage) {
    this.encouragementMessage = encouragementMessage;
  }

  /**
   * Get color based on score range
   *
   * @return color int value
   */
  public int getScoreColor() {
    if (score >= 80) {
      return 0xFF4CAF50; // Green
    } else if (score >= 60) {
      return 0xFFFF9800; // Orange
    } else {
      return 0xFFF44336; // Red
    }
  }
}
