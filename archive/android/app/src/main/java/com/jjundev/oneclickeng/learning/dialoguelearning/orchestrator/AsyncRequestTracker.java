package com.jjundev.oneclickeng.learning.dialoguelearning.orchestrator;

import androidx.annotation.NonNull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class AsyncRequestTracker {

  public static final String CHANNEL_SPEAKING = "speaking";
  public static final String CHANNEL_FEEDBACK = "feedback";
  public static final String CHANNEL_EXTRA = "extra";
  public static final String CHANNEL_SCRIPT = "script";

  private final AtomicLong requestSequence = new AtomicLong(0L);
  private final Map<String, Long> latestRequestByChannel = new ConcurrentHashMap<>();

  public long nextRequestId(@NonNull String channel) {
    long requestId = requestSequence.incrementAndGet();
    latestRequestByChannel.put(channel, requestId);
    return requestId;
  }

  public boolean isLatest(@NonNull String channel, long requestId) {
    if (requestId == 0L) {
      return false;
    }
    Long currentRequest = latestRequestByChannel.get(channel);
    return currentRequest != null && currentRequest == requestId;
  }

  public void invalidate(@NonNull String channel) {
    latestRequestByChannel.put(channel, 0L);
  }

  public void invalidateAll() {
    latestRequestByChannel.put(CHANNEL_SPEAKING, 0L);
    latestRequestByChannel.put(CHANNEL_FEEDBACK, 0L);
    latestRequestByChannel.put(CHANNEL_EXTRA, 0L);
    latestRequestByChannel.put(CHANNEL_SCRIPT, 0L);
  }

  public void clearForTurnBoundary() {
    invalidate(CHANNEL_SPEAKING);
    invalidate(CHANNEL_FEEDBACK);
    invalidate(CHANNEL_EXTRA);
  }

  public void clearForPlaybackBoundary() {
    invalidate(CHANNEL_SPEAKING);
    invalidate(CHANNEL_FEEDBACK);
    invalidate(CHANNEL_EXTRA);
  }
}
