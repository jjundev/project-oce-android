package com.jjundev.oneclickeng.learning.dialoguelearning;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.learning.dialoguelearning.controller.ExtraQuestionFlowController;
import com.jjundev.oneclickeng.learning.dialoguelearning.controller.FeedbackFlowController;
import com.jjundev.oneclickeng.learning.dialoguelearning.controller.ScriptFlowController;
import com.jjundev.oneclickeng.learning.dialoguelearning.controller.SpeakingFlowController;
import com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts.IDialogueGenerateManager;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.DialogueScript;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.ScriptTurn;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SentenceFeedback;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.SpeakingAnalysisResult;
import com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator.LearningSessionOrchestrator;
import com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator.LearningSessionSnapshot;
import com.jjundev.oneclickeng.learning.dialoguelearning.session.DialogueScriptStreamingSessionStore;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.BottomSheetMode;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.ConsumableEvent;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.DialogueUiEvent;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.ExtraQuestionUiState;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.FeedbackUiState;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.ScriptUiState;
import com.jjundev.oneclickeng.learning.dialoguelearning.state.SpeakingUiState;
import com.jjundev.oneclickeng.learning.dialoguelearning.ui.ChatMessage;
import com.jjundev.oneclickeng.tool.AudioRecorder;
import java.util.ArrayList;
import java.util.List;

public class DialogueLearningViewModel extends ViewModel {
  private static final String TAG = "DialogueLearningViewModel";

  private final ScriptFlowController scriptFlowController;
  private final SpeakingFlowController speakingFlowController;
  private final FeedbackFlowController feedbackFlowController;
  private final ExtraQuestionFlowController extraQuestionFlowController;
  private final LearningSessionOrchestrator sessionOrchestrator;
  @Nullable private final AudioRecorder audioRecorder;
  @Nullable private final DialogueScriptStreamingSessionStore scriptStreamingSessionStore;

  private final MutableLiveData<BottomSheetMode> bottomSheetMode =
      new MutableLiveData<>(BottomSheetMode.DEFAULT_INPUT);
  private final MutableLiveData<ScriptUiState> scriptUiState =
      new MutableLiveData<>(ScriptUiState.empty());
  private final MutableLiveData<SpeakingUiState> speakingUiState =
      new MutableLiveData<>(SpeakingUiState.idle());
  private final MutableLiveData<FeedbackUiState> feedbackUiState =
      new MutableLiveData<>(FeedbackUiState.idle());
  private final MutableLiveData<ConsumableEvent<DialogueUiEvent>> uiEvent = new MutableLiveData<>();
  private final MutableLiveData<LearningSessionSnapshot> sessionSnapshot =
      new MutableLiveData<>(
          new LearningSessionSnapshot(
              ScriptUiState.empty(),
              SpeakingUiState.idle(),
              FeedbackUiState.idle(),
              BottomSheetMode.DEFAULT_INPUT));
  @NonNull private final List<ChatMessage> retainedChatMessages = new ArrayList<>();

  private long speakingEmissionId = 0L;
  private long feedbackEmissionId = 0L;
  private long extraEmissionId = 0L;
  private final StringBuilder extraResponseBuilder = new StringBuilder();
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  @Nullable private String attachedScriptSessionId;
  @Nullable private DialogueScriptStreamingSessionStore.Listener scriptSessionListener;
  private boolean waitingForStreamTurn = false;
  private boolean abortEventDispatched = false;
  private int requestedScriptLength = 0;

  private static final String DEFAULT_TOPIC = "영어 연습";
  private static final String DEFAULT_OPPONENT_NAME = "AI Coach";
  private static final String DEFAULT_OPPONENT_ROLE = "Partner";
  private static final String DEFAULT_OPPONENT_GENDER = "female";

  public DialogueLearningViewModel(
      @NonNull ScriptFlowController scriptFlowController,
      @NonNull SpeakingFlowController speakingFlowController,
      @NonNull FeedbackFlowController feedbackFlowController,
      @NonNull ExtraQuestionFlowController extraQuestionFlowController,
      @Nullable AudioRecorder audioRecorder) {
    this(
        scriptFlowController,
        speakingFlowController,
        feedbackFlowController,
        extraQuestionFlowController,
        audioRecorder,
        null);
  }

  public DialogueLearningViewModel(
      @NonNull ScriptFlowController scriptFlowController,
      @NonNull SpeakingFlowController speakingFlowController,
      @NonNull FeedbackFlowController feedbackFlowController,
      @NonNull ExtraQuestionFlowController extraQuestionFlowController,
      @Nullable AudioRecorder audioRecorder,
      @Nullable DialogueScriptStreamingSessionStore scriptStreamingSessionStore) {
    this(
        scriptFlowController,
        speakingFlowController,
        feedbackFlowController,
        extraQuestionFlowController,
        audioRecorder,
        new LearningSessionOrchestrator(
            scriptFlowController,
            speakingFlowController,
            feedbackFlowController,
            extraQuestionFlowController),
        scriptStreamingSessionStore);
  }

  public DialogueLearningViewModel(
      @NonNull ScriptFlowController scriptFlowController,
      @NonNull SpeakingFlowController speakingFlowController,
      @NonNull FeedbackFlowController feedbackFlowController,
      @NonNull ExtraQuestionFlowController extraQuestionFlowController,
      @Nullable AudioRecorder audioRecorder,
      @NonNull LearningSessionOrchestrator sessionOrchestrator,
      @Nullable DialogueScriptStreamingSessionStore scriptStreamingSessionStore) {
    this.scriptFlowController = scriptFlowController;
    this.speakingFlowController = speakingFlowController;
    this.feedbackFlowController = feedbackFlowController;
    this.extraQuestionFlowController = extraQuestionFlowController;
    this.sessionOrchestrator = sessionOrchestrator;
    this.audioRecorder = audioRecorder;
    this.scriptStreamingSessionStore = scriptStreamingSessionStore;
    publishSessionSnapshot();
  }

  public LiveData<BottomSheetMode> getBottomSheetMode() {
    return bottomSheetMode;
  }

  public LiveData<ScriptUiState> getScriptUiState() {
    return scriptUiState;
  }

  public LiveData<SpeakingUiState> getSpeakingUiState() {
    return speakingUiState;
  }

  public LiveData<FeedbackUiState> getFeedbackUiState() {
    return feedbackUiState;
  }

  public LiveData<ConsumableEvent<DialogueUiEvent>> getUiEvent() {
    return uiEvent;
  }

  public LiveData<LearningSessionSnapshot> getSessionSnapshotState() {
    return sessionSnapshot;
  }

  public void setBottomSheetMode(@NonNull BottomSheetMode mode) {
    BottomSheetMode currentMode = bottomSheetMode.getValue();
    if (currentMode == mode) {
      return;
    }
    applyStateAndPublish(() -> bottomSheetMode.setValue(mode));
  }

  @NonNull
  public List<ChatMessage> getRetainedChatMessages() {
    return retainedChatMessages;
  }

  public void emitScriptTtsEvent(@NonNull String scriptText) {
    emitUiEvent(new DialogueUiEvent.PlayScriptTts(scriptText));
  }

  public LearningSessionSnapshot getSessionSnapshot() {
    return buildSessionSnapshot();
  }

  private LearningSessionSnapshot buildSessionSnapshot() {
    ScriptUiState currentScript = scriptUiState.getValue();
    if (currentScript == null) {
      currentScript = ScriptUiState.empty();
    }

    SpeakingUiState currentSpeaking = speakingUiState.getValue();
    if (currentSpeaking == null) {
      currentSpeaking = SpeakingUiState.idle();
    }

    FeedbackUiState currentFeedback = feedbackUiState.getValue();
    if (currentFeedback == null) {
      currentFeedback = FeedbackUiState.idle();
    }

    BottomSheetMode currentMode = bottomSheetMode.getValue();
    if (currentMode == null) {
      currentMode = BottomSheetMode.DEFAULT_INPUT;
    }

    return new LearningSessionSnapshot(
        currentScript, currentSpeaking, currentFeedback, currentMode);
  }

  /**
   * Internal state mutation contract: 1) mutate state 2) publish session snapshot exactly once from
   * same main-thread frame.
   */
  private void applyStateAndPublish(@NonNull Runnable stateMutation) {
    runOnMainThread(
        () -> {
          stateMutation.run();
          sessionSnapshot.setValue(buildSessionSnapshot());
        });
  }

  private void publishSessionSnapshot() {
    runOnMainThread(() -> sessionSnapshot.setValue(buildSessionSnapshot()));
  }

  private void runOnMainThread(@NonNull Runnable action) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      action.run();
      return;
    }
    mainHandler.post(action);
  }

  private void emitUiEvent(@NonNull DialogueUiEvent event) {
    uiEvent.postValue(new ConsumableEvent<>(event));
  }

  public boolean loadScriptData(@NonNull String scriptJson) {
    detachScriptStreamingSession(true);
    waitingForStreamTurn = false;
    abortEventDispatched = false;
    requestedScriptLength = 0;
    try {
      sessionOrchestrator.loadScript(scriptJson);
      DialogueScript script = scriptFlowController.getScript();
      if (script == null) {
        emitUiEvent(new DialogueUiEvent.ShowToast("데이터 로드 실패"));
        return false;
      }

      applyStateAndPublish(
          () ->
              scriptUiState.setValue(
                  new ScriptUiState(
                      0,
                      script.size(),
                      script.getTopic(),
                      script.getOpponentName(),
                      script.getOpponentGender(),
                      false,
                      null)));
      return true;
    } catch (Exception e) {
      emitUiEvent(new DialogueUiEvent.ShowToast("데이터 로드 실패"));
      return false;
    }
  }

  public boolean attachScriptStreamingSession(
      @Nullable String sessionId, int requestedLength, @Nullable String requestedTopic) {
    DialogueScriptStreamingSessionStore store = scriptStreamingSessionStore;
    logStream(
        "attach request: sessionId="
            + shortSession(sessionId)
            + ", requestedLength="
            + Math.max(1, requestedLength)
            + ", requestedTopic="
            + safeText(requestedTopic));
    if (store == null || isBlank(sessionId)) {
      logStream("attach rejected: store or sessionId invalid");
      return false;
    }

    detachScriptStreamingSession(true);
    waitingForStreamTurn = false;
    abortEventDispatched = false;
    requestedScriptLength = Math.max(1, requestedLength);

    scriptFlowController.startStreaming(
        firstNonBlank(trimToNull(requestedTopic), DEFAULT_TOPIC),
        DEFAULT_OPPONENT_NAME,
        DEFAULT_OPPONENT_ROLE,
        DEFAULT_OPPONENT_GENDER);
    applyStateAndPublish(() -> applyScriptStateFromController(false, null));

    DialogueScriptStreamingSessionStore.Listener listener =
        new DialogueScriptStreamingSessionStore.Listener() {
          @Override
          public void onMetadata(
              @NonNull DialogueScriptStreamingSessionStore.ScriptMetadata metadata) {
            logStream(
                "metadata: sessionId="
                    + shortSession(attachedScriptSessionId)
                    + ", topic="
                    + safeText(metadata.getTopic())
                    + ", opponent="
                    + safeText(metadata.getOpponentName()));
            applyStateAndPublish(
                () -> {
                  scriptFlowController.updateStreamMetadata(
                      firstNonBlank(trimToNull(metadata.getTopic()), DEFAULT_TOPIC),
                      firstNonBlank(trimToNull(metadata.getOpponentName()), DEFAULT_OPPONENT_NAME),
                      DEFAULT_OPPONENT_ROLE,
                      firstNonBlank(
                          trimToNull(metadata.getOpponentGender()), DEFAULT_OPPONENT_GENDER));
                  applyScriptStateFromController(false, null);
                });
          }

          @Override
          public void onTurn(@NonNull IDialogueGenerateManager.ScriptTurnChunk turn) {
            String korean = trimToNull(turn.getKorean());
            String english = trimToNull(turn.getEnglish());
            if (korean == null || english == null) {
              logStream("turn ignored: invalid text payload");
              return;
            }
            applyStateAndPublish(
                () -> {
                  scriptFlowController.appendStreamTurn(
                      new ScriptTurn(korean, english, resolveRole(turn.getRole())));
                  applyScriptStateFromController(false, null);
                  DialogueScript script = scriptFlowController.getScript();
                  int total = script == null ? 0 : script.size();
                  logStream(
                      "turn appended: sessionId="
                          + shortSession(attachedScriptSessionId)
                          + ", totalTurns="
                          + total
                          + ", role="
                          + safeText(turn.getRole()));
                });
            if (waitingForStreamTurn) {
              waitingForStreamTurn = false;
              logStream("waiting resolved: emit AdvanceTurn");
              emitUiEvent(new DialogueUiEvent.AdvanceTurn());
            }
          }

          @Override
          public void onComplete(@Nullable String warningMessage) {
            logStream(
                "stream complete: sessionId="
                    + shortSession(attachedScriptSessionId)
                    + ", warning="
                    + safeText(warningMessage));
            applyStateAndPublish(
                () -> {
                  scriptFlowController.markStreamCompleted();
                  applyScriptStateFromController(false, null);
                });
            if (waitingForStreamTurn) {
              waitingForStreamTurn = false;
              logStream("waiting resolved by complete: emit AdvanceTurn");
              emitUiEvent(new DialogueUiEvent.AdvanceTurn());
            }
          }

          @Override
          public void onFailure(@NonNull String error) {
            if (abortEventDispatched) {
              logStream("failure ignored: abort already dispatched");
              return;
            }
            abortEventDispatched = true;
            waitingForStreamTurn = false;
            logStream(
                "stream failure: sessionId="
                    + shortSession(attachedScriptSessionId)
                    + ", error="
                    + safeText(error));
            detachScriptStreamingSession(true);
            emitUiEvent(
                new DialogueUiEvent.AbortLearning(
                    firstNonBlank(trimToNull(error), "대본 생성 중 오류가 발생했어요")));
          }
        };

    DialogueScriptStreamingSessionStore.Snapshot snapshot = store.attach(sessionId, listener);
    if (snapshot == null) {
      logStream("attach failed: session not found, sessionId=" + shortSession(sessionId));
      return false;
    }

    attachedScriptSessionId = sessionId;
    scriptSessionListener = listener;
    logStream(
        "attach success: sessionId="
            + shortSession(sessionId)
            + ", bufferedTurns="
            + snapshot.getBufferedTurns().size()
            + ", completed="
            + snapshot.isCompleted()
            + ", failure="
            + (trimToNull(snapshot.getFailureMessage()) != null));
    applyScriptStreamingSnapshot(snapshot);
    return true;
  }

  private void applyScriptStreamingSnapshot(
      @NonNull DialogueScriptStreamingSessionStore.Snapshot snapshot) {
    requestedScriptLength = Math.max(1, snapshot.getRequestedLength());
    logStream(
        "snapshot apply: sessionId="
            + shortSession(attachedScriptSessionId)
            + ", requestedLength="
            + requestedScriptLength
            + ", bufferedTurns="
            + snapshot.getBufferedTurns().size()
            + ", completed="
            + snapshot.isCompleted());
    DialogueScriptStreamingSessionStore.ScriptMetadata metadata = snapshot.getMetadata();
    if (metadata != null) {
      logStream(
          "snapshot metadata: topic="
              + safeText(metadata.getTopic())
              + ", opponent="
              + safeText(metadata.getOpponentName()));
      scriptFlowController.updateStreamMetadata(
          firstNonBlank(trimToNull(metadata.getTopic()), DEFAULT_TOPIC),
          firstNonBlank(trimToNull(metadata.getOpponentName()), DEFAULT_OPPONENT_NAME),
          DEFAULT_OPPONENT_ROLE,
          firstNonBlank(trimToNull(metadata.getOpponentGender()), DEFAULT_OPPONENT_GENDER));
    }

    for (IDialogueGenerateManager.ScriptTurnChunk turn : snapshot.getBufferedTurns()) {
      String korean = trimToNull(turn.getKorean());
      String english = trimToNull(turn.getEnglish());
      if (korean == null || english == null) {
        continue;
      }
      scriptFlowController.appendStreamTurn(
          new ScriptTurn(korean, english, resolveRole(turn.getRole())));
    }
    if (snapshot.isCompleted()) {
      scriptFlowController.markStreamCompleted();
    }

    String failureMessage = trimToNull(snapshot.getFailureMessage());
    if (failureMessage != null) {
      if (!abortEventDispatched) {
        abortEventDispatched = true;
        logStream("snapshot failure: " + safeText(failureMessage));
        detachScriptStreamingSession(true);
        emitUiEvent(new DialogueUiEvent.AbortLearning(failureMessage));
      }
      return;
    }
    applyStateAndPublish(() -> applyScriptStateFromController(false, null));
  }

  @NonNull
  public List<ScriptTurn> getScriptTurnsSnapshot() {
    DialogueScript script = scriptFlowController.getScript();
    if (script == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>(script.getTurns());
  }

  @NonNull
  public LearningSessionOrchestrator.TurnDecision moveToNextTurnDecision() {
    LearningSessionOrchestrator.TurnDecision turnDecision =
        sessionOrchestrator.moveToNextTurnDecision();
    ScriptFlowController.NextTurnResult result = turnDecision.getNextTurnResult();

    ScriptUiState prev = scriptUiState.getValue();
    String topic = prev == null ? DEFAULT_TOPIC : prev.getTopic();
    String opponentName = prev == null ? DEFAULT_OPPONENT_NAME : prev.getOpponentName();
    String opponentGender = prev == null ? DEFAULT_OPPONENT_GENDER : prev.getOpponentGender();
    int visibleTotal = resolveVisibleTotalSteps(result.getTotalSteps());

    if (result.getType() == ScriptFlowController.NextTurnResult.Type.FINISHED) {
      waitingForStreamTurn = false;
      logStream("nextTurn: FINISHED");
      applyStateAndPublish(
          () -> {
            scriptUiState.setValue(
                new ScriptUiState(
                    result.getCurrentStep(),
                    visibleTotal,
                    topic,
                    opponentName,
                    opponentGender,
                    true,
                    null));
            bottomSheetMode.setValue(BottomSheetMode.FINISHED);
          });
      detachScriptStreamingSession(true);
      return turnDecision;
    }

    if (result.getType() == ScriptFlowController.NextTurnResult.Type.TURN) {
      waitingForStreamTurn = false;
      ScriptTurn turn = result.getTurn();
      logStream(
          "nextTurn: TURN, step="
              + result.getCurrentStep()
              + "/"
              + visibleTotal
              + ", role="
              + safeText(turn == null ? null : turn.getRole()));
      applyStateAndPublish(
          () -> {
            scriptUiState.setValue(
                new ScriptUiState(
                    result.getCurrentStep(),
                    visibleTotal,
                    topic,
                    opponentName,
                    opponentGender,
                    false,
                    turn));

            if (turn != null && !turn.isOpponentTurn()) {
              bottomSheetMode.setValue(BottomSheetMode.DEFAULT_INPUT);
            } else if (turn != null
                && turnDecision != null
                && turnDecision.getProposedBottomSheetMode() == BottomSheetMode.BEFORE_SPEAKING) {
              bottomSheetMode.setValue(BottomSheetMode.BEFORE_SPEAKING);
            }
          });
      return turnDecision;
    }

    if (result.getType() == ScriptFlowController.NextTurnResult.Type.WAITING) {
      waitingForStreamTurn = true;
      logStream(
          "nextTurn: WAITING, step="
              + result.getCurrentStep()
              + "/"
              + visibleTotal
              + ", streamCompleted="
              + scriptFlowController.isStreamCompleted());
      applyStateAndPublish(
          () ->
              scriptUiState.setValue(
                  new ScriptUiState(
                      result.getCurrentStep(),
                      visibleTotal,
                      topic,
                      opponentName,
                      opponentGender,
                      false,
                      prev == null ? null : prev.getActiveTurn())));
    }

    return turnDecision;
  }

  @NonNull
  public ScriptFlowController.NextTurnResult moveToNextTurn() {
    return moveToNextTurnDecision().getNextTurnResult();
  }

  @Nullable
  public LearningSessionOrchestrator.TurnDecision getLastTurnDecision() {
    return sessionOrchestrator.getLastTurnDecision();
  }

  public void onRecordingRequested(@NonNull String sentenceToTranslate) {
    sessionOrchestrator.onRecordingRequested(sentenceToTranslate);
    emitUiEvent(new DialogueUiEvent.RequestMicPermission(sentenceToTranslate));
  }

  public void onRecordingStarted() {
    sessionOrchestrator.onRecordingStarted();
    SpeakingUiState previous = speakingUiState.getValue();
    applyStateAndPublish(
        () -> {
          speakingUiState.setValue(
              new SpeakingUiState(
                  ++speakingEmissionId,
                  previous == null ? 0L : previous.getRequestId(),
                  true,
                  false,
                  0f,
                  previous == null ? null : previous.getLastRecordedAudio(),
                  null,
                  previous == null ? null : previous.getOriginalSentence(),
                  previous == null ? null : previous.getRecognizedText(),
                  null));
          bottomSheetMode.setValue(BottomSheetMode.WHILE_SPEAKING);
        });
  }

  public void onAudioChunk(@NonNull byte[] audioChunk) {
    sessionOrchestrator.onAudioChunk(audioChunk);
    SpeakingUiState previous = speakingUiState.getValue();
    if (previous == null) {
      return;
    }
    applyStateAndPublish(
        () ->
            speakingUiState.setValue(
                new SpeakingUiState(
                    ++speakingEmissionId,
                    previous.getRequestId(),
                    previous.isRecording(),
                    previous.isAnalyzing(),
                    SpeakingFlowController.calculateAmplitude(audioChunk),
                    previous.getLastRecordedAudio(),
                    previous.getFluencyResult(),
                    previous.getOriginalSentence(),
                    previous.getRecognizedText(),
                    previous.getError())));
  }

  public long analyzeSpeaking(
      @NonNull String originalSentence,
      @NonNull byte[] audioData,
      @Nullable String fallbackRecognizedText) {
    long requestId =
        sessionOrchestrator.analyzeSpeaking(
            originalSentence,
            audioData,
            fallbackRecognizedText,
            new SpeakingFlowController.Callback() {
              @Override
              public void onSuccess(@NonNull SpeakingAnalysisResult result) {
                applyStateAndPublish(
                    () -> {
                      speakingUiState.setValue(
                          new SpeakingUiState(
                              ++speakingEmissionId,
                              result.getRequestId(),
                              false,
                              false,
                              0f,
                              result.getRecordedAudio(),
                              result.getFluencyResult(),
                              result.getOriginalSentence(),
                              result.getRecognizedText(),
                              null));
                      bottomSheetMode.setValue(BottomSheetMode.FEEDBACK);
                    });

                startSentenceFeedback(result.getOriginalSentence(), result.getRecognizedText());
              }

              @Override
              public void onError(long requestId, @NonNull String error) {
                applyStateAndPublish(
                    () ->
                        speakingUiState.setValue(
                            new SpeakingUiState(
                                ++speakingEmissionId,
                                requestId,
                                false,
                                false,
                                0f,
                                null,
                                null,
                                originalSentence,
                                fallbackRecognizedText,
                                error)));
              }
            });

    applyStateAndPublish(
        () ->
            speakingUiState.setValue(
                new SpeakingUiState(
                    ++speakingEmissionId,
                    requestId,
                    false,
                    true,
                    0f,
                    null,
                    null,
                    originalSentence,
                    fallbackRecognizedText,
                    null)));
    return requestId;
  }

  public void startSentenceFeedback(
      @NonNull String originalSentence, @NonNull String userTranscript) {
    FeedbackUiState previous = feedbackUiState.getValue();
    ExtraQuestionUiState extraState =
        previous == null ? ExtraQuestionUiState.idle() : previous.getExtraQuestionUiState();

    applyStateAndPublish(
        () -> {
          feedbackUiState.setValue(
              new FeedbackUiState(
                  ++feedbackEmissionId, true, null, null, null, false, null, false, extraState));
          bottomSheetMode.setValue(BottomSheetMode.FEEDBACK);
        });

    sessionOrchestrator.startSentenceFeedback(
        originalSentence,
        userTranscript,
        new FeedbackFlowController.Callback() {
          @Override
          public void onSkipped() {
            applyStateAndPublish(
                () ->
                    feedbackUiState.setValue(
                        new FeedbackUiState(
                            ++feedbackEmissionId,
                            false,
                            null,
                            null,
                            null,
                            false,
                            null,
                            true,
                            extraState)));
          }

          @Override
          public void onSectionReady(
              long requestId,
              @NonNull String sectionKey,
              @NonNull SentenceFeedback partialFeedback) {
            applyStateAndPublish(
                () ->
                    feedbackUiState.setValue(
                        new FeedbackUiState(
                            ++feedbackEmissionId,
                            true,
                            sectionKey,
                            partialFeedback,
                            null,
                            false,
                            null,
                            false,
                            extraState)));
          }

          @Override
          public void onComplete(long requestId, SentenceFeedback fullFeedback) {
            applyStateAndPublish(
                () ->
                    feedbackUiState.setValue(
                        new FeedbackUiState(
                            ++feedbackEmissionId,
                            false,
                            null,
                            null,
                            fullFeedback,
                            shouldShowNextButton(fullFeedback),
                            null,
                            true,
                            extraState)));
          }

          @Override
          public void onError(long requestId, @NonNull String error) {
            FeedbackUiState current = feedbackUiState.getValue();
            SentenceFeedback existingFull = current == null ? null : current.getFullFeedback();
            applyStateAndPublish(
                () ->
                    feedbackUiState.setValue(
                        new FeedbackUiState(
                            ++feedbackEmissionId,
                            false,
                            null,
                            null,
                            existingFull,
                            shouldShowNextButton(existingFull),
                            error,
                            true,
                            extraState)));
          }
        });
  }

  public void askExtraQuestion(
      @NonNull String originalSentence, @NonNull String userSentence, @NonNull String question) {
    extraResponseBuilder.setLength(0);

    updateExtraQuestionState(new ExtraQuestionUiState(++extraEmissionId, true, question, "", null));

    sessionOrchestrator.askExtraQuestion(
        originalSentence,
        userSentence,
        question,
        new ExtraQuestionFlowController.Callback() {
          @Override
          public void onChunk(@NonNull String chunk) {
            extraResponseBuilder.append(chunk);
            updateExtraQuestionState(
                new ExtraQuestionUiState(
                    ++extraEmissionId, true, question, extraResponseBuilder.toString(), null));
          }

          @Override
          public void onComplete() {
            updateExtraQuestionState(
                new ExtraQuestionUiState(
                    ++extraEmissionId, false, question, extraResponseBuilder.toString(), null));
          }

          @Override
          public void onError(@NonNull String error) {
            updateExtraQuestionState(
                new ExtraQuestionUiState(
                    ++extraEmissionId, false, question, extraResponseBuilder.toString(), error));
          }
        });
  }

  public void emitScrollChatToBottom() {
    emitUiEvent(new DialogueUiEvent.ScrollChatToBottom());
  }

  public void emitOpenSummary() {
    emitUiEvent(new DialogueUiEvent.OpenSummary());
  }

  public void invalidateSpeakingRequest() {
    sessionOrchestrator.invalidateSpeakingRequest();
  }

  public void clearRequests() {
    sessionOrchestrator.clearRequests();
  }

  @Nullable
  public AudioRecorder getAudioRecorder() {
    return audioRecorder;
  }

  private void applyScriptStateFromController(boolean finished, @Nullable ScriptTurn activeTurn) {
    DialogueScript script = scriptFlowController.getScript();
    if (script == null) {
      return;
    }
    int currentStep =
        finished ? script.size() : Math.max(0, scriptFlowController.getCurrentIndex() + 1);
    scriptUiState.setValue(
        new ScriptUiState(
            currentStep,
            resolveVisibleTotalSteps(script.size()),
            script.getTopic(),
            script.getOpponentName(),
            script.getOpponentGender(),
            finished,
            activeTurn));
  }

  private int resolveVisibleTotalSteps(int fallback) {
    if (requestedScriptLength > 0) {
      return requestedScriptLength;
    }
    return Math.max(1, fallback);
  }

  private void detachScriptStreamingSession(boolean release) {
    DialogueScriptStreamingSessionStore store = scriptStreamingSessionStore;
    String sessionId = attachedScriptSessionId;
    DialogueScriptStreamingSessionStore.Listener listener = scriptSessionListener;
    attachedScriptSessionId = null;
    scriptSessionListener = null;

    if (store == null || sessionId == null || listener == null) {
      return;
    }
    store.detach(sessionId, listener);
    if (release) {
      store.release(sessionId);
    }
    logStream("detach: sessionId=" + shortSession(sessionId) + ", release=" + release);
  }

  @NonNull
  private static String resolveRole(@Nullable String rawRole) {
    String role = trimToNull(rawRole);
    if (role == null) {
      return "user";
    }
    return role;
  }

  @Nullable
  private static String trimToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static boolean isBlank(@Nullable String value) {
    return trimToNull(value) == null;
  }

  @NonNull
  private static String firstNonBlank(@Nullable String first, @NonNull String fallback) {
    String value = trimToNull(first);
    return value == null ? fallback : value;
  }

  private boolean shouldShowNextButton(@Nullable SentenceFeedback feedback) {
    if (feedback == null || feedback.getWritingScore() == null) {
      return false;
    }
    return feedback.getWritingScore().getScore() >= 70;
  }

  private void updateExtraQuestionState(@NonNull ExtraQuestionUiState extraQuestionUiState) {
    applyStateAndPublish(
        () -> {
          FeedbackUiState source = feedbackUiState.getValue();
          if (source == null) {
            source = FeedbackUiState.idle();
          }
          feedbackUiState.setValue(
              new FeedbackUiState(
                  ++feedbackEmissionId,
                  source.isLoading(),
                  source.getSectionKey(),
                  source.getPartialFeedback(),
                  source.getFullFeedback(),
                  source.isShowNextButton(),
                  source.getError(),
                  source.isCompleted(),
                  extraQuestionUiState));
        });
  }

  @Override
  protected void onCleared() {
    clearRequests();
    detachScriptStreamingSession(true);
    retainedChatMessages.clear();
    logStream("onCleared");
    super.onCleared();
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
}
