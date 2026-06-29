package com.jjundev.oneclickeng.learning.dialoguelearning.ui;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.FluencyFeedback;

public class BottomSheetSceneRenderer {

  public interface BottomSheetActionHandler {
    void onDefaultMicClicked(String sentenceToTranslate);

    void onDefaultSendClicked(String translatedSentence, String originalSentence);

    void onBeforeSpeakToRecord(String sentenceToTranslate);

    void onBeforeSpeakBack(String sentenceToTranslate);

    void onWhileSpeakBack(String sentenceToTranslate);

    void onFeedbackRetry(String sentenceToTranslate, boolean isSpeakingMode);

    void onFeedbackNext(String userMessage, byte[] recordedAudio, boolean isSpeakingMode);

    void onOpenSummary();

    void onPlayRecordedAudio(byte[] audio, ImageView speakerButton);

    void onNeedPermission(String sentenceToTranslate);
  }

  public interface SheetBinder {
    void bind(View content);
  }

  @NonNull private final BottomSheetActionHandler actionHandler;

  public BottomSheetSceneRenderer(@NonNull BottomSheetActionHandler actionHandler) {
    this.actionHandler = actionHandler;
  }

  public void renderDefaultContent(
      @NonNull View content, @NonNull String sentenceToTranslate, @Nullable SheetBinder binder) {
    TextView tvSentenceToTranslate = content.findViewById(R.id.tv_sentence_to_translate);
    EditText etUserInput = content.findViewById(R.id.et_user_input);
    ImageButton btnMic = content.findViewById(R.id.btn_mic);
    ImageButton btnSend = content.findViewById(R.id.btn_send);

    if (tvSentenceToTranslate != null) {
      tvSentenceToTranslate.setText(sentenceToTranslate);
    }

    if (etUserInput != null) {
      Object existingWatcherTag = etUserInput.getTag(R.id.et_user_input);
      if (existingWatcherTag instanceof TextWatcher) {
        etUserInput.removeTextChangedListener((TextWatcher) existingWatcherTag);
      }

      TextWatcher inputWatcher =
          new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
              // Not used
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
              updateSendButtonState(btnSend, s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
              // Not used
            }
          };

      etUserInput.setTag(R.id.et_user_input, inputWatcher);
      etUserInput.addTextChangedListener(inputWatcher);
      updateSendButtonState(
          btnSend, etUserInput.getText() == null ? "" : etUserInput.getText().toString());
    } else {
      updateSendButtonState(btnSend, "");
    }

    if (btnMic != null) {
      btnMic.setOnClickListener(v -> actionHandler.onDefaultMicClicked(sentenceToTranslate));
    }

    if (btnSend != null) {
      btnSend.setOnClickListener(
          v -> {
            String translatedSentence = etUserInput == null ? "" : etUserInput.getText().toString();
            actionHandler.onDefaultSendClicked(translatedSentence, sentenceToTranslate);
          });
    }

    if (binder != null) {
      binder.bind(content);
    }
  }

  public void renderBeforeSpeakingContent(
      @NonNull View content, @NonNull String sentenceToTranslate, @Nullable SheetBinder binder) {
    TextView tvSentenceToTranslate = content.findViewById(R.id.tv_sentence_to_translate);
    ImageButton btnMicCircle = content.findViewById(R.id.btn_mic_circle);
    TextView tvBackToChat = content.findViewById(R.id.tv_back_to_chat);

    if (tvSentenceToTranslate != null) {
      tvSentenceToTranslate.setText(sentenceToTranslate);
    }

    if (btnMicCircle != null) {
      btnMicCircle.setOnClickListener(
          v -> actionHandler.onBeforeSpeakToRecord(sentenceToTranslate));
    }

    if (tvBackToChat != null) {
      tvBackToChat.setOnClickListener(v -> actionHandler.onBeforeSpeakBack(sentenceToTranslate));
    }

    if (binder != null) {
      binder.bind(content);
    }
  }

  public void renderWhileSpeakingContent(
      @NonNull View content, @NonNull String sentenceToTranslate, @Nullable SheetBinder binder) {
    TextView tvOriginalSentence = content.findViewById(R.id.tv_original_sentence);
    TextView tvBack = content.findViewById(R.id.tv_back);

    if (tvOriginalSentence != null) {
      tvOriginalSentence.setText(sentenceToTranslate);
    }

    if (tvBack != null) {
      tvBack.setOnClickListener(v -> actionHandler.onWhileSpeakBack(sentenceToTranslate));
    }

    if (binder != null) {
      binder.bind(content);
    }
  }

  public void renderFeedbackContent(
      @NonNull View content,
      @NonNull String sentenceToTranslate,
      @NonNull String translatedSentence,
      @Nullable FluencyFeedback result,
      boolean isSpeakingMode,
      @Nullable byte[] recordedAudio,
      @Nullable SheetBinder binder) {
    TextView tvSentenceToTranslate = content.findViewById(R.id.tv_sentence_to_translate);
    TextView tvTranslatedSentenceLocal = content.findViewById(R.id.tv_translated_sentence);
    ImageView btnSpeakMyVoice = content.findViewById(R.id.btn_speak_my_voice);
    TextView tvSpeakingFeedbackMsg = content.findViewById(R.id.tv_speaking_feedback_message);
    TextView tvFluency = content.findViewById(R.id.tv_fluency_score);
    TextView tvConfidence = content.findViewById(R.id.tv_confidence_score);
    TextView tvHesitation = content.findViewById(R.id.tv_hesitation_count);
    TextView tvSpeakingScore = content.findViewById(R.id.tv_speaking_score);
    View layoutSpeakingFeedback = content.findViewById(R.id.layout_speaking_feedback);
    View layoutSentenceFeedback = content.findViewById(R.id.layout_sentence_feedback);
    View layoutBtns = content.findViewById(R.id.layout_btns);
    View layoutExtraAiQuestion = content.findViewById(R.id.layout_extra_ai_question);
    android.widget.Button btnRetry = content.findViewById(R.id.btn_retry);
    android.widget.Button btnNext = content.findViewById(R.id.btn_next);
    boolean hasRecordedAudio = recordedAudio != null && recordedAudio.length > 0;

    if (tvSentenceToTranslate != null) {
      tvSentenceToTranslate.setText(sentenceToTranslate);
    }

    if (tvTranslatedSentenceLocal != null) {
      tvTranslatedSentenceLocal.setText(translatedSentence);
    }

    if (layoutBtns != null) {
      layoutBtns.setVisibility(View.GONE);
    }
    if (layoutExtraAiQuestion != null) {
      layoutExtraAiQuestion.setVisibility(View.GONE);
    }

    if (btnSpeakMyVoice != null) {
      if (isSpeakingMode) {
        btnSpeakMyVoice.setVisibility(View.VISIBLE);
        btnSpeakMyVoice.setEnabled(hasRecordedAudio);
        btnSpeakMyVoice.setAlpha(hasRecordedAudio ? 1.0f : 0.4f);
        btnSpeakMyVoice.setOnClickListener(
            v -> {
              if (!hasRecordedAudio || btnSpeakMyVoice == null) {
                return;
              }
              actionHandler.onPlayRecordedAudio(recordedAudio, btnSpeakMyVoice);
            });
      } else {
        btnSpeakMyVoice.setVisibility(View.GONE);
      }
    }

    if (layoutSpeakingFeedback != null) {
      layoutSpeakingFeedback.setVisibility(
          isSpeakingMode && result != null ? View.VISIBLE : View.GONE);
    }

    if (layoutSentenceFeedback != null) {
      layoutSentenceFeedback.setVisibility(View.VISIBLE);
    }

    if (isSpeakingMode && result != null) {
      if (tvFluency != null) {
        tvFluency.setText(result.getFluency() + " / 10");
      }
      if (tvConfidence != null) {
        tvConfidence.setText(result.getConfidence() + " / 10");
      }
      if (tvHesitation != null) {
        tvHesitation.setText(result.getHesitations() + " 회");
      }
      if (tvSpeakingFeedbackMsg != null) {
        String feedbackMessage = result.getFeedbackMessage();
        tvSpeakingFeedbackMsg.setText(feedbackMessage == null ? "" : feedbackMessage);
        tvSpeakingFeedbackMsg.setVisibility(View.VISIBLE);
      }
      if (tvSpeakingScore != null) {
        int score = (result.getFluency() + result.getConfidence() - result.getHesitations()) * 5;
        tvSpeakingScore.setText(String.valueOf(score));
      }

      String displayedText = result.getTranscript();
      if (displayedText == null || displayedText.isEmpty()) {
        displayedText = translatedSentence;
      }
      if (!displayedText.equals(translatedSentence) && tvTranslatedSentenceLocal != null) {
        tvTranslatedSentenceLocal.setText(displayedText);
      }
      final String finalDisplayedText = displayedText;
      if (btnNext != null) {
        btnNext.setOnClickListener(
            v -> actionHandler.onFeedbackNext(finalDisplayedText, recordedAudio, isSpeakingMode));
      }
    } else {
      if (btnNext != null) {
        btnNext.setOnClickListener(
            v -> actionHandler.onFeedbackNext(translatedSentence, null, isSpeakingMode));
      }
    }

    if (btnRetry != null) {
      btnRetry.setOnClickListener(
          v -> actionHandler.onFeedbackRetry(sentenceToTranslate, isSpeakingMode));
    }

    if (binder != null) {
      binder.bind(content);
    }
  }

  public void renderLearningFinishedContent(@NonNull View content, @Nullable SheetBinder binder) {
    android.widget.Button summaryButton = content.findViewById(R.id.btn_go_to_summary);
    if (summaryButton != null) {
      summaryButton.setEnabled(true);
      summaryButton.setOnClickListener(v -> actionHandler.onOpenSummary());
    }

    if (binder != null) {
      binder.bind(content);
    }
  }

  private void updateSendButtonState(@Nullable ImageButton btnSend, @NonNull String text) {
    if (btnSend == null) {
      return;
    }

    String safeText = text == null ? "" : text;
    boolean enabled = !safeText.trim().isEmpty();
    btnSend.setEnabled(enabled);
    btnSend.setBackgroundResource(
        enabled ? R.drawable.send_button_background : R.drawable.send_button_background_disabled);
  }
}
