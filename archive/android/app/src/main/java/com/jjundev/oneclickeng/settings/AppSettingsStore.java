package com.jjundev.oneclickeng.settings;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class AppSettingsStore {
  public static final String PREF_NAME = "app_settings";
  private static final String KEY_MUTE_ALL_PLAYBACK = "mute_all_playback";
  private static final String KEY_USER_NICKNAME = "user_nickname";
  private static final String KEY_LLM_API_KEY_OVERRIDE = "llm_api_key_override";
  private static final String KEY_LLM_MODEL_SENTENCE = "llm_model_sentence";
  private static final String KEY_LLM_MODEL_SPEAKING = "llm_model_speaking";
  private static final String KEY_LLM_MODEL_SCRIPT = "llm_model_script";
  private static final String KEY_LLM_MODEL_SUMMARY = "llm_model_summary";
  private static final String KEY_LLM_MODEL_EXTRA = "llm_model_extra";
  private static final String KEY_TTS_PROVIDER = "tts_provider";
  private static final String KEY_TTS_SPEECH_RATE = "tts_speech_rate";
  private static final String KEY_TTS_LOCALE_TAG = "tts_locale_tag";

  @NonNull private final SharedPreferences preferences;

  public AppSettingsStore(@NonNull Context context) {
    preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
  }

  @NonNull
  public AppSettings getSettings() {
    return new AppSettings(
        preferences.getBoolean(KEY_MUTE_ALL_PLAYBACK, false),
        preferences.getString(KEY_USER_NICKNAME, ""),
        preferences.getString(KEY_LLM_API_KEY_OVERRIDE, ""),
        preferences.getString(KEY_LLM_MODEL_SENTENCE, AppSettings.DEFAULT_MODEL_SENTENCE),
        preferences.getString(KEY_LLM_MODEL_SPEAKING, AppSettings.DEFAULT_MODEL_SPEAKING),
        preferences.getString(KEY_LLM_MODEL_SCRIPT, AppSettings.DEFAULT_MODEL_SCRIPT),
        preferences.getString(KEY_LLM_MODEL_SUMMARY, AppSettings.DEFAULT_MODEL_SUMMARY),
        preferences.getString(KEY_LLM_MODEL_EXTRA, AppSettings.DEFAULT_MODEL_EXTRA),
        preferences.getString(KEY_TTS_PROVIDER, AppSettings.DEFAULT_TTS_PROVIDER),
        preferences.getFloat(KEY_TTS_SPEECH_RATE, AppSettings.DEFAULT_TTS_SPEECH_RATE),
        preferences.getString(KEY_TTS_LOCALE_TAG, AppSettings.DEFAULT_TTS_LOCALE_TAG));
  }

  public void setMuteAllPlayback(boolean enabled) {
    preferences.edit().putBoolean(KEY_MUTE_ALL_PLAYBACK, enabled).apply();
  }

  public void setUserNickname(@Nullable String nickname) {
    preferences.edit().putString(KEY_USER_NICKNAME, normalizeOrEmpty(nickname)).apply();
  }

  public void setLlmApiKeyOverride(@Nullable String apiKeyOverride) {
    // API 키 수동 설정을 지원하지 않음
  }

  public void setLlmModelSentence(@Nullable String modelName) {
    // 모델 수동 설정을 지원하지 않음
  }

  public void setLlmModelSpeaking(@Nullable String modelName) {
    // 모델 수동 설정을 지원하지 않음
  }

  public void setLlmModelScript(@Nullable String modelName) {
    // 모델 수동 설정을 지원하지 않음
  }

  public void setLlmModelSummary(@Nullable String modelName) {
    // 모델 수동 설정을 지원하지 않음
  }

  public void setLlmModelExtra(@Nullable String modelName) {
    // 모델 수동 설정을 지원하지 않음
  }

  public void setTtsProvider(@Nullable String ttsProvider) {
    preferences
        .edit()
        .putString(
            KEY_TTS_PROVIDER, normalizeOrDefault(ttsProvider, AppSettings.DEFAULT_TTS_PROVIDER))
        .apply();
  }

  public void setTtsSpeechRate(float speechRate) {
    float clamped = Math.max(0.5f, Math.min(speechRate, 1.5f));
    preferences.edit().putFloat(KEY_TTS_SPEECH_RATE, clamped).apply();
  }

  public void setTtsLocaleTag(@Nullable String localeTag) {
    preferences
        .edit()
        .putString(
            KEY_TTS_LOCALE_TAG, normalizeOrDefault(localeTag, AppSettings.DEFAULT_TTS_LOCALE_TAG))
        .apply();
  }

  @NonNull
  private static String normalizeOrDefault(@Nullable String value, @NonNull String defaultValue) {
    String normalized = normalizeOrEmpty(value);
    return normalized.isEmpty() ? defaultValue : normalized;
  }

  @NonNull
  private static String normalizeOrEmpty(@Nullable String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? "" : trimmed;
  }
}
