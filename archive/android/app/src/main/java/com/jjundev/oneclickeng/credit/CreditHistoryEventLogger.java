package com.jjundev.oneclickeng.credit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CreditHistoryEventLogger {
  public static final String EVENT_PURCHASE_CHARGE = "purchase_charge";
  public static final String EVENT_AD_CHARGE = "ad_charge";
  public static final String EVENT_LEARNING_USE = "learning_use";
  public static final String EVENT_QUIZ_USE = "quiz_use";
  public static final String EVENT_SIGNUP_BONUS_CHARGE = "signup_bonus_charge";

  public static final String SOURCE_APP = "app";
  public static final String SOURCE_SERVER = "server";

  private static final String COLLECTION_USERS = "users";
  private static final String COLLECTION_CREDIT_EVENTS = "credit_events";
  private static final String SIGNUP_BONUS_EVENT_ID = "signup_bonus_v1";
  private static final long SIGNUP_BONUS_CREDITS = 5L;
  private static final String SIGNUP_BONUS_REASON = "signup_welcome_bonus";
  private static final String SIGNUP_BONUS_SCREEN = "login";

  private CreditHistoryEventLogger() {}

  public interface Callback {
    void onSuccess(long creditAfter);

    void onFailure(@NonNull Exception exception);
  }

  public interface SignupBonusCallback {
    void onComplete(boolean granted, long creditAfter);

    void onFailure(@NonNull Exception exception);
  }

  public static void applyDeltaWithEvent(
      @NonNull String uid,
      long deltaCredits,
      @NonNull String event,
      @NonNull String reason,
      @Nullable String screen,
      @NonNull String source,
      @NonNull Callback callback) {
    String safeUid = normalizeString(uid);
    if (safeUid.isEmpty()) {
      callback.onFailure(new IllegalArgumentException("uid is empty"));
      return;
    }

    String safeEvent = normalizeString(event);
    String safeReason = normalizeString(reason);
    String safeSource = normalizeString(source);
    String safeScreen = normalizeString(screen);

    FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    firestore
        .runTransaction(
            transaction -> {
              DocumentReference userRef = firestore.collection(COLLECTION_USERS).document(safeUid);
              DocumentSnapshot userSnapshot = transaction.get(userRef);

              long currentCredit = readCredit(userSnapshot);
              long creditAfter = currentCredit + deltaCredits;

              Map<String, Object> userPayload = new HashMap<>();
              userPayload.put("credit", creditAfter);
              transaction.set(userRef, userPayload, SetOptions.merge());

              DocumentReference eventRef =
                  userRef.collection(COLLECTION_CREDIT_EVENTS).document(buildEventId());
              Map<String, Object> eventPayload = new HashMap<>();
              eventPayload.put("timestamp_epoch_ms", System.currentTimeMillis());
              eventPayload.put("timestamp_server", FieldValue.serverTimestamp());
              eventPayload.put("event", safeEvent);
              eventPayload.put("delta_credits", deltaCredits);
              eventPayload.put("credit_after", creditAfter);
              if (!safeSource.isEmpty()) {
                eventPayload.put("source", safeSource);
              }
              if (!safeReason.isEmpty()) {
                eventPayload.put("reason", safeReason);
              }
              if (!safeScreen.isEmpty()) {
                eventPayload.put("screen", safeScreen);
              }

              transaction.set(eventRef, eventPayload);
              return creditAfter;
            })
        .addOnSuccessListener(callback::onSuccess)
        .addOnFailureListener(callback::onFailure);
  }

  public static void grantSignupBonusIfAbsent(
      @NonNull String uid, @NonNull SignupBonusCallback callback) {
    String safeUid = normalizeString(uid);
    if (safeUid.isEmpty()) {
      callback.onFailure(new IllegalArgumentException("uid is empty"));
      return;
    }

    FirebaseFirestore firestore = FirebaseFirestore.getInstance();
    firestore
        .runTransaction(
            transaction -> {
              DocumentReference userRef = firestore.collection(COLLECTION_USERS).document(safeUid);
              DocumentReference eventRef =
                  userRef.collection(COLLECTION_CREDIT_EVENTS).document(SIGNUP_BONUS_EVENT_ID);

              DocumentSnapshot userSnapshot = transaction.get(userRef);
              DocumentSnapshot eventSnapshot = transaction.get(eventRef);
              long currentCredit = readCredit(userSnapshot);

              if (eventSnapshot.exists()) {
                return new SignupBonusGrantResult(false, currentCredit);
              }

              long creditAfter = currentCredit + SIGNUP_BONUS_CREDITS;
              Map<String, Object> userPayload = new HashMap<>();
              userPayload.put("credit", creditAfter);
              transaction.set(userRef, userPayload, SetOptions.merge());

              Map<String, Object> eventPayload = new HashMap<>();
              eventPayload.put("timestamp_epoch_ms", System.currentTimeMillis());
              eventPayload.put("timestamp_server", FieldValue.serverTimestamp());
              eventPayload.put("event", EVENT_SIGNUP_BONUS_CHARGE);
              eventPayload.put("delta_credits", SIGNUP_BONUS_CREDITS);
              eventPayload.put("credit_after", creditAfter);
              eventPayload.put("source", SOURCE_APP);
              eventPayload.put("reason", SIGNUP_BONUS_REASON);
              eventPayload.put("screen", SIGNUP_BONUS_SCREEN);
              transaction.set(eventRef, eventPayload);

              return new SignupBonusGrantResult(true, creditAfter);
            })
        .addOnSuccessListener(result -> callback.onComplete(result.granted, result.creditAfter))
        .addOnFailureListener(callback::onFailure);
  }

  private static long readCredit(@NonNull DocumentSnapshot snapshot) {
    Long credit = snapshot.getLong("credit");
    return credit != null ? credit : 0L;
  }

  @NonNull
  private static String buildEventId() {
    String random = UUID.randomUUID().toString().replace("-", "");
    if (random.length() > 8) {
      random = random.substring(0, 8);
    }
    return "evt_" + System.currentTimeMillis() + "_" + random;
  }

  @NonNull
  private static String normalizeString(@Nullable String value) {
    return value == null ? "" : value.trim();
  }

  private static final class SignupBonusGrantResult {
    private final boolean granted;
    private final long creditAfter;

    private SignupBonusGrantResult(boolean granted, long creditAfter) {
      this.granted = granted;
      this.creditAfter = creditAfter;
    }
  }
}
