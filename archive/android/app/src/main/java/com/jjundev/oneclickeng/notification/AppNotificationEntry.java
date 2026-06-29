package com.jjundev.oneclickeng.notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AppNotificationEntry {
  public static final String SOURCE_FCM = "FCM";
  public static final String SOURCE_LOCAL = "LOCAL";

  @NonNull private String id = "";
  @NonNull private String title = "";
  @NonNull private String body = "";
  @NonNull private String source = SOURCE_LOCAL;
  @NonNull private String channelId = "";
  private long receivedAtEpochMs;
  private boolean postedToSystem;
  @NonNull private String uid = AppNotificationStore.ANONYMOUS_UID;

  public AppNotificationEntry() {}

  public AppNotificationEntry(
      @NonNull String id,
      @NonNull String title,
      @NonNull String body,
      @NonNull String source,
      @NonNull String channelId,
      long receivedAtEpochMs,
      boolean postedToSystem,
      @NonNull String uid) {
    this.id = id;
    this.title = title;
    this.body = body;
    this.source = source;
    this.channelId = channelId;
    this.receivedAtEpochMs = receivedAtEpochMs;
    this.postedToSystem = postedToSystem;
    this.uid = uid;
  }

  @NonNull
  public String getId() {
    return id;
  }

  public void setId(@Nullable String id) {
    this.id = id == null ? "" : id;
  }

  @NonNull
  public String getTitle() {
    return title;
  }

  public void setTitle(@Nullable String title) {
    this.title = title == null ? "" : title;
  }

  @NonNull
  public String getBody() {
    return body;
  }

  public void setBody(@Nullable String body) {
    this.body = body == null ? "" : body;
  }

  @NonNull
  public String getSource() {
    return source;
  }

  public void setSource(@Nullable String source) {
    this.source = source == null ? SOURCE_LOCAL : source;
  }

  @NonNull
  public String getChannelId() {
    return channelId;
  }

  public void setChannelId(@Nullable String channelId) {
    this.channelId = channelId == null ? "" : channelId;
  }

  public long getReceivedAtEpochMs() {
    return receivedAtEpochMs;
  }

  public void setReceivedAtEpochMs(long receivedAtEpochMs) {
    this.receivedAtEpochMs = receivedAtEpochMs;
  }

  public boolean isPostedToSystem() {
    return postedToSystem;
  }

  public void setPostedToSystem(boolean postedToSystem) {
    this.postedToSystem = postedToSystem;
  }

  @NonNull
  public String getUid() {
    return uid;
  }

  public void setUid(@Nullable String uid) {
    this.uid = AppNotificationStore.normalizeUid(uid);
  }
}
