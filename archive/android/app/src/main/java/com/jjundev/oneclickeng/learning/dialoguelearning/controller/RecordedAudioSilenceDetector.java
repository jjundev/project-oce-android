package com.jjundev.oneclickeng.learning.dialoguelearning.controller;

import androidx.annotation.Nullable;

public final class RecordedAudioSilenceDetector {

  private RecordedAudioSilenceDetector() {}

  public static boolean isSilent(@Nullable byte[] audioData, float threshold) {
    if (threshold <= 0f) {
      return calculateNormalizedRms(audioData) <= 0f;
    }
    return calculateNormalizedRms(audioData) < threshold;
  }

  public static float calculateNormalizedRms(@Nullable byte[] audioData) {
    if (audioData == null || audioData.length < 2) {
      return 0f;
    }

    int sampleCount = audioData.length / 2;
    if (sampleCount <= 0) {
      return 0f;
    }

    double sumSquares = 0d;
    for (int i = 0; i + 1 < audioData.length; i += 2) {
      short sample = (short) ((audioData[i] & 0xFF) | (audioData[i + 1] << 8));
      double normalized = sample / 32768.0d;
      sumSquares += normalized * normalized;
    }

    double meanSquares = sumSquares / sampleCount;
    return (float) Math.sqrt(meanSquares);
  }
}
