package com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import com.jjundev.oneclickeng.learning.dialoguelearning.model.ScriptTurn;

public final class TurnPlaybackCoordinator {

  public interface Callback {
    boolean isActive();

    void onShowSkeleton();

    void onRenderOpponentMessage(@NonNull ScriptTurn turn);

    void onPlayScriptTts(@NonNull String text, @NonNull Runnable onDone);

    void onAdvanceToNextTurn();
  }

  private final Handler handler;

  public TurnPlaybackCoordinator() {
    this(new Handler(Looper.getMainLooper()));
  }

  public TurnPlaybackCoordinator(@NonNull Handler handler) {
    this.handler = handler;
  }

  public void scheduleOpponentReply(@NonNull ScriptTurn turn, @NonNull Callback callback) {
    // Keep only the latest turn playback chain to avoid duplicate scheduled transitions.
    clear();
    handler.postDelayed(
        () -> {
          if (!callback.isActive()) {
            return;
          }

          callback.onShowSkeleton();

          handler.postDelayed(
              () -> {
                if (!callback.isActive()) {
                  return;
                }

                callback.onRenderOpponentMessage(turn);
                callback.onPlayScriptTts(
                    turn.getEnglish(),
                    () ->
                        handler.postDelayed(
                            () -> {
                              if (callback.isActive()) {
                                callback.onAdvanceToNextTurn();
                              }
                            },
                            1000));
              },
              1000);
        },
        500);
  }

  public void clear() {
    handler.removeCallbacksAndMessages(null);
  }
}
