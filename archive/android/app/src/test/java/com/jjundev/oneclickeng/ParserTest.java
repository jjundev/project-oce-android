package com.jjundev.oneclickeng;

import static org.junit.Assert.*;

import com.jjundev.oneclickeng.tool.IncrementalDialogueScriptParser;
import org.junit.Test;

public class ParserTest {
  @Test
  public void testParser() {
    IncrementalDialogueScriptParser parser = new IncrementalDialogueScriptParser();
    IncrementalDialogueScriptParser.ParseUpdate update1 =
        parser.addChunk("{\n  \"topic\": \"테스트 토픽 안녕하세요\",\n  \"");
    assertNotNull(update1.getMetadata());
    assertEquals("테스트 토픽 안녕하세요", update1.getMetadata().getTopic());
    assertEquals("AI Coach", update1.getMetadata().getOpponentName());
    assertEquals("female", update1.getMetadata().getOpponentGender());

    IncrementalDialogueScriptParser.ParseUpdate update2 =
        parser.addChunk("opponent_name\": \"John\",\n  \"opponent_gender\": ");
    assertNotNull(update2.getMetadata());
    assertEquals("테스트 토픽 안녕하세요", update2.getMetadata().getTopic());
    assertEquals("John", update2.getMetadata().getOpponentName());
    assertEquals("female", update2.getMetadata().getOpponentGender());

    IncrementalDialogueScriptParser.ParseUpdate update3 =
        parser.addChunk("\"male\",\n  \"opponent_role\": \"Friend\",\n  \"script\": []\n}");
    assertNotNull(update3.getMetadata());
    assertEquals("테스트 토픽 안녕하세요", update3.getMetadata().getTopic());
    assertEquals("John", update3.getMetadata().getOpponentName());
    assertEquals("male", update3.getMetadata().getOpponentGender());
  }
}
