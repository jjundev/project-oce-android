package com.jjundev.oneclickeng.summary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.jjundev.oneclickeng.learning.dialoguelearning.model.QuizData;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import com.jjundev.oneclickeng.manager_gemini.QuizGenerateManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class QuizGenerateManagerTest {

  @Test
  public void parseQuizQuestionsPayload_capsAtFive() {
    String payload =
        "{\"questions\":["
            + "{\"question_main\":\"Q1\",\"answer\":\"A1\"},"
            + "{\"question_main\":\"Q2\",\"answer\":\"A2\"},"
            + "{\"question_main\":\"Q3\",\"answer\":\"A3\"},"
            + "{\"question_main\":\"Q4\",\"answer\":\"A4\"},"
            + "{\"question_main\":\"Q5\",\"answer\":\"A5\"},"
            + "{\"question_main\":\"Q6\",\"answer\":\"A6\"}"
            + "]}";

    QuizGenerateManager.ParseResult result =
        QuizGenerateManager.parseQuizQuestionsPayload(payload, 5);

    assertEquals(5, result.getQuestions().size());
    assertTrue(result.isCapped());
    assertEquals(6, result.getValidQuestionCount());
    assertEquals("Q1", result.getQuestions().get(0).getQuestionMain());
    assertEquals("Q5", result.getQuestions().get(4).getQuestionMain());
  }

  @Test
  public void parseQuizQuestionsPayload_filtersInvalidAndDeduplicates() {
    String payload =
        "{\"questions\":["
            + "{\"question_main\":\"\",\"answer\":\"A\"},"
            + "{\"question_main\":\"Valid\",\"question_material\":\"   \",\"answer\":\"Answer\",\"choices\":[\" A \",\"A\",\"B\",\"\"],\"explanation\":\"   \"},"
            + "{\"question_main\":\" valid \",\"question_material\":null,\"answer\":\"Duplicate\"},"
            + "{\"question_main\":\"NoAnswer\"}"
            + "]}";

    QuizGenerateManager.ParseResult result =
        QuizGenerateManager.parseQuizQuestionsPayload(payload, 5);

    assertEquals(1, result.getQuestions().size());
    assertFalse(result.isCapped());
    assertEquals(1, result.getValidQuestionCount());

    QuizData.QuizQuestion question = result.getQuestions().get(0);
    assertEquals("Valid", question.getQuestionMain());
    assertNull(question.getQuestionMaterial());
    assertEquals("Answer", question.getAnswer());
    assertNotNull(question.getChoices());
    assertEquals(Arrays.asList("A", "B"), question.getChoices());
    assertNull(question.getExplanation());
  }

  @Test
  public void parseQuizQuestionsPayload_rejectsLegacyQuestionFieldStrictly() {
    String payload = "{\"questions\":[{\"question\":\"legacy\",\"answer\":\"A1\"}]}";

    QuizGenerateManager.ParseResult result =
        QuizGenerateManager.parseQuizQuestionsPayload(payload, 5);

    assertTrue(result.getQuestions().isEmpty());
    assertFalse(result.isCapped());
    assertEquals(0, result.getValidQuestionCount());
  }

  @Test
  public void parseQuizQuestionsPayload_normalizesBlankQuestionMaterialToNull() {
    String payload =
        "{\"questions\":[{\"question_main\":\"Valid\",\"question_material\":\"   \",\"answer\":\"A1\"}]}";

    QuizGenerateManager.ParseResult result =
        QuizGenerateManager.parseQuizQuestionsPayload(payload, 5);

    assertEquals(1, result.getQuestions().size());
    assertNull(result.getQuestions().get(0).getQuestionMaterial());
  }

  @Test
  public void parseQuizQuestionsPayload_returnsEmptyOnMalformedJson() {
    QuizGenerateManager.ParseResult result =
        QuizGenerateManager.parseQuizQuestionsPayload("{not-json", 5);

    assertTrue(result.getQuestions().isEmpty());
    assertFalse(result.isCapped());
    assertEquals(0, result.getValidQuestionCount());
  }

  @Test
  public void buildQuizSeed_usesExpressionsAndWordsFromSummary() {
    SummaryData summaryData = new SummaryData();

    List<SummaryData.ExpressionItem> expressions = new ArrayList<>();
    expressions.add(
        new SummaryData.ExpressionItem("precise", "프롬프트", "before text", "after text", "explain"));
    expressions.add(
        new SummaryData.ExpressionItem(
            "precise", "프롬프트2", " before text ", " after text ", "duplicate"));
    expressions.add(new SummaryData.ExpressionItem("precise", "empty", null, null, "skip"));
    summaryData.setExpressions(expressions);

    List<SummaryData.WordItem> words = new ArrayList<>();
    words.add(new SummaryData.WordItem("context", "맥락", null, null));
    words.add(new SummaryData.WordItem(" context ", "중복", "example", "예시"));
    words.add(new SummaryData.WordItem(null, "누락", null, null));
    summaryData.setWords(words);

    QuizData.QuizSeed seed = QuizGenerateManager.buildQuizSeed(summaryData);

    assertEquals(1, seed.getExpressions().size());
    assertEquals(1, seed.getWords().size());

    QuizData.QuizSeedExpression expression = seed.getExpressions().get(0);
    assertEquals("before text", expression.getBefore());
    assertEquals("after text", expression.getAfter());

    QuizData.QuizSeedWord word = seed.getWords().get(0);
    assertEquals("context", word.getEnglish());
    assertEquals("맥락", word.getKorean());
    assertEquals("context", word.getExampleEnglish());
    assertEquals("맥락", word.getExampleKorean());
  }
}
