package com.jjundev.oneclickeng.fragment.history.model;

import androidx.annotation.Nullable;
import java.util.List;

public class SavedCard {
  private String id;
  private String cardType; // "WORD", "SENTENCE", "EXPRESSION"
  private long timestamp;

  // 공통 및 Word 필드
  @Nullable private String english;
  @Nullable private String korean;

  // Word 전용 필드
  @Nullable private String exampleEnglish;
  @Nullable private String exampleKorean;

  // Expression 전용 필드
  @Nullable private String type; // Expression sub-type
  @Nullable private String koreanPrompt;
  @Nullable private String before;
  @Nullable private String after;
  @Nullable private String explanation;
  @Nullable private List<String> afterHighlights;

  public SavedCard() {
    // Required empty public constructor for Firestore
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getCardType() {
    return cardType;
  }

  public void setCardType(String cardType) {
    this.cardType = cardType;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  @Nullable
  public String getEnglish() {
    return english;
  }

  public void setEnglish(@Nullable String english) {
    this.english = english;
  }

  @Nullable
  public String getKorean() {
    return korean;
  }

  public void setKorean(@Nullable String korean) {
    this.korean = korean;
  }

  @Nullable
  public String getExampleEnglish() {
    return exampleEnglish;
  }

  public void setExampleEnglish(@Nullable String exampleEnglish) {
    this.exampleEnglish = exampleEnglish;
  }

  @Nullable
  public String getExampleKorean() {
    return exampleKorean;
  }

  public void setExampleKorean(@Nullable String exampleKorean) {
    this.exampleKorean = exampleKorean;
  }

  @Nullable
  public String getType() {
    return type;
  }

  public void setType(@Nullable String type) {
    this.type = type;
  }

  @Nullable
  public String getKoreanPrompt() {
    return koreanPrompt;
  }

  public void setKoreanPrompt(@Nullable String koreanPrompt) {
    this.koreanPrompt = koreanPrompt;
  }

  @Nullable
  public String getBefore() {
    return before;
  }

  public void setBefore(@Nullable String before) {
    this.before = before;
  }

  @Nullable
  public String getAfter() {
    return after;
  }

  public void setAfter(@Nullable String after) {
    this.after = after;
  }

  @Nullable
  public String getExplanation() {
    return explanation;
  }

  public void setExplanation(@Nullable String explanation) {
    this.explanation = explanation;
  }

  @Nullable
  public List<String> getAfterHighlights() {
    return afterHighlights;
  }

  public void setAfterHighlights(@Nullable List<String> afterHighlights) {
    this.afterHighlights = afterHighlights;
  }
}
