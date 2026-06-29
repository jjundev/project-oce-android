package com.jjundev.oneclickeng.notification;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import com.jjundev.oneclickeng.R;
import java.util.UUID;

public final class SystemNotificationPublisher {
  public static final String DEFAULT_CHANNEL_ID = "oneclickeng_general";

  private SystemNotificationPublisher() {}

  public static void publishLocal(
      @NonNull Context context, @Nullable String title, @Nullable String body) {
    publish(
        context,
        AppNotificationEntry.SOURCE_LOCAL,
        title,
        body,
        DEFAULT_CHANNEL_ID,
        System.currentTimeMillis());
  }

  public static void publish(
      @NonNull Context context,
      @NonNull String source,
      @Nullable String title,
      @Nullable String body,
      @Nullable String channelId,
      long receivedAtEpochMs) {
    Context appContext = context.getApplicationContext();
    String resolvedChannelId = normalizeOrDefault(channelId, DEFAULT_CHANNEL_ID);
    String resolvedTitle =
        normalizeOrDefault(title, appContext.getString(R.string.notification_default_title));
    String resolvedBody =
        normalizeOrDefault(body, appContext.getString(R.string.notification_default_body));
    long safeTimestamp = receivedAtEpochMs > 0L ? receivedAtEpochMs : System.currentTimeMillis();

    ensureChannel(appContext, resolvedChannelId);

    boolean postedToSystem = false;
    if (canPostSystemNotification(appContext)) {
      try {
        NotificationCompat.Builder builder =
            new NotificationCompat.Builder(appContext, resolvedChannelId)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(resolvedTitle)
                .setContentText(resolvedBody)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(resolvedBody))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setWhen(safeTimestamp)
                .setShowWhen(true);

        int notificationId =
            buildNotificationId(safeTimestamp, resolvedTitle, resolvedBody, source);
        NotificationManagerCompat.from(appContext).notify(notificationId, builder.build());
        postedToSystem = true;
      } catch (SecurityException ignored) {
        postedToSystem = false;
      }
    }

    AppNotificationEntry entry =
        new AppNotificationEntry(
            UUID.randomUUID().toString(),
            resolvedTitle,
            resolvedBody,
            source,
            resolvedChannelId,
            safeTimestamp,
            postedToSystem,
            AppNotificationStore.resolveCurrentUserUid());
    new AppNotificationStore(appContext).append(entry);
  }

  private static boolean canPostSystemNotification(@NonNull Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
      return false;
    }
    return NotificationManagerCompat.from(context).areNotificationsEnabled();
  }

  private static void ensureChannel(@NonNull Context context, @NonNull String channelId) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return;
    }

    NotificationManager manager = context.getSystemService(NotificationManager.class);
    if (manager == null || manager.getNotificationChannel(channelId) != null) {
      return;
    }

    NotificationChannel channel =
        new NotificationChannel(
            channelId,
            context.getString(R.string.notification_channel_general_name),
            NotificationManager.IMPORTANCE_HIGH);
    channel.setDescription(context.getString(R.string.notification_channel_general_description));
    manager.createNotificationChannel(channel);
  }

  private static int buildNotificationId(
      long timestamp, @NonNull String title, @NonNull String body, @NonNull String source) {
    int hash = (title + "|" + body + "|" + source).hashCode();
    long combined = (timestamp & 0x7FFFFFFF) ^ hash;
    return (int) (combined & 0x7FFFFFFF);
  }

  @NonNull
  private static String normalizeOrDefault(@Nullable String value, @NonNull String defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? defaultValue : trimmed;
  }
}
