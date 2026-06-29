package com.jjundev.oneclickeng.manager_gemini;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.GeminiTtsAudio;
import org.junit.Test;

public class GeminiTtsManagerTest {

  @Test
  public void parseAudioFromResponseBody_parsesInlineAudioSuccessfully() {
    String body =
        "{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/L16;rate=24000\",\"data\":\"AAECAw==\"}}]}}]}";

    GeminiTtsAudio audio = GeminiTtsManager.parseAudioFromResponseBody(body);

    assertEquals(24000, audio.getSampleRateHz());
    assertEquals("audio/L16;rate=24000", audio.getMimeType());
    assertArrayEquals(new byte[] {0, 1, 2, 3}, audio.getPcmData());
  }

  @Test
  public void parseSampleRateFromMimeType_defaultsWhenRateMissing() {
    int parsed = GeminiTtsManager.parseSampleRateFromMimeType("audio/L16");

    assertEquals(24000, parsed);
  }

  @Test
  public void parseAudioFromResponseBody_defaultsSampleRateWhenMimeTypeMissingRate() {
    String body =
        "{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/L16\",\"data\":\"AAECAw==\"}}]}}]}";

    GeminiTtsAudio audio = GeminiTtsManager.parseAudioFromResponseBody(body);

    assertEquals(24000, audio.getSampleRateHz());
    assertEquals("audio/L16", audio.getMimeType());
  }

  @Test
  public void parseAudioFromResponseBody_throwsWhenCandidatesMissing() {
    String body = "{\"promptFeedback\":{}}";

    assertThrows(
        IllegalStateException.class, () -> GeminiTtsManager.parseAudioFromResponseBody(body));
  }

  @Test
  public void parseAudioFromResponseBody_throwsWhenInlineDataMissing() {
    String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"no audio\"}]}}]}";

    assertThrows(
        IllegalStateException.class, () -> GeminiTtsManager.parseAudioFromResponseBody(body));
  }

  @Test
  public void parseAudioFromResponseBody_throwsWhenBase64Invalid() {
    String body =
        "{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/L16;rate=24000\",\"data\":\"not_base64@@\"}}]}}]}";

    assertThrows(
        IllegalStateException.class, () -> GeminiTtsManager.parseAudioFromResponseBody(body));
  }
}
