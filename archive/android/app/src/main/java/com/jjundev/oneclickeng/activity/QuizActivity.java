package com.jjundev.oneclickeng.activity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.learning.dialoguelearning.di.LearningDependencyProvider;
import com.jjundev.oneclickeng.learning.quiz.DialogueQuizViewModel;
import com.jjundev.oneclickeng.learning.quiz.DialogueQuizViewModelFactory;
import com.jjundev.oneclickeng.learning.quiz.QuizFragment;
import com.jjundev.oneclickeng.learning.quiz.QuizResultFragment;
import com.jjundev.oneclickeng.settings.AppSettings;
import com.jjundev.oneclickeng.settings.AppSettingsStore;

public class QuizActivity extends AppCompatActivity implements QuizResultFragment.Host {
  public static final String EXTRA_SUMMARY_JSON = "extra_summary_json";
  public static final String EXTRA_FEATURE_BUNDLE_JSON = "extra_feature_bundle_json";
  public static final String EXTRA_REQUESTED_QUESTION_COUNT = "extra_requested_question_count";
  public static final String EXTRA_STREAM_SESSION_ID = "extra_stream_session_id";

  private static final String TAG = "JOB_J-20260217-002";

  @Nullable private DialogueQuizViewModel viewModel;
  @Nullable private ImageButton btnLeft;
  @Nullable private TextView tvTitle;
  @Nullable private TextView tvProgress;
  @Nullable private ProgressBar progressBar;
  private int requestedQuestionCount = 5;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_quiz);

    getOnBackPressedDispatcher()
        .addCallback(
            this,
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                showExitConfirmDialog();
              }
            });

    View rootView = findViewById(android.R.id.content);
    ViewCompat.setOnApplyWindowInsetsListener(
        rootView,
        (v, windowInsets) -> {
          Insets imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
          Insets systemBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
          v.setPadding(
              systemBarInsets.left,
              systemBarInsets.top,
              systemBarInsets.right,
              Math.max(imeInsets.bottom, systemBarInsets.bottom));
          return WindowInsetsCompat.CONSUMED;
        });

    bindViews();
    bindListeners();

    Intent intent = getIntent();
    String summaryJson = intent.getStringExtra(EXTRA_SUMMARY_JSON);
    String streamSessionId = intent.getStringExtra(EXTRA_STREAM_SESSION_ID);
    requestedQuestionCount = Math.max(1, intent.getIntExtra(EXTRA_REQUESTED_QUESTION_COUNT, 5));

    configureToolbar();
    initViewModel(summaryJson, streamSessionId);
    logDebug("quiz activity entered");
  }

  @Override
  public void onQuizFinishRequested() {
    navigateToMainActivity();
  }

  private void bindViews() {
    btnLeft = findViewById(R.id.btn_left);
    tvTitle = findViewById(R.id.tv_title);
    tvProgress = findViewById(R.id.tv_progress);
    progressBar = findViewById(R.id.progress_bar);
  }

  private void bindListeners() {
    if (btnLeft != null) {
      btnLeft.setOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }
  }

  private void initViewModel(@Nullable String summaryJson, @Nullable String streamSessionId) {
    AppSettings settings = new AppSettingsStore(getApplicationContext()).getSettings();
    DialogueQuizViewModelFactory factory =
        new DialogueQuizViewModelFactory(
            LearningDependencyProvider.provideQuizGenerationManager(
                getApplicationContext(),
                settings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY),
                settings.getLlmModelSummary()),
            LearningDependencyProvider.provideQuizStreamingSessionStore());
    viewModel = new ViewModelProvider(this, factory).get(DialogueQuizViewModel.class);
    viewModel.getUiState().observe(this, this::renderUiState);
    viewModel.initialize(summaryJson, requestedQuestionCount, streamSessionId);
  }

  private void renderUiState(@NonNull DialogueQuizViewModel.QuizUiState state) {
    switch (state.getStatus()) {
      case LOADING:
        updateToolbarProgress(0, requestedQuestionCount);
        showFragmentIfNeeded(QuizFragment.class);
        break;
      case COMPLETED:
        updateToolbarProgress(state.getTotalQuestions(), state.getTotalQuestions());
        showFragmentIfNeeded(QuizResultFragment.class);
        break;
      case READY:
        updateToolbarProgress(state.getCurrentQuestionNumber(), state.getTotalQuestions());
        showFragmentIfNeeded(QuizFragment.class);
        break;
      case ERROR:
      default:
        showFragmentIfNeeded(QuizFragment.class);
        break;
    }
  }

  private void showFragmentIfNeeded(@NonNull Class<? extends Fragment> targetClass) {
    if (isFinishing() || isDestroyed()) {
      return;
    }

    Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
    if (current != null && targetClass.isAssignableFrom(current.getClass())) {
      return;
    }

    if (getSupportFragmentManager().isStateSaved()) {
      return;
    }

    Fragment target =
        targetClass == QuizResultFragment.class
            ? QuizResultFragment.newInstance()
            : QuizFragment.newInstance();
    getSupportFragmentManager()
        .beginTransaction()
        .setReorderingAllowed(true)
        .replace(R.id.fragment_container, target)
        .commit();
  }

  private void configureToolbar() {
    if (tvTitle != null) {
      tvTitle.setText(R.string.quiz_toolbar_title);
    }
    updateToolbarProgress(0, requestedQuestionCount);
  }

  private void updateToolbarProgress(int current, int total) {
    int safeTotal = Math.max(1, total);
    int safeCurrent = Math.max(0, Math.min(current, safeTotal));
    if (tvProgress != null) {
      tvProgress.setText(getString(R.string.quiz_progress_format, safeCurrent, safeTotal));
    }
    if (progressBar != null) {
      progressBar.setMax(safeTotal);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        progressBar.setProgress(safeCurrent, true);
      } else {
        progressBar.setProgress(safeCurrent);
      }
    }
  }

  private void showExitConfirmDialog() {
    android.app.Dialog dialog = new android.app.Dialog(this);
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
    dialog.setContentView(R.layout.dialog_exit_confirm);

    if (dialog.getWindow() != null) {
      dialog
          .getWindow()
          .setBackgroundDrawable(
              new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
      android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
      int dialogWidth = (int) (metrics.widthPixels * 0.9);
      dialog.getWindow().setLayout(dialogWidth, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    TextView tvHeader = dialog.findViewById(R.id.tv_header);
    TextView tvMessage = dialog.findViewById(R.id.tv_message);
    if (tvHeader != null) {
      tvHeader.setText("퀴즈 종료");
    }
    if (tvMessage != null) {
      tvMessage.setText("현재 진행 중인 퀴즈 내역이 저장되지 않아요.");
    }

    View btnCancel = dialog.findViewById(R.id.btn_cancel);
    View btnConfirm = dialog.findViewById(R.id.btn_confirm);

    if (btnCancel != null) {
      btnCancel.setOnClickListener(v -> dialog.dismiss());
    }

    if (btnConfirm != null) {
      btnConfirm.setOnClickListener(
          v -> {
            dialog.dismiss();
            navigateToMainActivity();
          });
    }

    dialog.show();
  }

  private void navigateToMainActivity() {
    Intent intent = new Intent(this, MainActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    startActivity(intent);
    finish();
  }

  private void logDebug(@NonNull String message) {
    Log.d(TAG, message);
  }
}
