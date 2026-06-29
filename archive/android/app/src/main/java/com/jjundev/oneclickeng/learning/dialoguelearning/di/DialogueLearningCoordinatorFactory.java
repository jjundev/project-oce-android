package com.jjundev.oneclickeng.learning.dialoguelearning.di;

import android.os.Handler;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.learning.dialoguelearning.coordinator.DialogueFeedbackCoordinator;
import com.jjundev.oneclickeng.learning.dialoguelearning.coordinator.DialoguePlaybackCoordinator;
import com.jjundev.oneclickeng.learning.dialoguelearning.coordinator.DialogueSpeakingCoordinator;
import com.jjundev.oneclickeng.learning.dialoguelearning.coordinator.DialogueSummaryCoordinator;
import com.jjundev.oneclickeng.learning.dialoguelearning.coordinator.DialogueTurnCoordinator;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SentenceFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator.LearningSessionOrchestrator;
import com.jjundev.oneclickeng.learning.dialoguelearning.summary.BookmarkedParaphrase;
import java.util.List;

public class DialogueLearningCoordinatorFactory {

  public interface FactoryHost {
    void trace(@NonNull String key);

    void gate(@NonNull String key);

    void ux(@NonNull String key, @Nullable String fields);

    @NonNull
    List<SentenceFeedback> snapshotAccumulatedFeedbacks();

    @NonNull
    List<BookmarkedParaphrase> snapshotBookmarkedParaphrases();

    boolean isSummaryHostActive();

    void stopPlayback(@NonNull String reason);

    void navigateToSummary(@NonNull String summaryJson, @Nullable String featureBundleJson);

    @Nullable
    LearningSessionOrchestrator.TurnDecision moveToNextTurnDecision();

    void emitScrollChatToBottom();

    boolean isTurnHostActive();

    void clearFeedbackBinding();

    void requestDefaultInputSceneOnceAfterDelayIfFirstTime(@Nullable String sentenceToTranslate);

    void requestLearningFinishedSceneOnceAfterDelay();

    void requestScriptTtsPlayback(@NonNull String text, @NonNull Runnable onDone);

    void requestPermissionLauncher();

    boolean requestPermissionViaViewModel(@NonNull String sentenceToTranslate);

    void analyzeSpeaking(
        @NonNull String sentenceToTranslate,
        @NonNull byte[] audioData,
        @NonNull String recognizedText);

    void startSentenceFeedback(@NonNull String originalSentence, @NonNull String userTranscript);

    void askExtraQuestion(
        @NonNull String originalSentence, @NonNull String userSentence, @NonNull String question);

    void playTts(@NonNull String text, @Nullable ImageView speakerBtn);

    void bindFeedbackControls(@Nullable View content, boolean showNextButton);

    void hideKeyboard(@NonNull View tokenView);

    void showToast(@NonNull String message);
  }

  public static final class CoordinatorBundle {
    @NonNull public final DialogueSummaryCoordinator summaryCoordinator;
    @NonNull public final DialogueTurnCoordinator turnCoordinator;
    @NonNull public final DialoguePlaybackCoordinator playbackCoordinator;
    @NonNull public final DialogueSpeakingCoordinator speakingCoordinator;
    @NonNull public final DialogueFeedbackCoordinator feedbackCoordinator;

    public CoordinatorBundle(
        @NonNull DialogueSummaryCoordinator summaryCoordinator,
        @NonNull DialogueTurnCoordinator turnCoordinator,
        @NonNull DialoguePlaybackCoordinator playbackCoordinator,
        @NonNull DialogueSpeakingCoordinator speakingCoordinator,
        @NonNull DialogueFeedbackCoordinator feedbackCoordinator) {
      this.summaryCoordinator = summaryCoordinator;
      this.turnCoordinator = turnCoordinator;
      this.playbackCoordinator = playbackCoordinator;
      this.speakingCoordinator = speakingCoordinator;
      this.feedbackCoordinator = feedbackCoordinator;
    }
  }

  @NonNull
  public CoordinatorBundle create(@NonNull Handler mainHandler, @NonNull FactoryHost host) {
    DialogueSummaryCoordinator summaryCoordinator =
        new DialogueSummaryCoordinator(
            mainHandler,
            new DialogueSummaryCoordinator.LoggerDelegate() {
              @Override
              public void trace(@NonNull String key) {
                host.trace(key);
              }

              @Override
              public void gate(@NonNull String key) {
                host.gate(key);
              }

              @Override
              public void ux(@NonNull String key, @Nullable String fields) {
                host.ux(key, fields);
              }
            },
            new DialogueSummaryCoordinator.SummarySeedDelegate() {
              @NonNull
              @Override
              public List<SentenceFeedback> snapshotAccumulatedFeedbacks() {
                return host.snapshotAccumulatedFeedbacks();
              }

              @NonNull
              @Override
              public List<BookmarkedParaphrase> snapshotBookmarkedParaphrases() {
                return host.snapshotBookmarkedParaphrases();
              }
            },
            new DialogueSummaryCoordinator.SummaryNavigationDelegate() {
              @Override
              public boolean isHostActive() {
                return host.isSummaryHostActive();
              }

              @Override
              public void stopPlayback(@NonNull String reason) {
                host.stopPlayback(reason);
              }

              @Override
              public void navigateToSummary(
                  @NonNull String summaryJson, @Nullable String featureBundleJson) {
                host.navigateToSummary(summaryJson, featureBundleJson);
              }
            });

    DialogueTurnCoordinator turnCoordinator =
        new DialogueTurnCoordinator(
            new DialogueTurnCoordinator.LoggerDelegate() {
              @Override
              public void trace(@NonNull String key) {
                host.trace(key);
              }

              @Override
              public void gate(@NonNull String key) {
                host.gate(key);
              }

              @Override
              public void ux(@NonNull String key, @Nullable String fields) {
                host.ux(key, fields);
              }
            },
            new DialogueTurnCoordinator.TurnDataDelegate() {
              @Nullable
              @Override
              public LearningSessionOrchestrator.TurnDecision moveToNextTurnDecision() {
                return host.moveToNextTurnDecision();
              }

              @Override
              public void emitScrollChatToBottom() {
                host.emitScrollChatToBottom();
              }
            },
            new DialogueTurnCoordinator.TurnActionDelegate() {
              @Override
              public boolean isHostActive() {
                return host.isTurnHostActive();
              }

              @Override
              public void clearFeedbackBinding() {
                host.clearFeedbackBinding();
              }

              @Override
              public void requestDefaultInputSceneOnceAfterDelayIfFirstTime(
                  @Nullable String sentenceToTranslate) {
                host.requestDefaultInputSceneOnceAfterDelayIfFirstTime(sentenceToTranslate);
              }

              @Override
              public void requestLearningFinishedSceneOnceAfterDelay() {
                host.requestLearningFinishedSceneOnceAfterDelay();
              }

              @Override
              public void requestScriptTtsPlayback(@NonNull String text, @NonNull Runnable onDone) {
                host.requestScriptTtsPlayback(text, onDone);
              }
            });

    DialoguePlaybackCoordinator playbackCoordinator =
        new DialoguePlaybackCoordinator(
            mainHandler,
            new DialoguePlaybackCoordinator.LoggerDelegate() {
              @Override
              public void trace(@NonNull String key) {
                host.trace(key);
              }

              @Override
              public void gate(@NonNull String key) {
                host.gate(key);
              }

              @Override
              public void ux(@NonNull String key, @Nullable String fields) {
                host.ux(key, fields);
              }
            });

    DialogueSpeakingCoordinator speakingCoordinator =
        new DialogueSpeakingCoordinator(
            new DialogueSpeakingCoordinator.LoggerDelegate() {
              @Override
              public void trace(@NonNull String key) {
                host.trace(key);
              }

              @Override
              public void gate(@NonNull String key) {
                host.gate(key);
              }

              @Override
              public void ux(@NonNull String key, @Nullable String fields) {
                host.ux(key, fields);
              }
            },
            new DialogueSpeakingCoordinator.PermissionActionDelegate() {
              @Override
              public void requestPermissionLauncher() {
                host.requestPermissionLauncher();
              }

              @Override
              public boolean requestPermissionViaViewModel(@NonNull String sentenceToTranslate) {
                return host.requestPermissionViaViewModel(sentenceToTranslate);
              }
            },
            new DialogueSpeakingCoordinator.AnalysisDelegate() {
              @Override
              public void analyzeSpeaking(
                  @NonNull String sentenceToTranslate,
                  @NonNull byte[] audioData,
                  @NonNull String recognizedText) {
                host.analyzeSpeaking(sentenceToTranslate, audioData, recognizedText);
              }
            });

    DialogueFeedbackCoordinator feedbackCoordinator =
        new DialogueFeedbackCoordinator(
            new DialogueFeedbackCoordinator.LoggerDelegate() {
              @Override
              public void trace(@NonNull String key) {
                host.trace(key);
              }

              @Override
              public void gate(@NonNull String key) {
                host.gate(key);
              }

              @Override
              public void ux(@NonNull String key, @Nullable String fields) {
                host.ux(key, fields);
              }
            },
            new DialogueFeedbackCoordinator.FeedbackActionDelegate() {
              @Override
              public void startSentenceFeedback(
                  @NonNull String originalSentence, @NonNull String userTranscript) {
                host.startSentenceFeedback(originalSentence, userTranscript);
              }

              @Override
              public void askExtraQuestion(
                  @NonNull String originalSentence,
                  @NonNull String userSentence,
                  @NonNull String question) {
                host.askExtraQuestion(originalSentence, userSentence, question);
              }

              @Override
              public void playTts(@NonNull String text, @Nullable ImageView speakerBtn) {
                host.playTts(text, speakerBtn);
              }
            },
            new DialogueFeedbackCoordinator.FeedbackUiDelegate() {
              @Override
              public void bindFeedbackControls(@Nullable View content, boolean showNextButton) {
                host.bindFeedbackControls(content, showNextButton);
              }

              @Override
              public void hideKeyboard(@NonNull View tokenView) {
                host.hideKeyboard(tokenView);
              }

              @Override
              public void showToast(@NonNull String message) {
                host.showToast(message);
              }
            });

    return new CoordinatorBundle(
        summaryCoordinator,
        turnCoordinator,
        playbackCoordinator,
        speakingCoordinator,
        feedbackCoordinator);
  }
}
