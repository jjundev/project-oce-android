import { handle, HandlerRequest, HandlerResponse } from "../src/llm/handle";
import {
  CapExceededError,
  SessionGate,
  SessionInvalidError,
} from "../src/llm/session-cap";
import { LlmProvider, RawChunk } from "../src/providers/LlmProvider";
import { ErrorCode } from "../src/types/protocol";

// Offline, deterministic — mock auth like the other pipeline tests.
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

function req(body: unknown): HandlerRequest {
  return { headers: { authorization: "Bearer valid" }, body };
}

function parseEvents(writes: string[]): Array<{ event: string; data: unknown }> {
  const events: Array<{ event: string; data: unknown }> = [];
  for (const block of writes.join("").split("\n\n")) {
    const trimmed = block.trim();
    if (!trimmed) continue;
    const eventLine = trimmed.split("\n").find((l) => l.startsWith("event: "));
    const dataLine = trimmed.split("\n").find((l) => l.startsWith("data: "));
    if (!eventLine || !dataLine) continue;
    events.push({
      event: eventLine.slice("event: ".length),
      data: JSON.parse(dataLine.slice("data: ".length)),
    });
  }
  return events;
}

const FULL_JSON =
  '{"writingScore":{"score":85,"encouragementMessage":"잘했어요!"},' +
  '"grammar":{"correctedSentence":{"segments":[{"text":"ok","type":"normal"}]},"explanation":"좋아요."},' +
  '"naturalExpression":{"segments":[{"text":"x","type":"normal"}],"reason":{"keyword":"k","description":"d"}}}';

/** provider whose generateStream yields the given chunks, optionally throwing at the end. */
function streamProvider(chunks: string[], failAtEnd = false): LlmProvider {
  return {
    async *generateStream(): AsyncIterable<RawChunk> {
      for (const c of chunks) {
        yield { raw: c };
      }
      if (failAtEnd) {
        throw new Error("gemini boom");
      }
    },
    generateOnce: async () => ({}),
    tts: async () => ({ pcmBase64: "", sampleRate: 24000, mimeType: "" }),
  };
}

/** session-cap gate double with spyable refund and configurable reserve throw. */
function fakeGate(opts: { throwCap?: boolean; throwInvalid?: boolean } = {}): {
  gate: SessionGate;
  refunds: string[];
} {
  const refunds: string[] = [];
  const gate: SessionGate = {
    async reserve() {
      if (opts.throwCap) {
        throw new CapExceededError("at cap");
      }
      if (opts.throwInvalid) {
        throw new SessionInvalidError("foreign");
      }
    },
    async refund(sessionId) {
      refunds.push(sessionId);
    },
  };
  return { gate, refunds };
}

const validPayload = {
  koreanPrompt: "커피 주세요",
  userEnglish: "One coffee",
  referenceEnglish: "Can I get a coffee?",
  level: "normal",
};

describe("handle task=feedback", () => {
  it("400 INVALID_PAYLOAD when sessionId is missing (no stream opened)", async () => {
    const res = recorder();
    const { gate } = fakeGate();
    await handle(req({ task: "feedback", payload: validPayload }), res, {
      provider: streamProvider([]),
      sessionGate: gate,
    });
    expect(res.statusCode).toBe(400);
    expect(res.jsonBody).toEqual({ code: ErrorCode.INVALID_PAYLOAD });
    expect(res.writes).toHaveLength(0);
  });

  it("400 INVALID_PAYLOAD when the payload is malformed", async () => {
    const res = recorder();
    const { gate } = fakeGate();
    await handle(
      req({ task: "feedback", sessionId: "s1", payload: { koreanPrompt: "안녕" } }),
      res,
      { provider: streamProvider([]), sessionGate: gate }
    );
    expect(res.statusCode).toBe(400);
    expect(res.jsonBody).toEqual({ code: ErrorCode.INVALID_PAYLOAD });
    expect(res.writes).toHaveLength(0);
  });

  it("429 CAP_EXCEEDED pre-stream when the cap gate rejects (not an SSE frame)", async () => {
    const res = recorder();
    const { gate } = fakeGate({ throwCap: true });
    await handle(
      req({ task: "feedback", sessionId: "s1", payload: validPayload }),
      res,
      { provider: streamProvider([FULL_JSON]), sessionGate: gate }
    );
    expect(res.statusCode).toBe(429);
    expect(res.jsonBody).toEqual({ code: ErrorCode.CAP_EXCEEDED });
    expect(res.writes).toHaveLength(0); // never opened the SSE stream
  });

  it("403 SESSION_INVALID pre-stream for a foreign/expired session", async () => {
    const res = recorder();
    const { gate } = fakeGate({ throwInvalid: true });
    await handle(
      req({ task: "feedback", sessionId: "s1", payload: validPayload }),
      res,
      { provider: streamProvider([FULL_JSON]), sessionGate: gate }
    );
    expect(res.statusCode).toBe(403);
    expect(res.jsonBody).toEqual({ code: ErrorCode.SESSION_INVALID });
    expect(res.writes).toHaveLength(0);
  });

  it("streams the three feedbackSection frames → done on success", async () => {
    const res = recorder();
    const { gate } = fakeGate();
    await handle(
      req({ task: "feedback", sessionId: "s1", payload: validPayload }),
      res,
      { provider: streamProvider([FULL_JSON]), sessionGate: gate }
    );
    const events = parseEvents(res.writes);
    expect(events[0]).toEqual({
      event: "object",
      data: {
        type: "feedbackSection",
        data: { section: "writingScore", score: 85, encouragementMessage: "잘했어요!" },
      },
    });
    expect(events[1].data).toMatchObject({
      type: "feedbackSection",
      data: { section: "grammar" },
    });
    expect(events[2].data).toMatchObject({
      type: "feedbackSection",
      data: { section: "naturalExpression" },
    });
    expect(events[events.length - 1]).toEqual({ event: "done", data: { status: "ok" } });
    expect(res.ended).toBe(true);
  });

  it("on generation failure emits error+done, closes, THEN refunds the slot", async () => {
    const res = recorder();
    const { gate, refunds } = fakeGate();
    await handle(
      req({ task: "feedback", sessionId: "s1", payload: validPayload }),
      res,
      { provider: streamProvider([], true), sessionGate: gate }
    );
    const events = parseEvents(res.writes);
    expect(events).toContainEqual({ event: "error", data: { code: ErrorCode.INTERNAL } });
    expect(events[events.length - 1]).toEqual({ event: "done", data: { status: "error" } });
    expect(res.ended).toBe(true);
    expect(refunds).toEqual(["s1"]); // slot refunded so a failed call doesn't count
  });

  it("falls back to the NOT_IMPLEMENTED stub when the sessionGate is absent", async () => {
    const res = recorder();
    await handle(
      req({ task: "feedback", sessionId: "s1", payload: validPayload }),
      res,
      { provider: streamProvider([]) }
    );
    const events = parseEvents(res.writes);
    expect(events).toContainEqual({ event: "error", data: { code: ErrorCode.NOT_IMPLEMENTED } });
  });
});
