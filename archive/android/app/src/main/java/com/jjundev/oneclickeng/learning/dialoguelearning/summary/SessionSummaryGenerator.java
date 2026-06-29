package com.jjundev.oneclickeng.learning.dialoguelearning.summary;

import com.jjundev.oneclickeng.learning.dialoguelearning.model.ConceptualBridge;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.GrammarFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.NaturalnessFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.ParaphrasingLevel;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SentenceFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.StyledSentence;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.TextSegment;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.ToneStyle;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.VennCircle;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.VennDiagram;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.WritingScore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deterministic summary generator with optional LLM merge support. */
public class SessionSummaryGenerator {
  private static final int HIGHLIGHT_MIN_SCORE = 90;
  private static final int LEARNING_MATERIAL_EXCLUDE_MIN_SCORE = 90;
  private static final int MAX_HIGHLIGHTS = 1;
  private static final int MAX_FALLBACK_EXPRESSIONS = 10;
  private static final int MAX_WORDS = 12;
  private static final int MAX_LLM_CANDIDATES_HIGHLIGHTS = 10;
  private static final int MAX_LLM_CANDIDATES_WORDS = 15;

  public GenerationSeed buildSeed(
      List<SentenceFeedback> feedbacks, List<BookmarkedParaphrase> bookmarkedParaphrases) {
    List<SentenceFeedback> safeFeedbacks = feedbacks == null ? new ArrayList<>() : feedbacks;
    List<BookmarkedParaphrase> safeBookmarks =
        bookmarkedParaphrases == null ? new ArrayList<>() : bookmarkedParaphrases;

    int totalScore = calculateAverageScore(safeFeedbacks);
    boolean hasHighlightEligibleScore = hasAnyScoreAtLeast(safeFeedbacks, HIGHLIGHT_MIN_SCORE);
    List<SentenceFeedback> learningMaterialFeedbacks =
        filterLearningMaterialFeedbacks(safeFeedbacks);

    List<RankedItem<SummaryData.HighlightItem>> highlightRanked =
        buildHighlightCandidates(safeFeedbacks);
    List<RankedItem<SummaryData.ExpressionItem>> expressionRanked =
        buildExpressionCandidates(learningMaterialFeedbacks);
    List<RankedItem<SummaryData.WordItem>> wordRanked =
        buildWordCandidates(learningMaterialFeedbacks);

    List<SummaryData.HighlightItem> fallbackHighlights =
        hasHighlightEligibleScore ? topItems(highlightRanked, MAX_HIGHLIGHTS) : new ArrayList<>();
    List<SummaryData.ExpressionItem> fallbackExpressions =
        topItems(expressionRanked, MAX_FALLBACK_EXPRESSIONS);
    List<SummaryData.WordItem> fallbackWords = topItems(wordRanked, MAX_WORDS);
    List<SummaryData.SentenceItem> likedSentences = buildLikedSentences(safeBookmarks);
    List<String> sentenceCandidates = buildSentenceCandidates(safeFeedbacks, likedSentences);
    List<String> userOriginalSentences = collectUserOriginalSentences(safeFeedbacks);

    SummaryData fallback = new SummaryData();
    fallback.setTotalScore(totalScore);
    fallback.setHighlights(fallbackHighlights);
    fallback.setExpressions(fallbackExpressions);
    fallback.setWords(fallbackWords);
    fallback.setLikedSentences(likedSentences);

    SummaryFeatureBundle featureBundle = new SummaryFeatureBundle();
    featureBundle.setTotalScore(totalScore);
    featureBundle.setHighlightCandidates(
        hasHighlightEligibleScore
            ? toHighlightCandidates(topItems(highlightRanked, MAX_LLM_CANDIDATES_HIGHLIGHTS))
            : new ArrayList<>());
    featureBundle.setExpressionCandidates(toExpressionCandidates(allItems(expressionRanked)));
    featureBundle.setWordCandidates(
        toWordCandidates(topItems(wordRanked, MAX_LLM_CANDIDATES_WORDS)));
    featureBundle.setSentenceCandidates(sentenceCandidates);
    featureBundle.setUserOriginalSentences(userOriginalSentences);

    return new GenerationSeed(fallback, featureBundle);
  }

  private List<SentenceFeedback> filterLearningMaterialFeedbacks(List<SentenceFeedback> feedbacks) {
    List<SentenceFeedback> result = new ArrayList<>();
    if (feedbacks == null || feedbacks.isEmpty()) {
      return result;
    }

    for (SentenceFeedback feedback : feedbacks) {
      if (feedback == null) {
        continue;
      }
      if (safeScore(feedback.getWritingScore()) >= LEARNING_MATERIAL_EXCLUDE_MIN_SCORE) {
        continue;
      }
      result.add(feedback);
    }
    return result;
  }

  public SummaryData mergeWithLlm(
      SummaryData fallback, SessionSummaryManager.LlmSections llmSections) {
    SummaryData base = fallback == null ? createEmptySummary() : copySummary(fallback);
    if (llmSections == null) {
      return base;
    }

    boolean shouldMergeHighlights = base.getHighlights() != null && !base.getHighlights().isEmpty();
    if (shouldMergeHighlights) {
      List<SummaryData.HighlightItem> llmHighlights =
          fromLlmHighlights(llmSections.getHighlights(), base.getHighlights());
      if (!llmHighlights.isEmpty()) {
        base.setHighlights(limit(llmHighlights, MAX_HIGHLIGHTS));
      }
    }

    List<SummaryData.ExpressionItem> llmExpressions =
        fromLlmExpressions(llmSections.getExpressions(), base.getExpressions());
    if (!llmExpressions.isEmpty()) {
      base.setExpressions(llmExpressions);
    }

    List<SummaryData.WordItem> llmWords = fromLlmWords(llmSections.getWords());
    if (!llmWords.isEmpty()) {
      base.setWords(limit(llmWords, MAX_WORDS));
    }

    return base;
  }

  public SummaryData createEmptySummary() {
    SummaryData summaryData = new SummaryData();
    summaryData.setTotalScore(0);
    summaryData.setHighlights(new ArrayList<>());
    summaryData.setExpressions(new ArrayList<>());
    summaryData.setWords(new ArrayList<>());
    summaryData.setLikedSentences(new ArrayList<>());
    return summaryData;
  }

  private SummaryData copySummary(SummaryData source) {
    SummaryData copy = new SummaryData();
    copy.setTotalScore(source.getTotalScore());
    copy.setHighlights(
        source.getHighlights() == null
            ? new ArrayList<>()
            : new ArrayList<>(source.getHighlights()));
    copy.setExpressions(
        source.getExpressions() == null
            ? new ArrayList<>()
            : new ArrayList<>(source.getExpressions()));
    copy.setWords(
        source.getWords() == null ? new ArrayList<>() : new ArrayList<>(source.getWords()));
    copy.setLikedSentences(
        source.getLikedSentences() == null
            ? new ArrayList<>()
            : new ArrayList<>(source.getLikedSentences()));
    return copy;
  }

  private int calculateAverageScore(List<SentenceFeedback> feedbacks) {
    int count = 0;
    int sum = 0;
    for (SentenceFeedback feedback : feedbacks) {
      if (feedback == null || feedback.getWritingScore() == null) {
        continue;
      }
      int score = feedback.getWritingScore().getScore();
      if (score < 0 || score > 100) {
        continue;
      }
      sum += score;
      count++;
    }
    if (count == 0) {
      return 0;
    }
    return Math.round(sum / (float) count);
  }

  private boolean hasAnyScoreAtLeast(List<SentenceFeedback> feedbacks, int minScore) {
    if (feedbacks == null || feedbacks.isEmpty()) {
      return false;
    }
    for (SentenceFeedback feedback : feedbacks) {
      if (feedback == null) {
        continue;
      }
      if (safeScore(feedback.getWritingScore()) >= minScore) {
        return true;
      }
    }
    return false;
  }

  private List<SummaryData.SentenceItem> buildLikedSentences(List<BookmarkedParaphrase> bookmarks) {
    List<BookmarkedParaphrase> sorted = new ArrayList<>(bookmarks);
    sorted.sort(Comparator.comparingLong(BookmarkedParaphrase::getBookmarkedAt).reversed());

    List<SummaryData.SentenceItem> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (BookmarkedParaphrase bookmark : sorted) {
      if (bookmark == null) {
        continue;
      }
      String sentence = trimToNull(bookmark.getSentence());
      String translation = trimToNull(bookmark.getSentenceTranslation());
      if (sentence == null || translation == null) {
        continue;
      }
      String key = normalize(sentence);
      if (seen.contains(key)) {
        continue;
      }
      seen.add(key);
      result.add(new SummaryData.SentenceItem(sentence, translation));
    }
    return result;
  }

  private List<String> buildSentenceCandidates(
      List<SentenceFeedback> feedbacks, List<SummaryData.SentenceItem> likedSentences) {
    List<String> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();

    for (SentenceFeedback feedback : feedbacks) {
      if (feedback == null) {
        continue;
      }
      String naturalSentence =
          toSentenceText(
              feedback.getNaturalness() != null
                  ? feedback.getNaturalness().getNaturalSentence()
                  : null,
              true,
              true);
      addSentenceCandidate(result, seen, naturalSentence);
    }

    if (likedSentences != null) {
      for (SummaryData.SentenceItem liked : likedSentences) {
        if (liked == null) {
          continue;
        }
        addSentenceCandidate(result, seen, liked.getEnglish());
      }
    }

    return result;
  }

  private void addSentenceCandidate(List<String> result, Set<String> seen, String sentence) {
    String value = trimToNull(sentence);
    if (value == null) {
      return;
    }
    String key = normalize(value);
    if (seen.contains(key)) {
      return;
    }
    seen.add(key);
    result.add(value);
  }

  private List<RankedItem<SummaryData.HighlightItem>> buildHighlightCandidates(
      List<SentenceFeedback> feedbacks) {
    Map<String, RankedItem<SummaryData.HighlightItem>> map = new HashMap<>();
    long order = 0;

    for (SentenceFeedback feedback : feedbacks) {
      if (feedback == null) {
        continue;
      }
      int score = safeScore(feedback.getWritingScore());
      NaturalnessFeedback naturalness = feedback.getNaturalness();
      String english = resolveUserWrittenSentence(feedback);
      String korean =
          firstNonBlank(
              naturalness != null ? naturalness.getNaturalSentenceTranslation() : null,
              feedback.getConceptualBridge() != null
                  ? feedback.getConceptualBridge().getLiteralTranslation()
                  : null);
      String reason =
          firstNonBlank(
              naturalness != null ? naturalness.getExplanation() : null,
              feedback.getGrammar() != null ? feedback.getGrammar().getExplanation() : null);

      if (english == null || korean == null || reason == null) {
        continue;
      }

      String key = normalize(english);
      RankedItem<SummaryData.HighlightItem> candidate =
          new RankedItem<>(new SummaryData.HighlightItem(english, korean, reason), score, order++);
      putBestRanked(map, key, candidate);
    }

    return sortRanked(new ArrayList<>(map.values()));
  }

  private List<RankedItem<SummaryData.ExpressionItem>> buildExpressionCandidates(
      List<SentenceFeedback> feedbacks) {
    Map<String, RankedItem<SummaryData.ExpressionItem>> map = new HashMap<>();
    long order = 0;

    for (SentenceFeedback feedback : feedbacks) {
      if (feedback == null) {
        continue;
      }

      int score = safeScore(feedback.getWritingScore());
      GrammarFeedback grammar = feedback.getGrammar();
      NaturalnessFeedback naturalness = feedback.getNaturalness();
      ConceptualBridge conceptualBridge = feedback.getConceptualBridge();
      String userWrittenSentence = resolveUserWrittenSentence(feedback);

      String prompt =
          firstNonBlank(
              trimToNull(feedback.getOriginalSentence()),
              conceptualBridge != null ? conceptualBridge.getLiteralTranslation() : null,
              "\uC601\uC791\uD560 \uBB38\uC7A5");

      if (grammar != null && grammar.getCorrectedSentence() != null) {
        String before = userWrittenSentence;
        String after = toSentenceText(grammar.getCorrectedSentence(), false, true);
        String explanation =
            firstNonBlank(
                grammar.getExplanation(),
                naturalness != null ? naturalness.getExplanation() : null,
                "\uBB38\uC7A5 \uC758\uBBF8\uB97C \uB354 \uC815\uD655\uD558\uAC8C \uC804\uB2EC\uD560 \uC218 \uC788\uB3C4\uB85D \uB2E4\uB4EC\uC740 \uD45C\uD604\uC785\uB2C8\uB2E4.");
        if (before != null && after != null && !normalize(before).equals(normalize(after))) {
          List<String> afterHighlights =
              extractHighlightPhrases(
                  grammar.getCorrectedSentence(),
                  TextSegment.TYPE_CORRECTION,
                  TextSegment.TYPE_HIGHLIGHT);
          if (afterHighlights.isEmpty()) {
            afterHighlights = inferAfterHighlightsFromDiff(before, after);
          }

          SummaryData.ExpressionItem preciseItem =
              new SummaryData.ExpressionItem(
                  "\uC815\uD655\uD55C \uD45C\uD604",
                  prompt,
                  before,
                  after,
                  explanation,
                  afterHighlights);
          putBestRanked(
              map, "precise|" + normalize(after), new RankedItem<>(preciseItem, score, order++));
        }
      }

      String natural =
          naturalness != null ? toSentenceText(naturalness.getNaturalSentence(), true, true) : null;
      String naturalExplanation =
          firstNonBlank(
              naturalness != null ? naturalness.getExplanation() : null,
              grammar != null ? grammar.getExplanation() : null,
              "\uC790\uC8FC \uC4F0\uB294 \uD45C\uD604\uC73C\uB85C \uBC14\uAFD4 \uB9D0\uD558\uBA74 \uB354 \uC790\uC5F0\uC2A4\uB7FD\uC2B5\uB2C8\uB2E4.");
      if (userWrittenSentence != null
          && natural != null
          && !normalize(userWrittenSentence).equals(normalize(natural))) {
        List<String> afterHighlights =
            extractHighlightPhrases(
                naturalness != null ? naturalness.getNaturalSentence() : null,
                TextSegment.TYPE_HIGHLIGHT);
        if (afterHighlights.isEmpty()) {
          afterHighlights = inferAfterHighlightsFromDiff(userWrittenSentence, natural);
        }

        SummaryData.ExpressionItem naturalItem =
            new SummaryData.ExpressionItem(
                "\uC790\uC5F0\uC2A4\uB7EC\uC6B4 \uD45C\uD604",
                prompt,
                userWrittenSentence,
                natural,
                naturalExplanation,
                afterHighlights);
        putBestRanked(
            map, "natural|" + normalize(natural), new RankedItem<>(naturalItem, score, order++));
      }
    }

    return sortRanked(new ArrayList<>(map.values()));
  }

  private List<RankedItem<SummaryData.WordItem>> buildWordCandidates(
      List<SentenceFeedback> feedbacks) {
    Map<String, RankedItem<SummaryData.WordItem>> map = new HashMap<>();
    long order = 0;

    for (SentenceFeedback feedback : feedbacks) {
      if (feedback == null) {
        continue;
      }

      int score = safeScore(feedback.getWritingScore());
      ConceptualBridge conceptualBridge = feedback.getConceptualBridge();
      if (conceptualBridge == null || conceptualBridge.getVennDiagram() == null) {
        continue;
      }

      String exampleEnglish =
          firstNonBlank(
              getToneDefaultSentence(feedback.getToneStyle()),
              getParaphrasingSentence(feedback.getParaphrasing(), 2),
              toSentenceText(
                  feedback.getNaturalness() != null
                      ? feedback.getNaturalness().getNaturalSentence()
                      : null,
                  true,
                  true));
      String exampleKorean =
          firstNonBlank(
              getToneDefaultTranslation(feedback.getToneStyle()),
              getParaphrasingTranslation(feedback.getParaphrasing(), 2),
              feedback.getNaturalness() != null
                  ? feedback.getNaturalness().getNaturalSentenceTranslation()
                  : null);

      VennDiagram vennDiagram = conceptualBridge.getVennDiagram();
      order =
          addWordCandidate(
              map, vennDiagram.getLeftCircle(), exampleEnglish, exampleKorean, score, order);
      order =
          addWordCandidate(
              map, vennDiagram.getRightCircle(), exampleEnglish, exampleKorean, score, order);
    }

    return sortRanked(new ArrayList<>(map.values()));
  }

  private long addWordCandidate(
      Map<String, RankedItem<SummaryData.WordItem>> map,
      VennCircle circle,
      String exampleEnglish,
      String exampleKorean,
      int score,
      long order) {
    if (circle == null) {
      return order;
    }
    String word = trimToNull(circle.getWord());
    String korean = firstNonBlank(getFirstNonBlank(circle.getItems()), word);
    if (word == null || korean == null) {
      return order;
    }
    String finalExampleEnglish = firstNonBlank(exampleEnglish, word);
    String finalExampleKorean = firstNonBlank(exampleKorean, korean);
    SummaryData.WordItem item =
        new SummaryData.WordItem(word, korean, finalExampleEnglish, finalExampleKorean);
    putBestRanked(map, normalize(word), new RankedItem<>(item, score, order++));
    return order;
  }

  private List<String> collectUserOriginalSentences(List<SentenceFeedback> feedbacks) {
    List<String> result = new ArrayList<>();
    for (SentenceFeedback feedback : feedbacks) {
      if (feedback == null) {
        continue;
      }
      String userText = trimToNull(feedback.getUserSentence());
      if (userText != null) {
        result.add(userText);
      }
    }
    return result;
  }

  private List<SummaryFeatureBundle.HighlightCandidate> toHighlightCandidates(
      List<SummaryData.HighlightItem> source) {
    List<SummaryFeatureBundle.HighlightCandidate> result = new ArrayList<>();
    for (SummaryData.HighlightItem item : source) {
      if (item == null) {
        continue;
      }
      result.add(
          new SummaryFeatureBundle.HighlightCandidate(
              item.getEnglish(), item.getKorean(), item.getReason()));
    }
    return result;
  }

  private List<SummaryFeatureBundle.ExpressionCandidate> toExpressionCandidates(
      List<SummaryData.ExpressionItem> source) {
    List<SummaryFeatureBundle.ExpressionCandidate> result = new ArrayList<>();
    for (SummaryData.ExpressionItem item : source) {
      if (item == null) {
        continue;
      }
      result.add(
          new SummaryFeatureBundle.ExpressionCandidate(
              item.getType(),
              item.getKoreanPrompt(),
              item.getBefore(),
              item.getAfter(),
              item.getExplanation()));
    }
    return result;
  }

  private List<SummaryFeatureBundle.WordCandidate> toWordCandidates(
      List<SummaryData.WordItem> source) {
    List<SummaryFeatureBundle.WordCandidate> result = new ArrayList<>();
    for (SummaryData.WordItem item : source) {
      if (item == null) {
        continue;
      }
      result.add(
          new SummaryFeatureBundle.WordCandidate(
              item.getEnglish(),
              item.getKorean(),
              item.getExampleEnglish(),
              item.getExampleKorean()));
    }
    return result;
  }

  private List<SummaryData.HighlightItem> fromLlmHighlights(
      List<SessionSummaryManager.HighlightSection> source,
      List<SummaryData.HighlightItem> fallbackHighlights) {
    List<SummaryData.HighlightItem> result = new ArrayList<>();
    if (source == null) {
      return result;
    }

    List<SummaryData.HighlightItem> fallback =
        fallbackHighlights == null ? new ArrayList<>() : fallbackHighlights;

    for (int i = 0; i < source.size(); i++) {
      SessionSummaryManager.HighlightSection item = source.get(i);
      if (item == null) {
        continue;
      }

      String english = trimToNull(item.getEnglish());
      String korean = trimToNull(item.getKorean());
      String reason = trimToNull(item.getReason());
      if (korean == null || reason == null) {
        continue;
      }

      if (i < fallback.size()) {
        String fallbackEnglish = trimToNull(fallback.get(i).getEnglish());
        if (fallbackEnglish != null) {
          english = fallbackEnglish;
        }
      }

      if (english == null) {
        continue;
      }

      result.add(new SummaryData.HighlightItem(english, korean, reason));
    }

    return result;
  }

  private List<SummaryData.ExpressionItem> fromLlmExpressions(
      List<SessionSummaryManager.ExpressionSection> source,
      List<SummaryData.ExpressionItem> fallbackExpressions) {
    List<SummaryData.ExpressionItem> result = new ArrayList<>();
    if (source == null) {
      return result;
    }

    List<SummaryData.ExpressionItem> fallback =
        fallbackExpressions == null ? new ArrayList<>() : fallbackExpressions;

    for (int i = 0; i < source.size(); i++) {
      SessionSummaryManager.ExpressionSection item = source.get(i);
      if (item == null) {
        continue;
      }

      String type = trimToNull(item.getType());
      String prompt = trimToNull(item.getKoreanPrompt());
      String before = sanitizeLabeledSentence(item.getBefore());
      String rawAfter = sanitizeLabeledSentence(item.getAfter());
      String explanation = trimToNull(item.getExplanation());

      ParsedAfter parsedAfter = parseMarkedAfter(rawAfter);
      String after = parsedAfter.sentence;
      if (type == null
          || prompt == null
          || before == null
          || after == null
          || explanation == null) {
        continue;
      }

      SummaryData.ExpressionItem reference =
          findReferenceExpression(fallback, i, type, prompt, after);
      if (reference != null && trimToNull(reference.getBefore()) != null) {
        before = trimToNull(reference.getBefore());
      }

      if (normalize(before).equals(normalize(after))) {
        continue;
      }

      List<String> afterHighlights = new ArrayList<>(parsedAfter.highlights);
      if (afterHighlights.isEmpty()
          && reference != null
          && reference.getAfterHighlights() != null) {
        for (String highlight : reference.getAfterHighlights()) {
          String phrase = trimToNull(highlight);
          if (phrase != null) {
            afterHighlights.add(phrase);
          }
        }
      }
      if (afterHighlights.isEmpty()) {
        afterHighlights = inferAfterHighlightsFromDiff(before, after);
      }

      result.add(
          new SummaryData.ExpressionItem(
              type, prompt, before, after, explanation, afterHighlights));
    }

    return result;
  }

  private List<SummaryData.WordItem> fromLlmWords(List<SessionSummaryManager.WordSection> source) {
    List<SummaryData.WordItem> result = new ArrayList<>();
    if (source == null) {
      return result;
    }
    for (SessionSummaryManager.WordSection item : source) {
      if (item == null) {
        continue;
      }
      String english = trimToNull(item.getEnglish());
      String korean = trimToNull(item.getKorean());
      if (english == null || korean == null) {
        continue;
      }
      String exampleEnglish = firstNonBlank(item.getExampleEnglish(), english);
      String exampleKorean = firstNonBlank(item.getExampleKorean(), korean);
      result.add(new SummaryData.WordItem(english, korean, exampleEnglish, exampleKorean));
    }
    return result;
  }

  private int safeScore(WritingScore writingScore) {
    if (writingScore == null) {
      return 0;
    }
    int score = writingScore.getScore();
    if (score < 0) {
      return 0;
    }
    if (score > 100) {
      return 100;
    }
    return score;
  }

  private String getToneDefaultSentence(ToneStyle toneStyle) {
    if (toneStyle == null) {
      return null;
    }
    return trimToNull(toneStyle.getSentenceForLevel(toneStyle.getDefaultLevel()));
  }

  private String getToneDefaultTranslation(ToneStyle toneStyle) {
    if (toneStyle == null) {
      return null;
    }
    return trimToNull(toneStyle.getTranslationForLevel(toneStyle.getDefaultLevel()));
  }

  private String getParaphrasingSentence(List<ParaphrasingLevel> levels, int level) {
    if (levels == null) {
      return null;
    }
    for (ParaphrasingLevel item : levels) {
      if (item != null && item.getLevel() == level) {
        return trimToNull(item.getSentence());
      }
    }
    return null;
  }

  private String getParaphrasingTranslation(List<ParaphrasingLevel> levels, int level) {
    if (levels == null) {
      return null;
    }
    for (ParaphrasingLevel item : levels) {
      if (item != null && item.getLevel() == level) {
        return trimToNull(item.getSentenceTranslation());
      }
    }
    return null;
  }

  private String toSentenceText(
      StyledSentence sentence, boolean includeIncorrect, boolean includeCorrection) {
    if (sentence == null || sentence.getSegments() == null) {
      return null;
    }
    StringBuilder builder = new StringBuilder();
    for (TextSegment segment : sentence.getSegments()) {
      if (segment == null) {
        continue;
      }
      String type = segment.getType();
      if (TextSegment.TYPE_INCORRECT.equals(type) && !includeIncorrect) {
        continue;
      }
      if (TextSegment.TYPE_CORRECTION.equals(type) && !includeCorrection) {
        continue;
      }
      if (segment.getText() != null) {
        builder.append(segment.getText());
      }
    }
    return trimToNull(builder.toString());
  }

  private String resolveUserWrittenSentence(SentenceFeedback feedback) {
    if (feedback == null) {
      return null;
    }
    return firstNonBlank(
        trimToNull(feedback.getUserSentence()),
        toSentenceText(
            feedback.getGrammar() != null ? feedback.getGrammar().getCorrectedSentence() : null,
            true,
            false));
  }

  private List<String> extractHighlightPhrases(StyledSentence sentence, String... targetTypes) {
    List<String> result = new ArrayList<>();
    if (sentence == null
        || sentence.getSegments() == null
        || targetTypes == null
        || targetTypes.length == 0) {
      return result;
    }

    Set<String> targetTypeSet = new HashSet<>();
    for (String type : targetTypes) {
      String normalizedType = trimToNull(type);
      if (normalizedType != null) {
        targetTypeSet.add(normalizedType);
      }
    }
    if (targetTypeSet.isEmpty()) {
      return result;
    }

    StringBuilder current = new StringBuilder();
    for (TextSegment segment : sentence.getSegments()) {
      if (segment == null || segment.getText() == null) {
        continue;
      }
      String segmentType = trimToNull(segment.getType());
      boolean target = segmentType != null && targetTypeSet.contains(segmentType);
      if (target) {
        current.append(segment.getText());
      } else if (current.length() > 0) {
        addUniquePhrase(result, current.toString());
        current.setLength(0);
      }
    }
    if (current.length() > 0) {
      addUniquePhrase(result, current.toString());
    }
    return result;
  }

  private List<String> inferAfterHighlightsFromDiff(String before, String after) {
    List<String> result = new ArrayList<>();
    String safeBefore = trimToNull(before);
    String safeAfter = trimToNull(after);
    if (safeBefore == null || safeAfter == null) {
      return result;
    }

    Set<String> beforeTokens = new HashSet<>();
    for (String token : safeBefore.split("\\s+")) {
      String normalized = normalizeToken(token);
      if (normalized != null) {
        beforeTokens.add(normalized);
      }
    }

    List<String> candidates = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (String token : safeAfter.split("\\s+")) {
      String normalized = normalizeToken(token);
      boolean isChanged = normalized == null || !beforeTokens.contains(normalized);
      if (isChanged) {
        if (current.length() > 0) {
          current.append(' ');
        }
        current.append(token);
      } else if (current.length() > 0) {
        candidates.add(current.toString());
        current.setLength(0);
      }
    }
    if (current.length() > 0) {
      candidates.add(current.toString());
    }

    String best = null;
    for (String candidate : candidates) {
      String phrase = trimToNull(candidate);
      if (phrase == null) {
        continue;
      }
      if (best == null || phrase.length() > best.length()) {
        best = phrase;
      }
    }
    if (best != null) {
      result.add(best);
    }
    return result;
  }

  private String normalizeToken(String token) {
    String trimmed = trimToNull(token);
    if (trimmed == null) {
      return null;
    }
    String stripped = trimmed.replaceAll("^[^\\p{L}\\p{N}']+|[^\\p{L}\\p{N}']+$", "");
    return stripped.isEmpty() ? null : stripped.toLowerCase();
  }

  private void addUniquePhrase(List<String> phrases, String phrase) {
    String trimmed = trimToNull(phrase);
    if (trimmed == null) {
      return;
    }
    String normalized = normalize(trimmed);
    for (String existing : phrases) {
      if (normalize(existing).equals(normalized)) {
        return;
      }
    }
    phrases.add(trimmed);
  }

  private String sanitizeLabeledSentence(String sentence) {
    String value = trimToNull(sentence);
    if (value == null) {
      return null;
    }

    String lower = value.toLowerCase();
    if (lower.startsWith("before:") || lower.startsWith("after:")) {
      int separator = value.indexOf(':');
      if (separator >= 0 && separator + 1 < value.length()) {
        return trimToNull(value.substring(separator + 1));
      }
    }
    return value;
  }

  private ParsedAfter parseMarkedAfter(String rawAfter) {
    ParsedAfter parsed = new ParsedAfter();
    String value = trimToNull(rawAfter);
    if (value == null) {
      return parsed;
    }

    StringBuilder plainText = new StringBuilder();
    int cursor = 0;
    while (cursor < value.length()) {
      int start = value.indexOf("[[", cursor);
      if (start < 0) {
        plainText.append(value.substring(cursor));
        break;
      }
      plainText.append(value, cursor, start);
      int end = value.indexOf("]]", start + 2);
      if (end < 0) {
        plainText.append(value.substring(start));
        break;
      }

      String marked = trimToNull(value.substring(start + 2, end));
      if (marked != null) {
        addUniquePhrase(parsed.highlights, marked);
        plainText.append(marked);
      }
      cursor = end + 2;
    }

    parsed.sentence = trimToNull(plainText.toString());
    return parsed;
  }

  private SummaryData.ExpressionItem findReferenceExpression(
      List<SummaryData.ExpressionItem> fallback,
      int index,
      String type,
      String prompt,
      String after) {
    if (fallback == null || fallback.isEmpty()) {
      return null;
    }
    if (index >= 0 && index < fallback.size()) {
      SummaryData.ExpressionItem indexed = fallback.get(index);
      if (indexed != null) {
        return indexed;
      }
    }

    String targetType = normalizeExpressionType(type);
    String targetPrompt = normalize(prompt);
    String targetAfter = normalize(after);

    SummaryData.ExpressionItem best = null;
    int bestScore = Integer.MIN_VALUE;
    for (SummaryData.ExpressionItem candidate : fallback) {
      if (candidate == null) {
        continue;
      }
      int score = 0;
      if (normalizeExpressionType(candidate.getType()).equals(targetType)) {
        score += 3;
      }
      if (normalize(candidate.getKoreanPrompt()).equals(targetPrompt)) {
        score += 2;
      }
      String candidateAfter = normalize(candidate.getAfter());
      if (!candidateAfter.isEmpty()
          && !targetAfter.isEmpty()
          && (candidateAfter.equals(targetAfter)
              || candidateAfter.contains(targetAfter)
              || targetAfter.contains(candidateAfter))) {
        score += 1;
      }
      if (score > bestScore) {
        bestScore = score;
        best = candidate;
      }
    }
    return best;
  }

  private String normalizeExpressionType(String type) {
    String value = normalize(type);
    if (value.contains("precise") || value.contains("\uC815\uD655")) {
      return "precise";
    }
    if (value.contains("natural") || value.contains("\uC790\uC5F0")) {
      return "natural";
    }
    return value;
  }

  private String getFirstNonBlank(List<String> values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      String trimmed = trimToNull(value);
      if (trimmed != null) {
        return trimmed;
      }
    }
    return null;
  }

  private String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      String trimmed = trimToNull(value);
      if (trimmed != null) {
        return trimmed;
      }
    }
    return null;
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private <T> void putBestRanked(
      Map<String, RankedItem<T>> map, String key, RankedItem<T> candidate) {
    RankedItem<T> current = map.get(key);
    if (current == null
        || candidate.score > current.score
        || (candidate.score == current.score && candidate.order > current.order)) {
      map.put(key, candidate);
    }
  }

  private <T> List<RankedItem<T>> sortRanked(List<RankedItem<T>> source) {
    source.sort(
        (a, b) -> {
          if (a.score != b.score) {
            return Integer.compare(b.score, a.score);
          }
          return Long.compare(b.order, a.order);
        });
    return source;
  }

  private <T> List<T> topItems(List<RankedItem<T>> rankedItems, int maxCount) {
    List<T> result = new ArrayList<>();
    for (RankedItem<T> rankedItem : rankedItems) {
      result.add(rankedItem.item);
      if (result.size() >= maxCount) {
        break;
      }
    }
    return result;
  }

  private <T> List<T> limit(List<T> source, int maxCount) {
    if (source == null) {
      return new ArrayList<>();
    }
    if (source.size() <= maxCount) {
      return new ArrayList<>(source);
    }
    return new ArrayList<>(source.subList(0, maxCount));
  }

  private static class ParsedAfter {
    private String sentence;
    private List<String> highlights = new ArrayList<>();
  }

  public static class GenerationSeed {
    private final SummaryData fallbackSummary;
    private final SummaryFeatureBundle featureBundle;

    public GenerationSeed(SummaryData fallbackSummary, SummaryFeatureBundle featureBundle) {
      this.fallbackSummary = fallbackSummary;
      this.featureBundle = featureBundle;
    }

    public SummaryData getFallbackSummary() {
      return fallbackSummary;
    }

    public SummaryFeatureBundle getFeatureBundle() {
      return featureBundle;
    }
  }

  private static class RankedItem<T> {
    private final T item;
    private final int score;
    private final long order;

    private RankedItem(T item, int score, long order) {
      this.item = item;
      this.score = score;
      this.order = order;
    }
  }

  private <T> List<T> allItems(List<RankedItem<T>> rankedItems) {
    List<T> result = new ArrayList<>();
    for (RankedItem<T> rankedItem : rankedItems) {
      result.add(rankedItem.item);
    }
    return result;
  }
}
