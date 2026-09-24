import {
  MAX_SPEAKING_PAYLOAD_CHARS,
  MAX_TEXT_PAYLOAD_CHARS,
  isUuid,
  payloadWithinLimit,
} from "../src/llm/request-guards";

describe("isUuid", () => {
  it("accepts lowercase and uppercase UUIDs", () => {
    expect(isUuid("3f2b8c1e-9d4a-4f6b-8a2c-1e5d7f9b0a3c")).toBe(true);
    expect(isUuid("3F2B8C1E-9D4A-4F6B-8A2C-1E5D7F9B0A3C")).toBe(true);
  });

  it("rejects empty, path-like, reserved and oversized ids", () => {
    expect(isUuid("")).toBe(false);
    expect(isUuid("a/b")).toBe(false);
    expect(isUuid("__x__")).toBe(false);
    expect(isUuid("k1")).toBe(false);
    expect(isUuid("x".repeat(2000))).toBe(false);
  });
});

describe("payloadWithinLimit", () => {
  it("allows a normal text payload and rejects one over the text cap", () => {
    expect(payloadWithinLimit("summary", { words: ["a"] })).toBe(true);
    expect(
      payloadWithinLimit("summary", { blob: "x".repeat(MAX_TEXT_PAYLOAD_CHARS) })
    ).toBe(false);
  });

  it("gives speaking the larger audio budget only", () => {
    const audio = { audioBase64: "A".repeat(900_000) }; // ≈ 20s 16kHz mono WAV in base64
    expect(payloadWithinLimit("speaking", audio)).toBe(true);
    expect(payloadWithinLimit("feedback", audio)).toBe(false);
    expect(
      payloadWithinLimit("speaking", { audioBase64: "A".repeat(MAX_SPEAKING_PAYLOAD_CHARS) })
    ).toBe(false);
  });

  it("treats an absent payload as empty", () => {
    expect(payloadWithinLimit("tts", undefined)).toBe(true);
  });
});
