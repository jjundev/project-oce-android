import { buildGenerateBody, buildRepairBody } from "../src/providers/gemini";
import { tuningFor, GENERATION_TUNING, FEEDBACK_TEMPERATURE } from "../src/config/generation";

describe("buildGenerateBody — current behaviour (characterisation)", () => {
  it("wraps the payload as one user text part with role", () => {
    const body = buildGenerateBody({ a: 1 });
    expect(body.contents).toEqual([
      { role: "user", parts: [{ text: '{"a":1}' }] },
    ]);
  });

  it("always requests JSON output", () => {
    const body = buildGenerateBody({});
    expect(body.generationConfig).toEqual({ responseMimeType: "application/json" });
  });

  it("includes responseSchema only when provided", () => {
    const schema = { type: "OBJECT" };
    expect(buildGenerateBody({}, undefined, schema).generationConfig).toEqual({
      responseMimeType: "application/json",
      responseSchema: schema,
    });
    expect(buildGenerateBody({}, undefined, undefined).generationConfig).toEqual({
      responseMimeType: "application/json",
    });
  });

  it("includes systemInstruction only when a non-empty system prompt is given", () => {
    expect(buildGenerateBody({}, "be nice").systemInstruction).toEqual({
      parts: [{ text: "be nice" }],
    });
    expect(buildGenerateBody({}, "").systemInstruction).toBeUndefined();
    expect(buildGenerateBody({}, undefined).systemInstruction).toBeUndefined();
  });

  it("serialises a nullish payload as an empty object", () => {
    expect(buildGenerateBody(undefined).contents).toEqual([
      { role: "user", parts: [{ text: "{}" }] },
    ]);
  });

  it("adds temperature only when tuning provides it", () => {
    expect(buildGenerateBody({}, undefined, undefined, { temperature: 0.3 }).generationConfig)
      .toEqual({ responseMimeType: "application/json", temperature: 0.3 });
    // an empty tuning object must not introduce the key at all
    expect(buildGenerateBody({}, undefined, undefined, {}).generationConfig)
      .toEqual({ responseMimeType: "application/json" });
    expect(buildGenerateBody({}, undefined, undefined, undefined).generationConfig)
      .toEqual({ responseMimeType: "application/json" });
  });

  it("keeps temperature 0 — a falsy but meaningful value", () => {
    expect(buildGenerateBody({}, undefined, undefined, { temperature: 0 }).generationConfig)
      .toEqual({ responseMimeType: "application/json", temperature: 0 });
  });
});

describe("buildRepairBody — current behaviour (characterisation)", () => {
  it("appends the bad output and a repair instruction to the original contents", () => {
    const body = buildRepairBody({ a: 1 }, "sys", { type: "OBJECT" }, "not json", "boom");
    const contents = body.contents as Array<Record<string, unknown>>;
    expect(contents).toHaveLength(3);
    expect(contents[0]).toEqual({ role: "user", parts: [{ text: '{"a":1}' }] });
    expect(contents[1]).toEqual({ role: "model", parts: [{ text: "not json" }] });
    expect(contents[2].role).toBe("user");
    expect(String((contents[2].parts as Array<{ text: string }>)[0].text)).toContain("boom");
  });

  it("carries the original systemInstruction and schema through", () => {
    const schema = { type: "OBJECT" };
    const body = buildRepairBody({}, "sys", schema, "bad", "err");
    expect(body.systemInstruction).toEqual({ parts: [{ text: "sys" }] });
    expect(body.generationConfig).toEqual({
      responseMimeType: "application/json",
      responseSchema: schema,
    });
  });

  it("carries tuning through to the repair attempt", () => {
    const body = buildRepairBody({}, "sys", undefined, "bad", "err", { temperature: 0.3 });
    expect(body.generationConfig).toEqual({
      responseMimeType: "application/json",
      temperature: 0.3,
    });
  });
});

describe("tuningFor", () => {
  it("gives feedback and feedbackDeep the confirmed feedback temperature", () => {
    expect(tuningFor("feedback")).toEqual({ temperature: FEEDBACK_TEMPERATURE });
    expect(tuningFor("feedbackDeep")).toEqual({ temperature: FEEDBACK_TEMPERATURE });
  });

  it("leaves dialogue, summary, speaking and tts unset (provider default)", () => {
    // Out of scope for this plan — they have no eval coverage, so setting a
    // temperature for them could regress behaviour nothing here measures.
    expect(tuningFor("dialogue")).toEqual({});
    expect(tuningFor("summary")).toEqual({});
    expect(tuningFor("speaking")).toEqual({});
    expect(tuningFor("tts")).toEqual({});
  });

  it("normalises a sub-task id to its family", () => {
    // "summary.expressions" is not in the closed Task map (gemini.ts:203-205).
    expect(tuningFor("summary.expressions")).toEqual(tuningFor("summary"));
  });

  it("falls back to empty tuning for an unknown task", () => {
    expect(tuningFor("nonsense")).toEqual({});
    expect(tuningFor("")).toEqual({});
  });

  it("keeps the feedback temperature within the valid Gemini range", () => {
    expect(FEEDBACK_TEMPERATURE).toBeGreaterThanOrEqual(0);
    expect(FEEDBACK_TEMPERATURE).toBeLessThanOrEqual(2);
  });

  it("covers every task in the closed Task map", () => {
    expect(Object.keys(GENERATION_TUNING).sort()).toEqual(
      ["dialogue", "feedback", "feedbackDeep", "speaking", "summary", "tts"].sort()
    );
  });
});
