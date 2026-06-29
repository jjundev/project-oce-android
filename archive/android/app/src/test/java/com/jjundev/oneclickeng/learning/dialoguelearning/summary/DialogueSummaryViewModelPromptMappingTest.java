package com.jjundev.oneclickeng.learning.dialoguelearning.summary;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISessionSummaryLlmManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DialogueSummaryViewModelPromptMappingTest {

  private static final Gson GSON = new Gson();

  @Test
  public void initialize_usesSourcePromptWhenLlmPromptIsWrong() {
    List<ISessionSummaryLlmManager.FilteredExpression> expressions = new ArrayList<>();
    expressions.add(
        new ISessionSummaryLlmManager.FilteredExpression(
            "정확한 표현", "피드백 범주", "I goed home.", "I went home.", "동사의 과거형을 맞춰야 해요."));
    DialogueSummaryViewModel viewModel =
        new DialogueSummaryViewModel(new FakeSessionSummaryLlmManager(expressions));

    String summaryJson = emptySummaryJson();
    String featureBundleJson =
        featureBundleJson(
            new SummaryFeatureBundle.ExpressionCandidate(
                "정확한 표현", "나는 집에 갔다.", "I goed home.", "I went home.", "설명"));

    viewModel.initialize(summaryJson, featureBundleJson, null);

    SummaryData data = viewModel.getSummaryData().getValue();
    assertNotNull(data);
    assertNotNull(data.getExpressions());
    assertEquals(1, data.getExpressions().size());
    assertEquals("나는 집에 갔다.", data.getExpressions().get(0).getKoreanPrompt());
  }

  @Test
  public void initialize_dropsExpressionWhenSourcePromptCannotBeMapped() {
    List<ISessionSummaryLlmManager.FilteredExpression> expressions = new ArrayList<>();
    expressions.add(
        new ISessionSummaryLlmManager.FilteredExpression(
            "정확한 표현",
            "피드백 범주",
            "Completely different before.",
            "Completely different after.",
            "설명"));
    DialogueSummaryViewModel viewModel =
        new DialogueSummaryViewModel(new FakeSessionSummaryLlmManager(expressions));

    String summaryJson = emptySummaryJson();
    String featureBundleJson =
        featureBundleJson(
            new SummaryFeatureBundle.ExpressionCandidate(
                "정확한 표현", "원문 문장", "I goed home.", "I went home.", "설명"));

    viewModel.initialize(summaryJson, featureBundleJson, null);

    SummaryData data = viewModel.getSummaryData().getValue();
    assertNotNull(data);
    assertNotNull(data.getExpressions());
    assertTrue(data.getExpressions().isEmpty());
  }

  private String emptySummaryJson() {
    SummaryData data = new SummaryData();
    data.setTotalScore(0);
    data.setHighlights(new ArrayList<>());
    data.setExpressions(new ArrayList<>());
    data.setWords(new ArrayList<>());
    data.setLikedSentences(new ArrayList<>());
    return GSON.toJson(data);
  }

  private String featureBundleJson(SummaryFeatureBundle.ExpressionCandidate candidate) {
    SummaryFeatureBundle bundle = new SummaryFeatureBundle();
    List<SummaryFeatureBundle.ExpressionCandidate> candidates = new ArrayList<>();
    candidates.add(candidate);
    bundle.setExpressionCandidates(candidates);
    bundle.setWordCandidates(new ArrayList<>());
    bundle.setSentenceCandidates(new ArrayList<>());
    bundle.setUserOriginalSentences(new ArrayList<>());
    return GSON.toJson(bundle);
  }

  private static final class FakeSessionSummaryLlmManager implements ISessionSummaryLlmManager {
    private final List<FilteredExpression> expressions;

    private FakeSessionSummaryLlmManager(List<FilteredExpression> expressions) {
      this.expressions = expressions;
    }

    @Override
    public void extractWordsFromSentencesAsync(
        List<String> words,
        List<String> sentences,
        List<String> userOriginalSentences,
        WordExtractionCallback callback) {
      callback.onSuccess(new ArrayList<>());
    }

    @Override
    public void filterExpressionsAsync(
        SummaryFeatureBundle bundle, ExpressionFilterCallback callback) {
      for (FilteredExpression expression : expressions) {
        callback.onExpressionReceived(expression);
      }
      callback.onComplete();
    }
  }
}
