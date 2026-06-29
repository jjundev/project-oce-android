package com.jjundev.oneclickeng.fragment;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.jjundev.oneclickeng.BuildConfig;
import com.jjundev.oneclickeng.R;
import com.jjundev.oneclickeng.billing.CreditPurchaseStore;
import com.jjundev.oneclickeng.billing.CreditPurchaseVerifier;
import com.jjundev.oneclickeng.billing.PlayBillingManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CreditStoreFragment extends Fragment {
  private static final String TAG = "CreditStoreFragment";

  private static final String PRODUCT_CREDIT_10 = "credit_10_v2";
  private static final String PRODUCT_CREDIT_20 = "credit_20";
  private static final String PRODUCT_CREDIT_50 = "credit_50";
  private static final List<String> CREDIT_PRODUCT_IDS = Arrays.asList(PRODUCT_CREDIT_10, PRODUCT_CREDIT_20,
      PRODUCT_CREDIT_50);

  private static final String PRICE_LOADING = "가격 확인 중...";
  private static final String PRICE_UNAVAILABLE = "구매 준비 중";
  private static final String MESSAGE_PRECHECK_CONFIG = "결제 설정에 문제가 있어요. 잠시 후 다시 시도해주세요.";
  private static final String MESSAGE_PRECHECK_AUTH = "로그인 후 결제를 진행해주세요.";
  private static final String MESSAGE_PRECHECK_BILLING = "결제 서비스를 준비 중이에요";
  private static final String MESSAGE_PRECHECK_PRODUCT = "상품 정보를 불러오는 중이에요.";
  private static final String MESSAGE_VERIFICATION_RETRYABLE = "결제 확인이 지연되고 있어요. 잠시 후 자동으로 다시 시도할게요.";
  private static final String MESSAGE_ALREADY_OWNED_RECOVERY = "이미 보유한 상품을 정리하고 있어요. 잠시만 기다려주세요.";

  @Nullable
  private RecyclerView rvProducts;
  @Nullable
  private CreditStoreAdapter adapter;
  @Nullable
  private PlayBillingManager billingManager;
  @Nullable
  private CreditPurchaseStore purchaseStore;
  @Nullable
  private CreditPurchaseVerifier purchaseVerifier;

  @NonNull
  private final List<CreditProduct> products = new ArrayList<>();
  @NonNull
  private final Set<String> verificationInFlightTokens = new HashSet<>();
  @NonNull
  private final Set<String> consumptionInFlightTokens = new HashSet<>();
  @NonNull
  private final Set<String> retryableFailureNotifiedTokens = new HashSet<>();

  private boolean billingReady;
  private boolean billingUnavailableNotified;
  private boolean isVerifyUrlConfigured;
  private boolean hasShownConfigErrorToast;
  private boolean hasShownAuthErrorToast;

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.fragment_credit_store, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    initializeProducts();

    View btnBack = view.findViewById(R.id.btn_back);
    btnBack.setOnClickListener(v -> navigateBackFromCreditStore());

    requireActivity()
        .getOnBackPressedDispatcher()
        .addCallback(
            getViewLifecycleOwner(),
            new OnBackPressedCallback(true) {
              @Override
              public void handleOnBackPressed() {
                navigateBackFromCreditStore();
              }
            });

    purchaseStore = new CreditPurchaseStore(requireContext());
    purchaseVerifier = new CreditPurchaseVerifier(BuildConfig.CREDIT_BILLING_VERIFY_URL);

    rvProducts = view.findViewById(R.id.rv_scripts);
    adapter = new CreditStoreAdapter(products, this::onCreditProductClicked);
    rvProducts.setLayoutManager(new GridLayoutManager(requireContext(), 2));
    rvProducts.setNestedScrollingEnabled(false);
    rvProducts.setAdapter(adapter);
    refreshPurchaseEntryState();

    billingManager = new PlayBillingManager(requireContext(), this::onPurchasesUpdated);
    billingManager.startConnection(
        new PlayBillingManager.ConnectionListener() {
          @Override
          public void onBillingReady() {
            billingReady = true;
            billingUnavailableNotified = false;
            refreshPurchaseEntryState();
            requestProductDetails();
            recoverOwnedPurchasesAndProcessPending();
          }

          @Override
          public void onBillingDisconnected() {
            billingReady = false;
            refreshPurchaseEntryState();
            logDebug("Billing service disconnected.");
          }

          @Override
          public void onBillingUnavailable(@NonNull BillingResult billingResult) {
            billingReady = false;
            refreshPurchaseEntryState();
            if (!billingUnavailableNotified) {
              billingUnavailableNotified = true;
              showToastSafe("잠시 후 다시 시도해주세요");
            }
            logDebug(
                "Billing unavailable: "
                    + billingResult.getResponseCode()
                    + " / "
                    + billingResult.getDebugMessage());
          }
        });
  }

  @Override
  public void onResume() {
    super.onResume();
    refreshPurchaseEntryState();
    recoverOwnedPurchasesAndProcessPending();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    verificationInFlightTokens.clear();
    consumptionInFlightTokens.clear();
    retryableFailureNotifiedTokens.clear();
    rvProducts = null;
    adapter = null;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    if (billingManager != null) {
      billingManager.endConnection();
      billingManager = null;
    }
  }

  private void initializeProducts() {
    products.clear();
    products.add(new CreditProduct("10 크레딧", "가장 저렴해요", PRODUCT_CREDIT_10, "300원", PRICE_LOADING));
    products.add(new CreditProduct("20 크레딧", "무난한 선택!", PRODUCT_CREDIT_20, "500원", PRICE_LOADING));
    products.add(new CreditProduct("50 크레딧", "가장 효율이 좋아요", PRODUCT_CREDIT_50, "1000원", PRICE_LOADING));
  }

  private void requestProductDetails() {
    PlayBillingManager manager = billingManager;
    if (manager == null || !billingReady) {
      return;
    }

    manager.queryInAppProductDetails(
        CREDIT_PRODUCT_IDS,
        new PlayBillingManager.ProductDetailsListener() {
          @Override
          public void onLoaded(@NonNull Map<String, ProductDetails> productDetailsById) {
            for (CreditProduct product : products) {
              ProductDetails details = productDetailsById.get(product.productId);
              product.productDetails = details;
              product.priceText = resolveFormattedPrice(details, product.fallbackPriceText);
            }
            refreshPurchaseEntryState();
          }

          @Override
          public void onFailed(@NonNull BillingResult billingResult) {
            for (CreditProduct product : products) {
              product.productDetails = null;
              product.priceText = PRICE_UNAVAILABLE;
            }
            refreshPurchaseEntryState();
            logDebug(
                "Failed to query product details: "
                    + billingResult.getResponseCode()
                    + " / "
                    + billingResult.getDebugMessage());
            showToastSafe("상품 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.");
          }
        });
  }

  @NonNull
  private static String resolveFormattedPrice(
      @Nullable ProductDetails productDetails, @NonNull String fallbackPrice) {
    if (productDetails == null) {
      return fallbackPrice;
    }
    ProductDetails.OneTimePurchaseOfferDetails oneTimeOffer = productDetails.getOneTimePurchaseOfferDetails();
    if (oneTimeOffer == null) {
      return fallbackPrice;
    }
    String formattedPrice = oneTimeOffer.getFormattedPrice();
    if (formattedPrice == null || formattedPrice.trim().isEmpty()) {
      return fallbackPrice;
    }
    return formattedPrice.trim();
  }

  @Nullable
  static String getPreflightBlockMessage(
      @Nullable String verifyUrl,
      boolean userSignedIn,
      boolean billingAvailable,
      boolean productDetailsReady) {
    if (!isVerifyUrlConfigured(verifyUrl)) {
      return MESSAGE_PRECHECK_CONFIG;
    }
    if (!userSignedIn) {
      return MESSAGE_PRECHECK_AUTH;
    }
    if (!billingAvailable) {
      return MESSAGE_PRECHECK_BILLING;
    }
    if (!productDetailsReady) {
      return MESSAGE_PRECHECK_PRODUCT;
    }
    return null;
  }

  static boolean shouldKeepPendingPurchase(
      @NonNull CreditPurchaseVerifier.VerificationStatus status) {
    return status == CreditPurchaseVerifier.VerificationStatus.PENDING
        || status == CreditPurchaseVerifier.VerificationStatus.AUTH_ERROR
        || status == CreditPurchaseVerifier.VerificationStatus.CONFIG_ERROR
        || status == CreditPurchaseVerifier.VerificationStatus.NETWORK_ERROR
        || status == CreditPurchaseVerifier.VerificationStatus.SERVER_ERROR;
  }

  static boolean shouldRecoverOwnedPurchaseOnLaunchResult(int responseCode) {
    return responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED;
  }

  static boolean shouldForceConsumePurchaseState(int purchaseState) {
    return purchaseState == Purchase.PurchaseState.PURCHASED
        || purchaseState == Purchase.PurchaseState.PENDING;
  }

  static boolean doesPurchaseMatchTargetProduct(
      @Nullable String targetProductId, @Nullable List<String> purchaseProductIds) {
    String safeTargetProductId = normalizeProductId(targetProductId);
    if (safeTargetProductId.isEmpty()) {
      return true;
    }
    if (purchaseProductIds == null || purchaseProductIds.isEmpty()) {
      return false;
    }
    for (String productId : purchaseProductIds) {
      if (safeTargetProductId.equals(normalizeProductId(productId))) {
        return true;
      }
    }
    return false;
  }

  private static boolean isVerifyUrlConfigured(@Nullable String verifyUrl) {
    return verifyUrl != null && !verifyUrl.trim().isEmpty();
  }

  @NonNull
  private static String normalizeProductId(@Nullable String productId) {
    return productId == null ? "" : productId.trim();
  }

  private void refreshPurchaseEntryState() {
    isVerifyUrlConfigured = isVerifyUrlConfigured(BuildConfig.CREDIT_BILLING_VERIFY_URL);
    if (isVerifyUrlConfigured) {
      hasShownConfigErrorToast = false;
    }
    boolean signedIn = isUserSignedIn();
    if (signedIn) {
      hasShownAuthErrorToast = false;
    }
    updateProductCardStates(signedIn);
    notifyAdapterChanged();
  }

  private void updateProductCardStates(boolean signedIn) {
    boolean billingAvailable = billingReady && billingManager != null;
    for (CreditProduct product : products) {
      product.canAttemptPurchase = getPreflightBlockMessage(
          BuildConfig.CREDIT_BILLING_VERIFY_URL,
          signedIn,
          billingAvailable,
          product.productDetails != null) == null;
    }
  }

  private boolean isUserSignedIn() {
    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
    return user != null;
  }

  private void onCreditProductClicked(@NonNull CreditProduct product) {
    if (!isAdded()) {
      return;
    }

    String preflightBlockMessage = getPreflightBlockMessage(
        BuildConfig.CREDIT_BILLING_VERIFY_URL,
        isUserSignedIn(),
        billingReady && billingManager != null,
        product.productDetails != null);
    if (preflightBlockMessage != null) {
      showPreflightBlockToast(preflightBlockMessage);
      return;
    }

    Activity hostActivity = getActivity();
    if (hostActivity == null) {
      return;
    }

    PlayBillingManager manager = billingManager;
    if (manager == null) {
      showToastSafe(MESSAGE_PRECHECK_BILLING);
      return;
    }

    BillingResult launchResult = manager.launchBillingFlow(hostActivity, product.productDetails);
    int responseCode = launchResult.getResponseCode();
    logDebug(
        "launchBillingFlow result: responseCode="
            + responseCode
            + ", productId="
            + product.productId
            + ", message="
            + launchResult.getDebugMessage());
    if (responseCode == BillingClient.BillingResponseCode.OK
        || responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
      return;
    }
    if (shouldRecoverOwnedPurchaseOnLaunchResult(responseCode)) {
      showToastSafe(MESSAGE_ALREADY_OWNED_RECOVERY);
      recoverOwnedPurchasesAndProcessPending(product.productId, true);
      return;
    }

    logDebug(
        "Failed to launch billing flow: "
            + responseCode
            + " / "
            + launchResult.getDebugMessage());
    showToastSafe("결제를 시작하지 못했어요");
  }

  private void showPreflightBlockToast(@NonNull String message) {
    if (MESSAGE_PRECHECK_CONFIG.equals(message)) {
      if (!hasShownConfigErrorToast) {
        hasShownConfigErrorToast = true;
        showToastSafe(message);
      }
      logDebug("Purchase preflight blocked due to missing verify url.");
      return;
    }
    if (MESSAGE_PRECHECK_AUTH.equals(message)) {
      if (!hasShownAuthErrorToast) {
        hasShownAuthErrorToast = true;
        showToastSafe(message);
      }
      logDebug("Purchase preflight blocked due to signed-out state.");
      return;
    }
    showToastSafe(message);
  }

  private void onPurchasesUpdated(
      @NonNull BillingResult billingResult, @Nullable List<Purchase> purchases) {
    int responseCode = billingResult.getResponseCode();
    logDebug("onPurchasesUpdated: responseCode=" + responseCode
        + ", purchaseCount=" + (purchases != null ? purchases.size() : "null"));
    if (responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
      for (Purchase purchase : purchases) {
        handleIncomingPurchase(purchase, true);
      }
      processPendingPurchases();
      return;
    }

    if (responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
      showToastSafe("결제를 취소했어요");
      return;
    }

    if (responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
      showToastSafe(MESSAGE_ALREADY_OWNED_RECOVERY);
      recoverOwnedPurchasesAndProcessPending(null, true);
      return;
    }

    logDebug(
        "onPurchasesUpdated failed: "
            + responseCode
            + " / "
            + billingResult.getDebugMessage());
    showToastSafe("결제 처리 중 오류가 발생했어요");
  }

  private void recoverOwnedPurchasesAndProcessPending() {
    recoverOwnedPurchasesAndProcessPending(null, false);
  }

  private void recoverOwnedPurchasesAndProcessPending(
      @Nullable String targetProductId, boolean forceConsumeImmediately) {
    PlayBillingManager manager = billingManager;
    if (manager == null || !billingReady) {
      logDebug("recoverOwnedPurchases skipped: billing not ready.");
      return;
    }

    String safeTargetProductId = normalizeProductId(targetProductId);
    manager.queryInAppPurchases(
        (billingResult, purchases) -> {
          logDebug(
              "recoverOwnedPurchases query result: responseCode="
                  + billingResult.getResponseCode()
                  + ", purchaseCount="
                  + (purchases != null ? purchases.size() : "null")
                  + ", forceConsume="
                  + forceConsumeImmediately
                  + ", targetProductId="
                  + (safeTargetProductId.isEmpty() ? "(all)" : safeTargetProductId));
          if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            if (purchases != null) {
              for (Purchase purchase : purchases) {
                handleIncomingPurchase(purchase, false);
              }
            }
          } else {
            logDebug(
                "queryInAppPurchases failed: "
                    + billingResult.getResponseCode()
                    + " / "
                    + billingResult.getDebugMessage());
          }
          processPendingPurchases();
          if (forceConsumeImmediately && purchases != null && !purchases.isEmpty()) {
            forceConsumeOwnedPurchases(purchases, safeTargetProductId);
          }
        });
  }

  private void forceConsumeOwnedPurchases(
      @NonNull List<Purchase> purchases, @Nullable String targetProductId) {
    String safeTargetProductId = normalizeProductId(targetProductId);
    int scheduledCount = 0;
    for (Purchase purchase : purchases) {
      int state = purchase.getPurchaseState();
      List<String> productIds = purchase.getProducts();
      if (!shouldForceConsumePurchaseState(state)
          || !doesPurchaseMatchTargetProduct(safeTargetProductId, productIds)) {
        continue;
      }

      String token = purchase.getPurchaseToken() == null ? "" : purchase.getPurchaseToken().trim();
      if (token.isEmpty()) {
        continue;
      }

      logDebug(
          "forceConsume scheduled: token="
              + maskToken(token)
              + ", state="
              + state
              + ", targetProductId="
              + (safeTargetProductId.isEmpty() ? "(all)" : safeTargetProductId)
              + ", products="
              + productIds);
      consumePurchaseImmediately(token);
      scheduledCount++;
    }
    logDebug(
        "forceConsume summary: scheduled="
            + scheduledCount
            + ", purchaseCount="
            + purchases.size()
            + ", targetProductId="
            + (safeTargetProductId.isEmpty() ? "(all)" : safeTargetProductId));
  }

  private void handleIncomingPurchase(@NonNull Purchase purchase, boolean fromUserFlow) {
    int state = purchase.getPurchaseState();
    logDebug("handleIncomingPurchase: state=" + state
        + ", token=" + maskToken(purchase.getPurchaseToken())
        + ", products=" + purchase.getProducts());
    if (state == Purchase.PurchaseState.PENDING) {
      enqueuePendingPurchase(purchase);
      if (fromUserFlow) {
        showToastSafe("결제 승인을 기다리고 있어요");
      }
      return;
    }

    if (state == Purchase.PurchaseState.PURCHASED) {
      enqueuePendingPurchase(purchase);
      if (fromUserFlow) {
        showToastSafe("구매를 확인하고 있어요...\n잠시만 기다려 주세요");
      }
      return;
    }

    logDebug("Ignoring purchase with state=" + state + ", token=" + maskToken(purchase.getPurchaseToken()));
  }

  private void enqueuePendingPurchase(@NonNull Purchase purchase) {
    CreditPurchaseStore store = purchaseStore;
    if (store == null) {
      logDebug("enqueuePendingPurchase: store is null, skipping.");
      return;
    }

    List<String> productIds = purchase.getProducts();
    if (productIds == null || productIds.isEmpty()) {
      logDebug("enqueuePendingPurchase: productIds is null/empty, skipping.");
      return;
    }

    String packageName = requireContext().getPackageName();
    for (String productId : productIds) {
      String safeProductId = productId == null ? "" : productId.trim();
      if (safeProductId.isEmpty()) {
        continue;
      }
      CreditPurchaseStore.PendingPurchase pendingPurchase = new CreditPurchaseStore.PendingPurchase(
          packageName,
          safeProductId,
          purchase.getPurchaseToken(),
          purchase.getOrderId(),
          purchase.getPurchaseTime(),
          purchase.getQuantity(),
          purchase.getPurchaseState());
      store.upsertPendingPurchase(pendingPurchase);
    }
  }

  private void processPendingPurchases() {
    CreditPurchaseStore store = purchaseStore;
    CreditPurchaseVerifier verifier = purchaseVerifier;
    if (store == null || verifier == null) {
      logDebug("processPendingPurchases: store or verifier is null, skipping.");
      return;
    }
    List<CreditPurchaseStore.PendingPurchase> pendingPurchases = store.getPendingPurchases();
    logDebug("processPendingPurchases: pendingCount=" + pendingPurchases.size());
    if (pendingPurchases.isEmpty()) {
      return;
    }

    if (!isVerifyUrlConfigured) {
      logDebug("processPendingPurchases: verifyUrl not configured, blocking.");
      showPreflightBlockToast(MESSAGE_PRECHECK_CONFIG);
      return;
    }
    if (!isUserSignedIn()) {
      logDebug("processPendingPurchases: user not signed in, blocking.");
      showPreflightBlockToast(MESSAGE_PRECHECK_AUTH);
      return;
    }
    for (CreditPurchaseStore.PendingPurchase pendingPurchase : pendingPurchases) {
      String token = pendingPurchase.getPurchaseToken();
      if (token.isEmpty()) {
        logDebug("processPendingPurchases: skipping empty token.");
        continue;
      }
      if (verificationInFlightTokens.contains(token)) {
        logDebug("processPendingPurchases: skipping in-flight token=" + maskToken(token));
        continue;
      }
      verificationInFlightTokens.add(token);
      logDebug("processPendingPurchases: dispatching verification for token=" + maskToken(token)
          + ", productId=" + pendingPurchase.getProductId());
      verifier.verifyPurchase(
          pendingPurchase, result -> handleVerificationResult(pendingPurchase, result));
    }
  }

  private void handleVerificationResult(
      @NonNull CreditPurchaseStore.PendingPurchase pendingPurchase,
      @NonNull CreditPurchaseVerifier.VerificationResult verificationResult) {
    String token = pendingPurchase.getPurchaseToken();
    CreditPurchaseVerifier.VerificationStatus status = verificationResult.status;
    verificationInFlightTokens.remove(token);

    CreditPurchaseStore store = purchaseStore;
    if (!shouldKeepPendingPurchase(status) && store != null) {
      store.removeByPurchaseToken(token);
      retryableFailureNotifiedTokens.remove(token);
    }

    switch (verificationResult.status) {
      case GRANTED:
      case ALREADY_GRANTED:
        consumePurchaseImmediately(token);
        showToastSafe("크레딧이 충전되었어요.");
        return;
      case PENDING:
        return;
      case REJECTED:
      case INVALID:
        showToastSafe("결제 검증에 실패했어요");
        logDebug(
            "Verification rejected for token="
                + maskToken(token)
                + ", status="
                + verificationResult.status
                + ", message="
                + verificationResult.message);
        return;
      case AUTH_ERROR:
        if (!hasShownAuthErrorToast) {
          hasShownAuthErrorToast = true;
          showToastSafe(MESSAGE_PRECHECK_AUTH);
        }
        logDebug(
            "Verification auth failure for token="
                + maskToken(token)
                + ", message="
                + verificationResult.message);
        return;
      case CONFIG_ERROR:
        if (!hasShownConfigErrorToast) {
          hasShownConfigErrorToast = true;
          showToastSafe(MESSAGE_PRECHECK_CONFIG);
        }
        logDebug(
            "Verification config failure for token="
                + maskToken(token)
                + ", message="
                + verificationResult.message);
        return;
      case NETWORK_ERROR:
      case SERVER_ERROR:
        if (retryableFailureNotifiedTokens.add(token)) {
          showToastSafe(MESSAGE_VERIFICATION_RETRYABLE);
        }
        logDebug(
            "Verification retryable failure for token="
                + maskToken(token)
                + ", status="
                + verificationResult.status
                + ", message="
                + verificationResult.message);
        return;
      default:
        logDebug(
            "Verification retryable failure for token="
                + maskToken(token)
                + ", status="
                + verificationResult.status
                + ", message="
                + verificationResult.message);
    }
  }

  private void consumePurchaseImmediately(@Nullable String purchaseToken) {
    PlayBillingManager manager = billingManager;
    if (manager == null || !billingReady) {
      return;
    }

    String safeToken = purchaseToken == null ? "" : purchaseToken.trim();
    if (safeToken.isEmpty() || consumptionInFlightTokens.contains(safeToken)) {
      return;
    }
    consumptionInFlightTokens.add(safeToken);

    manager.consumePurchase(
        safeToken,
        (billingResult, consumedToken) -> {
          String callbackToken = consumedToken == null ? "" : consumedToken.trim();
          if (callbackToken.isEmpty()) {
            consumptionInFlightTokens.remove(safeToken);
          } else {
            consumptionInFlightTokens.remove(callbackToken);
          }
          int responseCode = billingResult.getResponseCode();
          if (responseCode == BillingClient.BillingResponseCode.OK
              || responseCode == BillingClient.BillingResponseCode.ITEM_NOT_OWNED) {
            logDebug(
                "consumePurchase completed: token="
                    + maskToken(safeToken)
                    + ", code="
                    + responseCode);
            return;
          }

          logDebug(
              "consumePurchase failed: token="
                  + maskToken(safeToken)
                  + ", code="
                  + responseCode
                  + ", message="
                  + billingResult.getDebugMessage());
          showToastSafe("크레딧은 충전되었지만 구매 정리가 지연되고 있어요. 잠시 후 자동으로 재시도할게요.");
        });
  }

  private void notifyAdapterChanged() {
    CreditStoreAdapter adapterRef = adapter;
    if (adapterRef != null) {
      adapterRef.notifyDataSetChanged();
    }
  }

  private void showToastSafe(@NonNull String message) {
    if (!isAdded()) {
      return;
    }
    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
  }

  private void navigateBackFromCreditStore() {
    if (!isAdded()) {
      return;
    }

    NavController navController = NavHostFragment.findNavController(this);
    if (navController.popBackStack()) {
      return;
    }

    int startDestinationId = navController.getGraph().getStartDestinationId();
    NavOptions navOptions = new NavOptions.Builder()
        .setLaunchSingleTop(true)
        .setRestoreState(true)
        .setPopUpTo(startDestinationId, false, true)
        .build();

    try {
      navController.navigate(R.id.studyModeSelectFragment, null, navOptions);
    } catch (IllegalArgumentException exception) {
      logDebug(
          "Failed to navigate to fallback destination from CreditStore: "
              + exception.getMessage());
    }
  }

  private void logDebug(@NonNull String message) {
    Log.d(TAG, message);
  }

  @NonNull
  private static String maskToken(@NonNull String token) {
    String safe = token.trim();
    if (safe.length() <= 8) {
      return safe;
    }
    return safe.substring(0, 4) + "..." + safe.substring(safe.length() - 4);
  }

  private static class CreditStoreAdapter
      extends RecyclerView.Adapter<CreditStoreAdapter.CreditStoreViewHolder> {

    @NonNull
    private final OnProductClickListener clickListener;
    @NonNull
    private final List<CreditProduct> items;

    CreditStoreAdapter(
        @NonNull List<CreditProduct> items, @NonNull OnProductClickListener clickListener) {
      this.items = items;
      this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public CreditStoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
      View view = LayoutInflater.from(parent.getContext())
          .inflate(R.layout.item_credit_store_product, parent, false);
      return new CreditStoreViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CreditStoreViewHolder holder, int position) {
      CreditProduct item = items.get(position);
      holder.tvProductName.setText(item.productName);
      holder.tvProductSubtitle.setText(item.productSubtitle);
      holder.tvPrice.setText(item.priceText);
      holder.card.setOnClickListener(v -> clickListener.onProductClicked(item));
      holder.card.setAlpha(item.canAttemptPurchase ? 1.0f : 0.6f);
    }

    @Override
    public int getItemCount() {
      return items.size();
    }

    static class CreditStoreViewHolder extends RecyclerView.ViewHolder {
      @NonNull
      private final View card;
      @NonNull
      private final TextView tvProductName;
      @NonNull
      private final TextView tvProductSubtitle;
      @NonNull
      private final TextView tvPrice;

      CreditStoreViewHolder(@NonNull View itemView) {
        super(itemView);
        card = itemView.findViewById(R.id.card_credit_product);
        tvProductName = itemView.findViewById(R.id.tv_card_title);
        tvProductSubtitle = itemView.findViewById(R.id.tv_card_subtitle);
        tvPrice = itemView.findViewById(R.id.tv_card_price);
      }
    }

    interface OnProductClickListener {
      void onProductClicked(@NonNull CreditProduct product);
    }
  }

  private static class CreditProduct {
    @NonNull
    private final String productName;
    @NonNull
    private final String productSubtitle;
    @NonNull
    private final String productId;
    @NonNull
    private final String fallbackPriceText;
    @Nullable
    private ProductDetails productDetails;
    @NonNull
    private String priceText;
    private boolean canAttemptPurchase;

    CreditProduct(
        @NonNull String productName,
        @NonNull String productSubtitle,
        @NonNull String productId,
        @NonNull String fallbackPriceText,
        @NonNull String initialPriceText) {
      this.productName = productName;
      this.productSubtitle = productSubtitle;
      this.productId = productId;
      this.fallbackPriceText = fallbackPriceText;
      this.priceText = initialPriceText;
      this.canAttemptPurchase = false;
    }
  }
}
