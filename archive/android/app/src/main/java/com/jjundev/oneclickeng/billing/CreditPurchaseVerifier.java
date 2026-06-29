package com.jjundev.oneclickeng.billing;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.Locale;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Calls backend purchase verification endpoint with Firebase ID token and
 * purchase payload.
 */
public final class CreditPurchaseVerifier {
  private static final String TAG = "CreditPurchaseVerifier";
  private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

  @NonNull
  private final String verifyUrl;
  @NonNull
  private final FirebaseAuth auth;
  @NonNull
  private final OkHttpClient httpClient;
  @NonNull
  private final Handler mainHandler;

  public CreditPurchaseVerifier(@NonNull String verifyUrl) {
    this(verifyUrl, FirebaseAuth.getInstance(), new OkHttpClient(), new Handler(Looper.getMainLooper()));
  }

  CreditPurchaseVerifier(
      @NonNull String verifyUrl,
      @NonNull FirebaseAuth auth,
      @NonNull OkHttpClient httpClient,
      @NonNull Handler mainHandler) {
    this.verifyUrl = verifyUrl.trim();
    this.auth = auth;
    this.httpClient = httpClient;
    this.mainHandler = mainHandler;
  }

  public void verifyPurchase(
      @NonNull CreditPurchaseStore.PendingPurchase pendingPurchase,
      @NonNull VerificationCallback callback) {
    Log.d(TAG, "verifyPurchase: start, productId=" + pendingPurchase.getProductId()
        + ", verifyUrl="
        + (verifyUrl.isEmpty() ? "(empty)" : verifyUrl.substring(0, Math.min(40, verifyUrl.length())) + "..."));
    FirebaseUser user = auth.getCurrentUser();
    VerificationResult preconditionError = preconditionError(user);
    if (preconditionError != null) {
      Log.w(TAG, "verifyPurchase: precondition failed, status=" + preconditionError.status
          + ", message=" + preconditionError.message);
      callback.onResult(preconditionError);
      return;
    }

    Log.d(TAG, "verifyPurchase: requesting Firebase ID token...");
    user.getIdToken(true)
        .addOnSuccessListener(
            tokenResult -> {
              String idToken = tokenResult != null && tokenResult.getToken() != null
                  ? tokenResult.getToken().trim()
                  : "";
              if (idToken.isEmpty()) {
                Log.w(TAG, "verifyPurchase: ID token is empty");
                callback.onResult(
                    VerificationResult.error(VerificationStatus.AUTH_ERROR, "Firebase ID token is empty"));
                return;
              }
              Log.d(TAG, "verifyPurchase: ID token acquired, starting HTTP request thread.");
              new Thread(() -> executeVerifyRequest(idToken, pendingPurchase, callback)).start();
            })
        .addOnFailureListener(
            e -> {
              Log.e(TAG, "verifyPurchase: getIdToken failed", e);
              callback.onResult(
                  VerificationResult.error(
                      VerificationStatus.AUTH_ERROR, "Failed to acquire Firebase ID token"));
            });
  }

  @Nullable
  VerificationResult preconditionError(@Nullable FirebaseUser user) {
    if (verifyUrl.isEmpty()) {
      return VerificationResult.error(
          VerificationStatus.CONFIG_ERROR, "CREDIT_BILLING_VERIFY_URL is empty");
    }
    if (user == null) {
      return VerificationResult.error(VerificationStatus.AUTH_ERROR, "User is not signed in");
    }
    return null;
  }

  private void executeVerifyRequest(
      @NonNull String idToken,
      @NonNull CreditPurchaseStore.PendingPurchase pendingPurchase,
      @NonNull VerificationCallback callback) {
    try {
      JsonObject payload = new JsonObject();
      payload.addProperty("packageName", pendingPurchase.getPackageName());
      payload.addProperty("productId", pendingPurchase.getProductId());
      payload.addProperty("purchaseToken", pendingPurchase.getPurchaseToken());
      payload.addProperty("orderId", pendingPurchase.getOrderId());
      payload.addProperty("purchaseTimeMillis", pendingPurchase.getPurchaseTimeMillis());
      payload.addProperty("quantity", pendingPurchase.getQuantity());
      payload.addProperty("purchaseState", pendingPurchase.getPurchaseState());

      Log.d(TAG, "executeVerifyRequest: POST " + verifyUrl
          + ", productId=" + pendingPurchase.getProductId());

      Request request = new Request.Builder()
          .url(verifyUrl)
          .addHeader("Authorization", "Bearer " + idToken)
          .addHeader("Content-Type", "application/json")
          .post(RequestBody.create(payload.toString(), JSON_MEDIA_TYPE))
          .build();

      try (Response response = httpClient.newCall(request).execute()) {
        String body = response.body() != null ? response.body().string() : "";
        Log.d(TAG, "executeVerifyRequest: response code=" + response.code()
            + ", body=" + (body.length() > 200 ? body.substring(0, 200) + "..." : body));
        if (!response.isSuccessful()) {
          postResult(
              callback,
              VerificationResult.error(
                  VerificationStatus.SERVER_ERROR,
                  "Server responded with " + response.code() + " " + body));
          return;
        }
        postResult(callback, parseResult(body));
      }
    } catch (IOException e) {
      Log.e(TAG, "executeVerifyRequest: IOException", e);
      postResult(
          callback,
          VerificationResult.error(
              VerificationStatus.NETWORK_ERROR, "Network error while verifying purchase"));
    } catch (Exception e) {
      Log.e(TAG, "executeVerifyRequest: unexpected exception", e);
      postResult(
          callback,
          VerificationResult.error(
              VerificationStatus.SERVER_ERROR, "Unexpected error while verifying purchase"));
    }
  }

  @NonNull
  private VerificationResult parseResult(@Nullable String responseBody) {
    if (responseBody == null || responseBody.trim().isEmpty()) {
      return VerificationResult.error(VerificationStatus.SERVER_ERROR, "Empty verification response");
    }

    try {
      JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
      String statusRaw = getString(root, "status");
      VerificationStatus status = parseStatus(statusRaw);
      long grantedCredits = getLong(root, "grantedCredits");
      long currentCreditBalance = getLong(root, "currentCreditBalance");
      String eventId = getString(root, "eventId");
      String purchaseToken = getString(root, "purchaseToken");
      String message = getString(root, "message");
      return new VerificationResult(
          status, grantedCredits, currentCreditBalance, eventId, purchaseToken, message);
    } catch (Exception e) {
      return VerificationResult.error(VerificationStatus.SERVER_ERROR, "Malformed verification response");
    }
  }

  @NonNull
  private static VerificationStatus parseStatus(@Nullable String rawStatus) {
    String safe = rawStatus == null ? "" : rawStatus.trim().toUpperCase(Locale.US);
    switch (safe) {
      case "GRANTED":
        return VerificationStatus.GRANTED;
      case "ALREADY_GRANTED":
        return VerificationStatus.ALREADY_GRANTED;
      case "PENDING":
        return VerificationStatus.PENDING;
      case "REJECTED":
        return VerificationStatus.REJECTED;
      case "INVALID":
        return VerificationStatus.INVALID;
      case "SERVER_ERROR":
        return VerificationStatus.SERVER_ERROR;
      default:
        return VerificationStatus.SERVER_ERROR;
    }
  }

  @NonNull
  private static String getString(@NonNull JsonObject source, @NonNull String key) {
    if (!source.has(key) || source.get(key).isJsonNull()) {
      return "";
    }
    try {
      return source.get(key).getAsString();
    } catch (Exception ignored) {
      return "";
    }
  }

  private static long getLong(@NonNull JsonObject source, @NonNull String key) {
    if (!source.has(key) || source.get(key).isJsonNull()) {
      return 0L;
    }
    try {
      return source.get(key).getAsLong();
    } catch (Exception ignored) {
      return 0L;
    }
  }

  private void postResult(@NonNull VerificationCallback callback, @NonNull VerificationResult result) {
    mainHandler.post(() -> callback.onResult(result));
  }

  public interface VerificationCallback {
    void onResult(@NonNull VerificationResult result);
  }

  public enum VerificationStatus {
    GRANTED,
    ALREADY_GRANTED,
    PENDING,
    REJECTED,
    INVALID,
    AUTH_ERROR,
    NETWORK_ERROR,
    SERVER_ERROR,
    CONFIG_ERROR
  }

  public static final class VerificationResult {
    @NonNull
    public final VerificationStatus status;
    public final long grantedCredits;
    public final long currentCreditBalance;
    @NonNull
    public final String eventId;
    @NonNull
    public final String purchaseToken;
    @NonNull
    public final String message;

    VerificationResult(
        @NonNull VerificationStatus status,
        long grantedCredits,
        long currentCreditBalance,
        @Nullable String eventId,
        @Nullable String purchaseToken,
        @Nullable String message) {
      this.status = status;
      this.grantedCredits = Math.max(0L, grantedCredits);
      this.currentCreditBalance = currentCreditBalance;
      this.eventId = eventId == null ? "" : eventId.trim();
      this.purchaseToken = purchaseToken == null ? "" : purchaseToken.trim();
      this.message = message == null ? "" : message.trim();
    }

    @NonNull
    static VerificationResult error(@NonNull VerificationStatus status, @NonNull String message) {
      return new VerificationResult(status, 0L, 0L, "", "", message);
    }
  }
}
