import { handle, HandlerRequest, HandlerResponse } from "../src/llm/handle";
import { LlmProvider, TtsResult } from "../src/providers/LlmProvider";
import { TtsSynthError } from "../src/providers/gemini";
import {
  parseTtsResponse,
  parseSampleRate,
  clampRate,
} from "../src/providers/gemini";
import { resolveVoiceName, parseTtsPayload } from "../src/llm/tts";
import { ErrorCode } from "../src/types/protocol";

// Mock auth so the pipeline test is offline and deterministic.
jest.mock("../src/llm/auth", () => ({
  authenticate: async (authHeader: string | undefined) => {
    if (authHeader === "Bearer valid") {
      return { uid: "u1" };
    }
    throw new Error("unauthenticated");
  },
}));

interface Recorder extends HandlerResponse {
  statusCode?: number;
  jsonBody?: unknown;
  writes: string[];
  ended: boolean;
  headers: Record<string, string>;
}

function recorder(): Recorder {
  const res: Recorder = {
    writes: [],
    ended: false,
    headers: {},
    headersSent: false,
    status(code: number) {
      res.statusCode = code;
      return res;
    },
    json(body: unknown) {
      res.jsonBody = body;
      res.headersSent = true;
      return res;
    },
    set(field: string, value: string) {
      res.headers[field] = value;
      return res;
    },
    write(chunk: string) {
      res.writes.push(chunk);
      res.headersSent = true;
      return true;
    },
    end() {
      res.ended = true;
      return res;
    },
    flush() {
      /* no-op */
    },
  };
  return res;
}

function req(headers: Record<string, string>, body: unknown): HandlerRequest {
  return { headers, body };
}

/** provider stub that records the args it was called with and returns a fixed clip. */
function fakeProvider(
  impl?: (text: string, voice: string, rate: number) => Promise<TtsResult>
): { provider: LlmProvider; calls: Array<[string, string, number]> } {
  const calls: Array<[string, string, number]> = [];
  const provider: LlmProvider = {
    generateStream() {
      throw new Error("unused");
    },
    generateOnce() {
      throw new Error("unused");
    },
    async tts(text: string, voice: string, rate: number) {
      calls.push([text, voice, rate]);
      if (impl) {
        return impl(text, voice, rate);
      }
      return { pcmBase64: "AAAA", sampleRate: 24000, mimeType: "audio/L16;rate=24000" };
    },
  };
  return { provider, calls };
}

describe("tts handler pipeline", () => {
  it("synthesizes and returns base64 PCM + sampleRate + mimeType", async () => {
    const res = recorder();
    const { provider } = fakeProvider();
    await handle(
      req(
        { authorization: "Bearer valid" },
        { task: "tts", payload: { text: "Hello", gender: "female", speechRate: 1.0 } }
      ),
      res,
      { provider }
    );
    expect(res.statusCode).toBe(200);
    expect(res.jsonBody).toEqual({
      pcmBase64: "AAAA",
      sampleRate: 24000,
      mimeType: "audio/L16;rate=24000",
    });
  });

  it("maps gender male→Puck and passes the resolved voice to the provider", async () => {
    const res = recorder();
    const { provider, calls } = fakeProvider();
    await handle(
      req(
        { authorization: "Bearer valid" },
        { task: "tts", payload: { text: "Hi", gender: "male", speechRate: 0.9 } }
      ),
      res,
      { provider }
    );
    expect(calls).toEqual([["Hi", "Puck", 0.9]]);
  });

  it("defaults an absent gender to Kore", async () => {
    const res = recorder();
    const { provider, calls } = fakeProvider();
    await handle(
      req({ authorization: "Bearer valid" }, { task: "tts", payload: { text: "Hi" } }),
      res,
      { provider }
    );
    expect(calls[0][1]).toBe("Kore");
    expect(calls[0][2]).toBe(1.0); // default rate
  });

  it("rejects an empty-text payload with 400 INVALID_PAYLOAD", async () => {
    const res = recorder();
    const { provider, calls } = fakeProvider();
    await handle(
      req(
        { authorization: "Bearer valid" },
        { task: "tts", payload: { text: "   " } }
      ),
      res,
      { provider }
    );
    expect(res.statusCode).toBe(400);
    expect(res.jsonBody).toEqual({ code: ErrorCode.INVALID_PAYLOAD });
    expect(calls).toHaveLength(0); // provider never called
  });

  it("maps a synthesis failure to 502 TTS_SYNTH_FAILED", async () => {
    const res = recorder();
    const { provider } = fakeProvider(async () => {
      throw new TtsSynthError("boom");
    });
    await handle(
      req({ authorization: "Bearer valid" }, { task: "tts", payload: { text: "Hi" } }),
      res,
      { provider }
    );
    expect(res.statusCode).toBe(502);
    expect(res.jsonBody).toEqual({ code: ErrorCode.TTS_SYNTH_FAILED });
  });

  it("falls back to the 501 stub when no provider is injected", async () => {
    const res = recorder();
    await handle(
      req({ authorization: "Bearer valid" }, { task: "tts", payload: { text: "Hi" } }),
      res
    );
    expect(res.statusCode).toBe(501);
    expect(res.jsonBody).toEqual({ code: ErrorCode.NOT_IMPLEMENTED });
  });
});

describe("tts payload parsing", () => {
  it("resolves voice by gender", () => {
    expect(resolveVoiceName("male")).toBe("Puck");
    expect(resolveVoiceName("MALE")).toBe("Puck");
    expect(resolveVoiceName("female")).toBe("Kore");
    expect(resolveVoiceName(undefined)).toBe("Kore");
  });

  it("requires non-empty text", () => {
    expect(() => parseTtsPayload({ text: "" })).toThrow();
    expect(() => parseTtsPayload({})).toThrow();
    expect(parseTtsPayload({ text: " hi " }).text).toBe("hi");
  });

  it("defaults gender/rate and drops invalid gender", () => {
    const p = parseTtsPayload({ text: "x", gender: "nonbinary" as never });
    expect(p.gender).toBeUndefined();
    expect(p.speechRate).toBe(1.0);
  });
});

describe("gemini tts response parsing", () => {
  const audioBody = JSON.stringify({
    candidates: [
      {
        content: {
          parts: [
            { inlineData: { data: "QUJD", mimeType: "audio/L16;rate=24000" } },
          ],
        },
      },
    ],
  });

  it("extracts base64 data + sample rate from the first inline audio part", () => {
    const result = parseTtsResponse(audioBody);
    expect(result.pcmBase64).toBe("QUJD");
    expect(result.sampleRate).toBe(24000);
    expect(result.mimeType).toBe("audio/L16;rate=24000");
  });

  it("throws on an empty / shapeless response", () => {
    expect(() => parseTtsResponse("")).toThrow();
    expect(() => parseTtsResponse("{}")).toThrow();
    expect(() => parseTtsResponse('{"candidates":[]}')).toThrow();
  });

  it("parses sample rate, defaulting to 24000", () => {
    expect(parseSampleRate("audio/L16;rate=16000")).toBe(16000);
    expect(parseSampleRate("audio/L16")).toBe(24000);
    expect(parseSampleRate("")).toBe(24000);
  });

  it("clamps rate to 0.5–1.5", () => {
    expect(clampRate(0.1)).toBe(0.5);
    expect(clampRate(2.0)).toBe(1.5);
    expect(clampRate(1.0)).toBe(1.0);
    expect(clampRate(Number.NaN)).toBe(1.0);
  });
});
