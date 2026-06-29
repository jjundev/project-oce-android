package com.jjundev.oneclickeng.manager_gemini;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IDialogueGenerateManager;
import com.jjundev.oneclickeng.tool.IncrementalDialogueScriptParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

public class DialogueGenerateManager implements IDialogueGenerateManager {
  private static final String TAG = "DialogueGenerateManager";
  private static final String DEFAULT_MODEL_NAME = "gemini-3.1-flash-lite-preview";
  private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
  private static final int CACHE_TTL_SECONDS = 3600; // 1 hour

  private static final String PREF_NAME = "gemini_script_cache_prefs";
  private static final String KEY_CACHE_NAME = "gemini_script_cache_name";
  private static final String KEY_CACHE_CREATED = "gemini_script_cache_created_at";
  private static final String KEY_CACHE_TTL = "gemini_script_cache_ttl_seconds";
  private static final String KEY_PROMPT_SIGNATURE = "gemini_script_prompt_signature";
  private static final String KEY_CACHE_MODEL = "gemini_script_cache_model";
  private static final int MIN_REMAINING_TTL_SECONDS = 300; // 5 minutes

  private static final OkHttpClient streamingClient =
      new OkHttpClient.Builder()
          .connectTimeout(30, TimeUnit.SECONDS)
          .readTimeout(0, TimeUnit.SECONDS)
          .writeTimeout(30, TimeUnit.SECONDS)
          .build();

  private final Gson gson;
  private final OkHttpClient client;
  private final String apiKey;
  private final String modelName;
  private final Context context;
  private final SharedPreferences prefs;
  private final Handler mainHandler;

  private String cachedContentName;
  private boolean cacheReady = false;

  public interface ScriptGenerationCallback
      extends IDialogueGenerateManager.ScriptGenerationCallback {}

  public interface InitCallback extends IDialogueGenerateManager.InitCallback {}

  interface ValidationCallback {
    void onValid(String cacheName, long remainingSeconds);

    void onInvalid();

    void onError(String error);
  }

  // Data classes for Gson serialization
  private static class ScriptData {
    @SerializedName("topic")
    String topic;

    @SerializedName("opponent_name")
    String opponentName;

    @SerializedName("opponent_role")
    String opponentRole;

    @SerializedName("opponent_gender")
    String opponentGender;

    @SerializedName("script")
    List<ScriptLine> script;

    public ScriptData(String topic, String opponentName, List<ScriptLine> script) {
      this(topic, opponentName, "Partner", (Math.random() > 0.5 ? "male" : "female"), script);
    }

    public ScriptData(
        String topic, String opponentName, String opponentRole, List<ScriptLine> script) {
      this(topic, opponentName, opponentRole, (Math.random() > 0.5 ? "male" : "female"), script);
    }

    public ScriptData(
        String topic,
        String opponentName,
        String opponentRole,
        String opponentGender,
        List<ScriptLine> script) {
      this.topic = topic;
      this.opponentName = opponentName;
      this.opponentRole = opponentRole;
      this.opponentGender = opponentGender;
      this.script = script;
    }
  }

  private static class ScriptLine {
    @SerializedName("ko")
    String ko;

    @SerializedName("en")
    String en;

    @SerializedName("role")
    String role;

    public ScriptLine(String ko, String en, String role) {
      this.ko = ko;
      this.en = en;
      this.role = role;
    }
  }

  public DialogueGenerateManager(Context context, String apiKey, String modelName) {
    this.context = context;
    this.apiKey = normalizeOrDefault(apiKey, "");
    this.modelName = normalizeOrDefault(modelName, DEFAULT_MODEL_NAME);
    this.gson = new Gson();
    this.client =
        new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    this.mainHandler = new Handler(Looper.getMainLooper());
  }

  @Override
  public void initializeCache(IDialogueGenerateManager.InitCallback callback) {
    String systemPrompt = getSystemPrompt();
    String promptSignature = buildPromptSignature(systemPrompt);
    String savedCacheName = prefs.getString(KEY_CACHE_NAME, null);
    String savedPromptSignature = prefs.getString(KEY_PROMPT_SIGNATURE, null);
    String savedModelName = prefs.getString(KEY_CACHE_MODEL, null);

    if (savedCacheName != null
        && (!promptSignature.equals(savedPromptSignature) || !modelName.equals(savedModelName))) {
      Log.i(TAG, "Prompt or model changed. Recreating cache.");
      clearLocalCacheData();
      createCache(systemPrompt, promptSignature, callback);
      return;
    }

    if (savedCacheName != null) {
      long createdAt = prefs.getLong(KEY_CACHE_CREATED, 0);
      int ttl = prefs.getInt(KEY_CACHE_TTL, CACHE_TTL_SECONDS);
      long elapsedSeconds = (System.currentTimeMillis() - createdAt) / 1000;

      if (elapsedSeconds > ttl) {
        Log.d(TAG, "Local cache expired, creating new one");
        clearLocalCacheData();
        createCache(systemPrompt, promptSignature, callback);
        return;
      }

      validateCacheFromServer(
          savedCacheName,
          new ValidationCallback() {
              @Override
              public void onValid(String name, long remainingSeconds) {
                if (remainingSeconds > MIN_REMAINING_TTL_SECONDS) {
                  Log.i(TAG, "Reusing existing cache: " + name);
                  cachedContentName = name;
                  cacheReady = true;
                  mainHandler.post(callback::onReady);
                } else {
                  Log.i(TAG, "Cache expiring soon. Creating new cache.");
                  createCache(systemPrompt, promptSignature, callback);
                }
              }

              @Override
              public void onInvalid() {
                Log.i(TAG, "Saved cache invalid. Creating new cache.");
                clearLocalCacheData();
                createCache(systemPrompt, promptSignature, callback);
              }

              @Override
              public void onError(String error) {
                Log.w(TAG, "Validation error: " + error + ". Creating new cache.");
                createCache(systemPrompt, promptSignature, callback);
              }
            });

    } else {
      Log.i(TAG, "No local cache found. Creating new cache.");
      createCache(systemPrompt, promptSignature, callback);
    }
  }

  private void createCache(
      @NonNull String systemPrompt,
      @NonNull String promptSignature,
      IDialogueGenerateManager.InitCallback callback) {
    new Thread(
            () -> {
              try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("model", "models/" + modelName);

                JsonArray contents = new JsonArray();

                // Add system instruction as part of the cache request structure
                // Note: For createCachedContent, systemInstruction is a top-level field, NOT
                // inside contents
                // But we also usually provide some example or context in 'contents' if needed.
                // Here we just cache the system instruction mainly.
                // However, the API requires 'contents' to be present and non-empty usually?
                // Let's check GeminiCachedManager. It puts a dummy user/model turn in
                // 'contents' to satisfy requirements or provide few-shot.
                // The user request didn't strictly say to use few-shot, but caching just a
                // system prompt is valid.
                // Let's stick to just system instruction if possible, or add a simple "I'm
                // ready" turn if strictly needed for context.
                // GeminiCachedFeedbackManager used a dummy user message with context.
                // I will add a simple acknowledgment interaction to be safe and ensure the
                // model is "primed".

                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray userParts = new JsonArray();
                JsonObject userPart = new JsonObject();
                userPart.addProperty("text", "Initialize conversation script generator.");
                userParts.add(userPart);
                userContent.add("parts", userParts);
                contents.add(userContent);

                JsonObject modelContent = new JsonObject();
                modelContent.addProperty("role", "model");
                JsonArray modelParts = new JsonArray();
                JsonObject modelPart = new JsonObject();
                modelPart.addProperty(
                    "text",
                    "I am ready to generate English conversation scripts based on your requirements.");
                modelParts.add(modelPart);
                modelContent.add("parts", modelParts);
                contents.add(modelContent);

                requestBody.add("contents", contents);

                JsonObject sysInstruction = new JsonObject();
                JsonArray sysParts = new JsonArray();
                JsonObject sysPart = new JsonObject();
                sysPart.addProperty("text", systemPrompt);
                sysParts.add(sysPart);
                sysInstruction.add("parts", sysParts);
                requestBody.add("systemInstruction", sysInstruction);

                requestBody.addProperty("ttl", CACHE_TTL_SECONDS + "s");
                requestBody.addProperty("displayName", "ScriptGeneratorCache");

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
                    Log.e(TAG, "Cache creation failed: " + response.code() + " - " + responseBody);
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
                      .putString(KEY_PROMPT_SIGNATURE, promptSignature)
                      .putString(KEY_CACHE_MODEL, modelName)
                      .apply();

                  Log.i(TAG, "New cache created: " + cachedContentName);
                  mainHandler.post(callback::onReady);
                }

              } catch (Exception e) {
                Log.e(TAG, "Cache initialization error", e);
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

                  if (result.has("expireTime")) {
                    String expireTimeStr = result.get("expireTime").getAsString();
                    Instant expireTime = Instant.parse(expireTimeStr);
                    long remainingSeconds =
                        expireTime.getEpochSecond() - Instant.now().getEpochSecond();
                    callback.onValid(cacheName, remainingSeconds);
                  } else {
                    callback.onError("No expireTime in response");
                  }
                }
              } catch (Exception e) {
                Log.e(TAG, "Cache validation error", e);
                callback.onError(e.getMessage());
              }
            })
        .start();
  }

  private void clearLocalCacheData() {
    prefs
        .edit()
        .remove(KEY_CACHE_NAME)
        .remove(KEY_CACHE_CREATED)
        .remove(KEY_CACHE_TTL)
        .remove(KEY_PROMPT_SIGNATURE)
        .remove(KEY_CACHE_MODEL)
        .apply();
  }

  @Override
  public void generateScript(
      String level,
      String topic,
      String format,
      int length,
      IDialogueGenerateManager.ScriptGenerationCallback callback) {
    if (cacheReady && cachedContentName != null) {
      generateScriptWithCache(level, topic, "dialogue", length, callback);
    } else {
      Log.w(TAG, "Cache not ready, fallback to non-cached generation");
      generateScriptWithoutCache(level, topic, "dialogue", length, callback);
    }
  }

  @Override
  public void generateScriptStreamingAsync(
      @NonNull String level,
      @NonNull String topic,
      @NonNull String format,
      int length,
      @NonNull IDialogueGenerateManager.ScriptStreamingCallback callback) {
    logStream(
        "request start: level="
            + level
            + ", topic="
            + trimForLog(topic)
            + ", format="
            + format
            + ", requestedLength="
            + Math.max(1, length)
            + ", cacheReady="
            + cacheReady);
    if (cacheReady && cachedContentName != null) {
      generateScriptStreamingWithCache(level, topic, format, length, callback);
    } else {
      Log.w(TAG, "Cache not ready, fallback to non-cached streaming generation");
      generateScriptStreamingWithoutCache(level, topic, format, length, callback);
    }
  }

  private void generateScriptStreamingWithCache(
      @NonNull String level,
      @NonNull String topic,
      @NonNull String format,
      int length,
      @NonNull IDialogueGenerateManager.ScriptStreamingCallback callback) {
    logStream(
        "request path: cached, cacheName="
            + trimForLog(cachedContentName)
            + ", length="
            + Math.max(1, length));
    new Thread(
            () -> {
              try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("cachedContent", cachedContentName);
                appendScriptStreamingPayload(requestBody, level, topic, format, length);
                streamAndParseRequest(requestBody, true, length, callback);
              } catch (Exception e) {
                Log.e(TAG, "Cached streaming generation error", e);
                postStreamingFailure(callback, "Streaming error: " + safeMessage(e));
              }
            })
        .start();
  }

  private void generateScriptStreamingWithoutCache(
      @NonNull String level,
      @NonNull String topic,
      @NonNull String format,
      int length,
      @NonNull IDialogueGenerateManager.ScriptStreamingCallback callback) {
    logStream("request path: non-cached, length=" + Math.max(1, length));
    new Thread(
            () -> {
              try {
                JsonObject requestBody = new JsonObject();
                JsonObject sysInstruction = new JsonObject();
                JsonArray sysParts = new JsonArray();
                JsonObject sysPart = new JsonObject();
                sysPart.addProperty("text", getSystemPrompt());
                sysParts.add(sysPart);
                sysInstruction.add("parts", sysParts);
                requestBody.add("systemInstruction", sysInstruction);

                appendScriptStreamingPayload(requestBody, level, topic, format, length);
                streamAndParseRequest(requestBody, false, length, callback);
              } catch (Exception e) {
                Log.e(TAG, "Non-cached streaming generation error", e);
                postStreamingFailure(callback, "Streaming error: " + safeMessage(e));
              }
            })
        .start();
  }

  private void generateScriptWithCache(
      String level,
      String topic,
      String format,
      int length,
      IDialogueGenerateManager.ScriptGenerationCallback callback) {
    new Thread(
            () -> {
              try {
                JsonObject requestBody = new JsonObject();
                requestBody.addProperty("cachedContent", cachedContentName);

                JsonArray contents = new JsonArray();
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();

                String userPrompt =
                    String.format(
                        "Generate a script with these parameters:\n- level: %s\n- topic: %s\n- format: %s\n- length: %d",
                        level, topic, format, length);

                part.addProperty("text", userPrompt);
                parts.add(part);
                userContent.add("parts", parts);
                contents.add(userContent);
                requestBody.add("contents", contents);

                JsonObject generationConfig = new JsonObject();
                generationConfig.addProperty("responseMimeType", "application/json");
                requestBody.add("generationConfig", generationConfig);

                sendAndParseRequest(requestBody, true, callback);

              } catch (Exception e) {
                Log.e(TAG, "Cached generation error", e);
                mainHandler.post(() -> callback.onError(e));
              }
            })
        .start();
  }

  private void generateScriptWithoutCache(
      String level,
      String topic,
      String format,
      int length,
      IDialogueGenerateManager.ScriptGenerationCallback callback) {
    new Thread(
            () -> {
              try {
                JsonObject requestBody = new JsonObject();

                // Add system instruction directly
                JsonObject sysInstruction = new JsonObject();
                JsonArray sysParts = new JsonArray();
                JsonObject sysPart = new JsonObject();
                sysPart.addProperty("text", getSystemPrompt());
                sysParts.add(sysPart);
                sysInstruction.add("parts", sysParts);
                requestBody.add("systemInstruction", sysInstruction);

                JsonArray contents = new JsonArray();
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray parts = new JsonArray();
                JsonObject part = new JsonObject();

                String userPrompt =
                    String.format(
                        "Generate a script with these parameters:\n- level: %s\n- topic: %s\n- format: %s\n- length: %d",
                        level, topic, format, length);

                part.addProperty("text", userPrompt);
                parts.add(part);
                userContent.add("parts", parts);
                contents.add(userContent);
                requestBody.add("contents", contents);

                JsonObject generationConfig = new JsonObject();
                generationConfig.addProperty("responseMimeType", "application/json");
                requestBody.add("generationConfig", generationConfig);

                sendAndParseRequest(requestBody, false, callback);

              } catch (Exception e) {
                Log.e(TAG, "Non-cached generation error", e);
                mainHandler.post(() -> callback.onError(e));
              }
            })
        .start();
  }

  private void appendScriptStreamingPayload(
      @NonNull JsonObject requestBody,
      @NonNull String level,
      @NonNull String topic,
      @NonNull String format,
      int length) {
    JsonArray contents = new JsonArray();
    JsonObject userContent = new JsonObject();
    userContent.addProperty("role", "user");
    JsonArray parts = new JsonArray();
    JsonObject part = new JsonObject();
    String userPrompt =
        String.format(
            "Generate a script with these parameters:\n- level: %s\n- topic: %s\n- format: %s\n- length: %d",
            level, topic, format, length);
    part.addProperty("text", userPrompt);
    parts.add(part);
    userContent.add("parts", parts);
    contents.add(userContent);
    requestBody.add("contents", contents);

    JsonObject generationConfig = new JsonObject();
    generationConfig.addProperty("responseMimeType", "application/json");
    requestBody.add("generationConfig", generationConfig);
  }

  private void streamAndParseRequest(
      @NonNull JsonObject requestBody,
      boolean usedCache,
      int requestedLength,
      @NonNull IDialogueGenerateManager.ScriptStreamingCallback callback) {
    int safeRequestedLength = Math.max(1, requestedLength);
    long streamStartedAt = System.currentTimeMillis();
    int emittedTurns = 0;
    logStream("sse open: usedCache=" + usedCache + ", requestedLength=" + safeRequestedLength);
    try {
      Request request =
          new Request.Builder()
              .url(
                  BASE_URL
                      + "/models/"
                      + modelName
                      + ":streamGenerateContent?alt=sse&key="
                      + apiKey)
              .post(
                  RequestBody.create(gson.toJson(requestBody), MediaType.parse("application/json")))
              .build();

      IncrementalDialogueScriptParser parser = new IncrementalDialogueScriptParser();
      try (Response response = streamingClient.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          String body = response.body() != null ? response.body().string() : "";
          Log.e(TAG, "Streaming request failed: " + response.code() + " - " + body);
          logStream("sse failed: code=" + response.code() + ", bodyLength=" + body.length());
          if (usedCache && (response.code() == 400 || response.code() == 404)) {
            cacheReady = false;
            cachedContentName = null;
          }
          postStreamingFailure(callback, "Request failed: " + response.code());
          return;
        }
        logStream("sse connected: code=" + response.code());
        if (response.body() == null) {
          logStream("sse failed: empty body");
          postStreamingFailure(callback, "Streaming response body is empty");
          return;
        }

        BufferedSource source = response.body().source();
        while (!source.exhausted() && emittedTurns < safeRequestedLength) {
          String line = source.readUtf8Line();
          if (line == null || !line.startsWith("data: ")) {
            continue;
          }
          String data = line.substring(6).trim();
          if (data.isEmpty() || "[DONE]".equals(data)) {
            continue;
          }

          try {
            JsonObject root = JsonParser.parseString(data).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.size() == 0) {
              continue;
            }
            JsonObject content = candidates.get(0).getAsJsonObject().getAsJsonObject("content");
            if (content == null) {
              continue;
            }
            JsonArray responseParts = content.getAsJsonArray("parts");
            if (responseParts == null || responseParts.size() == 0) {
              continue;
            }

            for (JsonElement responsePart : responseParts) {
              if (responsePart == null || !responsePart.isJsonObject()) {
                continue;
              }
              JsonObject responsePartObject = responsePart.getAsJsonObject();
              if (!responsePartObject.has("text")) {
                continue;
              }

              IncrementalDialogueScriptParser.ParseUpdate update =
                  parser.addChunk(responsePartObject.get("text").getAsString());
              IncrementalDialogueScriptParser.Metadata metadata = update.getMetadata();
              if (metadata != null) {
                logStream(
                    "metadata received: topic="
                        + trimForLog(metadata.getTopic())
                        + ", opponent="
                        + trimForLog(metadata.getOpponentName())
                        + ", gender="
                        + trimForLog(metadata.getOpponentGender()));
                postStreamingMetadata(
                    callback,
                    metadata.getTopic(),
                    metadata.getOpponentName(),
                    metadata.getOpponentGender());
              }

              for (String turnObject : update.getCompletedTurnObjects()) {
                if (emittedTurns >= safeRequestedLength) {
                  break;
                }
                IDialogueGenerateManager.ScriptTurnChunk turn =
                    parseTurnObjectForStreaming(turnObject, emittedTurns);
                if (turn == null) {
                  continue;
                }
                emittedTurns++;
                logStream(
                    "turn received: "
                        + emittedTurns
                        + "/"
                        + safeRequestedLength
                        + ", role="
                        + trimForLog(turn.getRole())
                        + ", ko="
                        + previewText(turn.getKorean())
                        + ", en="
                        + previewText(turn.getEnglish()));
                postStreamingTurn(callback, turn);
              }
            }
          } catch (Exception ignored) {
            // Ignore malformed chunk and continue streaming.
          }
        }
      }

      if (emittedTurns <= 0) {
        logStream("stream finished without valid turns");
        postStreamingFailure(callback, "Failed to parse script turns");
        return;
      }
      logStream(
          "stream complete: emittedTurns="
              + emittedTurns
              + ", elapsedMs="
              + (System.currentTimeMillis() - streamStartedAt));
      postStreamingComplete(callback, null);
    } catch (Exception e) {
      logStream(
          "stream exception: emittedTurns="
              + emittedTurns
              + ", message="
              + trimForLog(safeMessage(e)));
      postStreamingFailure(callback, "Streaming error: " + safeMessage(e));
    }
  }

  @Nullable
  private IDialogueGenerateManager.ScriptTurnChunk parseTurnObjectForStreaming(
      @Nullable String turnObject, int turnIndex) {
    if (turnObject == null || turnObject.trim().isEmpty()) {
      return null;
    }
    try {
      JsonObject item = JsonParser.parseString(turnObject).getAsJsonObject();
      String korean = trimToNull(readAsString(item, "ko"));
      String english = trimToNull(readAsString(item, "en"));
      if (korean == null || english == null) {
        return null;
      }
      String role = trimToNull(readAsString(item, "role"));
      if (role == null) {
        role = turnIndex % 2 == 0 ? "model" : "user";
      }
      return new IDialogueGenerateManager.ScriptTurnChunk(korean, english, role);
    } catch (Exception ignored) {
      return null;
    }
  }

  private void postStreamingMetadata(
      @NonNull IDialogueGenerateManager.ScriptStreamingCallback callback,
      @NonNull String topic,
      @NonNull String opponentName,
      @NonNull String opponentGender) {
    mainHandler.post(() -> callback.onMetadata(topic, opponentName, opponentGender));
  }

  private void postStreamingTurn(
      @NonNull IDialogueGenerateManager.ScriptStreamingCallback callback,
      @NonNull IDialogueGenerateManager.ScriptTurnChunk turn) {
    mainHandler.post(() -> callback.onTurn(turn));
  }

  private void postStreamingComplete(
      @NonNull IDialogueGenerateManager.ScriptStreamingCallback callback,
      @Nullable String warningMessage) {
    mainHandler.post(() -> callback.onComplete(warningMessage));
  }

  private void postStreamingFailure(
      @NonNull IDialogueGenerateManager.ScriptStreamingCallback callback, @NonNull String error) {
    mainHandler.post(() -> callback.onFailure(error));
  }

  private void sendAndParseRequest(
      JsonObject requestBody,
      boolean usedCache,
      IDialogueGenerateManager.ScriptGenerationCallback callback) {
    try {
      Log.d(TAG, "Sending script generation request. Used cache: " + usedCache);
      String jsonBody = gson.toJson(requestBody);
      Request request =
          new Request.Builder()
              .url(
                  requestBody.has("cachedContent")
                      ? BASE_URL + "/models/" + modelName + ":generateContent?key=" + apiKey
                      : BASE_URL + "/models/" + modelName + ":generateContent?key=" + apiKey)
              .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
              .build();

      try (Response response = client.newCall(request).execute()) {
        String responseBody = response.body() != null ? response.body().string() : "";

        if (!response.isSuccessful()) {
          Log.e(TAG, "Request failed: " + response.code() + " - " + responseBody);
          if (usedCache && (response.code() == 400 || response.code() == 404)) {
            cacheReady = false;
            cachedContentName = null;
            // Optionally retry without cache here, but for now just error out or
            // let user retry
          }
          throw new Exception("Request failed: " + response.code());
        }

        JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonArray candidates = root.getAsJsonArray("candidates");
        if (candidates != null && candidates.size() > 0) {
          JsonObject candidate = candidates.get(0).getAsJsonObject();
          JsonObject content = candidate.getAsJsonObject("content");
          JsonArray parts = content.getAsJsonArray("parts");
          String text = parts.get(0).getAsJsonObject().get("text").getAsString();
          mainHandler.post(() -> callback.onSuccess(text));
        } else {
          throw new Exception("No candidates found");
        }
      }
    } catch (Exception e) {
      mainHandler.post(() -> callback.onError(e));
    }
  }

  private String getSystemPrompt() {
    String prompt = readAssetFile("prompts/dialogue_generate/system_prompt.md");
    if (prompt.isEmpty()) {
      return getSystemPrompt_Dummy();
    }
    return prompt;
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

  private String getSystemPrompt_Dummy() {
    return "You are an English conversation script generator for Korean learners.\n"
        + "The user provides level, topic, format, and length.\n"
        + "Return ONLY valid JSON. Do not output markdown or extra text.\n"
        + "\n"
        + "Use this exact snake_case schema:\n"
        + "{\n"
        + "  \"topic\": \"Korean topic title (<=15 chars)\",\n"
        + "  \"opponent_name\": \"Partner name/title\",\n"
        + "  \"opponent_gender\": \"male\",\n"
        + "  \"opponent_role\": \"Partner role in English\",\n"
        + "  \"script\": [\n"
        + "    {\n"
        + "      \"ko\": \"Korean translation\",\n"
        + "      \"en\": \"English original\",\n"
        + "      \"role\": \"model\"\n"
        + "    }\n"
        + "  ]\n"
        + "}\n"
        + "\n"
        + "Rules:\n"
        + "1) Keys must be snake_case exactly: topic, opponent_name, opponent_gender, opponent_role, script, ko, en, role.\n"
        + "2) Put metadata fields before the script array.\n"
        + "3) role must be exactly \"model\" or \"user\".\n"
        + "4) topic must be Korean and 15 characters or fewer.\n"
        + "5) script length must be EXACTLY the requested length.\n"
        + "6) Start with opponent(model) and alternate naturally.\n"
        + "7) Final line must clearly close the conversation.\n"
        + "8) Respect the requested proficiency level.\n"
        + "9) Output only the JSON object.";
  }

  @Nullable
  private static String readAsString(@Nullable JsonObject obj, @NonNull String key) {
    try {
      if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) {
        return obj.get(key).getAsString();
      }
    } catch (Exception ignored) {
      // No-op.
    }
    return null;
  }

  @Nullable
  private static String trimToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  @NonNull
  private static String safeMessage(@Nullable Exception e) {
    if (e == null) {
      return "unknown";
    }
    String message = e.getMessage();
    return message == null ? "unknown" : message;
  }

  private void logStream(@NonNull String message) {
    Log.d(TAG, "[DL_STREAM] " + message);
  }

  @NonNull
  private static String previewText(@Nullable String text) {
    String value = trimToNull(text);
    if (value == null) {
      return "-";
    }
    if (value.length() <= 24) {
      return value;
    }
    return value.substring(0, 24) + "...";
  }

  @NonNull
  private static String trimForLog(@Nullable String value) {
    String trimmed = trimToNull(value);
    return trimmed == null ? "-" : trimmed;
  }

  @NonNull
  private static String buildPromptSignature(@Nullable String prompt) {
    String value = normalizeOrDefault(prompt, "");
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return toHex(hash);
    } catch (Exception ignored) {
      return Integer.toHexString(value.hashCode());
    }
  }

  @NonNull
  private static String toHex(@NonNull byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte current : bytes) {
      String hex = Integer.toHexString(current & 0xff);
      if (hex.length() == 1) {
        builder.append('0');
      }
      builder.append(hex);
    }
    return builder.toString();
  }

  private static String normalizeOrDefault(String value, String defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? defaultValue : trimmed;
  }
}
