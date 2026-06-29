package com.jjundev.oneclickeng.learning.dialoguelearning.model;

import java.util.List;

/** Tone and style with slider levels (0-4) */
public class ToneStyle {
  private int defaultLevel;
  private List<ToneLevel> levels;

  public int getDefaultLevel() {
    return defaultLevel;
  }

  public void setDefaultLevel(int defaultLevel) {
    this.defaultLevel = defaultLevel;
  }

  public List<ToneLevel> getLevels() {
    return levels;
  }

  public void setLevels(List<ToneLevel> levels) {
    this.levels = levels;
  }

  /**
   * Get sentence for a specific level
   *
   * @param level 0-4
   * @return sentence or empty string if not found
   */
  public String getSentenceForLevel(int level) {
    if (levels != null) {
      for (ToneLevel toneLevel : levels) {
        if (toneLevel.getLevel() == level) {
          return toneLevel.getSentence();
        }
      }
    }
    return "";
  }

  /**
   * Get sentence translation for a specific level
   *
   * @param level 0-4
   * @return translation or empty string if not found
   */
  public String getTranslationForLevel(int level) {
    if (levels != null) {
      for (ToneLevel toneLevel : levels) {
        if (toneLevel.getLevel() == level) {
          return toneLevel.getSentenceTranslation();
        }
      }
    }
    return "";
  }
}
