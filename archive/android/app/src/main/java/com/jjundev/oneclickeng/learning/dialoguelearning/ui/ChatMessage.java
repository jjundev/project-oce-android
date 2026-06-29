package com.jjundev.oneclickeng.learning.dialoguelearning.ui;

import androidx.annotation.Nullable;

public class ChatMessage {
  public static final int TYPE_AI = 0;
  public static final int TYPE_USER = 1;
  public static final int TYPE_SKELETON = 2;

  private final String engMessage;
  private final String koMessage;
  private final int type;
  private final byte[] audioData;

  public ChatMessage(String engMessage, String koMessage, int type) {
    this(engMessage, koMessage, type, null);
  }

  public ChatMessage(String message, int type) {
    this(message, null, type);
  }

  public ChatMessage(String message, int type, byte[] audioData) {
    this(message, null, type, audioData);
  }

  private ChatMessage(String engMessage, String koMessage, int type, byte[] audioData) {
    this.engMessage = engMessage;
    this.koMessage = koMessage;
    this.type = type;
    this.audioData = audioData;
  }

  public String getEngMessage() {
    return engMessage;
  }

  @Nullable
  public String getKoMessage() {
    return koMessage;
  }

  public int getType() {
    return type;
  }

  @Nullable
  public byte[] getAudioData() {
    return audioData;
  }

  public boolean hasAudio() {
    return audioData != null && audioData.length > 0;
  }
}
