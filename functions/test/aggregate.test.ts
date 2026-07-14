import {
  XP_BY_DIFFICULTY,
  aggregate,
  defaultProgress,
  isDifficulty,
  ProgressState,
} from "../src/gamification/aggregate";
import { kstDayKeyIso, kstDayDiff } from "../src/config/kst";

/** millis for KST midnight of a given yyyy-MM-dd (subtract the +9h offset from UTC midnight). */
function kstMidnightMs(dayKey: string): number {
  const [y, m, d] = dayKey.split("-").map(Number);
  return Date.UTC(y, m - 1, d) - 9 * 60 * 60 * 1000;
}

function input(difficulty: "easy" | "normal" | "hard", dayKey: string, awardedAtMs?: number) {
  return { difficulty, dayKey, awardedAtMs: awardedAtMs ?? kstMidnightMs(dayKey) + 1 } as const;
}

describe("XP map (single source of truth)", () => {
  it("is exactly 5/10/20/35/55", () => {
    expect(XP_BY_DIFFICULTY).toEqual({ starter: 5, easy: 10, normal: 20, hard: 35, expert: 55 });
  });
});

describe("isDifficulty", () => {
  it("accepts the enum and rejects everything else", () => {
    expect(isDifficulty("easy")).toBe(true);
    expect(isDifficulty("normal")).toBe(true);
    expect(isDifficulty("hard")).toBe(true);
    expect(isDifficulty("EASY")).toBe(false);
    expect(isDifficulty(undefined)).toBe(false);
    expect(isDifficulty(10)).toBe(false);
  });
});

describe("XP table 5 tiers", () => {
  it("awards the ratified XP per tier", () => {
    expect(XP_BY_DIFFICULTY).toEqual({ starter: 5, easy: 10, normal: 20, hard: 35, expert: 55 });
  });
  it("recognizes all 5 tokens as difficulty", () => {
    for (const t of ["starter", "easy", "normal", "hard", "expert"]) {
      expect(isDifficulty(t)).toBe(true);
    }
    expect(isDifficulty("legendary")).toBe(false);
  });
});

describe("aggregate — first study", () => {
  it("from undefined progress: xp accrues, streak=1, studyDays=1", () => {
    const { skip, next } = aggregate(undefined, input("normal", "2026-07-04"));
    expect(skip).toBe(false);
    expect(next).toEqual<ProgressState>({
      xp: 20,
      streak: 1,
      studyDays: 1,
      lastStudyDate: "2026-07-04",
      resetAtMs: null,
    });
  });

  it("difficulty drives xp", () => {
    expect(aggregate(undefined, input("easy", "2026-07-04")).next.xp).toBe(10);
    expect(aggregate(undefined, input("hard", "2026-07-04")).next.xp).toBe(35);
  });
});

describe("aggregate — streak recurrence", () => {
  const base: ProgressState = {
    xp: 100,
    streak: 5,
    studyDays: 9,
    lastStudyDate: "2026-07-04",
    resetAtMs: null,
  };

  it("gap 1 (next day) → streak +1, studyDays +1", () => {
    const { next } = aggregate(base, input("easy", "2026-07-05"));
    expect(next.streak).toBe(6);
    expect(next.studyDays).toBe(10);
    expect(next.xp).toBe(110);
    expect(next.lastStudyDate).toBe("2026-07-05");
  });

  it("gap 2 (one day missed) → streak held flat (one-day grace), studyDays +1", () => {
    const { next } = aggregate(base, input("easy", "2026-07-06"));
    expect(next.streak).toBe(5);
    expect(next.studyDays).toBe(10);
  });

  it("gap 3 (two+ days missed) → streak resets to 1", () => {
    const { next } = aggregate(base, input("easy", "2026-07-07"));
    expect(next.streak).toBe(1);
    expect(next.studyDays).toBe(10);
  });

  it("same day repeat → only xp changes; streak/studyDays/lastStudyDate untouched", () => {
    const { next } = aggregate(base, input("hard", "2026-07-04"));
    expect(next.xp).toBe(135);
    expect(next.streak).toBe(5);
    expect(next.studyDays).toBe(9);
    expect(next.lastStudyDate).toBe("2026-07-04");
  });

  it("does not mutate the input progress object", () => {
    const snapshot = { ...base };
    aggregate(base, input("easy", "2026-07-05"));
    expect(base).toEqual(snapshot);
  });
});

describe("aggregate — reset watermark", () => {
  it("skips a ledger awarded at or before resetAt (no write)", () => {
    const resetAtMs = kstMidnightMs("2026-07-10");
    const prev: ProgressState = {
      xp: 0,
      streak: 0,
      studyDays: 0,
      lastStudyDate: null,
      resetAtMs,
    };
    // awarded 1ms before the reset watermark
    const res = aggregate(prev, input("hard", "2026-07-09", resetAtMs - 1));
    expect(res.skip).toBe(true);
    expect(res.next).toBe(prev); // untouched
  });

  it("processes a ledger awarded after resetAt", () => {
    const resetAtMs = kstMidnightMs("2026-07-10");
    const prev: ProgressState = {
      xp: 0,
      streak: 0,
      studyDays: 0,
      lastStudyDate: null,
      resetAtMs,
    };
    const res = aggregate(prev, input("normal", "2026-07-11", resetAtMs + 1));
    expect(res.skip).toBe(false);
    expect(res.next.xp).toBe(20);
    expect(res.next.streak).toBe(1);
  });

  it("null resetAt never skips (never-reset account)", () => {
    const res = aggregate(defaultProgress(), input("easy", "2026-07-04", 1));
    expect(res.skip).toBe(false);
  });
});

describe("kst helpers", () => {
  it("kstDayKeyIso emits yyyy-MM-dd at Asia/Seoul", () => {
    // 2026-07-03T20:00:00Z is 2026-07-04 05:00 KST
    expect(kstDayKeyIso(Date.parse("2026-07-03T20:00:00Z"))).toBe("2026-07-04");
    // 2026-07-03T14:00:00Z is 2026-07-03 23:00 KST
    expect(kstDayKeyIso(Date.parse("2026-07-03T14:00:00Z"))).toBe("2026-07-03");
  });

  it("kstDayDiff is a whole-day calendar delta a − b", () => {
    expect(kstDayDiff("2026-07-05", "2026-07-04")).toBe(1);
    expect(kstDayDiff("2026-07-06", "2026-07-04")).toBe(2);
    expect(kstDayDiff("2026-07-04", "2026-07-04")).toBe(0);
    expect(kstDayDiff("2026-08-01", "2026-07-31")).toBe(1); // month boundary
    expect(kstDayDiff("2027-01-01", "2026-12-31")).toBe(1); // year boundary
  });
});
