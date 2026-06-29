package com.jjundev.oneclickeng.learning.dialoguelearning.model;

import android.graphics.Color;
import java.util.List;

/** Intersection area of the Venn diagram */
public class VennIntersection {
  private String color;
  private List<String> items;

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
      if (color != null) {
        return Color.parseColor(color);
      }
    } catch (Exception e) {
      // Fall through to default
    }
    return 0xFF8BC34A; // Default light green
  }
}
