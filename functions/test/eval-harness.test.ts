import { CASES, EvalCategory } from "../src/eval/cases";
import { parseFeedbackPayload } from "../src/llm/feedback";
import { LEVEL_TOKENS } from "../src/config/levels";
import {
  validateSlim,
  validateDeep,
  scoreOf,
  countIncorrectSegments,
  findHasipsioSentences,
} from "../src/eval/validate";

describe("golden case set", () => {
  it("has 14 cases", () => {
    expect(CASES).toHaveLength(14);
  });

  it("gives every case a unique id", () => {
    const ids = CASES.map((c) => c.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("covers all six categories with at least two cases each", () => {
    const categories: EvalCategory[] = [
      "typo-grammar",
      "konglish",
      "awkward-but-correct",
      "already-good",
      "minimal-input",
      "level-variance",
    ];
    for (const cat of categories) {
      expect(CASES.filter((c) => c.category === cat).length).toBeGreaterThanOrEqual(2);
    }
  });

  it("uses only real level tokens", () => {
    for (const c of CASES) {
      expect(LEVEL_TOKENS).toContain(c.payload.level);
    }
  });

  it("survives the production payload parser unchanged", () => {
    // If a case is malformed the parser silently rewrites it (e.g. an unknown level
    // becomes "normal"), which would make the eval measure something other than the
    // case as written. Round-tripping catches that.
    for (const c of CASES) {
      expect(parseFeedbackPayload(c.payload)).toEqual(c.payload);
    }
  });

  it("explains what each case probes", () => {
    for (const c of CASES) {
      expect(c.note.trim().length).toBeGreaterThan(0);
    }
  });

  it("pairs the level-variance cases on identical English", () => {
    // The pair exists to isolate `level` as the only variable — the prompt never
    // mentions `level`, so identical output across the pair is the finding.
    const pair = CASES.filter((c) => c.category === "level-variance");
    expect(pair).toHaveLength(2);
    expect(pair[0].payload.userEnglish).toBe(pair[1].payload.userEnglish);
    expect(pair[0].payload.koreanPrompt).toBe(pair[1].payload.koreanPrompt);
    expect(pair[0].payload.level).not.toBe(pair[1].payload.level);
  });
});

const GOOD_SLIM = {
  writingScore: { score: 85, encouragementMessage: "정말 잘했어요!" },
  grammar: {
    correctedSentence: {
      segments: [
        { text: "I ", type: "normal" },
        { text: "meet", type: "incorrect" },
        { text: "met", type: "correction" },
        { text: " my friend.", type: "normal" },
      ],
    },
    explanation: "지난 일을 말할 때는 met을 써요.",
  },
  naturalExpression: {
    segments: [
      { text: "I ", type: "normal" },
      { text: "caught up with", type: "highlight" },
      { text: " my friend.", type: "normal" },
    ],
    reason: { keyword: "caught up with", description: "친구를 만났다는 느낌이 더 살아나요." },
  },
};

/** deep-clone so a mutation in one test cannot leak into the next */
function clone<T>(o: T): T {
  return JSON.parse(JSON.stringify(o)) as T;
}

describe("validateSlim", () => {
  it("passes a well-formed response", () => {
    expect(validateSlim(GOOD_SLIM)).toEqual([]);
  });

  it("rejects a non-object", () => {
    expect(validateSlim(null).some((v) => v.check === "shape")).toBe(true);
    expect(validateSlim([]).some((v) => v.check === "shape")).toBe(true);
    expect(validateSlim("{}").some((v) => v.check === "shape")).toBe(true);
  });

  it("rejects a score outside 0-100", () => {
    const bad = clone(GOOD_SLIM);
    bad.writingScore.score = 120;
    expect(validateSlim(bad).some((v) => v.check === "writingScore.score")).toBe(true);
  });

  it("rejects a non-integer score", () => {
    const bad = clone(GOOD_SLIM);
    bad.writingScore.score = 85.5;
    expect(validateSlim(bad).some((v) => v.check === "writingScore.score")).toBe(true);
  });

  it("rejects an unknown segment type", () => {
    const bad = clone(GOOD_SLIM);
    bad.grammar.correctedSentence.segments[0].type = "bogus";
    expect(validateSlim(bad).some((v) => v.check === "grammar.segments")).toBe(true);
  });

  it("rejects `correction` in naturalExpression — only normal|highlight are allowed there", () => {
    const bad = clone(GOOD_SLIM);
    bad.naturalExpression.segments[1].type = "correction";
    expect(validateSlim(bad).some((v) => v.check === "naturalExpression.segments")).toBe(true);
  });

  it("rejects any colour — the client derives it from the score", () => {
    const bad = {
      ...clone(GOOD_SLIM),
      writingScore: { ...GOOD_SLIM.writingScore, color: "#FF0000" },
    };
    const violations = validateSlim(bad);
    expect(violations.filter((v) => v.check === "no-color").length).toBeGreaterThan(0);
  });

  it("rejects a non-Korean learner-facing string", () => {
    const bad = clone(GOOD_SLIM);
    bad.grammar.explanation = "Use the past tense here.";
    expect(validateSlim(bad).some((v) => v.check === "grammar.explanation")).toBe(true);
  });

  it("warns on a line that ends in 하십시오체", () => {
    const bad = clone(GOOD_SLIM);
    bad.writingScore.encouragementMessage = "정말 잘하셨습니다.";
    const violations = validateSlim(bad);
    expect(violations.some((v) => v.check === "haeyo" && v.severity === "warn")).toBe(true);
    expect(violations.some((v) => v.severity === "error")).toBe(false);
  });

  it("warns when 하십시오체 appears in a NON-final sentence", () => {
    // The old detector only tested the end of the whole string, so this direction of
    // mixed register went completely uncounted.
    const bad = clone(GOOD_SLIM);
    bad.grammar.explanation = "아주 좋은 표현입니다. 그대로 쓰시면 돼요.";
    expect(validateSlim(bad).some((v) => v.check === "haeyo")).toBe(true);
  });

  it("does not warn on 답니다 / 랍니다 — warm, not deferential", () => {
    const ok = clone(GOOD_SLIM);
    ok.grammar.explanation = "중복을 피하면 훨씬 깔끔해진답니다.";
    expect(validateSlim(ok).some((v) => v.check === "haeyo")).toBe(false);

    const ok2 = clone(GOOD_SLIM);
    ok2.naturalExpression.reason.description = "원어민이 가장 선호하는 방식이랍니다.";
    expect(validateSlim(ok2).some((v) => v.check === "haeyo")).toBe(false);
  });

  it("flags over-correction when the case expects none", () => {
    // GOOD_SLIM has one `incorrect` segment
    const violations = validateSlim(GOOD_SLIM, { noIncorrectSegments: true });
    expect(violations.some((v) => v.check === "expect.noIncorrectSegments")).toBe(true);
  });

  it("flags a missed error when the case requires one", () => {
    const clean = clone(GOOD_SLIM);
    clean.grammar.correctedSentence.segments = [{ text: "I met my friend.", type: "normal" }];
    const violations = validateSlim(clean, { requiresIncorrectSegments: true });
    expect(violations.some((v) => v.check === "expect.requiresIncorrectSegments")).toBe(true);
  });

  it("enforces the score bounds a case declares", () => {
    expect(validateSlim(GOOD_SLIM, { scoreMin: 90 }).some((v) => v.check === "expect.scoreMin")).toBe(true);
    expect(validateSlim(GOOD_SLIM, { scoreMax: 80 }).some((v) => v.check === "expect.scoreMax")).toBe(true);
    expect(validateSlim(GOOD_SLIM, { scoreMin: 80, scoreMax: 90 })).toEqual([]);
  });

  // ── section order (Fix 2) — production streams sections in schema propertyOrdering
  // order (feedback.ts IncrementalFeedbackParser); a reorder silently degrades that to
  // one late burst, and nothing else here checks it. ─────────────────────────────────
  it("passes when the three top-level sections are in the fixed writingScore → grammar → naturalExpression order", () => {
    // GOOD_SLIM's own literal key order already matches — this is the control case.
    expect(validateSlim(GOOD_SLIM).some((v) => v.check === "section-order")).toBe(false);
  });

  it("flags a reordered-but-otherwise-valid response", () => {
    const reordered = {
      grammar: GOOD_SLIM.grammar,
      writingScore: GOOD_SLIM.writingScore,
      naturalExpression: GOOD_SLIM.naturalExpression,
    };
    expect(validateSlim(reordered).some((v) => v.check === "section-order" && v.severity === "error")).toBe(true);
  });

  it("does not double-report a section that is simply absent as an order violation", () => {
    const missingNatural = { writingScore: GOOD_SLIM.writingScore, grammar: GOOD_SLIM.grammar };
    const violations = validateSlim(missingNatural);
    expect(violations.some((v) => v.check === "naturalExpression")).toBe(true);
    expect(violations.some((v) => v.check === "section-order")).toBe(false);
  });

  // ── widened colour check (Fix 9) ────────────────────────────────────────────────────
  it("catches a compound key ending in Color/Colour (textColor, bgColour) even with a non-hex, non-named value", () => {
    const bad1 = { ...clone(GOOD_SLIM), writingScore: { ...GOOD_SLIM.writingScore, textColor: "primary" } };
    expect(validateSlim(bad1).some((v) => v.check === "no-color")).toBe(true);
    const bad2 = { ...clone(GOOD_SLIM), writingScore: { ...GOOD_SLIM.writingScore, bgColour: "brand01" } };
    expect(validateSlim(bad2).some((v) => v.check === "no-color")).toBe(true);
  });

  it("catches rgb()/rgba()/hsl()/hsla() function syntax regardless of key name", () => {
    const rgb = { ...clone(GOOD_SLIM), writingScore: { ...GOOD_SLIM.writingScore, bg: "rgb(255,0,0)" } };
    expect(validateSlim(rgb).some((v) => v.check === "no-color")).toBe(true);
    const rgba = { ...clone(GOOD_SLIM), writingScore: { ...GOOD_SLIM.writingScore, bg: "rgba(0,0,0,0.5)" } };
    expect(validateSlim(rgba).some((v) => v.check === "no-color")).toBe(true);
    const hsl = { ...clone(GOOD_SLIM), writingScore: { ...GOOD_SLIM.writingScore, bg: "hsl(0,100%,50%)" } };
    expect(validateSlim(hsl).some((v) => v.check === "no-color")).toBe(true);
    const hsla = { ...clone(GOOD_SLIM), writingScore: { ...GOOD_SLIM.writingScore, bg: "hsla(0,100%,50%,0.5)" } };
    expect(validateSlim(hsla).some((v) => v.check === "no-color")).toBe(true);
  });

  it("catches a bare named-colour string value", () => {
    const bad = { ...clone(GOOD_SLIM), writingScore: { ...GOOD_SLIM.writingScore, bg: "Coral" } };
    expect(validateSlim(bad).some((v) => v.check === "no-color")).toBe(true);
  });

  it("does NOT flag a legitimate learner-facing sentence that merely contains a colour word", () => {
    // "red" appears mid-sentence, not as a bare colour-token value — must not trip the
    // widened named-colour check (that's the calibration this check depends on).
    const ok = clone(GOOD_SLIM);
    ok.grammar.correctedSentence.segments = [
      { text: "The red car is fast.", type: "normal" },
    ];
    const violations = validateSlim(ok);
    expect(violations.some((v) => v.check === "no-color")).toBe(false);
  });
});

describe("scoreOf / countIncorrectSegments", () => {
  it("reads the score, or null when absent", () => {
    expect(scoreOf(GOOD_SLIM)).toBe(85);
    expect(scoreOf({})).toBeNull();
    expect(scoreOf(null)).toBeNull();
  });

  it("counts incorrect segments", () => {
    expect(countIncorrectSegments(GOOD_SLIM)).toBe(1);
    expect(countIncorrectSegments({})).toBe(0);
  });
});

const GOOD_DEEP = {
  conceptualBridge: {
    literalTranslation: "저는 밥을 먹어요.",
    explanation: "의도와 실제 의미가 조금 달라요.",
    venn: {
      guide: "두 단어의 쓰임을 비교해 봐요.",
      leftCircle: { word: "eat rice", items: ["밥을 먹다"] },
      rightCircle: { word: "grab a meal", items: ["식사하다", "가볍게 만나다"] },
      intersection: { items: ["먹다"] },
    },
  },
  toneStyle: {
    defaultLevel: 2,
    levels: [
      { level: 0, sentence: "Might we dine together?", sentenceTranslation: "함께 식사하시겠어요?" },
      { level: 1, sentence: "Would you like to have a meal?", sentenceTranslation: "식사 한번 하실래요?" },
      { level: 2, sentence: "Let's grab a meal sometime.", sentenceTranslation: "언제 밥 한번 먹어요." },
      { level: 3, sentence: "Let's get food sometime.", sentenceTranslation: "언제 밥 먹자." },
      { level: 4, sentence: "Yo, food sometime?", sentenceTranslation: "야, 언제 밥?" },
    ],
  },
  paraphrasing: [
    { level: 1, label: "Beginner", sentence: "Let's eat together.", sentenceTranslation: "같이 먹어요." },
    { level: 2, label: "Intermediate", sentence: "Let's grab a meal.", sentenceTranslation: "밥 한번 먹어요." },
    { level: 3, label: "Advanced", sentence: "We should catch up over a meal.", sentenceTranslation: "밥 먹으면서 얘기해요." },
  ],
};

describe("validateDeep", () => {
  it("passes a well-formed response", () => {
    expect(validateDeep(GOOD_DEEP)).toEqual([]);
  });

  it("requires exactly five tone levels", () => {
    // The schema does NOT enforce this (no minItems/maxItems) — only the prompt asks
    // for it, so it is exactly the kind of thing a temperature change can break.
    const bad = clone(GOOD_DEEP);
    bad.toneStyle.levels = bad.toneStyle.levels.slice(0, 4);
    expect(validateDeep(bad).some((v) => v.check === "toneStyle.levels")).toBe(true);
  });

  it("requires tone levels 0 through 4", () => {
    const bad = clone(GOOD_DEEP);
    bad.toneStyle.levels[4].level = 9;
    expect(validateDeep(bad).some((v) => v.check === "toneStyle.levels")).toBe(true);
  });

  it("requires exactly three paraphrases at levels 1-3", () => {
    const bad = clone(GOOD_DEEP);
    bad.paraphrasing = bad.paraphrasing.slice(0, 2);
    expect(validateDeep(bad).some((v) => v.check === "paraphrasing")).toBe(true);
  });

  it("requires a non-empty Korean translation on every sentence", () => {
    const bad = clone(GOOD_DEEP);
    bad.toneStyle.levels[0].sentenceTranslation = "";
    expect(validateDeep(bad).some((v) => v.check === "toneStyle.levels")).toBe(true);
  });

  it("rejects a venn item longer than four words", () => {
    const bad = clone(GOOD_DEEP);
    bad.conceptualBridge.venn.leftCircle.items = [
      "물건을 사고 받은 증명서를 건네줄 때 쓰는 표현",
    ];
    expect(validateDeep(bad).some((v) => v.check === "venn.items")).toBe(true);
  });

  it("rejects any colour in deep output", () => {
    const bad = clone(GOOD_DEEP);
    // cast at the leaf only — the surrounding object keeps its inferred type
    (bad.conceptualBridge.venn.leftCircle as Record<string, unknown>).color = "#00FF00";
    expect(validateDeep(bad).some((v) => v.check === "no-color")).toBe(true);
  });

  // ── section order (Fix 2) — deep streams conceptualBridge → toneStyle → paraphrasing. ──
  it("passes when the three deep sections are in the fixed order", () => {
    expect(validateDeep(GOOD_DEEP).some((v) => v.check === "section-order")).toBe(false);
  });

  it("flags a reordered-but-otherwise-valid deep response", () => {
    const reordered = {
      toneStyle: GOOD_DEEP.toneStyle,
      conceptualBridge: GOOD_DEEP.conceptualBridge,
      paraphrasing: GOOD_DEEP.paraphrasing,
    };
    expect(validateDeep(reordered).some((v) => v.check === "section-order" && v.severity === "error")).toBe(true);
  });

  // ── venn items cardinality (Fix 10) — 1-3 items, empty was silently accepted before. ──
  it("rejects an empty venn items array", () => {
    const bad = clone(GOOD_DEEP);
    bad.conceptualBridge.venn.leftCircle.items = [];
    expect(validateDeep(bad).some((v) => v.check === "venn.items")).toBe(true);
  });

  it("rejects a venn items array with more than three items", () => {
    const bad = clone(GOOD_DEEP);
    bad.conceptualBridge.venn.intersection.items = ["하나", "둘", "셋", "넷"];
    expect(validateDeep(bad).some((v) => v.check === "venn.items")).toBe(true);
  });
});

describe("findHasipsioSentences", () => {
  it("returns nothing for a fully 해요체 line", () => {
    expect(findHasipsioSentences("지난 일을 말할 때는 met을 써요.")).toEqual([]);
    expect(findHasipsioSentences("정말 잘했어요! 자연스러운 표현이에요.")).toEqual([]);
  });

  it("catches every 하십시오체 family the sweep actually produced", () => {
    // -입니다 was 52% of all flagged strings on 2026-07-18; -습니다 and -ㅂ니다 the rest.
    expect(findHasipsioSentences("아주 자연스러운 표현입니다.")).toHaveLength(1);
    expect(findHasipsioSentences("정말 잘하셨습니다.")).toHaveLength(1);
    expect(findHasipsioSentences("훨씬 부드러운 인상을 줍니다.")).toHaveLength(1);
    expect(findHasipsioSentences("문장이 훨씬 깔끔해집니다.")).toHaveLength(1);
    expect(findHasipsioSentences("아주 좋습니다.")).toHaveLength(1);
  });

  it("allows 답니다 and 랍니다", () => {
    expect(findHasipsioSentences("하나만 써도 충분하답니다!")).toEqual([]);
    expect(findHasipsioSentences("원어민이 선호하는 방식이랍니다.")).toEqual([]);
  });

  it("scans every sentence, not just the last", () => {
    const mixed = "아주 좋은 표현입니다. 그대로 쓰시면 돼요.";
    expect(findHasipsioSentences(mixed)).toEqual(["아주 좋은 표현입니다"]);
  });

  it("returns each offending sentence, so a mixed line reports all of them", () => {
    const line = "문법이 완벽합니다. 잘하셨어요. 아주 좋은 표현입니다.";
    expect(findHasipsioSentences(line)).toEqual([
      "문법이 완벽합니다",
      "아주 좋은 표현입니다",
    ]);
  });

  it("handles a line with no terminal punctuation", () => {
    expect(findHasipsioSentences("아주 자연스러운 표현입니다")).toHaveLength(1);
    expect(findHasipsioSentences("아주 자연스러운 표현이에요")).toEqual([]);
  });

  it("ignores empty and whitespace-only input", () => {
    expect(findHasipsioSentences("")).toEqual([]);
    expect(findHasipsioSentences("   ")).toEqual([]);
    expect(findHasipsioSentences("...")).toEqual([]);
  });

  // ── Fix round 1: Finding 1 — a trailing tilde must not defeat the check ────────────
  it("strips a trailing tilde, so a 하십시오체 ending followed by ~ is still flagged", () => {
    expect(findHasipsioSentences("잘하셨습니다~")).toEqual(["잘하셨습니다"]);
    expect(findHasipsioSentences("잘하셨습니다~~")).toEqual(["잘하셨습니다"]);
  });

  it("does not flag 해요체 with a trailing tilde", () => {
    expect(findHasipsioSentences("잘했어요~")).toEqual([]);
    expect(findHasipsioSentences("자연스러워요~~")).toEqual([]);
  });

  // ── Fix round 1: Finding 2 — clauses with no terminal punctuation between them ─────
  it("flags a 하십시오체 clause even when no punctuation separates it from what follows", () => {
    expect(findHasipsioSentences("정말 잘하셨습니다 다음에 또 만나요")).toEqual([
      "정말 잘하셨습니다",
    ]);
  });

  it("still allows 답니다/랍니다 in every position, including mid-string before whitespace", () => {
    expect(findHasipsioSentences("충분하답니다!")).toEqual([]);
    expect(findHasipsioSentences("방식이랍니다.")).toEqual([]);
    expect(findHasipsioSentences("전달된답니다")).toEqual([]);
  });

  it("keeps the already-working non-final-sentence and multi-offender cases correct", () => {
    expect(findHasipsioSentences("아주 좋은 표현입니다. 그대로 쓰시면 돼요.")).toEqual([
      "아주 좋은 표현입니다",
    ]);
    expect(
      findHasipsioSentences("문법이 완벽합니다. 잘하셨어요. 아주 좋은 표현입니다.")
    ).toEqual(["문법이 완벽합니다", "아주 좋은 표현입니다"]);
  });

  it("keeps a fully-해요체 multi-sentence line clean", () => {
    expect(
      findHasipsioSentences("지난 일은 met을 써요. 이렇게 쓰면 훨씬 자연스러워요!")
    ).toEqual([]);
  });

  it("flags every required 하십시오체 form", () => {
    expect(findHasipsioSentences("표현입니다.")).toHaveLength(1);
    expect(findHasipsioSentences("잘하셨습니다.")).toHaveLength(1);
    expect(findHasipsioSentences("줍니다.")).toHaveLength(1);
    expect(findHasipsioSentences("됩니다.")).toHaveLength(1);
    expect(findHasipsioSentences("좋습니다.")).toHaveLength(1);
    expect(findHasipsioSentences("훌륭합니다.")).toHaveLength(1);
  });

  // ── Optional minor fix: a quote-wrapped ending should not slip through ─────────────
  it("flags a 하십시오체 ending wrapped in quotes", () => {
    expect(findHasipsioSentences('"정말 잘하셨습니다"')).toEqual(["정말 잘하셨습니다"]);
  });

  it("handles input with no terminal punctuation at all without crashing", () => {
    expect(findHasipsioSentences("~")).toEqual([]);
    expect(() => findHasipsioSentences("그냥 텍스트임")).not.toThrow();
    expect(findHasipsioSentences("그냥 텍스트임")).toEqual([]);
  });
});
