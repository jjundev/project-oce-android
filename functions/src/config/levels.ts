/**
 * Session-level single source of truth (server). Token strings are the wire/DB contract, mirrored
 * 1:1 by the client enum (SessionLevel.kt). [CEFR_BAND] drives the dialogue difficulty-band prompt
 * (gemini.ts) and is NEVER surfaced to the client. Order is easiest→hardest.
 */
export const LEVEL_TOKENS = ["starter", "easy", "normal", "hard", "expert"] as const;
export type LevelToken = (typeof LEVEL_TOKENS)[number];

export const CEFR_BAND: Record<LevelToken, string> = {
  starter: "A1",
  easy: "A2",
  normal: "B1",
  hard: "B2",
  expert: "C1",
};

/** Non-first-session length gate: even integers in [6, 20]. */
export function isEven6to20(n: number): boolean {
  return Number.isInteger(n) && n % 2 === 0 && n >= 6 && n <= 20;
}
