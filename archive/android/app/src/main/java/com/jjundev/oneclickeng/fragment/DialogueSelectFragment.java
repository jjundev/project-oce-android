package com.jjundev.oneclickeng.fragment;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
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
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.activity.DialogueLearningActivity;
import com.jjundev.oneclickeng.credit.CreditHistoryEventLogger;
import com.jjundev.oneclickeng.dialog.DialogueGenerateDialog;
import com.jjundev.oneclickeng.dialog.DialogueLearningSettingDialog;
import com.jjundev.oneclickeng.learning.dialoguelearning.di.LearningDependencyProvider;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IDialogueGenerateManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.session.DialogueScriptStreamingSessionStore;
import com.jjundev.oneclickeng.others.ScriptSelectAdapter;
import com.jjundev.oneclickeng.others.ScriptTemplate;
import com.jjundev.oneclickeng.settings.AppSettings;
import com.jjundev.oneclickeng.settings.AppSettingsStore;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class DialogueSelectFragment extends Fragment
    implements DialogueGenerateDialog.OnScriptParamsSelectedListener {
  private static final String TAG = "DialogueSelectFragment";
  private static final String DIALOG_TAG_LEARNING_SETTINGS = "DialogueLearningSettingDialog";
  private static final int MIN_TURN_COUNT_TO_START = 2;
  private static final int DISPLAYED_SCRIPT_COUNT = 4;
  private static final long REFRESH_ANIMATION_DURATION_MS = 500L;

  private ImageButton btnBack;
  private ImageButton btnSettings;
  private ImageButton btnRefreshScripts;
  private RecyclerView rvScripts;
  private View layoutEmptyState;
  private AppCompatButton btnGenerate;
  private ScriptSelectAdapter adapter;
  private List<ScriptTemplate> allTemplateList;
  private List<ScriptTemplate> templateList;
  private IDialogueGenerateManager scriptGenerator;
  @Nullable
  private String pendingScriptSessionId;
  @Nullable
  private DialogueScriptStreamingSessionStore.Listener pendingScriptSessionListener;
  private long scriptPreparationRequestId = 0L;
  private boolean isRefreshing = false;
  private final Handler refreshHandler = new Handler(Looper.getMainLooper());

  private RewardedAd rewardedAd;
  private boolean isRewardEarned = false;
  private boolean isWaitingForAd = false;
  private android.app.Dialog chargeCreditDialog;
  private int adRetryAttempt = 0;
  private final android.os.Handler adRetryHandler = new android.os.Handler(android.os.Looper.getMainLooper());
  private boolean adLifecycleActive = false;
  private boolean isAdLoading = false;
  private boolean isAdShowing = false;
  private long adLoadGeneration = 0L;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_dialogue_select, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    scriptGenerator = resolveScriptGenerator();
    scriptGenerator.initializeCache(
        new IDialogueGenerateManager.InitCallback() {
          @Override
          public void onReady() {
            logStream("generator cache ready");
          }

          @Override
          public void onError(String error) {
            Log.e(TAG, "[DL_STREAM] generator cache init error: " + error);
          }
        });

    btnBack = view.findViewById(R.id.btn_back);
    btnSettings = view.findViewById(R.id.btn_settings);
    btnRefreshScripts = view.findViewById(R.id.btn_refresh_scripts);
    rvScripts = view.findViewById(R.id.rv_scripts);
    layoutEmptyState = view.findViewById(R.id.layout_empty_state);
    btnGenerate = view.findViewById(R.id.btn_generate_script);

    setupRecyclerView();
    setupListeners();
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
    clearPendingScriptSession(true);
    scriptPreparationRequestId = 0L;
    refreshHandler.removeCallbacksAndMessages(null);
    isRefreshing = false;
    hardResetAdStateForDestroyView();
    super.onDestroyView();
  }

  private void setupRecyclerView() {
    allTemplateList = loadTemplatesFromAssets();

    // 전체 리스트에서 무작위 4개를 선택하여 표시용 리스트 생성
    templateList = new ArrayList<>();
    shuffleAndDisplayScripts();

    adapter = new ScriptSelectAdapter(
        templateList,
        template -> {
          hideKeyboard();
          DialogueGenerateDialog dialog = DialogueGenerateDialog.newInstance(template.getTitle());
          dialog.show(getChildFragmentManager(), "DialogueGenerateDialog");
        });

    rvScripts.setLayoutManager(new GridLayoutManager(getContext(), 2));
    rvScripts.setAdapter(adapter);

    // Apply layout animation to the RecyclerView
    android.view.animation.LayoutAnimationController controller = android.view.animation.AnimationUtils
        .loadLayoutAnimation(
            rvScripts.getContext(), R.anim.layout_anim_slide_fade_in);
    rvScripts.setLayoutAnimation(controller);

    updateEmptyState();
  }

  private IDialogueGenerateManager resolveScriptGenerator() {
    Context appContext = requireContext().getApplicationContext();
    AppSettings settings = new AppSettingsStore(appContext).getSettings();
    return LearningDependencyProvider.provideDialogueGenerateManager(
        appContext,
        settings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY),
        settings.getLlmModelScript());
  }

  private void setupListeners() {
    btnBack.setOnClickListener(
        v -> {
          Navigation.findNavController(v).popBackStack();
        });

    btnSettings.setOnClickListener(v -> showDialogueLearningSettingDialog());

    btnGenerate.setOnClickListener(
        v -> {
          hideKeyboard(); // Ensure keyboard is hidden

          DialogueGenerateDialog dialogueGenerateDialog = new DialogueGenerateDialog();
          dialogueGenerateDialog.show(getChildFragmentManager(), "DialogueGenerateDialog");
        });

    btnRefreshScripts.setOnClickListener(
        v -> {
          if (isRefreshing)
            return;
          startRefreshWithAnimation();
        });
  }

  private void showDialogueLearningSettingDialog() {
    if (!isAdded()) {
      return;
    }

    FragmentManager fragmentManager = getChildFragmentManager();
    if (fragmentManager.isStateSaved()) {
      return;
    }

    Fragment existingDialog = fragmentManager.findFragmentByTag(DIALOG_TAG_LEARNING_SETTINGS);
    if (existingDialog != null && existingDialog.isAdded()) {
      return;
    }

    new DialogueLearningSettingDialog().show(fragmentManager, DIALOG_TAG_LEARNING_SETTINGS);
  }

  @Override
  public void onScriptParamsSelected(
      String level,
      String topic,
      String format,
      int length,
      int requiredCredit,
      DialogueGenerateDialog dialog) {
    if (dialog != null) {
      dialog.showLoading(true);
    }
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) {
      if (dialog != null)
        dialog.showLoading(false);
      Toast.makeText(getContext(), "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
      return;
    }

    FirebaseFirestore.getInstance()
        .collection("users")
        .document(user.getUid())
        .get()
        .addOnCompleteListener(
            task -> {
              if (!isAdded())
                return;
              if (task.isSuccessful() && task.getResult() != null) {
                DocumentSnapshot doc = task.getResult();
                Long creditObj = doc.getLong("credit");
                long credit = creditObj != null ? creditObj : 0L;
                int finalRequiredCredit = Math.max(1, requiredCredit);
                if (credit >= finalRequiredCredit) {
                  generateScriptStreaming(
                      level, topic, format, length, finalRequiredCredit, dialog);
                } else {
                  if (dialog != null)
                    dialog.dismiss();
                  Toast.makeText(getContext(), "크레딧이 부족해요", Toast.LENGTH_SHORT).show();
                  showChargeCreditDialog();
                }
              } else {
                if (dialog != null)
                  dialog.dismiss();
                Toast.makeText(getContext(), "크레딧 정보를 불러오지 못했어요.", Toast.LENGTH_SHORT).show();
              }
            });
  }

  private void generateScriptStreaming(
      @NonNull String level,
      @NonNull String topic,
      @NonNull String format,
      int length,
      int requiredCredit,
      @Nullable DialogueGenerateDialog dialog) {
    logStream(
        "prepare start: level="
            + level
            + ", topic="
            + safeText(topic)
            + ", format="
            + format
            + ", requestedLength="
            + Math.max(1, length)
            + ", requiredCredit="
            + Math.max(1, requiredCredit));
    clearPendingScriptSession(true);
    long requestId = ++scriptPreparationRequestId;
    DialogueScriptStreamingSessionStore sessionStore = LearningDependencyProvider
        .provideDialogueScriptStreamingSessionStore();

    String sessionId = sessionStore.startSession(scriptGenerator, level, topic, format, length);
    logStream("session started: requestId=" + requestId + ", sessionId=" + shortSession(sessionId));
    final boolean[] started = { false };
    final String userRequestedTopic = topic;
    final String[] streamedTopic = { userRequestedTopic };
    final int[] validTurnCount = { 0 };

    DialogueScriptStreamingSessionStore.Listener listener = new DialogueScriptStreamingSessionStore.Listener() {
      @Override
      public void onMetadata(
          @NonNull DialogueScriptStreamingSessionStore.ScriptMetadata metadata) {
        if (requestId != scriptPreparationRequestId || started[0]) {
          return;
        }
        String metadataTopic = trimToNull(metadata.getTopic());
        if (metadataTopic != null) {
          streamedTopic[0] = metadataTopic;
        }
        logStream(
            "prepare metadata: requestId="
                + requestId
                + ", topic="
                + safeText(streamedTopic[0]));
      }

      @Override
      public void onTurn(@NonNull IDialogueGenerateManager.ScriptTurnChunk turn) {
        if (requestId != scriptPreparationRequestId || started[0]) {
          return;
        }
        if (!isValidTurn(turn)) {
          return;
        }
        validTurnCount[0]++;
        logStream(
            "prepare turn: requestId="
                + requestId
                + ", validTurnCount="
                + validTurnCount[0]
                + "/"
                + MIN_TURN_COUNT_TO_START
                + ", role="
                + safeText(turn.getRole()));
        if (validTurnCount[0] >= MIN_TURN_COUNT_TO_START) {
          started[0] = true;
          logStream(
              "prepare threshold reached: requestId="
                  + requestId
                  + ", sessionId="
                  + shortSession(sessionId));
          startPreparedScriptStudy(
              dialog,
              requestId,
              level,
              Math.max(1, length),
              Math.max(1, requiredCredit),
              userRequestedTopic,
              sessionId);
        }
      }

      @Override
      public void onComplete(@Nullable String warningMessage) {
        if (requestId != scriptPreparationRequestId || started[0]) {
          return;
        }
        logStream(
            "prepare complete before start: requestId="
                + requestId
                + ", warning="
                + safeText(warningMessage));
        showScriptPreparationError(dialog, requestId);
      }

      @Override
      public void onFailure(@NonNull String error) {
        if (requestId != scriptPreparationRequestId || started[0]) {
          return;
        }
        logStream(
            "prepare failure before start: requestId="
                + requestId
                + ", error="
                + safeText(error));
        showScriptPreparationError(dialog, requestId);
      }
    };

    pendingScriptSessionId = sessionId;
    pendingScriptSessionListener = listener;
    DialogueScriptStreamingSessionStore.Snapshot snapshot = sessionStore.attach(sessionId, listener);
    if (snapshot == null) {
      logStream(
          "prepare attach failed: requestId="
              + requestId
              + ", sessionId="
              + shortSession(sessionId));
      showScriptPreparationError(dialog, requestId);
      return;
    }
    logStream(
        "prepare snapshot: requestId="
            + requestId
            + ", bufferedTurns="
            + snapshot.getBufferedTurns().size()
            + ", completed="
            + snapshot.isCompleted()
            + ", failure="
            + (trimToNull(snapshot.getFailureMessage()) != null));
    if (snapshot.getMetadata() != null) {
      String metadataTopic = trimToNull(snapshot.getMetadata().getTopic());
      if (metadataTopic != null) {
        streamedTopic[0] = metadataTopic;
      }
    }
    validTurnCount[0] = countValidTurns(snapshot.getBufferedTurns());
    if (hasStartableTurns(snapshot.getBufferedTurns())) {
      started[0] = true;
      logStream(
          "prepare threshold reached from snapshot: requestId="
              + requestId
              + ", sessionId="
              + shortSession(sessionId));
      startPreparedScriptStudy(
          dialog,
          requestId,
          level,
          Math.max(1, length),
          Math.max(1, requiredCredit),
          userRequestedTopic,
          sessionId);
      return;
    }
    if (trimToNull(snapshot.getFailureMessage()) != null || snapshot.isCompleted()) {
      logStream(
          "prepare cannot start from snapshot: requestId="
              + requestId
              + ", completed="
              + snapshot.isCompleted()
              + ", failure="
              + safeText(snapshot.getFailureMessage()));
      showScriptPreparationError(dialog, requestId);
    }
  }

  private void startPreparedScriptStudy(
      @Nullable DialogueGenerateDialog dialog,
      long requestId,
      @NonNull String level,
      int requestedLength,
      int requiredCredit,
      @NonNull String requestedTopic,
      @NonNull String sessionId) {
    logStream(
        "start learning activity: requestId="
            + requestId
            + ", level="
            + level
            + ", requestedLength="
            + requestedLength
            + ", requiredCredit="
            + Math.max(1, requiredCredit)
            + ", topic="
            + safeText(requestedTopic)
            + ", sessionId="
            + shortSession(sessionId));
    clearPendingScriptSession(false);
    finishScriptPreparation(dialog, requestId);
    if (dialog != null && dialog.isAdded()) {
      dialog.dismiss();
    }
    startScriptStudyStreaming(level, requestedLength, requiredCredit, requestedTopic, sessionId);
  }

  private void showScriptPreparationError(@Nullable DialogueGenerateDialog dialog, long requestId) {
    logStream("prepare error: requestId=" + requestId);
    clearPendingScriptSession(true);
    finishScriptPreparation(dialog, requestId);
    if (!isAdded()) {
      return;
    }
    Toast.makeText(getContext(), "대본 생성 중 오류가 발생했어요", Toast.LENGTH_SHORT).show();
  }

  private void finishScriptPreparation(@Nullable DialogueGenerateDialog dialog, long requestId) {
    if (dialog != null) {
      dialog.showLoading(false);
    }
    if (requestId == scriptPreparationRequestId) {
      scriptPreparationRequestId = 0L;
    }
  }

  private void clearPendingScriptSession(boolean releaseSession) {
    String sessionId = pendingScriptSessionId;
    DialogueScriptStreamingSessionStore.Listener listener = pendingScriptSessionListener;
    pendingScriptSessionId = null;
    pendingScriptSessionListener = null;

    if (sessionId == null) {
      return;
    }
    DialogueScriptStreamingSessionStore sessionStore = LearningDependencyProvider
        .provideDialogueScriptStreamingSessionStore();
    if (listener != null) {
      sessionStore.detach(sessionId, listener);
    }
    if (releaseSession) {
      sessionStore.release(sessionId);
    }
    logStream(
        "clear pending session: sessionId="
            + shortSession(sessionId)
            + ", release="
            + releaseSession);
  }

  private void startScriptStudyStreaming(
      @NonNull String level,
      int requestedLength,
      int requiredCredit,
      @NonNull String requestedTopic,
      @NonNull String sessionId) {
    if (!isAdded() || getActivity() == null) {
      logStream("start activity aborted: host unavailable, sessionId=" + shortSession(sessionId));
      LearningDependencyProvider.provideDialogueScriptStreamingSessionStore().release(sessionId);
      return;
    }
    int creditToDeduct = Math.max(1, requiredCredit);

    Intent intent = new Intent(getActivity(), DialogueLearningActivity.class);
    intent.putExtra(DialogueLearningActivity.EXTRA_SCRIPT_LEVEL, level);
    intent.putExtra(DialogueLearningActivity.EXTRA_SCRIPT_STREAM_SESSION_ID, sessionId);
    intent.putExtra(DialogueLearningActivity.EXTRA_REQUESTED_SCRIPT_LENGTH, requestedLength);
    intent.putExtra(DialogueLearningActivity.EXTRA_SCRIPT_TOPIC, requestedTopic);
    try {
      startActivity(intent);

      // 요청된 대본 길이에 맞춰 크레딧 차감(11줄부터 2)
      FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
      if (user != null) {
        CreditHistoryEventLogger.applyDeltaWithEvent(
            user.getUid(),
            -creditToDeduct,
            CreditHistoryEventLogger.EVENT_LEARNING_USE,
            "learning_start",
            "dialogue_select",
            CreditHistoryEventLogger.SOURCE_APP,
            new CreditHistoryEventLogger.Callback() {
              @Override
              public void onSuccess(long creditAfter) {}

              @Override
              public void onFailure(@NonNull Exception exception) {
                logStream(
                    "Failed to decrement credit("
                        + creditToDeduct
                        + "): "
                        + exception.getMessage());
              }
            });
      }

    } catch (Exception e) {
      logStream(
          "start activity failed: sessionId="
              + shortSession(sessionId)
              + ", error="
              + safeText(e.getMessage()));
      LearningDependencyProvider.provideDialogueScriptStreamingSessionStore().release(sessionId);
      if (isAdded()) {
        Toast.makeText(getContext(), "대본 화면 이동 중 오류가 발생했어요", Toast.LENGTH_SHORT).show();
      }
      return;
    }
    logStream(
        "start activity success: sessionId="
            + shortSession(sessionId)
            + ", deductedCredit="
            + creditToDeduct);
    hideKeyboard();
  }

  private boolean hasStartableTurns(
      @Nullable List<IDialogueGenerateManager.ScriptTurnChunk> turns) {
    if (turns == null || turns.size() < MIN_TURN_COUNT_TO_START) {
      return false;
    }
    int validCount = 0;
    for (IDialogueGenerateManager.ScriptTurnChunk turn : turns) {
      if (isValidTurn(turn)) {
        validCount++;
      }
      if (validCount >= MIN_TURN_COUNT_TO_START) {
        return true;
      }
    }
    return false;
  }

  private boolean isValidTurn(@Nullable IDialogueGenerateManager.ScriptTurnChunk turn) {
    if (turn == null) {
      return false;
    }
    return trimToNull(turn.getKorean()) != null && trimToNull(turn.getEnglish()) != null;
  }

  private int countValidTurns(@Nullable List<IDialogueGenerateManager.ScriptTurnChunk> turns) {
    if (turns == null || turns.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (IDialogueGenerateManager.ScriptTurnChunk turn : turns) {
      if (isValidTurn(turn)) {
        count++;
      }
    }
    return count;
  }

  @Nullable
  private static String trimToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private void logStream(@NonNull String message) {
    Log.d(TAG, "[DL_STREAM] " + message);
  }

  @NonNull
  private static String shortSession(@Nullable String sessionId) {
    String value = trimToNull(sessionId);
    if (value == null) {
      return "-";
    }
    if (value.length() <= 8) {
      return value;
    }
    return value.substring(0, 8);
  }

  @NonNull
  private static String safeText(@Nullable String value) {
    String text = trimToNull(value);
    if (text == null) {
      return "-";
    }
    if (text.length() <= 32) {
      return text;
    }
    return text.substring(0, 32) + "...";
  }

  private void shuffleAndDisplayScripts() {
    List<ScriptTemplate> shuffled = new ArrayList<>(allTemplateList);
    Collections.shuffle(shuffled, new Random());
    templateList.clear();
    int count = Math.min(DISPLAYED_SCRIPT_COUNT, shuffled.size());
    for (int i = 0; i < count; i++) {
      templateList.add(shuffled.get(i));
    }
  }

  private void startRefreshWithAnimation() {
    isRefreshing = true;

    // 1) 새로고침 버튼 360도 회전 애니메이션
    ObjectAnimator rotateAnim = ObjectAnimator.ofFloat(btnRefreshScripts, "rotation", 0f, 360f);
    rotateAnim.setDuration(REFRESH_ANIMATION_DURATION_MS);
    rotateAnim.setInterpolator(new LinearInterpolator());
    rotateAnim.start();

    // 2) 스켈레톤 모드 ON
    if (adapter != null) {
      adapter.setSkeletonMode(true);
    }

    // 3) 애니메이션 종료 후 실제 데이터 갱신
    refreshHandler.postDelayed(() -> {
      if (!isAdded())
        return;

      shuffleAndDisplayScripts();
      if (adapter != null) {
        adapter.setSkeletonMode(false);
        // templateList는 adapter 내부 templates와 같은 참조이므로
        // shuffleAndDisplayScripts()에서 이미 갱신됨 → notifyDataSetChanged만 호출
        adapter.notifyDataSetChanged();
      }

      // 레이아웃 애니메이션 재실행
      if (rvScripts != null) {
        rvScripts.scheduleLayoutAnimation();
      }

      isRefreshing = false;
    }, REFRESH_ANIMATION_DURATION_MS);
  }

  private void updateEmptyState() {
    if (templateList.isEmpty()) {
      layoutEmptyState.setVisibility(View.VISIBLE);
      rvScripts.setVisibility(View.GONE);
    } else {
      layoutEmptyState.setVisibility(View.GONE);
      rvScripts.setVisibility(View.VISIBLE);
      rvScripts.scheduleLayoutAnimation();
    }
  }

  private void hideKeyboard() {
    View view = getActivity().getCurrentFocus();
    if (view != null) {
      InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
      imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
  }

  private void showChargeCreditDialog() {
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
          navController.navigate(R.id.action_scriptSelectFragment_to_creditStoreFragment);
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
    String adUnitId = BuildConfig.DEBUG
        ? "ca-app-pub-3940256099942544/5224354917"
        : BuildConfig.ADMOB_REWARDED_AD_UNIT_ID;
    logStream("Loading rewarded ad with Unit ID: " + adUnitId);
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
            logStream("Failed to load rewarded ad: " + loadAdError.getMessage());
            rewardedAd = null;
            adRetryAttempt++;
            long retryDelayMillis = (long) Math.pow(2, Math.min(6, adRetryAttempt)) * 1000L;
            if (isWaitingForAd && adRetryAttempt <= 5 && adLifecycleActive) {
              logStream(
                  "Retrying ad load in "
                      + retryDelayMillis
                      + "ms (Attempt "
                      + adRetryAttempt
                      + ")");
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
                  Toast.makeText(
                      getContext(), "광고를 불러오는 데 실패했어요. 나중에 다시 시도해주세요.", Toast.LENGTH_SHORT)
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
            logStream("Rewarded ad loaded.");
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
            logStream("Ad failed to show: " + adError.getMessage());
          }

          @Override
          public void onAdDismissedFullScreenContent() {
            isAdShowing = false;
            if (!adLifecycleActive) {
              return;
            }
            logStream("Ad dismissed");
            if (!isRewardEarned && isAdded()) {
              Toast.makeText(getContext(), "광고 시청을 완료하지 않아 크레딧이 지급되지 않았어요", Toast.LENGTH_SHORT)
                  .show();
            }
          }
        });
  }

  private List<ScriptTemplate> loadTemplatesFromAssets() {
    List<ScriptTemplate> templates = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(
        new InputStreamReader(requireContext().getAssets().open("data/recommend_scripts.csv"), "UTF-8"))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.trim().isEmpty())
          continue;
        String[] parts = line.split(",", 3);
        if (parts.length >= 3) {
          templates.add(new ScriptTemplate(parts[0], parts[1], parts[2]));
        }
      }
    } catch (Exception e) {
      Log.e(TAG, "Error loading templates from assets", e);
    }
    return templates;
  }

  private void increaseCredit() {
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    if (user == null) {
      Toast.makeText(getContext(), "로그인 정보를 확인할 수 없어요", Toast.LENGTH_SHORT).show();
      return;
    }

    CreditHistoryEventLogger.applyDeltaWithEvent(
        user.getUid(),
        1L,
        CreditHistoryEventLogger.EVENT_AD_CHARGE,
        "ad_reward",
        "dialogue_select",
        CreditHistoryEventLogger.SOURCE_APP,
        new CreditHistoryEventLogger.Callback() {
          @Override
          public void onSuccess(long creditAfter) {
            if (isAdded()) {
              Toast.makeText(getContext(), "1 크레딧이 충전되었어요", Toast.LENGTH_SHORT).show();
            }
          }

          @Override
          public void onFailure(@NonNull Exception exception) {
            logStream("Failed to add credit: " + exception.getMessage());
            if (isAdded()) {
              Toast.makeText(getContext(), "크레딧 충전에 실패했어요", Toast.LENGTH_SHORT).show();
            }
          }
        });
  }
}
