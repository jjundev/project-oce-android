package com.jjundev.oneclickeng.learning.dialoguelearning;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.activity.DialogueLearningActivity;
import com.jjundev.oneclickeng.learning.dialoguelearning.coordinator.DialogueFeedbackCoordinator;
import com.jjundev.oneclickeng.learning.dialoguelearning.coordinator.DialoguePlaybackCoordinator;
import com.jjundev.oneclickeng.learning.dialoguelearning.coordinator.DialogueSpeakingCoordinator;
import com.jjundev.oneclickeng.learning.dialoguelearning.coordinator.DialogueSummaryCoordinator;
import com.jjundev.oneclickeng.learning.dialoguelearning.coordinator.DialogueTurnCoordinator;
import com.jjundev.oneclickeng.learning.dialoguelearning.coordinator.MicPermissionCoordinator;
import com.jjundev.oneclickeng.learning.dialoguelearning.coordinator.SpeakingSceneCoordinator;
import com.jjundev.oneclickeng.learning.dialoguelearning.di.DialogueLearningCoordinatorFactory;
import com.jjundev.oneclickeng.learning.dialoguelearning.di.LearningDependencyProvider;
import com.jjundev.oneclickeng.learning.dialoguelearning.logging.UxGateLogger;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IExtraQuestionManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISentenceFeedbackManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.ISpeakingFeedbackManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.FluencyFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SentenceFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator.LearningSessionOrchestrator;
import com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator.LearningSessionSnapshot;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.BottomSheetMode;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.ConsumableEvent;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.DialogueUiEvent;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.FeedbackUiState;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.ScriptUiState;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.SpeakingUiState;
import com.jjundev.oneclickeng.learning.dialoguelearning.summary.BookmarkedParaphrase;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.BottomSheetSceneRenderer;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.ChatAdapter;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.ChatMessage;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.LearningBottomSheetActionRouter;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.LearningBottomSheetController;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.LearningBottomSheetRenderer;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.LearningChatRenderer;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.LearningControlsRenderer;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.LearningScene;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.LearningSceneResolver;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.RecordingUiController;
import com.jjundev.oneclickeng.settings.AppSettings;
import com.jjundev.oneclickeng.settings.AppSettingsStore;
import com.jjundev.oneclickeng.tool.AudioRecorder;
import com.jjundev.oneclickeng.view.WaveformView;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class DialogueLearningFragment extends Fragment {
  // Constants
  private static final String STATE_BOOKMARKS_JSON = "state_bookmarks_json";
  private static final String STATE_BOTTOM_SHEET_LAYOUT_RES_ID = "state_bottom_sheet_layout_res_id";
  private static final String STATE_BOTTOM_SHEET_HIERARCHY = "state_bottom_sheet_hierarchy";
  private static final String STATE_HAS_DEFAULT_INPUT_SCENE_SHOWN =
      "state_has_default_input_scene_shown";
  private static final String TAG = "DialogueLearning";
  private static final String JOB_TAG = "JOB_J-20260216-005";
  private static final int MAX_RECORDING_SECONDS = 60;
  private static final long SPEAKING_STATE_TRACE_INTERVAL_MS = 1000L;
  private static final long SPEAKING_STATE_TRACE_EMISSION_STRIDE = 25L;
  private static final long FINISHED_SCENE_DELAY_MS = 500L;
  private static final long DEFAULT_INPUT_SCENE_DELAY_MS = 300L;

  // Core tracing/session identifiers
  private final long traceSessionId = System.currentTimeMillis();
  private int traceSeq = 0;
  private final UxGateLogger uxGateLogger = new UxGateLogger(traceSessionId);

  // Core handlers
  private final Handler mainHandler = new Handler(Looper.getMainLooper());

  // Dependency providers
  @Nullable private ISpeakingFeedbackManager speakingFeedbackManager;
  @Nullable private IExtraQuestionManager extraQuestionManager;
  @Nullable private ISentenceFeedbackManager sentenceFeedbackManager;

  @Nullable private DialoguePlaybackCoordinator playbackCoordinator;
  @Nullable private DialogueSpeakingCoordinator speakingCoordinator;
  @Nullable private DialogueFeedbackCoordinator feedbackCoordinator;
  @Nullable private MicPermissionCoordinator micPermissionCoordinator;
  @Nullable private SpeakingSceneCoordinator speakingSceneCoordinator;
  @Nullable private DialogueTurnCoordinator turnCoordinator;
  @Nullable private DialogueSummaryCoordinator summaryCoordinator;
  @Nullable private AppSettingsStore appSettingsStore;
  private DialogueLearningViewModel viewModel;

  private AudioRecorder audioRecorderManager;
  private ByteArrayOutputStream audioAccumulator;
  private final Gson gson = new Gson();

  // UI components
  private RecyclerView recyclerViewMessages;
  private View bottomSheet;
  private FrameLayout bottomSheetContentContainer;
  private List<ChatMessage> messageList;
  private ChatAdapter chatAdapter;
  private TextView tvListeningStatus;
  private WaveformView waveformView;
  private ProgressBar progressRing;
  private ProgressBar loadingSpinner;
  private View ripple1, ripple2, ripple3;

  private LearningBottomSheetRenderer bottomSheetRenderer;
  private LearningChatRenderer chatRenderer;
  private LearningControlsRenderer controlsRenderer;
  private LearningBottomSheetController bottomSheetController;
  private BottomSheetSceneRenderer bottomSheetSceneRenderer;
  private final RecordingUiController recordingUiController =
      new RecordingUiController(MAX_RECORDING_SECONDS);
  private final LearningSceneResolver sceneResolver = new LearningSceneResolver();

  // Audio and recording state
  private byte[] lastRecordedAudio;
  private final StringBuilder transcriptBuilder = new StringBuilder();
  private long lastWaveformUpdateMs = 0L;

  // Runtime state
  private ActivityResultLauncher<String> requestPermissionLauncher;
  private boolean isScriptLoaded = false;
  private boolean isTtsInitialized = false;
  private boolean hasNotifiedLearningSessionFinished = false;
  private String topic = "영어 연습";
  private String opponentName = "English Coach";
  private String opponentGender = "female";
  private static final String USER_GENDER_DEFAULT = "male";
  private @Nullable String pendingScriptTtsText;
  private @Nullable Runnable pendingScriptTtsCompletion;

  // Feedback / pending scene state
  private @Nullable LinkedHashMap<String, BookmarkedParaphrase> pendingRestoredBookmarks;
  private @Nullable SparseArray<Parcelable> pendingBottomSheetHierarchyState;
  private @Nullable Integer pendingBottomSheetLayoutResId;
  private @Nullable LearningSessionSnapshot currentSessionSnapshot;
  private @Nullable LearningScene lastRenderedScene;
  private @Nullable String lastRenderedSentenceKey;

  // Scene/tracing deduplication
  private long lastHandledSpeakingEmissionId = 0L;
  private long lastRenderedScriptStep = -1;
  private long lastRenderedSpeakingRequestId = -1L;
  private long lastRenderedFeedbackEmissionId = -1L;
  private long lastSpeakingStateTraceMs = 0L;
  private long lastSpeakingStateTraceEmissionId = -1L;
  private long lastSpeakingStateTraceRequestId = -1L;
  private @Nullable Runnable pendingDefaultInputPresentation;
  private boolean hasDefaultInputSceneEverShown;
  private @Nullable Runnable pendingLearningFinishedPresentation;
  private boolean hasPostLayoutSnapshotRebound;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_dialogue_learning, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    initDependenciesAndCoordinators(savedInstanceState);
    initViewModel();
    initPlaybackCoordinator();
    registerMicPermissionLauncher();
    bindViews(view);
    initRenderersAndControllers();
    restorePendingBookmarksIfNeeded();
    initChatAdapterAndRecycler();
    bindBottomSheetAndTurnCoordinator(view);
    schedulePostLayoutSnapshotRebind(view);
    loadInitialScriptFromArguments();
    logInitCompleted();
  }

  private void initDependenciesAndCoordinators(@Nullable Bundle savedInstanceState) {
    Context appContext = requireContext().getApplicationContext();
    appSettingsStore = new AppSettingsStore(appContext);
    AppSettings appSettings = appSettingsStore.getSettings();
    String effectiveApiKey = appSettings.resolveEffectiveApiKey(BuildConfig.GEMINI_API_KEY);
    speakingFeedbackManager =
        LearningDependencyProvider.provideSpeakingFeedbackManager(
            appContext, effectiveApiKey, appSettings.getLlmModelSpeaking());
    extraQuestionManager =
        LearningDependencyProvider.provideExtraQuestionManager(
            effectiveApiKey, appSettings.getLlmModelExtra());
    sentenceFeedbackManager =
        LearningDependencyProvider.provideSentenceFeedbackManager(
            appContext, effectiveApiKey, appSettings.getLlmModelSentence());
    audioRecorderManager = LearningDependencyProvider.provideAudioRecorder();
    audioAccumulator = new ByteArrayOutputStream();
    DialogueLearningCoordinatorFactory.CoordinatorBundle coordinatorBundle =
        new DialogueLearningCoordinatorFactory().create(mainHandler, buildCoordinatorFactoryHost());
    summaryCoordinator = coordinatorBundle.summaryCoordinator;
    turnCoordinator = coordinatorBundle.turnCoordinator;
    playbackCoordinator = coordinatorBundle.playbackCoordinator;
    speakingCoordinator = coordinatorBundle.speakingCoordinator;
    feedbackCoordinator = coordinatorBundle.feedbackCoordinator;
    if (playbackCoordinator != null) {
      playbackCoordinator.setGeminiTtsManager(
          LearningDependencyProvider.provideGeminiTtsManager(effectiveApiKey));
    }
    micPermissionCoordinator = new MicPermissionCoordinator(buildMicPermissionCoordinatorHost());
    speakingSceneCoordinator = new SpeakingSceneCoordinator(buildSpeakingSceneCoordinatorHost());
    restoreTransientUiState(savedInstanceState);
    restoreSummarySessionState(savedInstanceState);
    if (micPermissionCoordinator != null) {
      micPermissionCoordinator.restoreFrom(savedInstanceState);
    }
  }

  private void initViewModel() {
    if (speakingFeedbackManager == null
        || sentenceFeedbackManager == null
        || extraQuestionManager == null
        || audioRecorderManager == null) {
      return;
    }
    DialogueLearningViewModelFactory factory =
        new DialogueLearningViewModelFactory(
            speakingFeedbackManager,
            sentenceFeedbackManager,
            extraQuestionManager,
            audioRecorderManager);
    viewModel = new ViewModelProvider(this, factory).get(DialogueLearningViewModel.class);
    observeViewModel();
  }

  private void initPlaybackCoordinator() {
    if (playbackCoordinator == null) {
      return;
    }
    applyPlaybackRuntimeSettings();
    playbackCoordinator.initialize(
        requireContext(),
        ready -> {
          isTtsInitialized = ready;
          if (!ready) {
            return;
          }
          mainHandler.post(
              () -> {
                if (viewModel == null) {
                  return;
                }
                tryStartTurnFlow();
              });
        });
  }

  private void registerMicPermissionLauncher() {
    requestPermissionLauncher =
        registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
              if (speakingCoordinator != null) {
                speakingCoordinator.onPermissionResult(isGranted);
              }
              if (micPermissionCoordinator != null) {
                micPermissionCoordinator.onPermissionResult(isGranted);
              }
            });
  }

  private void bindViews(@NonNull View view) {
    recyclerViewMessages = view.findViewById(R.id.recycler_view_messages);
    bottomSheet = view.findViewById(R.id.bottom_sheet);
    bottomSheetContentContainer = view.findViewById(R.id.bottom_sheet_content_container);
  }

  private void initRenderersAndControllers() {
    bottomSheetRenderer = new LearningBottomSheetRenderer(bottomSheet, bottomSheetContentContainer);
    chatRenderer = new LearningChatRenderer(recyclerViewMessages, waveformView);
    controlsRenderer = new LearningControlsRenderer();
    bottomSheetController =
        new LearningBottomSheetController(bottomSheetRenderer, chatRenderer, controlsRenderer);
    LearningBottomSheetActionRouter actionRouter =
        new LearningBottomSheetActionRouter(
            buildBottomSheetActionDelegate(), buildBottomSheetLoggerDelegate());
    bottomSheetSceneRenderer = new BottomSheetSceneRenderer(actionRouter);
  }

  private void restorePendingBookmarksIfNeeded() {
    if (pendingRestoredBookmarks == null || feedbackCoordinator == null) {
      return;
    }
    feedbackCoordinator.restoreBookmarkedParaphrases(pendingRestoredBookmarks);
    pendingRestoredBookmarks = null;
  }

  private void initChatAdapterAndRecycler() {
    if (viewModel != null) {
      messageList = viewModel.getRetainedChatMessages();
    } else {
      messageList = new ArrayList<>();
    }
    chatAdapter =
        new ChatAdapter(
            messageList,
            this::playTts,
            this::playRecordedAudio,
            opponentName,
            opponentGender,
            recyclerViewMessages);
    LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
    recyclerViewMessages.setLayoutManager(layoutManager);
    recyclerViewMessages.setAdapter(chatAdapter);
    recyclerViewMessages.setClipToPadding(false);
    if (chatRenderer != null) {
      chatRenderer.bindAdapterIfPossible(chatAdapter);
    }
  }

  private void bindBottomSheetAndTurnCoordinator(@NonNull View view) {
    if (bottomSheetController == null) {
      return;
    }
    bottomSheetController.setup(
        view,
        bottomSheet,
        bottomSheetContentContainer,
        () -> {
          if (chatRenderer != null) {
            chatRenderer.scrollToBottom();
          }
        });
    if (turnCoordinator != null) {
      turnCoordinator.bindChatComponents(
          messageList, chatAdapter, chatRenderer, bottomSheetController);
    }
  }

  private void schedulePostLayoutSnapshotRebind(@NonNull View root) {
    if (hasPostLayoutSnapshotRebound) {
      return;
    }
    root.post(this::rebindSnapshotAfterInitialLayoutIfNeeded);
  }

  private void rebindSnapshotAfterInitialLayoutIfNeeded() {
    if (hasPostLayoutSnapshotRebound) {
      logTrace("TRACE_POST_LAYOUT_REBIND skip reason=already_rebound");
      return;
    }
    if (viewModel == null) {
      logTrace("TRACE_POST_LAYOUT_REBIND skip reason=viewModel_null");
      return;
    }
    if (!isAdded()) {
      logTrace("TRACE_POST_LAYOUT_REBIND skip reason=host_not_added");
      return;
    }

    LearningSessionSnapshot snapshot = viewModel.getSessionSnapshotState().getValue();
    if (snapshot == null) {
      logTrace("TRACE_POST_LAYOUT_REBIND skip reason=snapshot_null");
      return;
    }
    if (!shouldRebindSnapshotAfterInitialLayout(snapshot)) {
      logTrace("TRACE_POST_LAYOUT_REBIND skip reason=conditions_not_met");
      return;
    }

    logTrace("TRACE_POST_LAYOUT_REBIND start");
    forceRenderFromSnapshotAfterLayout(snapshot);
    hasPostLayoutSnapshotRebound = true;
    logTrace("TRACE_POST_LAYOUT_REBIND done");
  }

  private boolean shouldRebindSnapshotAfterInitialLayout(
      @Nullable LearningSessionSnapshot snapshot) {
    if (snapshot == null) {
      return false;
    }

    ScriptUiState scriptState = snapshot.getScriptUiState();
    boolean hasLoadedScript = scriptState != null && scriptState.getTotalSteps() > 0;
    boolean hasPendingBottomSheetRestore = pendingBottomSheetLayoutResId != null;
    return hasLoadedScript || hasPendingBottomSheetRestore;
  }

  private void forceRenderFromSnapshotAfterLayout(@NonNull LearningSessionSnapshot snapshot) {
    logTrace("TRACE_POST_LAYOUT_FORCE_RENDER reset_dedupe=true");
    resetRenderedSnapshotDedupeState();
    renderFromSessionSnapshot(snapshot);
    onBottomSheetModeChanged(snapshot.getBottomSheetMode());
  }

  private void resetRenderedSnapshotDedupeState() {
    lastRenderedScene = null;
    lastRenderedSentenceKey = null;
    lastRenderedScriptStep = -1;
    lastRenderedSpeakingRequestId = -1L;
    lastRenderedFeedbackEmissionId = -1L;
  }

  private void loadInitialScriptFromArguments() {
    if (isScriptAlreadyLoadedInViewModel()) {
      isScriptLoaded = true;
      applyScriptMetadataFromState();
      logTrace("TRACE_PARSE_SKIP reason=already_loaded");
      tryStartTurnFlow();
      return;
    }

    if (getArguments() == null) {
      return;
    }
    String streamSessionId =
        getArguments().getString(DialogueLearningActivity.EXTRA_SCRIPT_STREAM_SESSION_ID);
    if (!isBlank(streamSessionId) && viewModel != null) {
      int requestedScriptLength =
          getArguments().getInt(DialogueLearningActivity.EXTRA_REQUESTED_SCRIPT_LENGTH, 0);
      String requestedTopic = getArguments().getString(DialogueLearningActivity.EXTRA_SCRIPT_TOPIC);
      boolean attached =
          viewModel.attachScriptStreamingSession(
              streamSessionId, requestedScriptLength, requestedTopic);
      if (!attached) {
        logTrace("TRACE_STREAM_ATTACH success=false");
        abortLearningHost("대본을 불러오지 못했어요");
        return;
      }
      logTrace("TRACE_STREAM_ATTACH success=true");
      isScriptLoaded = true;
      hasNotifiedLearningSessionFinished = false;
      applyScriptMetadataFromState();
      tryStartTurnFlow();
      return;
    }

    String scriptJson = getArguments().getString("SCRIPT_DATA");
    if (scriptJson == null) {
      return;
    }
    parseScriptData(scriptJson);
  }

  private boolean isScriptAlreadyLoadedInViewModel() {
    if (viewModel == null) {
      return false;
    }
    ScriptUiState state = viewModel.getScriptUiState().getValue();
    return state != null && state.getTotalSteps() > 0;
  }

  private void logInitCompleted() {
    logTrace("TRACE_INIT");
    logTrace("TRACE_CONTROLLER_READY");
    logUx("UX_INIT_READY", "view=created");
  }

  @NonNull
  private DialogueLearningCoordinatorFactory.FactoryHost buildCoordinatorFactoryHost() {
    return new DialogueLearningCoordinatorFactory.FactoryHost() {
      @Override
      public void trace(@NonNull String key) {
        logTrace(key);
      }

      @Override
      public void gate(@NonNull String key) {
        logGate(key);
      }

      @Override
      public void ux(@NonNull String key, @Nullable String fields) {
        logUx(key, fields);
      }

      @NonNull
      @Override
      public List<SentenceFeedback> snapshotAccumulatedFeedbacks() {
        if (feedbackCoordinator != null) {
          return feedbackCoordinator.snapshotAccumulatedFeedbacks();
        }
        return new ArrayList<>();
      }

      @NonNull
      @Override
      public List<BookmarkedParaphrase> snapshotBookmarkedParaphrases() {
        if (feedbackCoordinator != null) {
          return feedbackCoordinator.snapshotBookmarkedParaphrases();
        }
        if (pendingRestoredBookmarks != null) {
          return new ArrayList<>(pendingRestoredBookmarks.values());
        }
        return new ArrayList<>();
      }

      @Override
      public boolean isSummaryHostActive() {
        return getActivity() != null;
      }

      @Override
      public void stopPlayback(@NonNull String reason) {
        safeStopPlayback(reason);
      }

      @Override
      public void navigateToSummary(
          @NonNull String summaryJson, @Nullable String featureBundleJson) {
        navigateToSummaryInternal(summaryJson, featureBundleJson);
      }

      @Nullable
      @Override
      public LearningSessionOrchestrator.TurnDecision moveToNextTurnDecision() {
        if (viewModel == null) {
          return null;
        }
        return viewModel.moveToNextTurnDecision();
      }

      @Override
      public void emitScrollChatToBottom() {
        if (viewModel == null) {
          return;
        }
        viewModel.emitScrollChatToBottom();
      }

      @Override
      public boolean isTurnHostActive() {
        return isAdded() && getContext() != null;
      }

      @Override
      public void clearFeedbackBinding() {
        if (feedbackCoordinator != null) {
          feedbackCoordinator.bindFeedbackContent(null, null);
        }
      }

      @Override
      public void requestDefaultInputSceneOnceAfterDelayIfFirstTime(
          @Nullable String sentenceToTranslate) {
        DialogueLearningFragment.this.requestDefaultInputSceneOnceAfterDelayIfFirstTime(
            sentenceToTranslate);
      }

      @Override
      public void requestLearningFinishedSceneOnceAfterDelay() {
        DialogueLearningFragment.this.requestLearningFinishedSceneOnceAfterDelay();
      }

      @Override
      public void requestScriptTtsPlayback(@NonNull String text, @NonNull Runnable onDone) {
        DialogueLearningFragment.this.requestScriptTtsPlayback(text, onDone);
      }

      @Override
      public void requestPermissionLauncher() {
        requestMicPermissionFromWhileSpeakingDelegate();
      }

      @Override
      public boolean requestPermissionViaViewModel(@NonNull String sentenceToTranslate) {
        return requestPermissionViaViewModelInternal(sentenceToTranslate);
      }

      @Override
      public void analyzeSpeaking(
          @NonNull String sentenceToTranslate,
          @NonNull byte[] audioData,
          @NonNull String recognizedText) {
        if (viewModel == null) {
          return;
        }
        viewModel.analyzeSpeaking(sentenceToTranslate, audioData, recognizedText);
      }

      @Override
      public void startSentenceFeedback(
          @NonNull String originalSentence, @NonNull String userTranscript) {
        if (viewModel == null) {
          return;
        }
        viewModel.startSentenceFeedback(originalSentence, userTranscript);
      }

      @Override
      public void askExtraQuestion(
          @NonNull String originalSentence,
          @NonNull String userSentence,
          @NonNull String question) {
        if (viewModel == null) {
          return;
        }
        viewModel.askExtraQuestion(originalSentence, userSentence, question);
      }

      @Override
      public void playTts(@NonNull String text, @Nullable ImageView speakerBtn) {
        DialogueLearningFragment.this.playTts(text, speakerBtn, USER_GENDER_DEFAULT);
      }

      @Override
      public void bindFeedbackControls(@Nullable View content, boolean showNextButton) {
        if (controlsRenderer == null || content == null) {
          return;
        }
        controlsRenderer.bindFeedbackControls(content, showNextButton);
      }

      @Override
      public void hideKeyboard(@NonNull View tokenView) {
        InputMethodManager imm =
            (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
          imm.hideSoftInputFromWindow(tokenView.getWindowToken(), 0);
        }
      }

      @Override
      public void showToast(@NonNull String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
      }
    };
  }

  private void navigateToSummaryInternal(
      @NonNull String summaryJson, @Nullable String featureBundleJson) {
    if (getActivity() == null) {
      return;
    }
    logDebugJob003("navigate to summary fragment");
    DialogueSummaryFragment target =
        DialogueSummaryFragment.newInstance(summaryJson, featureBundleJson);
    getActivity()
        .getSupportFragmentManager()
        .beginTransaction()
        .setCustomAnimations(
            R.anim.slide_in_right,
            R.anim.slide_out_left,
            R.anim.slide_in_left,
            R.anim.slide_out_right)
        .hide(DialogueLearningFragment.this)
        .add(R.id.fragment_container, target)
        .addToBackStack(null)
        .commit();
  }

  private void requestMicPermissionFromWhileSpeakingDelegate() {
    String safeSentence = "";
    if (speakingCoordinator != null) {
      String delegatePending = speakingCoordinator.consumePendingSentenceToTranslate();
      if (!isBlank(delegatePending)) {
        safeSentence = delegatePending;
        speakingCoordinator.setPendingSentenceToTranslate(delegatePending);
      }
    }
    requestMicPermission(safeSentence, "while_speaking_delegate");
  }

  private boolean requestPermissionViaViewModelInternal(@NonNull String sentenceToTranslate) {
    if (viewModel != null) {
      logTrace("TRACE_RECORD_PERMISSION_DELEGATED viewModel=true");
    }
    logTrace("TRACE_RECORD_PERMISSION_REQUEST direct=while_speaking");
    requestMicPermission(sentenceToTranslate, "while_speaking");
    if (viewModel != null) {
      viewModel.onRecordingRequested(sentenceToTranslate);
    }
    return true;
  }

  @NonNull
  private MicPermissionCoordinator.Host buildMicPermissionCoordinatorHost() {
    return new MicPermissionCoordinator.Host() {
      @Override
      public boolean isHostActive() {
        return isAdded() && getContext() != null;
      }

      @Override
      public boolean isMicPermissionGranted() {
        return DialogueLearningFragment.this.isMicPermissionGranted();
      }

      @Override
      public boolean hasMicPermissionRationale() {
        return DialogueLearningFragment.this.hasMicPermissionRationale();
      }

      @Override
      public void requestPermissionLaunch() {
        if (requestPermissionLauncher != null) {
          requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
      }

      @Override
      public void onNeedOpenSettingsDialog(@NonNull String source) {
        showMicPermissionSettingsDialog(source);
      }

      @Override
      public void onPermissionDeniedToast() {
        Toast.makeText(getContext(), "Microphone permission is required.", Toast.LENGTH_SHORT)
            .show();
      }

      @Override
      public void onStartSpeakingAfterPermission(@NonNull String sentenceToTranslate) {
        startUserSpeakingAfterPermission(sentenceToTranslate);
      }

      @Override
      public void onShowBeforeSpeakingAfterPermission(@NonNull String sentenceToTranslate) {
        presentBeforeSpeakingScene(sentenceToTranslate);
      }

      @Nullable
      @Override
      public String onConsumePendingSentenceFromSpeakingCoordinator() {
        if (speakingCoordinator == null) {
          return null;
        }
        return speakingCoordinator.consumePendingSentenceToTranslate();
      }

      @Override
      public void onSetPendingSentenceToSpeakingCoordinator(@NonNull String sentenceToTranslate) {
        if (speakingCoordinator == null) {
          return;
        }
        speakingCoordinator.setPendingSentenceToTranslate(sentenceToTranslate);
      }

      @Override
      public void trace(@NonNull String key) {
        logTrace(key);
      }

      @Override
      public void gate(@NonNull String key) {
        logGate(key);
      }

      @Override
      public void ux(@NonNull String key, @Nullable String fields) {
        logUx(key, fields);
      }
    };
  }

  @NonNull
  private SpeakingSceneCoordinator.Host buildSpeakingSceneCoordinatorHost() {
    return new SpeakingSceneCoordinator.Host() {
      @Override
      public boolean isMicPermissionGranted() {
        return DialogueLearningFragment.this.isMicPermissionGranted();
      }

      @Override
      public boolean onWhileSpeakingPermissionCheck(
          @NonNull String sentenceToTranslate, boolean hasPermission) {
        if (speakingCoordinator == null) {
          return false;
        }
        return speakingCoordinator.onWhileSpeakingPermissionCheck(
            sentenceToTranslate, hasPermission);
      }

      @Override
      public void onRecordingStarted() {
        if (speakingCoordinator != null) {
          speakingCoordinator.onRecordingStarted();
        }
        if (viewModel != null) {
          viewModel.onRecordingStarted();
        }
      }

      @Override
      public void onRecordingStopRequestedOrAnalyzeFallback(
          @NonNull String sentenceToTranslate, @NonNull byte[] audioData) {
        handleRecordingStopRequestedOrAnalyzeFallback(sentenceToTranslate, audioData);
      }

      @Override
      public boolean isSpeaking() {
        return speakingCoordinator != null && speakingCoordinator.isSpeaking();
      }

      @Override
      public boolean isRecordingActive() {
        return speakingCoordinator != null && speakingCoordinator.isRecordingActive();
      }

      @Override
      public void stopSpeakingSessionState(@NonNull String reason) {
        if (speakingCoordinator != null) {
          speakingCoordinator.stopSession(reason);
        }
      }

      @Override
      public void safeStopPlayback(@NonNull String reason) {
        DialogueLearningFragment.this.safeStopPlayback(reason);
      }

      @Override
      public void safeStopRecording(@NonNull String reason) {
        DialogueLearningFragment.this.safeStopRecording();
      }

      @Nullable
      @Override
      public AudioRecorder getAudioRecorderManager() {
        return audioRecorderManager;
      }

      @NonNull
      @Override
      public Handler getMainHandler() {
        return mainHandler;
      }

      @Nullable
      @Override
      public ByteArrayOutputStream getAudioAccumulator() {
        return audioAccumulator;
      }

      @Override
      public StringBuilder getTranscriptBuilder() {
        return transcriptBuilder;
      }

      @Override
      public void onAudioChunk(@NonNull byte[] audioData) {
        if (viewModel != null) {
          viewModel.onAudioChunk(audioData);
        }
      }

      @Override
      public void setLastRecordedAudio(@NonNull byte[] audioData) {
        lastRecordedAudio = audioData;
      }

      @Override
      public void renderBottomSheetScene(
          int layoutResId, @Nullable BottomSheetSceneRenderer.SheetBinder binder) {
        DialogueLearningFragment.this.renderBottomSheetScene(layoutResId, binder);
      }

      @Override
      public void markRenderedScene(
          @NonNull LearningScene scene, @Nullable String sentenceToRender) {
        DialogueLearningFragment.this.markRenderedScene(scene, sentenceToRender);
      }

      @Override
      public void renderWhileSpeakingContent(
          @NonNull View content, @NonNull String sentenceToTranslate) {
        bottomSheetSceneRenderer.renderWhileSpeakingContent(content, sentenceToTranslate, null);
      }

      @Override
      public void startProgressAnimation() {
        recordingUiController.startProgressAnimation();
      }

      @Override
      public void stopProgressAnimation() {
        recordingUiController.stopProgressAnimation();
      }

      @Override
      public void startRippleAnimation() {
        recordingUiController.startRippleAnimation();
      }

      @Override
      public void stopRippleAnimation() {
        recordingUiController.stopRippleAnimation();
      }

      @Override
      public void onSpeakingViewsBound(
          @Nullable ImageButton btnMic,
          @Nullable ProgressBar progress,
          @Nullable ProgressBar loading,
          @Nullable TextView listeningStatus,
          @Nullable WaveformView waveform,
          @Nullable View firstRipple,
          @Nullable View secondRipple,
          @Nullable View thirdRipple) {
        progressRing = progress;
        loadingSpinner = loading;
        tvListeningStatus = listeningStatus;
        waveformView = waveform;
        ripple1 = firstRipple;
        ripple2 = secondRipple;
        ripple3 = thirdRipple;
        recordingUiController.bindViews(progress, firstRipple, secondRipple, thirdRipple);
      }

      @Override
      public void onSpeakingViewsCleared() {
        progressRing = null;
        loadingSpinner = null;
        tvListeningStatus = null;
        waveformView = null;
        ripple1 = null;
        ripple2 = null;
        ripple3 = null;
        recordingUiController.bindViews(null, null, null, null);
      }

      @Override
      public long getLastWaveformUpdateMs() {
        return lastWaveformUpdateMs;
      }

      @Override
      public void setLastWaveformUpdateMs(long value) {
        lastWaveformUpdateMs = value;
      }

      @Override
      public void trace(@NonNull String key) {
        logTrace(key);
      }

      @Override
      public void gate(@NonNull String key) {
        logGate(key);
      }

      @Override
      public void ux(@NonNull String key, @Nullable String fields) {
        logUx(key, fields);
      }

      @Override
      public boolean tryPresentBeforeSpeakingSceneAfterNoAudio(
          @NonNull String sentenceToTranslate) {
        if (!isAdded() || getContext() == null) {
          return false;
        }
        if (isCurrentBottomSheetLayout(R.layout.bottom_sheet_content_feedback)
            || isCurrentBottomSheetLayout(R.layout.bottom_sheet_learning_finished)) {
          return false;
        }
        presentBeforeSpeakingScene(sentenceToTranslate);
        return true;
      }
    };
  }

  private void handleRecordingStopRequestedOrAnalyzeFallback(
      @NonNull String sentenceToTranslate, @NonNull byte[] audioData) {
    if (speakingCoordinator != null) {
      speakingCoordinator.onRecordingStopRequested(
          sentenceToTranslate, audioData, transcriptBuilder.toString());
      return;
    }
    logTrace("TRACE_ANALYSIS_BEGIN sentenceLen=" + sentenceToTranslate.length());
    logGate("M2_ANALYSIS_BEGIN");
    String recognizedText = transcriptBuilder.toString().trim();
    if (recognizedText.isEmpty()) {
      recognizedText = "(녹음 내용이 없습니다)";
    }
    if (viewModel != null) {
      viewModel.analyzeSpeaking(sentenceToTranslate, audioData, recognizedText);
    }
  }

  private void startUserSpeakingAfterPermission(@NonNull String sentenceToTranslate) {
    logTrace("TRACE_START_RECORDING_AFTER_PERMISSION sentenceLen=" + sentenceToTranslate.length());
    presentWhileSpeakingScene(sentenceToTranslate);
  }

  private boolean isMicPermissionGranted() {
    if (getContext() == null) {
      return false;
    }
    return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
        == PackageManager.PERMISSION_GRANTED;
  }

  private boolean hasMicPermissionRationale() {
    if (!isAdded() || getContext() == null) {
      return false;
    }
    return shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO);
  }

  private void requestMicPermission(@Nullable String sentenceToTranslate, @NonNull String source) {
    if (micPermissionCoordinator == null) {
      return;
    }
    micPermissionCoordinator.request(sentenceToTranslate, source);
  }

  private void showMicPermissionSettingsDialog(@NonNull String source) {
    if (getContext() == null || !isAdded()) {
      return;
    }
    View dialogView = inflatePermissionDialogView();
    bindPermissionDialogTexts(dialogView);
    AlertDialog dialog = createPermissionDialog(dialogView);
    bindPermissionDialogButtons(dialog, dialogView);
    bindPermissionDialogCancel(dialog);
    dialog.show();
    applyPermissionDialogWindowStyle(dialog);
    logPermissionSettingsDialogShown(source);
  }

  @NonNull
  private View inflatePermissionDialogView() {
    return getLayoutInflater().inflate(R.layout.dialog_exit_confirm, null);
  }

  private void bindPermissionDialogTexts(@NonNull View dialogView) {
    TextView headerView = dialogView.findViewById(R.id.tv_header);
    TextView messageView = dialogView.findViewById(R.id.tv_message);
    Button cancelButton = dialogView.findViewById(R.id.btn_cancel);
    Button confirmButton = dialogView.findViewById(R.id.btn_confirm);
    if (headerView != null) {
      headerView.setText("마이크 권한이 필요합니다");
    }
    if (messageView != null) {
      messageView.setText("마이크 권한이 영구 거부되어 있어 음성 기능을 실행할 수 없습니다.\n앱 설정에서 마이크 권한을 허용해 주세요.");
    }
    if (cancelButton != null) {
      cancelButton.setText("취소");
    }
    if (confirmButton != null) {
      confirmButton.setText("설정으로 이동");
    }
  }

  @NonNull
  private AlertDialog createPermissionDialog(@NonNull View dialogView) {
    return new AlertDialog.Builder(requireContext())
        .setView(dialogView)
        .setCancelable(true)
        .create();
  }

  private void bindPermissionDialogButtons(@NonNull AlertDialog dialog, @NonNull View dialogView) {
    Button cancelButton = dialogView.findViewById(R.id.btn_cancel);
    Button confirmButton = dialogView.findViewById(R.id.btn_confirm);
    if (cancelButton != null) {
      cancelButton.setOnClickListener(
          v -> {
            dialog.dismiss();
            if (speakingCoordinator != null) {
              speakingCoordinator.onPermissionResult(false);
            }
          });
    }
    if (confirmButton != null) {
      confirmButton.setOnClickListener(
          v -> {
            dialog.dismiss();
            openAppSettingsForMicrophonePermission();
          });
    }
  }

  private void bindPermissionDialogCancel(@NonNull AlertDialog dialog) {
    dialog.setOnCancelListener(
        d -> {
          if (micPermissionCoordinator != null) {
            micPermissionCoordinator.onSettingsDialogCancelled();
          }
        });
  }

  private void applyPermissionDialogWindowStyle(@NonNull AlertDialog dialog) {
    if (dialog.getWindow() == null) {
      return;
    }
    dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90);
    dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
  }

  private void logPermissionSettingsDialogShown(@NonNull String source) {
    logGate("M1_PERMISSION_OPEN_SETTINGS source=" + source);
    logUx("UX_PERMISSION_OPEN_SETTINGS", "source=" + source);
  }

  private void openAppSettingsForMicrophonePermission() {
    if (getContext() == null) {
      return;
    }
    try {
      Intent intent =
          new Intent(
              Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
              Uri.fromParts("package", requireContext().getPackageName(), null));
      startActivity(intent);
      if (micPermissionCoordinator != null) {
        micPermissionCoordinator.markSettingsOpened();
      }
      logGate("M1_PERMISSION_OPEN_SETTINGS source=app_settings");
    } catch (ActivityNotFoundException e) {
      Toast.makeText(getContext(), "설정 화면을 열 수 없습니다.", Toast.LENGTH_SHORT).show();
    }
  }

  @NonNull
  private LearningBottomSheetActionRouter.ActionDelegate buildBottomSheetActionDelegate() {
    return new LearningBottomSheetActionRouter.ActionDelegate() {
      @Override
      public void hideKeyboard() {
        DialogueLearningFragment.this.hideKeyboard();
      }

      @Override
      public void presentBeforeSpeakingScene(@Nullable String sentenceToTranslate) {
        DialogueLearningFragment.this.presentBeforeSpeakingScene(sentenceToTranslate);
      }

      @Override
      public void requestDefaultInputSceneOnceAfterDelayIfFirstTime(
          @Nullable String sentenceToTranslate) {
        DialogueLearningFragment.this.requestDefaultInputSceneOnceAfterDelayIfFirstTime(
            sentenceToTranslate);
      }

      @Override
      public void handleBeforeSpeakToRecord(@Nullable String sentenceToTranslate) {
        DialogueLearningFragment.this.handleBeforeSpeakToRecord(sentenceToTranslate);
      }

      @Override
      public void stopSpeakingSession() {
        DialogueLearningFragment.this.stopSpeakingSession();
      }

      @Override
      public void invalidatePendingSpeakingAnalysis() {
        if (viewModel != null) {
          viewModel.invalidateSpeakingRequest();
        }
      }

      @Override
      public void presentFeedbackSceneForDefaultInput(
          @Nullable String originalSentence, @Nullable String translatedSentence) {
        DialogueLearningFragment.this.presentFeedbackScene(
            null, originalSentence, translatedSentence, false);
      }

      @Override
      public void handleNextStepSequence(@Nullable String userMessage, @Nullable byte[] audioData) {
        DialogueLearningFragment.this.handleNextStepSequence(userMessage, audioData);
      }

      @Override
      public void clearLastRecordedAudio() {
        lastRecordedAudio = null;
      }

      @Override
      public void emitOpenSummaryOrFallback() {
        DialogueLearningFragment.this.emitOpenSummaryOrFallback();
      }

      @Override
      public void playRecordedAudio(@Nullable byte[] audio, @Nullable ImageView speakerButton) {
        DialogueLearningFragment.this.playRecordedAudio(audio, speakerButton);
      }

      @Override
      public void requestMicPermission(
          @Nullable String sentenceToTranslate, @NonNull String source) {
        DialogueLearningFragment.this.requestMicPermission(sentenceToTranslate, source);
      }

      @Override
      public void stopRippleAnimation() {
        recordingUiController.stopRippleAnimation();
      }
    };
  }

  private void emitOpenSummaryOrFallback() {
    if (viewModel != null) {
      viewModel.emitOpenSummary();
      return;
    }
    openSummaryFragment("user_click");
  }

  @NonNull
  private LearningBottomSheetActionRouter.LoggerDelegate buildBottomSheetLoggerDelegate() {
    return new LearningBottomSheetActionRouter.LoggerDelegate() {
      @Override
      public void trace(@NonNull String key) {
        logTrace(key);
      }

      @Override
      public void gate(@NonNull String key) {
        logGate(key);
      }

      @Override
      public void ux(@NonNull String key, @Nullable String fields) {
        logUx(key, fields);
      }
    };
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);

    LinkedHashMap<String, BookmarkedParaphrase> bookmarks = new LinkedHashMap<>();
    if (feedbackCoordinator != null) {
      bookmarks.putAll(feedbackCoordinator.copyBookmarkedParaphrasesForState());
    } else if (pendingRestoredBookmarks != null) {
      bookmarks.putAll(pendingRestoredBookmarks);
    }
    outState.putString(STATE_BOOKMARKS_JSON, gson.toJson(bookmarks));
    if (micPermissionCoordinator != null) {
      micPermissionCoordinator.saveTo(outState);
    }

    if (summaryCoordinator != null) {
      summaryCoordinator.saveState(outState, gson);
    }

    outState.putBoolean(STATE_HAS_DEFAULT_INPUT_SCENE_SHOWN, hasDefaultInputSceneEverShown);
    saveBottomSheetHierarchyState(outState);
  }

  @Override
  public void onResume() {
    super.onResume();
    applyPlaybackRuntimeSettings();
    applyMuteStateIfEnabled();
    if (micPermissionCoordinator != null) {
      micPermissionCoordinator.onResume();
    }
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    logExitIfActivityFinishing();
    stopSessionAndResetRenderState();
    clearScheduledUiTasks();
    releaseCoordinators();
    releaseUiControllers();
    releaseAudioState();
    clearViewModelRequestsIfNeeded();
    clearTransientReferences();
  }

  private void logExitIfActivityFinishing() {
    if (getActivity() != null && getActivity().isFinishing()) {
      logGate("M4_BACK_TO_MAIN");
    }
  }

  private void stopSessionAndResetRenderState() {
    safeStopPlayback("onDestroyView");
    stopSpeakingSession();
    currentSessionSnapshot = null;
    lastRenderedScene = null;
    lastRenderedSentenceKey = null;
  }

  private void clearScheduledUiTasks() {
    if (pendingDefaultInputPresentation != null) {
      mainHandler.removeCallbacks(pendingDefaultInputPresentation);
      pendingDefaultInputPresentation = null;
    }
    if (pendingLearningFinishedPresentation != null) {
      mainHandler.removeCallbacks(pendingLearningFinishedPresentation);
      pendingLearningFinishedPresentation = null;
    }
    mainHandler.removeCallbacksAndMessages(null);
  }

  private void releaseCoordinators() {
    if (turnCoordinator != null) {
      turnCoordinator.release();
      turnCoordinator = null;
    }
    if (playbackCoordinator != null) {
      playbackCoordinator.release();
      playbackCoordinator = null;
    }
    if (speakingCoordinator != null) {
      speakingCoordinator.release();
      speakingCoordinator = null;
    }
    if (speakingSceneCoordinator != null) {
      speakingSceneCoordinator.release();
      speakingSceneCoordinator = null;
    }
    if (feedbackCoordinator != null) {
      feedbackCoordinator.release();
      feedbackCoordinator = null;
    }
    if (summaryCoordinator != null) {
      summaryCoordinator.release();
      summaryCoordinator = null;
    }
    micPermissionCoordinator = null;
  }

  private void releaseUiControllers() {
    if (bottomSheetController != null) {
      bottomSheetController.onDestroy();
      bottomSheetController = null;
    }
    recordingUiController.clear();
    if (controlsRenderer != null) {
      controlsRenderer.stopHandlerCallbacks();
      controlsRenderer = null;
    }
    bottomSheetRenderer = null;
    chatRenderer = null;
  }

  private void releaseAudioState() {
    if (audioRecorderManager != null) {
      safeStopRecording();
      audioRecorderManager = null;
    }
    if (audioAccumulator != null) {
      audioAccumulator.reset();
      audioAccumulator = null;
    }
    lastRecordedAudio = null;
  }

  private void clearViewModelRequestsIfNeeded() {
    if (viewModel != null && !isHostChangingConfigurations()) {
      viewModel.clearRequests();
    }
  }

  private boolean isHostChangingConfigurations() {
    return getActivity() != null && getActivity().isChangingConfigurations();
  }

  private void clearTransientReferences() {
    hasDefaultInputSceneEverShown = false;
    hasPostLayoutSnapshotRebound = false;
    pendingScriptTtsCompletion = null;
    pendingScriptTtsText = null;
    pendingBottomSheetHierarchyState = null;
    pendingBottomSheetLayoutResId = null;
    appSettingsStore = null;
  }

  private void tryStartTurnFlow() {
    if (turnCoordinator == null) {
      return;
    }
    if (!shouldStartTurnFlow()) {
      logTrace("TRACE_TRY_START_FLOW skip reason=restored_session");
      return;
    }
    turnCoordinator.tryStartTurnFlow(isScriptLoaded, isTtsInitialized);
  }

  private boolean shouldStartTurnFlow() {
    if (!isScriptLoaded || !isTtsInitialized) {
      return true;
    }
    if (viewModel == null) {
      return true;
    }
    ScriptUiState state = viewModel.getScriptUiState().getValue();
    if (state == null || state.getTotalSteps() <= 0) {
      return true;
    }
    return !state.isFinished() && state.getCurrentStep() == 0 && state.getActiveTurn() == null;
  }

  private void observeViewModel() {
    if (viewModel == null) {
      return;
    }

    viewModel
        .getSessionSnapshotState()
        .observe(getViewLifecycleOwner(), this::renderFromSessionSnapshot);
    viewModel.getUiEvent().observe(getViewLifecycleOwner(), this::onUiEventReceived);
  }

  private static final class SceneRenderContext {
    @NonNull private final LearningScene scene;
    @Nullable private final String sentenceToRender;
    private final boolean forceRebind;
    @Nullable private final String sceneKey;

    private SceneRenderContext(
        @NonNull LearningScene scene,
        @Nullable String sentenceToRender,
        boolean forceRebind,
        @Nullable String sceneKey) {
      this.scene = scene;
      this.sentenceToRender = sentenceToRender;
      this.forceRebind = forceRebind;
      this.sceneKey = sceneKey;
    }
  }

  private void renderFromSessionSnapshot(@Nullable LearningSessionSnapshot snapshot) {
    if (snapshot == null) {
      return;
    }
    updateCurrentSnapshot(snapshot);
    renderSnapshotSceneIfNeeded(snapshot);
    applySnapshotStateBindings(snapshot);
    bindSnapshotToRenderers(snapshot);
  }

  private boolean shouldRenderSnapshot(@NonNull LearningSessionSnapshot snapshot) {
    SceneRenderContext context = resolveRenderContext(snapshot);
    int currentStep = snapshot.getScriptUiState().getCurrentStep();
    long speakingRequestId = snapshot.getSpeakingUiState().getRequestId();
    if (isSceneOrTurnChanged(context.scene, context.forceRebind, currentStep, speakingRequestId)) {
      return true;
    }
    long feedbackEmissionId = snapshot.getFeedbackUiState().getEmissionId();
    return isFeedbackCompletionReRenderNeeded(snapshot, context.scene, feedbackEmissionId);
  }

  private void markRenderedSnapshotState(@NonNull LearningSessionSnapshot snapshot) {
    lastRenderedScriptStep = snapshot.getScriptUiState().getCurrentStep();
    lastRenderedSpeakingRequestId = snapshot.getSpeakingUiState().getRequestId();
    lastRenderedFeedbackEmissionId = snapshot.getFeedbackUiState().getEmissionId();
  }

  private void renderScene(@NonNull LearningSessionSnapshot snapshot) {
    SceneRenderContext context = resolveRenderContext(snapshot);
    logSceneRenderDecision(context);
    applySceneDispatchAndMode(snapshot, context);
    rememberRenderedSceneState(context);
  }

  private void updateCurrentSnapshot(@NonNull LearningSessionSnapshot snapshot) {
    currentSessionSnapshot = snapshot;
  }

  private void renderSnapshotSceneIfNeeded(@NonNull LearningSessionSnapshot snapshot) {
    if (!shouldRenderSnapshot(snapshot)) {
      return;
    }
    logTrace("TRACE_RENDER_SNAPSHOT");
    renderScene(snapshot);
  }

  private void applySnapshotStateBindings(@NonNull LearningSessionSnapshot snapshot) {
    onScriptUiStateChanged(snapshot.getScriptUiState());
    onSpeakingUiStateChanged(snapshot.getSpeakingUiState());
    onFeedbackUiStateChanged(snapshot.getFeedbackUiState());
    markRenderedSnapshotState(snapshot);
    renderSentenceFeedbackControls(snapshot.getFeedbackUiState());
  }

  private void bindSnapshotToRenderers(@NonNull LearningSessionSnapshot snapshot) {
    if (chatRenderer != null) {
      chatRenderer.bindFromSnapshot(snapshot);
    }
    if (controlsRenderer != null) {
      controlsRenderer.bindFromSnapshot(getBottomSheetContentHost(), snapshot);
    }
  }

  private boolean isSceneOrTurnChanged(
      @NonNull LearningScene scene, boolean forceRebind, int currentStep, long speakingRequestId) {
    return scene != lastRenderedScene
        || forceRebind
        || currentStep != lastRenderedScriptStep
        || speakingRequestId != lastRenderedSpeakingRequestId;
  }

  private boolean isFeedbackCompletionReRenderNeeded(
      @NonNull LearningSessionSnapshot snapshot,
      @NonNull LearningScene scene,
      long feedbackEmissionId) {
    if (scene == LearningScene.WHILE_SPEAKING) {
      return false;
    }
    return scene == LearningScene.FEEDBACK
        && feedbackEmissionId != lastRenderedFeedbackEmissionId
        && snapshot.getFeedbackUiState().isCompleted();
  }

  @NonNull
  private SceneRenderContext resolveRenderContext(@NonNull LearningSessionSnapshot snapshot) {
    LearningScene scene = sceneResolver.resolve(snapshot);
    String sentenceToRender = resolveSceneSentence(snapshot);
    boolean forceRebind = shouldForceSceneRebind(scene, sentenceToRender);
    String sceneKey = normalizeSceneSentenceKey(sentenceToRender);
    return new SceneRenderContext(scene, sentenceToRender, forceRebind, sceneKey);
  }

  private void logSceneRenderDecision(@NonNull SceneRenderContext context) {
    logTrace(
        "TRACE_SCENE_RENDER scene="
            + context.scene
            + " forceRebind="
            + context.forceRebind
            + " sentenceKey="
            + context.sceneKey
            + " lastScene="
            + lastRenderedScene
            + " lastSentenceKey="
            + lastRenderedSentenceKey);
  }

  private void applySceneDispatchAndMode(
      @NonNull LearningSessionSnapshot snapshot, @NonNull SceneRenderContext context) {
    if (context.forceRebind) {
      dispatchSceneRender(snapshot, context.scene, context.sentenceToRender, true);
    }
    if (context.scene != lastRenderedScene || context.forceRebind) {
      onBottomSheetModeChanged(snapshot.getBottomSheetMode());
    }
  }

  private void rememberRenderedSceneState(@NonNull SceneRenderContext context) {
    lastRenderedScene = context.scene;
    lastRenderedSentenceKey = normalizeSceneSentenceKey(context.sentenceToRender);
  }

  private void dispatchSceneRender(
      @NonNull LearningSessionSnapshot snapshot,
      @NonNull LearningScene scene,
      @Nullable String sentenceToRender,
      boolean forceRebind) {
    if (scene == LearningScene.FEEDBACK) {
      if (forceRebind || !isCurrentBottomSheetLayout(R.layout.bottom_sheet_content_feedback)) {
        presentFeedbackSceneFromSnapshot(snapshot, sentenceToRender);
      }
      return;
    }

    if (scene == LearningScene.BEFORE_SPEAKING && isOpponentTurnSnapshot(snapshot)) {
      showEmptyBottomSheetForOpponentTurn();
      return;
    }

    if ((scene == LearningScene.DEFAULT_INPUT || scene == LearningScene.WHILE_SPEAKING)
        && isBlank(sentenceToRender)) {
      return;
    }

    renderSceneContent(scene, sentenceToRender == null ? "" : sentenceToRender, forceRebind);
  }

  private boolean isOpponentTurnSnapshot(@NonNull LearningSessionSnapshot snapshot) {
    ScriptUiState scriptState = snapshot.getScriptUiState();
    return scriptState != null
        && scriptState.getActiveTurn() != null
        && scriptState.getActiveTurn().isOpponentTurn();
  }

  private void showEmptyBottomSheetForOpponentTurn() {
    if (bottomSheetController == null) {
      return;
    }
    bottomSheetController.setVisible(true);
    bottomSheetController.clearContent();
  }

  private void renderSceneContent(
      @NonNull LearningScene scene, @NonNull String sentenceToRender, boolean forceRebind) {
    logTrace(
        "TRACE_SCENE_RENDER_CONTENT scene="
            + scene
            + " forceRebind="
            + forceRebind
            + " sentenceKey="
            + normalizeSceneSentenceKey(sentenceToRender));
    switch (scene) {
      case DEFAULT_INPUT:
        if (forceRebind || !isCurrentBottomSheetLayout(R.layout.bottom_sheet_content_default)) {
          requestDefaultInputSceneOnceAfterDelayIfFirstTime(sentenceToRender);
        }
        break;
      case BEFORE_SPEAKING:
        if (forceRebind
            || !isCurrentBottomSheetLayout(R.layout.bottom_sheet_content_before_speaking)) {
          presentBeforeSpeakingScene(sentenceToRender);
        }
        break;
      case WHILE_SPEAKING:
        if (forceRebind
            || !isCurrentBottomSheetLayout(R.layout.bottom_sheet_content_while_speaking)) {
          presentWhileSpeakingScene(sentenceToRender);
        }
        break;
      case FINISHED:
        if (forceRebind || !isCurrentBottomSheetLayout(R.layout.bottom_sheet_learning_finished)) {
          requestLearningFinishedSceneOnceAfterDelay();
        }
        break;
      case FEEDBACK:
      default:
        break;
    }
  }

  private void presentFeedbackSceneFromSnapshot(
      @NonNull LearningSessionSnapshot snapshot, @Nullable String sceneSentence) {
    SpeakingUiState speakingState = snapshot.getSpeakingUiState();
    FeedbackUiState feedbackState = snapshot.getFeedbackUiState();
    SentenceFeedback fullFeedback = feedbackState.getFullFeedback();

    String sentenceToTranslate =
        firstNonBlank(
            speakingState.getOriginalSentence(),
            fullFeedback == null ? null : fullFeedback.getOriginalSentence(),
            sceneSentence,
            resolveSentenceFromScriptState(snapshot.getScriptUiState()));

    String translatedSentence =
        firstNonBlank(
            speakingState.getRecognizedText(),
            fullFeedback == null ? null : fullFeedback.getUserSentence(),
            resolveTranslatedSentenceFromCurrentBottomSheet());

    FluencyFeedback result = speakingState.getFluencyResult();
    presentFeedbackScene(
        result,
        sentenceToTranslate == null ? "" : sentenceToTranslate,
        translatedSentence == null ? "" : translatedSentence,
        result != null);
  }

  @Nullable
  private String firstNonBlank(@Nullable String... candidates) {
    if (candidates == null) {
      return null;
    }
    for (String candidate : candidates) {
      if (!isBlank(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  private boolean shouldForceSceneRebind(
      @NonNull LearningScene scene, @Nullable String sentenceToRender) {
    if (scene != lastRenderedScene) {
      return true;
    }
    String sentenceKey = normalizeSceneSentenceKey(sentenceToRender);
    return sentenceKey != null && !sentenceKey.equals(lastRenderedSentenceKey);
  }

  @Nullable
  private String normalizeSceneSentenceKey(@Nullable String sentence) {
    if (isBlank(sentence)) {
      return null;
    }
    return sentence.trim();
  }

  private boolean isCurrentBottomSheetLayout(@LayoutRes int layoutResId) {
    View content = getBottomSheetContentHost();
    if (content == null) {
      return false;
    }
    return content.getTag() instanceof Integer && ((Integer) content.getTag()) == layoutResId;
  }

  private void markRenderedScene(@NonNull LearningScene scene, @Nullable String sentenceToRender) {
    lastRenderedScene = scene;
    lastRenderedSentenceKey = normalizeSceneSentenceKey(sentenceToRender);
  }

  private void onBottomSheetModeChanged(@Nullable BottomSheetMode mode) {
    if (mode == null || bottomSheetController == null) {
      return;
    }
    bottomSheetController.bindFromSnapshot(mode);
  }

  private void syncBottomSheetMode(@NonNull BottomSheetMode mode, @NonNull String source) {
    if (viewModel == null) {
      return;
    }
    logTrace("TRACE_MODE_SYNC target=" + mode + " source=" + source);
    viewModel.setBottomSheetMode(mode);
  }

  private void onScriptUiStateChanged(@Nullable ScriptUiState state) {
    if (state == null) {
      return;
    }

    topic = state.getTopic();
    opponentName = state.getOpponentName();
    opponentGender = state.getOpponentGender();

    if (progressListener != null) {
      progressListener.onMetadataLoaded(topic, opponentName);
      progressListener.onProgressUpdate(state.getCurrentStep(), state.getTotalSteps());
    }
    if (state.isFinished() && !hasNotifiedLearningSessionFinished) {
      hasNotifiedLearningSessionFinished = true;
      if (progressListener != null) {
        progressListener.onLearningSessionFinished();
      }
    }
    if (chatAdapter != null) {
      chatAdapter.updateOpponentProfile(opponentName, opponentGender);
    }
  }

  private void onSpeakingUiStateChanged(@Nullable SpeakingUiState state) {
    if (shouldIgnoreSpeakingEmission(state)) {
      return;
    }
    lastHandledSpeakingEmissionId = state.getEmissionId();
    cacheLastRecordedAudioIfPresent(state);
    long now = SystemClock.elapsedRealtime();
    if (!shouldTraceSpeakingState(state, now)) {
      return;
    }
    traceSpeakingState(state, now);
    if (handleSpeakingErrorState(state)) {
      return;
    }
    handleSpeakingSuccessState(state);
  }

  private boolean shouldIgnoreSpeakingEmission(@Nullable SpeakingUiState state) {
    return state == null || state.getEmissionId() <= lastHandledSpeakingEmissionId;
  }

  private void cacheLastRecordedAudioIfPresent(@NonNull SpeakingUiState state) {
    if (state.getLastRecordedAudio() != null) {
      lastRecordedAudio = state.getLastRecordedAudio();
    }
  }

  private boolean shouldTraceSpeakingState(@NonNull SpeakingUiState state, long now) {
    boolean isRecording = state.isRecording();
    boolean isAnalyzing = state.isAnalyzing();
    boolean isTerminalSignal =
        state.getError() != null || state.getFluencyResult() != null || isAnalyzing;
    long emissionDelta = Math.max(0L, state.getEmissionId() - lastSpeakingStateTraceEmissionId);
    boolean requestChanged = state.getRequestId() != lastSpeakingStateTraceRequestId;
    boolean shouldTraceByRate = now - lastSpeakingStateTraceMs >= SPEAKING_STATE_TRACE_INTERVAL_MS;
    boolean shouldTraceByStride = emissionDelta >= SPEAKING_STATE_TRACE_EMISSION_STRIDE;
    return isRecording
        ? (requestChanged || shouldTraceByRate || shouldTraceByStride)
        : isTerminalSignal;
  }

  private void traceSpeakingState(@NonNull SpeakingUiState state, long now) {
    logTrace(
        "TRACE_SPEAKING_STATE emissionId="
            + state.getEmissionId()
            + " recording="
            + state.isRecording()
            + " analyzing="
            + state.isAnalyzing()
            + " req="
            + state.getRequestId());
    lastSpeakingStateTraceMs = now;
    lastSpeakingStateTraceEmissionId = state.getEmissionId();
    lastSpeakingStateTraceRequestId = state.getRequestId();
  }

  private boolean handleSpeakingErrorState(@NonNull SpeakingUiState state) {
    if (state.getError() == null) {
      return false;
    }
    logTrace("TRACE_ANALYSIS_ERROR msgLen=" + state.getError().length());
    if (speakingCoordinator != null) {
      speakingCoordinator.onSpeakingStateError(state.getRequestId(), state.getError().length());
    }
    if (tvListeningStatus != null) {
      tvListeningStatus.setText("분석 실패: " + state.getError());
      tvListeningStatus.setTextColor(
          ContextCompat.getColor(tvListeningStatus.getContext(), R.color.state_error));
    }
    if (loadingSpinner != null) {
      loadingSpinner.setVisibility(View.GONE);
    }
    stopSpeakingSession();
    return true;
  }

  private void handleSpeakingSuccessState(@NonNull SpeakingUiState state) {
    if (state.getFluencyResult() == null || state.getOriginalSentence() == null) {
      return;
    }
    String recognizedText = state.getRecognizedText() == null ? "" : state.getRecognizedText();
    logTrace(
        "TRACE_ANALYSIS_RESULT originalLen="
            + state.getOriginalSentence().length()
            + " recognizedLen="
            + recognizedText.length());
    if (speakingCoordinator != null) {
      speakingCoordinator.onSpeakingStateSuccess(
          state.getRequestId(), state.getOriginalSentence().length(), recognizedText.length());
    }
    transitionToCompleteState(
        state.getFluencyResult(), state.getOriginalSentence(), recognizedText);
  }

  private void onFeedbackUiStateChanged(@Nullable FeedbackUiState state) {
    if (feedbackCoordinator == null) {
      return;
    }
    feedbackCoordinator.onFeedbackUiStateChanged(state);
  }

  private void renderSentenceFeedbackControls(@NonNull FeedbackUiState feedbackState) {
    if (feedbackCoordinator == null) {
      return;
    }
    feedbackCoordinator.renderSentenceFeedbackControls(feedbackState);
  }

  private void onUiEventReceived(@Nullable ConsumableEvent<DialogueUiEvent> event) {
    if (event == null) {
      return;
    }
    DialogueUiEvent consumedEvent = consumeUiEvent(event);
    if (consumedEvent == null) {
      return;
    }
    if (handleRequestMicPermissionEvent(consumedEvent)) {
      return;
    }
    if (handleShowToastEvent(consumedEvent)) {
      return;
    }
    if (handleScrollChatToBottomEvent(consumedEvent)) {
      return;
    }
    if (handleOpenSummaryEvent(consumedEvent)) {
      return;
    }
    if (handleAdvanceTurnEvent(consumedEvent)) {
      return;
    }
    if (handleAbortLearningEvent(consumedEvent)) {
      return;
    }
    handlePlayScriptTtsEvent(consumedEvent);
  }

  @Nullable
  private DialogueUiEvent consumeUiEvent(@NonNull ConsumableEvent<DialogueUiEvent> event) {
    return event.consumeIfNotHandled();
  }

  private boolean handleRequestMicPermissionEvent(@NonNull DialogueUiEvent consumedEvent) {
    if (!(consumedEvent instanceof DialogueUiEvent.RequestMicPermission)) {
      return false;
    }
    DialogueUiEvent.RequestMicPermission request =
        (DialogueUiEvent.RequestMicPermission) consumedEvent;
    if (micPermissionCoordinator != null && micPermissionCoordinator.isRequestInFlight()) {
      logTrace("TRACE_PERMISSION_EVENT_IGNORED source=ui_event");
      return true;
    }
    requestMicPermission(request.getSentenceToTranslate(), "ui_event");
    return true;
  }

  private boolean handleShowToastEvent(@NonNull DialogueUiEvent consumedEvent) {
    if (!(consumedEvent instanceof DialogueUiEvent.ShowToast)) {
      return false;
    }
    DialogueUiEvent.ShowToast toastEvent = (DialogueUiEvent.ShowToast) consumedEvent;
    logGate("M1_TOAST_SHOWN source=ui_event");
    Toast.makeText(getContext(), toastEvent.getMessage(), Toast.LENGTH_SHORT).show();
    return true;
  }

  private boolean handleScrollChatToBottomEvent(@NonNull DialogueUiEvent consumedEvent) {
    if (!(consumedEvent instanceof DialogueUiEvent.ScrollChatToBottom)) {
      return false;
    }
    if (chatRenderer != null) {
      chatRenderer.scrollToBottom();
    }
    return true;
  }

  private boolean handleOpenSummaryEvent(@NonNull DialogueUiEvent consumedEvent) {
    if (!(consumedEvent instanceof DialogueUiEvent.OpenSummary)) {
      return false;
    }
    logTrace("TRACE_SUMMARY_TRIGGER reason=user_click");
    openSummaryFragment("user_click");
    return true;
  }

  private boolean handleAdvanceTurnEvent(@NonNull DialogueUiEvent consumedEvent) {
    if (!(consumedEvent instanceof DialogueUiEvent.AdvanceTurn)) {
      return false;
    }
    if (turnCoordinator != null) {
      turnCoordinator.processNextScriptStep();
    }
    return true;
  }

  private boolean handleAbortLearningEvent(@NonNull DialogueUiEvent consumedEvent) {
    if (!(consumedEvent instanceof DialogueUiEvent.AbortLearning)) {
      return false;
    }
    DialogueUiEvent.AbortLearning abortEvent = (DialogueUiEvent.AbortLearning) consumedEvent;
    abortLearningHost(abortEvent.getMessage());
    return true;
  }

  private void handlePlayScriptTtsEvent(@NonNull DialogueUiEvent consumedEvent) {
    if (!(consumedEvent instanceof DialogueUiEvent.PlayScriptTts)) {
      return;
    }
    if (pendingScriptTtsCompletion == null || pendingScriptTtsText == null) {
      return;
    }
    DialogueUiEvent.PlayScriptTts playScriptTtsEvent =
        (DialogueUiEvent.PlayScriptTts) consumedEvent;
    if (!pendingScriptTtsText.equals(playScriptTtsEvent.getText())) {
      return;
    }
    Runnable completion = pendingScriptTtsCompletion;
    pendingScriptTtsCompletion = null;
    pendingScriptTtsText = null;
    playScriptTts(playScriptTtsEvent.getText(), completion);
  }

  private void restoreSummarySessionState(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState == null) {
      return;
    }

    try {
      String bookmarksJson = savedInstanceState.getString(STATE_BOOKMARKS_JSON);
      if (bookmarksJson != null && !bookmarksJson.trim().isEmpty()) {
        Type mapType = new TypeToken<LinkedHashMap<String, BookmarkedParaphrase>>() {}.getType();
        LinkedHashMap<String, BookmarkedParaphrase> restored =
            gson.fromJson(bookmarksJson, mapType);
        pendingRestoredBookmarks = restored == null ? new LinkedHashMap<>() : restored;
      }
    } catch (Exception e) {
      Log.w(TAG, "Failed to restore paraphrasing bookmarks", e);
    }

    if (summaryCoordinator != null) {
      summaryCoordinator.restoreState(savedInstanceState, gson);
    }
  }

  private void restoreTransientUiState(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState == null) {
      return;
    }
    hasDefaultInputSceneEverShown =
        savedInstanceState.getBoolean(
            STATE_HAS_DEFAULT_INPUT_SCENE_SHOWN, hasDefaultInputSceneEverShown);

    int savedLayoutResId = savedInstanceState.getInt(STATE_BOTTOM_SHEET_LAYOUT_RES_ID, 0);
    pendingBottomSheetLayoutResId = savedLayoutResId == 0 ? null : savedLayoutResId;
    pendingBottomSheetHierarchyState =
        savedInstanceState.getSparseParcelableArray(STATE_BOTTOM_SHEET_HIERARCHY);
  }

  private void saveBottomSheetHierarchyState(@NonNull Bundle outState) {
    View content = getBottomSheetContentHost();
    if (content == null || !(content.getTag() instanceof Integer)) {
      return;
    }
    SparseArray<Parcelable> hierarchyState = new SparseArray<>();
    content.saveHierarchyState(hierarchyState);
    outState.putInt(STATE_BOTTOM_SHEET_LAYOUT_RES_ID, (Integer) content.getTag());
    outState.putSparseParcelableArray(STATE_BOTTOM_SHEET_HIERARCHY, hierarchyState);
  }

  private void restoreBottomSheetHierarchyIfNeeded(
      @LayoutRes int layoutResId, @NonNull View content) {
    if (pendingBottomSheetLayoutResId == null
        || pendingBottomSheetHierarchyState == null
        || pendingBottomSheetLayoutResId != layoutResId) {
      return;
    }
    content.restoreHierarchyState(pendingBottomSheetHierarchyState);
    pendingBottomSheetLayoutResId = null;
    pendingBottomSheetHierarchyState = null;
  }

  @Nullable
  private View getBottomSheetContentHost() {
    return bottomSheetController == null ? null : bottomSheetController.getCurrentContent();
  }

  private boolean isBlank(@Nullable String text) {
    return text == null || text.trim().isEmpty();
  }

  private void renderBottomSheetScene(
      @LayoutRes int layoutResId, @Nullable BottomSheetSceneRenderer.SheetBinder sheetBinder) {
    if (bottomSheetController == null) {
      return;
    }
    boolean autoChange = !isCurrentBottomSheetLayout(layoutResId);
    boolean skipStateAnimation = shouldSkipBottomSheetStateAnimation(layoutResId);
    logTrace(
        "TRACE_BS_RENDER layout="
            + layoutResId
            + " autoChange="
            + autoChange
            + " skipStateAnimation="
            + skipStateAnimation);

    bottomSheetController.changeContent(
        () -> {
          View content =
              bottomSheetController.replaceOrReuseContent(
                  layoutResId, bottomSheetSceneRenderer, () -> {});
          if (content == null) {
            return;
          }
          if (sheetBinder != null) {
            sheetBinder.bind(content);
          }
          restoreBottomSheetHierarchyIfNeeded(layoutResId, content);
        },
        skipStateAnimation);
  }

  private boolean shouldSkipBottomSheetStateAnimation(@LayoutRes int nextLayoutResId) {
    boolean isBeforeToWhile =
        isCurrentBottomSheetLayout(R.layout.bottom_sheet_content_before_speaking)
            && nextLayoutResId == R.layout.bottom_sheet_content_while_speaking;
    boolean isWhileToBefore =
        isCurrentBottomSheetLayout(R.layout.bottom_sheet_content_while_speaking)
            && nextLayoutResId == R.layout.bottom_sheet_content_before_speaking;
    return isBeforeToWhile || isWhileToBefore;
  }

  private void presentDefaultInputScene(@NonNull String sentenceToTranslate) {
    logTrace("TRACE_SCENE_TRANSITION scene=DEFAULT_INPUT");
    syncBottomSheetMode(BottomSheetMode.DEFAULT_INPUT, "presentDefaultInputScene");
    logUx("UX_SCENE_DEFAULT_INPUT", "sentenceLen=" + sentenceToTranslate.length());
    renderBottomSheetScene(
        R.layout.bottom_sheet_content_default,
        content -> {
          markRenderedScene(LearningScene.DEFAULT_INPUT, sentenceToTranslate);
          bottomSheetSceneRenderer.renderDefaultContent(content, sentenceToTranslate, null);
        });
  }

  private void presentBeforeSpeakingScene(@NonNull String sentenceToTranslate) {
    logTrace("TRACE_SCENE_TRANSITION scene=BEFORE_SPEAKING");
    syncBottomSheetMode(BottomSheetMode.BEFORE_SPEAKING, "presentBeforeSpeakingScene");
    renderBottomSheetScene(
        R.layout.bottom_sheet_content_before_speaking,
        content -> {
          markRenderedScene(LearningScene.BEFORE_SPEAKING, sentenceToTranslate);

          ripple1 = content.findViewById(R.id.ripple_1);
          ripple2 = content.findViewById(R.id.ripple_2);
          ripple3 = content.findViewById(R.id.ripple_3);
          recordingUiController.bindViews(null, ripple1, ripple2, ripple3);
          recordingUiController.startRippleAnimation();

          bottomSheetSceneRenderer.renderBeforeSpeakingContent(content, sentenceToTranslate, null);
        });
  }

  private void presentWhileSpeakingScene(@NonNull String sentenceToTranslate) {
    if (speakingSceneCoordinator == null) {
      return;
    }
    speakingSceneCoordinator.presentWhileSpeakingScene(sentenceToTranslate);
  }

  private void handleBeforeSpeakToRecord(@NonNull String sentenceToTranslate) {
    boolean hasPermission = isMicPermissionGranted();
    if (!hasPermission) {
      requestMicPermission(sentenceToTranslate, "before_speak");
      return;
    }
    presentWhileSpeakingScene(sentenceToTranslate);
  }

  @Nullable
  private String resolveSceneSentence(@NonNull LearningSessionSnapshot snapshot) {
    String sentenceFromScript = resolveSentenceFromScriptState(snapshot.getScriptUiState());
    if (!isBlank(sentenceFromScript)) {
      return sentenceFromScript;
    }
    String sentenceFromSpeaking = resolveSentenceFromSpeakingState(snapshot.getSpeakingUiState());
    if (!isBlank(sentenceFromSpeaking)) {
      return sentenceFromSpeaking;
    }
    return resolveSentenceFromCurrentBottomSheet();
  }

  @Nullable
  private String resolveSentenceFromScriptState(@Nullable ScriptUiState scriptUiState) {
    if (scriptUiState == null || scriptUiState.getActiveTurn() == null) {
      return null;
    }
    String korean = scriptUiState.getActiveTurn().getKorean();
    return isBlank(korean) ? null : korean;
  }

  @Nullable
  private String resolveSentenceFromSpeakingState(@Nullable SpeakingUiState speakingUiState) {
    if (speakingUiState == null || isBlank(speakingUiState.getOriginalSentence())) {
      return null;
    }
    return speakingUiState.getOriginalSentence();
  }

  @Nullable
  private String resolveSentenceFromCurrentBottomSheet() {
    View content = getBottomSheetContentHost();
    if (content == null) {
      return null;
    }
    TextView tvSentence = content.findViewById(R.id.tv_sentence_to_translate);
    if (tvSentence == null || isBlank(tvSentence.getText().toString())) {
      return null;
    }
    return tvSentence.getText().toString();
  }

  @Nullable
  private String resolveTranslatedSentenceFromCurrentBottomSheet() {
    View content = getBottomSheetContentHost();
    if (content == null) {
      return null;
    }
    TextView tvTranslatedSentence = content.findViewById(R.id.tv_translated_sentence);
    if (tvTranslatedSentence == null || isBlank(tvTranslatedSentence.getText().toString())) {
      return null;
    }
    return tvTranslatedSentence.getText().toString();
  }

  private void transitionToCompleteState(
      FluencyFeedback result, String sentenceToTranslate, String recognizedText) {
    stopSpeakingSession();

    presentFeedbackScene(result, sentenceToTranslate, recognizedText, true);
  }

  private void stopSpeakingSession() {
    if (speakingSceneCoordinator != null) {
      speakingSceneCoordinator.stopSpeakingSession();
      return;
    }
    safeStopRecording();
    if (speakingCoordinator != null) {
      speakingCoordinator.stopSession("stopSpeakingSession");
    }
    lastWaveformUpdateMs = 0L;
    recordingUiController.stopProgressAnimation();
    recordingUiController.stopRippleAnimation();
    if (waveformView != null) {
      waveformView.reset();
    }
    if (audioAccumulator != null) {
      audioAccumulator.reset();
    }
  }

  private void presentFeedbackScene(
      @Nullable FluencyFeedback result,
      String sentenceToTranslate,
      String translatedSentence,
      boolean isSpeakingMode) {
    logTrace("TRACE_SCENE_TRANSITION scene=FEEDBACK");
    if (feedbackCoordinator != null) {
      feedbackCoordinator.bindFeedbackContent(null, null);
    }
    final byte[] recordedAudio = isSpeakingMode ? lastRecordedAudio : null;
    renderBottomSheetScene(
        R.layout.bottom_sheet_content_feedback,
        content -> {
          markRenderedScene(LearningScene.FEEDBACK, sentenceToTranslate);

          bottomSheetSceneRenderer.renderFeedbackContent(
              content,
              sentenceToTranslate,
              translatedSentence,
              result,
              isSpeakingMode,
              recordedAudio,
              null);

          if (feedbackCoordinator != null) {
            feedbackCoordinator.bindFeedbackContent(content, bottomSheetContentContainer);
            FeedbackUiState feedbackState =
                currentSessionSnapshot == null ? null : currentSessionSnapshot.getFeedbackUiState();
            feedbackCoordinator.onFeedbackScenePresented(
                sentenceToTranslate,
                translatedSentence,
                result,
                isSpeakingMode,
                recordedAudio,
                feedbackState);
          }
        });
  }

  private void presentLearningFinishedScene() {
    if (bottomSheetController == null) {
      return;
    }
    bottomSheetController.setVisible(true);
    logTrace("TRACE_SCENE_TRANSITION scene=FINISHED");

    renderBottomSheetScene(
        R.layout.bottom_sheet_learning_finished,
        content -> {
          ensureSummarySeedPrepared();
          markRenderedScene(LearningScene.FINISHED, null);
          Button summaryButton = content.findViewById(R.id.btn_go_to_summary);
          if (summaryButton == null) {
            return;
          }
          summaryButton.setEnabled(true);
          bottomSheetSceneRenderer.renderLearningFinishedContent(content, null);
        });
  }

  private void requestDefaultInputSceneOnceAfterDelayIfFirstTime(
      @NonNull String sentenceToTranslate) {
    if (hasDefaultInputSceneEverShown) {
      logTrace("TRACE_DEFAULT_INPUT_SCENE_DELAY skip reason=already_shown");
      presentDefaultInputScene(sentenceToTranslate);
      return;
    }

    if (pendingDefaultInputPresentation != null) {
      logTrace("TRACE_DEFAULT_INPUT_SCENE_DELAY skip reason=already_scheduled");
      return;
    }

    pendingDefaultInputPresentation =
        () -> {
          pendingDefaultInputPresentation = null;
          hasDefaultInputSceneEverShown = true;

          if (isCurrentBottomSheetLayout(R.layout.bottom_sheet_content_default)) {
            logTrace("TRACE_DEFAULT_INPUT_SCENE_DELAY skip reason=already_rendered");
            return;
          }

          logTrace("TRACE_DEFAULT_INPUT_SCENE_DELAY execute");
          presentDefaultInputScene(sentenceToTranslate);
        };
    mainHandler.postDelayed(pendingDefaultInputPresentation, DEFAULT_INPUT_SCENE_DELAY_MS);
    logTrace("TRACE_DEFAULT_INPUT_SCENE_DELAY scheduled");
  }

  private void requestLearningFinishedSceneOnceAfterDelay() {
    if (pendingLearningFinishedPresentation != null) {
      logTrace("TRACE_FINISHED_SCENE skip reason=already_scheduled");
      return;
    }

    pendingLearningFinishedPresentation =
        () -> {
          pendingLearningFinishedPresentation = null;

          if (isCurrentBottomSheetLayout(R.layout.bottom_sheet_learning_finished)) {
            logTrace("TRACE_FINISHED_SCENE skip reason=already_rendered");
            return;
          }

          logTrace("TRACE_FINISHED_SCENE execute");
          presentLearningFinishedScene();
        };
    mainHandler.postDelayed(pendingLearningFinishedPresentation, FINISHED_SCENE_DELAY_MS);
    logTrace("TRACE_FINISHED_SCENE scheduled");
  }

  private void handleNextStepSequence(String userMessage, byte[] audioData) {
    if (turnCoordinator == null) {
      return;
    }
    turnCoordinator.advanceFromNextButton(userMessage, audioData);
  }

  private void safeStopPlayback(@NonNull String reason) {
    if (playbackCoordinator == null) {
      return;
    }
    playbackCoordinator.stopAllPlayback(reason);
  }

  private void safeStopRecording() {
    if (audioRecorderManager == null
        || speakingCoordinator == null
        || !speakingCoordinator.isRecordingActive()) {
      return;
    }
    audioRecorderManager.stopRecording();
    speakingCoordinator.onRecordingHardwareStopped();
  }

  private void playTts(String text, ImageView speakerBtn, String gender) {
    if (playbackCoordinator == null) {
      return;
    }
    applyPlaybackRuntimeSettings();
    if (blockPlaybackIfMuted("message_tts")) {
      return;
    }
    playbackCoordinator.playMessageTts(
        text,
        speakerBtn,
        gender,
        error -> Toast.makeText(getContext(), "TTS를 사용할 수 없습니다", Toast.LENGTH_SHORT).show());
  }

  private void playRecordedAudio(byte[] audioData, ImageView speakerBtn) {
    if (playbackCoordinator == null) {
      return;
    }
    if (blockPlaybackIfMuted("recorded_audio")) {
      return;
    }
    playbackCoordinator.playRecordedAudio(
        audioData,
        speakerBtn,
        error -> Toast.makeText(getContext(), "재생 오류: " + error, Toast.LENGTH_SHORT).show());
  }

  private void applyPlaybackRuntimeSettings() {
    if (playbackCoordinator == null) {
      return;
    }
    AppSettings settings = getCurrentAppSettings();
    if (settings == null) {
      return;
    }
    playbackCoordinator.applyTtsSettings(
        settings.getTtsProvider(), settings.getTtsSpeechRate(), settings.getTtsLocaleTag());
    logJobDebug("Applied TTS settings from runtime store.");
  }

  private void applyMuteStateIfEnabled() {
    if (!isMuteAllPlaybackEnabled()) {
      return;
    }
    safeStopPlayback("mute_all_enabled_on_resume");
    logJobDebug("Mute-all enabled. Existing playback stopped.");
  }

  private boolean blockPlaybackIfMuted(@NonNull String source) {
    if (!isMuteAllPlaybackEnabled()) {
      return false;
    }
    safeStopPlayback("mute_all_enabled_" + source);
    if (isAdded()) {
      Toast.makeText(getContext(), R.string.settings_mute_blocked_toast, Toast.LENGTH_SHORT).show();
    }
    logJobDebug("Playback blocked by mute setting. source=" + source);
    return true;
  }

  private boolean isMuteAllPlaybackEnabled() {
    AppSettings settings = getCurrentAppSettings();
    return settings != null && settings.isMuteAllPlayback();
  }

  @Nullable
  private AppSettings getCurrentAppSettings() {
    AppSettingsStore store = appSettingsStore;
    if (store == null) {
      return null;
    }
    return store.getSettings();
  }

  private void hideKeyboard() {
    if (getActivity() != null && getView() != null) {
      InputMethodManager imm =
          (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
      if (imm != null) {
        imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
      }
    }
  }

  private void abortLearningHost(@Nullable String message) {
    if (isAdded()) {
      String toastMessage = isBlank(message) ? "대본 생성 중 오류가 발생했어요" : message.trim();
      Toast.makeText(getContext(), toastMessage, Toast.LENGTH_SHORT).show();
    }
    if (getActivity() != null) {
      getActivity().finish();
    }
  }

  private void parseScriptData(String jsonString) {
    if (!validateViewModelForScriptParse()) {
      return;
    }
    logTrace("TRACE_PARSE_START len=" + (jsonString == null ? 0 : jsonString.length()));
    if (!loadScriptIntoViewModel(jsonString)) {
      logTrace("TRACE_PARSE_RESULT success=false");
      return;
    }
    applyScriptMetadataFromState();
    onScriptLoadSuccess();
  }

  private void requestScriptTtsPlayback(@NonNull String text, @NonNull Runnable onDone) {
    setPendingScriptTts(text, onDone);
    if (playScriptTtsImmediatelyIfNoViewModel(text)) {
      return;
    }
    emitScriptTtsEvent(text);
  }

  private void playScriptTts(String text, Runnable onComplete) {
    if (playbackCoordinator == null) {
      logTrace("TRACE_TTS_FLOW reason=playback_coordinator_null");
      onComplete.run();
      return;
    }
    applyPlaybackRuntimeSettings();
    if (blockPlaybackIfMuted("script_tts")) {
      onComplete.run();
      return;
    }
    playbackCoordinator.playScriptTts(
        text, opponentGender, this::markLatestAiSpeakerButtonActive, onComplete);
  }

  private boolean validateViewModelForScriptParse() {
    if (viewModel != null) {
      return true;
    }
    logTrace("TRACE_PARSE_RESULT success=false reason=viewModel_null");
    Toast.makeText(getContext(), "데이터 로드 실패", Toast.LENGTH_SHORT).show();
    return false;
  }

  private boolean loadScriptIntoViewModel(@NonNull String jsonString) {
    return viewModel != null && viewModel.loadScriptData(jsonString);
  }

  private void applyScriptMetadataFromState() {
    if (viewModel == null) {
      return;
    }
    ScriptUiState state = viewModel.getScriptUiState().getValue();
    if (state == null) {
      return;
    }
    topic = state.getTopic();
    opponentName = state.getOpponentName();
    opponentGender = state.getOpponentGender();
  }

  private void onScriptLoadSuccess() {
    isScriptLoaded = true;
    hasNotifiedLearningSessionFinished = false;
    logTrace("TRACE_PARSE_RESULT success=true isScriptLoaded=" + isScriptLoaded);
    logGate("M0_SCRIPT_LOADED");
    tryStartTurnFlow();
  }

  private void setPendingScriptTts(@NonNull String text, @NonNull Runnable onDone) {
    pendingScriptTtsCompletion = onDone;
    pendingScriptTtsText = text;
  }

  private boolean playScriptTtsImmediatelyIfNoViewModel(@NonNull String text) {
    if (viewModel != null) {
      return false;
    }
    Runnable completion = pendingScriptTtsCompletion;
    if (completion != null) {
      playScriptTts(text, completion);
    }
    pendingScriptTtsCompletion = null;
    pendingScriptTtsText = null;
    return true;
  }

  private void emitScriptTtsEvent(@NonNull String text) {
    if (viewModel == null) {
      return;
    }
    viewModel.emitScriptTtsEvent(text);
  }

  private void markLatestAiSpeakerButtonActive() {
    if (chatAdapter == null || playbackCoordinator == null) {
      return;
    }
    chatAdapter.postUi(
        () -> {
          ImageView btnSpeaker = chatAdapter.getLatestAiSpeakerButton();
          if (btnSpeaker == null || playbackCoordinator == null) {
            return;
          }
          playbackCoordinator.markSpeakerButtonActive(btnSpeaker);
        });
  }

  public interface OnScriptProgressListener {
    void onProgressUpdate(int currentStep, int totalSteps);

    void onMetadataLoaded(String topic, String opponentName);

    void onLearningSessionFinished();
  }

  private OnScriptProgressListener progressListener;

  @Override
  public void onAttach(@NonNull Context context) {
    super.onAttach(context);
    if (context instanceof OnScriptProgressListener) {
      progressListener = (OnScriptProgressListener) context;
    } else {
      throw new RuntimeException(context + "Must implement OnScriptProgressListener");
    }
  }

  @Override
  public void onDetach() {
    super.onDetach();
    progressListener = null;
  }

  private void ensureSummarySeedPrepared() {
    if (summaryCoordinator == null) {
      return;
    }
    summaryCoordinator.ensureSummarySeedPrepared();
  }

  private void openSummaryFragment(@NonNull String reason) {
    if (summaryCoordinator == null) {
      return;
    }
    summaryCoordinator.openSummary(reason);
  }

  private void logGate(@NonNull String key) {
    Log.d(TAG, key);
  }

  private void logTrace(@NonNull String key) {
    Log.d(TAG, "[TRACE] s=" + traceSessionId + " seq=" + (++traceSeq) + " " + key);
  }

  private void logJobDebug(@NonNull String message) {
    Log.d(JOB_TAG, message);
  }

  private void logDebugJob003(@NonNull String message) {
    Log.d("JOB_J-20260217-003", message);
  }

  private void logUx(@NonNull String key, @Nullable String fields) {
    uxGateLogger.log(key, fields);
  }
}
