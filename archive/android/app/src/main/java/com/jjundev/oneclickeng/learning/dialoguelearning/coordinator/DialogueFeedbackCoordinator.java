package com.jjundev.oneclickeng.learning.dialoguelearning.coordinator;

import android.animation.ObjectAnimator;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.button.MaterialButton;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.FluencyFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.ParaphrasingLevel;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SentenceFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.ExtraQuestionUiState;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.FeedbackUiState;
import com.jjundev.oneclickeng.learning.dialoguelearning.summary.BookmarkedParaphrase;
import com.jjundev.oneclickeng.others.SentenceFeedbackBinder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public final class DialogueFeedbackCoordinator {

  private static final long FEEDBACK_SCROLL_DELAY_MS = 300L;

  public interface LoggerDelegate {
    void trace(@NonNull String key);

    void gate(@NonNull String key);

    void ux(@NonNull String key, @Nullable String fields);
  }

  public interface FeedbackActionDelegate {
    void startSentenceFeedback(@NonNull String originalSentence, @NonNull String userTranscript);

    void askExtraQuestion(
        @NonNull String originalSentence, @NonNull String userSentence, @NonNull String question);

    void playTts(@NonNull String text, @Nullable ImageView speakerBtn);
  }

  public interface FeedbackUiDelegate {
    void bindFeedbackControls(@Nullable View content, boolean showNextButton);

    void hideKeyboard(@NonNull View tokenView);

    void showToast(@NonNull String message);
  }

  @NonNull private final LoggerDelegate loggerDelegate;
  @NonNull private final FeedbackActionDelegate feedbackActionDelegate;
  @NonNull private final FeedbackUiDelegate feedbackUiDelegate;

  @Nullable private View currentContent;
  @Nullable private View feedbackContentContainer;
  @Nullable private SentenceFeedbackBinder currentBinder;

  @NonNull private final List<SentenceFeedback> accumulatedFeedbacks = new ArrayList<>();

  @NonNull
  private final LinkedHashMap<String, BookmarkedParaphrase> bookmarkedParaphrases =
      new LinkedHashMap<>();

  private boolean hasScrolledToSentenceFeedback;
  private long lastHandledFeedbackEmissionId;
  private long lastHandledExtraEmissionId;
  private boolean isFeedbackButtonsResyncScheduled;

  @Nullable private Runnable pendingFeedbackScrollRunnable;
  @Nullable private View pendingFeedbackScrollHostView;
  @Nullable private FeedbackUiState latestFeedbackState;

  public DialogueFeedbackCoordinator(
      @NonNull LoggerDelegate loggerDelegate,
      @NonNull FeedbackActionDelegate feedbackActionDelegate,
      @NonNull FeedbackUiDelegate feedbackUiDelegate) {
    this.loggerDelegate = loggerDelegate;
    this.feedbackActionDelegate = feedbackActionDelegate;
    this.feedbackUiDelegate = feedbackUiDelegate;
  }

  public void bindFeedbackContent(@Nullable View content, @Nullable View contentContainer) {
    cancelPendingFeedbackScrollInternal();
    currentContent = content;
    feedbackContentContainer = contentContainer;
  }

  public void onFeedbackScenePresented(
      @Nullable String sentenceToTranslate,
      @Nullable String translatedSentence,
      @Nullable FluencyFeedback result,
      boolean isSpeakingMode,
      @Nullable byte[] recordedAudio,
      @Nullable FeedbackUiState currentFeedbackState) {
    View content = currentContent;
    if (content == null) {
      return;
    }
    hasScrolledToSentenceFeedback = false;

    String safeSentence = sentenceToTranslate == null ? "" : sentenceToTranslate;
    String safeTranslated = translatedSentence == null ? "" : translatedSentence;

    View layoutSpeakingFeedback = content.findViewById(R.id.layout_speaking_feedback);
    View layoutSentenceFeedback = content.findViewById(R.id.layout_sentence_feedback);
    TextView tvSpeakingFeedbackMsg = content.findViewById(R.id.tv_speaking_feedback_message);
    TextView tvFluency = content.findViewById(R.id.tv_fluency_score);
    TextView tvConfidence = content.findViewById(R.id.tv_confidence_score);
    TextView tvHesitation = content.findViewById(R.id.tv_hesitation_count);
    TextView tvTranslatedSentenceLocal = content.findViewById(R.id.tv_translated_sentence);

    currentBinder = createSentenceFeedbackBinder(content);
    setupExtraAiQuestionLogic(content);

    if (isSpeakingMode && result != null) {
      if (tvTranslatedSentenceLocal != null) {
        String displayText =
            !isBlank(result.getTranscript()) ? result.getTranscript() : safeTranslated;
        tvTranslatedSentenceLocal.setText(displayText);
      }
      if (layoutSpeakingFeedback != null) {
        layoutSpeakingFeedback.setVisibility(View.VISIBLE);
        scheduleSpeakingFeedbackScrollWithDelay(content, layoutSpeakingFeedback);
      }
      if (tvFluency != null) {
        tvFluency.setText(result.getFluency() + " / 10");
      }
      if (tvConfidence != null) {
        tvConfidence.setText(result.getConfidence() + " / 10");
      }
      if (tvHesitation != null) {
        tvHesitation.setText(result.getHesitations() + " 회");
      }

      String feedbackMessage = result.getFeedbackMessage();
      if (isBlank(feedbackMessage)) {
        int speakingScore =
            (result.getFluency() + result.getConfidence() - result.getHesitations()) * 5;
        feedbackMessage = generateSpeakingFeedbackMessage(speakingScore);
      }
      if (tvSpeakingFeedbackMsg != null) {
        tvSpeakingFeedbackMsg.setText(feedbackMessage);
        tvSpeakingFeedbackMsg.setVisibility(View.VISIBLE);
      }

      SentenceFeedback fullFeedback =
          currentFeedbackState == null ? null : currentFeedbackState.getFullFeedback();

      if (fullFeedback != null) {
        if (layoutSentenceFeedback != null) {
          layoutSentenceFeedback.setVisibility(View.VISIBLE);
        }
        if (currentBinder != null) {
          currentBinder.hideAllSkeletons();
          currentBinder.bind(fullFeedback);
        }
        saveFeedbackToAccumulatedList(fullFeedback);
      } else {
        if (layoutSentenceFeedback != null) {
          layoutSentenceFeedback.setVisibility(View.VISIBLE);
        }
        if (currentBinder != null) {
          currentBinder.hideAllSections();
          currentBinder.showAllSkeletons();
        }
      }

      try {
        int score = (result.getFluency() + result.getConfidence() - result.getHesitations()) * 5;
        TextView tvSpeakingScore = content.findViewById(R.id.tv_speaking_score);
        if (tvSpeakingScore != null) {
          tvSpeakingScore.setText(String.valueOf(score));
        }
      } catch (Exception ignored) {
      }
    } else {
      if (layoutSpeakingFeedback != null) {
        layoutSpeakingFeedback.setVisibility(View.GONE);
      }
      if (layoutSentenceFeedback != null) {
        layoutSentenceFeedback.setVisibility(View.VISIBLE);
      }
      if (tvSpeakingFeedbackMsg != null) {
        tvSpeakingFeedbackMsg.setVisibility(View.GONE);
      }
      if (currentBinder != null) {
        currentBinder.hideAllSections();
        currentBinder.showAllSkeletons();
      }
      if (shouldStartSentenceFeedback(currentFeedbackState)) {
        startSentenceFeedbackWithText(safeSentence, safeTranslated);
      }
    }
  }

  public void onFeedbackUiStateChanged(@Nullable FeedbackUiState state) {
    if (state == null || state.getEmissionId() <= lastHandledFeedbackEmissionId) {
      return;
    }
    lastHandledFeedbackEmissionId = state.getEmissionId();
    latestFeedbackState = state;

    if (state.getSectionKey() != null && state.getPartialFeedback() != null) {
      loggerDelegate.ux(
          "UX_FEEDBACK_SECTION",
          "section=" + state.getSectionKey() + " emissionId=" + state.getEmissionId());
      handleSentenceFeedbackSection(state.getSectionKey(), state.getPartialFeedback());
    }

    if (state.getFullFeedback() != null && state.isCompleted()) {
      loggerDelegate.gate("M2_FEEDBACK_FULL_RECEIVED emissionId=" + state.getEmissionId());
      loggerDelegate.ux(
          "UX_FEEDBACK_FULL_READY",
          "emissionId=" + state.getEmissionId() + " showNext=" + state.isShowNextButton());
      updateSentenceFeedbackUI(state.getFullFeedback());
    }

    if (!isBlank(state.getError())) {
      loggerDelegate.trace("TRACE_FEEDBACK_ERROR msgLen=" + state.getError().length());
      updateSentenceFeedbackUI(state.getFullFeedback());
    }

    ExtraQuestionUiState extraState = state.getExtraQuestionUiState();
    if (extraState != null && extraState.getEmissionId() > lastHandledExtraEmissionId) {
      lastHandledExtraEmissionId = extraState.getEmissionId();
      applyExtraQuestionUi(extraState);
    }
  }

  public void renderSentenceFeedbackControls(@Nullable FeedbackUiState feedbackState) {
    if (feedbackState == null) {
      return;
    }
    latestFeedbackState = feedbackState;
    View content = currentContent;
    if (content == null) {
      scheduleLayoutButtonsResync();
      return;
    }

    setLayoutBtnsVisibility(feedbackState.getFullFeedback(), content);
    feedbackUiDelegate.bindFeedbackControls(content, feedbackState.isShowNextButton());
  }

  public void restoreBookmarkedParaphrases(
      @Nullable LinkedHashMap<String, BookmarkedParaphrase> restored) {
    bookmarkedParaphrases.clear();
    if (restored != null) {
      bookmarkedParaphrases.putAll(restored);
    }
  }

  @NonNull
  public LinkedHashMap<String, BookmarkedParaphrase> copyBookmarkedParaphrasesForState() {
    return new LinkedHashMap<>(bookmarkedParaphrases);
  }

  @NonNull
  public List<SentenceFeedback> snapshotAccumulatedFeedbacks() {
    return new ArrayList<>(accumulatedFeedbacks);
  }

  @NonNull
  public List<BookmarkedParaphrase> snapshotBookmarkedParaphrases() {
    return new ArrayList<>(bookmarkedParaphrases.values());
  }

  public void release() {
    cancelPendingFeedbackScrollInternal();
    currentContent = null;
    feedbackContentContainer = null;
    currentBinder = null;
    latestFeedbackState = null;
    isFeedbackButtonsResyncScheduled = false;
  }

  private void setLayoutBtnsVisibility(@Nullable SentenceFeedback feedback, @NonNull View content) {
    View layoutBtns = content.findViewById(R.id.layout_btns);
    View layoutExtraAiQuestion = content.findViewById(R.id.layout_extra_ai_question);
    if (layoutBtns == null) {
      return;
    }

    if (feedback == null) {
      layoutBtns.setVisibility(View.GONE);
      if (layoutExtraAiQuestion != null) {
        layoutExtraAiQuestion.setVisibility(View.GONE);
      }
      return;
    }

    layoutBtns.setVisibility(View.VISIBLE);
    if (layoutExtraAiQuestion != null) {
      layoutExtraAiQuestion.setVisibility(View.VISIBLE);
    }
    loggerDelegate.gate("M2_LAYOUT_BTNS_VISIBLE");
  }

  private void scheduleLayoutButtonsResync() {
    if (feedbackContentContainer == null || isFeedbackButtonsResyncScheduled) {
      return;
    }

    isFeedbackButtonsResyncScheduled = true;
    feedbackContentContainer.post(
        () -> {
          isFeedbackButtonsResyncScheduled = false;
          if (latestFeedbackState == null || currentContent == null) {
            return;
          }
          setLayoutBtnsVisibility(latestFeedbackState.getFullFeedback(), currentContent);
          feedbackUiDelegate.bindFeedbackControls(
              currentContent, latestFeedbackState.isShowNextButton());
        });
  }

  private void handleSentenceFeedbackSection(
      @NonNull String sectionKey, @NonNull SentenceFeedback partialFeedback) {
    View content = currentContent;
    if (content != null) {
      View layoutSentenceFeedback = content.findViewById(R.id.layout_sentence_feedback);
      if (layoutSentenceFeedback != null
          && layoutSentenceFeedback.getVisibility() != View.VISIBLE) {
        layoutSentenceFeedback.setVisibility(View.VISIBLE);
      }

      if (!hasScrolledToSentenceFeedback
          && layoutSentenceFeedback != null
          && layoutSentenceFeedback.getVisibility() == View.VISIBLE) {
        hasScrolledToSentenceFeedback = true;
        View finalContent = content;
        View finalLayoutSentenceFeedback = layoutSentenceFeedback;
        content.post(
            () -> {
              if (finalContent instanceof NestedScrollView) {
                NestedScrollView scrollView = (NestedScrollView) finalContent;
                int targetY = finalLayoutSentenceFeedback.getTop();
                ObjectAnimator.ofInt(scrollView, "scrollY", targetY).setDuration(500).start();
              }
            });
      }
    }

    if (currentBinder != null) {
      currentBinder.bindSection(sectionKey, partialFeedback);
    }
  }

  private void applyExtraQuestionUi(@NonNull ExtraQuestionUiState state) {
    View content = currentContent;
    if (content == null) {
      return;
    }
    View cardResponse = content.findViewById(R.id.card_extra_ai_response);
    TextView tvResponse = content.findViewById(R.id.tv_extra_ai_response);
    TextView tvQuestion = content.findViewById(R.id.tv_extra_ai_question);
    ShimmerFrameLayout shimmerResponse = content.findViewById(R.id.skeleton_extra_ai_response);
    EditText etExtraInput = content.findViewById(R.id.et_user_input);
    ImageButton btnExtraSend = content.findViewById(R.id.btn_send);

    if (tvQuestion != null && state.getQuestion() != null) {
      tvQuestion.setText(state.getQuestion());
    }

    if (cardResponse != null) {
      cardResponse.setVisibility(View.VISIBLE);
    }

    if (shimmerResponse != null) {
      if (state.isLoading() && isBlank(state.getResponse())) {
        shimmerResponse.setVisibility(View.VISIBLE);
        shimmerResponse.startShimmer();
      } else {
        shimmerResponse.stopShimmer();
        shimmerResponse.setVisibility(View.GONE);
      }
    }

    if (tvResponse != null) {
      if (isBlank(state.getResponse())) {
        tvResponse.setVisibility(state.isLoading() ? View.GONE : View.VISIBLE);
        tvResponse.setText("");
      } else {
        tvResponse.setVisibility(View.VISIBLE);
        tvResponse.setText(state.getResponse());
      }
    }

    if (btnExtraSend != null) {
      btnExtraSend.setEnabled(!state.isLoading());
    }
    if (etExtraInput != null) {
      etExtraInput.setEnabled(!state.isLoading());
    }

    if (!isBlank(state.getError())) {
      loggerDelegate.ux("UX_FEEDBACK_EXTRA_ERROR", "msgLen=" + state.getError().length());
      feedbackUiDelegate.showToast("오류 발생: " + state.getError());
      return;
    }

    if (!state.isLoading()) {
      int responseLen = state.getResponse() == null ? 0 : state.getResponse().length();
      loggerDelegate.ux("UX_FEEDBACK_EXTRA_DONE", "responseLen=" + responseLen);
    }
  }

  private void updateSentenceFeedbackUI(@Nullable SentenceFeedback feedback) {
    View content = currentContent;
    if (content == null) {
      return;
    }

    View layoutSentenceFeedback = content.findViewById(R.id.layout_sentence_feedback);
    View layoutBtns = content.findViewById(R.id.layout_btns);
    View layoutExtraAiQuestion = content.findViewById(R.id.layout_extra_ai_question);
    if (layoutSentenceFeedback == null) {
      return;
    }

    if (feedback != null) {
      layoutSentenceFeedback.setVisibility(View.VISIBLE);
      if (layoutBtns != null) {
        layoutBtns.setVisibility(View.VISIBLE);
        if (layoutExtraAiQuestion != null) {
          layoutExtraAiQuestion.setVisibility(View.VISIBLE);
        }
      }

      currentBinder = createSentenceFeedbackBinder(content);
      setupExtraAiQuestionLogic(content);
      if (currentBinder != null) {
        currentBinder.hideAllSkeletons();
        currentBinder.bind(feedback);
      }

      MaterialButton btnNext = content.findViewById(R.id.btn_next);
      if (btnNext != null
          && feedback.getWritingScore() != null
          && feedback.getWritingScore().getScore() < 70) {
        btnNext.setVisibility(View.GONE);
      }
      loggerDelegate.gate("M2_LAYOUT_BTNS_VISIBLE");
      saveFeedbackToAccumulatedList(feedback);
    } else if (currentBinder != null) {
      currentBinder.hideAllSkeletons();
    }
  }

  @NonNull
  private SentenceFeedbackBinder createSentenceFeedbackBinder(@NonNull View content) {
    SentenceFeedbackBinder binder = new SentenceFeedbackBinder(content);
    binder.setTtsActionListener((text, btn) -> feedbackActionDelegate.playTts(text, btn));
    binder.setParaphrasingBookmarkDelegate(
        new SentenceFeedbackBinder.ParaphrasingBookmarkDelegate() {
          @Override
          public boolean isBookmarked(String sentence) {
            return isParaphrasingBookmarked(sentence);
          }

          @Override
          public void onToggleBookmark(ParaphrasingLevel level, boolean targetSaved) {
            updateParaphrasingBookmark(level, targetSaved);
          }
        });
    return binder;
  }

  private void startSentenceFeedbackWithText(
      @Nullable String originalSentence, @Nullable String userTranscript) {
    String safeOriginal = originalSentence == null ? "" : originalSentence;
    String safeTranscript = userTranscript == null ? "" : userTranscript;
    if (safeTranscript.isEmpty()
        || "(?녹음 내용이 없습니다)".equals(safeTranscript)
        || ("(?몄떇" + "???뚯꽦 ?놁쓬)").equals(safeTranscript)
        || "(녹음 내용이 없습니다)".equals(safeTranscript)) {
      updateSentenceFeedbackUI(null);
      return;
    }

    feedbackActionDelegate.startSentenceFeedback(safeOriginal, safeTranscript);
  }

  private boolean shouldStartSentenceFeedback(@Nullable FeedbackUiState currentFeedbackState) {
    if (currentFeedbackState == null) {
      return true;
    }
    if (currentFeedbackState.isLoading()) {
      return false;
    }
    if (currentFeedbackState.getSectionKey() != null
        || currentFeedbackState.getPartialFeedback() != null) {
      return false;
    }
    if (currentFeedbackState.getFullFeedback() != null) {
      return false;
    }
    if (currentFeedbackState.isCompleted()) {
      return false;
    }
    return isBlank(currentFeedbackState.getError());
  }

  private void saveFeedbackToAccumulatedList(@NonNull SentenceFeedback feedback) {
    if (!accumulatedFeedbacks.isEmpty()
        && accumulatedFeedbacks.get(accumulatedFeedbacks.size() - 1) == feedback) {
      return;
    }

    accumulatedFeedbacks.add(feedback);
  }

  private boolean isParaphrasingBookmarked(@Nullable String sentence) {
    String key = normalizeSentenceKey(sentence);
    return key != null && bookmarkedParaphrases.containsKey(key);
  }

  private void updateParaphrasingBookmark(@Nullable ParaphrasingLevel level, boolean targetSaved) {
    if (level == null) {
      return;
    }

    String sentence = level.getSentence();
    String translation = level.getSentenceTranslation();
    String key = normalizeSentenceKey(sentence);
    if (key == null) {
      return;
    }

    if (targetSaved) {
      if (isBlank(translation)) {
        return;
      }
      bookmarkedParaphrases.put(
          key,
          new BookmarkedParaphrase(
              level.getLevel(),
              level.getLabel(),
              sentence.trim(),
              translation.trim(),
              System.currentTimeMillis()));
    } else {
      bookmarkedParaphrases.remove(key);
    }

    loggerDelegate.ux(
        "UX_FEEDBACK_BOOKMARK_TOGGLE", "level=" + level.getLevel() + " saved=" + targetSaved);
  }

  @Nullable
  private String normalizeSentenceKey(@Nullable String sentence) {
    if (sentence == null) {
      return null;
    }
    String normalized = sentence.trim().toLowerCase(Locale.getDefault());
    return normalized.isEmpty() ? null : normalized;
  }

  private void setupExtraAiQuestionLogic(@NonNull View content) {
    EditText etExtraInput = content.findViewById(R.id.et_user_input);
    ImageButton btnExtraSend = content.findViewById(R.id.btn_send);
    TextView tvSentenceToTranslate = content.findViewById(R.id.tv_sentence_to_translate);
    TextView tvTranslatedSentenceLocal = content.findViewById(R.id.tv_translated_sentence);
    View layoutExtraAiQuestion = content.findViewById(R.id.layout_extra_ai_question);

    if (btnExtraSend == null || etExtraInput == null) {
      return;
    }

    btnExtraSend.setOnClickListener(
        v -> {
          String question = etExtraInput.getText().toString().trim();
          if (question.isEmpty()) {
            return;
          }

          loggerDelegate.ux("UX_FEEDBACK_EXTRA_ASK", "questionLen=" + question.length());

          if (layoutExtraAiQuestion != null) {
            content.post(
                () -> {
                  if (content instanceof NestedScrollView) {
                    NestedScrollView scrollView = (NestedScrollView) content;
                    int targetY = layoutExtraAiQuestion.getTop();
                    ObjectAnimator.ofInt(scrollView, "scrollY", targetY).setDuration(500).start();
                  }
                });
          }

          String originalSentence =
              tvSentenceToTranslate == null ? "" : tvSentenceToTranslate.getText().toString();
          String userSentence =
              tvTranslatedSentenceLocal == null
                  ? ""
                  : tvTranslatedSentenceLocal.getText().toString();

          feedbackUiDelegate.hideKeyboard(etExtraInput);
          etExtraInput.setText("");
          feedbackActionDelegate.askExtraQuestion(originalSentence, userSentence, question);
        });
  }

  private void scheduleSpeakingFeedbackScrollWithDelay(
      @NonNull View content, @NonNull View layoutSpeakingFeedback) {
    if (!(content instanceof NestedScrollView)) {
      return;
    }

    cancelPendingFeedbackScrollInternal();
    NestedScrollView scrollView = (NestedScrollView) content;
    Runnable scrollRunnable =
        new Runnable() {
          @Override
          public void run() {
            int targetY = layoutSpeakingFeedback.getTop();
            ObjectAnimator.ofInt(scrollView, "scrollY", targetY).setDuration(500).start();
            if (pendingFeedbackScrollRunnable == this) {
              pendingFeedbackScrollRunnable = null;
              pendingFeedbackScrollHostView = null;
            }
          }
        };

    pendingFeedbackScrollRunnable = scrollRunnable;
    pendingFeedbackScrollHostView = content;
    content.postDelayed(scrollRunnable, FEEDBACK_SCROLL_DELAY_MS);
  }

  private void cancelPendingFeedbackScrollInternal() {
    if (pendingFeedbackScrollHostView != null && pendingFeedbackScrollRunnable != null) {
      pendingFeedbackScrollHostView.removeCallbacks(pendingFeedbackScrollRunnable);
    }
    pendingFeedbackScrollRunnable = null;
    pendingFeedbackScrollHostView = null;
  }

  @NonNull
  private String generateSpeakingFeedbackMessage(int speakingScore) {
    if (speakingScore >= 80) {
      return "자신감이 느껴져서 아주 훌륭해요!";
    } else if (speakingScore >= 60) {
      return "좋아요! 조금만 더 연습하면 완벽해요!";
    } else if (speakingScore >= 40) {
      return "괜찮아요! 자신감을 가지고 다시 해볼까요?";
    }
    return "천천히 또박또박 말해보세요.";
  }

  private boolean isBlank(@Nullable String text) {
    return text == null || text.trim().isEmpty();
  }
}
