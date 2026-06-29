package com.jjundev.oneclickeng.manager_gemini;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISentenceFeedbackManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.ConceptualBridge;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.GrammarFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.NaturalnessFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.ParaphrasingLevel;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SentenceFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.ToneStyle;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.WritingScore;
import com.jjundev.oneclickeng.tool.IncrementalJsonSectionParser;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

/**
 * Gemini Feedback Manager (Gemini 2 Flash Preview) Integrated with Prompt Caching and Streaming
 * support.
 */
public class SentenceFeedbackManager
    implements com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts
        .ISentenceFeedbackManager {

  private static final String TAG = "SentenceFeedbackManager";
  private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
  private static final String DEFAULT_MODEL_NAME = "gemini-3.1-flash-lite-preview";
  private static final int CACHE_TTL_SECONDS = 3600; // 1 hour

  private static final String PREF_NAME = "gemini_cache_prefs";
  private static final String KEY_CACHE_NAME = "gemini_cache_name_v2";
  private static final String KEY_CACHE_CREATED = "gemini_cache_created_at";
  private static final String KEY_CACHE_TTL = "gemini_cache_ttl_seconds";
  private static final int MIN_REMAINING_TTL_SECONDS = 300; // 5 minutes

  private final OkHttpClient client;
  private final Gson gson;
  private final String apiKey;
  private final String modelName;
  private final Handler mainHandler;
  private final SharedPreferences prefs;

  private final Context context;
  private String cachedContentName;
  private boolean cacheReady = false;

  public interface FeedbackCallback {
    void onSuccess(SentenceFeedback feedback, boolean usedCache);

    void onError(String error);
  }

  interface ValidationCallback {
    void onValid(String cacheName, long remainingSeconds);

    void onInvalid();

    void onError(String error);
  }

  public SentenceFeedbackManager(Context context, String apiKey, String modelName) {
    this.context = context;
    this.apiKey = normalizeOrDefault(apiKey, "");
    this.modelName = normalizeOrDefault(modelName, DEFAULT_MODEL_NAME);
    this.client =
        new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();
    this.gson = new Gson();
    this.mainHandler = new Handler(Looper.getMainLooper());
    this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
  }

  private static final OkHttpClient streamingClient =
      new OkHttpClient.Builder()
          .connectTimeout(30, TimeUnit.SECONDS)
          .readTimeout(0, TimeUnit.SECONDS) // No read timeout for streaming
          .writeTimeout(30, TimeUnit.SECONDS)
          .build();

  /** Cache initialization - Caches system prompt and context. */
  @Override
  public void initializeCache(ISentenceFeedbackManager.InitCallback callback) {
    String savedCacheName = prefs.getString(KEY_CACHE_NAME, null);

    if (savedCacheName != null) {
      long createdAt = prefs.getLong(KEY_CACHE_CREATED, 0);
      int ttl = prefs.getInt(KEY_CACHE_TTL, CACHE_TTL_SECONDS);
      long elapsedSeconds = (System.currentTimeMillis() - createdAt) / 1000;

      if (elapsedSeconds > ttl) {
        Log.d(TAG, "[GeminiCache] Local cache expired, creating new one");
        clearLocalCacheData();
        createCache(callback);
        return;
      }

      Log.d(TAG, "[GeminiCache] Found local cache info, verifying with server...");
      validateCacheFromServer(
          savedCacheName,
          new ValidationCallback() {
            @Override
            public void onValid(String name, long remainingSeconds) {
              if (remainingSeconds > MIN_REMAINING_TTL_SECONDS) {
                Log.i(TAG, "[GeminiCache] REUSING EXISTING CACHE: " + name);
                cachedContentName = name;
                cacheReady = true;
                mainHandler.post(callback::onReady);
              } else {
                Log.i(TAG, "[GeminiCache] Cache expiring soon. CREATING NEW CACHE.");
                createCache(callback);
              }
            }

            @Override
            public void onInvalid() {
              Log.i(TAG, "[GeminiCache] Cache invalid on server. CREATING NEW CACHE.");
              clearLocalCacheData();
              createCache(callback);
            }

            @Override
            public void onError(String error) {
              Log.w(TAG, "[GeminiCache] Validation error: " + error + ". CREATING NEW CACHE.");
              createCache(callback);
            }
          });
    } else {
      Log.i(TAG, "[GeminiCache] No local cache found. CREATING NEW CACHE.");
      createCache(callback);
    }
  }

  private void createCache(ISentenceFeedbackManager.InitCallback callback) {
    new Thread(
            () -> {
              try {
                String systemPrompt = buildSystemPrompt();
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", "models/" + modelName);

                JsonArray contents = new JsonArray();
                JsonObject contextContent = new JsonObject();
                contextContent.addProperty("role", "user");
                JsonArray contextParts = new JsonArray();
                JsonObject contextPart = new JsonObject();
                contextPart.addProperty("text", buildContextMaterial());
                contextParts.add(contextPart);
                contextContent.add("parts", contextParts);
                contents.add(contextContent);

                JsonObject modelResponse = new JsonObject();
                modelResponse.addProperty("role", "model");
                JsonArray modelParts = new JsonArray();
                JsonObject modelPart = new JsonObject();
                modelPart.addProperty("text", "I understand. I am ready to evaluate sentences.");
                modelParts.add(modelPart);
                modelResponse.add("parts", modelParts);
                contents.add(modelResponse);

                requestBody.add("contents", contents);

                JsonObject sysInstruction = new JsonObject();
                JsonArray sysParts = new JsonArray();
                JsonObject sysPart = new JsonObject();
                sysPart.addProperty("text", systemPrompt);
                sysParts.add(sysPart);
                sysInstruction.add("parts", sysParts);
                requestBody.add("systemInstruction", sysInstruction);

                requestBody.addProperty("ttl", CACHE_TTL_SECONDS + "s");
                requestBody.addProperty("displayName", "SentenceFeedbackContext");

                String url = BASE_URL + "/cachedContents?key=" + apiKey;
                String jsonBody = gson.toJson(requestBody);

                Request request =
                    new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                  String responseBody = response.body() != null ? response.body().string() : "";
                  if (!response.isSuccessful()) {
                    mainHandler.post(
                        () -> callback.onError("Cache creation failed: " + responseBody));
                    return;
                  }

                  JsonObject result = JsonParser.parseString(responseBody).getAsJsonObject();
                  cachedContentName = result.get("name").getAsString();
                  cacheReady = true;

                  prefs
                      .edit()
                      .putString(KEY_CACHE_NAME, cachedContentName)
                      .putLong(KEY_CACHE_CREATED, System.currentTimeMillis())
                      .putInt(KEY_CACHE_TTL, CACHE_TTL_SECONDS)
                      .apply();

                  Log.i(TAG, "[GeminiCache] NEW CACHE CREATED: " + cachedContentName);
                  mainHandler.post(callback::onReady);
                }
              } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
              }
            })
        .start();
  }

  private void validateCacheFromServer(String cacheName, ValidationCallback callback) {
    new Thread(
            () -> {
              try {
                String url = BASE_URL + "/" + cacheName + "?key=" + apiKey;
                Request request = new Request.Builder().url(url).get().build();
                try (Response response = client.newCall(request).execute()) {
                  if (response.code() == 404) {
                    callback.onInvalid();
                    return;
                  }
                  if (!response.isSuccessful()) {
                    callback.onError("Server check failed: " + response.code());
                    return;
                  }
                  String responseBody = response.body().string();
                  JsonObject result = JsonParser.parseString(responseBody).getAsJsonObject();
                  if (result.has("expireTime")) {
                    Instant expireTime = Instant.parse(result.get("expireTime").getAsString());
                    long remainingSeconds =
                        expireTime.getEpochSecond() - Instant.now().getEpochSecond();
                    callback.onValid(cacheName, remainingSeconds);
                  } else {
                    callback.onError("No expireTime in response");
                  }
                }
              } catch (Exception e) {
                callback.onError(e.getMessage());
              }
            })
        .start();
  }

  private void clearLocalCacheData() {
    prefs.edit().remove(KEY_CACHE_NAME).remove(KEY_CACHE_CREATED).remove(KEY_CACHE_TTL).apply();
  }

  @Override
  public void analyzeSentenceStreaming(
      String originalSentence,
      String userSentence,
      ISentenceFeedbackManager.StreamingFeedbackCallback callback) {
    new Thread(
            () -> {
              try {
                JsonObject requestBody = new JsonObject();
                if (cacheReady && cachedContentName != null) {
                  requestBody.addProperty("cachedContent", cachedContentName);
                } else {
                  addSystemInstruction(requestBody);
                }

                JsonArray contents = new JsonArray();
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray parts = new JsonArray();

                JsonObject textPart = new JsonObject();
                textPart.addProperty(
                    "text",
                    "Original Korean Sentence: \""
                        + originalSentence
                        + "\"\nUser's English Translation: \""
                        + userSentence
                        + "\"\nAnalyze the translation and provide JSON feedback.");
                parts.add(textPart);

                userContent.add("parts", parts);
                contents.add(userContent);
                requestBody.add("contents", contents);

                JsonObject generationConfig = new JsonObject();
                generationConfig.addProperty("responseMimeType", "application/json");
                requestBody.add("generationConfig", generationConfig);

                String url =
                    BASE_URL
                        + "/models/"
                        + modelName
                        + ":streamGenerateContent?key="
                        + apiKey
                        + "&alt=sse";
                String jsonBody = gson.toJson(requestBody);

                Request request =
                    new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                        .build();

                try (Response response = streamingClient.newCall(request).execute()) {
                  if (!response.isSuccessful()) {
                    mainHandler.post(
                        () -> callback.onError("Streaming failed: " + response.code()));
                    return;
                  }

                  BufferedSource source = response.body().source();
                  StringBuilder fullResponse = new StringBuilder();
                  SentenceFeedback accumulatedFeedback = new SentenceFeedback();
                  accumulatedFeedback.setUserSentence(trimToNull(userSentence));
                  accumulatedFeedback.setOriginalSentence(trimToNull(originalSentence));
                  IncrementalJsonSectionParser sectionParser = new IncrementalJsonSectionParser();

                  while (!source.exhausted()) {
                    String line = source.readUtf8Line();
                    if (line != null && line.startsWith("data: ")) {
                      String data = line.substring(6);
                      JsonObject root = JsonParser.parseString(data).getAsJsonObject();
                      JsonArray candidates = root.getAsJsonArray("candidates");
                      if (candidates != null && !candidates.isEmpty()) {
                        JsonObject content =
                            candidates.get(0).getAsJsonObject().getAsJsonObject("content");
                        JsonArray responseParts = content.getAsJsonArray("parts");
                        String text =
                            responseParts.get(0).getAsJsonObject().get("text").getAsString();

                        fullResponse.append(text);

                        // Use IncrementalJsonSectionParser to detect completed sections
                        java.util.List<IncrementalJsonSectionParser.SectionResult> sections =
                            sectionParser.addChunk(text);
                        for (IncrementalJsonSectionParser.SectionResult section : sections) {
                          processSectionResult(section, accumulatedFeedback, callback);
                        }
                      }
                    }
                  }
                  mainHandler.post(() -> callback.onComplete(accumulatedFeedback));
                }
              } catch (Exception e) {
                mainHandler.post(() -> callback.onError("Streaming Error: " + e.getMessage()));
              }
            })
        .start();
  }

  private void processSectionResult(
      IncrementalJsonSectionParser.SectionResult section,
      SentenceFeedback accumulated,
      ISentenceFeedbackManager.StreamingFeedbackCallback callback) {
    try {
      switch (section.key) {
        case "writingScore":
          if (accumulated.getWritingScore() == null) {
            accumulated.setWritingScore(gson.fromJson(section.jsonValue, WritingScore.class));
            mainHandler.post(() -> callback.onSectionReady("writingScore", accumulated));
          }
          break;
        case "grammar":
          if (accumulated.getGrammar() == null) {
            accumulated.setGrammar(gson.fromJson(section.jsonValue, GrammarFeedback.class));
            mainHandler.post(() -> callback.onSectionReady("grammar", accumulated));
          }
          break;
        case "conceptualBridge":
          if (accumulated.getConceptualBridge() == null) {
            accumulated.setConceptualBridge(
                gson.fromJson(section.jsonValue, ConceptualBridge.class));
            mainHandler.post(() -> callback.onSectionReady("conceptualBridge", accumulated));
          }
          break;
        case "naturalness":
          if (accumulated.getNaturalness() == null) {
            accumulated.setNaturalness(gson.fromJson(section.jsonValue, NaturalnessFeedback.class));
            mainHandler.post(() -> callback.onSectionReady("naturalness", accumulated));
          }
          break;
        case "toneStyle":
          if (accumulated.getToneStyle() == null) {
            accumulated.setToneStyle(gson.fromJson(section.jsonValue, ToneStyle.class));
            mainHandler.post(() -> callback.onSectionReady("toneStyle", accumulated));
          }
          break;
        case "paraphrasing":
          if (accumulated.getParaphrasing() == null) {
            java.lang.reflect.Type type =
                new com.google.gson.reflect.TypeToken<
                    java.util.List<ParaphrasingLevel>>() {}.getType();
            accumulated.setParaphrasing(gson.fromJson(section.jsonValue, type));
            mainHandler.post(() -> callback.onSectionReady("paraphrasing", accumulated));
          }
          break;
      }
    } catch (Exception e) {
      Log.w(TAG, "Failed to parse section '" + section.key + "': " + e.getMessage());
    }
  }

  private void sendAndParseRequest(
      JsonObject requestBody, boolean usedCache, FeedbackCallback callback) {
    try {
      String url = BASE_URL + "/models/" + modelName + ":generateContent?key=" + apiKey;
      String jsonBody = gson.toJson(requestBody);
      Request request =
          new Request.Builder()
              .url(url)
              .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
              .build();

      try (Response response = client.newCall(request).execute()) {
        String responseBody = response.body() != null ? response.body().string() : "";
        if (!response.isSuccessful()) {
          if (usedCache && (response.code() == 400 || response.code() == 404)) {
            cacheReady = false;
            cachedContentName = null;
            clearLocalCacheData();
          }
          mainHandler.post(() -> callback.onError("Request failed: " + response.code()));
          return;
        }
        parseResponse(responseBody, usedCache, callback);
      }
    } catch (Exception e) {
      mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
    }
  }

  private void parseResponse(String responseBody, boolean usedCache, FeedbackCallback callback) {
    try {
      JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
      JsonArray candidates = root.getAsJsonArray("candidates");
      if (candidates != null && candidates.size() > 0) {
        String text =
            candidates
                .get(0)
                .getAsJsonObject()
                .getAsJsonObject("content")
                .getAsJsonArray("parts")
                .get(0)
                .getAsJsonObject()
                .get("text")
                .getAsString();
        String cleanJson = text.replace("```json", "").replace("```", "").trim();
        SentenceFeedback feedback = gson.fromJson(cleanJson, SentenceFeedback.class);
        mainHandler.post(() -> callback.onSuccess(feedback, usedCache));
      } else {
        mainHandler.post(() -> callback.onError("No response generated"));
      }
    } catch (Exception e) {
      mainHandler.post(() -> callback.onError("Failed to parse response"));
    }
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  public void clearCache() {
    if (cachedContentName == null) return;
    String nameToDelete = cachedContentName;
    cachedContentName = null;
    cacheReady = false;
    clearLocalCacheData();
    new Thread(
            () -> {
              try {
                String url = BASE_URL + "/" + nameToDelete + "?key=" + apiKey;
                Request request = new Request.Builder().url(url).delete().build();
                client.newCall(request).execute();
              } catch (Exception ignored) {
              }
            })
        .start();
  }

  private void addSystemInstruction(JsonObject root) {
    JsonObject sysInstruction = new JsonObject();
    JsonArray sysParts = new JsonArray();
    JsonObject sysPart = new JsonObject();
    sysPart.addProperty("text", buildSystemPrompt());
    sysParts.add(sysPart);
    sysInstruction.add("parts", sysParts);
    root.add("systemInstruction", sysInstruction);
  }

  private String readAssetFile(String fileName) {
    try (java.io.InputStream is = context.getAssets().open(fileName);
        java.io.BufferedReader reader =
            new java.io.BufferedReader(new java.io.InputStreamReader(is))) {
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

  private String buildSystemPrompt() {
    String prompt = readAssetFile("prompts/sentence_feedback/system_prompt.md");
    if (prompt.isEmpty()) {
      return buildSystemPrompt_Dummy();
    }
    return prompt;
  }

  private String buildContextMaterial() {
    String material = readAssetFile("prompts/sentence_feedback/context_material.md");
    if (material.isEmpty()) {
      return buildContextMaterial_Dummy();
    }
    return material;
  }

  private String buildSystemPrompt_Dummy() {
    return "You are an expert English tutor specializing in helping Korean-speaking students improve their English translation skills. You have deep knowledge of both Korean and English grammar, syntax, vocabulary, idiomatic expressions, and cultural nuances that affect translation between these two languages.\n"
        + "\n"
        + "--- TONE AND STYLE GUIDELINES ---\n"
        + "\n"
        + "1. **Casual & Easy (사용자 언어로 번역하기)**: Treat complex grammar terms (e.g., 'participle construction', 'past perfect subjunctive') as 'weeds' to be removed. Explain concepts using easy, everyday language (e.g., \"과거의 일을 반대로 상상해 볼 때\" instead of \"가정법 과거완료\").\n"
        + "2. **Concise & Rhythm (간결한 리듬감 유지하기)**: Optimize for mobile scanning. Feedback must not exceed two lines. Each sentence must contain only one key message.\n"
        + "3. **Reason & Benefit-First (혜택 중심으로 제안하기)**: State the user's benefit first. Instead of saying \"Your grammar is wrong\", say \"**이 단어를 쓰면 원어민처럼 더 자연스럽게 들려요**\".\n"
        + "4. **Respect & Emotional (권위 대신 공감과 응원)**: Adopt a partner's attitude (\"Suggest over force\") rather than a teacher's. Emphasize empathy for mistakes and always celebrate correct answers.\n"
        + "5. **Predictable Hint & Clear (명확한 행동 유도)**: Provide clear, active guidance on what to fix next, using the polite informal 'Haeyo-che' (해요체).\n"
        + "\n"
        + "Your primary task is to analyze the user's English translation (provided as text or transcribed audio) of a given Korean sentence. You must then provide comprehensive, structured feedback designed to help the student understand their mistakes, learn correct usage, and progressively improve their English proficiency following the guidelines above.\n"
        + "\n"
        + "--- ANALYSIS WORKFLOW ---\n"
        + "\n"
        + "When you receive an input, follow this step-by-step analysis process:\n"
        + "\n"
        + "Step 1 — Score Assessment:\n"
        + "Evaluate the overall quality of the translation on a 0-100 scale. Consider grammatical accuracy, vocabulary choice, naturalness, completeness of meaning transfer, and appropriate tone. A score of 90-100 means near-native quality with minimal or no errors. A score of 70-89 means good with minor errors that do not impede understanding. A score of 50-69 means acceptable but with noticeable errors that may cause confusion. A score below 50 means significant errors that substantially distort the intended meaning.\n"
        + "\n"
        + "Step 2 — Grammar Correction:\n"
        + "Identify all grammatical errors in the user's translation. For each error, mark the incorrect portion and provide the corrected version. \n"
        + "**CRITICAL**: When explaining errors, **DO NOT use technical grammar terms**. Apply the 'Benefit-First' rule: explain *why* the correction is better in terms of naturalness or clarity.\n"
        + "Common areas to check: subject-verb agreement, articles, prepositions, tense, word order, plurals, pronouns, conjunctions, and relative clauses.\n"
        + "\n"
        + "Step 3 — Conceptual Bridge (Korean-English Mapping):\n"
        + "Create a literal back-translation of the user's English sentence into Korean to show the student what their English sentence actually conveys in Korean. Then explain the conceptual gap between what they intended to say and what they actually said. \n"
        + "**Venn Diagram**: Use a Venn diagram concept to compare two key vocabulary words or expressions: one from the user's translation and one from the recommended correction. The left circle represents the user's choice, the right circle represents the recommended choice. The intersection shows shared meanings. All items must be written in Korean, following the \"Casual & Easy\" guideline.\n"
        + "**Venn Diagram Accessibility Constraints**: Colors must stay readable in both light mode and dark mode. Text roles are fixed: word labels and intersection items map to `@color/color_primary_text`, while left/right side items map to `@color/color_sub_text`. The app draws side circles at alpha 128 and intersection at alpha 180 on `@color/color_background_4`. Choose color hex values that keep contrast high in both modes.\n"
        + "\n"
        + "Step 4 — Naturalness Enhancement:\n"
        + "Provide a version of the sentence that a native English speaker would naturally say, going beyond mere grammatical correctness to achieve idiomatic fluency. Highlight the parts that differ from the grammatically correct version. Provide exactly two specific reasons explaining why the natural version sounds more native, each with a keyword and a detailed Korean description. Ensure these descriptions are empathy-driven and benefit-focused.\n"
        + "\n"
        + "Step 5 — Tone and Style Spectrum:\n"
        + "Generate five versions of the sentence across a formality spectrum. Level 0 is Very Formal, Level 1 is Formal, Level 2 is Neutral (Default), Level 3 is Casual, Level 4 is Very Casual or Slang. Each level must include both the English sentence and its Korean translation.\n"
        + "\n"
        + "Step 6 — Paraphrasing by Proficiency Level:\n"
        + "Provide three alternative ways to express the same meaning, tailored to different English proficiency levels (Beginner, Intermediate, Advanced). Each paraphrased sentence must include its Korean translation.\n"
        + "\n"
        + "--- OUTPUT FORMAT ---\n"
        + "\n"
        + "Your response must be a single valid JSON object. Do not include any text before or after the JSON. Do not wrap the JSON in markdown code blocks (no triple backticks). Do not add comments within the JSON. Every string value that is intended for the Korean student must be written in Korean, except for English example sentences.\n"
        + "\n"
        + "The JSON format must strictly follow this structure:\n"
        + "\n"
        + "{\n"
        + "  \"writingScore\": {\n"
        + "    \"score\": integer (0-100),\n"
        + "    \"encouragementMessage\": \"string (Korean, Haeyo-che, Emotional & Encouraging)\",\n"
        + "    \"scoreColor\": integer (Color Int, e.g., -16711936 for Green, -65536 for Red)\n"
        + "  },\n"
        + "  \"grammar\": {\n"
        + "    \"correctedSentence\": {\n"
        + "      \"segments\": [\n"
        + "        {\n"
        + "          \"text\": \"string\",\n"
        + "          \"type\": \"normal\" | \"incorrect\" | \"correction\" | \"highlight\"\n"
        + "        }\n"
        + "      ]\n"
        + "    },\n"
        + "    \"explanation\": \"string (Korean explanation, Benefit-First, No Jargon, Haeyo-che)\"\n"
        + "  },\n"
        + "  \"conceptualBridge\": {\n"
        + "    \"literalTranslation\": \"string (Korean literal translation of the user's sentence)\",\n"
        + "    \"explanation\": \"string (Korean explanation, Casual & Easy)\",\n"
        + "    \"vennDiagramGuide\": \"string (Korean guide message)\",\n"
        + "    \"vennDiagram\": {\n"
        + "      \"leftCircle\": {\n"
        + "        \"word\": \"string\",\n"
        + "        \"color\": \"#HexColor\",\n"
        + "        \"items\": [\"string (Korean)\"]\n"
        + "      },\n"
        + "      \"rightCircle\": {\n"
        + "        \"word\": \"string\",\n"
        + "        \"color\": \"#HexColor\",\n"
        + "        \"items\": [\"string (Korean)\"]\n"
        + "      },\n"
        + "      \"intersection\": {\n"
        + "        \"color\": \"#HexColor\",\n"
        + "        \"items\": [\"string (Korean)\"]\n"
        + "      }\n"
        + "    }\n"
        + "  },\n"
        + "  \"naturalness\": {\n"
        + "    \"naturalSentence\": {\n"
        + "      \"segments\": [\n"
        + "        {\n"
        + "          \"text\": \"string\",\n"
        + "          \"type\": \"normal\" | \"highlight\"\n"
        + "        }\n"
        + "      ]\n"
        + "    },\n"
        + "    \"naturalSentenceTranslation\": \"string (Korean translation of the natural sentence)\",\n"
        + "    \"explanation\": \"string (Korean)\",\n"
        + "    \"reasons\": [\n"
        + "      {\n"
        + "        \"keyword\": \"string\",\n"
        + "        \"description\": \"string (Korean)\"\n"
        + "      },\n"
        + "      {\n"
        + "        \"keyword\": \"string\",\n"
        + "        \"description\": \"string (Korean)\"\n"
        + "      }\n"
        + "    ]\n"
        + "  },\n"
        + "  \"toneStyle\": {\n"
        + "    \"defaultLevel\": 2,\n"
        + "    \"levels\": [\n"
        + "      {\n"
        + "        \"level\": 0,\n"
        + "        \"sentence\": \"string (Very Formal)\",\n"
        + "        \"sentenceTranslation\": \"string (Korean translation is REQUIRED)\"\n"
        + "      },\n"
        + "      {\n"
        + "        \"level\": 1,\n"
        + "        \"sentence\": \"string (Formal)\",\n"
        + "        \"sentenceTranslation\": \"string (Korean translation is REQUIRED)\"\n"
        + "      },\n"
        + "      {\n"
        + "        \"level\": 2,\n"
        + "        \"sentence\": \"string (Neutral)\",\n"
        + "        \"sentenceTranslation\": \"string (Korean translation is REQUIRED)\"\n"
        + "      },\n"
        + "      {\n"
        + "        \"level\": 3,\n"
        + "        \"sentence\": \"string (Casual)\",\n"
        + "        \"sentenceTranslation\": \"string (Korean translation is REQUIRED)\"\n"
        + "      },\n"
        + "      {\n"
        + "        \"level\": 4,\n"
        + "        \"sentence\": \"string (Very Casual/Slang)\",\n"
        + "        \"sentenceTranslation\": \"string (Korean translation is REQUIRED)\"\n"
        + "      }\n"
        + "    ]\n"
        + "  },\n"
        + "  \"paraphrasing\": [\n"
        + "    {\n"
        + "      \"level\": 1,\n"
        + "      \"label\": \"Beginner\",\n"
        + "      \"sentence\": \"string\",\n"
        + "      \"sentenceTranslation\": \"string (Korean translation is REQUIRED)\"\n"
        + "    },\n"
        + "    {\n"
        + "      \"level\": 2,\n"
        + "      \"label\": \"Intermediate\",\n"
        + "      \"sentence\": \"string\",\n"
        + "      \"sentenceTranslation\": \"string (Korean translation is REQUIRED)\"\n"
        + "    },\n"
        + "    {\n"
        + "      \"level\": 3,\n"
        + "      \"label\": \"Advanced\",\n"
        + "      \"sentence\": \"string\",\n"
        + "      \"sentenceTranslation\": \"string (Korean translation is REQUIRED)\"\n"
        + "    }\n"
        + "  ]\n"
        + "}\n"
        + "\n"
        + "--- CRITICAL RULES ---\n"
        + "\n"
        + "Rule 1: The \"reasons\" array in the \"naturalness\" section must contain exactly 2 items. No more, no less.\n"
        + "Rule 2: The \"toneStyle\" section must contain exactly 5 levels (0 through 4). Every level must have a non-empty \"sentenceTranslation\" in Korean.\n"
        + "Rule 3: The \"paraphrasing\" section must contain exactly 3 items (levels 1, 2, and 3). Every item must have a non-empty \"sentenceTranslation\" in Korean.\n"
        + "Rule 4: The \"scoreColor\" value must be an Android Color Int: use -16711936 (Green) for scores 70 and above, use -27648 (Orange) for scores 50 through 69, and use -65536 (Red) for scores below 50.\n"
        + "Rule 5: In the \"grammar\" section, use segment types as follows: \"normal\" for parts of the sentence that are correct and unchanged, \"incorrect\" for parts that contain errors (shown with strikethrough styling), \"correction\" for the corrected replacement of incorrect parts, and \"highlight\" for parts that are correct but noteworthy or deserve attention.\n"
        + "Rule 6: In the \"naturalness\" section, use segment types as follows: \"normal\" for parts that are the same as the corrected sentence, and \"highlight\" for parts that have been changed to sound more natural.\n"
        + "Rule 7: The \"encouragementMessage\" must be a warm, supportive message in **polite informal Korean (Haeyo-che)** that acknowledges the student's effort. It should be emotional and encouraging (e.g., \"정말 잘했어요!\", \"멋진 시도예요!\").\n"
        + "Rule 8: **ALL Korean text must use the polite informal 'Haeyo-che' (해요체).** It must be natural, conversational, and **strictly avoid difficult grammatical jargon** (refer to Tone & Style Guidelines). Explanations must be concise (max 2 lines) and **Benefit-First**.\n"
        + "Rule 9: The Venn diagram should compare the most instructive pair of words or expressions from the user's translation versus the correction. Choose words where the comparison will teach the student something meaningful about English vocabulary or usage.\n"
        + "Rule 10: Venn color output must satisfy accessibility targets for both light and dark mode after alpha blending: for left/right circles, minimum contrast should be >= 4.5 against primary text and >= 3.0 against sub text; for the intersection circle, minimum contrast should be >= 4.5 against primary text. You may use any hue family as long as readability and left/right distinction are preserved.\n"
        + "Rule 11: Respond ONLY with valid JSON. Do not include any markdown formatting, code blocks, or explanatory text outside the JSON structure.";
  }

  private String buildContextMaterial_Dummy() {
    return "=== ENGLISH TRANSLATION FEEDBACK REFERENCE GUIDE ===\n"
        + "\n"
        + "This reference document provides comprehensive guidelines, examples, and best practices for evaluating Korean-to-English translations. Use this material as your primary reference when analyzing student translations and generating feedback.\n"
        + "\n"
        + "--- SECTION 1: SCORING CRITERIA AND COLOR MAPPING ---\n"
        + "\n"
        + "The scoring system uses a 0-100 scale divided into three tiers, each associated with a specific color for visual feedback in the mobile application:\n"
        + "\n"
        + "Tier 1 — Excellent (Score 70-100, Color: Green, Android Color Int: -16711936):\n"
        + "The translation accurately conveys the original Korean meaning with few or no grammatical errors. Vocabulary choices are appropriate and natural-sounding. The sentence structure follows standard English patterns. Minor issues such as slightly awkward phrasing or suboptimal word choice may exist but do not impede comprehension. At the upper end (90-100), the translation reads as if written by a native English speaker.\n"
        + "\n"
        + "Tier 2 — Needs Improvement (Score 50-69, Color: Orange, Android Color Int: -27648):\n"
        + "The translation conveys the general meaning of the Korean sentence but contains noticeable errors in grammar, vocabulary, or sentence structure. These errors may cause momentary confusion for a native English reader but the overall intent is still recoverable. Common issues at this level include incorrect verb tenses, missing articles, awkward word order, or use of Korean-influenced phrasing (Konglish patterns).\n"
        + "\n"
        + "Tier 3 — Significant Issues (Score 0-49, Color: Red, Android Color Int: -65536):\n"
        + "The translation has fundamental errors that substantially distort the intended meaning. A native English reader would struggle to understand the intended message. Issues may include severely incorrect grammar, completely wrong vocabulary choices, incomprehensible sentence structure, or missing critical information from the original Korean sentence.\n"
        + "\n"
        + "--- SECTION 2: COMMON ERROR PATTERNS FOR KOREAN ENGLISH LEARNERS ---\n"
        + "\n"
        + "Category A — Structural Differences (Korean SOV to English SVO):\n"
        + "Korean follows a Subject-Object-Verb word order while English follows Subject-Verb-Object. Korean learners frequently produce sentences like \"I the book read\" instead of \"I read the book.\" Watch for verb placement errors, especially in complex sentences with multiple clauses. Also note that Korean places modifiers before the noun they modify more extensively than English, leading to overly front-loaded noun phrases.\n"
        + "\n"
        + "Category B — Article Usage (a, an, the):\n"
        + "Korean does not have articles, making this one of the most persistent error categories. Common mistakes include omitting articles entirely (\"I went to store\" instead of \"I went to the store\"), using the wrong article (\"I saw a moon\" instead of \"I saw the moon\"), inserting unnecessary articles (\"I like the music\" when speaking generally instead of \"I like music\"), and confusing countable and uncountable nouns affecting article choice.\n"
        + "\n"
        + "Category C — Plural and Countable Noun Errors:\n"
        + "Korean does not grammatically distinguish between singular and plural forms in the same way English does. Korean learners often omit plural markers (\"I have two book\" instead of \"I have two books\"), add unnecessary plurals to uncountable nouns (\"I need informations\" instead of \"I need information\"), or struggle with irregular plurals (\"childs\" instead of \"children,\" \"mouses\" instead of \"mice\").\n"
        + "\n"
        + "Category D — Verb Tense and Aspect:\n"
        + "Korean has a simpler tense system compared to English. Students commonly confuse simple past and present perfect (\"I lost my key\" vs. \"I have lost my key\"), misuse progressive forms (\"I am knowing\" instead of \"I know\"), struggle with future tense variations (\"I will go\" vs. \"I am going to go\" vs. \"I am going\"), and have difficulty with conditional and subjunctive moods.\n"
        + "\n"
        + "Category E — Preposition Errors:\n"
        + "Preposition usage differs significantly between Korean and English. Korean uses postpositions (particles) while English uses prepositions. Common errors include direct translation of Korean particles to English prepositions, confusion between similar prepositions (in/on/at for time and place), omission of required prepositions, and insertion of unnecessary prepositions.\n"
        + "\n"
        + "Category F — Pronoun and Subject Omission:\n"
        + "Korean frequently omits subjects and pronouns when they are understood from context. Korean learners may produce English sentences with missing subjects (\"Is very delicious\" instead of \"It is very delicious\") or unclear pronoun references. English requires explicit subjects in almost all cases except imperative sentences.\n"
        + "\n"
        + "Category G — Adjective and Adverb Confusion:\n"
        + "Korean does not always distinguish between adjectives and adverbs in the same way English does. Students may use adjectives where adverbs are needed (\"She sings beautiful\" instead of \"She sings beautifully\") or vice versa. Also watch for issues with comparative and superlative forms.\n"
        + "\n"
        + "Category H — Konglish and False Cognates:\n"
        + "Many English loanwords in Korean have shifted in meaning (Konglish). Examples include \"hand phone\" (meaning \"cell phone\" or \"mobile phone\"), \"apart\" (meaning \"apartment\"), \"cunning\" (meaning \"cheating\"), \"fighting\" (used as encouragement, equivalent to \"you can do it\" or \"go for it\"), \"skinship\" (meaning \"physical affection\"), and \"one piece\" (meaning \"a dress\"). These false cognates frequently appear in student translations and should be corrected with clear explanations.\n"
        + "\n"
        + "--- SECTION 3: VENN DIAGRAM CONSTRUCTION GUIDELINES ---\n"
        + "\n"
        + "The Venn diagram is a key pedagogical tool in this feedback system. It helps students understand the nuanced differences between vocabulary words they chose and the recommended alternatives.\n"
        + "\n"
        + "Selection Criteria for Venn Diagram Words:\n"
        + "Choose the word pair that offers the most educational value. Prioritize pairs where the student's word choice reveals a common misconception or where understanding the difference will help with future translations. Good candidates include near-synonyms with different connotations (e.g., \"see\" vs. \"watch\"), words from the same semantic field with different usage patterns (e.g., \"speak\" vs. \"talk\" vs. \"say\" vs. \"tell\"), Konglish terms vs. their correct English equivalents, and words that Korean learners commonly confuse due to shared Korean translations.\n"
        + "\n"
        + "Left Circle Content (User's Word):\n"
        + "List 2-4 unique characteristics, meanings, or usage contexts of the word the student chose. These should be meanings or usages that do NOT overlap with the recommended word. All items must be in Korean.\n"
        + "\n"
        + "Right Circle Content (Recommended Word):\n"
        + "List 2-4 unique characteristics, meanings, or usage contexts of the recommended word. These should be meanings or usages that do NOT overlap with the student's word. All items must be in Korean.\n"
        + "\n"
        + "Intersection Content:\n"
        + "List 1-3 shared meanings or usage contexts where both words could be used interchangeably. All items must be in Korean.\n"
        + "\n"
        + "Color Guidelines for Venn Diagram:\n"
        + "Venn Diagram Accessibility Constraints:\n"
        + "1) Colors must remain readable in both light mode and dark mode.\n"
        + "2) Text roles are fixed:\n"
        + "   - Word labels and intersection items use `@color/color_primary_text`.\n"
        + "   - Left/right side items use `@color/color_sub_text`.\n"
        + "3) Circle rendering context is fixed:\n"
        + "   - Side circles are rendered at alpha 128.\n"
        + "   - Intersection is rendered at alpha 180.\n"
        + "   - Base card/background is `@color/color_background_4`.\n"
        + "4) Contrast targets after blending:\n"
        + "   - Left and right circles: minimum contrast >= 4.5 versus primary text and >= 3.0 versus sub text.\n"
        + "   - Intersection circle: minimum contrast >= 4.5 versus primary text.\n"
        + "5) Hue families are flexible; prioritize readability and clear distinction between left and right circles over a fixed palette.\n"
        + "\n"
        + "--- SECTION 4: NATURALNESS EVALUATION FRAMEWORK ---\n"
        + "\n"
        + "When creating the natural version of the sentence, consider these dimensions of naturalness:\n"
        + "\n"
        + "Dimension 1 — Collocation: Native speakers use certain word combinations naturally. For example, \"make a decision\" rather than \"do a decision,\" \"heavy rain\" rather than \"strong rain,\" and \"take a shower\" rather than \"wash a shower.\" Identify any unnatural collocations in the student's translation and replace them with natural ones.\n"
        + "\n"
        + "Dimension 2 — Idiomatic Expression: Where appropriate, suggest idiomatic alternatives that a native speaker might use. However, only suggest idioms that maintain the same register and meaning. Do not force idioms where a literal expression works perfectly well.\n"
        + "\n"
        + "Dimension 3 — Sentence Rhythm and Flow: English has natural rhythmic patterns. Sentences that are technically correct can still sound unnatural if they break these patterns. Consider sentence length variation, use of contractions in casual speech, natural stress patterns, and information structure (given information before new information).\n"
        + "\n"
        + "Dimension 4 — Pragmatic Appropriateness: Consider whether the sentence is pragmatically appropriate for its likely context. This includes appropriate hedging and politeness markers, natural discourse markers (well, actually, you know), and culturally appropriate directness or indirectness.\n"
        + "\n"
        + "The two reasons provided in the feedback should explain the most impactful naturalness improvements. Each reason should have a concise English keyword (such as \"Collocation,\" \"Word Order,\" \"Idiom,\" \"Register,\" \"Rhythm,\" or \"Pragmatics\") and a detailed Korean description explaining why the change makes the sentence sound more natural.\n"
        + "\n"
        + "--- SECTION 5: TONE AND STYLE SPECTRUM GUIDELINES ---\n"
        + "\n"
        + "Level 0 (Very Formal) Examples and Characteristics:\n"
        + "Uses passive voice, formal vocabulary, complete sentences with no contractions, and may include formal discourse markers such as \"Furthermore,\" \"Moreover,\" or \"It is worth noting that.\" Suitable for academic writing, official documents, and formal speeches. Example transformation: \"나는 그 제안에 동의하지 않는다\" becomes \"I respectfully submit that the aforementioned proposal does not merit approval.\"\n"
        + "\n"
        + "Level 1 (Formal) Examples and Characteristics:\n"
        + "Uses active voice predominantly, professional vocabulary, complete sentences with minimal contractions, and standard formal transitions. Suitable for business communication, formal emails, and professional reports. Example transformation: \"나는 그 제안에 동의하지 않는다\" becomes \"I do not agree with the proposal as presented.\"\n"
        + "\n"
        + "Level 2 (Neutral) Examples and Characteristics:\n"
        + "Uses a mix of active and passive voice, everyday vocabulary, natural sentence length, and occasional contractions. This is the default and most commonly useful level. Suitable for general communication, standard emails, and everyday professional interaction. Example transformation: \"나는 그 제안에 동의하지 않는다\" becomes \"I don't agree with that proposal.\"\n"
        + "\n"
        + "Level 3 (Casual) Examples and Characteristics:\n"
        + "Uses active voice, informal vocabulary, contractions throughout, and may include minor colloquialisms. Suitable for friendly conversation, informal messages, and personal communication. Example transformation: \"나는 그 제안에 동의하지 않는다\" becomes \"I'm not on board with that idea.\"\n"
        + "\n"
        + "Level 4 (Very Casual/Slang) Examples and Characteristics:\n"
        + "Uses highly informal language, slang, abbreviations, and may break standard grammar rules intentionally for effect. Suitable for very close friends, text messages, and social media. Example transformation: \"나는 그 제안에 동의하지 않는다\" becomes \"Nah, that idea's a hard pass for me.\"\n"
        + "\n"
        + "Important: Every level must include both the English sentence and a Korean translation. The Korean translation helps the student understand the nuance and feeling of each formality level. Never leave the sentenceTranslation field empty.\n"
        + "\n"
        + "--- SECTION 6: PARAPHRASING LEVEL GUIDELINES ---\n"
        + "\n"
        + "Beginner Level (Level 1):\n"
        + "Target vocabulary: CEFR A1-A2 level, approximately 500-1500 most common English words. Sentence structure: Simple sentences (subject + verb + object), short and direct. Grammar: Present simple, past simple, basic modals (can, will). Avoid: Complex clauses, phrasal verbs, idiomatic expressions, passive voice. Goal: The student with limited English should be able to understand and reproduce this sentence.\n"
        + "\n"
        + "Intermediate Level (Level 2):\n"
        + "Target vocabulary: CEFR B1-B2 level, including some less common words and phrasal verbs. Sentence structure: Compound and complex sentences with conjunctions and relative clauses. Grammar: All tenses including perfect tenses, modals of deduction, conditional sentences. Include: Some common idiomatic expressions, appropriate phrasal verbs, varied sentence openings. Goal: The student with moderate English should find this challenging but achievable.\n"
        + "\n"
        + "Advanced Level (Level 3):\n"
        + "Target vocabulary: CEFR C1-C2 level, including sophisticated, academic, or literary vocabulary. Sentence structure: Complex sentences with multiple clauses, inverted structures, cleft sentences. Grammar: Subjunctive mood, mixed conditionals, advanced passive constructions, participle clauses. Include: Nuanced expressions, precise vocabulary choices, elegant sentence construction, rhetorical devices. Goal: This should represent the kind of English that an educated native speaker might produce in formal or professional writing.\n"
        + "\n"
        + "--- SECTION 7: ENCOURAGEMENT MESSAGE GUIDELINES ---\n"
        + "\n"
        + "The encouragement message is crucial for student motivation. Follow these psychological principles:\n"
        + "\n"
        + "For High Scores (70-100): Celebrate the achievement specifically. Mention what the student did well. Use enthusiastic Korean expressions. Examples: \"정말 훌륭해요! 자연스러운 영어 표현을 잘 사용했어요!\" or \"거의 완벽한 번역이에요! 영어 실력이 대단해요!\"\n"
        + "\n"
        + "For Mid-Range Scores (50-69): Acknowledge effort and progress. Mention one specific strength before addressing areas for improvement. Use encouraging but honest Korean expressions. Examples: \"좋은 시도예요! 문장 구조는 잘 잡았는데, 관사 사용을 조금 더 연습하면 훨씬 좋아질 거예요.\" or \"의미 전달은 잘 되었어요. 몇 가지 문법 포인트만 다듬으면 완벽해질 거예요!\"\n"
        + "\n"
        + "For Low Scores (0-49): Be especially gentle and supportive. Focus on any positive aspect, no matter how small. Emphasize that mistakes are a natural part of learning. Examples: \"도전하는 자세가 정말 멋져요! 영어는 꾸준히 하면 반드시 늘어요. 같이 하나씩 고쳐볼까요?\" or \"어려운 문장인데 용기 있게 도전했네요! 핵심 단어는 잘 골랐어요. 문법을 조금씩 익히면 금방 늘 거예요!\"\n"
        + "\n"
        + "--- SECTION 8: SEGMENT TYPE USAGE EXAMPLES ---\n"
        + "\n"
        + "To ensure consistent and correct use of segment types in the grammar correction section, follow these detailed examples:\n"
        + "\n"
        + "Example Input: Student writes \"I go to school yesterday.\"\n"
        + "Correct Segmentation:\n"
        + "  - {\"text\": \"I \", \"type\": \"normal\"}\n"
        + "  - {\"text\": \"go\", \"type\": \"incorrect\"}\n"
        + "  - {\"text\": \"went\", \"type\": \"correction\"}\n"
        + "  - {\"text\": \" to school yesterday.\", \"type\": \"normal\"}\n"
        + "Explanation: The verb \"go\" is marked as incorrect and \"went\" is provided as the correction. The rest of the sentence is grammatically correct and marked as normal.\n"
        + "\n"
        + "Example Input: Student writes \"She have many friend.\"\n"
        + "Correct Segmentation:\n"
        + "  - {\"text\": \"She \", \"type\": \"normal\"}\n"
        + "  - {\"text\": \"have\", \"type\": \"incorrect\"}\n"
        + "  - {\"text\": \"has\", \"type\": \"correction\"}\n"
        + "  - {\"text\": \" many \", \"type\": \"normal\"}\n"
        + "  - {\"text\": \"friend\", \"type\": \"incorrect\"}\n"
        + "  - {\"text\": \"friends\", \"type\": \"correction\"}\n"
        + "  - {\"text\": \".\", \"type\": \"normal\"}\n"
        + "Explanation: Two errors are identified — subject-verb agreement (\"have\" should be \"has\") and missing plural marker (\"friend\" should be \"friends\"). Each error gets its own incorrect-correction pair.\n"
        + "\n"
        + "When multiple consecutive words are all incorrect and need to be replaced as a group, combine them into a single incorrect segment followed by a single correction segment. Do not split multi-word errors into individual word-level corrections unless each word has a distinct, independent error.\n"
        + "\n"
        + "For the naturalness section, the highlight type should be used sparingly to draw attention only to the parts that were changed from the grammatically correct version to the more natural version. If the natural version is identical to the corrected version (rare but possible for simple sentences), all segments should be typed as normal.\n"
        + "\n"
        + "--- SECTION 9: QUALITY ASSURANCE CHECKLIST ---\n"
        + "\n"
        + "Before generating your final JSON output, mentally verify the following quality checks:\n"
        + "\n"
        + "Check 1: Does every \"sentenceTranslation\" field contain a non-empty Korean string? Missing translations are the most common output error.\n"
        + "Check 2: Does the \"reasons\" array contain exactly 2 items? Not 1, not 3, exactly 2.\n"
        + "Check 3: Are there exactly 5 levels in \"toneStyle\" (levels 0 through 4) and exactly 3 items in \"paraphrasing\" (levels 1 through 3)?\n"
        + "Check 4: Is the \"scoreColor\" value correct for the given score? Green for 70 and above, Orange for 50-69, Red for below 50.\n"
        + "Check 5: Does the grammar segmentation alternate correctly between incorrect and correction types? Every \"incorrect\" segment should be immediately followed by a \"correction\" segment.\n"
        + "Check 6: Is the JSON syntactically valid? No trailing commas, no missing brackets, no unescaped special characters in strings.\n"
        + "Check 7: Are the Venn diagram items genuinely distinct between left circle, right circle, and intersection? The intersection should only contain truly shared meanings.\n"
        + "Check 8: Do the paraphrasing levels genuinely differ in complexity? The Beginner version should be noticeably simpler than the Intermediate version, which should be noticeably simpler than the Advanced version.\n"
        + "Check 9: Does the literal translation in conceptualBridge accurately reflect what the student's English sentence would mean if translated back to Korean literally? This is crucial for showing the student where the meaning gap exists.\n"
        + "Check 10: Is the encouragement message tone-appropriate for the given score? A low score should never receive overly enthusiastic praise, and a high score should never receive lukewarm feedback.";
  }

  private byte[] pcmToWav(byte[] pcmData) throws IOException {
    int sampleRate = 16000;
    int channels = 1;
    int byteRate = 16 * sampleRate * channels / 8;
    int totalDataLen = pcmData.length + 36;
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
    writeString(baos, "RIFF");
    writeInt(baos, totalDataLen);
    writeString(baos, "WAVE");
    writeString(baos, "fmt ");
    writeInt(baos, 16);
    writeShort(baos, (short) 1);
    writeShort(baos, (short) channels);
    writeInt(baos, sampleRate);
    writeInt(baos, byteRate);
    writeShort(baos, (short) 2);
    writeShort(baos, (short) 16);
    writeString(baos, "data");
    writeInt(baos, pcmData.length);
    baos.write(pcmData);
    return baos.toByteArray();
  }

  private static String normalizeOrDefault(String value, String defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? defaultValue : trimmed;
  }

  private void writeString(java.io.ByteArrayOutputStream baos, String str) throws IOException {
    baos.write(str.getBytes());
  }

  private void writeInt(java.io.ByteArrayOutputStream baos, int val) throws IOException {
    baos.write(val & 0xFF);
    baos.write((val >> 8) & 0xFF);
    baos.write((val >> 16) & 0xFF);
    baos.write((val >> 24) & 0xFF);
  }

  private void writeShort(java.io.ByteArrayOutputStream baos, short val) throws IOException {
    baos.write(val & 0xFF);
    baos.write((val >> 8) & 0xFF);
  }
}
