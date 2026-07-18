import { CASES, EvalCategory } from "../src/eval/cases";
import { parseFeedbackPayload } from "../src/llm/feedback";
import { LEVEL_TOKENS } from "../src/config/levels";
import {
  validateSlim,
  validateDeep,
  scoreOf,
  countIncorrectSegments,
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

  it("warns — but does not fail — on a Korean line that is not 해요체", () => {
    const bad = clone(GOOD_SLIM);
    bad.writingScore.encouragementMessage = "훌륭하다";
    const violations = validateSlim(bad);
    expect(violations.some((v) => v.check === "haeyo" && v.severity === "warn")).toBe(true);
    expect(violations.some((v) => v.severity === "error")).toBe(false);
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
});
