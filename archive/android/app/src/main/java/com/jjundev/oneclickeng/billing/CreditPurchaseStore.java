package com.jjundev.oneclickeng.billing;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** SharedPreferences-backed queue for purchases awaiting server verification/consumption. */
public final class CreditPurchaseStore {
  private static final String PREF_NAME = "credit_purchase_store";
  private static final String KEY_PENDING_PURCHASES_JSON = "pending_purchases_json";

  @NonNull private final SharedPreferences preferences;
  @NonNull private final Gson gson;
  @NonNull private final Type pendingType;

  public CreditPurchaseStore(@NonNull Context context) {
    this(
        context
            .getApplicationContext()
            .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
        new Gson(),
        new TypeToken<List<PendingPurchase>>() {}.getType());
  }

  CreditPurchaseStore(
      @NonNull SharedPreferences preferences, @NonNull Gson gson, @NonNull Type pendingType) {
    this.preferences = preferences;
    this.gson = gson;
    this.pendingType = pendingType;
  }

  public synchronized void upsertPendingPurchase(@NonNull PendingPurchase pendingPurchase) {
    PendingPurchase normalized = PendingPurchase.normalizedCopy(pendingPurchase);
    if (normalized == null) {
      return;
    }

    List<PendingPurchase> current = readPendingPurchasesInternal();
    Map<String, PendingPurchase> byToken = new LinkedHashMap<>();
    for (PendingPurchase item : current) {
      PendingPurchase safe = PendingPurchase.normalizedCopy(item);
      if (safe != null) {
        byToken.put(safe.getPurchaseToken(), safe);
      }
    }
    byToken.put(normalized.getPurchaseToken(), normalized);
    persistPendingPurchases(new ArrayList<>(byToken.values()));
  }

  public synchronized void removeByPurchaseToken(@Nullable String purchaseToken) {
    String normalizedToken = normalizeToken(purchaseToken);
    if (normalizedToken.isEmpty()) {
      return;
    }

    List<PendingPurchase> current = readPendingPurchasesInternal();
    List<PendingPurchase> remaining = new ArrayList<>();
    for (PendingPurchase item : current) {
      PendingPurchase safe = PendingPurchase.normalizedCopy(item);
      if (safe == null) {
        continue;
      }
      if (!normalizedToken.equals(safe.getPurchaseToken())) {
        remaining.add(safe);
      }
    }
    persistPendingPurchases(remaining);
  }

  @NonNull
  public synchronized List<PendingPurchase> getPendingPurchases() {
    return new ArrayList<>(readPendingPurchasesInternal());
  }

  @NonNull
  private List<PendingPurchase> readPendingPurchasesInternal() {
    String rawJson = preferences.getString(KEY_PENDING_PURCHASES_JSON, null);
    if (rawJson == null || rawJson.trim().isEmpty()) {
      return new ArrayList<>();
    }

    try {
      List<PendingPurchase> parsed = gson.fromJson(rawJson, pendingType);
      if (parsed == null || parsed.isEmpty()) {
        return new ArrayList<>();
      }
      Map<String, PendingPurchase> deduplicated = new LinkedHashMap<>();
      for (PendingPurchase item : parsed) {
        PendingPurchase safe = PendingPurchase.normalizedCopy(item);
        if (safe != null) {
          deduplicated.put(safe.getPurchaseToken(), safe);
        }
      }
      return new ArrayList<>(deduplicated.values());
    } catch (Exception ignored) {
      return new ArrayList<>();
    }
  }

  private void persistPendingPurchases(@NonNull List<PendingPurchase> purchases) {
    preferences
        .edit()
        .putString(KEY_PENDING_PURCHASES_JSON, gson.toJson(purchases, pendingType))
        .apply();
  }

  @NonNull
  private static String normalizeToken(@Nullable String purchaseToken) {
    return purchaseToken == null ? "" : purchaseToken.trim();
  }

  /** Pending purchase payload forwarded to server verification API. */
  public static final class PendingPurchase {
    @Nullable private String packageName;
    @Nullable private String productId;
    @Nullable private String purchaseToken;
    @Nullable private String orderId;
    private long purchaseTimeMillis;
    private int quantity;
    private int purchaseState;

    public PendingPurchase() {}

    public PendingPurchase(
        @NonNull String packageName,
        @NonNull String productId,
        @NonNull String purchaseToken,
        @Nullable String orderId,
        long purchaseTimeMillis,
        int quantity,
        int purchaseState) {
      this.packageName = packageName;
      this.productId = productId;
      this.purchaseToken = purchaseToken;
      this.orderId = orderId;
      this.purchaseTimeMillis = purchaseTimeMillis;
      this.quantity = quantity;
      this.purchaseState = purchaseState;
    }

    @NonNull
    public String getPackageName() {
      return packageName == null ? "" : packageName.trim();
    }

    @NonNull
    public String getProductId() {
      return productId == null ? "" : productId.trim();
    }

    @NonNull
    public String getPurchaseToken() {
      return normalizeToken(purchaseToken);
    }

    @NonNull
    public String getOrderId() {
      return orderId == null ? "" : orderId.trim();
    }

    public long getPurchaseTimeMillis() {
      return Math.max(0L, purchaseTimeMillis);
    }

    public int getQuantity() {
      return Math.max(1, quantity);
    }

    public int getPurchaseState() {
      return purchaseState;
    }

    @Nullable
    public static PendingPurchase normalizedCopy(@Nullable PendingPurchase source) {
      if (source == null) {
        return null;
      }

      String safePackageName = source.packageName == null ? "" : source.packageName.trim();
      String safeProductId = source.productId == null ? "" : source.productId.trim();
      String safePurchaseToken = normalizeToken(source.purchaseToken);
      if (safePackageName.isEmpty() || safeProductId.isEmpty() || safePurchaseToken.isEmpty()) {
        return null;
      }

      String safeOrderId = source.orderId == null ? "" : source.orderId.trim();
      long safePurchaseTimeMillis = Math.max(0L, source.purchaseTimeMillis);
      int safeQuantity = Math.max(1, source.quantity);
      int safePurchaseState = source.purchaseState;

      return new PendingPurchase(
          safePackageName,
          safeProductId,
          safePurchaseToken,
          safeOrderId.isEmpty() ? null : safeOrderId,
          safePurchaseTimeMillis,
          safeQuantity,
          safePurchaseState);
    }

    @NonNull
    @Override
    public String toString() {
      return "PendingPurchase{"
          + "productId='"
          + getProductId()
          + "', token='"
          + maskToken(getPurchaseToken())
          + "', state="
          + getPurchaseState()
          + ", quantity="
          + getQuantity()
          + '}';
    }

    @NonNull
    private static String maskToken(@NonNull String token) {
      if (token.length() <= 8) {
        return token;
      }
      return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
  }
}
