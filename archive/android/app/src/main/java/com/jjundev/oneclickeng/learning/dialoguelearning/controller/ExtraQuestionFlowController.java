package com.jjundev.oneclickeng.learning.dialoguelearning.controller;

import androidx.annotation.NonNull;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IExtraQuestionManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator.AsyncRequestTracker;

public class ExtraQuestionFlowController {

  public interface Callback {
    void onChunk(@NonNull String chunk);

    void onComplete();

    void onError(@NonNull String error);
  }

  public interface ExtraQuestionService {
    void askExtraQuestionStreaming(
        String originalSentence,
        String userSentence,
        String userQuestion,
        IExtraQuestionManager.StreamingResponseCallback callback);
  }

  public static class ManagerExtraQuestionService implements ExtraQuestionService {
    private final IExtraQuestionManager manager;

    public ManagerExtraQuestionService(@NonNull IExtraQuestionManager manager) {
      this.manager = manager;
    }

    @Override
    public void askExtraQuestionStreaming(
        String originalSentence,
        String userSentence,
        String userQuestion,
        IExtraQuestionManager.StreamingResponseCallback callback) {
      manager.askExtraQuestionStreaming(originalSentence, userSentence, userQuestion, callback);
    }
  }

  private final ExtraQuestionService service;
  private final AsyncRequestTracker requestTracker;
  private final String requestChannel;

  public ExtraQuestionFlowController(@NonNull ExtraQuestionService service) {
    this(service, new AsyncRequestTracker(), AsyncRequestTracker.CHANNEL_EXTRA);
  }

  public ExtraQuestionFlowController(
      @NonNull ExtraQuestionService service, @NonNull AsyncRequestTracker requestTracker) {
    this(service, requestTracker, AsyncRequestTracker.CHANNEL_EXTRA);
  }

  public ExtraQuestionFlowController(
      @NonNull ExtraQuestionService service,
      @NonNull AsyncRequestTracker requestTracker,
      @NonNull String requestChannel) {
    this.service = service;
    this.requestTracker = requestTracker;
    this.requestChannel = requestChannel;
  }

  public void ask(
      @NonNull String originalSentence,
      @NonNull String userSentence,
      @NonNull String userQuestion,
      @NonNull Callback callback) {
    long requestId = requestTracker.nextRequestId(requestChannel);

    service.askExtraQuestionStreaming(
        originalSentence,
        userSentence,
        userQuestion,
        new IExtraQuestionManager.StreamingResponseCallback() {
          @Override
          public void onTextChunk(String text) {
            if (!requestTracker.isLatest(requestChannel, requestId)) {
              return;
            }
            callback.onChunk(text == null ? "" : text);
          }

          @Override
          public void onComplete() {
            if (!requestTracker.isLatest(requestChannel, requestId)) {
              return;
            }
            callback.onComplete();
          }

          @Override
          public void onError(String error) {
            if (!requestTracker.isLatest(requestChannel, requestId)) {
              return;
            }
            callback.onError(error == null ? "Extra question failed" : error);
          }
        });
  }

  public void invalidate() {
    requestTracker.invalidate(requestChannel);
  }
}
