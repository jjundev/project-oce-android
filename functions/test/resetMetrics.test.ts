import { ProgressReset, ResetStore, StudytimeReset, runReset } from "../src/metrics/resetMetrics";

const TS = "SERVER_TS"; // stable server-timestamp sentinel for assertions

/**
 * In-memory ResetStore double: records the progress/studytime writes and whether the marks were
 * deleted, plus the call ORDER (the reset's safety rests on progress-with-watermark landing first).
 */
function fakeStore(): {
  store: ResetStore;
  rec: {
    progress: ProgressReset | null;
    studytime: StudytimeReset | null;
    marksDeleted: boolean;
    order: string[];
  };
} {
  const rec = {
    progress: null as ProgressReset | null,
    studytime: null as StudytimeReset | null,
    marksDeleted: false,
    order: [] as string[],
  };
  const store: ResetStore = {
    async writeProgress(_uid, data) {
      rec.progress = data;
      rec.order.push("progress");
    },
    async deleteProgressMarks(_uid) {
      rec.marksDeleted = true;
      rec.order.push("marks");
    },
    async writeStudytime(_uid, data) {
      rec.studytime = data;
      rec.order.push("studytime");
    },
    serverTimestamp: () => TS,
  };
  return { store, rec };
}

describe("runReset", () => {
  it("zeroes progress with a fresh resetAt watermark", async () => {
    const { store, rec } = fakeStore();
    await runReset("u1", store);
    expect(rec.progress).toEqual({
      xp: 0,
      streak: 0,
      studyDays: 0,
      lastStudyDate: null,
      resetAt: TS,
    });
  });

  it("deletes all progress_marks and zeroes studytime with an empty today bucket", async () => {
    const { store, rec } = fakeStore();
    await runReset("u1", store);
    expect(rec.marksDeleted).toBe(true);
    expect(rec.studytime).toEqual({ totalSeconds: 0, today: {} });
  });

  it("writes the progress watermark BEFORE deleting marks (in-flight trigger revival guard)", async () => {
    const { store, rec } = fakeStore();
    await runReset("u1", store);
    expect(rec.order).toEqual(["progress", "marks", "studytime"]);
  });

  it("is idempotent: a second run converges on the same zeroed state", async () => {
    const { store, rec } = fakeStore();
    await runReset("u1", store);
    await runReset("u1", store);
    expect(rec.progress).toEqual({ xp: 0, streak: 0, studyDays: 0, lastStudyDate: null, resetAt: TS });
    expect(rec.studytime).toEqual({ totalSeconds: 0, today: {} });
  });
});
