package com.jjundev.oneclickeng.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.jjundev.oneclickeng.settings.LearningDataRetentionPolicy.Preset;
import java.util.Calendar;
import org.junit.Test;

public class LearningDataRetentionPolicyTest {

  @Test
  public void resolveCutoffEpochMs_matchesExpectedBoundaryForEachPreset() {
    long now = createLocalDateTimeEpochMs(2026, 2, 23, 10, 30, 0, 0);

    assertEquals(
        createLocalDateTimeEpochMs(2026, 2, 16, 23, 59, 59, 999),
        LearningDataRetentionPolicy.resolveCutoffEpochMs(Preset.KEEP_1_WEEK, now));
    assertEquals(
        createLocalDateTimeEpochMs(2026, 2, 9, 23, 59, 59, 999),
        LearningDataRetentionPolicy.resolveCutoffEpochMs(Preset.KEEP_2_WEEKS, now));
    assertEquals(
        createLocalDateTimeEpochMs(2026, 1, 23, 23, 59, 59, 999),
        LearningDataRetentionPolicy.resolveCutoffEpochMs(Preset.KEEP_1_MONTH, now));
    assertEquals(
        createLocalDateTimeEpochMs(2025, 11, 23, 23, 59, 59, 999),
        LearningDataRetentionPolicy.resolveCutoffEpochMs(Preset.KEEP_3_MONTHS, now));
  }

  @Test
  public void shouldDelete_includesBoundaryTimestamp() {
    long now = createLocalDateTimeEpochMs(2026, 2, 23, 10, 30, 0, 0);
    long cutoff = LearningDataRetentionPolicy.resolveCutoffEpochMs(Preset.KEEP_1_WEEK, now);

    assertTrue(LearningDataRetentionPolicy.shouldDelete(cutoff, Preset.KEEP_1_WEEK, now));
    assertFalse(LearningDataRetentionPolicy.shouldDelete(cutoff + 1L, Preset.KEEP_1_WEEK, now));
  }

  @Test
  public void shouldDelete_preservesMissingTimestampForRetentionPresets() {
    long now = createLocalDateTimeEpochMs(2026, 2, 23, 10, 30, 0, 0);

    assertFalse(LearningDataRetentionPolicy.shouldDelete(null, Preset.KEEP_1_WEEK, now));
    assertFalse(LearningDataRetentionPolicy.shouldDelete(null, Preset.KEEP_2_WEEKS, now));
    assertFalse(LearningDataRetentionPolicy.shouldDelete(null, Preset.KEEP_1_MONTH, now));
    assertFalse(LearningDataRetentionPolicy.shouldDelete(null, Preset.KEEP_3_MONTHS, now));
  }

  @Test
  public void shouldDelete_deleteAllAlwaysDeletes() {
    long now = createLocalDateTimeEpochMs(2026, 2, 23, 10, 30, 0, 0);

    assertTrue(LearningDataRetentionPolicy.shouldDelete(null, Preset.DELETE_ALL, now));
    assertTrue(LearningDataRetentionPolicy.shouldDelete(now, Preset.DELETE_ALL, now));
  }

  private static long createLocalDateTimeEpochMs(
      int year, int month, int day, int hour, int minute, int second, int millisecond) {
    Calendar calendar = Calendar.getInstance();
    calendar.set(Calendar.YEAR, year);
    calendar.set(Calendar.MONTH, month - 1);
    calendar.set(Calendar.DAY_OF_MONTH, day);
    calendar.set(Calendar.HOUR_OF_DAY, hour);
    calendar.set(Calendar.MINUTE, minute);
    calendar.set(Calendar.SECOND, second);
    calendar.set(Calendar.MILLISECOND, millisecond);
    return calendar.getTimeInMillis();
  }
}
