package com.jjundev.oneclickeng.fragment;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.Purchase;
import com.jjundev.oneclickeng.billing.CreditPurchaseVerifier;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class CreditStoreFragmentPreflightTest {

  @Test
  public void getPreflightBlockMessage_verifyUrlMissing_blocksLaunch() {
    String message = CreditStoreFragment.getPreflightBlockMessage("", true, true, true);

    assertNotNull(message);
  }

  @Test
  public void getPreflightBlockMessage_userSignedOut_blocksLaunch() {
    String message =
        CreditStoreFragment.getPreflightBlockMessage("https://example.com/verify", false, true, true);

    assertNotNull(message);
  }

  @Test
  public void getPreflightBlockMessage_billingNotReady_blocksLaunch() {
    String message =
        CreditStoreFragment.getPreflightBlockMessage("https://example.com/verify", true, false, true);

    assertNotNull(message);
  }

  @Test
  public void getPreflightBlockMessage_productNotReady_blocksLaunch() {
    String message =
        CreditStoreFragment.getPreflightBlockMessage("https://example.com/verify", true, true, false);

    assertNotNull(message);
  }

  @Test
  public void getPreflightBlockMessage_allReady_allowsLaunch() {
    String message =
        CreditStoreFragment.getPreflightBlockMessage("https://example.com/verify", true, true, true);

    assertNull(message);
  }

  @Test
  public void shouldKeepPendingPurchase_retryableStatus_returnsTrue() {
    assertTrue(
        CreditStoreFragment.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.NETWORK_ERROR));
    assertTrue(
        CreditStoreFragment.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.SERVER_ERROR));
    assertTrue(
        CreditStoreFragment.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.CONFIG_ERROR));
    assertTrue(
        CreditStoreFragment.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.AUTH_ERROR));
    assertTrue(
        CreditStoreFragment.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.PENDING));
  }

  @Test
  public void shouldKeepPendingPurchase_nonRetryableStatus_returnsFalse() {
    assertFalse(
        CreditStoreFragment.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.GRANTED));
    assertFalse(
        CreditStoreFragment.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.ALREADY_GRANTED));
    assertFalse(
        CreditStoreFragment.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.REJECTED));
    assertFalse(
        CreditStoreFragment.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.INVALID));
  }

  @Test
  public void shouldRecoverOwnedPurchaseOnLaunchResult_itemAlreadyOwned_returnsTrue() {
    assertTrue(
        CreditStoreFragment.shouldRecoverOwnedPurchaseOnLaunchResult(
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED));
  }

  @Test
  public void shouldRecoverOwnedPurchaseOnLaunchResult_ok_returnsFalse() {
    assertFalse(
        CreditStoreFragment.shouldRecoverOwnedPurchaseOnLaunchResult(
            BillingClient.BillingResponseCode.OK));
  }

  @Test
  public void shouldForceConsumePurchaseState_purchasedAndPending_returnsTrue() {
    assertTrue(
        CreditStoreFragment.shouldForceConsumePurchaseState(Purchase.PurchaseState.PURCHASED));
    assertTrue(
        CreditStoreFragment.shouldForceConsumePurchaseState(Purchase.PurchaseState.PENDING));
  }

  @Test
  public void shouldForceConsumePurchaseState_unspecified_returnsFalse() {
    assertFalse(
        CreditStoreFragment.shouldForceConsumePurchaseState(
            Purchase.PurchaseState.UNSPECIFIED_STATE));
  }

  @Test
  public void doesPurchaseMatchTargetProduct_emptyTarget_returnsTrue() {
    assertTrue(
        CreditStoreFragment.doesPurchaseMatchTargetProduct(
            "",
            Arrays.asList("credit_10_v2")));
  }

  @Test
  public void doesPurchaseMatchTargetProduct_matchingProduct_returnsTrue() {
    assertTrue(
        CreditStoreFragment.doesPurchaseMatchTargetProduct(
            "credit_20",
            Arrays.asList("credit_10_v2", "credit_20")));
  }

  @Test
  public void doesPurchaseMatchTargetProduct_nonMatchingProduct_returnsFalse() {
    assertFalse(
        CreditStoreFragment.doesPurchaseMatchTargetProduct(
            "credit_50",
            Arrays.asList("credit_10_v2", "credit_20")));
  }

  @Test
  public void doesPurchaseMatchTargetProduct_targetSetAndNoProducts_returnsFalse() {
    assertFalse(CreditStoreFragment.doesPurchaseMatchTargetProduct("credit_10_v2", null));
    assertFalse(
        CreditStoreFragment.doesPurchaseMatchTargetProduct(
            "credit_10_v2", Collections.emptyList()));
  }
}
