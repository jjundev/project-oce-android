package com.jjundev.oneclickeng.learning.dialoguelearning.summary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.jjundev.oneclickeng.learning.dialoguelearning.model.ConceptualBridge;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.GrammarFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SentenceFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.StyledSentence;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.TextSegment;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.VennCircle;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.VennDiagram;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.WritingScore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class SessionSummaryGeneratorTest {

  @Test
  public void buildSeed_expressionPromptPrefersOriginalSentenceOverLiteralTranslation() {
    SessionSummaryGenerator generator = new SessionSummaryGenerator();
    SentenceFeedback feedback = new SentenceFeedback();
    feedback.setOriginalSentence("그는 어제 학교에 갔다.");
    feedback.setUserSentence("He go to school yesterday.");

    WritingScore writingScore = new WritingScore();
    writingScore.setScore(82);
    feedback.setWritingScore(writingScore);

    ConceptualBridge conceptualBridge = new ConceptualBridge();
    conceptualBridge.setLiteralTranslation("그는 학교에 간다.");
    feedback.setConceptualBridge(conceptualBridge);

    GrammarFeedback grammarFeedback = new GrammarFeedback();
    grammarFeedback.setCorrectedSentence(styledSentence("He went to school yesterday."));
    grammarFeedback.setExplanation("시제에 맞게 고친 문장입니다.");
    feedback.setGrammar(grammarFeedback);

    SessionSummaryGenerator.GenerationSeed seed =
        generator.buildSeed(Arrays.asList(feedback), new ArrayList<>());
    List<SummaryData.ExpressionItem> expressions = seed.getFallbackSummary().getExpressions();

    assertNotNull(expressions);
    assertFalse(expressions.isEmpty());
    assertEquals("그는 어제 학교에 갔다.", expressions.get(0).getKoreanPrompt());
  }

  @Test
  public void buildSeed_excludesScoreNinetyOrHigherFromExpressionAndWordMaterials() {
    SessionSummaryGenerator generator = new SessionSummaryGenerator();
    SentenceFeedback highScore =
        buildFeedback(90, "그녀는 곧 나타났다.", "She appear soon.", "She appeared soon.", "excludeword");

    SessionSummaryGenerator.GenerationSeed seed =
        generator.buildSeed(Arrays.asList(highScore), new ArrayList<>());

    assertNotNull(seed.getFallbackSummary());
    assertTrue(seed.getFallbackSummary().getExpressions().isEmpty());
    assertTrue(seed.getFallbackSummary().getWords().isEmpty());
    assertNotNull(seed.getFeatureBundle());
    assertTrue(seed.getFeatureBundle().getExpressionCandidates().isEmpty());
    assertTrue(seed.getFeatureBundle().getWordCandidates().isEmpty());
  }

  @Test
  public void buildSeed_keepsOnlyBelowNinetyForExpressionAndWordMaterials() {
    SessionSummaryGenerator generator = new SessionSummaryGenerator();
    SentenceFeedback lowScore =
        buildFeedback(89, "나는 집에 갔다.", "I goed home.", "I went home.", "includeword");
    SentenceFeedback highScore =
        buildFeedback(90, "그녀는 곧 나타났다.", "She appear soon.", "She appeared soon.", "excludeword");

    SessionSummaryGenerator.GenerationSeed seed =
        generator.buildSeed(Arrays.asList(lowScore, highScore), new ArrayList<>());
    SummaryData summary = seed.getFallbackSummary();

    assertNotNull(summary);
    assertNotNull(summary.getExpressions());
    assertFalse(summary.getExpressions().isEmpty());
    assertEquals("I goed home.", summary.getExpressions().get(0).getBefore());
    assertNotNull(summary.getWords());
    assertTrue(containsWord(summary.getWords(), "includeword"));
    assertFalse(containsWord(summary.getWords(), "excludeword"));
  }

  private StyledSentence styledSentence(String text) {
    TextSegment segment = new TextSegment();
    segment.setType(TextSegment.TYPE_NORMAL);
    segment.setText(text);

    StyledSentence sentence = new StyledSentence();
    sentence.setSegments(Arrays.asList(segment));
    return sentence;
  }

  private SentenceFeedback buildFeedback(
      int score,
      String originalSentence,
      String userSentence,
      String correctedSentence,
      String vennWord) {
    SentenceFeedback feedback = new SentenceFeedback();
    feedback.setOriginalSentence(originalSentence);
    feedback.setUserSentence(userSentence);

    WritingScore writingScore = new WritingScore();
    writingScore.setScore(score);
    feedback.setWritingScore(writingScore);

    GrammarFeedback grammarFeedback = new GrammarFeedback();
    grammarFeedback.setCorrectedSentence(styledSentence(correctedSentence));
    grammarFeedback.setExplanation("문장을 고쳤습니다.");
    feedback.setGrammar(grammarFeedback);

    VennCircle leftCircle = new VennCircle();
    leftCircle.setWord(vennWord);
    leftCircle.setItems(Arrays.asList(vennWord + " 의미"));
    VennDiagram vennDiagram = new VennDiagram();
    vennDiagram.setLeftCircle(leftCircle);

    ConceptualBridge conceptualBridge = new ConceptualBridge();
    conceptualBridge.setLiteralTranslation(originalSentence);
    conceptualBridge.setVennDiagram(vennDiagram);
    feedback.setConceptualBridge(conceptualBridge);

    return feedback;
  }

  private boolean containsWord(List<SummaryData.WordItem> words, String english) {
    for (SummaryData.WordItem word : words) {
      if (word != null && english.equals(word.getEnglish())) {
        return true;
      }
    }
    return false;
  }
}
