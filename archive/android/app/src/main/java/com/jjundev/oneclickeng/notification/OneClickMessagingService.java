package com.jjundev.oneclickeng.notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public class OneClickMessagingService extends FirebaseMessagingService {
  private static final String KEY_TITLE = "title";
  private static final String KEY_BODY = "body";
  private static final String KEY_CHANNEL_ID = "channelId";
  private static final String KEY_SENT_AT = "sentAt";

  @Override
  public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
    Map<String, String> data = remoteMessage.getData();

    String title = trimToNull(data.get(KEY_TITLE));
    String body = trimToNull(data.get(KEY_BODY));
    String channelId = trimToNull(data.get(KEY_CHANNEL_ID));
    long sentAt = parseTimestampOrNow(data.get(KEY_SENT_AT));

    RemoteMessage.Notification notification = remoteMessage.getNotification();
    if (notification != null) {
      if (title == null) {
        title = trimToNull(notification.getTitle());
      }
      if (body == null) {
        body = trimToNull(notification.getBody());
      }
    }

    SystemNotificationPublisher.publish(
        this, AppNotificationEntry.SOURCE_FCM, title, body, channelId, sentAt);
  }

  @Override
  public void onNewToken(@NonNull String token) {
    super.onNewToken(token);
  }

  private long parseTimestampOrNow(@Nullable String value) {
    if (value == null) {
      return System.currentTimeMillis();
    }
    try {
      long parsed = Long.parseLong(value.trim());
      return parsed > 0L ? parsed : System.currentTimeMillis();
    } catch (Exception ignored) {
      return System.currentTimeMillis();
    }
  }

  @Nullable
  private static String trimToNull(@Nullable String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
