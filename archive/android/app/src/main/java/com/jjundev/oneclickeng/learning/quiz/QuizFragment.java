package com.jjundev.oneclickeng.learning.quiz;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import java.util.List;

public class QuizFragment extends Fragment {
  private static final String TAG = "JOB_J-20260217-002";
  private static final long SHOW_CHOICES_DELAY_MS = 500L;
  private static final long AUTO_CHECK_DELAY_MS = 200L;
  private static final long SHOW_NEXT_BUTTON_DELAY_MS = 500L;
  private static final float BOTTOM_SHEET_MAX_HEIGHT_RATIO = 0.8f;
  private static final float BOTTOM_SHEET_MIN_HEIGHT_RATIO = 0.2f;

  private enum BottomSheetUiStage {
    HIDDEN,
    CHOICES_ONLY,
    NEXT_BUTTON_ONLY
  }

  @Nullable private DialogueQuizViewModel viewModel;
  @Nullable private View loadingView;
  @Nullable private View errorView;
  @Nullable private View contentView;
  @Nullable private View bottomSheetView;
  @Nullable private BottomSheetBehavior<View> bottomSheetBehavior;
  @Nullable private TextView tvErrorMessage;
  @Nullable private MaterialButton btnRetry;
  @Nullable private TextView tvQuestion;
  @Nullable private MaterialCardView cardQuestionMaterial;
  @Nullable private TextView tvQuestionMaterial;
  @Nullable private LinearLayout choiceContainer;
  @Nullable private TextInputLayout inputAnswerLayout;
  @Nullable private MaterialCardView resultCard;
  @Nullable private TextView tvResultTitle;
  @Nullable private TextView tvCorrectAnswer;
  @Nullable private TextView tvExplanation;
  @Nullable private MaterialButton btnPrimary;
  @Nullable private TextView tvNextQuestionLoadingNotice;

  @NonNull private final Handler uiHandler = new Handler(Looper.getMainLooper());
  @Nullable private Runnable showChoicesRunnable;
  @Nullable private Runnable autoCheckRunnable;
  @Nullable private Runnable showNextButtonRunnable;

  @NonNull private BottomSheetUiStage bottomSheetUiStage = BottomSheetUiStage.HIDDEN;
  private int renderedBottomSheetQuestionIndex = -1;
  private int lastLoggedQuestionIndex = -1;
  private boolean autoCheckPending = false;
  private boolean showNextButtonScheduled = false;
  private boolean isProgrammaticHideInProgress = false;
  @Nullable private BottomSheetBehavior.BottomSheetCallback bottomSheetCallback;

  public QuizFragment() {
    super(R.layout.fragment_quiz);
  }

  @NonNull
  public static QuizFragment newInstance() {
    return new QuizFragment();
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    bindViews(view);
    bindListeners();
    viewModel = new ViewModelProvider(requireActivity()).get(DialogueQuizViewModel.class);
    viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderUiState);
  }

  @Override
  public void onDestroyView() {
    cancelPendingBottomSheetTasks();
    hideNextQuestionLoadingNotice();
    detachBottomSheetCallback();
    hideBottomSheet();
    bottomSheetUiStage = BottomSheetUiStage.HIDDEN;
    renderedBottomSheetQuestionIndex = -1;
    lastLoggedQuestionIndex = -1;
    autoCheckPending = false;
    showNextButtonScheduled = false;
    isProgrammaticHideInProgress = false;
    clearViewRefs();
    super.onDestroyView();
  }

  private void bindViews(@NonNull View root) {
    loadingView = root.findViewById(R.id.layout_quiz_loading);
    errorView = root.findViewById(R.id.layout_quiz_error);
    contentView = root.findViewById(R.id.layout_quiz_content);
    bottomSheetView = root.findViewById(R.id.bottom_sheet_quiz);
    tvErrorMessage = root.findViewById(R.id.tv_quiz_error_message);
    btnRetry = root.findViewById(R.id.btn_quiz_retry);
    tvQuestion = root.findViewById(R.id.tv_quiz_question);
    cardQuestionMaterial = root.findViewById(R.id.card_quiz_material);
    tvQuestionMaterial = root.findViewById(R.id.tv_quiz_material);
    choiceContainer = root.findViewById(R.id.layout_quiz_choice_container);
    inputAnswerLayout = root.findViewById(R.id.layout_quiz_answer_input);
    resultCard = root.findViewById(R.id.card_quiz_result);
    tvResultTitle = root.findViewById(R.id.tv_quiz_result_title);
    tvCorrectAnswer = root.findViewById(R.id.tv_quiz_correct_answer);
    tvExplanation = root.findViewById(R.id.tv_quiz_explanation);
    btnPrimary = root.findViewById(R.id.btn_quiz_primary);
    tvNextQuestionLoadingNotice = root.findViewById(R.id.tv_quiz_next_loading_notice);

    if (bottomSheetView != null) {
      bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetView);
      bottomSheetBehavior.setFitToContents(true);
      bottomSheetBehavior.setHideable(true);
      bottomSheetBehavior.setSkipCollapsed(false);
      bottomSheetBehavior.setDraggable(true);
      bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
      attachBottomSheetCallback();
      setupBottomSheetHeightBounds(root);
    }
  }

  private void bindListeners() {
    if (btnRetry != null) {
      btnRetry.setOnClickListener(
          v -> {
            if (viewModel != null) {
              viewModel.retryLoad();
            }
          });
    }

    if (btnPrimary != null) {
      btnPrimary.setOnClickListener(
          v -> {
            if (bottomSheetUiStage != BottomSheetUiStage.NEXT_BUTTON_ONLY || viewModel == null) {
              return;
            }
            DialogueQuizViewModel.PrimaryActionResult result = viewModel.onPrimaryAction();
            if (result == DialogueQuizViewModel.PrimaryActionResult.WAITING_NEXT_QUESTION) {
              logDebug("quiz next requested: waiting for streamed question");
              showNextQuestionLoadingNotice();
              return;
            }

            hideNextQuestionLoadingNotice();
            if (result == DialogueQuizViewModel.PrimaryActionResult.MOVED_TO_NEXT) {
              // Do not cancel pending tasks here.
              // The next question render may already have scheduled showChoicesRunnable.
              logDebug("quiz next requested: moved to next question");
              hideBottomSheet();
              bottomSheetUiStage = BottomSheetUiStage.HIDDEN;
              autoCheckPending = false;
              return;
            }
            if (result == DialogueQuizViewModel.PrimaryActionResult.COMPLETED) {
              logDebug("quiz next requested: completed");
              cancelPendingBottomSheetTasks();
              hideBottomSheet();
              bottomSheetUiStage = BottomSheetUiStage.HIDDEN;
              autoCheckPending = false;
            }
          });
    }
  }

  private void renderUiState(@NonNull DialogueQuizViewModel.QuizUiState state) {
    switch (state.getStatus()) {
      case LOADING:
        showOnly(loadingView);
        break;
      case ERROR:
        showOnly(errorView);
        renderError(state);
        break;
      case READY:
        showOnly(contentView);
        renderReady(state);
        break;
      case COMPLETED:
      default:
        break;
    }
  }

  private void renderError(@NonNull DialogueQuizViewModel.QuizUiState state) {
    if (tvErrorMessage == null) {
      return;
    }
    String message = state.getErrorMessage();
    if (message == null || message.trim().isEmpty()) {
      message = getString(R.string.quiz_error_default);
    }
    tvErrorMessage.setText(message);
  }

  private void renderReady(@NonNull DialogueQuizViewModel.QuizUiState state) {
    DialogueQuizViewModel.QuizQuestionState questionState = state.getQuestionState();
    if (questionState == null || !questionState.isMultipleChoice()) {
      showOnly(errorView);
      if (tvErrorMessage != null) {
        tvErrorMessage.setText(getString(R.string.quiz_error_default));
      }
      return;
    }

    if (tvQuestion != null) {
      tvQuestion.setText(questionState.getQuestionMain());
    }

    String questionMaterial = questionState.getQuestionMaterial();
    boolean hasQuestionMaterial = questionMaterial != null;
    if (cardQuestionMaterial != null) {
      cardQuestionMaterial.setVisibility(hasQuestionMaterial ? View.VISIBLE : View.GONE);
    }
    if (tvQuestionMaterial != null) {
      tvQuestionMaterial.setText(hasQuestionMaterial ? questionMaterial : "");
    }

    boolean questionChanged = renderedBottomSheetQuestionIndex != state.getCurrentQuestionIndex();
    if (questionChanged) {
      renderedBottomSheetQuestionIndex = state.getCurrentQuestionIndex();
      resetBottomSheetForNewQuestion();
    }

    renderResultCard(questionState);
    if (questionState.isChecked()) {
      autoCheckPending = false;
      if (bottomSheetUiStage != BottomSheetUiStage.NEXT_BUTTON_ONLY && !showNextButtonScheduled) {
        scheduleShowNextButton(
            state.getCurrentQuestionIndex(), state.isLastQuestion(), questionState.isCorrect());
      }
    } else {
      if (questionChanged) {
        scheduleShowChoices(state.getCurrentQuestionIndex(), questionState);
      } else if (bottomSheetUiStage == BottomSheetUiStage.CHOICES_ONLY) {
        showBottomSheetChoices(questionState);
      }
    }

    if (lastLoggedQuestionIndex != state.getCurrentQuestionIndex()) {
      lastLoggedQuestionIndex = state.getCurrentQuestionIndex();
      logDebug(
          "quiz question render: index="
              + state.getCurrentQuestionNumber()
              + "/"
              + state.getTotalQuestions());
    }
  }

  private void resetBottomSheetForNewQuestion() {
    cancelPendingBottomSheetTasks();
    autoCheckPending = false;
    showNextButtonScheduled = false;
    bottomSheetUiStage = BottomSheetUiStage.HIDDEN;
    if (choiceContainer != null) {
      choiceContainer.setVisibility(View.GONE);
    }
    if (inputAnswerLayout != null) {
      inputAnswerLayout.setVisibility(View.GONE);
    }
    if (btnPrimary != null) {
      btnPrimary.setVisibility(View.GONE);
    }
    hideNextQuestionLoadingNotice();
    hideBottomSheet();
  }

  private void renderChoiceButtons(@NonNull DialogueQuizViewModel.QuizQuestionState state) {
    LinearLayout container = choiceContainer;
    if (container == null) {
      return;
    }

    container.removeAllViews();
    LayoutInflater inflater = LayoutInflater.from(requireContext());
    List<String> choices = state.getChoices();
    if (choices == null) {
      return;
    }

    String selectedChoice = state.getSelectedChoice();
    for (String choice : choices) {
      View item = inflater.inflate(R.layout.item_quiz_choice, container, false);
      MaterialButton button = item.findViewById(R.id.btn_quiz_choice);
      button.setText(choice);
      applyChoiceButtonStyle(button, state, choice, selectedChoice);
      if (!state.isChecked()
          && !autoCheckPending
          && bottomSheetUiStage == BottomSheetUiStage.CHOICES_ONLY) {
        button.setOnClickListener(v -> onChoiceSelected(choice));
      }
      container.addView(item);
    }
  }

  private void onChoiceSelected(@NonNull String choice) {
    if (viewModel == null
        || autoCheckPending
        || bottomSheetUiStage != BottomSheetUiStage.CHOICES_ONLY) {
      return;
    }
    autoCheckPending = true;
    viewModel.onChoiceSelected(choice);
    scheduleAutoCheck(renderedBottomSheetQuestionIndex);
  }

  private void applyChoiceButtonStyle(
      @NonNull MaterialButton button,
      @NonNull DialogueQuizViewModel.QuizQuestionState state,
      @NonNull String choice,
      @Nullable String selectedChoice) {
    Context context = button.getContext();
    int defaultBg = ContextCompat.getColor(context, R.color.color_background_2);
    int defaultStroke = ContextCompat.getColor(context, R.color.grey_300);
    int defaultText = ContextCompat.getColor(context, R.color.color_primary_text);
    int selectedBg = ContextCompat.getColor(context, R.color.color_background_3);
    int selectedStroke = ContextCompat.getColor(context, R.color.grey_300);
    int selectedText = ContextCompat.getColor(context, R.color.color_primary_text);

    boolean isSelected = normalize(choice).equals(normalize(selectedChoice));
    boolean isChecked = state.isChecked();
    boolean isCorrectChoice = normalize(choice).equals(normalize(state.getAnswer()));
    boolean isWrongSelected = isChecked && isSelected && !isCorrectChoice;

    int bgColor = isSelected ? selectedBg : defaultBg;
    int strokeColor = isSelected ? selectedStroke : defaultStroke;
    int textColor = isSelected ? selectedText : defaultText;

    if (isChecked && isCorrectChoice) {
      bgColor = ContextCompat.getColor(context, R.color.expression_natural_after_bg);
      strokeColor = ContextCompat.getColor(context, R.color.expression_natural_accent);
      textColor = ContextCompat.getColor(context, R.color.expression_natural_accent);
    } else if (isWrongSelected) {
      bgColor = ContextCompat.getColor(context, R.color.expression_precise_after_bg);
      strokeColor = ContextCompat.getColor(context, R.color.expression_precise_accent);
      textColor = ContextCompat.getColor(context, R.color.expression_precise_accent);
    }

    boolean enabled =
        !isChecked && !autoCheckPending && bottomSheetUiStage == BottomSheetUiStage.CHOICES_ONLY;
    button.setEnabled(enabled);
    button.setStrokeWidth(dpToPx(1));
    button.setStrokeColor(ColorStateList.valueOf(strokeColor));
    button.setBackgroundTintList(ColorStateList.valueOf(bgColor));
    button.setTextColor(textColor);
  }

  private void renderResultCard(@NonNull DialogueQuizViewModel.QuizQuestionState state) {
    if (resultCard != null) {
      resultCard.setVisibility(state.isChecked() ? View.VISIBLE : View.GONE);
    }
    if (!state.isChecked()) {
      return;
    }

    if (tvResultTitle != null) {
      int color =
          ContextCompat.getColor(
              requireContext(),
              state.isCorrect()
                  ? R.color.expression_natural_accent
                  : R.color.expression_precise_accent);
      tvResultTitle.setText(
          state.isCorrect() ? R.string.quiz_result_correct : R.string.quiz_result_incorrect);
      tvResultTitle.setTextColor(color);
    }
    if (tvCorrectAnswer != null) {
      tvCorrectAnswer.setText(getString(R.string.quiz_correct_answer_format, state.getAnswer()));
    }
    if (tvExplanation != null) {
      String explanation = state.getExplanation();
      if (explanation == null || explanation.trim().isEmpty()) {
        tvExplanation.setVisibility(View.GONE);
      } else {
        tvExplanation.setVisibility(View.VISIBLE);
        tvExplanation.setText(getString(R.string.quiz_explanation_format, explanation));
      }
    }
  }

  private void scheduleShowChoices(
      int questionIndex, @NonNull DialogueQuizViewModel.QuizQuestionState questionState) {
    if (showChoicesRunnable != null) {
      uiHandler.removeCallbacks(showChoicesRunnable);
      showChoicesRunnable = null;
    }
    Runnable runnable =
        () -> {
          showChoicesRunnable = null;
          if (!isAdded() || getView() == null || renderedBottomSheetQuestionIndex != questionIndex) {
            return;
          }
          showBottomSheetChoices(questionState);
        };
    showChoicesRunnable = runnable;
    uiHandler.postDelayed(runnable, SHOW_CHOICES_DELAY_MS);
  }

  private void scheduleAutoCheck(int questionIndex) {
    if (autoCheckRunnable != null) {
      uiHandler.removeCallbacks(autoCheckRunnable);
      autoCheckRunnable = null;
    }
    Runnable runnable =
        () -> {
          autoCheckRunnable = null;
          if (!isAdded() || getView() == null || renderedBottomSheetQuestionIndex != questionIndex) {
            autoCheckPending = false;
            return;
          }
          hideBottomSheet();
          bottomSheetUiStage = BottomSheetUiStage.HIDDEN;
          if (choiceContainer != null) {
            choiceContainer.setVisibility(View.GONE);
          }
          if (viewModel != null) {
            viewModel.onPrimaryAction();
          } else {
            autoCheckPending = false;
          }
        };
    autoCheckRunnable = runnable;
    uiHandler.postDelayed(runnable, AUTO_CHECK_DELAY_MS);
  }

  private void scheduleShowNextButton(int questionIndex, boolean lastQuestion, boolean isCorrect) {
    if (showNextButtonRunnable != null) {
      uiHandler.removeCallbacks(showNextButtonRunnable);
      showNextButtonRunnable = null;
    }
    showNextButtonScheduled = true;
    Runnable runnable =
        () -> {
          showNextButtonRunnable = null;
          showNextButtonScheduled = false;
          if (!isAdded() || getView() == null || renderedBottomSheetQuestionIndex != questionIndex) {
            return;
          }
          showBottomSheetNextButton(lastQuestion, isCorrect);
        };
    showNextButtonRunnable = runnable;
    uiHandler.postDelayed(runnable, SHOW_NEXT_BUTTON_DELAY_MS);
  }

  private void showBottomSheetChoices(@NonNull DialogueQuizViewModel.QuizQuestionState questionState) {
    bottomSheetUiStage = BottomSheetUiStage.CHOICES_ONLY;
    if (choiceContainer != null) {
      choiceContainer.setVisibility(View.VISIBLE);
    }
    if (inputAnswerLayout != null) {
      inputAnswerLayout.setVisibility(View.GONE);
    }
    if (btnPrimary != null) {
      btnPrimary.setVisibility(View.GONE);
    }
    hideNextQuestionLoadingNotice();
    renderChoiceButtons(questionState);
    expandBottomSheet();
  }

  private void showBottomSheetNextButton(boolean lastQuestion, boolean isCorrect) {
    bottomSheetUiStage = BottomSheetUiStage.NEXT_BUTTON_ONLY;
    autoCheckPending = false;
    if (choiceContainer != null) {
      choiceContainer.setVisibility(View.GONE);
    }
    if (inputAnswerLayout != null) {
      inputAnswerLayout.setVisibility(View.GONE);
    }
    if (btnPrimary != null) {
      int buttonColor =
          ContextCompat.getColor(
              requireContext(),
              isCorrect ? R.color.expression_natural_accent : R.color.expression_precise_accent);
      btnPrimary.setBackgroundColor(buttonColor);
      btnPrimary.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_on_fixed_bg));
      btnPrimary.setVisibility(View.VISIBLE);
      btnPrimary.setEnabled(true);
      btnPrimary.setText(lastQuestion ? R.string.quiz_primary_finish : R.string.quiz_primary_next);
    }
    hideNextQuestionLoadingNotice();
    expandBottomSheet();
  }

  private void expandBottomSheet() {
    if (bottomSheetView == null) {
      return;
    }
    isProgrammaticHideInProgress = false;
    bottomSheetView.setVisibility(View.VISIBLE);
    if (bottomSheetBehavior != null) {
      bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }
  }

  private void hideBottomSheet() {
    if (bottomSheetBehavior != null) {
      isProgrammaticHideInProgress = true;
      bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
      return;
    }
    isProgrammaticHideInProgress = false;
    if (bottomSheetView != null) {
      bottomSheetView.setVisibility(View.GONE);
    }
  }

  private void setupBottomSheetHeightBounds(@NonNull View root) {
    ViewTreeObserver viewTreeObserver = root.getViewTreeObserver();
    viewTreeObserver.addOnGlobalLayoutListener(
        new ViewTreeObserver.OnGlobalLayoutListener() {
          @Override
          public void onGlobalLayout() {
            if (!root.getViewTreeObserver().isAlive()) {
              return;
            }
            root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            if (bottomSheetBehavior == null || bottomSheetView == null) {
              return;
            }

            int rootHeight = root.getHeight();
            if (rootHeight <= 0) {
              return;
            }

            int maxHeight = (int) (rootHeight * BOTTOM_SHEET_MAX_HEIGHT_RATIO);
            int minHeight = (int) (rootHeight * BOTTOM_SHEET_MIN_HEIGHT_RATIO);
            ViewGroup.LayoutParams params = bottomSheetView.getLayoutParams();
            if (params != null) {
              params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
              bottomSheetView.setLayoutParams(params);
            }
            bottomSheetBehavior.setPeekHeight(minHeight);
            bottomSheetBehavior.setMaxHeight(maxHeight);
            logDebug(
                "quiz bottom sheet bounds updated: maxHeight="
                    + maxHeight
                    + ", minHeight="
                    + minHeight);
          }
        });
  }

  private void attachBottomSheetCallback() {
    if (bottomSheetBehavior == null || bottomSheetCallback != null) {
      return;
    }
    bottomSheetCallback =
        new BottomSheetBehavior.BottomSheetCallback() {
          @Override
          public void onStateChanged(@NonNull View bottomSheet, int newState) {
            BottomSheetBehavior<View> behavior = bottomSheetBehavior;
            if (behavior == null) {
              return;
            }

            if (newState == BottomSheetBehavior.STATE_HIDDEN && isProgrammaticHideInProgress) {
              isProgrammaticHideInProgress = false;
              return;
            }

            if (newState == BottomSheetBehavior.STATE_COLLAPSED
                || newState == BottomSheetBehavior.STATE_HIDDEN) {
              isProgrammaticHideInProgress = false;
              behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
          }

          @Override
          public void onSlide(@NonNull View bottomSheet, float slideOffset) {
            // No-op: spring-like restoration is handled in onStateChanged.
          }
        };
    bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback);
  }

  private void detachBottomSheetCallback() {
    if (bottomSheetBehavior != null && bottomSheetCallback != null) {
      bottomSheetBehavior.removeBottomSheetCallback(bottomSheetCallback);
    }
    bottomSheetCallback = null;
  }

  private void cancelPendingBottomSheetTasks() {
    if (showChoicesRunnable != null) {
      uiHandler.removeCallbacks(showChoicesRunnable);
      showChoicesRunnable = null;
    }
    if (autoCheckRunnable != null) {
      uiHandler.removeCallbacks(autoCheckRunnable);
      autoCheckRunnable = null;
    }
    if (showNextButtonRunnable != null) {
      uiHandler.removeCallbacks(showNextButtonRunnable);
      showNextButtonRunnable = null;
    }
    showNextButtonScheduled = false;
  }

  private void showNextQuestionLoadingNotice() {
    if (tvNextQuestionLoadingNotice != null) {
      tvNextQuestionLoadingNotice.setVisibility(View.VISIBLE);
      tvNextQuestionLoadingNotice.setText(R.string.quiz_next_question_loading_notice);
    }
  }

  private void hideNextQuestionLoadingNotice() {
    if (tvNextQuestionLoadingNotice != null) {
      tvNextQuestionLoadingNotice.setVisibility(View.GONE);
    }
  }

  private void showOnly(@Nullable View target) {
    if (target != contentView) {
      cancelPendingBottomSheetTasks();
      bottomSheetUiStage = BottomSheetUiStage.HIDDEN;
      renderedBottomSheetQuestionIndex = -1;
      autoCheckPending = false;
      hideNextQuestionLoadingNotice();
      hideBottomSheet();
    }
    setVisible(loadingView, loadingView == target);
    setVisible(errorView, errorView == target);
    setVisible(contentView, contentView == target);
    setVisible(bottomSheetView, contentView == target);
  }

  private void setVisible(@Nullable View view, boolean visible) {
    if (view != null) {
      view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
  }

  private int dpToPx(int dp) {
    float density = getResources().getDisplayMetrics().density;
    return (int) (dp * density);
  }

  @NonNull
  private static String normalize(@Nullable String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private void clearViewRefs() {
    loadingView = null;
    errorView = null;
    contentView = null;
    bottomSheetView = null;
    bottomSheetBehavior = null;
    tvErrorMessage = null;
    btnRetry = null;
    tvQuestion = null;
    cardQuestionMaterial = null;
    tvQuestionMaterial = null;
    choiceContainer = null;
    inputAnswerLayout = null;
    resultCard = null;
    tvResultTitle = null;
    tvCorrectAnswer = null;
    tvExplanation = null;
    btnPrimary = null;
    tvNextQuestionLoadingNotice = null;
    bottomSheetCallback = null;
  }

  private void logDebug(@NonNull String message) {
    Log.d(TAG, message);
  }
}
