package com.jjundev.oneclickeng.learning.dialoguelearning.summary;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SessionSummaryManagerAfterSanitizationTest {

  @Test
  public void sanitizeExpressionAfterForDisplay_removesDoubleBracketMarkers() {
    String sanitized =
        SessionSummaryManager.sanitizeExpressionAfterForDisplay(
            "After: I [[really look forward to]] hearing from you.");

    assertEquals("I really look forward to hearing from you.", sanitized);
  }

  @Test
  public void sanitizeExpressionAfterForDisplay_removesSingleBracketMarkers() {
    String sanitized =
        SessionSummaryManager.sanitizeExpressionAfterForDisplay("I am [very excited] today!");

    assertEquals("I am very excited today!", sanitized);
  }

  @Test
  public void sanitizeExpressionAfterForDisplay_removesUnmatchedBrackets() {
    String sanitized =
        SessionSummaryManager.sanitizeExpressionAfterForDisplay("I am ]so [ready for this.");

    assertEquals("I am so ready for this.", sanitized);
  }

  @Test
  public void sanitizeExpressionAfterForDisplay_removesDisallowedSpecialCharacters() {
    String sanitized =
        SessionSummaryManager.sanitizeExpressionAfterForDisplay(
            "After: I @#$%^&* love this [] idea.");

    assertEquals("I love this idea.", sanitized);
  }

  @Test
  public void sanitizeExpressionAfterForDisplay_keepsAllowedPunctuation() {
    String sanitized =
        SessionSummaryManager.sanitizeExpressionAfterForDisplay(
            "After: I'm ready, really! \"Yes\" (today): go-now; fine?");

    assertEquals("I'm ready, really! \"Yes\" (today): go-now; fine?", sanitized);
  }
}
