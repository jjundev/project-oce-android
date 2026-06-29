package com.jjundev.oneclickeng.billing;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.billingclient.api.Purchase;
import org.junit.Test;

public class BillingStartupAutoConsumeCoordinatorTest {

  @Test
  public void shouldKeepPendingPurchase_retryableStatus_returnsTrue() {
    assertTrue(
        BillingStartupAutoConsumeCoordinator.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.PENDING));
    assertTrue(
        BillingStartupAutoConsumeCoordinator.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.AUTH_ERROR));
    assertTrue(
        BillingStartupAutoConsumeCoordinator.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.CONFIG_ERROR));
    assertTrue(
        BillingStartupAutoConsumeCoordinator.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.NETWORK_ERROR));
    assertTrue(
        BillingStartupAutoConsumeCoordinator.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.SERVER_ERROR));
  }

  @Test
  public void shouldKeepPendingPurchase_nonRetryableStatus_returnsFalse() {
    assertFalse(
        BillingStartupAutoConsumeCoordinator.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.GRANTED));
    assertFalse(
        BillingStartupAutoConsumeCoordinator.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.ALREADY_GRANTED));
    assertFalse(
        BillingStartupAutoConsumeCoordinator.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.REJECTED));
    assertFalse(
        BillingStartupAutoConsumeCoordinator.shouldKeepPendingPurchase(
            CreditPurchaseVerifier.VerificationStatus.INVALID));
  }

  @Test
  public void shouldConsumeAfterVerification_grantedStatusesOnly() {
    assertTrue(
        BillingStartupAutoConsumeCoordinator.shouldConsumeAfterVerification(
            CreditPurchaseVerifier.VerificationStatus.GRANTED));
    assertTrue(
        BillingStartupAutoConsumeCoordinator.shouldConsumeAfterVerification(
            CreditPurchaseVerifier.VerificationStatus.ALREADY_GRANTED));
    assertFalse(
        BillingStartupAutoConsumeCoordinator.shouldConsumeAfterVerification(
            CreditPurchaseVerifier.VerificationStatus.PENDING));
    assertFalse(
        BillingStartupAutoConsumeCoordinator.shouldConsumeAfterVerification(
            CreditPurchaseVerifier.VerificationStatus.REJECTED));
    assertFalse(
        BillingStartupAutoConsumeCoordinator.shouldConsumeAfterVerification(
            CreditPurchaseVerifier.VerificationStatus.INVALID));
    assertFalse(
        BillingStartupAutoConsumeCoordinator.shouldConsumeAfterVerification(
            CreditPurchaseVerifier.VerificationStatus.AUTH_ERROR));
    assertFalse(
        BillingStartupAutoConsumeCoordinator.shouldConsumeAfterVerification(
            CreditPurchaseVerifier.VerificationStatus.NETWORK_ERROR));
    assertFalse(
        BillingStartupAutoConsumeCoordinator.shouldConsumeAfterVerification(
            CreditPurchaseVerifier.VerificationStatus.SERVER_ERROR));
    assertFalse(
        BillingStartupAutoConsumeCoordinator.shouldConsumeAfterVerification(
            CreditPurchaseVerifier.VerificationStatus.CONFIG_ERROR));
  }

  @Test
  public void shouldQueuePurchaseState_onlyPurchasedOrPending() {
    assertTrue(
        BillingStartupAutoConsumeCoordinator.shouldQueuePurchaseState(
            Purchase.PurchaseState.PURCHASED));
    assertTrue(
        BillingStartupAutoConsumeCoordinator.shouldQueuePurchaseState(
            Purchase.PurchaseState.PENDING));
    assertFalse(
        BillingStartupAutoConsumeCoordinator.shouldQueuePurchaseState(
            Purchase.PurchaseState.UNSPECIFIED_STATE));
  }
}
