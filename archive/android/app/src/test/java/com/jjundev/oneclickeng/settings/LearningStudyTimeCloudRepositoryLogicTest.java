package com.jjundev.oneclickeng.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import org.junit.Test;

public class LearningStudyTimeCloudRepositoryLogicTest {

  @Test
  public void mergeRemoteWithPending_sameDay_addsTodayAndTotal() {
    long dayStart = 1_700_000_000_000L;
    HashSet<String> remoteStudyKeys = new HashSet<>(Arrays.asList("2026-02-24"));
    HashSet<String> remoteStreakKeys = new HashSet<>(Arrays.asList("2026-02-24"));
    HashSet<String> pendingStudyKeys = new HashSet<>(Arrays.asList("2026-02-24"));
    HashSet<String> pendingStreakKeys = new HashSet<>(Arrays.asList("2026-02-24"));

    LearningStudyTimeCloudRepository.MergedMetrics merged =
        LearningStudyTimeCloudRepository.mergeRemoteWithPending(
            3_600_000L,
            900_000L,
            dayStart,
            remoteStudyKeys,
            remoteStreakKeys,
            600_000L,
            300_000L,
            dayStart,
            pendingStudyKeys,
            pendingStreakKeys);

    assertEquals(4_200_000L, merged.totalVisibleMillis);
    assertEquals(1_200_000L, merged.todayVisibleMillis);
    assertEquals(dayStart, merged.dayStartEpochMs);
    assertEquals(1, merged.totalStudyDays);
    assertEquals(1, merged.studyDayKeys.size());
    assertTrue(merged.studyDayKeys.contains("2026-02-24"));
    assertEquals(1, merged.totalStreakDays);
    assertEquals(1, merged.streakDayKeys.size());
    assertTrue(merged.streakDayKeys.contains("2026-02-24"));
  }

  @Test
  public void mergeRemoteWithPending_differentDay_replacesTodayWithPending() {
    long remoteDayStart = 1_700_000_000_000L;
    long pendingDayStart = 1_700_086_400_000L;
    HashSet<String> remoteStudyKeys = new HashSet<>(Arrays.asList("2026-02-24"));
    HashSet<String> remoteStreakKeys = new HashSet<>(Arrays.asList("2026-02-24"));
    HashSet<String> pendingStudyKeys = new HashSet<>(Arrays.asList("2026-02-25"));
    HashSet<String> pendingStreakKeys = new HashSet<>(Arrays.asList("2026-02-25"));

    LearningStudyTimeCloudRepository.MergedMetrics merged =
        LearningStudyTimeCloudRepository.mergeRemoteWithPending(
            10_000_000L,
            2_000_000L,
            remoteDayStart,
            remoteStudyKeys,
            remoteStreakKeys,
            1_000_000L,
            600_000L,
            pendingDayStart,
            pendingStudyKeys,
            pendingStreakKeys);

    assertEquals(11_000_000L, merged.totalVisibleMillis);
    assertEquals(600_000L, merged.todayVisibleMillis);
    assertEquals(pendingDayStart, merged.dayStartEpochMs);
    assertEquals(2, merged.totalStudyDays);
    assertEquals(2, merged.studyDayKeys.size());
    assertTrue(merged.studyDayKeys.contains("2026-02-24"));
    assertTrue(merged.studyDayKeys.contains("2026-02-25"));
    assertEquals(2, merged.totalStreakDays);
    assertEquals(2, merged.streakDayKeys.size());
    assertTrue(merged.streakDayKeys.contains("2026-02-24"));
    assertTrue(merged.streakDayKeys.contains("2026-02-25"));
  }

  @Test
  public void mergeRemoteWithPending_clampsNegativeInputsToZero() {
    long dayStart = 1_700_000_000_000L;
    HashSet<String> remoteStudyKeys = new HashSet<>(Arrays.asList("2026-02-24"));
    HashSet<String> remoteStreakKeys = new HashSet<>(Arrays.asList("2026-02-24"));
    HashSet<String> pendingStudyKeys = new HashSet<>(Arrays.asList("2026-02-24"));
    HashSet<String> pendingStreakKeys = new HashSet<>(Arrays.asList("2026-02-24"));

    LearningStudyTimeCloudRepository.MergedMetrics merged =
        LearningStudyTimeCloudRepository.mergeRemoteWithPending(
            -1L,
            -1L,
            dayStart,
            remoteStudyKeys,
            remoteStreakKeys,
            120_000L,
            60_000L,
            dayStart,
            pendingStudyKeys,
            pendingStreakKeys);

    assertEquals(120_000L, merged.totalVisibleMillis);
    assertEquals(60_000L, merged.todayVisibleMillis);
    assertEquals(dayStart, merged.dayStartEpochMs);
    assertEquals(1, merged.totalStudyDays);
    assertEquals(1, merged.totalStreakDays);
  }

  @Test
  public void mergeRemoteWithPending_streakOnlyPending_keepsTimeAndMergesStreakDays() {
    long dayStart = 1_700_000_000_000L;
    HashSet<String> remoteStudyKeys = new HashSet<>(Arrays.asList("2026-02-24"));
    HashSet<String> remoteStreakKeys = new HashSet<>(Arrays.asList("2026-02-24"));
    HashSet<String> pendingStudyKeys = new HashSet<>();
    HashSet<String> pendingStreakKeys = new HashSet<>(Arrays.asList("2026-02-25"));

    LearningStudyTimeCloudRepository.MergedMetrics merged =
        LearningStudyTimeCloudRepository.mergeRemoteWithPending(
            3_000_000L,
            500_000L,
            dayStart,
            remoteStudyKeys,
            remoteStreakKeys,
            0L,
            0L,
            dayStart,
            pendingStudyKeys,
            pendingStreakKeys);

    assertEquals(3_000_000L, merged.totalVisibleMillis);
    assertEquals(500_000L, merged.todayVisibleMillis);
    assertEquals(1, merged.totalStudyDays);
    assertEquals(2, merged.totalStreakDays);
    assertTrue(merged.streakDayKeys.contains("2026-02-24"));
    assertTrue(merged.streakDayKeys.contains("2026-02-25"));
  }

  @Test
  public void mergeRemoteWithPending_manualBonusKey_mergesStudyAndStreakAndTime() {
    long dayStart = 1_700_000_000_000L;
    HashSet<String> remoteStudyKeys = new HashSet<>(Arrays.asList("2026-02-24"));
    HashSet<String> remoteStreakKeys = new HashSet<>(Arrays.asList("2026-02-24"));
    HashSet<String> pendingStudyKeys = new HashSet<>(Arrays.asList("creator_bonus_day_1"));
    HashSet<String> pendingStreakKeys = new HashSet<>(Arrays.asList("creator_bonus_day_1"));

    LearningStudyTimeCloudRepository.MergedMetrics merged =
        LearningStudyTimeCloudRepository.mergeRemoteWithPending(
            600_000L,
            300_000L,
            dayStart,
            remoteStudyKeys,
            remoteStreakKeys,
            1_200_000L,
            1_200_000L,
            dayStart,
            pendingStudyKeys,
            pendingStreakKeys);

    assertEquals(1_800_000L, merged.totalVisibleMillis);
    assertEquals(1_500_000L, merged.todayVisibleMillis);
    assertEquals(2, merged.totalStudyDays);
    assertEquals(2, merged.totalStreakDays);
    assertTrue(merged.studyDayKeys.contains("2026-02-24"));
    assertTrue(merged.studyDayKeys.contains("creator_bonus_day_1"));
    assertTrue(merged.streakDayKeys.contains("2026-02-24"));
    assertTrue(merged.streakDayKeys.contains("creator_bonus_day_1"));
  }

  @Test
  public void mergeTimeBonus_sameDay_addsTotalAndTodayOnly() {
    long dayStart = 1_700_000_000_000L;

    LearningStudyTimeCloudRepository.TimeBonusMergeResult merged =
        LearningStudyTimeCloudRepository.mergeTimeBonus(
            3_600_000L, 900_000L, dayStart, 600_000L, dayStart);

    assertEquals(4_200_000L, merged.totalVisibleMillis);
    assertEquals(1_500_000L, merged.todayVisibleMillis);
    assertEquals(dayStart, merged.dayStartEpochMs);
  }

  @Test
  public void mergeTimeBonus_dayChanged_resetsTodayToBonusAndAddsTotal() {
    long remoteDayStart = 1_700_000_000_000L;
    long bonusDayStart = 1_700_086_400_000L;

    LearningStudyTimeCloudRepository.TimeBonusMergeResult merged =
        LearningStudyTimeCloudRepository.mergeTimeBonus(
            3_600_000L, 900_000L, remoteDayStart, 600_000L, bonusDayStart);

    assertEquals(4_200_000L, merged.totalVisibleMillis);
    assertEquals(600_000L, merged.todayVisibleMillis);
    assertEquals(bonusDayStart, merged.dayStartEpochMs);
  }
}
