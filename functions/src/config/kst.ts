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

/**
 * epoch millis → `yyyy-MM-dd` (ISO, dash-separated) calendar-day string at Asia/Seoul (UTC+9).
 *
 * This is the format `gamification/progress.lastStudyDate` and `gamification/studytime.today.dayKey`
 * store (firestore-schema.md:98,182) — distinct from `kstDateKey`'s separator-less `yyyymmdd`, which
 * keys `usage/{yyyymmdd}`. Both are the same KST calendar day; only the string shape differs.
 */
export function kstDayKeyIso(nowMs: number): string {
  const kst = new Date(nowMs + KST_OFFSET_MS);
  const year = kst.getUTCFullYear();
  const month = String(kst.getUTCMonth() + 1).padStart(2, "0");
  const day = String(kst.getUTCDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

/**
 * Whole-day difference `a − b` between two `yyyy-MM-dd` KST day-keys — the O(1) input to the streak
 * recurrence (firestore-schema.md:186). Both args must be non-null ISO day-keys (the caller guards
 * the `lastStudyDate == null` first-study case). Computed from each key's UTC midnight, so it is a
 * pure calendar-day delta independent of the wall-clock instants that produced the keys.
 */
export function kstDayDiff(a: string, b: string): number {
  const [ay, am, ad] = a.split("-").map(Number);
  const [by, bm, bd] = b.split("-").map(Number);
  const aMidnight = Date.UTC(ay, am - 1, ad);
  const bMidnight = Date.UTC(by, bm - 1, bd);
  return Math.round((aMidnight - bMidnight) / (24 * 60 * 60 * 1000));
}
