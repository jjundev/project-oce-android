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
  it("applies the confirmed feedback temperature to feedback and feedbackDeep only", () => {
    // Confirmed by the 2026-07-18 sweep (280 calls, stddev 0.6 at t=0 vs 1.2-1.4
    // elsewhere) — see config/generation.ts. FEEDBACK_TEMPERATURE is 0, a falsy value,
    // so assert the actual number rather than just "is defined": a `!== undefined`
    // regression at the tuning-table level would still pass a definedness-only check.
    expect(FEEDBACK_TEMPERATURE).toBe(0);
    expect(tuningFor("feedback")).toEqual({ temperature: 0 });
    expect(tuningFor("feedbackDeep")).toEqual({ temperature: 0 });
    expect(tuningFor("feedback").temperature).toBe(FEEDBACK_TEMPERATURE);
    expect(tuningFor("feedbackDeep").temperature).toBe(FEEDBACK_TEMPERATURE);
  });

  it("leaves dialogue, summary, speaking and tts unset (provider default)", () => {
    // They have no eval coverage, so setting a temperature for them could regress
    // behaviour nothing here measures.
    expect(tuningFor("dialogue")).toEqual({});
    expect(tuningFor("summary")).toEqual({});
    expect(tuningFor("speaking")).toEqual({});
    expect(tuningFor("tts")).toEqual({});
  });

  it("carries a temperature for exactly feedback and feedbackDeep in GENERATION_TUNING", () => {
    // If a future edit turns a temperature on/off for the wrong task, this fails loudly.
    const withTemperature = ["feedback", "feedbackDeep"];
    for (const task of Object.keys(GENERATION_TUNING)) {
      const tuning = GENERATION_TUNING[task as keyof typeof GENERATION_TUNING];
      if (withTemperature.includes(task)) {
        expect(tuning).toEqual({ temperature: 0 });
      } else {
        expect(tuning).toEqual({});
      }
    }
  });

  it("normalises a sub-task id to its family", () => {
    // "summary.expressions" is not in the closed Task map (gemini.ts:203-205).
    expect(tuningFor("summary.expressions")).toEqual(tuningFor("summary"));
  });

  it("falls back to empty tuning for an unknown task", () => {
    expect(tuningFor("nonsense")).toEqual({});
    expect(tuningFor("")).toEqual({});
  });

  it("does not fall through to Object.prototype for a task named after a prototype property", () => {
    // tuningFor used to index GENERATION_TUNING as a plain object literal, so
    // tuningFor("__proto__") returned Object.prototype instead of {}.
    expect(tuningFor("__proto__")).toEqual({});
    expect(tuningFor("toString")).toEqual({});
    expect(tuningFor("constructor")).toEqual({});
  });

  it("returns a tuning object a caller cannot mutate the shared table through", () => {
    const first = tuningFor("dialogue");
    (first as { temperature?: number }).temperature = 0.9;
    expect(tuningFor("dialogue")).toEqual({});
    expect(GENERATION_TUNING.dialogue).toEqual({});
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
