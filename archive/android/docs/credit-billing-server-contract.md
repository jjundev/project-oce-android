# Credit Billing Server Contract

## Summary
- Purchase type: Google Play **INAPP (consumable)**.
- App verifies purchase through backend endpoint before consuming token.
- Backend owns SKU-to-credit mapping and idempotency.
- Refund clawback is supported and may drive credit balance negative.

## Endpoint
- Method: `POST`
- URL: `{CREDIT_BILLING_VERIFY_URL}`
- Auth: `Authorization: Bearer <firebase_id_token>`
- Content-Type: `application/json`

## Client Setup
1. `local.properties`에 아래 키를 반드시 설정한다.
   - `CREDIT_BILLING_VERIFY_URL=https://<your-host>/...`
2. 누락 시 앱 동작:
   - CreditStore 결제 진입이 차단됨.
   - 검증 단계에서 `CONFIG_ERROR`가 반환되어 크레딧이 지급되지 않음.
3. 확인 방법:
   - `app/build.gradle`의 `BuildConfig.CREDIT_BILLING_VERIFY_URL` 주입 라인 확인.
   - 앱 로그에서 `CREDIT_BILLING_VERIFY_URL is empty` 또는 config failure 키워드 확인.

## Request JSON
```json
{
  "packageName": "com.jjundev.oneclickeng",
  "productId": "credit_10_v2",
  "purchaseToken": "token_from_play",
  "orderId": "GPA.1234-5678-9012-34567",
  "purchaseTimeMillis": 1739491200000,
  "quantity": 1,
  "purchaseState": 1
}
```

## Response JSON
```json
{
  "status": "GRANTED",
  "grantedCredits": 10,
  "currentCreditBalance": 134,
  "eventId": "evt_20260228_abc123",
  "purchaseToken": "token_from_play",
  "message": "ok"
}
```

## `status` Enum
- `GRANTED`: credit granted in this request.
- `ALREADY_GRANTED`: same purchase token already granted previously.
- `PENDING`: Play purchase pending; no grant yet.
- `REJECTED`: verification failed by policy/rule.
- `INVALID`: invalid token/product/package mismatch.
- `SERVER_ERROR`: temporary backend/server-side validation failure (including Google Play API auth/config issues). Client must keep token pending and retry later.

## Server Processing Rules
1. Verify Firebase ID token and resolve `uid`.
2. Verify purchase token via Google Play Developer API.
   - If Google Play responds `401/403` (authorization/configuration issue), server returns HTTP `200` with `status=SERVER_ERROR` and does not grant credits.
3. Resolve granted credits from server-side SKU mapping (`credit_10_v2`, `credit_20`, `credit_50`).
4. Use `purchaseToken` as idempotency key.
5. Apply Firestore transaction:
6. Update `users/{uid}.credit` with increment/decrement.
7. Upsert `billing_purchases/{purchaseToken}` status/history.
8. Write immutable ledger event under `billing_ledger/{uid}/events/{eventId}`.

## Firestore Schema (Server-owned)
- `billing_purchases/{purchaseToken}`
  - `uid`
  - `product_id`
  - `status` (`granted`, `already_granted`, `pending`, `rejected`, `invalid`, `refunded`, `clawed_back`)
  - `granted_credits`
  - `clawed_back_credits`
  - `order_id`
  - `updated_at_epoch_ms`
- `billing_ledger/{uid}/events/{eventId}`
  - `purchase_token`
  - `delta_credits` (positive for grant, negative for clawback)
  - `reason` (`purchase_grant`, `refund_clawback`)
  - `created_at_epoch_ms`

## Refund / Clawback
1. Detect via RTDN + Voided Purchases API.
2. If purchase was previously granted, decrement by granted amount.
3. Negative credit balance is allowed.
4. Same purchase token can be clawed back only once.
5. Failed clawback should be retried by backend queue.

## Client Expectations
1. On `GRANTED` or `ALREADY_GRANTED`, app consumes purchase token.
2. On `PENDING`, token stays pending locally.
3. On `REJECTED`/`INVALID`, app does not consume token.
4. On `SERVER_ERROR` and network/auth/server transport errors, app keeps token in pending queue and retries on next app/session entry.

## Operational Troubleshooting
1. If logs show `apiStatus=401` / `apiStatus=403` or `apiReason=permissionDenied`, check Play Console `Setup > API access` first.
2. Confirm the linked GCP project is `one-click-eng`.
3. Confirm `play-purchase-verifier@one-click-eng.iam.gserviceaccount.com` has app access for `com.jjundev.oneclickeng` and purchase/order read permissions.
