package com.jjundev.oneclickeng.learning.dialoguelearning.summary;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SessionSummaryBinderScrollTargetTest {

  @Test
  public void calculateCenteredScrollTargetY_returnsCenteredTargetInNormalCase() {
    int targetY = SessionSummaryBinder.calculateCenteredScrollTargetY(900, 40, 1000, 3000);

    assertEquals(420, targetY);
  }

  @Test
  public void calculateCenteredScrollTargetY_clampsToZeroWhenDesiredIsNegative() {
    int targetY = SessionSummaryBinder.calculateCenteredScrollTargetY(100, 40, 500, 2000);

    assertEquals(0, targetY);
  }

  @Test
  public void calculateCenteredScrollTargetY_clampsToScrollRangeWhenDesiredExceedsMax() {
    int targetY = SessionSummaryBinder.calculateCenteredScrollTargetY(1900, 60, 500, 2100);

    assertEquals(1600, targetY);
  }

  @Test
  public void calculateCenteredScrollTargetY_returnsZeroWhenContentFitsViewport() {
    int targetY = SessionSummaryBinder.calculateCenteredScrollTargetY(500, 40, 800, 700);

    assertEquals(0, targetY);
  }
}
