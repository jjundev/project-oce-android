import { handle, HandlerRequest, HandlerResponse } from "../src/llm/handle";
import { LlmProvider, RawJson } from "../src/providers/LlmProvider";
import {
  SpeakingAnalyzeError,
  buildAnalysisBody,
  parseAnalysisResponse,
} from "../src/providers/gemini";
import { parseSpeakingPayload, analyzeSpeaking } from "../src/llm/speaking";
import {
  CapExceededError,
  SessionGate,
  SessionInvalidError,
} from "../src/llm/session-cap";
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

/** provider stub whose generateOnce returns a fixed object (or throws). */
function fakeProvider(
  impl?: (payload: unknown) => Promise<RawJson>
): { provider: LlmProvider; calls: unknown[] } {
  const calls: unknown[] = [];
  const provider: LlmProvider = {
    generateStream() {
      throw new Error("unused");
    },
    async generateOnce(r) {
      calls.push(r.payload);
      if (impl) {
        return impl(r.payload);
      }
      return { transcript: "hello there", feedbackMessage: "자연스럽게 말했어요" };
    },
    tts() {
      throw new Error("unused");
    },
  };
  return { provider, calls };
}

/** cap-gate stub that records reserve/refund and can be told to throw on reserve. */
function fakeGate(reserveError?: Error): {
  gate: SessionGate;
  reserved: string[];
  refunded: string[];
} {
  const reserved: string[] = [];
  const refunded: string[] = [];
  const gate: SessionGate = {
    async reserve(_uid, sessionId) {
      if (reserveError) {
        throw reserveError;
      }
      reserved.push(sessionId);
    },
    async refund(sessionId) {
      refunded.push(sessionId);
    },
  };
  return { gate, reserved, refunded };
}

const okBody = {
  task: "speaking",
  sessionId: "s1",
  payload: { audioBase64: "QUJD" },
};

describe("speaking handler pipeline", () => {
  it("reserves a slot then returns transcript + feedbackMessage on success", async () => {
    const res = recorder();
    const { provider } = fakeProvider();
    const { gate, reserved, refunded } = fakeGate();
    await handle(req({ authorization: "Bearer valid" }, okBody), res, {
      provider,
      sessionGate: gate,
    });
    expect(res.statusCode).toBe(200);
    expect(res.jsonBody).toEqual({
      transcript: "hello there",
      feedbackMessage: "자연스럽게 말했어요",
    });
    expect(reserved).toEqual(["s1"]); // slot reserved
    expect(refunded).toEqual([]); // success → not refunded
  });

  it("counts an empty/unintelligible clip as a 200 (transcript \"\"), no refund", async () => {
    const res = recorder();
    const { provider } = fakeProvider(async () => ({
      transcript: "",
      feedbackMessage: "잘 안 들렸어요. 다시 한 번 해볼까요?",
    }));
    const { gate, reserved, refunded } = fakeGate();
    await handle(req({ authorization: "Bearer valid" }, okBody), res, {
      provider,
      sessionGate: gate,
    });
    expect(res.statusCode).toBe(200);
    expect((res.jsonBody as { transcript: string }).transcript).toBe("");
    expect(reserved).toEqual(["s1"]);
    expect(refunded).toEqual([]); // an empty transcript is a success, still counts
  });

  it("rejects a missing sessionId with 400 INVALID_PAYLOAD before reserving", async () => {
    const res = recorder();
    const { provider, calls } = fakeProvider();
    const { gate, reserved } = fakeGate();
    await handle(
      req(
        { authorization: "Bearer valid" },
        { task: "speaking", payload: { audioBase64: "QUJD" } }
      ),
      res,
      { provider, sessionGate: gate }
    );
    expect(res.statusCode).toBe(400);
    expect(res.jsonBody).toEqual({ code: ErrorCode.INVALID_PAYLOAD });
    expect(reserved).toEqual([]);
    expect(calls).toHaveLength(0); // never called Gemini
  });

  it("rejects a missing audioBase64 with 400 INVALID_PAYLOAD", async () => {
    const res = recorder();
    const { provider, calls } = fakeProvider();
    const { gate, reserved } = fakeGate();
    await handle(
      req(
        { authorization: "Bearer valid" },
        { task: "speaking", sessionId: "s1", payload: {} }
      ),
      res,
      { provider, sessionGate: gate }
    );
    expect(res.statusCode).toBe(400);
    expect(res.jsonBody).toEqual({ code: ErrorCode.INVALID_PAYLOAD });
    expect(reserved).toEqual([]);
    expect(calls).toHaveLength(0);
  });

  it("maps a full cap to 429 CAP_EXCEEDED and never calls Gemini", async () => {
    const res = recorder();
    const { provider, calls } = fakeProvider();
    const { gate } = fakeGate(new CapExceededError("at cap"));
    await handle(req({ authorization: "Bearer valid" }, okBody), res, {
      provider,
      sessionGate: gate,
    });
    expect(res.statusCode).toBe(429);
    expect(res.jsonBody).toEqual({ code: ErrorCode.CAP_EXCEEDED });
    expect(calls).toHaveLength(0);
  });

  it("maps an invalid/expired session to 403 SESSION_INVALID", async () => {
    const res = recorder();
    const { provider } = fakeProvider();
    const { gate } = fakeGate(new SessionInvalidError("expired"));
    await handle(req({ authorization: "Bearer valid" }, okBody), res, {
      provider,
      sessionGate: gate,
    });
    expect(res.statusCode).toBe(403);
    expect(res.jsonBody).toEqual({ code: ErrorCode.SESSION_INVALID });
  });

  it("maps an analysis failure to 502 and refunds the reserved slot", async () => {
    const res = recorder();
    const { provider } = fakeProvider(async () => {
      throw new SpeakingAnalyzeError("boom");
    });
    const { gate, reserved, refunded } = fakeGate();
    await handle(req({ authorization: "Bearer valid" }, okBody), res, {
      provider,
      sessionGate: gate,
    });
    expect(res.statusCode).toBe(502);
    expect(res.jsonBody).toEqual({ code: ErrorCode.SPEAKING_ANALYZE_FAILED });
    expect(reserved).toEqual(["s1"]);
    expect(refunded).toEqual(["s1"]); // terminal failure → slot refunded (A1)
  });

  it("falls back to the 501 stub when no sessionGate is injected", async () => {
    const res = recorder();
    const { provider } = fakeProvider();
    await handle(req({ authorization: "Bearer valid" }, okBody), res, {
      provider,
    });
    expect(res.statusCode).toBe(501);
    expect(res.jsonBody).toEqual({ code: ErrorCode.NOT_IMPLEMENTED });
  });
});

describe("speaking payload parsing", () => {
  it("requires non-empty audioBase64", () => {
    expect(() => parseSpeakingPayload({})).toThrow();
    expect(() => parseSpeakingPayload({ audioBase64: "  " })).toThrow();
    expect(parseSpeakingPayload({ audioBase64: " QUJD " }).audioBase64).toBe(
      "QUJD"
    );
  });
});

describe("analyzeSpeaking shape narrowing", () => {
  it("passes through a valid {transcript, feedbackMessage}", async () => {
    const { provider } = fakeProvider();
    const out = await analyzeSpeaking({ audioBase64: "QUJD" }, provider);
    expect(out).toEqual({
      transcript: "hello there",
      feedbackMessage: "자연스럽게 말했어요",
    });
  });

  it("throws SpeakingAnalyzeError when a key is missing/non-string", async () => {
    const { provider } = fakeProvider(async () => ({ transcript: "x" }));
    await expect(
      analyzeSpeaking({ audioBase64: "QUJD" }, provider)
    ).rejects.toBeInstanceOf(SpeakingAnalyzeError);
  });
});

describe("gemini speaking request/response parsing", () => {
  it("builds an inline audio part + structured responseSchema", () => {
    const body = buildAnalysisBody("QUJD") as {
      contents: Array<{ parts: Array<Record<string, unknown>> }>;
      generationConfig: {
        responseMimeType: string;
        responseSchema: { required: string[] };
      };
    };
    const parts = body.contents[0].parts;
    expect(parts[0]).toEqual({
      inlineData: { mimeType: "audio/wav", data: "QUJD" },
    });
    expect(typeof (parts[1] as { text: string }).text).toBe("string");
    expect(body.generationConfig.responseMimeType).toBe("application/json");
    expect(body.generationConfig.responseSchema.required).toEqual([
      "transcript",
      "feedbackMessage",
    ]);
  });

  it("extracts the JSON object from the first candidate text part", () => {
    const responseBody = JSON.stringify({
      candidates: [
        {
          content: {
            parts: [
              {
                text: JSON.stringify({
                  transcript: "hi",
                  feedbackMessage: "좋아요",
                }),
              },
            ],
          },
        },
      ],
    });
    expect(parseAnalysisResponse(responseBody)).toEqual({
      transcript: "hi",
      feedbackMessage: "좋아요",
    });
  });

  it("throws on empty / shapeless / non-JSON-text responses", () => {
    expect(() => parseAnalysisResponse("")).toThrow();
    expect(() => parseAnalysisResponse("{}")).toThrow();
    expect(() => parseAnalysisResponse('{"candidates":[]}')).toThrow();
    const notJsonText = JSON.stringify({
      candidates: [{ content: { parts: [{ text: "not json" }] } }],
    });
    expect(() => parseAnalysisResponse(notJsonText)).toThrow();
  });
});
