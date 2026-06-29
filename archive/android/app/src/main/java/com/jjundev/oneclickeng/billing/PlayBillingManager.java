package com.jjundev.oneclickeng.billing;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Thin wrapper around BillingClient for one-time INAPP credit products. */
public final class PlayBillingManager {
  @NonNull private final BillingClient billingClient;
  @NonNull private final Handler mainHandler;

  public PlayBillingManager(
      @NonNull Context context, @NonNull PurchasesUpdatedListener purchaseUpdateListener) {
    mainHandler = new Handler(Looper.getMainLooper());
    billingClient =
        BillingClient.newBuilder(context.getApplicationContext())
            .setListener(
                (billingResult, purchases) ->
                    dispatchOnMain(
                        () -> purchaseUpdateListener.onPurchasesUpdated(billingResult, purchases)))
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build();
  }

  public boolean isReady() {
    return billingClient.isReady();
  }

  public void startConnection(@NonNull ConnectionListener listener) {
    if (billingClient.isReady()) {
      dispatchOnMain(listener::onBillingReady);
      return;
    }

    billingClient.startConnection(
        new BillingClientStateListener() {
          @Override
          public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
              dispatchOnMain(listener::onBillingReady);
              return;
            }
            dispatchOnMain(() -> listener.onBillingUnavailable(billingResult));
          }

          @Override
          public void onBillingServiceDisconnected() {
            dispatchOnMain(listener::onBillingDisconnected);
          }
        });
  }

  public void endConnection() {
    billingClient.endConnection();
  }

  public void queryInAppProductDetails(
      @NonNull List<String> productIds, @NonNull ProductDetailsListener listener) {
    if (!billingClient.isReady()) {
      dispatchOnMain(() -> listener.onFailed(buildNotReadyResult("BillingClient not ready")));
      return;
    }

    List<QueryProductDetailsParams.Product> products = new ArrayList<>();
    for (String productId : productIds) {
      String safeProductId = productId == null ? "" : productId.trim();
      if (safeProductId.isEmpty()) {
        continue;
      }
      products.add(
          QueryProductDetailsParams.Product.newBuilder()
              .setProductId(safeProductId)
              .setProductType(BillingClient.ProductType.INAPP)
              .build());
    }

    if (products.isEmpty()) {
      dispatchOnMain(() -> listener.onFailed(buildNotReadyResult("No product ids")));
      return;
    }

    QueryProductDetailsParams params =
        QueryProductDetailsParams.newBuilder().setProductList(products).build();
    billingClient.queryProductDetailsAsync(
        params,
        (billingResult, productDetailsResult) -> {
          if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            dispatchOnMain(() -> listener.onFailed(billingResult));
            return;
          }
          Map<String, ProductDetails> byProductId = new LinkedHashMap<>();
          List<ProductDetails> productDetailsList =
              productDetailsResult != null ? productDetailsResult.getProductDetailsList() : null;
          if (productDetailsList != null) {
            for (ProductDetails details : productDetailsList) {
              byProductId.put(details.getProductId(), details);
            }
          }
          dispatchOnMain(() -> listener.onLoaded(byProductId));
        });
  }

  public void queryInAppPurchases(@NonNull InAppPurchasesListener listener) {
    if (!billingClient.isReady()) {
      dispatchOnMain(
          () ->
              listener.onResult(
                  buildNotReadyResult("BillingClient not ready"), Collections.emptyList()));
      return;
    }

    QueryPurchasesParams params =
        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build();
    billingClient.queryPurchasesAsync(
        params, (billingResult, purchases) -> dispatchOnMain(() -> listener.onResult(billingResult, purchases)));
  }

  @NonNull
  public BillingResult launchBillingFlow(
      @NonNull Activity activity, @Nullable ProductDetails productDetails) {
    if (!billingClient.isReady()) {
      return buildNotReadyResult("BillingClient not ready");
    }
    if (productDetails == null) {
      return buildNotReadyResult("ProductDetails is null");
    }

    BillingFlowParams.ProductDetailsParams productParams =
        BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(productDetails).build();
    BillingFlowParams params =
        BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(Collections.singletonList(productParams))
            .build();
    return billingClient.launchBillingFlow(activity, params);
  }

  public void consumePurchase(
      @NonNull String purchaseToken, @NonNull ConsumeResultListener consumeResultListener) {
    if (!billingClient.isReady()) {
      dispatchOnMain(
          () ->
              consumeResultListener.onConsumeResult(
                  buildNotReadyResult("BillingClient not ready"), purchaseToken));
      return;
    }

    String safeToken = purchaseToken.trim();
    if (safeToken.isEmpty()) {
      dispatchOnMain(
          () ->
              consumeResultListener.onConsumeResult(
                  buildNotReadyResult("Empty purchase token"), purchaseToken));
      return;
    }

    ConsumeParams params = ConsumeParams.newBuilder().setPurchaseToken(safeToken).build();
    billingClient.consumeAsync(
        params,
        (billingResult, outToken) ->
            dispatchOnMain(() -> consumeResultListener.onConsumeResult(billingResult, outToken)));
  }

  private void dispatchOnMain(@NonNull Runnable action) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
      action.run();
      return;
    }
    mainHandler.post(action);
  }

  @NonNull
  private static BillingResult buildNotReadyResult(@NonNull String message) {
    return BillingResult.newBuilder()
        .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
        .setDebugMessage(message)
        .build();
  }

  public interface ConnectionListener {
    void onBillingReady();

    void onBillingDisconnected();

    void onBillingUnavailable(@NonNull BillingResult billingResult);
  }

  public interface ProductDetailsListener {
    void onLoaded(@NonNull Map<String, ProductDetails> productDetailsById);

    void onFailed(@NonNull BillingResult billingResult);
  }

  public interface InAppPurchasesListener {
    void onResult(@NonNull BillingResult billingResult, @NonNull List<Purchase> purchases);
  }

  public interface ConsumeResultListener {
    void onConsumeResult(@NonNull BillingResult billingResult, @NonNull String purchaseToken);
  }
}
