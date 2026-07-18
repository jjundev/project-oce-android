import { buildGenerateBody, buildRepairBody } from "../src/providers/gemini";

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
});
