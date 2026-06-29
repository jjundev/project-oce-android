package com.jjundev.oneclickeng.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class IncrementalDialogueScriptParserTest {

  @Test
  public void addChunk_extractsSnakeCaseMetadataAndTurnsIncrementally() {
    IncrementalDialogueScriptParser parser = new IncrementalDialogueScriptParser();

    IncrementalDialogueScriptParser.ParseUpdate first =
        parser.addChunk(
            "{\"topic\":\"인사\",\"opponent_name\":\"Coach\",\"opponent_gender\":\"male\","
                + "\"script\":[{\"ko\":\"안녕하세요\",\"en\":\"Hello\",\"role\":\"model\"},");
    assertNotNull(first.getMetadata());
    assertEquals("인사", first.getMetadata().getTopic());
    assertEquals("Coach", first.getMetadata().getOpponentName());
    assertEquals("male", first.getMetadata().getOpponentGender());
    assertEquals(1, first.getCompletedTurnObjects().size());

    IncrementalDialogueScriptParser.ParseUpdate second =
        parser.addChunk("{\"ko\":\"반가워요\",\"en\":\"Nice to meet you\",\"role\":\"user\"}]}");
    assertNull(second.getMetadata());
    assertEquals(1, second.getCompletedTurnObjects().size());
  }

  @Test
  public void addChunk_supportsCamelCaseMetadataKeys() {
    IncrementalDialogueScriptParser parser = new IncrementalDialogueScriptParser();

    IncrementalDialogueScriptParser.ParseUpdate update =
        parser.addChunk(
            "{\"topic\":\"공항 체크인\",\"opponentName\":\"Agent\",\"opponentGender\":\"female\","
                + "\"script\":[]}");

    assertNotNull(update.getMetadata());
    assertEquals("공항 체크인", update.getMetadata().getTopic());
    assertEquals("Agent", update.getMetadata().getOpponentName());
    assertEquals("female", update.getMetadata().getOpponentGender());
  }

  @Test
  public void addChunk_emitsTopicFirst_thenEmitsMetadataUpdateWhenDetailsArrive() {
    IncrementalDialogueScriptParser parser = new IncrementalDialogueScriptParser();

    IncrementalDialogueScriptParser.ParseUpdate first = parser.addChunk("{\"topic\":\"카페 주문\",");
    assertNotNull(first.getMetadata());
    assertEquals("카페 주문", first.getMetadata().getTopic());
    assertEquals("AI Coach", first.getMetadata().getOpponentName());
    assertEquals("female", first.getMetadata().getOpponentGender());

    IncrementalDialogueScriptParser.ParseUpdate second = parser.addChunk("\"opponent_name\":\"Barista\",");
    assertNotNull(second.getMetadata());
    assertEquals("카페 주문", second.getMetadata().getTopic());
    assertEquals("Barista", second.getMetadata().getOpponentName());
    assertEquals("female", second.getMetadata().getOpponentGender());

    IncrementalDialogueScriptParser.ParseUpdate third =
        parser.addChunk("\"opponent_gender\":\"male\",\"script\":[]}");
    assertNotNull(third.getMetadata());
    assertEquals("카페 주문", third.getMetadata().getTopic());
    assertEquals("Barista", third.getMetadata().getOpponentName());
    assertEquals("male", third.getMetadata().getOpponentGender());
  }

  @Test
  public void addChunk_doesNotReEmitIdenticalMetadata() {
    IncrementalDialogueScriptParser parser = new IncrementalDialogueScriptParser();

    IncrementalDialogueScriptParser.ParseUpdate first =
        parser.addChunk(
            "{\"topic\":\"미팅\",\"opponent_name\":\"Manager\",\"opponent_gender\":\"female\",");
    assertNotNull(first.getMetadata());

    IncrementalDialogueScriptParser.ParseUpdate second = parser.addChunk("\"script\":[]}");
    assertNull(second.getMetadata());
  }
}
