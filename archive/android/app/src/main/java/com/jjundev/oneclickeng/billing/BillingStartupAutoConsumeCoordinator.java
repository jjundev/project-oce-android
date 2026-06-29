package com.jjundev.oneclickeng.billing;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.jjundev.oneclickeng.BuildConfig;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs a one-shot startup flow for stale in-app purchases:
 * query purchases -> enqueue pending -> verify -> consume.
 */
public final class BillingStartupAutoConsumeCoordinator {
  private static final String TAG = "BillingStartupAutoConsume";

  @NonNull private final Context appContext;
  @NonNull private final PlayBillingManager billingManager;
  @NonNull private final CreditPurchaseStore purchaseStore;
  @NonNull private final CreditPurchaseVerifier purchaseVerifier;
  @NonNull private final Set<String> verificationInFlightTokens = new HashSet<>();
  @NonNull private final Set<String> consumptionInFlightTokens = new HashSet<>();

  private boolean billingReady;
  private boolean started;
  private boolean stopped;
  private boolean initialQueryFinished;

  public BillingStartupAutoConsumeCoordinator(@NonNull Context context) {
    appContext = context.getApplicationContext();
    purchaseStore = new CreditPurchaseStore(appContext);
    purchaseVerifier = new CreditPurchaseVerifier(BuildConfig.CREDIT_BILLING_VERIFY_URL);
    billingManager = new PlayBillingManager(appContext, this::onPurchasesUpdated);
  }

  public void start() {
    if (started || stopped) {
      logDebug("start ignored: started=" + started + ", stopped=" + stopped);
      return;
    }
    started = true;
    logDebug("Startup auto-consume started.");
    logDebug(
        "startup state: billingReady="
            + billingReady
            + ", initialQueryFinished="
            + initialQueryFinished
            + ", started="
            + started
            + ", stopped="
            + stopped);
    billingManager.startConnection(
        new PlayBillingManager.ConnectionListener() {
          @Override
          public void onBillingReady() {
            if (stopped) {
              return;
            }
            billingReady = true;
            logDebug("Billing ready. Running initial purchase recovery.");
            runInitialPurchaseRecovery();
          }

          @Override
          public void onBillingDisconnected() {
            billingReady = false;
            logWarn("Billing disconnected during startup auto-consume.");
            logShutdownState("billing_disconnected");
            tryShutdownIfIdle();
          }

          @Override
          public void onBillingUnavailable(@NonNull BillingResult billingResult) {
            billingReady = false;
            initialQueryFinished = true;
            logWarn(
                "Billing unavailable: code="
                    + billingResult.getResponseCode()
                    + ", message="
                    + billingResult.getDebugMessage());
            logDebug("shutdown check after billing_unavailable");
            tryShutdownIfIdle();
          }
        });
  }

  public void shutdown() {
    if (stopped) {
      return;
    }
    stopped = true;
    billingReady = false;
    verificationInFlightTokens.clear();
    consumptionInFlightTokens.clear();
    billingManager.endConnection();
    logDebug("Startup auto-consume stopped.");
  }

  private void runInitialPurchaseRecovery() {
    if (stopped || !billingReady) {
      return;
    }

    billingManager.queryInAppPurchases(
        (billingResult, purchases) -> {
          if (stopped) {
            return;
          }

          if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            List<Purchase> safePurchases =
                purchases == null ? Collections.emptyList() : purchases;
            logDebug("queryInAppPurchases success: purchaseCount=" + safePurchases.size());
            enqueueOwnedPurchases(safePurchases);
          } else {
            logWarn(
                "queryInAppPurchases failed: code="
                    + billingResult.getResponseCode()
                    + ", message="
                    + billingResult.getDebugMessage());
          }
          initialQueryFinished = true;
          logDebug("initialQueryFinished=true; moving to pending processing");
          processPendingPurchases();
        });
  }

  private void onPurchasesUpdated(
      @NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
    if (stopped) {
      return;
    }

    int responseCode = billingResult.getResponseCode();
    if (responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
      enqueueOwnedPurchases(purchases);
      processPendingPurchases();
      return;
    }

    if (responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
      runInitialPurchaseRecovery();
      return;
    }

    if (responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
      logWarn(
          "onPurchasesUpdated ignored: code="
              + responseCode
              + ", message="
              + billingResult.getDebugMessage());
    }
  }

  private void enqueueOwnedPurchases(@NonNull List<Purchase> purchases) {
    String packageName = appContext.getPackageName();
    int queuedEntries = 0;
    int skippedState = 0;
    int skippedEmptyToken = 0;
    int skippedEmptyProducts = 0;
    int skippedEmptyProductId = 0;
    for (Purchase purchase : purchases) {
      if (!shouldQueuePurchaseState(purchase.getPurchaseState())) {
        skippedState++;
        continue;
      }

      String token = normalizeToken(purchase.getPurchaseToken());
      if (token.isEmpty()) {
        skippedEmptyToken++;
        continue;
      }

      List<String> productIds = purchase.getProducts();
      if (productIds == null || productIds.isEmpty()) {
        skippedEmptyProducts++;
        continue;
      }

      for (String productId : productIds) {
        String safeProductId = productId == null ? "" : productId.trim();
        if (safeProductId.isEmpty()) {
          skippedEmptyProductId++;
          continue;
        }

        purchaseStore.upsertPendingPurchase(
            new CreditPurchaseStore.PendingPurchase(
                packageName,
                safeProductId,
                token,
                purchase.getOrderId(),
                purchase.getPurchaseTime(),
                purchase.getQuantity(),
                purchase.getPurchaseState()));
        queuedEntries++;
      }
    }
    logDebug(
        "enqueueOwnedPurchases summary: purchases="
            + purchases.size()
            + ", queuedEntries="
            + queuedEntries
            + ", skippedState="
            + skippedState
            + ", skippedEmptyToken="
            + skippedEmptyToken
            + ", skippedEmptyProducts="
            + skippedEmptyProducts
            + ", skippedEmptyProductId="
            + skippedEmptyProductId);
  }

  private void processPendingPurchases() {
    if (stopped) {
      return;
    }

    List<CreditPurchaseStore.PendingPurchase> pendingPurchases = purchaseStore.getPendingPurchases();
    logDebug("processPendingPurchases start: pendingCount=" + pendingPurchases.size());

    boolean verifyUrlConfigured = isVerifyUrlConfigured();
    boolean signedIn = isUserSignedIn();
    if (!verifyUrlConfigured || !signedIn) {
      logDebug(
          "Verification preconditions not met. verifyUrlConfigured="
              + verifyUrlConfigured
              + ", signedIn="
              + signedIn
              + ". Skipping for this startup.");
      tryShutdownIfIdle();
      return;
    }
    if (pendingPurchases.isEmpty()) {
      tryShutdownIfIdle();
      return;
    }

    int dispatchCount = 0;
    for (CreditPurchaseStore.PendingPurchase pendingPurchase : pendingPurchases) {
      String token = normalizeToken(pendingPurchase.getPurchaseToken());
      if (token.isEmpty() || verificationInFlightTokens.contains(token)) {
        continue;
      }

      verificationInFlightTokens.add(token);
      dispatchCount++;
      logDebug(
          "dispatch verify: token="
              + maskToken(token)
              + ", productId="
              + pendingPurchase.getProductId()
              + ", verifyInFlight="
              + verificationInFlightTokens.size());
      purchaseVerifier.verifyPurchase(
          pendingPurchase, result -> handleVerificationResult(pendingPurchase, result));
    }
    logDebug("processPendingPurchases dispatched: count=" + dispatchCount);
    tryShutdownIfIdle();
  }

  private void handleVerificationResult(
      @NonNull CreditPurchaseStore.PendingPurchase pendingPurchase,
      @NonNull CreditPurchaseVerifier.VerificationResult verificationResult) {
    if (stopped) {
      return;
    }

    String token = normalizeToken(pendingPurchase.getPurchaseToken());
    verificationInFlightTokens.remove(token);

    CreditPurchaseVerifier.VerificationStatus status = verificationResult.status;
    logDebug(
        "verify result: token="
            + maskToken(token)
            + ", status="
            + status
            + ", message="
            + verificationResult.message);
    if (!shouldKeepPendingPurchase(status)) {
      purchaseStore.removeByPurchaseToken(token);
      logDebug("pending removed: token=" + maskToken(token) + ", status=" + status);
    } else {
      logDebug("pending kept: token=" + maskToken(token) + ", status=" + status);
    }

    if (shouldConsumeAfterVerification(status)) {
      logDebug("consume scheduled: token=" + maskToken(token) + ", status=" + status);
      consumeImmediately(token);
      return;
    }
    logDebug("consume skipped by status: token=" + maskToken(token) + ", status=" + status);

    tryShutdownIfIdle();
  }

  private void consumeImmediately(@NonNull String purchaseToken) {
    logDebug(
        "consume attempt: token="
            + maskToken(purchaseToken)
            + ", billingReady="
            + billingReady
            + ", consumeInFlight="
            + consumptionInFlightTokens.size());
    if (stopped || !billingReady) {
      logWarn("consume skipped: billing not ready.");
      tryShutdownIfIdle();
      return;
    }

    String safeToken = normalizeToken(purchaseToken);
    if (safeToken.isEmpty() || consumptionInFlightTokens.contains(safeToken)) {
      tryShutdownIfIdle();
      return;
    }

    consumptionInFlightTokens.add(safeToken);
    billingManager.consumePurchase(
        safeToken,
        (billingResult, consumedToken) -> {
          String callbackToken = normalizeToken(consumedToken);
          if (callbackToken.isEmpty()) {
            consumptionInFlightTokens.remove(safeToken);
          } else {
            consumptionInFlightTokens.remove(callbackToken);
          }

          int responseCode = billingResult.getResponseCode();
          if (responseCode == BillingClient.BillingResponseCode.OK
              || responseCode == BillingClient.BillingResponseCode.ITEM_NOT_OWNED) {
            logDebug("consume completed: code=" + responseCode + ", token=" + maskToken(safeToken));
          } else {
            logWarn(
                "consume failed: code="
                    + responseCode
                    + ", message="
                    + billingResult.getDebugMessage()
                    + ", token="
                    + maskToken(safeToken));
          }
          logDebug(
              "consume callback complete: token="
                  + maskToken(safeToken)
                  + ", responseCode="
                  + responseCode
                  + ", consumeInFlight="
                  + consumptionInFlightTokens.size());
          tryShutdownIfIdle();
        });
  }

  private boolean isVerifyUrlConfigured() {
    return BuildConfig.CREDIT_BILLING_VERIFY_URL != null
        && !BuildConfig.CREDIT_BILLING_VERIFY_URL.trim().isEmpty();
  }

  private boolean isUserSignedIn() {
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    return user != null;
  }

  private void tryShutdownIfIdle() {
    if (!initialQueryFinished) {
      logDebug("shutdown deferred: initialQueryFinished=false");
      return;
    }
    if (!verificationInFlightTokens.isEmpty()) {
      logDebug("shutdown deferred: verifyInFlight=" + verificationInFlightTokens.size());
      return;
    }
    if (!consumptionInFlightTokens.isEmpty()) {
      logDebug("shutdown deferred: consumeInFlight=" + consumptionInFlightTokens.size());
      return;
    }
    logDebug("shutdown condition met. closing startup coordinator.");
    shutdown();
  }

  static boolean shouldQueuePurchaseState(int purchaseState) {
    return purchaseState == Purchase.PurchaseState.PURCHASED
        || purchaseState == Purchase.PurchaseState.PENDING;
  }

  static boolean shouldKeepPendingPurchase(
      @NonNull CreditPurchaseVerifier.VerificationStatus status) {
    return status == CreditPurchaseVerifier.VerificationStatus.PENDING
        || status == CreditPurchaseVerifier.VerificationStatus.AUTH_ERROR
        || status == CreditPurchaseVerifier.VerificationStatus.CONFIG_ERROR
        || status == CreditPurchaseVerifier.VerificationStatus.NETWORK_ERROR
        || status == CreditPurchaseVerifier.VerificationStatus.SERVER_ERROR;
  }

  static boolean shouldConsumeAfterVerification(
      @NonNull CreditPurchaseVerifier.VerificationStatus status) {
    return status == CreditPurchaseVerifier.VerificationStatus.GRANTED
        || status == CreditPurchaseVerifier.VerificationStatus.ALREADY_GRANTED;
  }

  @NonNull
  private static String normalizeToken(@Nullable String token) {
    return token == null ? "" : token.trim();
  }

  @NonNull
  private static String maskToken(@NonNull String token) {
    if (token.length() <= 8) {
      return token;
    }
    return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
  }

  private void logDebug(@NonNull String message) {
    Log.d(TAG, message);
  }

  private void logWarn(@NonNull String message) {
    Log.w(TAG, message);
  }

  private void logShutdownState(@NonNull String reason) {
    logDebug(
        "shutdown wait: reason="
            + reason
            + ", initialQueryFinished="
            + initialQueryFinished
            + ", verifyInFlight="
            + verificationInFlightTokens.size()
            + ", consumeInFlight="
            + consumptionInFlightTokens.size());
  }
}



