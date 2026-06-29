# FCM Data Message Contract (OneClickEng)

To guarantee notification history capture in-app, backend push must use **data-only FCM messages**.

## Required message type

- Use `data` payload.
- Do not rely on `notification` payload auto-display for production delivery.

## Supported keys

- `title`: notification title (optional)
- `body`: notification body (optional)
- `channelId`: Android notification channel id (optional)
- `sentAt`: epoch millis as string (optional)

## Example payload

```json
{
  "to": "<fcm_token>",
  "data": {
    "title": "새 학습 알림",
    "body": "오늘 학습을 시작해볼까요?",
    "channelId": "oneclickeng_general",
    "sentAt": "1762358400000"
  }
}
```

## Why this contract exists

- OneClickEng stores notification history only when message handling runs through `OneClickMessagingService`.
- Data messages ensure foreground/background handling consistently reaches app-side storage and publishing logic.
