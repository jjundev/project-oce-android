package com.jjundev.oneclickeng.learning.dialoguelearning.state;

import androidx.annotation.Nullable;

/** Single-consumption event wrapper for one-off LiveData notifications. */
public class ConsumableEvent<T> {

  private final T content;
  private boolean hasBeenHandled;

  public ConsumableEvent(@Nullable T content) {
    this.content = content;
  }

  @Nullable
  public synchronized T consumeIfNotHandled() {
    if (hasBeenHandled) {
      return null;
    }
    hasBeenHandled = true;
    return content;
  }

  @Nullable
  public T peekContent() {
    return content;
  }

  public synchronized boolean wasHandled() {
    return hasBeenHandled;
  }
}
