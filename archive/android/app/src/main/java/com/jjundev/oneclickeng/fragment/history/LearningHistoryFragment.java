package com.jjundev.oneclickeng.fragment.history;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.material.tabs.TabLayout;
import com.google.gson.Gson;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.activity.QuizActivity;
import com.jjundev.oneclickeng.credit.CreditHistoryEventLogger;
import com.jjundev.oneclickeng.dialog.QuizGenerateDialog;
import com.jjundev.oneclickeng.learning.dialoguelearning.di.LearningDependencyProvider;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IQuizGenerationManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.QuizData;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SummaryData;
import com.jjundev.oneclickeng.learning.quiz.session.QuizStreamingSessionStore;
import com.jjundev.oneclickeng.settings.AppSettings;
import com.jjundev.oneclickeng.settings.AppSettingsStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LearningHistoryFragment extends Fragment {

  private static final String TAG = "LearningHistoryFrag";
  private static final String TAG_HISTORY_QUIZ_CONFIG_DIALOG = "QuizGenerateDialog";
  private static final int QUIZ_CREDIT_THRESHOLD_COUNT = 6;
  private static final int QUIZ_CREDIT_DEFAULT = 1;
  private static final int QUIZ_CREDIT_EXTENDED = 2;

  private LearningHistoryViewModel viewModel;
  private LearningHistoryAdapter adapter;
  private TabLayout tabLayout;
  private RecyclerView recyclerView;
  private View emptyStateLayout;
  @Nullable private QuizGenerateDialog pendingConfigDialog;
  @Nullable private String pendingQuizSessionId;
  @Nullable private QuizStreamingSessionStore.Listener pendingQuizSessionListener;
  @Nullable private RewardedAd rewardedAd;
  @Nullable private android.app.Dialog chargeCreditDialog;
  private boolean isRewardEarned = false;
  private boolean isWaitingForAd = false;
  private int adRetryAttempt = 0;
  @NonNull private final Handler uiHandler = new Handler(Looper.getMainLooper());
  @NonNull private final Handler adRetryHandler = new Handler(Looper.getMainLooper());
  private boolean adLifecycleActive = false;
  private boolean isAdLoading = false;
  private boolean isAdShowing = false;
  private long adLoadGeneration = 0L;
  private long quizPreparationRequestId = 0L;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_learning_history, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    initViewModel();
    initViews(view);
    observeViewModel();
  }

  @Override
  public void onStart() {
    super.onStart();
    activateAdLifecycle();
  }

  @Override
  public void onStop() {
    softResetAdStateForStop();
    super.onStop();
  }

  @Override
  public void onDestroyView() {
    clearPendingQuizSession(true);
    hardResetAdStateForDestroyView();
    pendingConfigDialog = null;
    quizPreparationRequestId = 0L;
    super.onDestroyView();
  }

  private void initViews(View view) {
    tabLayout = view.findViewById(R.id.tab_layout_history);
    recyclerView = view.findViewById(R.id.rv_learning_history);
    emptyStateLayout = view.findViewById(R.id.layout_empty_state);

    setupTabs();

    adapter =
        new LearningHistoryAdapter(
            item -> {
              // Handle save/unsave click later
              logDebug("Item clicked: " + item.getType());
            });
    recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
    recyclerView.setAdapter(adapter);

    com.jjundev.oneclickeng.utils.SwipeHelper swipeHelper =
        new com.jjundev.oneclickeng.utils.SwipeHelper(requireContext(), recyclerView) {
          @Override
          public void instantiateUnderlayButton(
              RecyclerView.ViewHolder viewHolder, java.util.List<UnderlayButton> underlayButtons) {
            underlayButtons.add(
                new UnderlayButton(
                    requireContext(),
                    "",
                    androidx.core.content.ContextCompat.getDrawable(
                        requireContext(), R.drawable.ic_delete_sweep),
                    R.color.purple_700,
                    new UnderlayButtonClickListener() {
                      @Override
                      public void onClick(int pos) {
                        HistoryItemWrapper itemToSwipe = adapter.getItem(pos);
                        if (itemToSwipe != null && viewModel != null) {
                          viewModel.removeCard(itemToSwipe);
                          android.widget.Toast.makeText(
                                  requireContext(), "카드를 삭제했어요", android.widget.Toast.LENGTH_SHORT)
                              .show();
                        }
                      }
                    }));
          }
        };

    View btnQuiz = view.findViewById(R.id.btn_history_quiz);
    if (btnQuiz != null) {
      btnQuiz.setOnClickListener(
          v -> {
            QuizGenerateDialog dialog = new QuizGenerateDialog();
            pendingConfigDialog = dialog;
            dialog.show(getChildFragmentManager(), TAG_HISTORY_QUIZ_CONFIG_DIALOG);
          });
    }

    getChildFragmentManager()
        .setFragmentResultListener(
            QuizGenerateDialog.REQUEST_KEY,
            getViewLifecycleOwner(),
            (requestKey, result) -> {
              clearPendingQuizSession(true);
              long requestId = ++quizPreparationRequestId;
              int periodBucket = result.getInt(QuizGenerateDialog.BUNDLE_KEY_PERIOD_BUCKET);
              int questionCount = result.getInt(QuizGenerateDialog.BUNDLE_KEY_QUESTION_COUNT);
              int requiredCredit =
                  result.containsKey(QuizGenerateDialog.BUNDLE_KEY_REQUIRED_CREDIT)
                      ? result.getInt(QuizGenerateDialog.BUNDLE_KEY_REQUIRED_CREDIT)
                      : calculateRequiredCredit(questionCount);
              requiredCredit = Math.max(1, requiredCredit);
              int currentTab = tabLayout != null ? tabLayout.getSelectedTabPosition() : 0;
              QuizGenerateDialog dialog = resolveConfigDialog();

              if (dialog == null) {
                return;
              }

              dialog.setLoadingState(true);
              if (!isAdded()) {
                dialog.setLoadingState(false);
                return;
              }
              verifyCreditAndPrepareQuiz(
                  dialog, requestId, periodBucket, questionCount, requiredCredit, currentTab);
            });
  }

  private void setupTabs() {
    tabLayout.addTab(tabLayout.newTab().setText("표현"));
    tabLayout.addTab(tabLayout.newTab().setText("단어"));
    tabLayout.addTab(tabLayout.newTab().setText("문장"));

    tabLayout.addOnTabSelectedListener(
        new TabLayout.OnTabSelectedListener() {
          @Override
          public void onTabSelected(TabLayout.Tab tab) {
            filterData(tab.getPosition());
          }

          @Override
          public void onTabUnselected(TabLayout.Tab tab) {}

          @Override
          public void onTabReselected(TabLayout.Tab tab) {
            filterData(tab.getPosition());
          }
        });
  }

  private void initViewModel() {
    viewModel = new ViewModelProvider(this).get(LearningHistoryViewModel.class);
    viewModel.loadSavedCards();
  }

  private void observeViewModel() {
    viewModel
        .getHistoryItems()
        .observe(
            getViewLifecycleOwner(),
            items -> {
              if (items != null) {
                filterData(tabLayout.getSelectedTabPosition());
              }
            });
  }

  private void filterData(int tabPosition) {
    List<HistoryItemWrapper> allItems = viewModel.getHistoryItems().getValue();
    if (allItems == null) return;

    adapter.submitList(allItems, tabPosition);

    boolean isEmpty = adapter.getItemCount() == 0;
    if (isEmpty) {
      if (recyclerView != null) recyclerView.setVisibility(View.GONE);
      if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.VISIBLE);
    } else {
      if (recyclerView != null) {
        recyclerView.setVisibility(View.VISIBLE);
        android.view.animation.LayoutAnimationController controller =
            android.view.animation.AnimationUtils.loadLayoutAnimation(
                recyclerView.getContext(), R.anim.layout_anim_slide_fade_in);
        recyclerView.setLayoutAnimation(controller);
        recyclerView.scheduleLayoutAnimation();
      }
      if (emptyStateLayout != null) emptyStateLayout.setVisibility(View.GONE);
    }
  }

  private void verifyCreditAndPrepareQuiz(
      @NonNull QuizGenerateDialog dialog,
      long requestId,
      int periodBucket,
      int questionCount,
      int requiredCredit,
      int currentTab) {
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) {
      finishQuizPreparation(dialog, requestId);
      showToast("로그인이 필요합니다.");
      return;
    }

    FirebaseFirestore.getInstance()
        .collection("users")
        .document(user.getUid())
        .get()
        .addOnCompleteListener(
            task -> {
              if (!isAdded() || requestId != quizPreparationRequestId) {
                return;
              }

              if (!task.isSuccessful() || task.getResult() == null) {
                finishQuizPreparation(dialog, requestId);
                showToast("크레딧 정보를 불러오지 못했어요.");
                return;
              }

              DocumentSnapshot doc = task.getResult();
              Long creditObj = doc.getLong("credit");
              long credit = creditObj != null ? creditObj : 0L;
              if (credit < requiredCredit) {
                logDebug(
                    "Insufficient credit: required="
                        + requiredCredit
                        + ", current="
                        + credit
                        + ", questionCount="
                        + questionCount);
                finishQuizPreparation(dialog, requestId);
                if (dialog.isAdded()) {
                  dialog.dismiss();
                }
                showToast("크레딧이 부족해요");
                showChargeCreditDialog();
                return;
              }

              prepareQuizGeneration(
                  dialog, requestId, periodBucket, questionCount, requiredCredit, currentTab);
            });
  }

  private void prepareQuizGeneration(
      @NonNull QuizGenerateDialog dialog,
      long requestId,
      int periodBucket,
      int questionCount,
      int requiredCredit,
      int currentTab) {
    if (requestId != quizPreparationRequestId) {
      return;
    }

    SummaryData seed = viewModel.generateQuizSeed(periodBucket, currentTab);
    if (seed == null) {
      finishQuizPreparation(dialog, requestId);
      showToast(R.string.history_quiz_err_no_items);
      return;
    }

    AppSettings settings = new AppSettingsStore(requireContext()).getSettings();
    IQuizGenerationManager quizManager =
        LearningDependencyProvider.provideQuizGenerationManager(
            requireContext(),
            settings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY),
            settings.getLlmModelSummary());
    QuizStreamingSessionStore sessionStore =
        LearningDependencyProvider.provideQuizStreamingSessionStore();

    final boolean[] completed = {false};
    final SummaryData finalSeed = seed;
    String sessionId = sessionStore.startSession(quizManager, finalSeed, questionCount);

    QuizStreamingSessionStore.Listener listener =
        new QuizStreamingSessionStore.Listener() {
          @Override
          public void onQuestion(@NonNull QuizData.QuizQuestion question) {
            runOnMainThread(
                () -> {
                  if (requestId != quizPreparationRequestId || completed[0]) {
                    return;
                  }
                  if (!isValidQuestionReadyForStart(question)) {
                    return;
                  }
                  completed[0] = true;
                  startPreparedQuiz(
                      dialog, requestId, finalSeed, questionCount, requiredCredit, sessionId);
                });
          }

          @Override
          public void onComplete(@Nullable String warningMessage) {
            runOnMainThread(
                () -> {
                  if (requestId != quizPreparationRequestId || completed[0]) {
                    return;
                  }
                  completed[0] = true;
                  showPreparationError(dialog, requestId, warningMessage);
                });
          }

          @Override
          public void onFailure(@NonNull String error) {
            runOnMainThread(
                () -> {
                  if (requestId != quizPreparationRequestId || completed[0]) {
                    return;
                  }
                  completed[0] = true;
                  showPreparationError(dialog, requestId, error);
                });
          }
        };
    pendingQuizSessionId = sessionId;
    pendingQuizSessionListener = listener;

    QuizStreamingSessionStore.Snapshot snapshot = sessionStore.attach(sessionId, listener);
    if (snapshot == null) {
      completed[0] = true;
      showPreparationError(dialog, requestId, null);
      return;
    }
    if (hasStartableQuestion(snapshot.getBufferedQuestions())) {
      completed[0] = true;
      startPreparedQuiz(dialog, requestId, finalSeed, questionCount, requiredCredit, sessionId);
      return;
    }

    String snapshotFailure = trimToNull(snapshot.getFailureMessage());
    if (snapshotFailure != null) {
      completed[0] = true;
      showPreparationError(dialog, requestId, snapshotFailure);
      return;
    }
    if (snapshot.isCompleted()) {
      completed[0] = true;
      showPreparationError(dialog, requestId, snapshot.getWarningMessage());
    }
  }

  private void startPreparedQuiz(
      @NonNull QuizGenerateDialog dialog,
      long requestId,
      @NonNull SummaryData seed,
      int questionCount,
      int requiredCredit,
      @NonNull String sessionId) {
    clearPendingQuizSession(false);
    finishQuizPreparation(dialog, requestId);
    if (dialog.isAdded()) {
      dialog.dismiss();
    }
    startDialogueQuiz(seed, questionCount, requiredCredit, sessionId);
  }

  private void showPreparationError(
          @NonNull QuizGenerateDialog dialog, long requestId, @Nullable String detailMessage) {
    clearPendingQuizSession(true);
    finishQuizPreparation(dialog, requestId);
    if (!isAdded()) {
      return;
    }
    String safeMessage = trimToNull(detailMessage);
    if (safeMessage == null) {
      showToast(R.string.quiz_error_default);
      return;
    }
    Toast.makeText(
            requireContext(),
            getString(R.string.quiz_error_default) + " (" + safeMessage + ")",
            Toast.LENGTH_SHORT)
        .show();
  }

  private void finishQuizPreparation(@NonNull QuizGenerateDialog dialog, long requestId) {
    dialog.setLoadingState(false);
    if (requestId == quizPreparationRequestId) {
      quizPreparationRequestId = 0L;
    }
    if (dialog == pendingConfigDialog) {
      pendingConfigDialog = null;
    }
  }

  private void clearPendingQuizSession(boolean releaseSession) {
    String sessionId = pendingQuizSessionId;
    QuizStreamingSessionStore.Listener listener = pendingQuizSessionListener;
    pendingQuizSessionId = null;
    pendingQuizSessionListener = null;

    if (sessionId == null) {
      return;
    }
    QuizStreamingSessionStore sessionStore =
        LearningDependencyProvider.provideQuizStreamingSessionStore();
    if (listener != null) {
      sessionStore.detach(sessionId, listener);
    }
    if (releaseSession) {
      sessionStore.release(sessionId);
    }
  }

  private boolean hasStartableQuestion(@Nullable List<QuizData.QuizQuestion> questions) {
    if (questions == null || questions.isEmpty()) {
      return false;
    }
    for (QuizData.QuizQuestion question : questions) {
      if (isValidQuestionReadyForStart(question)) {
        return true;
      }
    }
    return false;
  }

  private void startDialogueQuiz(
      @NonNull SummaryData seed,
      int questionCount,
      int requiredCredit,
      @NonNull String sessionId) {
    if (!isAdded()) {
      LearningDependencyProvider.provideQuizStreamingSessionStore().release(sessionId);
      return;
    }
    Intent intent = new Intent(requireContext(), QuizActivity.class);
    intent.putExtra(QuizActivity.EXTRA_SUMMARY_JSON, new Gson().toJson(seed));
    intent.putExtra(QuizActivity.EXTRA_REQUESTED_QUESTION_COUNT, questionCount);
    intent.putExtra(QuizActivity.EXTRA_STREAM_SESSION_ID, sessionId);
    try {
      startActivity(intent);
      if (getActivity() != null) {
        getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
      }
      decrementCreditOnQuizStart(requiredCredit);
    } catch (Exception e) {
      LearningDependencyProvider.provideQuizStreamingSessionStore().release(sessionId);
      logDebug("Activity start failed: " + e.getMessage());
      showToast("퀴즈 이동 중 오류가 발생했어요.");
    }
  }

  private void decrementCreditOnQuizStart(int requiredCredit) {
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) {
      return;
    }
    int creditToDeduct = Math.max(1, requiredCredit);

    CreditHistoryEventLogger.applyDeltaWithEvent(
        user.getUid(),
        -creditToDeduct,
        CreditHistoryEventLogger.EVENT_QUIZ_USE,
        "quiz_start",
        "learning_history",
        CreditHistoryEventLogger.SOURCE_APP,
        new CreditHistoryEventLogger.Callback() {
          @Override
          public void onSuccess(long creditAfter) {}

          @Override
          public void onFailure(@NonNull Exception exception) {
            logDebug(
                "Failed to decrement credit("
                    + creditToDeduct
                    + "): "
                    + exception.getMessage());
          }
        });
  }

  private static int calculateRequiredCredit(int questionCount) {
    return questionCount >= QUIZ_CREDIT_THRESHOLD_COUNT
        ? QUIZ_CREDIT_EXTENDED
        : QUIZ_CREDIT_DEFAULT;
  }

  private boolean isValidQuestionReadyForStart(@Nullable QuizData.QuizQuestion question) {
    if (question == null) {
      return false;
    }

    String questionText = trimToNull(question.getQuestionMain());
    String answer = trimToNull(question.getAnswer());
    if (questionText == null || answer == null) {
      return false;
    }

    List<String> choices = question.getChoices();
    if (choices == null) {
      return false;
    }
    List<String> sanitizedChoices = sanitizeChoices(choices, answer);
    return sanitizedChoices != null && sanitizedChoices.size() >= 2;
  }

  @Nullable
  private List<String> sanitizeChoices(
      @Nullable List<String> sourceChoices, @NonNull String answer) {
    if (sourceChoices == null || sourceChoices.isEmpty()) {
      return null;
    }

    List<String> result = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    String trimmedAnswer = trimToNull(answer);
    for (String rawChoice : sourceChoices) {
      String choice = trimToNull(rawChoice);
      if (choice == null) {
        continue;
      }
      String normalized = normalize(choice);
      if (!seen.add(normalized)) {
        continue;
      }
      result.add(choice);
    }

    if (result.isEmpty() && trimmedAnswer != null) {
      result.add(trimmedAnswer);
    }

    if (trimmedAnswer != null && !seen.contains(normalize(trimmedAnswer))) {
      result.add(trimmedAnswer);
    }

    return result.size() >= 2 ? result : null;
  }

  private void showChargeCreditDialog() {
    if (!isAdded()) {
      return;
    }

    View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_charge_credit, null);
    View layoutContent = dialogView.findViewById(R.id.layout_content);
    View layoutLoading = dialogView.findViewById(R.id.layout_loading);
    AppCompatButton btnGoToCreditStore = dialogView.findViewById(R.id.btn_go_to_credit_store);
    AppCompatButton btnCancel = dialogView.findViewById(R.id.btn_charge_cancel);
    AppCompatButton btnAd = dialogView.findViewById(R.id.btn_charge_ad);

    chargeCreditDialog = new android.app.Dialog(requireContext());
    chargeCreditDialog.setContentView(dialogView);
    if (chargeCreditDialog.getWindow() != null) {
      chargeCreditDialog
          .getWindow()
          .setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
      android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
      int width = (int) (metrics.widthPixels * 0.9f);
      chargeCreditDialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    chargeCreditDialog.setOnDismissListener(
        d -> {
          isWaitingForAd = false;
          chargeCreditDialog = null;
        });

    btnCancel.setOnClickListener(v -> chargeCreditDialog.dismiss());
    btnGoToCreditStore.setOnClickListener(
        v -> {
          if (!isAdded()) {
            return;
          }

          NavController navController = NavHostFragment.findNavController(this);
          if (navController.getCurrentDestination() != null
              && navController.getCurrentDestination().getId() == R.id.creditStoreFragment) {
            return;
          }

          chargeCreditDialog.dismiss();
          navController.navigate(R.id.action_learningHistoryFragment_to_creditStoreFragment);
        });

    btnAd.setOnClickListener(
        v -> {
          if (rewardedAd != null && isAdded() && isResumed()) {
            dismissChargeCreditDialog();
            showRewardedAdIfAvailable();
          } else {
            isWaitingForAd = true;
            layoutContent.setVisibility(View.GONE);
            layoutLoading.setVisibility(View.VISIBLE);
            chargeCreditDialog.setCancelable(false);

            ensureRewardedAdPreloaded();
          }
        });

    chargeCreditDialog.show();
  }

  private void activateAdLifecycle() {
    adLifecycleActive = true;
    adLoadGeneration++;
  }

  private void softResetAdStateForStop() {
    adLifecycleActive = false;
    adLoadGeneration++;
    adRetryHandler.removeCallbacksAndMessages(null);
    dismissChargeCreditDialog();
    isAdLoading = false;
    adRetryAttempt = 0;
  }

  private void hardResetAdStateForDestroyView() {
    softResetAdStateForStop();
    rewardedAd = null;
    isAdLoading = false;
    isAdShowing = false;
    isRewardEarned = false;
    adRetryAttempt = 0;
    chargeCreditDialog = null;
  }

  private void dismissChargeCreditDialog() {
    if (chargeCreditDialog != null && chargeCreditDialog.isShowing()) {
      chargeCreditDialog.dismiss();
    }
    chargeCreditDialog = null;
    isWaitingForAd = false;
  }

  private void ensureRewardedAdPreloaded() {
    if (!adLifecycleActive || isAdLoading || rewardedAd != null || !isAdded() || getContext() == null) {
      return;
    }

    AdRequest adRequest = new AdRequest.Builder().build();
    String adUnitId =
        BuildConfig.DEBUG
            ? "ca-app-pub-3940256099942544/5224354917"
            : BuildConfig.ADMOB_REWARDED_AD_UNIT_ID;
    logDebug("Loading rewarded ad with Unit ID: " + adUnitId);
    isAdLoading = true;
    long generation = adLoadGeneration;
    RewardedAd.load(
        requireContext(),
        adUnitId,
        adRequest,
        new RewardedAdLoadCallback() {
          @Override
          public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
            if (generation != adLoadGeneration) {
              return;
            }
            isAdLoading = false;
            if (!adLifecycleActive) {
              return;
            }
            logDebug("Failed to load rewarded ad: " + loadAdError.getMessage());
            rewardedAd = null;
            adRetryAttempt++;
            long retryDelayMillis = (long) Math.pow(2, Math.min(6, adRetryAttempt)) * 1000L;
            if (isWaitingForAd && adRetryAttempt <= 5 && adLifecycleActive) {
              logDebug("Retrying ad load in " + retryDelayMillis + "ms (Attempt " + adRetryAttempt + ")");
              adRetryHandler.postDelayed(
                  () -> {
                    if (!adLifecycleActive || generation != adLoadGeneration) {
                      return;
                    }
                    ensureRewardedAdPreloaded();
                  },
                  retryDelayMillis);
            } else {
              if (isWaitingForAd) {
                isWaitingForAd = false;
                if (chargeCreditDialog != null && chargeCreditDialog.isShowing()) {
                  chargeCreditDialog.dismiss();
                }
                if (isAdded()) {
                  Toast.makeText(getContext(), "광고를 불러오는 데 실패했어요. 나중에 다시 시도해주세요.", Toast.LENGTH_SHORT)
                      .show();
                }
              }
            }
          }

          @Override
          public void onAdLoaded(@NonNull RewardedAd ad) {
            if (generation != adLoadGeneration) {
              return;
            }
            isAdLoading = false;
            if (!adLifecycleActive || !isAdded()) {
              return;
            }
            logDebug("Rewarded ad loaded.");
            rewardedAd = ad;
            adRetryAttempt = 0;
            setFullScreenContentCallback(ad);

            if (isWaitingForAd && isResumed()) {
              dismissChargeCreditDialog();
              showRewardedAdIfAvailable();
            }
          }
        });
  }

  private void showRewardedAdIfAvailable() {
    if (!adLifecycleActive || !isAdded() || !isResumed()) {
      return;
    }
    RewardedAd adToShow = rewardedAd;
    if (adToShow == null) {
      return;
    }

    rewardedAd = null;
    isAdShowing = true;
    isRewardEarned = false;
    adToShow.show(
        requireActivity(),
        rewardItem -> {
          isRewardEarned = true;
          increaseCredit();
        });
  }

  private void setFullScreenContentCallback(@NonNull RewardedAd ad) {
    ad.setFullScreenContentCallback(
        new FullScreenContentCallback() {
          @Override
          public void onAdShowedFullScreenContent() {
            isAdShowing = true;
          }

          @Override
          public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
            isAdShowing = false;
            if (!adLifecycleActive) {
              return;
            }
            logDebug("Ad failed to show: " + adError.getMessage());
          }

          @Override
          public void onAdDismissedFullScreenContent() {
            isAdShowing = false;
            if (!adLifecycleActive) {
              return;
            }
            logDebug("Ad dismissed");
            if (!isRewardEarned && isAdded()) {
              Toast.makeText(getContext(), "광고 시청을 완료하지 않아 크레딧이 지급되지 않았어요", Toast.LENGTH_SHORT)
                  .show();
            }
          }
        });
  }

  private void increaseCredit() {
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) {
      showToast("로그인 정보를 확인할 수 없어요");
      return;
    }

    CreditHistoryEventLogger.applyDeltaWithEvent(
        user.getUid(),
        1L,
        CreditHistoryEventLogger.EVENT_AD_CHARGE,
        "ad_reward",
        "learning_history",
        CreditHistoryEventLogger.SOURCE_APP,
        new CreditHistoryEventLogger.Callback() {
          @Override
          public void onSuccess(long creditAfter) {
            showToast("1 크레딧이 충전되었어요");
          }

          @Override
          public void onFailure(@NonNull Exception exception) {
            logDebug("Failed to add credit: " + exception.getMessage());
            showToast("크레딧 충전에 실패했어요");
          }
        });
  }

  private void showToast(int messageResId) {
    if (isAdded()) {
      Toast.makeText(requireContext(), messageResId, Toast.LENGTH_SHORT).show();
    }
  }

  private void showToast(@NonNull String message) {
    if (isAdded()) {
      Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
  }

  @Nullable
  private static String trimToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  @NonNull
  private static String normalize(@Nullable String value) {
    return value == null ? "" : value.trim().toLowerCase();
  }

  private QuizGenerateDialog resolveConfigDialog() {
    androidx.fragment.app.Fragment dialogFragment =
        getChildFragmentManager().findFragmentByTag(TAG_HISTORY_QUIZ_CONFIG_DIALOG);
    if (dialogFragment instanceof QuizGenerateDialog) {
      return (QuizGenerateDialog) dialogFragment;
    }
    return pendingConfigDialog;
  }

  private void logDebug(String message) {
    Log.d(TAG, message);
  }

  private void runOnMainThread(@NonNull Runnable action) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      action.run();
      return;
    }
    uiHandler.post(action);
  }
}
