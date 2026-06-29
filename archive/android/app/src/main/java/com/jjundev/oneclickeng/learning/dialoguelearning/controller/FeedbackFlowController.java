package com.jjundev.oneclickeng.learning.dialoguelearning.controller;

import androidx.annotation.NonNull;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISentenceFeedbackManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SentenceFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator.AsyncRequestTracker;

public class FeedbackFlowController {

  public interface Callback {
    void onSkipped();

    void onSectionReady(
        long requestId, @NonNull String sectionKey, @NonNull SentenceFeedback partialFeedback);

    void onComplete(long requestId, SentenceFeedback fullFeedback);

    void onError(long requestId, @NonNull String error);
  }

  public interface SentenceFeedbackService {
    void analyzeSentenceStreaming(
        String originalSentence,
        String userSentence,
        ISentenceFeedbackManager.StreamingFeedbackCallback callback);
  }

  public static class ManagerSentenceFeedbackService implements SentenceFeedbackService {
    private final ISentenceFeedbackManager manager;

    public ManagerSentenceFeedbackService(@NonNull ISentenceFeedbackManager manager) {
      this.manager = manager;
    }

    @Override
    public void analyzeSentenceStreaming(
        String originalSentence,
        String userSentence,
        ISentenceFeedbackManager.StreamingFeedbackCallback callback) {
      manager.analyzeSentenceStreaming(originalSentence, userSentence, callback);
    }
  }

  private final AsyncRequestTracker requestTracker;
  private final String requestChannel;
  private final SentenceFeedbackService feedbackService;

  public FeedbackFlowController(@NonNull SentenceFeedbackService feedbackService) {
    this(feedbackService, new AsyncRequestTracker(), AsyncRequestTracker.CHANNEL_FEEDBACK);
  }

  public FeedbackFlowController(
      @NonNull SentenceFeedbackService feedbackService,
      @NonNull AsyncRequestTracker requestTracker) {
    this(feedbackService, requestTracker, AsyncRequestTracker.CHANNEL_FEEDBACK);
  }

  public FeedbackFlowController(
      @NonNull SentenceFeedbackService feedbackService,
      @NonNull AsyncRequestTracker requestTracker,
      @NonNull String requestChannel) {
    this.feedbackService = feedbackService;
    this.requestTracker = requestTracker;
    this.requestChannel = requestChannel;
  }

  public long startSentenceFeedback(
      @NonNull String originalSentence,
      @NonNull String userTranscript,
      @NonNull Callback callback) {
    if (userTranscript.trim().isEmpty() || userTranscript.equals("(녹음 내용이 없습니다)")) {
      callback.onSkipped();
      return 0L;
    }

    long requestId = requestTracker.nextRequestId(requestChannel);

    feedbackService.analyzeSentenceStreaming(
        originalSentence,
        userTranscript,
        new ISentenceFeedbackManager.StreamingFeedbackCallback() {
          @Override
          public void onSectionReady(String sectionKey, SentenceFeedback partialFeedback) {
            if (!requestTracker.isLatest(requestChannel, requestId)) {
              return;
            }
            if (partialFeedback != null && partialFeedback.getUserSentence() == null) {
              partialFeedback.setUserSentence(userTranscript);
            }
            if (partialFeedback != null && partialFeedback.getOriginalSentence() == null) {
              partialFeedback.setOriginalSentence(originalSentence);
            }
            if (partialFeedback != null) {
              callback.onSectionReady(requestId, sectionKey, partialFeedback);
            }
          }

          @Override
          public void onComplete(SentenceFeedback fullFeedback) {
            if (!requestTracker.isLatest(requestChannel, requestId)) {
              return;
            }
            if (fullFeedback != null && fullFeedback.getUserSentence() == null) {
              fullFeedback.setUserSentence(userTranscript);
            }
            if (fullFeedback != null && fullFeedback.getOriginalSentence() == null) {
              fullFeedback.setOriginalSentence(originalSentence);
            }
            callback.onComplete(requestId, fullFeedback);
          }

          @Override
          public void onError(String error) {
            if (!requestTracker.isLatest(requestChannel, requestId)) {
              return;
            }
            callback.onError(requestId, error == null ? "Streaming analysis error" : error);
          }
        });

    return requestId;
  }

  public void invalidate() {
    requestTracker.invalidate(requestChannel);
  }
}
