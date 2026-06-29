package com.jjundev.oneclickeng.billing;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Builds a safe startup diagnostic payload for billing URL configuration checks. */
public final class BillingStartupDiagnostics {
  private static final String EVENT_NAME = "APP_START_BILLING_DIAG";
  private static final String UNAVAILABLE = "-";

  private BillingStartupDiagnostics() {}

  @NonNull
  public static String buildStartupLogPayload(
      @Nullable String verifyUrl,
      boolean isDebugBuild,
      int versionCode,
      @Nullable String versionName,
      boolean signedIn,
      @Nullable String uid) {
    String safeUrl = verifyUrl == null ? "" : verifyUrl.trim();
    boolean configured = !safeUrl.isEmpty();

    UrlSummary summary = summarizeUrl(safeUrl);
    String fingerprint = configured ? fingerprint(safeUrl) : UNAVAILABLE;
    String uidPrefix = signedIn ? uidPrefix(uid) : UNAVAILABLE;
    String safeVersionName = normalizeValue(versionName);

    return "event="
        + EVENT_NAME
        + " versionCode="
        + versionCode
        + " versionName="
        + safeVersionName
        + " buildType="
        + (isDebugBuild ? "debug" : "release")
        + " verifyUrlConfigured="
        + configured
        + " verifyUrlHost="
        + summary.host
        + " verifyUrlPath="
        + summary.path
        + " verifyUrlFingerprint="
        + fingerprint
        + " firebaseSignedIn="
        + signedIn
        + " uidPrefix="
        + uidPrefix;
  }

  @NonNull
  private static UrlSummary summarizeUrl(@NonNull String verifyUrl) {
    if (verifyUrl.isEmpty()) {
      return new UrlSummary(UNAVAILABLE, UNAVAILABLE);
    }

    try {
      URI uri = URI.create(verifyUrl);
      String host = normalizeValue(uri.getHost());
      String path = normalizePath(uri.getPath());
      return new UrlSummary(host, path);
    } catch (Exception ignored) {
      return new UrlSummary(UNAVAILABLE, UNAVAILABLE);
    }
  }

  @NonNull
  private static String normalizePath(@Nullable String path) {
    if (path == null) {
      return UNAVAILABLE;
    }
    String trimmed = path.trim();
    if (trimmed.isEmpty()) {
      return "/";
    }
    return trimmed;
  }

  @NonNull
  private static String fingerprint(@NonNull String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return toHex(hash).substring(0, 8);
    } catch (Exception ignored) {
      return UNAVAILABLE;
    }
  }

  @NonNull
  private static String toHex(@NonNull byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      builder.append(String.format(Locale.US, "%02x", value));
    }
    return builder.toString();
  }

  @NonNull
  private static String uidPrefix(@Nullable String uid) {
    String safeUid = normalizeValue(uid);
    if (UNAVAILABLE.equals(safeUid)) {
      return UNAVAILABLE;
    }
    int prefixLength = Math.min(6, safeUid.length());
    return safeUid.substring(0, prefixLength);
  }

  @NonNull
  private static String normalizeValue(@Nullable String value) {
    if (value == null) {
      return UNAVAILABLE;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? UNAVAILABLE : trimmed;
  }

  private static final class UrlSummary {
    @NonNull private final String host;
    @NonNull private final String path;

    private UrlSummary(@NonNull String host, @NonNull String path) {
      this.host = host;
      this.path = path;
    }
  }
}
