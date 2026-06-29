package com.jjundev.oneclickeng.learning.dialoguelearning.manager_contracts;

import androidx.annotation.NonNull;

public final class GeminiTtsAudio {
  @NonNull private final byte[] pcmData;
  private final int sampleRateHz;
  @NonNull private final String mimeType;

  public GeminiTtsAudio(@NonNull byte[] pcmData, int sampleRateHz, @NonNull String mimeType) {
    this.pcmData = pcmData;
    this.sampleRateHz = sampleRateHz;
    this.mimeType = mimeType;
  }

  @NonNull
  public byte[] getPcmData() {
    return pcmData;
  }

  public int getSampleRateHz() {
    return sampleRateHz;
  }

  @NonNull
  public String getMimeType() {
    return mimeType;
  }
}
