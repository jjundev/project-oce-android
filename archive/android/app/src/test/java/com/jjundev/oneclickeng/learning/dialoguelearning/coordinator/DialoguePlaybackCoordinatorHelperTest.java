package com.jjundev.oneclickeng.learning.dialoguelearning.coordinator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DialoguePlaybackCoordinatorHelperTest {

  @Test
  public void shouldUseGeminiProvider_returnsTrueOnlyForGoogle() {
    assertTrue(DialoguePlaybackCoordinator.shouldUseGeminiProvider("google"));
    assertFalse(DialoguePlaybackCoordinator.shouldUseGeminiProvider("android"));
    assertFalse(DialoguePlaybackCoordinator.shouldUseGeminiProvider(null));
  }

  @Test
  public void shouldAttemptGeminiSynthesis_requiresProviderAndManager() {
    assertTrue(DialoguePlaybackCoordinator.shouldAttemptGeminiSynthesis("google", true));
    assertFalse(DialoguePlaybackCoordinator.shouldAttemptGeminiSynthesis("google", false));
    assertFalse(DialoguePlaybackCoordinator.shouldAttemptGeminiSynthesis("android", true));
  }

  @Test
  public void resolveGeminiVoiceName_mapsByGenderAndDefaultsToKore() {
    assertEquals("Puck", DialoguePlaybackCoordinator.resolveGeminiVoiceName("male"));
    assertEquals("Kore", DialoguePlaybackCoordinator.resolveGeminiVoiceName("female"));
    assertEquals("Kore", DialoguePlaybackCoordinator.resolveGeminiVoiceName(null));
  }
}
