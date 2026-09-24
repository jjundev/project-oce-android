/**
 * Per-user daily tts quota (2026-09-24). `task=tts` carries no sessionId (it also serves the review
 * screen), so it is bounded per uid per KST day on the SAME usage doc as the dialogue start count:
 * `users/{uid}/usage/{yyyymmdd}.ttsCount`. Writes merge so `sessionCount` is preserved. Over the
 * limit the handler answers 429 and the client falls back to device TTS
 * (TtsPlaybackCoordinator.synthesize → null → device), so the learner never sees an error.
 */
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { kstDateKey } from "../config/kst";
import { DailyLimitError, DbLike, LimitProvider, usageDocPath } from "./start-gate";

/** default server-voice lines per user per KST day when config/limits.dailyTtsLines is absent. */
export const DEFAULT_DAILY_TTS_LINES = 300;

/** Reserve one server tts line for the caller's KST day, or throw DailyLimitError. */
export interface TtsQuota {
  reserve(uid: string): Promise<void>;
}

export function firestoreTtsQuota(
  limitProvider: LimitProvider,
  db: DbLike = getFirestore() as unknown as DbLike,
  now: () => number = () => Date.now()
): TtsQuota {
  return {
    async reserve(uid) {
      const limit = await limitProvider();
      const nowMs = now();
      const ref = db.doc(usageDocPath(uid, kstDateKey(nowMs)));
      await db.runTransaction(async (txn) => {
        const snap = await txn.get(ref);
        const v = snap.exists ? snap.data()?.ttsCount : undefined;
        const count = typeof v === "number" ? v : 0;
        if (count >= limit) {
          throw new DailyLimitError(`ttsCount ${count} >= limit ${limit}`);
        }
        txn.set(
          ref,
          { ttsCount: count + 1, updatedAt: Timestamp.fromMillis(nowMs) },
          { merge: true }
        );
      });
    },
  };
}
