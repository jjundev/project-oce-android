package com.jjundev.oneclickeng.learning.dialoguelearning.summary;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.gson.Gson;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISessionSummaryLlmManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DialogueSummaryViewModel extends ViewModel {

  private static final String STATE_SUMMARY_DATA_JSON = "state_summary_data_json";
  private static final String STATE_WORD_LOAD_STATUS = "state_word_load_status";
  private static final String STATE_WORD_LOAD_ERROR = "state_word_load_error";
  private static final String STATE_EXPRESSION_LOAD_STATUS = "state_expression_load_status";
  private static final String STATE_EXPRESSION_LOAD_ERROR = "state_expression_load_error";
  private static final int MAX_WORDS = 12;
  private static final Gson GSON = new Gson();

  private final ISessionSummaryLlmManager sessionSummaryLlmManager;
  private final MutableLiveData<SummaryData> summaryData =
      new MutableLiveData<>(new SessionSummaryGenerator().createEmptySummary());
  private final MutableLiveData<ExpressionLoadState> expressionLoadState =
      new MutableLiveData<>(ExpressionLoadState.loading());
  private final MutableLiveData<WordLoadState> wordLoadState =
      new MutableLiveData<>(WordLoadState.loading());

  @Nullable private SummaryFeatureBundle featureBundle;
  @NonNull private final Map<String, String> expressionPromptByTypeAndBefore = new HashMap<>();
  @NonNull private final Map<String, String> expressionPromptByBefore = new HashMap<>();
  private int requestToken = 0;
  private boolean hasInitialized = false;
  private boolean hasWordExtractionStarted = false;
  private boolean hasExpressionFilterStarted = false;

  public DialogueSummaryViewModel(@NonNull ISessionSummaryLlmManager sessionSummaryLlmManager) {
    this.sessionSummaryLlmManager = sessionSummaryLlmManager;
  }

  public LiveData<SummaryData> getSummaryData() {
    return summaryData;
  }

  public LiveData<ExpressionLoadState> getExpressionLoadState() {
    return expressionLoadState;
  }

  public LiveData<WordLoadState> getWordLoadState() {
    return wordLoadState;
  }

  public void initialize(
      @Nullable String summaryJson,
      @Nullable String featureBundleJson,
      @Nullable Bundle savedInstanceState) {
    SummaryData restoredSummary = resolveSavedSummaryData(savedInstanceState);
    SummaryData initialSummary =
        restoredSummary == null ? resolveSummaryData(summaryJson) : restoredSummary;
    if (initialSummary.getWords() == null) {
      initialSummary.setWords(new ArrayList<>());
    }
    if (restoredSummary == null) {
      // Words are API-only. Hide any pre-generated fallback words until API succeeds.
      initialSummary.setWords(new ArrayList<>());
    }
    summaryData.setValue(initialSummary);

    if (!hasInitialized) {
      hasInitialized = true;
      featureBundle = resolveFeatureBundle(featureBundleJson);
      rebuildExpressionPromptIndex(featureBundle);
      restoreWordLoadState(savedInstanceState);
      restoreExpressionLoadState(savedInstanceState);
    }

    if (!hasExpressionFilterStarted) {
      ExpressionLoadState currentExprState = expressionLoadState.getValue();
      if (currentExprState != null && currentExprState.status == ExpressionLoadStatus.LOADING) {
        hasExpressionFilterStarted = true;
        startExpressionFiltering();
      }
    }

    if (!hasWordExtractionStarted) {
      WordLoadState currentWordState = wordLoadState.getValue();
      if (currentWordState != null && currentWordState.status == WordLoadStatus.LOADING) {
        hasWordExtractionStarted = true;
        startWordExtractionIfPossible();
      }
    }
  }

  public void onViewDestroyed() {
    requestToken++;
  }

  public void saveState(@NonNull Bundle outState) {
    SummaryData currentSummary = summaryData.getValue();
    if (currentSummary != null) {
      outState.putString(STATE_SUMMARY_DATA_JSON, GSON.toJson(currentSummary));
    }

    ExpressionLoadState exprState = expressionLoadState.getValue();
    if (exprState != null) {
      outState.putString(STATE_EXPRESSION_LOAD_STATUS, exprState.status.name());
      outState.putString(STATE_EXPRESSION_LOAD_ERROR, exprState.errorMessage);
    }

    WordLoadState wordState = wordLoadState.getValue();
    if (wordState != null) {
      outState.putString(STATE_WORD_LOAD_STATUS, wordState.status.name());
      outState.putString(STATE_WORD_LOAD_ERROR, wordState.errorMessage);
    }
  }

  private void startExpressionFiltering() {
    if (featureBundle == null) {
      expressionLoadState.setValue(ExpressionLoadState.ready());
      return;
    }

    final int currentToken = ++requestToken;
    expressionLoadState.setValue(ExpressionLoadState.loading());
    final boolean[] clearedFallback = {false};

    sessionSummaryLlmManager.filterExpressionsAsync(
        featureBundle,
        new ISessionSummaryLlmManager.ExpressionFilterCallback() {
          @Override
          public void onExpressionReceived(
              @NonNull ISessionSummaryLlmManager.FilteredExpression expression) {
            if (!isRequestActive(currentToken)) {
              return;
            }

            SummaryData.ExpressionItem item = toExpressionItem(expression);
            if (item == null) {
              return;
            }

            // On first streamed expression, clear fallback expressions
            if (!clearedFallback[0]) {
              clearExpressionsInSummary();
              clearedFallback[0] = true;
            }
            appendExpressionToSummary(item);
          }

          @Override
          public void onComplete() {
            if (!isRequestActive(currentToken)) {
              return;
            }
            expressionLoadState.setValue(ExpressionLoadState.ready());
          }

          @Override
          public void onFailure(@NonNull String error) {
            if (!isRequestActive(currentToken)) {
              return;
            }
            // Keep fallback expressions already in summaryData.
            expressionLoadState.setValue(ExpressionLoadState.ready());
          }
        });
  }

  @Nullable
  private SummaryData.ExpressionItem toExpressionItem(
      @Nullable ISessionSummaryLlmManager.FilteredExpression f) {
    if (f == null) {
      return null;
    }
    String type = trimToNull(f.getType());
    String before = trimToNull(f.getBefore());
    String after = trimToNull(f.getAfter());
    String explanation = trimToNull(f.getExplanation());
    if (type == null || before == null || after == null || explanation == null) {
      return null;
    }
    String prompt = resolveSourceExpressionPrompt(type, before);
    if (prompt == null) {
      return null;
    }
    return new SummaryData.ExpressionItem(type, prompt, before, after, explanation);
  }

  private void clearExpressionsInSummary() {
    SummaryData current = summaryData.getValue();
    if (current == null) {
      current = new SessionSummaryGenerator().createEmptySummary();
    }
    SummaryData updated = new SummaryData();
    updated.setTotalScore(current.getTotalScore());
    updated.setHighlights(current.getHighlights());
    updated.setExpressions(new ArrayList<>());
    updated.setWords(current.getWords());
    updated.setLikedSentences(current.getLikedSentences());
    summaryData.setValue(updated);
  }

  private void appendExpressionToSummary(@NonNull SummaryData.ExpressionItem item) {
    SummaryData current = summaryData.getValue();
    if (current == null) {
      current = new SessionSummaryGenerator().createEmptySummary();
    }
    List<SummaryData.ExpressionItem> existingExpressions = current.getExpressions();
    List<SummaryData.ExpressionItem> newList =
        existingExpressions != null ? new ArrayList<>(existingExpressions) : new ArrayList<>();
    newList.add(item);

    SummaryData updated = new SummaryData();
    updated.setTotalScore(current.getTotalScore());
    updated.setHighlights(current.getHighlights());
    updated.setExpressions(newList);
    updated.setWords(current.getWords());
    updated.setLikedSentences(current.getLikedSentences());
    summaryData.setValue(updated);
  }

  private boolean isRequestActive(int token) {
    return token == requestToken;
  }

  private void restoreExpressionLoadState(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState == null) {
      expressionLoadState.setValue(ExpressionLoadState.loading());
      return;
    }

    ExpressionLoadStatus status = ExpressionLoadStatus.LOADING;
    try {
      String rawStatus = savedInstanceState.getString(STATE_EXPRESSION_LOAD_STATUS);
      if (rawStatus != null) {
        status = ExpressionLoadStatus.valueOf(rawStatus);
      }
    } catch (Exception ignored) {
      status = ExpressionLoadStatus.LOADING;
    }

    String errorMessage = savedInstanceState.getString(STATE_EXPRESSION_LOAD_ERROR);
    if (status == ExpressionLoadStatus.READY) {
      expressionLoadState.setValue(ExpressionLoadState.ready());
      return;
    }
    if (status == ExpressionLoadStatus.ERROR) {
      expressionLoadState.setValue(ExpressionLoadState.error(errorMessage));
      return;
    }
    expressionLoadState.setValue(ExpressionLoadState.loading());
  }

  private void restoreWordLoadState(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState == null) {
      wordLoadState.setValue(WordLoadState.loading());
      return;
    }

    WordLoadStatus status = WordLoadStatus.LOADING;
    try {
      String rawStatus = savedInstanceState.getString(STATE_WORD_LOAD_STATUS);
      if (rawStatus != null) {
        status = WordLoadStatus.valueOf(rawStatus);
      }
    } catch (Exception ignored) {
      status = WordLoadStatus.LOADING;
    }

    String errorMessage = savedInstanceState.getString(STATE_WORD_LOAD_ERROR);
    if (status == WordLoadStatus.READY) {
      wordLoadState.setValue(WordLoadState.ready());
      return;
    }
    if (status == WordLoadStatus.ERROR) {
      wordLoadState.setValue(WordLoadState.error(errorMessage));
      return;
    }
    if (status == WordLoadStatus.HIDDEN) {
      wordLoadState.setValue(WordLoadState.hidden());
      return;
    }
    if (status == WordLoadStatus.EMPTY) {
      wordLoadState.setValue(WordLoadState.empty());
      return;
    }
    wordLoadState.setValue(WordLoadState.loading());
  }

  private SummaryData resolveSummaryData(@Nullable String summaryJson) {
    if (summaryJson != null && !summaryJson.trim().isEmpty()) {
      try {
        SummaryData parsed = GSON.fromJson(summaryJson, SummaryData.class);
        if (parsed != null) {
          return parsed;
        }
      } catch (Exception ignored) {
        // Fallback handled below.
      }
    }
    return new SessionSummaryGenerator().createEmptySummary();
  }

  @Nullable
  private SummaryData resolveSavedSummaryData(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState == null) {
      return null;
    }
    return parseSummaryData(savedInstanceState.getString(STATE_SUMMARY_DATA_JSON));
  }

  @Nullable
  private SummaryData parseSummaryData(@Nullable String json) {
    if (json == null || json.trim().isEmpty()) {
      return null;
    }
    try {
      return GSON.fromJson(json, SummaryData.class);
    } catch (Exception ignored) {
      return null;
    }
  }

  private SummaryFeatureBundle resolveFeatureBundle(@Nullable String bundleJson) {
    if (bundleJson == null || bundleJson.trim().isEmpty()) {
      return null;
    }
    try {
      return GSON.fromJson(bundleJson, SummaryFeatureBundle.class);
    } catch (Exception ignored) {
      return null;
    }
  }

  private void rebuildExpressionPromptIndex(@Nullable SummaryFeatureBundle bundle) {
    expressionPromptByTypeAndBefore.clear();
    expressionPromptByBefore.clear();
    if (bundle == null || bundle.getExpressionCandidates() == null) {
      return;
    }

    for (SummaryFeatureBundle.ExpressionCandidate candidate : bundle.getExpressionCandidates()) {
      if (candidate == null) {
        continue;
      }

      String type = trimToNull(candidate.getType());
      String before = trimToNull(candidate.getBefore());
      String prompt = trimToNull(candidate.getKoreanPrompt());
      if (before == null || prompt == null) {
        continue;
      }

      if (type != null) {
        String typedKey = buildExpressionPromptKey(type, before);
        if (!expressionPromptByTypeAndBefore.containsKey(typedKey)) {
          expressionPromptByTypeAndBefore.put(typedKey, prompt);
        }
      }

      String beforeKey = normalize(before);
      if (!expressionPromptByBefore.containsKey(beforeKey)) {
        expressionPromptByBefore.put(beforeKey, prompt);
      }
    }
  }

  @Nullable
  private String resolveSourceExpressionPrompt(@Nullable String type, @Nullable String before) {
    String safeBefore = trimToNull(before);
    if (safeBefore == null) {
      return null;
    }

    String safeType = trimToNull(type);
    if (safeType != null) {
      String typedPrompt =
          expressionPromptByTypeAndBefore.get(buildExpressionPromptKey(safeType, safeBefore));
      if (typedPrompt != null) {
        return typedPrompt;
      }
    }
    return expressionPromptByBefore.get(normalize(safeBefore));
  }

  @NonNull
  private String buildExpressionPromptKey(@Nullable String type, @Nullable String before) {
    return normalize(type) + "||" + normalize(before);
  }

  private void startWordExtractionIfPossible() {
    if (featureBundle == null) {
      setWordHidden();
      return;
    }

    List<String> words = collectWordSeeds(featureBundle.getWordCandidates());
    List<String> sentences = collectSentenceCandidates(featureBundle.getSentenceCandidates());
    List<String> userOriginals =
        collectSentenceCandidates(featureBundle.getUserOriginalSentences());
    if (words.isEmpty() || sentences.isEmpty()) {
      setWordHidden();
      return;
    }

    wordLoadState.setValue(WordLoadState.loading());
    final int currentToken = requestToken;
    sessionSummaryLlmManager.extractWordsFromSentencesAsync(
        words,
        sentences,
        userOriginals,
        new ISessionSummaryLlmManager.WordExtractionCallback() {
          @Override
          public void onSuccess(@NonNull List<ISessionSummaryLlmManager.ExtractedWord> words) {
            if (!isRequestActive(currentToken)) {
              return;
            }

            List<SummaryData.WordItem> apiWords = toApiWordItems(words);
            applyWordsToSummary(apiWords);
            if (apiWords.isEmpty()) {
              setWordEmpty();
              return;
            }
            wordLoadState.setValue(WordLoadState.ready());
          }

          @Override
          public void onFailure(@NonNull String error) {
            if (!isRequestActive(currentToken)) {
              return;
            }
            applyWordsToSummary(new ArrayList<>());
            setWordError(error);
          }
        });
  }

  @NonNull
  private List<String> collectWordSeeds(
      @Nullable List<SummaryFeatureBundle.WordCandidate> candidates) {
    List<String> result = new ArrayList<>();
    if (candidates == null) {
      return result;
    }
    for (SummaryFeatureBundle.WordCandidate candidate : candidates) {
      if (candidate == null) {
        continue;
      }
      String english = trimToNull(candidate.getEnglish());
      if (english == null) {
        continue;
      }
      if (!containsNormalized(result, english)) {
        result.add(english);
      }
    }
    return result;
  }

  @NonNull
  private List<String> collectSentenceCandidates(@Nullable List<String> candidates) {
    List<String> result = new ArrayList<>();
    if (candidates == null) {
      return result;
    }
    for (String candidate : candidates) {
      String sentence = trimToNull(candidate);
      if (sentence == null) {
        continue;
      }
      if (!containsNormalized(result, sentence)) {
        result.add(sentence);
      }
    }
    return result;
  }

  private boolean containsNormalized(@NonNull List<String> values, @NonNull String target) {
    String normalizedTarget = normalize(target);
    for (String value : values) {
      if (normalize(value).equals(normalizedTarget)) {
        return true;
      }
    }
    return false;
  }

  @NonNull
  private List<SummaryData.WordItem> toApiWordItems(
      @Nullable List<ISessionSummaryLlmManager.ExtractedWord> extractedWords) {
    List<SummaryData.WordItem> result = new ArrayList<>();
    if (extractedWords == null || extractedWords.isEmpty()) {
      return result;
    }
    Set<String> seen = new HashSet<>();
    for (ISessionSummaryLlmManager.ExtractedWord extracted : extractedWords) {
      if (extracted == null || result.size() >= MAX_WORDS) {
        continue;
      }
      String english = trimToNull(extracted.getEn());
      String korean = trimToNull(extracted.getKo());
      if (english == null || korean == null) {
        continue;
      }
      String key = normalize(english);
      if (seen.contains(key)) {
        continue;
      }
      seen.add(key);
      String exampleEnglish = firstNonBlank(extracted.getExampleEn(), english);
      String exampleKorean = firstNonBlank(extracted.getExampleKo(), korean);
      result.add(new SummaryData.WordItem(english, korean, exampleEnglish, exampleKorean));
    }
    return result;
  }

  private void applyWordsToSummary(@NonNull List<SummaryData.WordItem> words) {
    SummaryData current = summaryData.getValue();
    if (current == null) {
      current = new SessionSummaryGenerator().createEmptySummary();
    }
    SummaryData updated = new SummaryData();
    updated.setTotalScore(current.getTotalScore());
    updated.setHighlights(current.getHighlights());
    updated.setExpressions(current.getExpressions());
    updated.setWords(new ArrayList<>(words));
    updated.setLikedSentences(current.getLikedSentences());
    summaryData.setValue(updated);
  }

  @Nullable
  private String trimToNull(@Nullable String text) {
    if (text == null) {
      return null;
    }
    String trimmed = text.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  @NonNull
  private String normalize(@Nullable String text) {
    return text == null ? "" : text.trim().toLowerCase();
  }

  @NonNull
  private String firstNonBlank(@Nullable String first, @NonNull String fallback) {
    String value = trimToNull(first);
    return value == null ? fallback : value;
  }

  private void setWordError(@Nullable String message) {
    wordLoadState.setValue(WordLoadState.error(message));
  }

  private void setWordHidden() {
    wordLoadState.setValue(WordLoadState.hidden());
  }

  private void setWordEmpty() {
    wordLoadState.setValue(WordLoadState.empty());
  }

  public static final class ExpressionLoadState {
    private final ExpressionLoadStatus status;
    @Nullable private final String errorMessage;

    private ExpressionLoadState(
        @NonNull ExpressionLoadStatus status, @Nullable String errorMessage) {
      this.status = status;
      this.errorMessage = errorMessage;
    }

    public static ExpressionLoadState loading() {
      return new ExpressionLoadState(ExpressionLoadStatus.LOADING, null);
    }

    public static ExpressionLoadState ready() {
      return new ExpressionLoadState(ExpressionLoadStatus.READY, null);
    }

    public static ExpressionLoadState error(@Nullable String errorMessage) {
      return new ExpressionLoadState(ExpressionLoadStatus.ERROR, errorMessage);
    }

    @NonNull
    public ExpressionLoadStatus getStatus() {
      return status;
    }

    @Nullable
    public String getErrorMessage() {
      return errorMessage;
    }
  }

  public enum ExpressionLoadStatus {
    LOADING,
    READY,
    ERROR
  }

  public static final class WordLoadState {
    private final WordLoadStatus status;
    @Nullable private final String errorMessage;

    private WordLoadState(@NonNull WordLoadStatus status, @Nullable String errorMessage) {
      this.status = status;
      this.errorMessage = errorMessage;
    }

    public static WordLoadState loading() {
      return new WordLoadState(WordLoadStatus.LOADING, null);
    }

    public static WordLoadState ready() {
      return new WordLoadState(WordLoadStatus.READY, null);
    }

    public static WordLoadState hidden() {
      return new WordLoadState(WordLoadStatus.HIDDEN, null);
    }

    public static WordLoadState empty() {
      return new WordLoadState(WordLoadStatus.EMPTY, null);
    }

    public static WordLoadState error(@Nullable String errorMessage) {
      return new WordLoadState(WordLoadStatus.ERROR, errorMessage);
    }

    @NonNull
    public WordLoadStatus getStatus() {
      return status;
    }

    @Nullable
    public String getErrorMessage() {
      return errorMessage;
    }
  }

  public enum WordLoadStatus {
    LOADING,
    READY,
    HIDDEN,
    EMPTY,
    ERROR
  }
}
