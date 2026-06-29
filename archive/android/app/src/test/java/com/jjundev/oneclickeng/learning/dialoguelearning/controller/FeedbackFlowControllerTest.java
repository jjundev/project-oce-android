package com.jjundev.oneclickeng.learning.dialoguelearning.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISentenceFeedbackManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SentenceFeedback;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public class FeedbackFlowControllerTest {

  @Test
  public void startSentenceFeedback_setsOriginalSentenceOnPartialAndFullFeedback() {
    FeedbackFlowController controller =
        new FeedbackFlowController(new ImmediateSentenceFeedbackService());

    AtomicReference<SentenceFeedback> partialRef = new AtomicReference<>();
    AtomicReference<SentenceFeedback> fullRef = new AtomicReference<>();

    long requestId =
        controller.startSentenceFeedback(
            "원문 한국어 문장",
            "I goed home.",
            new FeedbackFlowController.Callback() {
              @Override
              public void onSkipped() {
                fail("onSkipped should not be called for non-empty transcript.");
              }

              @Override
              public void onSectionReady(
                  long requestId, String sectionKey, SentenceFeedback partialFeedback) {
                partialRef.set(partialFeedback);
              }

              @Override
              public void onComplete(long requestId, SentenceFeedback fullFeedback) {
                fullRef.set(fullFeedback);
              }

              @Override
              public void onError(long requestId, String error) {
                fail("onError should not be called. error=" + error);
              }
            });

    assertTrue(requestId > 0L);
    assertNotNull(partialRef.get());
    assertNotNull(fullRef.get());
    assertEquals("원문 한국어 문장", partialRef.get().getOriginalSentence());
    assertEquals("I goed home.", partialRef.get().getUserSentence());
    assertEquals("원문 한국어 문장", fullRef.get().getOriginalSentence());
    assertEquals("I goed home.", fullRef.get().getUserSentence());
  }

  private static final class ImmediateSentenceFeedbackService
      implements FeedbackFlowController.SentenceFeedbackService {
    @Override
    public void analyzeSentenceStreaming(
        String originalSentence,
        String userSentence,
        ISentenceFeedbackManager.StreamingFeedbackCallback callback) {
      callback.onSectionReady("grammar", new SentenceFeedback());
      callback.onComplete(new SentenceFeedback());
    }
  }
}
