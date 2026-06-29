package com.jjundev.oneclickeng.learning.dialoguelearning.model;

/** Venn diagram data with two circles and intersection */
public class VennDiagram {
  private VennCircle leftCircle;
  private VennCircle rightCircle;
  private VennIntersection intersection;

  public VennCircle getLeftCircle() {
    return leftCircle;
  }

  public void setLeftCircle(VennCircle leftCircle) {
    this.leftCircle = leftCircle;
  }

  public VennCircle getRightCircle() {
    return rightCircle;
  }

  public void setRightCircle(VennCircle rightCircle) {
    this.rightCircle = rightCircle;
  }

  public VennIntersection getIntersection() {
    return intersection;
  }

  public void setIntersection(VennIntersection intersection) {
    this.intersection = intersection;
  }
}
