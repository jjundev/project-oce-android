package com.jjundev.oneclickeng.billing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BillingStartupDiagnosticsTest {

  @Test
  public void buildStartupLogPayload_whenUrlEmpty_marksUnconfiguredAndMasked() {
    String payload =
        BillingStartupDiagnostics.buildStartupLogPayload("", false, 8, "1.0.2", false, null);

    assertEquals("false", field(payload, "verifyUrlConfigured"));
    assertEquals("-", field(payload, "verifyUrlHost"));
    assertEquals("-", field(payload, "verifyUrlPath"));
    assertEquals("-", field(payload, "verifyUrlFingerprint"));
    assertEquals("-", field(payload, "uidPrefix"));
  }

  @Test
  public void buildStartupLogPayload_whenUrlPresent_outputsHostPathAndStableFingerprint() {
    String url = "https://us-central1-one-click-eng.cloudfunctions.net/verifyCreditPurchase";
    String payloadA =
        BillingStartupDiagnostics.buildStartupLogPayload(url, false, 8, "1.0.2", true, "abcdef123");
    String payloadB =
        BillingStartupDiagnostics.buildStartupLogPayload(url, false, 8, "1.0.2", true, "abcdef123");

    assertEquals("true", field(payloadA, "verifyUrlConfigured"));
    assertEquals("us-central1-one-click-eng.cloudfunctions.net", field(payloadA, "verifyUrlHost"));
    assertEquals("/verifyCreditPurchase", field(payloadA, "verifyUrlPath"));
    assertEquals(field(payloadA, "verifyUrlFingerprint"), field(payloadB, "verifyUrlFingerprint"));
    assertTrue(field(payloadA, "verifyUrlFingerprint").matches("[0-9a-f]{8}"));
    assertFalse(payloadA.contains(url));
  }

  @Test
  public void buildStartupLogPayload_whenUrlHasQuery_omitsQueryAndFragmentFromPath() {
    String url =
        "https://us-central1-one-click-eng.cloudfunctions.net/verifyCreditPurchase?x=1#frag";
    String payload =
        BillingStartupDiagnostics.buildStartupLogPayload(url, false, 8, "1.0.2", false, null);

    assertEquals("us-central1-one-click-eng.cloudfunctions.net", field(payload, "verifyUrlHost"));
    assertEquals("/verifyCreditPurchase", field(payload, "verifyUrlPath"));
  }

  @Test
  public void buildStartupLogPayload_whenSignedIn_includesUidPrefixSixChars() {
    String payload =
        BillingStartupDiagnostics.buildStartupLogPayload(
            "https://example.com/a", true, 9, "1.0.3", true, "1234567890abcdef");

    assertEquals("true", field(payload, "firebaseSignedIn"));
    assertEquals("123456", field(payload, "uidPrefix"));
  }

  @Test
  public void buildStartupLogPayload_whenSignedOut_usesDashUidPrefix() {
    String payload =
        BillingStartupDiagnostics.buildStartupLogPayload(
            "https://example.com/a", true, 9, "1.0.3", false, "1234567890abcdef");

    assertEquals("false", field(payload, "firebaseSignedIn"));
    assertEquals("-", field(payload, "uidPrefix"));
  }

  private static String field(String payload, String key) {
    String prefix = key + "=";
    String[] tokens = payload.split(" ");
    for (String token : tokens) {
      if (token.startsWith(prefix)) {
        return token.substring(prefix.length());
      }
    }
    return "";
  }
}
