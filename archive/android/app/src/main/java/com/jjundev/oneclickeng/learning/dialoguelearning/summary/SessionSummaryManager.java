package com.jjundev.oneclickeng.learning.dialoguelearning.summary;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISessionSummaryLlmManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

/** One-shot LLM manager for summary section refinement. */
public class SessionSummaryManager implements ISessionSummaryLlmManager {
  private static final String TAG = "SessionSummaryManager";
  private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
  private static final String DEFAULT_MODEL_NAME = "gemini-3-flash-preview";
  private static final int MAX_WORDS = 12;
  private static final int CACHE_TTL_SECONDS = 3600; // 1 hour
  private static final int MIN_REMAINING_TTL_SECONDS = 300; // 5 minutes

  private static final String PREF_NAME = "gemini_session_summary_cache_prefs";
  private static final String KEY_EXPRESSION_CACHE_NAME =
      "gemini_session_summary_expression_cache_name_v3";
  private static final String KEY_EXPRESSION_CACHE_CREATED =
      "gemini_session_summary_expression_cache_created_at_v3";
  private static final String KEY_EXPRESSION_CACHE_TTL =
      "gemini_session_summary_expression_cache_ttl_seconds_v3";
  private static final String KEY_WORD_CACHE_NAME = "gemini_session_summary_word_cache_name_v2";
  private static final String KEY_WORD_CACHE_CREATED = "gemini_session_summary_word_cache_created";
  private static final String KEY_WORD_CACHE_TTL = "gemini_session_summary_word_cache_ttl_seconds";

  private static final String DISPLAY_NAME_EXPRESSION_FILTER =
      "SessionSummaryExpressionFilterPrompt_v3";
  private static final String DISPLAY_NAME_WORD_EXTRACTION =
      "SessionSummaryWordExtractionPrompt_v2";
  private static final String EXPRESSION_FILTER_PROMPT_ASSET_PATH =
      "prompts/session_summary/expression_filter_system_prompt.md";
  private static final String WORD_EXTRACTION_PROMPT_ASSET_PATH =
      "prompts/session_summary/word_extraction_system_prompt.md";

  private final OkHttpClient client;
  private final OkHttpClient streamingClient;
  private final Gson gson;
  private final Handler mainHandler;
  private final String apiKey;
  private final String modelName;
  private final Context context;
  private final SharedPreferences prefs;
  private final CacheState expressionFilterCache = new CacheState();
  private final CacheState wordExtractionCache = new CacheState();
  private final Object promptCacheLock = new Object();

  @Nullable private volatile String expressionFilterSystemPromptCache;
  @Nullable private volatile String wordExtractionSystemPromptCache;

  private enum CacheType {
    EXPRESSION_FILTER,
    WORD_EXTRACTION
  }

  private enum CacheValidationStatus {
    VALID,
    INVALID,
    ERROR
  }

  private static final class CacheState {
    private final Object lock = new Object();
    @Nullable private String cachedContentName;
    private boolean cacheReady = false;
  }

  private static final class CacheValidationResult {
    private final CacheValidationStatus status;
    private final long remainingSeconds;

    private CacheValidationResult(CacheValidationStatus status, long remainingSeconds) {
      this.status = status;
      this.remainingSeconds = remainingSeconds;
    }

    private static CacheValidationResult valid(long remainingSeconds) {
      return new CacheValidationResult(CacheValidationStatus.VALID, remainingSeconds);
    }

    private static CacheValidationResult invalid() {
      return new CacheValidationResult(CacheValidationStatus.INVALID, 0);
    }

    private static CacheValidationResult error() {
      return new CacheValidationResult(CacheValidationStatus.ERROR, 0);
    }
  }

  public SessionSummaryManager(Context context, String apiKey, String modelName) {
    this.context = context.getApplicationContext();
    this.apiKey = normalizeOrDefault(apiKey, "");
    this.modelName = normalizeOrDefault(modelName, DEFAULT_MODEL_NAME);
    this.client =
        new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    this.streamingClient =
        new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    this.gson = new Gson();
    this.mainHandler = new Handler(Looper.getMainLooper());
    this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
  }

  @Override
  public void filterExpressionsAsync(
      @NonNull SummaryFeatureBundle bundle,
      @NonNull ISessionSummaryLlmManager.ExpressionFilterCallback callback) {
    if (bundle == null) {
      mainHandler.post(() -> callback.onFailure("Feature bundle is null"));
      return;
    }
    if (apiKey == null || apiKey.trim().isEmpty()) {
      mainHandler.post(() -> callback.onFailure("API key is missing"));
      return;
    }

    new Thread(
            () -> {
              try {
                String cachedContentName = ensureCacheReady(CacheType.EXPRESSION_FILTER);
                boolean completed =
                    executeExpressionFilterRequest(bundle, callback, cachedContentName, true);
                if (!completed) {
                  invalidateCache(CacheType.EXPRESSION_FILTER);
                  executeExpressionFilterRequest(bundle, callback, null, false);
                }
              } catch (Exception e) {
                mainHandler.post(
                    () -> callback.onFailure("Expression filter error: " + e.getMessage()));
              }
            })
        .start();
  }

  @Override
  public void extractWordsFromSentencesAsync(
      @NonNull List<String> words,
      @NonNull List<String> sentences,
      @NonNull List<String> userOriginalSentences,
      @NonNull ISessionSummaryLlmManager.WordExtractionCallback callback) {
    if (callback == null) {
      return;
    }
    if (words == null || words.isEmpty() || sentences == null || sentences.isEmpty()) {
      mainHandler.post(() -> callback.onFailure("Word extraction input is empty"));
      return;
    }
    if (apiKey == null || apiKey.trim().isEmpty()) {
      mainHandler.post(() -> callback.onFailure("API key is missing"));
      return;
    }

    new Thread(
            () -> {
              try {
                String cachedContentName = ensureCacheReady(CacheType.WORD_EXTRACTION);
                boolean completed =
                    executeWordExtractionRequest(
                        words, sentences, userOriginalSentences, callback, cachedContentName, true);
                if (!completed) {
                  invalidateCache(CacheType.WORD_EXTRACTION);
                  executeWordExtractionRequest(
                      words, sentences, userOriginalSentences, callback, null, false);
                }
              } catch (Exception e) {
                mainHandler.post(() -> callback.onFailure("Summary LLM error: " + e.getMessage()));
              }
            })
        .start();
  }

  private boolean executeExpressionFilterRequest(
      @NonNull SummaryFeatureBundle bundle,
      @NonNull ISessionSummaryLlmManager.ExpressionFilterCallback callback,
      @Nullable String cachedContentName,
      boolean allowRetryWithoutCache)
      throws IOException {
    JsonObject requestBody = buildExpressionFilterRequestBody(bundle, cachedContentName);
    String url = BASE_URL + "/models/" + modelName + ":streamGenerateContent?alt=sse&key=" + apiKey;
    Request request =
        new Request.Builder()
            .url(url)
            .post(RequestBody.create(gson.toJson(requestBody), MediaType.parse("application/json")))
            .build();

    try (Response response = streamingClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        int code = response.code();
        if (cachedContentName != null && allowRetryWithoutCache && (code == 400 || code == 404)) {
          return false;
        }
        mainHandler.post(() -> callback.onFailure("Expression filter request failed: " + code));
        return true;
      }

      if (response.body() == null) {
        mainHandler.post(() -> callback.onFailure("Expression filter response body is empty"));
        return true;
      }

      StringBuilder accumulated = new StringBuilder();
      int emittedCount = 0;

      BufferedSource source = response.body().source();
      while (!source.exhausted()) {
        String line = source.readUtf8Line();
        if (line == null || !line.startsWith("data: ")) {
          continue;
        }
        String json = line.substring(6).trim();
        if (json.equals("[DONE]")) {
          break;
        }

        String chunkText = extractTextFromSseChunk(json);
        if (chunkText.isEmpty()) {
          continue;
        }
        accumulated.append(chunkText);

        List<ISessionSummaryLlmManager.FilteredExpression> parsed =
            tryParseExpressions(accumulated.toString());
        for (int i = emittedCount; i < parsed.size(); i++) {
          ISessionSummaryLlmManager.FilteredExpression expr = parsed.get(i);
          mainHandler.post(() -> callback.onExpressionReceived(expr));
        }
        emittedCount = parsed.size();
      }

      List<ISessionSummaryLlmManager.FilteredExpression> finalParsed =
          tryParseExpressions(accumulated.toString());
      for (int i = emittedCount; i < finalParsed.size(); i++) {
        ISessionSummaryLlmManager.FilteredExpression expr = finalParsed.get(i);
        mainHandler.post(() -> callback.onExpressionReceived(expr));
      }

      mainHandler.post(callback::onComplete);
      return true;
    }
  }

  private boolean executeWordExtractionRequest(
      @NonNull List<String> words,
      @NonNull List<String> sentences,
      @NonNull List<String> userOriginalSentences,
      @NonNull ISessionSummaryLlmManager.WordExtractionCallback callback,
      @Nullable String cachedContentName,
      boolean allowRetryWithoutCache)
      throws IOException {
    JsonObject requestBody =
        buildWordExtractionRequestBody(words, sentences, userOriginalSentences, cachedContentName);
    String url = BASE_URL + "/models/" + modelName + ":generateContent?key=" + apiKey;
    Request request =
        new Request.Builder()
            .url(url)
            .post(RequestBody.create(gson.toJson(requestBody), MediaType.parse("application/json")))
            .build();

    try (Response response = client.newCall(request).execute()) {
      String body = response.body() != null ? response.body().string() : "";
      if (!response.isSuccessful()) {
        int code = response.code();
        if (cachedContentName != null && allowRetryWithoutCache && (code == 400 || code == 404)) {
          return false;
        }
        mainHandler.post(() -> callback.onFailure("Summary LLM request failed: " + code));
        return true;
      }

      String responseText = extractFirstTextPart(body);
      List<ISessionSummaryLlmManager.ExtractedWord> extractedWords =
          parseExtractedWordsPayload(responseText);
      mainHandler.post(() -> callback.onSuccess(extractedWords));
      return true;
    }
  }

  private JsonObject buildExpressionFilterRequestBody(
      @NonNull SummaryFeatureBundle bundle, @Nullable String cachedContentName) {
    JsonObject requestBody = new JsonObject();
    if (cachedContentName != null) {
      requestBody.addProperty("cachedContent", cachedContentName);
    } else {
      addExpressionFilterSystemInstruction(requestBody);
    }

    JsonArray contents = new JsonArray();
    JsonObject userContent = new JsonObject();
    userContent.addProperty("role", "user");
    JsonArray parts = new JsonArray();
    JsonObject part = new JsonObject();
    part.addProperty("text", buildExpressionFilterUserPrompt(bundle));
    parts.add(part);
    userContent.add("parts", parts);
    contents.add(userContent);
    requestBody.add("contents", contents);

    JsonObject generationConfig = new JsonObject();
    generationConfig.addProperty("responseMimeType", "application/json");
    requestBody.add("generationConfig", generationConfig);
    return requestBody;
  }

  private JsonObject buildWordExtractionRequestBody(
      @NonNull List<String> words,
      @NonNull List<String> sentences,
      @NonNull List<String> userOriginalSentences,
      @Nullable String cachedContentName) {
    JsonObject requestBody = new JsonObject();
    if (cachedContentName != null) {
      requestBody.addProperty("cachedContent", cachedContentName);
    } else {
      addWordExtractionSystemInstruction(requestBody);
    }

    JsonArray contents = new JsonArray();
    JsonObject userContent = new JsonObject();
    userContent.addProperty("role", "user");
    JsonArray parts = new JsonArray();
    JsonObject part = new JsonObject();
    part.addProperty(
        "text", buildWordExtractionUserPrompt(words, sentences, userOriginalSentences));
    parts.add(part);
    userContent.add("parts", parts);
    contents.add(userContent);
    requestBody.add("contents", contents);

    JsonObject generationConfig = new JsonObject();
    generationConfig.addProperty("responseMimeType", "application/json");
    requestBody.add("generationConfig", generationConfig);
    return requestBody;
  }

  @Nullable
  private String ensureCacheReady(@NonNull CacheType cacheType) {
    CacheState state = getCacheState(cacheType);
    synchronized (state.lock) {
      if (state.cacheReady && state.cachedContentName != null) {
        return state.cachedContentName;
      }

      String cachedContentName = prefs.getString(getCacheNameKey(cacheType), null);
      if (cachedContentName != null) {
        long createdAt = prefs.getLong(getCacheCreatedKey(cacheType), 0);
        int ttl = prefs.getInt(getCacheTtlKey(cacheType), CACHE_TTL_SECONDS);
        long elapsedSeconds = (System.currentTimeMillis() - createdAt) / 1000L;

        if (elapsedSeconds <= ttl) {
          CacheValidationResult validationResult = validateCacheFromServer(cachedContentName);
          if (validationResult.status == CacheValidationStatus.VALID
              && validationResult.remainingSeconds > MIN_REMAINING_TTL_SECONDS) {
            state.cachedContentName = cachedContentName;
            state.cacheReady = true;
            return cachedContentName;
          }
        }
        clearLocalCacheData(cacheType);
      }

      String newCacheName = createCache(cacheType);
      if (newCacheName != null) {
        state.cachedContentName = newCacheName;
        state.cacheReady = true;
        return newCacheName;
      }

      state.cachedContentName = null;
      state.cacheReady = false;
      return null;
    }
  }

  @Nullable
  private String createCache(@NonNull CacheType cacheType) {
    try {
      JsonObject requestBody = new JsonObject();
      requestBody.addProperty("model", "models/" + modelName);

      JsonArray contents = new JsonArray();

      JsonObject userContent = new JsonObject();
      userContent.addProperty("role", "user");
      JsonArray userParts = new JsonArray();
      JsonObject userPart = new JsonObject();
      userPart.addProperty("text", getCacheInitUserPrompt(cacheType));
      userParts.add(userPart);
      userContent.add("parts", userParts);
      contents.add(userContent);

      JsonObject modelContent = new JsonObject();
      modelContent.addProperty("role", "model");
      JsonArray modelParts = new JsonArray();
      JsonObject modelPart = new JsonObject();
      modelPart.addProperty("text", getCacheInitModelPrompt(cacheType));
      modelParts.add(modelPart);
      modelContent.add("parts", modelParts);
      contents.add(modelContent);

      requestBody.add("contents", contents);
      addSystemInstructionForCacheType(requestBody, cacheType);
      requestBody.addProperty("ttl", CACHE_TTL_SECONDS + "s");
      requestBody.addProperty("displayName", getCacheDisplayName(cacheType));

      String url = BASE_URL + "/cachedContents?key=" + apiKey;
      Request request =
          new Request.Builder()
              .url(url)
              .post(
                  RequestBody.create(gson.toJson(requestBody), MediaType.parse("application/json")))
              .build();

      try (Response response = client.newCall(request).execute()) {
        String body = response.body() != null ? response.body().string() : "";
        if (!response.isSuccessful()) {
          Log.w(TAG, "Cache creation failed(" + cacheType + "): " + response.code() + " " + body);
          return null;
        }

        JsonObject result = JsonParser.parseString(body).getAsJsonObject();
        String cacheName = result.get("name").getAsString();
        saveLocalCacheData(cacheType, cacheName);
        Log.i(TAG, "Cache created(" + cacheType + "): " + cacheName);
        return cacheName;
      }
    } catch (Exception e) {
      Log.e(TAG, "createCache failed(" + cacheType + ")", e);
      return null;
    }
  }

  private CacheValidationResult validateCacheFromServer(@NonNull String cacheName) {
    try {
      String url = BASE_URL + "/" + cacheName + "?key=" + apiKey;
      Request request = new Request.Builder().url(url).get().build();
      try (Response response = client.newCall(request).execute()) {
        if (response.code() == 404) {
          return CacheValidationResult.invalid();
        }
        if (!response.isSuccessful()) {
          return CacheValidationResult.error();
        }
        String body = response.body() != null ? response.body().string() : "";
        JsonObject result = JsonParser.parseString(body).getAsJsonObject();
        if (!result.has("expireTime")) {
          return CacheValidationResult.error();
        }

        Instant expireTime = Instant.parse(result.get("expireTime").getAsString());
        long remainingSeconds = expireTime.getEpochSecond() - Instant.now().getEpochSecond();
        return CacheValidationResult.valid(remainingSeconds);
      }
    } catch (Exception e) {
      Log.w(TAG, "validateCacheFromServer failed: " + cacheName, e);
      return CacheValidationResult.error();
    }
  }

  private void invalidateCache(@NonNull CacheType cacheType) {
    CacheState state = getCacheState(cacheType);
    synchronized (state.lock) {
      state.cachedContentName = null;
      state.cacheReady = false;
      clearLocalCacheData(cacheType);
    }
  }

  private CacheState getCacheState(@NonNull CacheType cacheType) {
    return cacheType == CacheType.EXPRESSION_FILTER ? expressionFilterCache : wordExtractionCache;
  }

  private String getCacheNameKey(@NonNull CacheType cacheType) {
    return cacheType == CacheType.EXPRESSION_FILTER
        ? KEY_EXPRESSION_CACHE_NAME
        : KEY_WORD_CACHE_NAME;
  }

  private String getCacheCreatedKey(@NonNull CacheType cacheType) {
    return cacheType == CacheType.EXPRESSION_FILTER
        ? KEY_EXPRESSION_CACHE_CREATED
        : KEY_WORD_CACHE_CREATED;
  }

  private String getCacheTtlKey(@NonNull CacheType cacheType) {
    return cacheType == CacheType.EXPRESSION_FILTER ? KEY_EXPRESSION_CACHE_TTL : KEY_WORD_CACHE_TTL;
  }

  private String getCacheDisplayName(@NonNull CacheType cacheType) {
    return cacheType == CacheType.EXPRESSION_FILTER
        ? DISPLAY_NAME_EXPRESSION_FILTER
        : DISPLAY_NAME_WORD_EXTRACTION;
  }

  private void saveLocalCacheData(@NonNull CacheType cacheType, @NonNull String cacheName) {
    prefs
        .edit()
        .putString(getCacheNameKey(cacheType), cacheName)
        .putLong(getCacheCreatedKey(cacheType), System.currentTimeMillis())
        .putInt(getCacheTtlKey(cacheType), CACHE_TTL_SECONDS)
        .apply();
  }

  private void clearLocalCacheData(@NonNull CacheType cacheType) {
    prefs
        .edit()
        .remove(getCacheNameKey(cacheType))
        .remove(getCacheCreatedKey(cacheType))
        .remove(getCacheTtlKey(cacheType))
        .apply();
  }

  private String getCacheInitUserPrompt(@NonNull CacheType cacheType) {
    if (cacheType == CacheType.EXPRESSION_FILTER) {
      return "Initialize session summary expression filtering.";
    }
    return "Initialize session summary word extraction.";
  }

  private String getCacheInitModelPrompt(@NonNull CacheType cacheType) {
    if (cacheType == CacheType.EXPRESSION_FILTER) {
      return "I am ready to filter and rank learning expressions.";
    }
    return "I am ready to extract learning vocabulary in JSON format.";
  }

  private void addSystemInstructionForCacheType(
      @NonNull JsonObject requestBody, @NonNull CacheType cacheType) {
    if (cacheType == CacheType.EXPRESSION_FILTER) {
      addExpressionFilterSystemInstruction(requestBody);
      return;
    }
    addWordExtractionSystemInstruction(requestBody);
  }

  private void addExpressionFilterSystemInstruction(@NonNull JsonObject requestBody) {
    addSystemInstruction(requestBody, buildExpressionFilterSystemPrompt());
  }

  private void addWordExtractionSystemInstruction(@NonNull JsonObject requestBody) {
    addSystemInstruction(requestBody, buildWordExtractionSystemPrompt());
  }

  private void addSystemInstruction(@NonNull JsonObject requestBody, @NonNull String promptText) {
    JsonObject systemInstruction = new JsonObject();
    JsonArray systemParts = new JsonArray();
    JsonObject systemPart = new JsonObject();
    systemPart.addProperty("text", promptText);
    systemParts.add(systemPart);
    systemInstruction.add("parts", systemParts);
    requestBody.add("systemInstruction", systemInstruction);
  }

  private String buildExpressionFilterSystemPrompt() {
    String cachedPrompt = expressionFilterSystemPromptCache;
    if (cachedPrompt != null && !cachedPrompt.isEmpty()) {
      return cachedPrompt;
    }
    synchronized (promptCacheLock) {
      if (expressionFilterSystemPromptCache != null
          && !expressionFilterSystemPromptCache.isEmpty()) {
        return expressionFilterSystemPromptCache;
      }
      String prompt = readAssetFile(EXPRESSION_FILTER_PROMPT_ASSET_PATH);
      if (prompt.isEmpty()) {
        prompt = buildExpressionFilterSystemPromptFallback();
      }
      expressionFilterSystemPromptCache = prompt;
      return prompt;
    }
  }

  private String buildWordExtractionSystemPrompt() {
    String cachedPrompt = wordExtractionSystemPromptCache;
    if (cachedPrompt != null && !cachedPrompt.isEmpty()) {
      return cachedPrompt;
    }
    synchronized (promptCacheLock) {
      if (wordExtractionSystemPromptCache != null && !wordExtractionSystemPromptCache.isEmpty()) {
        return wordExtractionSystemPromptCache;
      }
      String prompt = readAssetFile(WORD_EXTRACTION_PROMPT_ASSET_PATH);
      if (prompt.isEmpty()) {
        prompt = buildWordExtractionSystemPromptFallback();
      }
      wordExtractionSystemPromptCache = prompt;
      return prompt;
    }
  }

  private String buildExpressionFilterSystemPromptFallback() {
    return "You are an English learning expression filter for Korean learners.\n"
        + "Return JSON only with this exact top-level shape:\n"
        + "{\n"
        + "  \"expressions\": [{\"type\":\"...\",\"koreanPrompt\":\"...\",\"before\":\"...\",\"after\":\"...\",\"explanation\":\"...\"}]\n"
        + "}\n"
        + "Rules:\n"
        + "1) Select ONLY expressions that are genuinely useful for the learner.\n"
        + "2) Remove trivial or redundant corrections (e.g. minor capitalisation, article-only fixes).\n"
        + "3) Prioritise expressions that teach new grammar patterns, natural phrasing, or idiomatic usage.\n"
        + "4) Re-order selected expressions from most educational to least.\n"
        + "5) expressions.before must exactly reuse the user's original English sentence.\n"
        + "6) In expressions.after, wrap the key improved phrase with [[...]] (one or more allowed).\n"
        + "7) expressions.type must be exactly one of \"자연스러운 표현\" or \"정확한 표현\".\n"
        + "8) Map input types grammar/word_choice/sentence_structure to \"정확한 표현\".\n"
        + "9) Map input types naturalness/idiom/collocation to \"자연스러운 표현\".\n"
        + "10) If type is unknown, empty, or non-standard, default to \"자연스러운 표현\".\n"
        + "11) expressions.koreanPrompt must exactly reuse input koreanPrompt (original Korean sentence).\n"
        + "12) Keep other Korean fields natural and concise.\n"
        + "13) Do not include markdown code fences.\n"
        + "14) Do not invent facts outside input.";
  }

  private String buildExpressionFilterUserPrompt(SummaryFeatureBundle bundle) {
    JsonObject payload = new JsonObject();
    payload.addProperty("totalScore", bundle.getTotalScore());
    payload.add("expressionCandidates", gson.toJsonTree(bundle.getExpressionCandidates()));

    return "Filter and reorder the expression candidates. Keep only those genuinely useful for learning.\n"
        + "Input data:\n"
        + gson.toJson(payload);
  }

  private String buildWordExtractionSystemPromptFallback() {
    return "You are an English vocabulary extractor for Korean learners.\n"
        + "Your goal: identify words the learner likely encountered for the FIRST TIME.\n"
        + "Focus on:\n"
        + "- Words in corrected/natural sentences that do NOT appear in the user's original writing\n"
        + "- Words with nuanced meanings a Korean speaker might confuse\n"
        + "- Semi-formal or academic vocabulary that expands the learner's range\n"
        + "Exclude:\n"
        + "- Basic/common words (go, make, think, want, get, etc.)\n"
        + "- Words the user already used correctly in their original sentences\n"
        + "Return JSON only with this exact top-level shape:\n"
        + "{\n"
        + "  \"items\": [\n"
        + "    {\"en\":\"...\",\"ko\":\"...\",\"example\":{\"en\":\"...\",\"ko\":\"...\"}}\n"
        + "  ]\n"
        + "}\n"
        + "Rules:\n"
        + "1) Use only words supported by provided words/sentences context.\n"
        + "2) en and ko are required and non-empty.\n"
        + "3) example.en and example.ko are required and non-empty.\n"
        + "4) example.en should be concise English sentence and example.ko should be Korean translation.\n"
        + "5) Do not include markdown code fences.";
  }

  private String buildWordExtractionUserPrompt(
      List<String> words, List<String> sentences, List<String> userOriginalSentences) {
    JsonObject payload = new JsonObject();
    payload.add("words", gson.toJsonTree(words));
    payload.add("sentences", gson.toJsonTree(sentences));
    payload.add("userOriginalSentences", gson.toJsonTree(userOriginalSentences));
    return "Extract words the learner likely learned NEW from corrected/natural versions.\n"
        + "Compare userOriginalSentences with sentences to find words the user didn't know.\n"
        + "Input data:\n"
        + gson.toJson(payload);
  }

  private String readAssetFile(String fileName) {
    try (InputStream is = context.getAssets().open(fileName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
      StringBuilder sb = new StringBuilder();
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
      return sb.toString().trim();
    } catch (IOException e) {
      Log.e(TAG, "Failed to read asset file: " + fileName, e);
      return "";
    }
  }

  private String extractFirstTextPart(String body) {
    try {
      JsonObject root = JsonParser.parseString(body).getAsJsonObject();
      JsonArray candidates = root.getAsJsonArray("candidates");
      if (candidates == null || candidates.size() == 0) {
        return "";
      }
      JsonObject candidate = candidates.get(0).getAsJsonObject();
      JsonObject content = candidate.getAsJsonObject("content");
      if (content == null) {
        return "";
      }
      JsonArray parts = content.getAsJsonArray("parts");
      if (parts == null || parts.size() == 0) {
        return "";
      }
      JsonObject firstPart = parts.get(0).getAsJsonObject();
      if (firstPart == null || !firstPart.has("text")) {
        return "";
      }
      return firstPart.get("text").getAsString();
    } catch (Exception ignored) {
      return "";
    }
  }

  private String extractTextFromSseChunk(String json) {
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      JsonArray candidates = root.getAsJsonArray("candidates");
      if (candidates == null || candidates.size() == 0) {
        return "";
      }
      JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
      JsonArray partsArr = content.getAsJsonArray("parts");
      if (partsArr == null || partsArr.size() == 0) {
        return "";
      }
      return partsArr.get(0).getAsJsonObject().get("text").getAsString();
    } catch (Exception e) {
      return "";
    }
  }

  /**
   * Best-effort incremental parser: attempts to parse completed expression objects from accumulated
   * JSON text. Returns all fully-parsed expressions found so far.
   */
  private List<ISessionSummaryLlmManager.FilteredExpression> tryParseExpressions(
      String accumulatedText) {
    List<ISessionSummaryLlmManager.FilteredExpression> result = new ArrayList<>();
    String cleanJson = accumulatedText.replace("```json", "").replace("```", "").trim();

    // Find the start of the expressions array
    int arrStart = cleanJson.indexOf('[');
    if (arrStart < 0) {
      return result;
    }
    String fromArray = cleanJson.substring(arrStart);

    // Walk through the string and try to extract complete JSON objects
    int depth = 0;
    int objStart = -1;
    for (int i = 0; i < fromArray.length(); i++) {
      char c = fromArray.charAt(i);
      if (c == '{') {
        if (depth == 0) {
          objStart = i;
        }
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0 && objStart >= 0) {
          String objStr = fromArray.substring(objStart, i + 1);
          ISessionSummaryLlmManager.FilteredExpression expr = tryParseSingleExpression(objStr);
          if (expr != null) {
            result.add(expr);
          }
          objStart = -1;
        }
      }
    }
    return result;
  }

  private ISessionSummaryLlmManager.FilteredExpression tryParseSingleExpression(String jsonStr) {
    try {
      JsonObject item = JsonParser.parseString(jsonStr).getAsJsonObject();
      String type = trimToNull(readAsString(item, "type"));
      String prompt = trimToNull(readAsString(item, "koreanPrompt"));
      String before = trimToNull(readAsString(item, "before"));
      String after = sanitizeExpressionAfterForDisplay(readAsString(item, "after"));
      String explanation = trimToNull(readAsString(item, "explanation"));
      if (type == null
          || prompt == null
          || before == null
          || after == null
          || explanation == null) {
        return null;
      }
      return new ISessionSummaryLlmManager.FilteredExpression(
          type, prompt, before, after, explanation);
    } catch (Exception e) {
      return null;
    }
  }

  private List<HighlightSection> limitAndSanitizeHighlights(
      List<HighlightSection> input, int maxCount) {
    List<HighlightSection> result = new ArrayList<>();
    if (input == null) {
      return result;
    }
    for (HighlightSection item : input) {
      if (item == null) {
        continue;
      }
      String english = trimToNull(item.getEnglish());
      String korean = trimToNull(item.getKorean());
      String reason = trimToNull(item.getReason());
      if (english == null || korean == null || reason == null) {
        continue;
      }
      result.add(new HighlightSection(english, korean, reason));
      if (result.size() >= maxCount) {
        break;
      }
    }
    return result;
  }

  private List<ExpressionSection> limitAndSanitizeExpressions(
      List<ExpressionSection> input, int maxCount) {
    List<ExpressionSection> result = new ArrayList<>();
    if (input == null) {
      return result;
    }
    for (ExpressionSection item : input) {
      if (item == null) {
        continue;
      }
      String type = trimToNull(item.getType());
      String prompt = trimToNull(item.getKoreanPrompt());
      String before = trimToNull(item.getBefore());
      String after = sanitizeExpressionAfterForDisplay(item.getAfter());
      String explanation = trimToNull(item.getExplanation());
      if (type == null
          || prompt == null
          || before == null
          || after == null
          || explanation == null) {
        continue;
      }
      if (normalize(before).equals(normalize(after))) {
        continue;
      }
      result.add(new ExpressionSection(type, prompt, before, after, explanation));
      if (result.size() >= maxCount) {
        break;
      }
    }
    return result;
  }

  private List<WordSection> limitAndSanitizeWords(List<WordSection> input, int maxCount) {
    List<WordSection> result = new ArrayList<>();
    if (input == null) {
      return result;
    }
    for (WordSection item : input) {
      if (item == null) {
        continue;
      }
      String english = trimToNull(item.getEnglish());
      String korean = trimToNull(item.getKorean());
      String exampleEnglish = trimToNull(item.getExampleEnglish());
      String exampleKorean = trimToNull(item.getExampleKorean());
      if (english == null || korean == null) {
        continue;
      }
      if (exampleEnglish == null) {
        exampleEnglish = english;
      }
      if (exampleKorean == null) {
        exampleKorean = korean;
      }
      result.add(new WordSection(english, korean, exampleEnglish, exampleKorean));
      if (result.size() >= maxCount) {
        break;
      }
    }
    return result;
  }

  static List<ISessionSummaryLlmManager.ExtractedWord> parseExtractedWordsPayload(
      String rawPayload) {
    List<ISessionSummaryLlmManager.ExtractedWord> result = new ArrayList<>();
    String cleanJson = stripJsonFence(rawPayload);
    if (cleanJson.isEmpty()) {
      return result;
    }

    JsonArray items = null;
    try {
      if (cleanJson.trim().startsWith("[")) {
        items = JsonParser.parseString(cleanJson).getAsJsonArray();
      } else {
        JsonObject root = JsonParser.parseString(cleanJson).getAsJsonObject();
        if (root.has("items") && root.get("items").isJsonArray()) {
          items = root.getAsJsonArray("items");
        }
      }
    } catch (Exception ignored) {
      return result;
    }

    if (items == null) {
      return result;
    }

    Set<String> seen = new HashSet<>();
    for (int i = 0; i < items.size(); i++) {
      if (!items.get(i).isJsonObject()) {
        continue;
      }
      JsonObject item = items.get(i).getAsJsonObject();
      String en = trimToNullStatic(readAsString(item, "en"));
      String ko = trimToNullStatic(readAsString(item, "ko"));
      JsonObject exampleObject = readAsObject(item, "example");
      String exampleEn = trimToNullStatic(readAsString(exampleObject, "en"));
      String exampleKo = trimToNullStatic(readAsString(exampleObject, "ko"));
      if (en == null
          || ko == null
          || exampleObject == null
          || exampleEn == null
          || exampleKo == null) {
        continue;
      }
      String key = normalizeStatic(en);
      if (seen.contains(key)) {
        continue;
      }
      seen.add(key);
      result.add(new ISessionSummaryLlmManager.ExtractedWord(en, ko, exampleEn, exampleKo));
    }
    return result;
  }

  static String sanitizeExpressionAfterForDisplay(String rawAfter) {
    String value = trimToNullStatic(rawAfter);
    if (value == null) {
      return null;
    }

    int colonIndex = value.indexOf(':');
    if (colonIndex > 0) {
      String label = value.substring(0, colonIndex).trim();
      if ("after".equalsIgnoreCase(label)) {
        value = trimToNullStatic(value.substring(colonIndex + 1));
        if (value == null) {
          return null;
        }
      }
    }

    String withoutBrackets = value.replace("[", "").replace("]", "");
    StringBuilder filtered = new StringBuilder(withoutBrackets.length());
    for (int i = 0; i < withoutBrackets.length(); i++) {
      char c = withoutBrackets.charAt(i);
      if (isAllowedAfterCharacter(c) || Character.isWhitespace(c)) {
        filtered.append(c);
      } else {
        filtered.append(' ');
      }
    }

    String normalizedWhitespace = filtered.toString().replaceAll("\\s+", " ").trim();
    return normalizedWhitespace.isEmpty() ? null : normalizedWhitespace;
  }

  private static boolean isAllowedAfterCharacter(char c) {
    if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
      return true;
    }
    switch (c) {
      case '.':
      case ',':
      case '!':
      case '?':
      case '\'':
      case '"':
      case '(':
      case ')':
      case ':':
      case ';':
      case '-':
        return true;
      default:
        return false;
    }
  }

  private static String stripJsonFence(String rawPayload) {
    if (rawPayload == null) {
      return "";
    }
    return rawPayload.replace("```json", "").replace("```", "").trim();
  }

  private static String readAsString(JsonObject obj, String key) {
    try {
      if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
        return obj.get(key).getAsString();
      }
    } catch (Exception ignored) {
      // No-op
    }
    return null;
  }

  private static JsonObject readAsObject(JsonObject obj, String key) {
    try {
      if (obj != null && obj.has(key) && obj.get(key).isJsonObject()) {
        return obj.getAsJsonObject(key);
      }
    } catch (Exception ignored) {
      // No-op
    }
    return null;
  }

  private static String trimToNullStatic(String text) {
    if (text == null) {
      return null;
    }
    String trimmed = text.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String normalizeStatic(String text) {
    return text == null ? "" : text.trim().toLowerCase();
  }

  private String trimToNull(String text) {
    if (text == null) {
      return null;
    }
    String trimmed = text.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String normalize(String text) {
    return text == null ? "" : text.trim().toLowerCase();
  }

  private static String normalizeOrDefault(String value, String defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? defaultValue : trimmed;
  }

  public static class LlmSections {
    private List<HighlightSection> highlights;
    private List<ExpressionSection> expressions;
    private List<WordSection> words;

    public List<HighlightSection> getHighlights() {
      return highlights;
    }

    public void setHighlights(List<HighlightSection> highlights) {
      this.highlights = highlights;
    }

    public List<ExpressionSection> getExpressions() {
      return expressions;
    }

    public void setExpressions(List<ExpressionSection> expressions) {
      this.expressions = expressions;
    }

    public List<WordSection> getWords() {
      return words;
    }

    public void setWords(List<WordSection> words) {
      this.words = words;
    }
  }

  public static class HighlightSection {
    private String english;
    private String korean;
    private String reason;

    public HighlightSection() {}

    public HighlightSection(String english, String korean, String reason) {
      this.english = english;
      this.korean = korean;
      this.reason = reason;
    }

    public String getEnglish() {
      return english;
    }

    public String getKorean() {
      return korean;
    }

    public String getReason() {
      return reason;
    }
  }

  public static class ExpressionSection {
    private String type;
    private String koreanPrompt;
    private String before;
    private String after;
    private String explanation;

    public ExpressionSection() {}

    public ExpressionSection(
        String type, String koreanPrompt, String before, String after, String explanation) {
      this.type = type;
      this.koreanPrompt = koreanPrompt;
      this.before = before;
      this.after = after;
      this.explanation = explanation;
    }

    public String getType() {
      return type;
    }

    public String getKoreanPrompt() {
      return koreanPrompt;
    }

    public String getBefore() {
      return before;
    }

    public String getAfter() {
      return after;
    }

    public String getExplanation() {
      return explanation;
    }
  }

  public static class WordSection {
    private String english;
    private String korean;
    private String exampleEnglish;
    private String exampleKorean;

    public WordSection() {}

    public WordSection(String english, String korean, String exampleEnglish, String exampleKorean) {
      this.english = english;
      this.korean = korean;
      this.exampleEnglish = exampleEnglish;
      this.exampleKorean = exampleKorean;
    }

    public String getEnglish() {
      return english;
    }

    public String getKorean() {
      return korean;
    }

    public String getExampleEnglish() {
      return exampleEnglish;
    }

    public String getExampleKorean() {
      return exampleKorean;
    }
  }
}
