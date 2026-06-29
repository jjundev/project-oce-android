package com.jjundev.oneclickeng.manager_gemini;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IExtraQuestionManager;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;

/** Gemini 추가 질문 처리 매니저 피드백 결과에 대한 사용자의 추가 질문을 처리합니다. */
public class ExtraQuestionManager
    implements com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts
        .IExtraQuestionManager {
  private static final String TAG = "ExtraQuestionManager";
  private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
  private static final String DEFAULT_MODEL_NAME = "gemini-3.1-flash-lite-preview";

  private final String apiKey;
  private final String modelName;
  private final Handler mainHandler;
  private final Gson gson;

  private static final OkHttpClient streamingClient =
      new OkHttpClient.Builder()
          .connectTimeout(30, TimeUnit.SECONDS)
          .readTimeout(0, TimeUnit.SECONDS) // 스트리밍을 위해 타임아웃 무제한
          .writeTimeout(30, TimeUnit.SECONDS)
          .build();

  public ExtraQuestionManager(String apiKey, String modelName) {
    this.apiKey = normalizeOrDefault(apiKey, "");
    this.modelName = normalizeOrDefault(modelName, DEFAULT_MODEL_NAME);
    this.mainHandler = new Handler(Looper.getMainLooper());
    this.gson = new Gson();
  }

  /**
   * 사용자의 추가 질문에 대해 스트리밍 응답을 생성합니다.
   *
   * @param originalSentence 원본 한국어 문장
   * @param userSentence 사용자의 영어 번역
   * @param userQuestion 사용자의 추가 질문
   * @param callback 스트리밍 응답 콜백
   */
  @Override
  public void askExtraQuestionStreaming(
      String originalSentence,
      String userSentence,
      String userQuestion,
      IExtraQuestionManager.StreamingResponseCallback callback) {
    new Thread(
            () -> {
              try {
                JsonObject requestBody = new JsonObject();
                JsonArray contents = new JsonArray();
                JsonObject userContent = new JsonObject();
                userContent.addProperty("role", "user");
                JsonArray parts = new JsonArray();

                JsonObject textPart = new JsonObject();
                String prompt =
                    "[Translation Context]\n"
                        + "- Target Korean: \""
                        + originalSentence
                        + "\"\n"
                        + "- User's Translation: \""
                        + userSentence
                        + "\"\n\n"
                        + "[User Question]\n"
                        + "\""
                        + userQuestion
                        + "\"\n\n"
                        + "[Instruction]\n"
                        + "You are a helpful English tutor who has just finished giving feedback on the user's English translation. Now the user has a follow-up question. Based on the [Translation Context], please answer the [User Question] accurately in Korean.\n\n"
                        + "[Constraints]\n"
                        + "1. Do NOT re-evaluate the entire translation. Only re-evaluate if the user explicitly asks for it.\n"
                        + "2. Focus strictly on answering the [User Question] directly.\n"
                        + "3. Keep the answer concise and helpful, within 2-3 sentences unless a longer explanation is necessary.\n"
                        + "4. The final answer MUST be written in Korean.\n"
                        + "5. Your output will be displayed directly in a mobile app UI as plain text. Do NOT use any markdown formatting whatsoever — no bold (**), no italics (*_), no headers (#), no bullet points (- or *), no code blocks (```). Use only plain text with line breaks for readability.\n"
                        + "6. If the question is unrelated to English learning, politely redirect the user to ask English-related questions.";
                textPart.addProperty("text", prompt);
                parts.add(textPart);

                userContent.add("parts", parts);
                contents.add(userContent);
                requestBody.add("contents", contents);

                String url =
                    BASE_URL
                        + "/models/"
                        + modelName
                        + ":streamGenerateContent?alt=sse&key="
                        + apiKey;
                String jsonBody = gson.toJson(requestBody);

                Request request =
                    new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                        .build();

                try (Response response = streamingClient.newCall(request).execute()) {
                  if (!response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.e(
                        TAG, "Streaming Request failed: " + response.code() + " - " + responseBody);
                    mainHandler.post(() -> callback.onError("Request failed: " + response.code()));
                    return;
                  }

                  BufferedSource source = response.body().source();
                  while (!source.exhausted()) {
                    String line = source.readUtf8Line();
                    if (line != null && line.startsWith("data: ")) {
                      String json = line.substring(6).trim();
                      if (json.equals("[DONE]")) break;

                      try {
                        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
                        JsonArray candidates = root.getAsJsonArray("candidates");
                        if (candidates != null && candidates.size() > 0) {
                          JsonObject content =
                              candidates.get(0).getAsJsonObject().getAsJsonObject("content");
                          JsonArray partsArr = content.getAsJsonArray("parts");
                          if (partsArr != null && partsArr.size() > 0) {
                            String text =
                                partsArr.get(0).getAsJsonObject().get("text").getAsString();
                            mainHandler.post(() -> callback.onTextChunk(text));
                          }
                        }
                      } catch (Exception e) {
                        // 개별 청크 파싱 에러는 무시
                      }
                    }
                  }
                  mainHandler.post(callback::onComplete);
                }
              } catch (Exception e) {
                Log.e(TAG, "Streaming error", e);
                mainHandler.post(() -> callback.onError("Error: " + e.getMessage()));
              }
            })
        .start();
  }

  private static String normalizeOrDefault(String value, String defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? defaultValue : trimmed;
  }
}
