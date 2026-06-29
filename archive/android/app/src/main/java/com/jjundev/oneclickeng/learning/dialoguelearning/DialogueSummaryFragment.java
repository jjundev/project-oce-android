package com.jjundev.oneclickeng.learning.dialoguelearning;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.card.MaterialCardView;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.activity.QuizActivity;
import com.jjundev.oneclickeng.learning.dialoguelearning.di.LearningDependencyProvider;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import com.jjundev.oneclickeng.learning.dialoguelearning.summary.DialogueSummaryViewModel;
import com.jjundev.oneclickeng.learning.dialoguelearning.summary.DialogueSummaryViewModelFactory;
import com.jjundev.oneclickeng.learning.dialoguelearning.summary.SessionSummaryBinder;
import com.jjundev.oneclickeng.settings.AppSettings;
import com.jjundev.oneclickeng.settings.AppSettingsStore;
import com.jjundev.oneclickeng.settings.InAppReviewPromptStore;

public class DialogueSummaryFragment extends Fragment {
  public static final String ARG_SUMMARY_JSON = "arg_summary_json";
  public static final String ARG_FEATURE_BUNDLE_JSON = "arg_feature_bundle_json";
  private static final String TAG = "JOB_J-20260217-003";
  private static final String STATE_EXPRESSION_REQUESTED_VISIBLE_COUNT =
      "state_expression_requested_visible_count";
  private static final String STATE_EXPRESSION_LAST_TOTAL_COUNT =
      "state_expression_last_total_count";

  private BottomSheetBehavior<MaterialCardView> bottomSheetBehavior;
  private NestedScrollView scrollView;
  private DialogueSummaryViewModel viewModel;
  private Boolean isFullyLoaded = null;
  private TextView tvBottomSheetTitle;
  private TextView tvBottomSheetSubtitle;
  private View btnStartQuiz;
  private View btnFinishSummary;
  private boolean isFinishingFlowInProgress = false;

  public static DialogueSummaryFragment newInstance(String summaryJson) {
    return newInstance(summaryJson, null);
  }

  public static DialogueSummaryFragment newInstance(
      String summaryJson, @Nullable String featureBundleJson) {
    DialogueSummaryFragment fragment = new DialogueSummaryFragment();
    Bundle args = new Bundle();
    args.putString(ARG_SUMMARY_JSON, summaryJson);
    args.putString(ARG_FEATURE_BUNDLE_JSON, featureBundleJson);
    fragment.setArguments(args);
    return fragment;
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_dialogue_summary, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    scrollView = view.findViewById(R.id.scroll_view_summary);
    MaterialCardView bottomSheetCard = view.findViewById(R.id.card_finish_bottom_sheet);
    bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetCard);
    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

    tvBottomSheetTitle = view.findViewById(R.id.tv_bottom_sheet_title);
    tvBottomSheetSubtitle = view.findViewById(R.id.tv_bottom_sheet_subtitle);
    btnStartQuiz = view.findViewById(R.id.btn_start_quiz_summary);
    btnFinishSummary = view.findViewById(R.id.btn_finish_summary);

    scrollView.setOnScrollChangeListener(
        (NestedScrollView.OnScrollChangeListener)
            (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
              View child = v.getChildAt(0);
              if (child == null) {
                return;
              }
              int scrollRange = child.getMeasuredHeight() - v.getMeasuredHeight();
              boolean reachedBottom = scrollY >= (scrollRange - 20);

              if (reachedBottom && scrollY > oldScrollY) {
                if (bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_EXPANDED) {
                  bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                }
              } else if (scrollY < scrollRange - 100) {
                if (bottomSheetBehavior.getState() != BottomSheetBehavior.STATE_HIDDEN) {
                  bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                }
              }
            });

    restoreExpressionToggleState(view, savedInstanceState);
    initViewModel(view, savedInstanceState);
    logDebug("summary fragment entered");
    if (btnFinishSummary != null) {
      btnFinishSummary.setOnClickListener(v -> handleFinishSummaryClick());
    }
    if (btnStartQuiz != null) {
      btnStartQuiz.setOnClickListener(v -> navigateToQuiz());
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    if (viewModel != null) {
      viewModel.saveState(outState);
    }
    saveExpressionToggleState(outState);
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    if (viewModel != null) {
      viewModel.onViewDestroyed();
    }
  }

  private void restoreExpressionToggleState(
      @NonNull View rootView, @Nullable Bundle savedInstanceState) {
    if (savedInstanceState == null) {
      return;
    }
    View toggleBtn = rootView.findViewById(R.id.btn_expression_toggle);
    if (toggleBtn == null) {
      return;
    }
    if (savedInstanceState.containsKey(STATE_EXPRESSION_REQUESTED_VISIBLE_COUNT)) {
      int requestedVisibleCount =
          savedInstanceState.getInt(STATE_EXPRESSION_REQUESTED_VISIBLE_COUNT);
      toggleBtn.setTag(R.id.tag_expression_requested_visible_count, requestedVisibleCount);
    }
    if (savedInstanceState.containsKey(STATE_EXPRESSION_LAST_TOTAL_COUNT)) {
      int lastTotalCount = savedInstanceState.getInt(STATE_EXPRESSION_LAST_TOTAL_COUNT);
      toggleBtn.setTag(R.id.tag_expression_last_total_count, lastTotalCount);
    }
  }

  private void saveExpressionToggleState(@NonNull Bundle outState) {
    View rootView = getView();
    if (rootView == null) {
      return;
    }
    View toggleBtn = rootView.findViewById(R.id.btn_expression_toggle);
    if (toggleBtn == null) {
      return;
    }
    Object requestedVisibleCount = toggleBtn.getTag(R.id.tag_expression_requested_visible_count);
    if (requestedVisibleCount instanceof Integer) {
      outState.putInt(STATE_EXPRESSION_REQUESTED_VISIBLE_COUNT, (Integer) requestedVisibleCount);
    }
    Object lastTotalCount = toggleBtn.getTag(R.id.tag_expression_last_total_count);
    if (lastTotalCount instanceof Integer) {
      outState.putInt(STATE_EXPRESSION_LAST_TOTAL_COUNT, (Integer) lastTotalCount);
    }
  }

  private void initViewModel(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
    AppSettings settings =
        new AppSettingsStore(requireContext().getApplicationContext()).getSettings();
    DialogueSummaryViewModelFactory factory =
        new DialogueSummaryViewModelFactory(
            LearningDependencyProvider.provideSessionSummaryLlmManager(
                requireContext().getApplicationContext(),
                settings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY),
                settings.getLlmModelSummary()));
    viewModel = new ViewModelProvider(this, factory).get(DialogueSummaryViewModel.class);

    viewModel
        .getSummaryData()
        .observe(
            getViewLifecycleOwner(),
            data -> {
              if (data != null) {
                SessionSummaryBinder.bind(rootView, data);
                DialogueSummaryViewModel.WordLoadState wordState =
                    viewModel.getWordLoadState().getValue();
                if (wordState != null
                    && wordState.getStatus() == DialogueSummaryViewModel.WordLoadStatus.READY) {
                  SessionSummaryBinder.bindWordsContent(rootView, data.getWords());
                }
              }
            });

    viewModel
        .getExpressionLoadState()
        .observe(
            getViewLifecycleOwner(),
            state -> {
              if (state == null) {
                return;
              }
              applyExpressionLoadState(rootView, state);
              checkIfFullyLoaded();
            });
    viewModel
        .getWordLoadState()
        .observe(
            getViewLifecycleOwner(),
            state -> {
              if (state == null) {
                return;
              }
              applyWordLoadState(rootView, state);
              checkIfFullyLoaded();
            });

    Bundle args = getArguments();
    String summaryJson = args == null ? null : args.getString(ARG_SUMMARY_JSON);
    String featureBundleJson = args == null ? null : args.getString(ARG_FEATURE_BUNDLE_JSON);
    viewModel.initialize(summaryJson, featureBundleJson, savedInstanceState);
  }

  private void applyExpressionLoadState(
      View rootView, @NonNull DialogueSummaryViewModel.ExpressionLoadState state) {
    switch (state.getStatus()) {
      case READY:
        SummaryData exprData = viewModel == null ? null : viewModel.getSummaryData().getValue();
        SessionSummaryBinder.bindExpressions(
            rootView, exprData == null ? null : exprData.getExpressions());
        break;
      case ERROR:
        SessionSummaryBinder.showExpressionsError(
            rootView, getString(R.string.summary_expressions_load_error));
        break;
      case LOADING:
      default:
        SessionSummaryBinder.showExpressionsLoading(rootView);
        break;
    }
  }

  private void applyWordLoadState(
      View rootView, @NonNull DialogueSummaryViewModel.WordLoadState state) {
    String errorMessage = getString(R.string.summary_words_load_error);
    switch (state.getStatus()) {
      case READY:
        SummaryData data = viewModel == null ? null : viewModel.getSummaryData().getValue();
        SessionSummaryBinder.bindWordsContent(rootView, data == null ? null : data.getWords());
        break;
      case HIDDEN:
        SessionSummaryBinder.hideWordsSection(rootView);
        break;
      case EMPTY:
        SessionSummaryBinder.showWordsEmpty(rootView);
        break;
      case ERROR:
        SessionSummaryBinder.showWordsError(
            rootView, state.getErrorMessage() == null ? errorMessage : state.getErrorMessage());
        break;
      case LOADING:
      default:
        SessionSummaryBinder.showWordsLoading(rootView);
        break;
    }
  }

  private void checkIfFullyLoaded() {
    if (viewModel == null) return;
    DialogueSummaryViewModel.ExpressionLoadState exprState =
        viewModel.getExpressionLoadState().getValue();
    DialogueSummaryViewModel.WordLoadState wordState = viewModel.getWordLoadState().getValue();

    boolean isNowLoaded = true;

    if (exprState != null
        && exprState.getStatus() == DialogueSummaryViewModel.ExpressionLoadStatus.LOADING) {
      isNowLoaded = false;
    }
    if (wordState != null
        && wordState.getStatus() == DialogueSummaryViewModel.WordLoadStatus.LOADING) {
      isNowLoaded = false;
    }

    if (isFullyLoaded == null || isFullyLoaded != isNowLoaded) {
      isFullyLoaded = isNowLoaded;
      updateBottomSheetUI(isNowLoaded);
    }
  }

  private void updateBottomSheetUI(boolean isLoaded) {
    if (isLoaded) {
      if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        if (tvBottomSheetTitle != null) tvBottomSheetTitle.setText("학습을 마칠까요?");
        if (tvBottomSheetSubtitle != null) tvBottomSheetSubtitle.setText("오늘의 성과가 저장되었습니다.");
        if (btnStartQuiz != null) btnStartQuiz.setVisibility(View.VISIBLE);
        if (btnFinishSummary != null) btnFinishSummary.setVisibility(View.VISIBLE);

        if (getView() != null) {
          getView()
              .postDelayed(
                  () -> {
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                  },
                  200);
        }
      } else {
        if (tvBottomSheetTitle != null) tvBottomSheetTitle.setText("학습을 마칠까요?");
        if (tvBottomSheetSubtitle != null) tvBottomSheetSubtitle.setText("오늘의 성과가 저장되었습니다.");
        if (btnStartQuiz != null) btnStartQuiz.setVisibility(View.VISIBLE);
        if (btnFinishSummary != null) btnFinishSummary.setVisibility(View.VISIBLE);
      }
    } else {
      if (tvBottomSheetTitle != null) tvBottomSheetTitle.setText("잠시만 기다려주세요");
      if (tvBottomSheetSubtitle != null) tvBottomSheetSubtitle.setText("요약 데이터가 완전히 로딩되지 않았어요");
      if (btnStartQuiz != null) btnStartQuiz.setVisibility(View.GONE);
      if (btnFinishSummary != null) btnFinishSummary.setVisibility(View.GONE);
    }
  }

  private void handleFinishSummaryClick() {
    if (isFinishingFlowInProgress) {
      logDebug("summary finish ignored: flow already in progress");
      return;
    }
    isFinishingFlowInProgress = true;
    if (btnFinishSummary != null) {
      btnFinishSummary.setEnabled(false);
    }

    logDebug("summary finish selected");
    InAppReviewPromptStore reviewPromptStore =
        new InAppReviewPromptStore(requireContext().getApplicationContext());
    boolean reviewOpportunityConsumed =
        reviewPromptStore.consumeDialogueSummaryFinishReviewOpportunity();
    logDebug("summary review opportunity consumed: " + reviewOpportunityConsumed);

    if (!reviewOpportunityConsumed) {
      finishHostActivitySafely();
      return;
    }
    requestInAppReviewThenFinish();
  }

  private void requestInAppReviewThenFinish() {
    if (!isAdded()) {
      logDebug("summary review request skipped: fragment not added");
      finishHostActivitySafely();
      return;
    }
    ReviewManager reviewManager = ReviewManagerFactory.create(requireContext());
    reviewManager
        .requestReviewFlow()
        .addOnSuccessListener(
            reviewInfo -> {
              logDebug("summary review request success");
              launchInAppReviewThenFinish(reviewManager, reviewInfo);
            })
        .addOnFailureListener(
            throwable -> {
              String reason = throwable == null ? "unknown" : String.valueOf(throwable.getMessage());
              logDebug("summary review request failed: " + reason);
              finishHostActivitySafely();
            });
  }

  private void launchInAppReviewThenFinish(
      @NonNull ReviewManager reviewManager, @NonNull ReviewInfo reviewInfo) {
    FragmentActivity hostActivity = getActivity();
    if (hostActivity == null) {
      logDebug("summary review launch skipped: host activity null");
      finishHostActivitySafely();
      return;
    }
    reviewManager
        .launchReviewFlow(hostActivity, reviewInfo)
        .addOnCompleteListener(
            task -> {
              logDebug("summary review launch completed");
              finishHostActivitySafely();
            });
  }

  private void finishHostActivitySafely() {
    FragmentActivity hostActivity = getActivity();
    if (hostActivity != null) {
      hostActivity.finish();
      return;
    }
    if (btnFinishSummary != null) {
      btnFinishSummary.setEnabled(true);
    }
    isFinishingFlowInProgress = false;
  }

  private void navigateToQuiz() {
    if (getActivity() == null) {
      return;
    }
    Bundle args = getArguments();
    String summaryJson = args == null ? null : args.getString(ARG_SUMMARY_JSON);
    String featureBundleJson = args == null ? null : args.getString(ARG_FEATURE_BUNDLE_JSON);
    logDebug("summary quiz selected");
    android.content.Intent intent =
        new android.content.Intent(
            requireContext(), QuizActivity.class);
    intent.putExtra(
        QuizActivity.EXTRA_SUMMARY_JSON, summaryJson);
    intent.putExtra(
        QuizActivity.EXTRA_FEATURE_BUNDLE_JSON,
        featureBundleJson);
    startActivity(intent);
    if (getActivity() != null) {
      getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }
  }

  private void logDebug(@NonNull String message) {
    Log.d(TAG, message);
  }
}
