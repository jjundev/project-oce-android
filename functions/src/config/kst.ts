/**
 * KST (Asia/Seoul, UTC+9) day-boundary helper — backend-functions.md:92, firestore-schema.md:20.
 *
 * The daily-free-session cap and the streak both key on the KST calendar day, so the usage
 * doc lives at `usage/{yyyymmdd}` where the date is computed at UTC+9, NOT the server's local
 * zone. Korea observes no DST, so a fixed +9h offset is exact year-round.
 */
const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

/** epoch millis → `yyyymmdd` calendar-day string at Asia/Seoul (UTC+9). */
export function kstDateKey(nowMs: number): string {
  // Shift the instant by +9h, then read UTC fields → the KST wall-clock date.
  const kst = new Date(nowMs + KST_OFFSET_MS);
  const year = kst.getUTCFullYear();
  const month = String(kst.getUTCMonth() + 1).padStart(2, "0");
  const day = String(kst.getUTCDate()).padStart(2, "0");
  return `${year}${month}${day}`;
}
