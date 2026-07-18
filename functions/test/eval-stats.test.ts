/**
 * Offline unit tests for eval/stats.js — the pure statistics helpers the eval runner
 * (eval/run.js) uses to summarise a temperature sweep. eval/run.js itself stays outside
 * src/ and test/ on purpose (jest.config.js:1 — "no emulator, no network"; run.js is
 * nothing but network), but eval/stats.js has no network dependency, so its maths is
 * exercised here just like src/eval/validate.ts is in eval-harness.test.ts.
 */
// eslint-disable-next-line @typescript-eslint/no-var-requires
const { populationStdDev } = require("../eval/stats");

describe("populationStdDev", () => {
  it("returns null for fewer than two values", () => {
    expect(populationStdDev([])).toBeNull();
    expect(populationStdDev([42])).toBeNull();
  });

  it("returns 0 when every value is identical", () => {
    expect(populationStdDev([85, 85, 85])).toBe(0);
  });

  it("matches a hand-computed population standard deviation", () => {
    // scores 80, 85, 90, 95 — mean 87.5, population variance 31.25, sd = sqrt(31.25)
    const sd = populationStdDev([80, 85, 90, 95]);
    expect(sd).not.toBeNull();
    expect(sd as number).toBeCloseTo(Math.sqrt(31.25), 10);
    expect(sd as number).toBeCloseTo(5.590169943749474, 10);
  });

  it("divides by n, not n-1 (population, not sample, formula)", () => {
    // scores 2, 4, 4, 4, 5, 5, 7, 9 — the textbook example where population sd = 2
    // and sample (n-1) sd would be a different value (~2.138).
    const sd = populationStdDev([2, 4, 4, 4, 5, 5, 7, 9]);
    expect(sd).toBeCloseTo(2, 10);
  });

  it("is order-independent", () => {
    const a = populationStdDev([1, 2, 3, 4, 5]) as number;
    const b = populationStdDev([5, 4, 3, 2, 1]) as number;
    expect(a).toBeCloseTo(b, 10);
  });

  it("does not treat an all-failed case as zero variance", () => {
    // Simulates the caller's contract: a case with 0 or 1 successful scores at a given
    // temperature contributes nothing, rather than being folded in as a 0 (which would
    // misrepresent "no evidence" as "perfectly stable").
    expect(populationStdDev([])).toBeNull();
    expect(populationStdDev([100])).toBeNull();
  });
});
