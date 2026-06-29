package com.jjundev.oneclickeng.settings;

import java.util.Calendar;

public final class LearningDataRetentionPolicy {

  public enum Preset {
    KEEP_1_WEEK,
    KEEP_2_WEEKS,
    KEEP_1_MONTH,
    KEEP_3_MONTHS,
    DELETE_ALL
  }

  private LearningDataRetentionPolicy() {}

  public static long resolveCutoffEpochMs(Preset preset, long nowEpochMs) {
    if (preset == Preset.DELETE_ALL) {
      return Long.MAX_VALUE;
    }

    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(nowEpochMs);
    resetToStartOfDay(calendar);

    switch (preset) {
      case KEEP_1_WEEK:
        calendar.add(Calendar.DAY_OF_YEAR, -7);
        break;
      case KEEP_2_WEEKS:
        calendar.add(Calendar.DAY_OF_YEAR, -14);
        break;
      case KEEP_1_MONTH:
        calendar.add(Calendar.MONTH, -1);
        break;
      case KEEP_3_MONTHS:
        calendar.add(Calendar.MONTH, -3);
        break;
      case DELETE_ALL:
      default:
        return Long.MAX_VALUE;
    }

    Calendar endOfCutoffDay = (Calendar) calendar.clone();
    endOfCutoffDay.add(Calendar.DAY_OF_YEAR, 1);
    endOfCutoffDay.add(Calendar.MILLISECOND, -1);
    return endOfCutoffDay.getTimeInMillis();
  }

  public static boolean shouldDelete(Long timestampEpochMs, Preset preset, long nowEpochMs) {
    if (preset == Preset.DELETE_ALL) {
      return true;
    }
    if (timestampEpochMs == null) {
      return false;
    }
    return timestampEpochMs <= resolveCutoffEpochMs(preset, nowEpochMs);
  }

  private static void resetToStartOfDay(Calendar calendar) {
    calendar.set(Calendar.HOUR_OF_DAY, 0);
    calendar.set(Calendar.MINUTE, 0);
    calendar.set(Calendar.SECOND, 0);
    calendar.set(Calendar.MILLISECOND, 0);
  }
}
