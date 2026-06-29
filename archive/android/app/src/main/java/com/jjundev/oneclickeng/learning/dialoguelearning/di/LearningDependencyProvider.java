package com.jjundev.oneclickeng.learning.dialoguelearning.di;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IDialogueGenerateManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IExtraQuestionManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IGeminiTtsManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IQuizGenerationManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISentenceFeedbackManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISessionSummaryLlmManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISpeakingFeedbackManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.session.DialogueScriptStreamingSessionStore;
import com.jjundev.oneclickeng.learning.dialoguelearning.session.InMemoryDialogueScriptStreamingSessionStore;
import com.jjundev.oneclickeng.learning.dialoguelearning.summary.SessionSummaryManager;
import com.jjundev.oneclickeng.learning.quiz.session.InMemoryQuizStreamingSessionStore;
import com.jjundev.oneclickeng.learning.quiz.session.QuizStreamingSessionStore;
import com.jjundev.oneclickeng.manager_gemini.DialogueGenerateManager;
import com.jjundev.oneclickeng.manager_gemini.ExtraQuestionManager;
import com.jjundev.oneclickeng.manager_gemini.GeminiTtsManager;
import com.jjundev.oneclickeng.manager_gemini.QuizGenerateManager;
import com.jjundev.oneclickeng.manager_gemini.SentenceFeedbackManager;
import com.jjundev.oneclickeng.manager_gemini.SpeakingFeedbackManager;
import com.jjundev.oneclickeng.tool.AudioRecorder;

public final class LearningDependencyProvider {

  @Nullable private static volatile QuizStreamingSessionStore quizStreamingSessionStore;

  @Nullable
  private static volatile DialogueScriptStreamingSessionStore dialogueScriptStreamingSessionStore;

  private LearningDependencyProvider() {}

  @NonNull
  public static ISpeakingFeedbackManager provideSpeakingFeedbackManager(
      @NonNull Context appContext, @NonNull String apiKey, @NonNull String modelName) {
    return new SpeakingFeedbackManager(appContext, apiKey, modelName);
  }

  @NonNull
  public static ISentenceFeedbackManager provideSentenceFeedbackManager(
      @NonNull Context appContext, @NonNull String apiKey, @NonNull String modelName) {
    return new SentenceFeedbackManager(appContext, apiKey, modelName);
  }

  @NonNull
  public static LearningManagerInitializer provideLearningManagerInitializer(
      @NonNull ISpeakingFeedbackManager speakingFeedbackManager,
      @NonNull ISentenceFeedbackManager sentenceFeedbackManager) {
    return new LearningManagerInitializer(speakingFeedbackManager, sentenceFeedbackManager, true);
  }

  @NonNull
  public static IExtraQuestionManager provideExtraQuestionManager(
      @NonNull String apiKey, @NonNull String modelName) {
    return new ExtraQuestionManager(apiKey, modelName);
  }

  @NonNull
  public static IDialogueGenerateManager provideDialogueGenerateManager(
      @NonNull Context appContext, @NonNull String apiKey, @NonNull String modelName) {
    return new DialogueGenerateManager(appContext, apiKey, modelName);
  }

  @NonNull
  public static IGeminiTtsManager provideGeminiTtsManager(@NonNull String apiKey) {
    return new GeminiTtsManager(apiKey);
  }

  @NonNull
  public static ISessionSummaryLlmManager provideSessionSummaryLlmManager(
      @NonNull Context appContext, @NonNull String apiKey, @NonNull String modelName) {
    return new SessionSummaryManager(appContext, apiKey, modelName);
  }

  @NonNull
  public static IQuizGenerationManager provideQuizGenerationManager(
      @NonNull Context appContext, @NonNull String apiKey, @NonNull String modelName) {
    return new QuizGenerateManager(appContext, apiKey, modelName);
  }

  @NonNull
  public static QuizStreamingSessionStore provideQuizStreamingSessionStore() {
    QuizStreamingSessionStore existing = quizStreamingSessionStore;
    if (existing != null) {
      return existing;
    }
    synchronized (LearningDependencyProvider.class) {
      if (quizStreamingSessionStore == null) {
        quizStreamingSessionStore = new InMemoryQuizStreamingSessionStore();
      }
      return quizStreamingSessionStore;
    }
  }

  @NonNull
  public static DialogueScriptStreamingSessionStore provideDialogueScriptStreamingSessionStore() {
    DialogueScriptStreamingSessionStore existing = dialogueScriptStreamingSessionStore;
    if (existing != null) {
      return existing;
    }
    synchronized (LearningDependencyProvider.class) {
      if (dialogueScriptStreamingSessionStore == null) {
        dialogueScriptStreamingSessionStore = new InMemoryDialogueScriptStreamingSessionStore();
      }
      return dialogueScriptStreamingSessionStore;
    }
  }

  @NonNull
  public static AudioRecorder provideAudioRecorder() {
    return new AudioRecorder();
  }
}
