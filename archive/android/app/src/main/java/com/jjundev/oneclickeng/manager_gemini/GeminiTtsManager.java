package com.jjundev.oneclickeng.manager_gemini;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.GeminiTtsAudio;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IGeminiTtsManager;
import java.io.IOException;
import java.util.Locale;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class GeminiTtsManager implements IGeminiTtsManager {
  private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
  private static final String MODEL_NAME = "gemini-2.5-flash-preview-tts";
  private static final int DEFAULT_SAMPLE_RATE_HZ = 24000;
  private static final Pattern MIME_RATE_PATTERN = Pattern.compile("rate=(\\d+)");

  private final Object requestLock = new Object();
  @NonNull private final OkHttpClient client;
  @NonNull private final Gson gson;
  @NonNull private final Handler mainHandler;
  @NonNull private final String apiKey;

  @Nullable private Call activeCall;
  private long requestToken = 0L;

  public GeminiTtsManager(@NonNull String apiKey) {
    this.apiKey = normalize(apiKey);
    this.client =
        new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    this.gson = new Gson();
    this.mainHandler = new Handler(Looper.getMainLooper());
  }

  @Override
  public void synthesize(
      @NonNull String text,
      @Nullable String localeTag,
      float speechRate,
      @Nullable String voiceName,
      @NonNull SynthesisCallback callback) {
    String safeText = normalize(text);
    if (safeText.isEmpty()) {
      postError(callback, "Text is empty");
      return;
    }
    if (apiKey.isEmpty()) {
      postError(callback, "Gemini API key is missing");
      return;
    }

    long token = reserveRequestToken();
    String resolvedVoiceName = normalize(voiceName).isEmpty() ? "Kore" : normalize(voiceName);
    Request request = buildSynthesisRequest(safeText, localeTag, speechRate, resolvedVoiceName);

    new Thread(
            () -> {
              Call call = client.newCall(request);
              if (!setActiveCallIfCurrent(token, call)) {
                call.cancel();
                return;
              }

              try (Response response = call.execute()) {
                if (!isRequestCurrent(token)) {
                  return;
                }
                if (!response.isSuccessful()) {
                  String body = response.body() != null ? response.body().string() : "";
                  postErrorIfCurrent(
                      token,
                      callback,
                      "Gemini TTS request failed: " + response.code() + (body.isEmpty() ? "" : " " + body));
                  return;
                }

                String body = response.body() != null ? response.body().string() : "";
                GeminiTtsAudio audio = parseAudioFromResponseBody(body);
                postSuccessIfCurrent(token, callback, audio);
              } catch (Exception e) {
                if (call.isCanceled() || !isRequestCurrent(token)) {
                  return;
                }
                postErrorIfCurrent(
                    token, callback, "Gemini TTS synthesize failed: " + normalize(e.getMessage()));
              } finally {
                clearActiveCall(token, call);
              }
            })
        .start();
  }

  @Override
  public void cancelActiveRequest() {
    Call callToCancel;
    synchronized (requestLock) {
      requestToken++;
      callToCancel = activeCall;
      activeCall = null;
    }
    if (callToCancel != null) {
      callToCancel.cancel();
    }
  }

  @NonNull
  private Request buildSynthesisRequest(
      @NonNull String text, @Nullable String localeTag, float speechRate, @NonNull String voiceName) {
    JsonObject root = new JsonObject();
    JsonArray contents = new JsonArray();
    JsonObject content = new JsonObject();
    JsonArray parts = new JsonArray();
    JsonObject textPart = new JsonObject();
    textPart.addProperty("text", buildSpeechPrompt(text, localeTag, speechRate));
    parts.add(textPart);
    content.add("parts", parts);
    contents.add(content);
    root.add("contents", contents);

    JsonObject generationConfig = new JsonObject();
    JsonArray responseModalities = new JsonArray();
    responseModalities.add("AUDIO");
    generationConfig.add("responseModalities", responseModalities);

    JsonObject speechConfig = new JsonObject();
    JsonObject voiceConfig = new JsonObject();
    JsonObject prebuiltVoiceConfig = new JsonObject();
    prebuiltVoiceConfig.addProperty("voiceName", voiceName);
    voiceConfig.add("prebuiltVoiceConfig", prebuiltVoiceConfig);
    speechConfig.add("voiceConfig", voiceConfig);
    generationConfig.add("speechConfig", speechConfig);
    root.add("generationConfig", generationConfig);

    String url = BASE_URL + "/models/" + MODEL_NAME + ":generateContent?key=" + apiKey;
    String json = gson.toJson(root);
    return new Request.Builder()
        .url(url)
        .post(RequestBody.create(json, MediaType.parse("application/json")))
        .build();
  }

  @NonNull
  private String buildSpeechPrompt(@NonNull String text, @Nullable String localeTag, float speechRate) {
    float clamped = clamp(speechRate, 0.5f, 1.5f);
    String locale = normalize(localeTag);
    if (locale.isEmpty()) {
      locale = "en-US";
    }
    return "Read the following text out loud only. "
        + "Use pronunciation for locale "
        + locale
        + ". "
        + "Aim for speaking speed multiplier "
        + String.format(Locale.US, "%.2f", clamped)
        + ". "
        + "Do not add commentary or extra words. "
        + "Text: "
        + text;
  }

  @NonNull
  static GeminiTtsAudio parseAudioFromResponseBody(@Nullable String responseBody) {
    String safeBody = normalize(responseBody);
    if (safeBody.isEmpty()) {
      throw new IllegalStateException("Empty Gemini TTS response");
    }
    JsonObject root = JsonParser.parseString(safeBody).getAsJsonObject();
    if (!root.has("candidates")) {
      throw new IllegalStateException("Missing candidates");
    }
    JsonArray candidates = root.getAsJsonArray("candidates");
    if (candidates == null || candidates.size() == 0) {
      throw new IllegalStateException("No candidates");
    }

    for (JsonElement candidateElement : candidates) {
      if (candidateElement == null || !candidateElement.isJsonObject()) {
        continue;
      }
      JsonObject candidate = candidateElement.getAsJsonObject();
      if (!candidate.has("content") || candidate.get("content").isJsonNull()) {
        continue;
      }
      JsonObject content = candidate.getAsJsonObject("content");
      if (!content.has("parts") || content.get("parts").isJsonNull()) {
        continue;
      }
      JsonArray parts = content.getAsJsonArray("parts");
      if (parts == null) {
        continue;
      }

      for (JsonElement partElement : parts) {
        if (partElement == null || !partElement.isJsonObject()) {
          continue;
        }
        JsonObject part = partElement.getAsJsonObject();
        if (!part.has("inlineData") || part.get("inlineData").isJsonNull()) {
          continue;
        }
        JsonObject inlineData = part.getAsJsonObject("inlineData");
        if (!inlineData.has("data") || inlineData.get("data").isJsonNull()) {
          continue;
        }
        String base64 = normalize(inlineData.get("data").getAsString());
        if (base64.isEmpty()) {
          continue;
        }
        String mimeType =
            inlineData.has("mimeType") && !inlineData.get("mimeType").isJsonNull()
                ? normalize(inlineData.get("mimeType").getAsString())
                : "";
        if (mimeType.isEmpty()) {
          mimeType = "audio/L16;rate=" + DEFAULT_SAMPLE_RATE_HZ;
        }
        byte[] pcm;
        try {
          pcm = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
          throw new IllegalStateException("Invalid base64 audio payload");
        }
        if (pcm == null || pcm.length == 0) {
          throw new IllegalStateException("Decoded audio is empty");
        }
        int sampleRateHz = parseSampleRateFromMimeType(mimeType);
        return new GeminiTtsAudio(pcm, sampleRateHz, mimeType);
      }
    }

    throw new IllegalStateException("Missing inlineData audio");
  }

  static int parseSampleRateFromMimeType(@Nullable String mimeType) {
    String safeMimeType = normalize(mimeType);
    if (safeMimeType.isEmpty()) {
      return DEFAULT_SAMPLE_RATE_HZ;
    }
    Matcher matcher = MIME_RATE_PATTERN.matcher(safeMimeType);
    if (!matcher.find()) {
      return DEFAULT_SAMPLE_RATE_HZ;
    }
    try {
      int parsed = Integer.parseInt(matcher.group(1));
      return parsed > 0 ? parsed : DEFAULT_SAMPLE_RATE_HZ;
    } catch (NumberFormatException e) {
      return DEFAULT_SAMPLE_RATE_HZ;
    }
  }

  private long reserveRequestToken() {
    Call callToCancel;
    long token;
    synchronized (requestLock) {
      requestToken++;
      token = requestToken;
      callToCancel = activeCall;
      activeCall = null;
    }
    if (callToCancel != null) {
      callToCancel.cancel();
    }
    return token;
  }

  private boolean setActiveCallIfCurrent(long token, @NonNull Call call) {
    synchronized (requestLock) {
      if (token != requestToken) {
        return false;
      }
      activeCall = call;
      return true;
    }
  }

  private boolean isRequestCurrent(long token) {
    synchronized (requestLock) {
      return token == requestToken;
    }
  }

  private void clearActiveCall(long token, @NonNull Call call) {
    synchronized (requestLock) {
      if (token == requestToken && activeCall == call) {
        activeCall = null;
      }
    }
  }

  private void postSuccessIfCurrent(
      long token, @NonNull SynthesisCallback callback, @NonNull GeminiTtsAudio audio) {
    if (!isRequestCurrent(token)) {
      return;
    }
    mainHandler.post(
        () -> {
          if (!isRequestCurrent(token)) {
            return;
          }
          callback.onSuccess(audio);
        });
  }

  private void postErrorIfCurrent(long token, @NonNull SynthesisCallback callback, @NonNull String error) {
    if (!isRequestCurrent(token)) {
      return;
    }
    postError(callback, error);
  }

  private void postError(@NonNull SynthesisCallback callback, @NonNull String error) {
    mainHandler.post(() -> callback.onError(error));
  }

  @NonNull
  private static String normalize(@Nullable String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? "" : trimmed;
  }

  private static float clamp(float value, float min, float max) {
    if (value < min) {
      return min;
    }
    return Math.min(value, max);
  }
}
