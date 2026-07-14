import {
  IncrementalFeedbackParser,
  InvalidFeedbackPayloadError,
  extractCompletedObjectValue,
  parseFeedbackPayload,
} from "../src/llm/feedback";

describe("parseFeedbackPayload", () => {
  it("accepts a full payload", () => {
    expect(
      parseFeedbackPayload({
        koreanPrompt: "커피 한 잔 주세요",
        userEnglish: "One coffee please",
        referenceEnglish: "Can I get a coffee?",
        level: "normal",
      })
    ).toEqual({
      koreanPrompt: "커피 한 잔 주세요",
      userEnglish: "One coffee please",
      referenceEnglish: "Can I get a coffee?",
      level: "normal",
    });
  });

  it("allows empty referenceEnglish and defaults an out-of-range level to normal", () => {
    expect(
      parseFeedbackPayload({ koreanPrompt: "안녕", userEnglish: "Hi", level: "legendary" })
    ).toEqual({ koreanPrompt: "안녕", userEnglish: "Hi", referenceEnglish: "", level: "normal" });
  });

  it("rejects a missing koreanPrompt", () => {
    expect(() =>
      parseFeedbackPayload({ koreanPrompt: "  ", userEnglish: "Hi" })
    ).toThrow(InvalidFeedbackPayloadError);
  });

  it("rejects a missing userEnglish", () => {
    expect(() =>
      parseFeedbackPayload({ koreanPrompt: "안녕", userEnglish: "" })
    ).toThrow(InvalidFeedbackPayloadError);
  });
});

describe("parseFeedbackPayload 5-tier level", () => {
  const base = { koreanPrompt: "안녕", userEnglish: "hi", referenceEnglish: "" };
  it("passes through the two new tokens", () => {
    expect(parseFeedbackPayload({ ...base, level: "starter" }).level).toBe("starter");
    expect(parseFeedbackPayload({ ...base, level: "expert" }).level).toBe("expert");
  });
  it("still defaults unknown level to normal", () => {
    expect(parseFeedbackPayload({ ...base, level: "A2" }).level).toBe("normal");
  });
});

/** canonical slim object: three sections in propertyOrdering. */
const FULL_JSON =
  '{"writingScore":{"score":85,"encouragementMessage":"정말 잘했어요!"},' +
  '"grammar":{"correctedSentence":{"segments":[' +
  '{"text":"I ","type":"normal"},{"text":"goed","type":"incorrect"},{"text":"went","type":"correction"}' +
  ']},"explanation":"과거형을 바르게 썼어요."},' +
  '"naturalExpression":{"segments":[{"text":"I went there","type":"normal"}],' +
  '"reason":{"keyword":"자연스러움","description":"이미 자연스러워요."}}}';

describe("IncrementalFeedbackParser — full object", () => {
  it("emits the three sections once, in fixed order, flattened with `section`", () => {
    const parser = new IncrementalFeedbackParser();
    const sections = parser.addChunk(FULL_JSON);
    expect(sections.map((s) => s.section)).toEqual([
      "writingScore",
      "grammar",
      "naturalExpression",
    ]);
    expect(sections[0]).toEqual({
      section: "writingScore",
      score: 85,
      encouragementMessage: "정말 잘했어요!",
    });
    expect(sections[1]).toMatchObject({ section: "grammar", explanation: "과거형을 바르게 썼어요." });
    expect(sections[2]).toMatchObject({
      section: "naturalExpression",
      reason: { keyword: "자연스러움", description: "이미 자연스러워요." },
    });
  });

  it("does not re-emit a section across chunks", () => {
    const parser = new IncrementalFeedbackParser();
    const first = parser.addChunk(FULL_JSON);
    const second = parser.addChunk("");
    expect(first).toHaveLength(3);
    expect(second).toHaveLength(0);
  });
});

describe("IncrementalFeedbackParser — progressive streaming", () => {
  it("emits each section only once its object boundary completes, in order", () => {
    const parser = new IncrementalFeedbackParser();
    const emitted: string[] = [];

    // A half-written writingScore emits nothing (closing brace not yet arrived).
    emitted.push(
      ...parser.addChunk('{"writingScore":{"score":85,"encouragementMessage":"정말').map((s) => s.section)
    );
    expect(emitted).toEqual([]);

    // Complete writingScore + open grammar → only writingScore emits.
    emitted.push(
      ...parser
        .addChunk(
          ' 잘했어요!"},"grammar":{"correctedSentence":{"segments":[{"text":"ok","type":"normal"}]}'
        )
        .map((s) => s.section)
    );
    expect(emitted).toEqual(["writingScore"]);

    // Close grammar + full naturalExpression → grammar then naturalExpression emit, in order.
    emitted.push(
      ...parser
        .addChunk(
          ',"explanation":"좋아요."},"naturalExpression":{"segments":[{"text":"x","type":"normal"}],' +
            '"reason":{"keyword":"k","description":"d"}}}'
        )
        .map((s) => s.section)
    );
    expect(emitted).toEqual(["writingScore", "grammar", "naturalExpression"]);
  });
});

describe("extractCompletedObjectValue — brace-depth extraction", () => {
  it("returns undefined until the object closes", () => {
    expect(extractCompletedObjectValue('{"grammar":{"a":1', "grammar")).toBeUndefined();
  });

  it("extracts a nested object value, brace-depth aware", () => {
    expect(
      extractCompletedObjectValue('{"grammar":{"a":{"b":2},"c":3},"next":1}', "grammar")
    ).toBe('{"a":{"b":2},"c":3}');
  });

  it("is string-escape aware (braces inside strings do not miscount depth)", () => {
    expect(
      extractCompletedObjectValue('{"grammar":{"t":"a } b { c"}}', "grammar")
    ).toBe('{"t":"a } b { c"}');
  });

  it("returns undefined when the value is not an object", () => {
    expect(extractCompletedObjectValue('{"grammar":"str"}', "grammar")).toBeUndefined();
  });
});
