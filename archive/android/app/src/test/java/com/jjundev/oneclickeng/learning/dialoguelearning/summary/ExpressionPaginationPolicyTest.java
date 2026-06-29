package com.jjundev.oneclickeng.learning.dialoguelearning.summary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ExpressionPaginationPolicyTest {

  @Test
  public void resolveRequestedVisibleCount_defaultsToPageSize() {
    int requested = ExpressionPaginationPolicy.resolveRequestedVisibleCount(null, null, 6);

    assertEquals(3, requested);
  }

  @Test
  public void nextRequestedVisibleCountAfterClick_expandsByPageAndCollapsesAtEnd() {
    int requested = 3;

    requested = ExpressionPaginationPolicy.nextRequestedVisibleCountAfterClick(requested, 10);
    assertEquals(6, requested);

    requested = ExpressionPaginationPolicy.nextRequestedVisibleCountAfterClick(requested, 10);
    assertEquals(9, requested);

    requested = ExpressionPaginationPolicy.nextRequestedVisibleCountAfterClick(requested, 10);
    assertEquals(10, requested);

    requested = ExpressionPaginationPolicy.nextRequestedVisibleCountAfterClick(requested, 10);
    assertEquals(3, requested);
  }

  @Test
  public void resolveRequestedVisibleCount_keepsRequestedCountWhenDataAppends() {
    int requested = ExpressionPaginationPolicy.resolveRequestedVisibleCount(6, 6, 7);

    assertEquals(6, requested);
  }

  @Test
  public void resolveRequestedVisibleCount_resetsWhenDatasetShrinks() {
    int requested = ExpressionPaginationPolicy.resolveRequestedVisibleCount(10, 10, 2);

    assertEquals(2, requested);
  }

  @Test
  public void computeToggleText_matchesExpandAndCollapseLabels() {
    assertEquals("더 보기 (1)", ExpressionPaginationPolicy.computeToggleText(9, 10));
    assertEquals("접기", ExpressionPaginationPolicy.computeToggleText(10, 10));
    assertNull(ExpressionPaginationPolicy.computeToggleText(3, 3));
  }
}
