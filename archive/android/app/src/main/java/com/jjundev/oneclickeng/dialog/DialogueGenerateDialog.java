package com.jjundev.oneclickeng.dialog;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.DialogFragment;
import com.jjundev.oneclickeng.R;

public class DialogueGenerateDialog extends DialogFragment {
  private static final long LOADING_MESSAGE_ROTATION_INTERVAL_MS = 2500L;
  private static final long LOADING_MESSAGE_FADE_DURATION_MS = 300L;
  private static final int CREDIT_THRESHOLD_LENGTH = 11;
  private static final int CREDIT_COST_DEFAULT = 1;
  private static final int CREDIT_COST_EXTENDED = 2;
  private static final String ARG_TOPIC = "arg_topic";

  public static DialogueGenerateDialog newInstance(String topic) {
    DialogueGenerateDialog fragment = new DialogueGenerateDialog();
    Bundle args = new Bundle();
    args.putString(ARG_TOPIC, topic);
    fragment.setArguments(args);
    return fragment;
  }

  private View layoutInputForm;
  private View layoutLoading;
  private EditText etTopic;
  private AutoCompleteTextView spinnerLevel;
  private TextView tvLevelDescription;
  private android.widget.SeekBar sbLength;
  private TextView tvLengthValue;
  private TextView tvLoadingMessage;
  private AppCompatButton btnConfirm;
  @Nullable private Runnable loadingMessageRunnable;
  @NonNull private String[] loadingMessages = new String[0];
  private int loadingMessageIndex = 0;
  @NonNull private final Handler uiHandler = new Handler(Looper.getMainLooper());
  private OnScriptParamsSelectedListener listener;

  public interface OnScriptParamsSelectedListener {
    void onScriptParamsSelected(
        String level,
        String topic,
        String format,
        int length,
        int requiredCredit,
        DialogueGenerateDialog dialog);
  }

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    if (getParentFragment() instanceof OnScriptParamsSelectedListener) {
      listener = (OnScriptParamsSelectedListener) getParentFragment();
    } else if (context instanceof OnScriptParamsSelectedListener) {
      listener = (OnScriptParamsSelectedListener) context;
    } else {
      throw new RuntimeException(
          context.toString() + " must implement OnScriptParamsSelectedListener");
    }
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.dialog_dialogue_generate, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    layoutInputForm = view.findViewById(R.id.layout_input_form);
    layoutLoading = view.findViewById(R.id.layout_loading);
    etTopic = view.findViewById(R.id.et_topic_input);

    if (getArguments() != null && getArguments().containsKey(ARG_TOPIC)) {
      etTopic.setText(getArguments().getString(ARG_TOPIC));
    }

    spinnerLevel = view.findViewById(R.id.spinner_level);
    tvLevelDescription = view.findViewById(R.id.tv_level_description);
    sbLength = view.findViewById(R.id.sb_length);
    tvLengthValue = view.findViewById(R.id.tv_length_value);
    tvLoadingMessage = view.findViewById(R.id.tv_dialogue_loading_message);
    loadingMessages = getResources().getStringArray(R.array.dialogue_generate_loading_messages);
    btnConfirm = view.findViewById(R.id.btn_confirm_generate);

    setupDropdowns();
    setupSeekBar();

    btnConfirm.setOnClickListener(v -> generateScript());
  }

  @Override
  public void onStart() {
    super.onStart();
    if (getDialog() != null && getDialog().getWindow() != null) {
      // Set transparent background to show rounded corners
      getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);

      // Set dialog width to 90% of screen width
      int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
      getDialog().getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
  }

  @Override
  public void onDestroyView() {
    stopLoadingMessageRotation(false);
    layoutInputForm = null;
    layoutLoading = null;
    etTopic = null;
    spinnerLevel = null;
    tvLevelDescription = null;
    sbLength = null;
    tvLengthValue = null;
    tvLoadingMessage = null;
    btnConfirm = null;
    loadingMessages = new String[0];
    super.onDestroyView();
  }

  private void setupDropdowns() {
    String[] levels = getResources().getStringArray(R.array.levels);
    ArrayAdapter<String> levelAdapter =
        new ArrayAdapter<>(requireContext(), R.layout.item_dropdown, levels);
    spinnerLevel.setAdapter(levelAdapter);

    spinnerLevel.setOnItemClickListener(
        (parent, view, position, id) -> {
          String selectedLevel = (String) parent.getItemAtPosition(position);
          updateLevelDescription(selectedLevel);
        });

    spinnerLevel.setOnClickListener(v -> hideKeyboard());
    spinnerLevel.setOnFocusChangeListener(
        (v, hasFocus) -> {
          if (hasFocus) {
            hideKeyboard();
          }
        });

    // Initialize description
    updateLevelDescription(spinnerLevel.getText().toString());
  }

  private void updateLevelDescription(String level) {
    String description;
    switch (level) {
      case "매우 쉬움":
        description = "기초적인 단어와 간단한 문장으로 대화할 수 있어요.";
        break;
      case "쉬움":
        description = "익숙한 주제에 대해 짧고 쉬운 표현을 사용할 수 있어요.";
        break;
      case "보통":
        description = "일상적인 대화를 자연스럽게 나눌 수 있어요.";
        break;
      case "어려움":
        description = "다양한 주제에 대해 복잡한 문장을 구사할 수 있어요.";
        break;
      case "매우 어려움":
        description = "전문적인 주제나 추상적인 개념도 유창하게 표현할 수 있어요.";
        break;
      default:
        description = "";
        break;
    }
    tvLevelDescription.setText(description);
  }

  private void setupSeekBar() {
    sbLength.setOnSeekBarChangeListener(
        new android.widget.SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onProgressChanged(
              android.widget.SeekBar seekBar, int progress, boolean fromUser) {
            int length = progress + 2;
            updateLengthUi(length);
          }

          @Override
          public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}

          @Override
          public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

    // Initialize text
    updateLengthUi(sbLength.getProgress() + 2);
  }

  private void updateLengthUi(int length) {
    tvLengthValue.setText(length + "줄");
    int requiredCredit = calculateRequiredCredit(length);
    int textResId =
        requiredCredit == CREDIT_COST_EXTENDED
            ? R.string.dialogue_generate_confirm_credit_2
            : R.string.dialogue_generate_confirm_credit_1;
    btnConfirm.setText(textResId);
  }

  private int calculateRequiredCredit(int length) {
    return length >= CREDIT_THRESHOLD_LENGTH ? CREDIT_COST_EXTENDED : CREDIT_COST_DEFAULT;
  }

  public void showLoading(boolean loading) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      uiHandler.post(() -> showLoading(loading));
      return;
    }

    View root = getView();
    if (layoutInputForm == null && root != null) {
      layoutInputForm = root.findViewById(R.id.layout_input_form);
    }
    if (layoutLoading == null && root != null) {
      layoutLoading = root.findViewById(R.id.layout_loading);
    }
    if (tvLoadingMessage == null && root != null) {
      tvLoadingMessage = root.findViewById(R.id.tv_dialogue_loading_message);
    }
    ensureLoadingMessages();

    if (loading) {
      if (layoutInputForm != null) {
        layoutInputForm.setVisibility(View.GONE);
      }
      if (layoutLoading != null) {
        layoutLoading.setVisibility(View.VISIBLE);
      }
      setCancelable(false);
      startLoadingMessageRotation();
    } else {
      if (layoutInputForm != null) {
        layoutInputForm.setVisibility(View.VISIBLE);
      }
      if (layoutLoading != null) {
        layoutLoading.setVisibility(View.GONE);
      }
      setCancelable(true);
      stopLoadingMessageRotation(true);
    }
  }

  private void ensureLoadingMessages() {
    if (loadingMessages.length == 0 && isAdded()) {
      loadingMessages = getResources().getStringArray(R.array.dialogue_generate_loading_messages);
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
      tvLoadingMessage.setText(R.string.dialogue_generate_loading);
    }

    if (loadingMessages.length <= 1) {
      return;
    }

    loadingMessageRunnable =
        new Runnable() {
          @Override
          public void run() {
            if (!isAdded()
                || tvLoadingMessage == null
                || layoutLoading == null
                || layoutLoading.getVisibility() != View.VISIBLE) {
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
      tvLoadingMessage.setText(R.string.dialogue_generate_loading);
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

  private void hideKeyboard() {
    View view = getView();
    if (view != null) {
      InputMethodManager imm =
          (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
      if (imm != null) {
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
      }
    }
  }

  private void generateScript() {
    String topic = etTopic.getText().toString().trim();
    if (topic.isEmpty()) {
      Toast.makeText(getContext(), "주제를 입력해주세요", Toast.LENGTH_SHORT).show();
      return;
    }

    hideKeyboard();

    String levelUi = spinnerLevel.getText().toString();
    String level = "intermediate";
    switch (levelUi) {
      case "매우 쉬움":
        level = "beginner";
        break;
      case "쉬움":
        level = "elementary";
        break;
      case "보통":
        level = "intermediate";
        break;
      case "어려움":
        level = "upper-intermediate";
        break;
      case "매우 어려움":
        level = "advanced";
        break;
    }

    String format = "dialogue";
    int length = sbLength.getProgress() + 2;
    int requiredCredit = calculateRequiredCredit(length);

    if (listener != null) {
      showLoading(true);
      listener.onScriptParamsSelected(level, topic, format, length, requiredCredit, this);
    }
  }
}
