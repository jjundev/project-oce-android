package com.jjundev.oneclickeng.learning.dialoguelearning.coordinator;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.GeminiTtsAudio;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IGeminiTtsManager;
import com.jjundev.oneclickeng.settings.AppSettings;
import com.jjundev.oneclickeng.tool.RecordingAudioPlayer;
import java.util.Locale;
import java.util.Set;

public final class DialoguePlaybackCoordinator {
  private static final float DEFAULT_TTS_SPEECH_RATE = 1.00f;
  @NonNull private static final Locale DEFAULT_TTS_LOCALE = Locale.US;
  private static final long SCRIPT_TTS_WATCHDOG_ANDROID_MS = 7000L;
  private static final long SCRIPT_TTS_WATCHDOG_GEMINI_MS = 30000L;

  public interface LoggerDelegate {
    void trace(@NonNull String key);

    void gate(@NonNull String key);

    void ux(@NonNull String key, @Nullable String fields);
  }

  public interface OnTtsInitializedListener {
    void onTtsInitialized(boolean ready);
  }

  public interface PlaybackErrorListener {
    void onError(@NonNull String message);
  }

  @NonNull private final Handler mainHandler;
  @NonNull private final LoggerDelegate loggerDelegate;

  @Nullable private TextToSpeech tts;
  private boolean isTtsReady;
  @Nullable private RecordingAudioPlayer recordingAudioPlayer;
  @Nullable private RecordingAudioPlayer geminiPcmPlayer;
  @Nullable private IGeminiTtsManager geminiTtsManager;
  @Nullable private ImageView currentlyPlayingSpeakerBtn;
  @Nullable private Runnable ttsWatchdogRunnable;
  @Nullable private Runnable currentTtsOnDoneCallback;
  @Nullable private String currentTtsSource;
  @Nullable private String currentUtteranceId;
  private boolean isPlaybackStopInProgress;
  private float ttsSpeechRate = DEFAULT_TTS_SPEECH_RATE;
  @NonNull private Locale ttsLocale = DEFAULT_TTS_LOCALE;
  @NonNull private String ttsProvider = AppSettings.DEFAULT_TTS_PROVIDER;
  private long ttsSessionToken = 0L;

  public DialoguePlaybackCoordinator(
      @NonNull Handler mainHandler, @NonNull LoggerDelegate loggerDelegate) {
    this.mainHandler = mainHandler;
    this.loggerDelegate = loggerDelegate;
  }

  public void setGeminiTtsManager(@Nullable IGeminiTtsManager geminiTtsManager) {
    this.geminiTtsManager = geminiTtsManager;
  }

  public void initialize(@NonNull Context context, @NonNull OnTtsInitializedListener listener) {
    release();
    recordingAudioPlayer = new RecordingAudioPlayer();
    geminiPcmPlayer = new RecordingAudioPlayer();
    tts =
        new TextToSpeech(
            context,
            status -> {
              boolean ready = false;
              if (status == TextToSpeech.SUCCESS && tts != null) {
                ready = applyConfiguredTtsLanguage();
                tts.setSpeechRate(ttsSpeechRate);
              }
              isTtsReady = ready;
              listener.onTtsInitialized(ready);
            });

    if (tts != null) {
      tts.setOnUtteranceProgressListener(
          new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {}

            @Override
            public void onDone(String utteranceId) {
              onAndroidUtteranceTerminal(utteranceId, false);
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onError(String utteranceId) {
              onAndroidUtteranceTerminal(utteranceId, true);
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
              onError(utteranceId);
            }
          });
    }
  }

  public void release() {
    isTtsReady = false;
    stopAllPlayback("release");
    if (tts != null) {
      tts.shutdown();
      tts = null;
    }
    if (recordingAudioPlayer != null) {
      recordingAudioPlayer.stop();
      recordingAudioPlayer = null;
    }
    if (geminiPcmPlayer != null) {
      geminiPcmPlayer.stop();
      geminiPcmPlayer = null;
    }
    resetSpeakerButton();
  }

  public void stopAllPlayback(@NonNull String reason) {
    if (isPlaybackStopInProgress) {
      return;
    }
    isPlaybackStopInProgress = true;
    try {
      ttsSessionToken++;
      currentUtteranceId = null;
      currentTtsOnDoneCallback = null;
      currentTtsSource = null;
      if (ttsWatchdogRunnable != null) {
        mainHandler.removeCallbacks(ttsWatchdogRunnable);
        ttsWatchdogRunnable = null;
      }
      if (geminiTtsManager != null) {
        geminiTtsManager.cancelActiveRequest();
      }
      if (tts != null && tts.isSpeaking()) {
        tts.stop();
      }
      if (recordingAudioPlayer != null) {
        recordingAudioPlayer.stop();
      }
      if (geminiPcmPlayer != null) {
        geminiPcmPlayer.stop();
      }
      resetSpeakerButton();
    } finally {
      isPlaybackStopInProgress = false;
    }
  }

  public void applyTtsSettings(
      @Nullable String provider, float speechRate, @Nullable String localeTag) {
    ttsProvider = normalizeProvider(provider);
    ttsSpeechRate = clamp(speechRate, 0.5f, 1.5f);
    ttsLocale = resolveLocale(localeTag);
    if (tts == null) {
      return;
    }
    applyConfiguredTtsLanguage();
    tts.setSpeechRate(ttsSpeechRate);
  }

  public void applyTtsSettings(float speechRate, @Nullable String localeTag) {
    applyTtsSettings(ttsProvider, speechRate, localeTag);
  }

  public void playMessageTts(
      @NonNull String text,
      @Nullable ImageView speakerBtn,
      @Nullable String gender,
      @Nullable PlaybackErrorListener errorListener) {
    if (speakerBtn != null && currentlyPlayingSpeakerBtn == speakerBtn) {
      stopAllPlayback("playMessageTts_toggle");
      return;
    }

    stopAllPlayback("playMessageTts_start");
    long sessionToken = ttsSessionToken;

    if (speakerBtn != null) {
      currentlyPlayingSpeakerBtn = speakerBtn;
      speakerBtn.setImageResource(R.drawable.ic_speaker_active);
      speakerBtn.setColorFilter(0xFF2196F3);
    }

    currentTtsSource = "message";
    currentTtsOnDoneCallback = null;
    loggerDelegate.ux("UX_TTS_START", "source=message");

    if (shouldAttemptGeminiSynthesis(ttsProvider, geminiTtsManager != null)) {
      playMessageWithGemini(text, gender, sessionToken, errorListener);
      return;
    }
    playMessageWithAndroidTts(text, gender, sessionToken, errorListener);
  }

  public void playRecordedAudio(
      @Nullable byte[] audioData,
      @Nullable ImageView speakerBtn,
      @Nullable PlaybackErrorListener errorListener) {
    if (speakerBtn != null && currentlyPlayingSpeakerBtn == speakerBtn) {
      stopAllPlayback("playRecordedAudio_toggle");
      return;
    }

    stopAllPlayback("playRecordedAudio_start");

    if (audioData == null || audioData.length == 0 || recordingAudioPlayer == null) {
      loggerDelegate.ux("UX_TTS_DONE", "source=recorded_error");
      if (errorListener != null) {
        errorListener.onError("Audio unavailable");
      }
      return;
    }

    if (speakerBtn != null) {
      currentlyPlayingSpeakerBtn = speakerBtn;
      speakerBtn.setImageResource(R.drawable.ic_speaker_active);
      speakerBtn.setColorFilter(0xFF2196F3);
    }

    loggerDelegate.ux("UX_TTS_START", "source=recorded");
    recordingAudioPlayer.play(
        audioData,
        new RecordingAudioPlayer.PlaybackCallback() {
          @Override
          public void onPlaybackCompleted() {
            resetSpeakerButton();
            loggerDelegate.ux("UX_TTS_DONE", "source=recorded_callback");
          }

          @Override
          public void onPlaybackError(String error) {
            resetSpeakerButton();
            loggerDelegate.ux("UX_TTS_DONE", "source=recorded_error");
            if (errorListener != null) {
              errorListener.onError(error == null ? "Playback failed" : error);
            }
          }
        });
  }

  public void playScriptTts(
      @Nullable String text,
      @Nullable String gender,
      @Nullable Runnable activateLatestAiSpeaker,
      @NonNull Runnable onComplete) {
    if (text == null || text.isEmpty()) {
      loggerDelegate.trace("TRACE_TTS_FLOW reason=empty_text");
      onComplete.run();
      return;
    }
    loggerDelegate.trace("TRACE_TTS_FLOW source=playScriptTts textLen=" + text.length());

    stopAllPlayback("playScriptTts_start");
    long sessionToken = ttsSessionToken;

    if (activateLatestAiSpeaker != null) {
      activateLatestAiSpeaker.run();
    }

    currentTtsSource = "script";
    currentTtsOnDoneCallback = onComplete;

    long watchdogMs =
        shouldAttemptGeminiSynthesis(ttsProvider, geminiTtsManager != null)
            ? SCRIPT_TTS_WATCHDOG_GEMINI_MS
            : SCRIPT_TTS_WATCHDOG_ANDROID_MS;
    scheduleScriptWatchdog(sessionToken, onComplete, watchdogMs);

    if (shouldAttemptGeminiSynthesis(ttsProvider, geminiTtsManager != null)) {
      playScriptWithGemini(text, gender, sessionToken, onComplete);
      return;
    }
    playScriptWithAndroidTts(text, gender, sessionToken, onComplete);
  }

  private void playMessageWithGemini(
      @NonNull String text,
      @Nullable String gender,
      long sessionToken,
      @Nullable PlaybackErrorListener errorListener) {
    IGeminiTtsManager manager = geminiTtsManager;
    if (manager == null) {
      playMessageWithAndroidTts(text, gender, sessionToken, errorListener);
      return;
    }
    String voiceName = resolveGeminiVoiceName(gender);
    manager.synthesize(
        text,
        ttsLocale.toLanguageTag(),
        ttsSpeechRate,
        voiceName,
        new IGeminiTtsManager.SynthesisCallback() {
          @Override
          public void onSuccess(@NonNull GeminiTtsAudio audio) {
            if (!isSessionCurrent(sessionToken)) {
              return;
            }
            RecordingAudioPlayer player = geminiPcmPlayer;
            if (player == null) {
              playMessageWithAndroidTts(text, gender, sessionToken, errorListener);
              return;
            }
            player.play(
                audio.getPcmData(),
                audio.getSampleRateHz(),
                new RecordingAudioPlayer.PlaybackCallback() {
                  @Override
                  public void onPlaybackCompleted() {
                    if (!isSessionCurrent(sessionToken)) {
                      return;
                    }
                    loggerDelegate.trace("TRACE_TTS_FLOW source=gemini_message_playback_completed");
                    onTtsTerminal(false);
                  }

                  @Override
                  public void onPlaybackError(String error) {
                    if (!isSessionCurrent(sessionToken)) {
                      return;
                    }
                    loggerDelegate.trace("TRACE_TTS_FLOW source=gemini_message_playback_error");
                    playMessageWithAndroidTts(text, gender, sessionToken, errorListener);
                  }
                });
          }

          @Override
          public void onError(@NonNull String errorMessage) {
            if (!isSessionCurrent(sessionToken)) {
              return;
            }
            loggerDelegate.trace("TRACE_TTS_FLOW source=gemini_message_error");
            playMessageWithAndroidTts(text, gender, sessionToken, errorListener);
          }
        });
  }

  private void playScriptWithGemini(
      @NonNull String text, @Nullable String gender, long sessionToken, @NonNull Runnable onComplete) {
    IGeminiTtsManager manager = geminiTtsManager;
    if (manager == null) {
      playScriptWithAndroidTts(text, gender, sessionToken, onComplete);
      return;
    }
    loggerDelegate.trace("TRACE_TTS_FLOW source=gemini_start");
    loggerDelegate.gate("M1_TTS_START");
    loggerDelegate.ux("UX_TTS_START", "source=script");

    String voiceName = resolveGeminiVoiceName(gender);
    manager.synthesize(
        text,
        ttsLocale.toLanguageTag(),
        ttsSpeechRate,
        voiceName,
        new IGeminiTtsManager.SynthesisCallback() {
          @Override
          public void onSuccess(@NonNull GeminiTtsAudio audio) {
            if (!isSessionCurrent(sessionToken)) {
              return;
            }
            RecordingAudioPlayer player = geminiPcmPlayer;
            if (player == null) {
              playScriptWithAndroidTts(text, gender, sessionToken, onComplete);
              return;
            }
            player.play(
                audio.getPcmData(),
                audio.getSampleRateHz(),
                new RecordingAudioPlayer.PlaybackCallback() {
                  @Override
                  public void onPlaybackCompleted() {
                    if (!isSessionCurrent(sessionToken)) {
                      return;
                    }
                    loggerDelegate.trace("TRACE_TTS_FLOW source=gemini_script_playback_completed");
                    onTtsTerminal(false);
                  }

                  @Override
                  public void onPlaybackError(String error) {
                    if (!isSessionCurrent(sessionToken)) {
                      return;
                    }
                    loggerDelegate.trace("TRACE_TTS_FLOW source=gemini_script_playback_error");
                    playScriptWithAndroidTts(text, gender, sessionToken, onComplete);
                  }
                });
          }

          @Override
          public void onError(@NonNull String errorMessage) {
            if (!isSessionCurrent(sessionToken)) {
              return;
            }
            loggerDelegate.trace("TRACE_TTS_FLOW source=gemini_script_error");
            playScriptWithAndroidTts(text, gender, sessionToken, onComplete);
          }
        });
  }

  private void playMessageWithAndroidTts(
      @NonNull String text,
      @Nullable String gender,
      long sessionToken,
      @Nullable PlaybackErrorListener errorListener) {
    if (!isSessionCurrent(sessionToken)) {
      return;
    }
    if (!isTtsReady || tts == null) {
      loggerDelegate.ux("UX_TTS_DONE", "source=message_error");
      if (errorListener != null) {
        errorListener.onError("TTS unavailable");
      }
      return;
    }

    applyConfiguredTtsLanguage();
    if (shouldApplyGenderVoiceSelection()) {
      setTtsVoice(gender);
    }
    Bundle params = new Bundle();
    tts.setSpeechRate(ttsSpeechRate);
    currentUtteranceId = buildUtteranceId("tts_utterance", sessionToken);
    tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, currentUtteranceId);
  }

  private void playScriptWithAndroidTts(
      @NonNull String text, @Nullable String gender, long sessionToken, @NonNull Runnable onComplete) {
    if (!isSessionCurrent(sessionToken)) {
      return;
    }
    if (isTtsReady && tts != null) {
      loggerDelegate.trace("TRACE_TTS_FLOW source=tts_start");
      loggerDelegate.gate("M1_TTS_START");
      loggerDelegate.ux("UX_TTS_START", "source=script");
      applyConfiguredTtsLanguage();
      if (shouldApplyGenderVoiceSelection()) {
        setTtsVoice(gender);
      }
      Bundle params = new Bundle();
      tts.setSpeechRate(ttsSpeechRate);
      currentUtteranceId = buildUtteranceId("script_tts_utterance", sessionToken);
      tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, currentUtteranceId);
      return;
    }

    loggerDelegate.trace("TRACE_TTS_FLOW reason=tts_not_ready");
    loggerDelegate.trace("TRACE_TTS_FLOW source=tts_callback");
    loggerDelegate.gate("M1_TTS_DONE source=tts_callback");
    loggerDelegate.ux("UX_TTS_DONE", "source=script_error");
    if (ttsWatchdogRunnable != null) {
      mainHandler.removeCallbacks(ttsWatchdogRunnable);
      ttsWatchdogRunnable = null;
    }
    currentTtsOnDoneCallback = null;
    currentTtsSource = null;
    currentUtteranceId = null;
    onComplete.run();
  }

  private void scheduleScriptWatchdog(
      long sessionToken, @NonNull Runnable onComplete, long timeoutMillis) {
    if (ttsWatchdogRunnable != null) {
      mainHandler.removeCallbacks(ttsWatchdogRunnable);
    }
    ttsWatchdogRunnable =
        () -> {
          if (!isSessionCurrent(sessionToken)) {
            return;
          }
          Log.w("DialoguePlaybackCoordinator", "TTS Watchdog triggered - forcing next step");
          loggerDelegate.trace("TRACE_TTS_FLOW source=watchdog");
          loggerDelegate.gate("M1_TTS_DONE source=watchdog");
          loggerDelegate.ux("UX_TTS_DONE", "source=watchdog");
          currentTtsOnDoneCallback = null;
          currentTtsSource = null;
          currentUtteranceId = null;
          if (tts != null && tts.isSpeaking()) {
            tts.stop();
          }
          if (geminiTtsManager != null) {
            geminiTtsManager.cancelActiveRequest();
          }
          if (geminiPcmPlayer != null) {
            geminiPcmPlayer.stop();
          }
          resetSpeakerButton();
          onComplete.run();
        };
    mainHandler.postDelayed(ttsWatchdogRunnable, timeoutMillis);
  }

  private void onAndroidUtteranceTerminal(@Nullable String utteranceId, boolean isError) {
    if (utteranceId == null) {
      return;
    }
    mainHandler.post(
        () -> {
          if (currentUtteranceId == null || !currentUtteranceId.equals(utteranceId)) {
            return;
          }
          onTtsTerminal(isError);
        });
  }

  private void onTtsTerminal(boolean isError) {
    resetSpeakerButton();
    if (ttsWatchdogRunnable != null) {
      mainHandler.removeCallbacks(ttsWatchdogRunnable);
      ttsWatchdogRunnable = null;
    }
    String source = currentTtsSource;
    Runnable callback = currentTtsOnDoneCallback;
    currentTtsSource = null;
    currentTtsOnDoneCallback = null;
    currentUtteranceId = null;

    if (isError && source != null) {
      if ("script".equals(source)) {
        loggerDelegate.trace("TRACE_TTS_FLOW source=tts_callback");
        loggerDelegate.gate("M1_TTS_DONE source=tts_callback");
        loggerDelegate.ux("UX_TTS_DONE", "source=script_error");
        if (callback != null) {
          callback.run();
        }
      } else if ("message".equals(source)) {
        loggerDelegate.ux("UX_TTS_DONE", "source=message_error");
      }
      return;
    }

    if ("script".equals(source)) {
      loggerDelegate.trace("TRACE_TTS_FLOW source=tts_callback");
      loggerDelegate.gate("M1_TTS_DONE source=tts_callback");
      loggerDelegate.ux("UX_TTS_DONE", "source=script_callback");
    } else if ("message".equals(source)) {
      loggerDelegate.ux("UX_TTS_DONE", "source=message_callback");
    }

    if (callback != null) {
      callback.run();
    }
  }

  private void resetSpeakerButton() {
    if (currentlyPlayingSpeakerBtn != null) {
      currentlyPlayingSpeakerBtn.setImageResource(R.drawable.ic_speaker);
      currentlyPlayingSpeakerBtn.clearColorFilter();
      currentlyPlayingSpeakerBtn = null;
    }
  }

  public void markSpeakerButtonActive(@Nullable ImageView speakerBtn) {
    if (speakerBtn == null) {
      return;
    }
    currentlyPlayingSpeakerBtn = speakerBtn;
    speakerBtn.setImageResource(R.drawable.ic_speaker_active);
    speakerBtn.setColorFilter(0xFF2196F3);
  }

  private void setTtsVoice(@Nullable String gender) {
    if (!isTtsReady || tts == null) {
      return;
    }

    Set<android.speech.tts.Voice> voices = null;
    try {
      voices = tts.getVoices();
    } catch (Exception e) {
      Log.e("DialoguePlaybackCoordinator", "Error getting voices: " + e.getMessage());
    }

    if (voices == null || voices.isEmpty()) {
      return;
    }

    boolean isMaleRequested = "male".equalsIgnoreCase(gender);
    String targetLanguage = ttsLocale.getLanguage();
    String targetCountry = ttsLocale.getCountry();
    android.speech.tts.Voice selectedVoice = null;

    for (android.speech.tts.Voice voice : voices) {
      if (voice.getLocale().getLanguage().equalsIgnoreCase(targetLanguage)) {
        String name = voice.getName().toLowerCase();
        if (isMaleRequested) {
          if (name.contains("male") && !name.contains("female")) {
            selectedVoice = voice;
            break;
          }
        } else if (name.contains("female")) {
          selectedVoice = voice;
          break;
        }
      }
    }

    if (selectedVoice == null) {
      for (android.speech.tts.Voice voice : voices) {
        if (voice.getLocale().getLanguage().equalsIgnoreCase(targetLanguage)) {
          String name = voice.getName().toLowerCase();
          if (isMaleRequested) {
            if (name.contains("iol")
                || name.contains("-im-")
                || name.contains("guy")
                || name.contains("-m-")
                || name.endsWith("-m")
                || name.contains(".male")) {
              selectedVoice = voice;
              break;
            }
          } else if (name.contains("sfg")
              || name.contains("tpf")
              || name.contains("lady")
              || name.contains("-f-")
              || name.endsWith("-f")
              || name.contains(".female")) {
            selectedVoice = voice;
            break;
          }
        }
      }
    }

    if (selectedVoice == null) {
      for (android.speech.tts.Voice voice : voices) {
        if (voice.getLocale().getLanguage().equalsIgnoreCase(targetLanguage)) {
          String name = voice.getName().toLowerCase();
          if (isMaleRequested) {
            if (name.contains("female")
                || name.contains("sfg")
                || name.contains("tpf")
                || name.contains("lady")) {
              continue;
            }
            selectedVoice = voice;
            if (!targetCountry.isEmpty()
                && voice.getLocale().getCountry().equalsIgnoreCase(targetCountry)) {
              break;
            }
          } else {
            if (name.contains("male")
                || name.contains("iol")
                || name.contains("-im-")
                || name.contains("guy")) {
              continue;
            }
            selectedVoice = voice;
            if (!targetCountry.isEmpty()
                && voice.getLocale().getCountry().equalsIgnoreCase(targetCountry)) {
              break;
            }
          }
        }
      }
    }

    if (selectedVoice == null) {
      for (android.speech.tts.Voice voice : voices) {
        if (voice.getLocale().getLanguage().equalsIgnoreCase(targetLanguage)
            && (targetCountry.isEmpty()
                || voice.getLocale().getCountry().equalsIgnoreCase(targetCountry))) {
          selectedVoice = voice;
          break;
        }
      }
    }

    if (selectedVoice != null) {
      tts.setVoice(selectedVoice);
    }
  }

  private boolean shouldApplyGenderVoiceSelection() {
    return "en".equalsIgnoreCase(ttsLocale.getLanguage());
  }

  @NonNull
  private Locale resolveLocale(@Nullable String localeTag) {
    if (localeTag == null) {
      return DEFAULT_TTS_LOCALE;
    }
    String normalized = localeTag.trim().replace('_', '-');
    if (normalized.isEmpty()) {
      return DEFAULT_TTS_LOCALE;
    }
    Locale locale = Locale.forLanguageTag(normalized);
    if (locale.getLanguage() == null || locale.getLanguage().trim().isEmpty()) {
      return DEFAULT_TTS_LOCALE;
    }
    return locale;
  }

  private boolean applyConfiguredTtsLanguage() {
    if (tts == null) {
      return false;
    }
    int result = tts.setLanguage(ttsLocale);
    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
      ttsLocale = DEFAULT_TTS_LOCALE;
      int fallbackResult = tts.setLanguage(DEFAULT_TTS_LOCALE);
      return fallbackResult != TextToSpeech.LANG_MISSING_DATA
          && fallbackResult != TextToSpeech.LANG_NOT_SUPPORTED;
    }
    return true;
  }

  @NonNull
  private String buildUtteranceId(@NonNull String prefix, long sessionToken) {
    return prefix + "_" + sessionToken;
  }

  private boolean isSessionCurrent(long sessionToken) {
    return sessionToken == ttsSessionToken;
  }

  @NonNull
  private static String normalizeProvider(@Nullable String provider) {
    if (provider == null) {
      return AppSettings.DEFAULT_TTS_PROVIDER;
    }
    String normalized = provider.trim().toLowerCase(Locale.US);
    if (normalized.isEmpty()) {
      return AppSettings.DEFAULT_TTS_PROVIDER;
    }
    if (AppSettings.TTS_PROVIDER_GOOGLE.equals(normalized)) {
      return AppSettings.TTS_PROVIDER_GOOGLE;
    }
    return AppSettings.TTS_PROVIDER_ANDROID;
  }

  static boolean shouldUseGeminiProvider(@Nullable String provider) {
    return AppSettings.TTS_PROVIDER_GOOGLE.equals(normalizeProvider(provider));
  }

  static boolean shouldAttemptGeminiSynthesis(@Nullable String provider, boolean hasGeminiManager) {
    return shouldUseGeminiProvider(provider) && hasGeminiManager;
  }

  @NonNull
  static String resolveGeminiVoiceName(@Nullable String gender) {
    if ("male".equalsIgnoreCase(gender)) {
      return "Puck";
    }
    return "Kore";
  }

  private float clamp(float value, float min, float max) {
    if (value < min) {
      return min;
    }
    return Math.min(value, max);
  }
}
