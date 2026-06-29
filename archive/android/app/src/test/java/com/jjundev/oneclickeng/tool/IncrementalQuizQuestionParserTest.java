package com.jjundev.oneclickeng.tool;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.Test;

public class IncrementalQuizQuestionParserTest {

  @Test
  public void addChunk_extractsObjectsAcrossChunkBoundaries() {
    IncrementalQuizQuestionParser parser = new IncrementalQuizQuestionParser();

    List<String> first =
        parser.addChunk("{\"questions\":[{\"question_main\":\"Q1\",\"answer\":\"A1\"},");
    List<String> second = parser.addChunk("{\"question_main\":\"Q2\",\"answer\":\"A2\"");
    List<String> third = parser.addChunk("}]}");

    assertEquals(1, first.size());
    assertTrue(second.isEmpty());
    assertEquals(1, third.size());
    assertEquals("Q1", parseQuestion(first.get(0)));
    assertEquals("Q2", parseQuestion(third.get(0)));
  }

  @Test
  public void addChunk_handlesBraceCharactersInsideStrings() {
    IncrementalQuizQuestionParser parser = new IncrementalQuizQuestionParser();

    List<String> questions =
        parser.addChunk(
            "{\"questions\":[{\"question_main\":\"What does '{' mean?\",\"answer\":\"left brace\"}]}");

    assertEquals(1, questions.size());
    assertEquals("What does '{' mean?", parseQuestion(questions.get(0)));
  }

  @Test
  public void addChunk_doesNotEmitIncompleteObject() {
    IncrementalQuizQuestionParser parser = new IncrementalQuizQuestionParser();

    List<String> first =
        parser.addChunk("{\"questions\":[{\"question_main\":\"Q1\",\"answer\":\"A1\"");
    List<String> second = parser.addChunk("}]}");

    assertTrue(first.isEmpty());
    assertEquals(1, second.size());
    assertEquals("Q1", parseQuestion(second.get(0)));
  }

  private static String parseQuestion(String questionObject) {
    JsonObject obj = JsonParser.parseString(questionObject).getAsJsonObject();
    return obj.get("question_main").getAsString();
  }
}
