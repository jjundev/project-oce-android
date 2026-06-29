package com.jjundev.oneclickeng.learning.dialoguelearning.model;

import android.graphics.Color;
import java.util.List;

/** A circle in the Venn diagram with word label, color, and items */
public class VennCircle {
  private String word;
  private String color;
  private List<String> items;

  public String getWord() {
    return word;
  }

  public void setWord(String word) {
    this.word = word;
  }

  public String getColor() {
    return color;
  }

  public void setColor(String color) {
    this.color = color;
  }

  public List<String> getItems() {
    return items;
  }

  public void setItems(List<String> items) {
    this.items = items;
  }

  /**
   * Parse hex color string to int
   *
   * @return color int value
   */
  public int getColorInt() {
    try {
      return Color.parseColor(color);
    } catch (Exception e) {
      return Color.GRAY;
    }
  }
}
