package com.jjundev.oneclickeng.learning.dialoguelearning.model;

import java.util.List;

/** A sentence with styled segments for rendering with SpannableString */
public class StyledSentence {
  private List<TextSegment> segments;

  public List<TextSegment> getSegments() {
    return segments;
  }

  public void setSegments(List<TextSegment> segments) {
    this.segments = segments;
  }
}
