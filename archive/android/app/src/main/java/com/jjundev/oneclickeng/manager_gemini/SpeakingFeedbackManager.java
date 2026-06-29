package com.jjundev.oneclickeng.manager_gemini;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISpeakingFeedbackManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.FluencyFeedback;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** Gemini Fluency Analysis Manager Uses REST API generateContent to analyze audio. */
public class SpeakingFeedbackManager
    implements com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts
        .ISpeakingFeedbackManager {

  private static final String TAG = "SpeakingFeedbackManager";
  private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
  private static final String DEFAULT_MODEL_NAME = "gemini-3.1-flash-lite-preview";
  private static final int CACHE_TTL_SECONDS = 3600; // 1 hour
  private static final int MIN_REMAINING_TTL_SECONDS = 300; // 5 minutes

  private static final String PREF_NAME = "gemini_speaking_cache_prefs";
  private static final String KEY_CACHE_NAME = "gemini_speaking_cache_name_v1";
  private static final String KEY_CACHE_CREATED = "gemini_speaking_cache_created_at";
  private static final String KEY_CACHE_TTL = "gemini_speaking_cache_ttl_seconds";

  private final OkHttpClient client;
  private final Gson gson;
  private final String apiKey;
  private final String modelName;
  private final Handler mainHandler;
  private final Context context;
  private final SharedPreferences prefs;

  private String cachedContentName;
  private boolean cacheReady = false;

  interface ValidationCallback {
    void onValid(String cacheName, long remainingSeconds);

    void onInvalid();

    void onError(String error);
  }

  public SpeakingFeedbackManager(Context context, String apiKey, String modelName) {
    this.context = context.getApplicationContext();
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
    this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
  }

  @Override
  public void initializeCache(ISpeakingFeedbackManager.InitCallback callback) {
    String savedCacheName = prefs.getString(KEY_CACHE_NAME, null);

    if (savedCacheName != null) {
      long createdAt = prefs.getLong(KEY_CACHE_CREATED, 0);
      int ttl = prefs.getInt(KEY_CACHE_TTL, CACHE_TTL_SECONDS);
      long elapsedSeconds = (System.currentTimeMillis() - createdAt) / 1000;

      if (elapsedSeconds > ttl) {
        Log.d(TAG, "[SpeakingCache] Local cache expired, creating new one");
        clearLocalCacheData();
        createCache(callback);
        return;
      }

      validateCacheFromServer(
          savedCacheName,
          new ValidationCallback() {
            @Override
            public void onValid(String name, long remainingSeconds) {
              if (remainingSeconds > MIN_REMAINING_TTL_SECONDS) {
                Log.i(TAG, "[SpeakingCache] Reusing cache: " + name);
                cachedContentName = name;
                cacheReady = true;
                mainHandler.post(callback::onReady);
              } else {
                Log.i(TAG, "[SpeakingCache] Cache expiring soon, creating new cache");
                createCache(callback);
              }
            }

            @Override
            public void onInvalid() {
              Log.i(TAG, "[SpeakingCache] Server cache invalid, creating new cache");
              clearLocalCacheData();
              createCache(callback);
            }

            @Override
            public void onError(String error) {
              Log.w(TAG, "[SpeakingCache] Validation error: " + error + ". Creating new cache.");
              createCache(callback);
            }
          });
    } else {
      Log.i(TAG, "[SpeakingCache] No local cache found. Creating new cache.");
      createCache(callback);
    }
  }

  private void createCache(ISpeakingFeedbackManager.InitCallback callback) {
    new Thread(
            () -> {
              try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", "models/" + modelName);

                JsonArray contents = new JsonArray();

                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray userParts = new JsonArray();
                JsonObject userPart = new JsonObject();
                userPart.addProperty("text", "Initialize Speaking Feedback analysis.");
                userParts.add(userPart);
                userContent.add("parts", userParts);
                contents.add(userContent);

                JsonObject modelContent = new JsonObject();
                modelContent.addProperty("role", "model");
                JsonArray modelParts = new JsonArray();
                JsonObject modelPart = new JsonObject();
                modelPart.addProperty(
                    "text", "I am ready to analyze speaking audio and return JSON feedback.");
                modelParts.add(modelPart);
                modelContent.add("parts", modelParts);
                contents.add(modelContent);

                requestBody.add("contents", contents);

                addSystemInstruction(requestBody);
                requestBody.addProperty("ttl", CACHE_TTL_SECONDS + "s");
                requestBody.addProperty("displayName", "SpeakingFeedbackPrompt");

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
                    Log.e(
                        TAG,
                        "[SpeakingCache] Cache creation failed: "
                            + response.code()
                            + " - "
                            + responseBody);
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

                  Log.i(TAG, "[SpeakingCache] New cache created: " + cachedContentName);
                  mainHandler.post(callback::onReady);
                }
              } catch (Exception e) {
                Log.e(TAG, "[SpeakingCache] createCache failed", e);
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

                  String responseBody = response.body() != null ? response.body().string() : "";
                  JsonObject result = JsonParser.parseString(responseBody).getAsJsonObject();
                  if (!result.has("expireTime")) {
                    callback.onError("No expireTime in cache response");
                    return;
                  }

                  Instant expireTime = Instant.parse(result.get("expireTime").getAsString());
                  long remainingSeconds =
                      expireTime.getEpochSecond() - Instant.now().getEpochSecond();
                  callback.onValid(cacheName, remainingSeconds);
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

  private void invalidateCacheState() {
    cachedContentName = null;
    cacheReady = false;
    clearLocalCacheData();
  }

  /**
   * Analyze audio for fluency
   *
   * @param pcmData Raw PCM data (16kHz, 16-bit, Mono)
   * @param callback Callback for result
   */
  @Override
  public void analyzeAudio(byte[] pcmData, ISpeakingFeedbackManager.AnalysisCallback callback) {
    new Thread(
            () -> {
              try {
                // 1. Convert PCM to WAV
                byte[] wavData = pcmToWav(pcmData);
                String base64Audio = Base64.encodeToString(wavData, Base64.NO_WRAP);

                sendAnalysisRequest(base64Audio, callback, true, true);
              } catch (Exception e) {
                Log.e(TAG, "Analysis Failed", e);
                notifyError(callback, "Analysis Failed: " + e.getMessage());
              }
            })
        .start();
  }

  private void sendAnalysisRequest(
      String base64Audio,
      ISpeakingFeedbackManager.AnalysisCallback callback,
      boolean allowCacheUse,
      boolean allowRetryWithoutCache)
      throws IOException {
    JsonObject root = new JsonObject();
    boolean useCache = allowCacheUse && cacheReady && cachedContentName != null;

    if (useCache) {
      root.addProperty("cachedContent", cachedContentName);
    } else {
      addSystemInstruction(root);
    }

    JsonArray contents = new JsonArray();
    JsonObject content = new JsonObject();
    content.addProperty("role", "user");
    JsonArray parts = new JsonArray();

    JsonObject audioPart = new JsonObject();
    JsonObject inlineData = new JsonObject();
    inlineData.addProperty("mimeType", "audio/wav");
    inlineData.addProperty("data", base64Audio);
    audioPart.add("inlineData", inlineData);
    parts.add(audioPart);

    JsonObject textPart = new JsonObject();
    textPart.addProperty("text", "Analyze this audio and return only the required JSON.");
    parts.add(textPart);

    content.add("parts", parts);
    contents.add(content);
    root.add("contents", contents);

    JsonObject generationConfig = new JsonObject();
    generationConfig.addProperty("responseMimeType", "application/json");
    root.add("generationConfig", generationConfig);

    String jsonBody = gson.toJson(root);
    String url =
        BASE_URL
            + "/models/"
            + modelName
            + ":generateContent?key="
            + apiKey
            + "&fields=candidates.content.parts.text";
    Request request =
        new Request.Builder()
            .url(url)
            .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
            .build();

    Log.d(TAG, "Sending analysis request. useCache=" + useCache);

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String errorBody = response.body() != null ? response.body().string() : "Unknown error";
        int code = response.code();

        if (useCache && allowRetryWithoutCache && (code == 400 || code == 404)) {
          Log.w(
              TAG,
              "Cached request failed (" + code + "), invalidating cache and retrying non-cached.");
          invalidateCacheState();
          sendAnalysisRequest(base64Audio, callback, false, false);
          return;
        }

        notifyError(callback, "API Error: " + code + " - " + errorBody);
        return;
      }

      String responseBody = response.body() != null ? response.body().string() : "";
      Log.d(TAG, "Response: " + responseBody);
      parseResponse(responseBody, callback);
    }
  }

  private void parseResponse(
      String responseBody, ISpeakingFeedbackManager.AnalysisCallback callback) {
    try {
      JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
      if (!root.has("candidates")) {
        notifyError(callback, "Invalid response: missing candidates");
        return;
      }

      JsonArray candidates = root.getAsJsonArray("candidates");
      if (candidates != null && candidates.size() > 0) {
        JsonObject candidate = candidates.get(0).getAsJsonObject();
        if (!candidate.has("content") || candidate.get("content").isJsonNull()) {
          notifyError(callback, "Response blocked or empty (safety filter?)");
          return;
        }

        JsonObject content = candidate.getAsJsonObject("content");
        if (!content.has("parts") || content.get("parts").isJsonNull()) {
          notifyError(callback, "Invalid response: missing parts in content");
          return;
        }

        JsonArray parts = content.getAsJsonArray("parts");
        if (parts == null || parts.size() == 0) {
          notifyError(callback, "Invalid response: empty parts");
          return;
        }

        JsonElement firstPart = parts.get(0);
        if (firstPart == null
            || !firstPart.isJsonObject()
            || !firstPart.getAsJsonObject().has("text")) {
          notifyError(callback, "Invalid response: missing text in parts");
          return;
        }

        String text = firstPart.getAsJsonObject().get("text").getAsString();

        // Clean and Extract JSON
        String cleanJson = extractJson(text);

        // Parse with Lenient mode
        JsonReader reader = new JsonReader(new StringReader(cleanJson));
        reader.setLenient(true);
        JsonElement element = JsonParser.parseReader(reader);

        JsonObject resultJson;

        if (element.isJsonArray()) {
          JsonArray resultArray = element.getAsJsonArray();
          if (resultArray.size() > 0) {
            resultJson = resultArray.get(0).getAsJsonObject();
          } else {
            notifyError(callback, "Empty JSON array in response");
            return;
          }
        } else if (element.isJsonObject()) {
          resultJson = element.getAsJsonObject();
        } else {
          notifyError(callback, "Invalid JSON format in text");
          return;
        }

        int fluency = resultJson.has("fluency") ? resultJson.get("fluency").getAsInt() : 0;
        int confidence = resultJson.has("confidence") ? resultJson.get("confidence").getAsInt() : 0;
        int hesitations =
            resultJson.has("hesitations") ? resultJson.get("hesitations").getAsInt() : 0;
        String transcript =
            resultJson.has("transcript") ? resultJson.get("transcript").getAsString() : "";
        String feedbackMessage = "";
        if (resultJson.has("feedback_message")
            && !resultJson.get("feedback_message").isJsonNull()) {
          feedbackMessage = resultJson.get("feedback_message").getAsString();
        } else if (resultJson.has("feedbackMessage")
            && !resultJson.get("feedbackMessage").isJsonNull()) {
          feedbackMessage = resultJson.get("feedbackMessage").getAsString();
        }
        final String finalFeedbackMessage = feedbackMessage;

        mainHandler.post(
            () ->
                callback.onSuccess(
                    new FluencyFeedback(
                        fluency, confidence, hesitations, transcript, finalFeedbackMessage)));
      } else {
        notifyError(callback, "No candidates in response");
      }
    } catch (Exception e) {
      Log.e(TAG, "Parsing Error. Body: " + responseBody, e);
      notifyError(callback, "Failed to parse result: " + e.getMessage());
    }
  }

  /** Extracts the first JSON object or array from the text. */
  private String extractJson(String text) {
    if (text == null || text.isEmpty()) return "";

    int firstBrace = text.indexOf('{');
    int firstBracket = text.indexOf('[');
    int start = -1;

    if (firstBrace != -1 && (firstBracket == -1 || firstBrace < firstBracket)) {
      start = firstBrace;
    } else if (firstBracket != -1) {
      start = firstBracket;
    }

    if (start == -1) return text;

    int lastBrace = text.lastIndexOf('}');
    int lastBracket = text.lastIndexOf(']');
    int end = -1;

    if (lastBrace != -1 && (lastBracket == -1 || lastBrace > lastBracket)) {
      end = lastBrace;
    } else if (lastBracket != -1) {
      end = lastBracket;
    }

    if (end == -1 || end < start) return text;

    return text.substring(start, end + 1);
  }

  private void notifyError(ISpeakingFeedbackManager.AnalysisCallback callback, String error) {
    mainHandler.post(() -> callback.onError(error));
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

  private String buildSystemPrompt() {
    String prompt = readAssetFile("prompts/speaking_feedback/system_prompt.md");
    if (!prompt.isEmpty()) {
      return prompt;
    }
    return buildSystemPromptFallback();
  }

  private String buildSystemPromptFallback() {
    return "You are an English speaking coach. Analyze learner audio and return only valid JSON.\n"
        + "The response schema must be exactly:\n"
        + "{\"fluency\":int,\"confidence\":int,\"hesitations\":int,\"transcript\":string,\"feedback_message\":string}\n"
        + "Rules:\n"
        + "1) fluency and confidence must be 1-10.\n"
        + "2) hesitations must be a non-negative integer.\n"
        + "3) transcript must be the recognized speech text.\n"
        + "4) feedback_message must be one short Korean message for Speaking Feedback only.\n"
        + "5) \"Sentence Feedback\"과 헷갈리지 말 것.\n"
        + "6) \"해요\"체로 생성해주세요.\n"
        + "7) Output JSON only. Do not use markdown code fences.";
  }

  // Helper: Add WAV Header to PCM Data
  private byte[] pcmToWav(byte[] pcmData) throws IOException {
    int sampleRate = 16000;
    int channels = 1;
    int byteRate = 16 * sampleRate * channels / 8;
    int totalDataLen = pcmData.length + 36;

    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    // WAV Header
    writeString(baos, "RIFF"); // ChunkID
    writeInt(baos, totalDataLen); // ChunkSize
    writeString(baos, "WAVE"); // Format
    writeString(baos, "fmt "); // Subchunk1ID
    writeInt(baos, 16); // Subchunk1Size
    writeShort(baos, (short) 1); // AudioFormat (PCM)
    writeShort(baos, (short) channels); // NumChannels
    writeInt(baos, sampleRate); // SampleRate
    writeInt(baos, byteRate); // ByteRate
    writeShort(baos, (short) 2); // BlockAlign
    writeShort(baos, (short) 16); // BitsPerSample
    writeString(baos, "data"); // Subchunk2ID
    writeInt(baos, pcmData.length); // Subchunk2Size

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

  private void writeString(ByteArrayOutputStream baos, String str) throws IOException {
    baos.write(str.getBytes());
  }

  private void writeInt(ByteArrayOutputStream baos, int val) throws IOException {
    baos.write(val & 0xFF);
    baos.write((val >> 8) & 0xFF);
    baos.write((val >> 16) & 0xFF);
    baos.write((val >> 24) & 0xFF);
  }

  private void writeShort(ByteArrayOutputStream baos, short val) throws IOException {
    baos.write(val & 0xFF);
    baos.write((val >> 8) & 0xFF);
  }
}
