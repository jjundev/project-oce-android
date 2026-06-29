package com.jjundev.oneclickeng.billing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.os.Handler;
import android.os.Looper;
import com.google.firebase.auth.FirebaseAuth;
import okhttp3.OkHttpClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = android.app.Application.class)
public class CreditPurchaseVerifierTest {

  @Test
  public void preconditionError_emptyVerifyUrl_returnsConfigError() {
    CreditPurchaseVerifier verifier =
        new CreditPurchaseVerifier(
            "   ", (FirebaseAuth) null, new OkHttpClient(), new Handler(Looper.getMainLooper()));

    CreditPurchaseVerifier.VerificationResult result = verifier.preconditionError(null);

    assertNotNull(result);
    assertEquals(CreditPurchaseVerifier.VerificationStatus.CONFIG_ERROR, result.status);
  }

  @Test
  public void preconditionError_missingUser_returnsAuthError() {
    CreditPurchaseVerifier verifier =
        new CreditPurchaseVerifier(
            "https://example.com/verify",
            (FirebaseAuth) null,
            new OkHttpClient(),
            new Handler(Looper.getMainLooper()));

    CreditPurchaseVerifier.VerificationResult result = verifier.preconditionError(null);

    assertNotNull(result);
    assertEquals(CreditPurchaseVerifier.VerificationStatus.AUTH_ERROR, result.status);
  }
}
