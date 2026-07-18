/**
 * Structural validators for turn-feedback model output.
 *
 * These check only what is MECHANICALLY decidable — schema shape, enum membership,
 * cardinality, script (Hangul), and the absence of colours. Whether a correction is
 * pedagogically right is a human call; eval/run.js renders those into a markdown
 * report for review. Keeping the two apart is the point: a machine gate that tried
 * to judge teaching quality would be wrong often enough to be ignored.
 *
 * Pure functions — no network, no I/O, no imports. Fully unit-tested offline.
 */

/** what a golden case asserts beyond generic structural validity */
export interface CaseExpectation {
  scoreMin?: number;
  scoreMax?: number;
  /** the sentence is already good — marking anything `incorrect` is over-correction */
  noIncorrectSegments?: boolean;
  /** the sentence has a real error — failing to mark it is a miss */
  requiresIncorrectSegments?: boolean;
}

export interface Violation {
  severity: "error" | "warn";
  /** stable machine-readable id, e.g. "writingScore.score" — grouped in the report */
  check: string;
  detail: string;
}

const HANGUL = /[가-힣]/;
const HEX_COLOR = /#[0-9a-fA-F]{3,8}\b/;
/** exact key "color"/"colour" OR a compound key ENDING in it (textColor, bgColour, ...). */
const COLOR_KEY = /"[a-zA-Z0-9_]*colou?r"\s*:/i;
/** rgb()/rgba()/hsl()/hsla() function syntax, wherever it appears in the serialized output. */
const COLOR_FUNCTION = /\b(?:rgb|rgba|hsl|hsla)\(\s*[^)]*\)/i;
/**
 * Common CSS named colours. Matched ONLY as a JSON string value that is EXACTLY one of
 * these words (see NAMED_COLOR_VALUE below) — never as a free-text scan. A learner
 * sentence like "The red car is fast" or a Korean explanation that happens to use the
 * English word "red" mid-sentence must NOT trip this: those are multi-word string
 * values, so they never match the anchored `:"word"` pattern. Only a field whose ENTIRE
 * value is a bare colour token (the shape a leaking style value would take) matches.
 */
const CSS_NAMED_COLORS = [
  "red", "orange", "yellow", "green", "blue", "indigo", "violet", "purple",
  "pink", "brown", "black", "white", "gray", "grey", "cyan", "magenta",
  "teal", "navy", "maroon", "olive", "lime", "aqua", "silver", "gold",
  "beige", "coral", "crimson", "turquoise", "salmon", "khaki", "lavender",
  "plum", "orchid", "chocolate", "tan", "ivory", "azure", "mint",
];
const NAMED_COLOR_VALUE = new RegExp(`:\\s*"(?:${CSS_NAMED_COLORS.join("|")})"`, "i");
const GRAMMAR_SEGMENT_TYPES = new Set(["normal", "incorrect", "correction", "highlight"]);
const NATURAL_SEGMENT_TYPES = new Set(["normal", "highlight"]);
/** feedback-deep.md: venn items are single words or short phrases, never sentences */
const MAX_VENN_ITEM_WORDS = 4;
/** feedback-deep.md: venn `items` (per-circle and intersection) are 1-3 short notes. */
const MIN_VENN_ITEMS = 1;
const MAX_VENN_ITEMS = 3;
/** fixed top-level section order the streaming parsers rely on (see feedback.ts, feedback-deep.ts). */
const SLIM_SECTION_ORDER = ["writingScore", "grammar", "naturalExpression"] as const;
const DEEP_SECTION_ORDER = ["conceptualBridge", "toneStyle", "paraphrasing"] as const;

function isRecord(v: unknown): v is Record<string, unknown> {
  return typeof v === "object" && v !== null && !Array.isArray(v);
}

/**
 * Sentence-final 하십시오체 (deferential formal) endings: `-입니다`, `-습니다`, and the
 * `-ㅂ니다` contractions (합니다/됩니다/줍니다/…). The prompt requires 해요체 throughout;
 * the 2026-07-18 sweep found these in 135 of 153 flagged strings, `-입니다` alone being 52%.
 *
 * The negative lookbehind `(?<![답랍아])` exempts three preceding syllables:
 * - `답` and `랍`: `-답니다`/`-랍니다` are deliberately allowed as warm, conversational
 *   endings that suit the app's voice, not deferential formal speech. They were 18 of 153
 *   flags and every one was a false positive.
 * - `아`: the plain-form copula `아니다` ("is not"). Exempting `아니다` prevents
 *   mislabeling 반말 (plain form) as 하십시오체 — they are different register failures
 *   with different causes. No 하십시오체 conjugation ever places a bare `아` immediately
 *   before `니다` (the -ㅂ니다 contraction always fuses, e.g. 닙니다 not 아닙니다 in
 *   deferential form). The honorific copula `아닙니다` still flags correctly.
 *
 * This guard must survive every split/boundary change below: both HASIPSIO_ENDING and
 * HASIPSIO_MID_BOUNDARY carry the identical `(?<![답랍아])` guard, so all three exemptions
 * stay in effect no matter where in the string they land.
 */
const HASIPSIO_ENDING = /(?<![답랍아])니다$/u;

/**
 * A 하십시오체 ending is also a real clause boundary when it's followed by whitespace
 * instead of terminal punctuation — LLM output plausibly drops the period between two short
 * clauses ("정말 잘하셨습니다 다음에 또 만나요"). Without this, the leading 하십시오체
 * clause gets swallowed into one giant "sentence" whose `$` anchor only ever tests the very
 * end — exactly the whole-string-end-only miss the sentence splitter was built to eliminate,
 * just triggered by absent punctuation instead of clause order. Used to insert a synthetic
 * split point so the run-on is tested as two ordinary sentences instead of needing a second
 * detection path; carries the same 답/랍/아 exemptions as HASIPSIO_ENDING.
 */
const HASIPSIO_MID_BOUNDARY = /(?<![답랍아])니다(?=\s)/gu;

/**
 * Quote marks (straight and curly) that may wrap a whole reported sentence. Stripped so a
 * quoted 하십시오체 ending (`"정말 잘하셨습니다"`) still lands its `니다` on the `$` anchor
 * instead of being masked by the trailing quote character.
 */
const WRAPPING_QUOTES = /^["'“”‘’]+|["'“”‘’]+$/gu;

/**
 * Split a Korean line into sentences, dropping empty fragments.
 *
 * Splits on terminal punctuation (`.` `!` `?` `…`) AND on a trailing `~` — the previous
 * whole-string detector explicitly stripped `~` before testing, since a bare tilde is common
 * in this app's warm, casual register and must not defeat the check. Also splits on an
 * implicit boundary right after any 하십시오체 ending that's followed by whitespace rather
 * than punctuation (see HASIPSIO_MID_BOUNDARY), so a punctuation-free run of clauses can't
 * hide an offending clause behind a trailing 해요체 one.
 *
 * Known limitation: this is a punctuation split, not a real tokenizer, so a decimal number
 * or a "Mr."-style abbreviation can fragment the reported sentence text. Detection still
 * fires on whichever fragment carries the actual 하십시오체 ending — only the message may
 * read as a truncated clause rather than the full original sentence.
 */
function splitSentences(s: string): string[] {
  const withImplicitBoundaries = s.replace(HASIPSIO_MID_BOUNDARY, "니다.");
  return withImplicitBoundaries
    .split(/[.!?…~]+/u)
    .map((part) => part.trim().replace(WRAPPING_QUOTES, "").trim())
    .filter((part) => part !== "");
}

/**
 * The sentences in `s` that end in 하십시오체; empty when the line is fully 해요체.
 *
 * Scans EVERY sentence produced by splitSentences (see there for what counts as a sentence
 * boundary). The original detector tested only the end of the whole string, so
 * "표현입니다. 그대로 쓰시면 돼요." — 하십시오체 first, 해요체 last — went entirely
 * uncounted, meaning it silently undercounted the mixed-register problem it existed to
 * measure. Returning the offending sentences (not just a boolean) lets the report name
 * exactly which clause drifted.
 */
export function findHasipsioSentences(s: string): string[] {
  return splitSentences(s).filter((sentence) => HASIPSIO_ENDING.test(sentence));
}

function makeCollector(): { out: Violation[]; err: (c: string, d: string) => void; warn: (c: string, d: string) => void } {
  const out: Violation[] = [];
  return {
    out,
    err: (check, detail) => out.push({ severity: "error", check, detail }),
    warn: (check, detail) => out.push({ severity: "warn", check, detail }),
  };
}

/**
 * Colours are computed client-side (feedback-slim.md, feedback-deep.md) — the model must
 * emit none. Both prompts carry an emphatic "NO colors anywhere" rule, and negative
 * instructions like that are exactly what erodes first as sampling loosens, so this check
 * is widened beyond bare hex codes / an exact `color`/`colour` key: it also catches
 * compound keys ending in colour (`textColor`, `bgColour`), `rgb()`/`rgba()`/`hsl()`/
 * `hsla()` function syntax, and bare named-colour string values (see NAMED_COLOR_VALUE's
 * comment for why that last one is anchored rather than a free-text scan).
 */
function checkNoColor(root: Record<string, unknown>, err: (c: string, d: string) => void): void {
  const serialized = JSON.stringify(root);
  const hex = HEX_COLOR.exec(serialized);
  if (hex) {
    err("no-color", `hex colour in output: ${hex[0]}`);
  }
  const colorKey = COLOR_KEY.exec(serialized);
  if (colorKey) {
    err("no-color", `a colour key is present in output: ${colorKey[0]}`);
  }
  const colorFn = COLOR_FUNCTION.exec(serialized);
  if (colorFn) {
    err("no-color", `colour function syntax in output: ${colorFn[0]}`);
  }
  const namedColor = NAMED_COLOR_VALUE.exec(serialized);
  if (namedColor) {
    err("no-color", `named colour value in output: ${namedColor[0]}`);
  }
}

/**
 * Slim/deep both stream to the client via an incremental parser that relies on the
 * schema's `propertyOrdering` (feedback.ts / IncrementalFeedbackParser): each top-level
 * section is only safe to render once it — and everything before it — has arrived in
 * order. `JSON.parse` preserves key insertion order, so the parsed object already carries
 * this information; a temperature-induced reorder would silently degrade production from
 * incremental rendering to one late burst, and nothing else here would catch it. Only
 * keys that are actually PRESENT are compared (in relative order) against the fixed
 * order — a genuinely missing section is already reported by the per-section checks above/
 * below, so this must not double-report it.
 */
function checkSectionOrder(
  root: Record<string, unknown>,
  order: readonly string[],
  err: (c: string, d: string) => void
): void {
  const expected = order.filter((k) => Object.prototype.hasOwnProperty.call(root, k));
  const actual = Object.keys(root).filter((k) => (order as readonly string[]).includes(k));
  const expectedLabel = expected.join(" → ") || "(none present)";
  const actualLabel = actual.join(" → ") || "(none present)";
  if (actualLabel !== expectedLabel) {
    err("section-order", `expected order ${expectedLabel}, got ${actualLabel}`);
  }
}

/** Learner-facing Korean: non-empty, actually Hangul, and free of 하십시오체. */
function checkKoreanString(
  value: unknown,
  check: string,
  err: (c: string, d: string) => void
): void {
  if (typeof value !== "string" || value.trim() === "") {
    err(check, "missing or empty");
    return;
  }
  if (!HANGUL.test(value)) {
    err(check, `not Korean: ${value}`);
    return;
  }
  const offenders = findHasipsioSentences(value);
  if (offenders.length > 0) {
    // `error`, not `warn`: 해요체 is a rule FEEDBACK_SYSTEM_PROMPT states outright, so
    // breaking it is a defect. It is counted in its own column (run.js `toneErrors`), NOT
    // folded into structural errors — a drifted register and a broken schema are different
    // kinds of failure and each hides the other when summed.
    err("haeyo", `${check} uses 하십시오체 (해요체 required): ${offenders.join(" / ")}`);
  }
}

function checkSegments(
  segments: unknown,
  check: string,
  allowed: Set<string>,
  err: (c: string, d: string) => void
): void {
  if (!Array.isArray(segments) || segments.length === 0) {
    err(check, "missing or empty");
    return;
  }
  segments.forEach((s, i) => {
    if (!isRecord(s)) {
      err(check, `[${i}] is not an object`);
      return;
    }
    if (typeof s.text !== "string") {
      err(check, `[${i}].text is not a string`);
    }
    if (typeof s.type !== "string" || !allowed.has(s.type)) {
      err(check, `[${i}].type invalid: ${JSON.stringify(s.type)}`);
    }
  });
}

export function scoreOf(json: unknown): number | null {
  if (!isRecord(json)) return null;
  const ws = json.writingScore;
  if (!isRecord(ws) || typeof ws.score !== "number") return null;
  return ws.score;
}

export function countIncorrectSegments(json: unknown): number {
  if (!isRecord(json)) return 0;
  const grammar = json.grammar;
  if (!isRecord(grammar)) return 0;
  const cs = grammar.correctedSentence;
  if (!isRecord(cs) || !Array.isArray(cs.segments)) return 0;
  return cs.segments.filter((s) => isRecord(s) && s.type === "incorrect").length;
}

export function validateSlim(json: unknown, expected: CaseExpectation = {}): Violation[] {
  const { out, err } = makeCollector();
  if (!isRecord(json)) {
    err("shape", "top level is not a JSON object");
    return out;
  }
  checkNoColor(json, err);
  checkSectionOrder(json, SLIM_SECTION_ORDER, err);

  // ── writingScore ──────────────────────────────────────────────────────────
  const ws = json.writingScore;
  if (!isRecord(ws)) {
    err("writingScore", "missing");
  } else {
    const score = ws.score;
    if (typeof score !== "number" || !Number.isInteger(score)) {
      err("writingScore.score", `not an integer: ${JSON.stringify(score)}`);
    } else {
      if (score < 0 || score > 100) {
        err("writingScore.score", `outside 0-100: ${score}`);
      }
      if (expected.scoreMin !== undefined && score < expected.scoreMin) {
        err("expect.scoreMin", `score ${score} below expected minimum ${expected.scoreMin}`);
      }
      if (expected.scoreMax !== undefined && score > expected.scoreMax) {
        err("expect.scoreMax", `score ${score} above expected maximum ${expected.scoreMax}`);
      }
    }
    checkKoreanString(ws.encouragementMessage, "writingScore.encouragementMessage", err);
  }

  // ── grammar ───────────────────────────────────────────────────────────────
  const grammar = json.grammar;
  if (!isRecord(grammar)) {
    err("grammar", "missing");
  } else {
    const cs = grammar.correctedSentence;
    if (!isRecord(cs)) {
      err("grammar.correctedSentence", "missing");
    } else {
      checkSegments(cs.segments, "grammar.segments", GRAMMAR_SEGMENT_TYPES, err);
    }
    checkKoreanString(grammar.explanation, "grammar.explanation", err);
  }

  const incorrect = countIncorrectSegments(json);
  if (expected.noIncorrectSegments && incorrect > 0) {
    err(
      "expect.noIncorrectSegments",
      `over-correction: ${incorrect} incorrect segment(s) on a sentence that was already good`
    );
  }
  if (expected.requiresIncorrectSegments && incorrect === 0) {
    err("expect.requiresIncorrectSegments", "the learner's error was not marked incorrect");
  }

  // ── naturalExpression ─────────────────────────────────────────────────────
  const ne = json.naturalExpression;
  if (!isRecord(ne)) {
    err("naturalExpression", "missing");
  } else {
    checkSegments(ne.segments, "naturalExpression.segments", NATURAL_SEGMENT_TYPES, err);
    const reason = ne.reason;
    if (!isRecord(reason)) {
      err("naturalExpression.reason", "missing");
    } else {
      if (typeof reason.keyword !== "string" || reason.keyword.trim() === "") {
        err("naturalExpression.reason.keyword", "missing or empty");
      }
      checkKoreanString(reason.description, "naturalExpression.reason.description", err);
    }
  }

  return out;
}

export function validateDeep(json: unknown): Violation[] {
  const { out, err } = makeCollector();
  if (!isRecord(json)) {
    err("shape", "top level is not a JSON object");
    return out;
  }
  checkNoColor(json, err);
  checkSectionOrder(json, DEEP_SECTION_ORDER, err);

  // ── conceptualBridge ──────────────────────────────────────────────────────
  const cb = json.conceptualBridge;
  if (!isRecord(cb)) {
    err("conceptualBridge", "missing");
  } else {
    checkKoreanString(cb.literalTranslation, "conceptualBridge.literalTranslation", err);
    checkKoreanString(cb.explanation, "conceptualBridge.explanation", err);
    const venn = cb.venn;
    if (!isRecord(venn)) {
      err("venn", "missing");
    } else {
      if (typeof venn.guide !== "string" || venn.guide.trim() === "") {
        err("venn.guide", "missing or empty");
      }
      for (const side of ["leftCircle", "rightCircle"] as const) {
        const circle = venn[side];
        if (!isRecord(circle)) {
          err(`venn.${side}`, "missing");
          continue;
        }
        if (typeof circle.word !== "string" || circle.word.trim() === "") {
          err(`venn.${side}`, "word missing or empty");
        }
        checkVennItems(circle.items, `venn.items`, `${side}`, err);
      }
      const intersection = venn.intersection;
      if (!isRecord(intersection)) {
        err("venn.intersection", "missing");
      } else {
        checkVennItems(intersection.items, "venn.items", "intersection", err);
      }
    }
  }

  // ── toneStyle — EXACTLY 5 levels, 0..4. Not schema-enforced; prompt-only. ──
  const ts = json.toneStyle;
  if (!isRecord(ts)) {
    err("toneStyle", "missing");
  } else {
    if (typeof ts.defaultLevel !== "number" || ts.defaultLevel !== 2) {
      err("toneStyle.defaultLevel", `expected 2, got ${JSON.stringify(ts.defaultLevel)}`);
    }
    const levels = ts.levels;
    if (!Array.isArray(levels) || levels.length !== 5) {
      err(
        "toneStyle.levels",
        `expected exactly 5 levels, got ${Array.isArray(levels) ? levels.length : "none"}`
      );
    } else {
      const seen = new Set<number>();
      levels.forEach((lv, i) => {
        if (!isRecord(lv)) {
          err("toneStyle.levels", `[${i}] is not an object`);
          return;
        }
        if (typeof lv.level !== "number" || lv.level < 0 || lv.level > 4) {
          err("toneStyle.levels", `[${i}].level outside 0-4: ${JSON.stringify(lv.level)}`);
        } else {
          seen.add(lv.level);
        }
        if (typeof lv.sentence !== "string" || lv.sentence.trim() === "") {
          err("toneStyle.levels", `[${i}].sentence missing or empty`);
        }
        if (typeof lv.sentenceTranslation !== "string" || lv.sentenceTranslation.trim() === "") {
          err("toneStyle.levels", `[${i}].sentenceTranslation missing or empty`);
        } else if (!HANGUL.test(lv.sentenceTranslation)) {
          err("toneStyle.levels", `[${i}].sentenceTranslation is not Korean`);
        }
      });
      if (seen.size !== 5) {
        err("toneStyle.levels", `levels 0-4 not all present, got [${[...seen].sort().join(",")}]`);
      }
    }
  }

  // ── paraphrasing — EXACTLY 3, levels 1..3. Also prompt-only. ──────────────
  const para = json.paraphrasing;
  if (!Array.isArray(para) || para.length !== 3) {
    err(
      "paraphrasing",
      `expected exactly 3 alternatives, got ${Array.isArray(para) ? para.length : "none"}`
    );
  } else {
    const seen = new Set<number>();
    para.forEach((p, i) => {
      if (!isRecord(p)) {
        err("paraphrasing", `[${i}] is not an object`);
        return;
      }
      if (typeof p.level !== "number" || p.level < 1 || p.level > 3) {
        err("paraphrasing", `[${i}].level outside 1-3: ${JSON.stringify(p.level)}`);
      } else {
        seen.add(p.level);
      }
      if (typeof p.label !== "string" || p.label.trim() === "") {
        err("paraphrasing", `[${i}].label missing or empty`);
      }
      if (typeof p.sentence !== "string" || p.sentence.trim() === "") {
        err("paraphrasing", `[${i}].sentence missing or empty`);
      }
      if (typeof p.sentenceTranslation !== "string" || p.sentenceTranslation.trim() === "") {
        err("paraphrasing", `[${i}].sentenceTranslation missing or empty`);
      } else if (!HANGUL.test(p.sentenceTranslation)) {
        err("paraphrasing", `[${i}].sentenceTranslation is not Korean`);
      }
    });
    if (seen.size !== 3) {
      err("paraphrasing", `levels 1-3 not all present, got [${[...seen].sort().join(",")}]`);
    }
  }

  return out;
}

function checkVennItems(
  items: unknown,
  check: string,
  where: string,
  err: (c: string, d: string) => void
): void {
  if (!Array.isArray(items)) {
    err(check, `${where}: items is not an array`);
    return;
  }
  // feedback-deep.md: `items` is 1-3 short meaning notes — an empty array is not a
  // degenerate-but-valid case, it's the prompt's cardinality rule silently unmet.
  if (items.length < MIN_VENN_ITEMS || items.length > MAX_VENN_ITEMS) {
    err(
      check,
      `${where}: expected ${MIN_VENN_ITEMS}-${MAX_VENN_ITEMS} items, got ${items.length}`
    );
  }
  items.forEach((item, i) => {
    if (typeof item !== "string" || item.trim() === "") {
      err(check, `${where}[${i}] is not a non-empty string`);
      return;
    }
    const words = item.trim().split(/\s+/u).length;
    if (words > MAX_VENN_ITEM_WORDS) {
      err(check, `${where}[${i}] is ${words} words (max ${MAX_VENN_ITEM_WORDS}): ${item}`);
    }
  });
}

/** `grammar.explanation`, trimmed; null when absent or not a string. */
function explanationOf(json: Record<string, unknown>): string | null {
  const grammar = json.grammar;
  if (!isRecord(grammar) || typeof grammar.explanation !== "string") return null;
  const text = grammar.explanation.trim();
  return text === "" ? null : text;
}

/** The suggested natural phrasing, reassembled from `naturalExpression.segments`. */
function suggestionOf(json: Record<string, unknown>): string | null {
  const ne = json.naturalExpression;
  if (!isRecord(ne) || !Array.isArray(ne.segments)) return null;
  const text = ne.segments
    .map((s) => (isRecord(s) && typeof s.text === "string" ? s.text : ""))
    .join("")
    .trim();
  return text === "" ? null : text;
}

/**
 * Compare two slim responses to the SAME English sentence submitted at two different
 * `level`s. This is the one check `validateSlim` structurally cannot express: it takes a
 * single response, and level-awareness is only observable in the DIFFERENCE between a pair.
 *
 * What level must change, and what it must not:
 *   - `grammar.explanation` and the suggested phrasing MUST adapt — a starter needs simpler
 *     Korean and an easier alternative sentence than an expert. Identical output across the
 *     pair is the signature of `level` being ignored outright (exactly what the 2026-07-18
 *     sweep found: the two responses were character-for-character identical).
 *   - `writingScore.score` must NOT move. The score judges the English itself, so the same
 *     sentence scores the same at every level — otherwise a learner who changes level sees
 *     their number jump for no reason they can perceive. A drift is a `warn` rather than an
 *     `error` because a one-point wobble is model noise, not evidence of level-scaled grading.
 *
 * `lower`/`higher` name the lower- and higher-level response; the checks are symmetric and
 * the order only shapes the message.
 */
export function compareLevelSensitivity(lower: unknown, higher: unknown): Violation[] {
  const { out, err, warn } = makeCollector();
  if (!isRecord(lower) || !isRecord(higher)) {
    err("level.shape", "one or both responses are not JSON objects");
    return out;
  }

  const lowerExplanation = explanationOf(lower);
  const higherExplanation = explanationOf(higher);
  if (lowerExplanation === null || higherExplanation === null) {
    err("level.explanation", "grammar.explanation is missing from one or both responses");
  } else if (lowerExplanation === higherExplanation) {
    err(
      "level.explanation",
      `identical explanation at both levels — level is being ignored: ${lowerExplanation}`
    );
  }

  const lowerSuggestion = suggestionOf(lower);
  const higherSuggestion = suggestionOf(higher);
  if (lowerSuggestion === null || higherSuggestion === null) {
    err(
      "level.naturalExpression",
      "naturalExpression.segments is missing from one or both responses"
    );
  } else if (lowerSuggestion === higherSuggestion) {
    err(
      "level.naturalExpression",
      `identical suggested phrasing at both levels — level is being ignored: ${lowerSuggestion}`
    );
  }

  const lowerScore = scoreOf(lower);
  const higherScore = scoreOf(higher);
  if (lowerScore !== null && higherScore !== null && lowerScore !== higherScore) {
    warn(
      "level.score",
      `score moved with level (${lowerScore} vs ${higherScore}) — scoring is meant to be absolute so a learner's number stays comparable across levels`
    );
  }

  return out;
}
