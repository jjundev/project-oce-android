package com.jjundev.oneclickeng.dialog;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.jjundev.oneclickeng.R;

public class QuizGenerateDialog extends DialogFragment {

  private static final long LOADING_MESSAGE_ROTATION_INTERVAL_MS = 2500L;
  private static final long LOADING_MESSAGE_FADE_DURATION_MS = 300L;
  private static final int QUIZ_CREDIT_THRESHOLD_COUNT = 6;
  private static final int QUIZ_CREDIT_DEFAULT = 1;
  private static final int QUIZ_CREDIT_EXTENDED = 2;

  public static final String REQUEST_KEY = "history_quiz_config_request";
  public static final String BUNDLE_KEY_PERIOD_BUCKET = "bundle_key_period_bucket";
  public static final String BUNDLE_KEY_QUESTION_COUNT = "bundle_key_question_count";
  public static final String BUNDLE_KEY_REQUIRED_CREDIT = "bundle_key_required_credit";

  public static final int PERIOD_1W = 0;
  public static final int PERIOD_2W = 1;
  public static final int PERIOD_3W = 2;
  public static final int PERIOD_OLDER = 3;

  @Nullable private View loadingContainer;
  @Nullable private View configContainer;
  @Nullable private Button btnStart;
  @Nullable private TextView tvLoadingMessage;
  @Nullable private Runnable loadingMessageRunnable;
  @NonNull private String[] loadingMessages = new String[0];
  private int loadingMessageIndex = 0;
  @NonNull private final Handler uiHandler = new Handler(Looper.getMainLooper());

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    if (getDialog() != null && getDialog().getWindow() != null) {
      getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
      getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
    }
    return inflater.inflate(R.layout.dialog_quiz_generate, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    configContainer = view.findViewById(R.id.layout_quiz_config_content);
    loadingContainer = view.findViewById(R.id.layout_quiz_loading);
    btnStart = (Button) view.findViewById(R.id.btn_quiz_start);
    RadioGroup rgPeriod = view.findViewById(R.id.rg_quiz_period);
    SeekBar sbQuestionCount = view.findViewById(R.id.sb_question_count);
    TextView tvQuizCountValue = view.findViewById(R.id.tv_quiz_count_value);
    tvLoadingMessage = view.findViewById(R.id.tv_quiz_loading_message);
    loadingMessages = getResources().getStringArray(R.array.history_quiz_config_loading_messages);
    updateQuestionCountUi(tvQuizCountValue, sbQuestionCount.getProgress() + 1);

    sbQuestionCount.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            int count = progress + 1;
            updateQuestionCountUi(tvQuizCountValue, count);
          }

          @Override
          public void onStartTrackingTouch(SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(SeekBar seekBar) {}
        });

    if (btnStart != null) {
      btnStart.setOnClickListener(
          v -> {
            if (isLoadingState()) {
              return;
            }

            int selectedPeriodId = rgPeriod.getCheckedRadioButtonId();
            int periodBucket = PERIOD_1W;

            if (selectedPeriodId == R.id.rb_period_2w) {
              periodBucket = PERIOD_2W;
            } else if (selectedPeriodId == R.id.rb_period_3w) {
              periodBucket = PERIOD_3W;
            } else if (selectedPeriodId == R.id.rb_period_older) {
              periodBucket = PERIOD_OLDER;
            }

            int questionCount = sbQuestionCount.getProgress() + 1;
            int requiredCredit = calculateRequiredCredit(questionCount);

            Bundle result = new Bundle();
            result.putInt(BUNDLE_KEY_PERIOD_BUCKET, periodBucket);
            result.putInt(BUNDLE_KEY_QUESTION_COUNT, questionCount);
            result.putInt(BUNDLE_KEY_REQUIRED_CREDIT, requiredCredit);

            setLoadingState(true);
            getParentFragmentManager().setFragmentResult(REQUEST_KEY, result);
          });
    }

    setLoadingState(false);
  }

  @Override
  public void onStart() {
    super.onStart();
    if (getDialog() != null && getDialog().getWindow() != null) {
      int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
      getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
      getDialog().setCancelable(!isLoadingState());
    }
  }

  @Override
  public void onDestroyView() {
    stopLoadingMessageRotation(false);
    configContainer = null;
    loadingContainer = null;
    btnStart = null;
    tvLoadingMessage = null;
    loadingMessages = new String[0];
    super.onDestroyView();
  }

  public void setLoadingState(boolean loading) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      uiHandler.post(() -> setLoadingState(loading));
      return;
    }

    View root = getView();
    if (configContainer == null && root != null) {
      configContainer = root.findViewById(R.id.layout_quiz_config_content);
    }
    if (loadingContainer == null && root != null) {
      loadingContainer = root.findViewById(R.id.layout_quiz_loading);
    }
    if (btnStart == null && root != null) {
      View view = root.findViewById(R.id.btn_quiz_start);
      btnStart = view instanceof Button ? (Button) view : null;
    }
    if (tvLoadingMessage == null && root != null) {
      View view = root.findViewById(R.id.tv_quiz_loading_message);
      tvLoadingMessage = view instanceof TextView ? (TextView) view : null;
    }
    ensureLoadingMessages();

    if (configContainer != null) {
      configContainer.setVisibility(loading ? View.GONE : View.VISIBLE);
    }
    if (loadingContainer != null) {
      loadingContainer.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    if (root != null) {
      setInputsEnabled(root, !loading);
    }
    setButtonsEnabled(!loading);

    if (getDialog() != null) {
      getDialog().setCancelable(!loading);
    }

    if (loading) {
      startLoadingMessageRotation();
    } else {
      stopLoadingMessageRotation(true);
    }
  }

  public boolean isLoadingState() {
    return loadingContainer != null && loadingContainer.getVisibility() == View.VISIBLE;
  }

  private void setButtonsEnabled(boolean enabled) {
    if (btnStart != null) {
      btnStart.setEnabled(enabled);
    }
  }

  private void setInputsEnabled(@NonNull View root, boolean enabled) {
    RadioGroup rgPeriod = root.findViewById(R.id.rg_quiz_period);
    SeekBar sbQuestionCount = root.findViewById(R.id.sb_question_count);
    if (rgPeriod != null) {
      rgPeriod.setEnabled(enabled);
      for (int i = 0; i < rgPeriod.getChildCount(); i++) {
        View child = rgPeriod.getChildAt(i);
        if (child != null) {
          child.setEnabled(enabled);
        }
      }
    }
    if (sbQuestionCount != null) {
      sbQuestionCount.setEnabled(enabled);
    }
  }

  private void updateQuestionCountUi(@NonNull TextView textView, int count) {
    textView.setText(getString(R.string.history_quiz_config_count_value_format, count));
    if (btnStart == null) {
      return;
    }
    int requiredCredit = calculateRequiredCredit(count);
    int textResId =
        requiredCredit == QUIZ_CREDIT_EXTENDED
            ? R.string.history_quiz_config_confirm_credit_2
            : R.string.history_quiz_config_confirm_credit_1;
    btnStart.setText(textResId);
  }

  private int calculateRequiredCredit(int questionCount) {
    return questionCount >= QUIZ_CREDIT_THRESHOLD_COUNT
        ? QUIZ_CREDIT_EXTENDED
        : QUIZ_CREDIT_DEFAULT;
  }

  private void ensureLoadingMessages() {
    if (loadingMessages.length == 0 && isAdded()) {
      loadingMessages = getResources().getStringArray(R.array.history_quiz_config_loading_messages);
    }
  }

  private void startLoadingMessageRotation() {
    ensureLoadingMessages();
    if (tvLoadingMessage == null) {
      return;
    }

    stopLoadingMessageRotation(false);
    loadingMessageIndex = 0;
    tvLoadingMessage.setAlpha(1f);
    if (loadingMessages.length > 0) {
      tvLoadingMessage.setText(loadingMessages[0]);
    } else {
      tvLoadingMessage.setText(R.string.history_quiz_config_loading);
    }

    if (loadingMessages.length <= 1) {
      return;
    }

    loadingMessageRunnable =
        new Runnable() {
          @Override
          public void run() {
            if (!isAdded() || tvLoadingMessage == null || !isLoadingState()) {
              return;
            }
            if (loadingMessageIndex >= loadingMessages.length - 1) {
              loadingMessageRunnable = null;
              return;
            }
            loadingMessageIndex = loadingMessageIndex + 1;
            changeMessageWithFade(loadingMessages[loadingMessageIndex]);
            if (loadingMessageIndex < loadingMessages.length - 1) {
              uiHandler.postDelayed(this, LOADING_MESSAGE_ROTATION_INTERVAL_MS);
            } else {
              loadingMessageRunnable = null;
            }
          }
        };
    uiHandler.postDelayed(loadingMessageRunnable, LOADING_MESSAGE_ROTATION_INTERVAL_MS);
  }

  private void stopLoadingMessageRotation(boolean resetMessage) {
    if (loadingMessageRunnable != null) {
      uiHandler.removeCallbacks(loadingMessageRunnable);
      loadingMessageRunnable = null;
    }

    if (tvLoadingMessage == null) {
      if (resetMessage) {
        loadingMessageIndex = 0;
      }
      return;
    }

    tvLoadingMessage.animate().cancel();
    tvLoadingMessage.setAlpha(1f);
    if (!resetMessage) {
      return;
    }

    loadingMessageIndex = 0;
    ensureLoadingMessages();
    if (loadingMessages.length > 0) {
      tvLoadingMessage.setText(loadingMessages[0]);
    } else {
      tvLoadingMessage.setText(R.string.history_quiz_config_loading);
    }
  }

  private void changeMessageWithFade(@NonNull String newMessage) {
    if (tvLoadingMessage == null) {
      return;
    }
    TextView messageView = tvLoadingMessage;
    messageView.animate().cancel();
    messageView
        .animate()
        .alpha(0f)
        .setDuration(LOADING_MESSAGE_FADE_DURATION_MS)
        .withEndAction(
            () -> {
              TextView currentView = tvLoadingMessage;
              if (currentView == null) {
                return;
              }
              currentView.setText(newMessage);
              currentView
                  .animate()
                  .alpha(1f)
                  .setDuration(LOADING_MESSAGE_FADE_DURATION_MS)
                  .start();
            })
        .start();
  }
}
