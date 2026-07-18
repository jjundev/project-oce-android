"use strict";

/**
 * Pure statistics helpers for the eval runner (run.js). Split out from run.js so the
 * maths can be exercised without a network call — run.js itself stays outside src/ and
 * test/ per the harness's own rule (see run.js:8-12), but a dependency-free pure
 * function has no such reason to dodge coverage, so it lives here and is required by
 * both run.js and test/eval-stats.test.js.
 */

/**
 * Population standard deviation (divide by n, not n-1 / Bessel's correction). The scores
 * collected for one (case, temperature) cell are the COMPLETE set of observations at that
 * setting — every repeat that transported and parsed successfully — not a sample drawn
 * from some larger population we are trying to infer from. That makes the population
 * formula the correct one, not the sample formula.
 *
 * Returns null when there are fewer than two values. A single observation carries no
 * variance information, and a case whose calls all failed but one is not evidence of
 * stability — reporting 0 in either case would misrepresent "we don't know" as "this is
 * perfectly stable". Callers must skip nulls rather than folding them into an average as 0.
 */
function populationStdDev(values) {
  if (!Array.isArray(values) || values.length < 2) return null;
  const n = values.length;
  const mean = values.reduce((sum, v) => sum + v, 0) / n;
  const variance = values.reduce((sum, v) => sum + (v - mean) ** 2, 0) / n;
  return Math.sqrt(variance);
}

module.exports = { populationStdDev };
