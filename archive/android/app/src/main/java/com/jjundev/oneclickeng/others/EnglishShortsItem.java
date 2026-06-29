package com.jjundev.oneclickeng.others;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.List;

/** Data model for a single English Shorts card. */
public class EnglishShortsItem {
  @NonNull private String title;
  @NonNull private String videoUrl;
  @NonNull private List<String> tags;
  @NonNull private String documentId;
  private int likeCount;
  private int dislikeCount;
  private boolean isActive;

  /** No-arg constructor required for Firestore deserialization. */
  public EnglishShortsItem() {
    this.title = "";
    this.videoUrl = "";
    this.tags = new ArrayList<>();
    this.documentId = "";
    this.likeCount = 0;
    this.dislikeCount = 0;
    this.isActive = true;
  }

  @NonNull
  public String getTitle() {
    return title;
  }

  public void setTitle(@NonNull String title) {
    this.title = title;
  }

  @NonNull
  public String getVideoUrl() {
    return videoUrl;
  }

  public void setVideoUrl(@NonNull String videoUrl) {
    this.videoUrl = videoUrl;
  }

  @NonNull
  public List<String> getTags() {
    if (tags == null) {
      return new ArrayList<>();
    }
    return new ArrayList<>(tags);
  }

  public void setTags(@NonNull List<String> tags) {
    if (tags == null) {
      this.tags = new ArrayList<>();
      return;
    }
    this.tags = new ArrayList<>(tags);
  }

  @NonNull
  public String getDocumentId() {
    return documentId;
  }

  public void setDocumentId(@NonNull String documentId) {
    this.documentId = documentId;
  }

  public int getLikeCount() {
    return likeCount;
  }

  public void setLikeCount(int likeCount) {
    this.likeCount = likeCount;
  }

  public int getDislikeCount() {
    return dislikeCount;
  }

  public void setDislikeCount(int dislikeCount) {
    this.dislikeCount = dislikeCount;
  }

  public boolean isActive() {
    return isActive;
  }

  public void setActive(boolean active) {
    isActive = active;
  }
}
