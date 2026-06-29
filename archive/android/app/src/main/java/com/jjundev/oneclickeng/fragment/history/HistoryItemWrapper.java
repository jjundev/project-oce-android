package com.jjundev.oneclickeng.fragment.history;

public class HistoryItemWrapper {
  public static final int TYPE_WORD = 0;
  public static final int TYPE_EXPRESSION = 1;
  public static final int TYPE_SENTENCE = 2;
  public static final int TYPE_HIGHLIGHT = 3;

  private final int type;
  private final Object data;
  private final long learnedAtEpochMs;

  public HistoryItemWrapper(int type, Object data, long learnedAtEpochMs) {
    this.type = type;
    this.data = data;
    this.learnedAtEpochMs = learnedAtEpochMs;
  }

  public int getType() {
    return type;
  }

  public Object getData() {
    return data;
  }

  public long getLearnedAtEpochMs() {
    return learnedAtEpochMs;
  }
}
