/**
 * XP + streak + study-days aggregation — the PURE core of the `point_ledger` onCreate trigger
 * (M3-05, firestore-schema.md §5). Free of firebase-admin types so it unit-tests without the
 * emulator, exactly like `evaluateStart` (start-gate.ts) / `evaluateSlot` (session-cap.ts).
 *
 * The trigger wrapper (onLedgerCreate.ts) extracts primitives off the ledger + progress docs,
 * calls `aggregate`, and commits the result under a single transaction with the idempotency
 * marker. All the branching that must be verified — reset watermark, one-day grace, same-day
 * no-op — lives here.
 */
import { kstDayDiff } from "../config/kst";

/**
 * difficulty → XP. SINGLE SOURCE OF TRUTH (firestore-schema.md:198): the security rule validates
 * only the `difficulty` enum, never the XP value, and the ledger doc deliberately does NOT store
 * `points`. XP is a pure function of difficulty, owned here.
 */
export const XP_BY_DIFFICULTY = { easy: 10, normal: 20, hard: 35 } as const;

export type Difficulty = keyof typeof XP_BY_DIFFICULTY;

/** true iff `d` is one of the known difficulty enum values. */
export function isDifficulty(d: unknown): d is Difficulty {
  return d === "easy" || d === "normal" || d === "hard";
}

/** The `gamification/progress` fields this aggregation reads and writes (firestore-schema.md:92). */
export interface ProgressState {
  xp: number;
  streak: number;
  studyDays: number;
  /** `yyyy-MM-dd` KST day-key of the most recent study day, or null before any study. */
  lastStudyDate: string | null;
  /** reset watermark as epoch millis, or null when the account has never been reset. */
  resetAtMs: number | null;
}

/** The already-normalized ledger inputs the aggregation needs. */
export interface LedgerInput {
  difficulty: Difficulty;
  /** `awardedAt` as epoch millis — compared against the reset watermark. */
  awardedAtMs: number;
  /** `yyyy-MM-dd` KST day-key derived from `awardedAt` (kstDayKeyIso). */
  dayKey: string;
}

export interface AggregateResult {
  /**
   * true when the ledger predates the reset watermark (firestore-schema.md:179): the wrapper must
   * write NOTHING and create NO marker (bare return), so a redelivery re-evaluates and skips again.
   */
  skip: boolean;
  /** the progress to commit when `skip` is false. */
  next: ProgressState;
}

/** first-ever progress: everything zero, no last study day, no reset watermark. */
export function defaultProgress(): ProgressState {
  return { xp: 0, streak: 0, studyDays: 0, lastStudyDate: null, resetAtMs: null };
}

/**
 * Apply one ledger entry to the progress aggregate (firestore-schema.md:176-192).
 *
 * - Reset watermark: `awardedAt <= resetAt` → skip (null watermark = 0 = never skip).
 * - XP always accrues (difficulty is pre-validated by the caller).
 * - A new KST day (`dayKey != lastStudyDate`) bumps studyDays and advances the streak by the O(1)
 *   recurrence: first study → 1; gap 1 day → +1; gap 2 days → held flat (one-day grace); gap ≥ 3
 *   (or same-day handled above) → reset to 1. A same-day repeat touches only XP.
 */
export function aggregate(
  prev: ProgressState | undefined,
  input: LedgerInput
): AggregateResult {
  const p = prev ?? defaultProgress();

  // Reset watermark (firestore-schema.md:179). Null → 0 so a never-reset account never skips.
  if (input.awardedAtMs <= (p.resetAtMs ?? 0)) {
    return { skip: true, next: p };
  }

  const next: ProgressState = { ...p };
  next.xp += XP_BY_DIFFICULTY[input.difficulty];

  if (input.dayKey !== next.lastStudyDate) {
    next.studyDays += 1;
    if (next.lastStudyDate === null) {
      next.streak = 1;
    } else {
      const gap = kstDayDiff(input.dayKey, next.lastStudyDate);
      next.streak = gap === 1 ? next.streak + 1 : gap === 2 ? next.streak : 1;
    }
    next.lastStudyDate = input.dayKey;
  }

  return { skip: false, next };
}
