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

// A full deep response: conceptualBridge (object) → toneStyle (object) → paraphrasing (ARRAY).
const FULL_JSON =
  '{"conceptualBridge":{"literalTranslation":"직역","explanation":"설명",' +
  '"venn":{"guide":"안내","leftCircle":{"word":"get","items":["얻다"]},' +
  '"rightCircle":{"word":"order","items":["주문하다"]},"intersection":{"items":["받다"]}}},' +
  '"toneStyle":{"defaultLevel":2,"levels":[' +
  '{"level":0,"sentence":"a","sentenceTranslation":"가"},' +
  '{"level":1,"sentence":"b","sentenceTranslation":"나"},' +
  '{"level":2,"sentence":"c","sentenceTranslation":"다"},' +
  '{"level":3,"sentence":"d","sentenceTranslation":"라"},' +
  '{"level":4,"sentence":"e","sentenceTranslation":"마"}]},' +
  '"paraphrasing":[' +
  '{"level":1,"label":"Beginner","sentence":"p1","sentenceTranslation":"번역1"},' +
  '{"level":2,"label":"Intermediate","sentence":"p2","sentenceTranslation":"번역2"},' +
  '{"level":3,"label":"Advanced","sentence":"p3","sentenceTranslation":"번역3"}]}';

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

describe("handle task=feedbackDeep", () => {
  it("400 INVALID_PAYLOAD when sessionId is missing (no stream opened)", async () => {
    const res = recorder();
    const { gate } = fakeGate();
    await handle(req({ task: "feedbackDeep", payload: validPayload }), res, {
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
      req({ task: "feedbackDeep", sessionId: "s1", payload: { koreanPrompt: "안녕" } }),
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
      req({ task: "feedbackDeep", sessionId: "s1", payload: validPayload }),
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
      req({ task: "feedbackDeep", sessionId: "s1", payload: validPayload }),
      res,
      { provider: streamProvider([FULL_JSON]), sessionGate: gate }
    );
    expect(res.statusCode).toBe(403);
    expect(res.jsonBody).toEqual({ code: ErrorCode.SESSION_INVALID });
    expect(res.writes).toHaveLength(0);
  });

  it("streams the three feedbackDeepSection frames → done on success", async () => {
    const res = recorder();
    const { gate } = fakeGate();
    await handle(
      req({ task: "feedbackDeep", sessionId: "s1", payload: validPayload }),
      res,
      { provider: streamProvider([FULL_JSON]), sessionGate: gate }
    );
    const events = parseEvents(res.writes);
    expect(events[0]).toMatchObject({
      event: "object",
      data: { type: "feedbackDeepSection", data: { section: "conceptualBridge" } },
    });
    expect(events[1].data).toMatchObject({
      type: "feedbackDeepSection",
      data: { section: "toneStyle" },
    });
    // paraphrasing (array section) is wrapped under `items`.
    expect(events[2]).toEqual({
      event: "object",
      data: {
        type: "feedbackDeepSection",
        data: {
          section: "paraphrasing",
          items: [
            { level: 1, label: "Beginner", sentence: "p1", sentenceTranslation: "번역1" },
            { level: 2, label: "Intermediate", sentence: "p2", sentenceTranslation: "번역2" },
            { level: 3, label: "Advanced", sentence: "p3", sentenceTranslation: "번역3" },
          ],
        },
      },
    });
    expect(events[events.length - 1]).toEqual({ event: "done", data: { status: "ok" } });
    expect(res.ended).toBe(true);
  });

  it("emits only the completed sections when the stream cuts off mid-object", async () => {
    const res = recorder();
    const { gate } = fakeGate();
    // conceptualBridge completes; toneStyle never closes → only one frame, then done.
    const partial =
      '{"conceptualBridge":{"literalTranslation":"직역","explanation":"설명",' +
      '"venn":{"guide":"안내","leftCircle":{"word":"get","items":["얻다"]},' +
      '"rightCircle":{"word":"order","items":["주문하다"]},"intersection":{"items":["받다"]}}},' +
      '"toneStyle":{"defaultLevel":2,"levels":[';
    await handle(
      req({ task: "feedbackDeep", sessionId: "s1", payload: validPayload }),
      res,
      { provider: streamProvider([partial]), sessionGate: gate }
    );
    const events = parseEvents(res.writes);
    const objectEvents = events.filter((e) => e.event === "object");
    expect(objectEvents).toHaveLength(1);
    expect(objectEvents[0].data).toMatchObject({
      type: "feedbackDeepSection",
      data: { section: "conceptualBridge" },
    });
    expect(events[events.length - 1]).toEqual({ event: "done", data: { status: "ok" } });
  });

  it("on generation failure emits error+done, closes, THEN refunds the slot", async () => {
    const res = recorder();
    const { gate, refunds } = fakeGate();
    await handle(
      req({ task: "feedbackDeep", sessionId: "s1", payload: validPayload }),
      res,
      { provider: streamProvider([], true), sessionGate: gate }
    );
    const events = parseEvents(res.writes);
    expect(events).toContainEqual({ event: "error", data: { code: ErrorCode.INTERNAL } });
    expect(events[events.length - 1]).toEqual({ event: "done", data: { status: "error" } });
    expect(res.ended).toBe(true);
    expect(refunds).toEqual(["s1"]);
  });

  it("falls back to the NOT_IMPLEMENTED stub when the sessionGate is absent", async () => {
    const res = recorder();
    await handle(
      req({ task: "feedbackDeep", sessionId: "s1", payload: validPayload }),
      res,
      { provider: streamProvider([]) }
    );
    const events = parseEvents(res.writes);
    expect(events).toContainEqual({ event: "error", data: { code: ErrorCode.NOT_IMPLEMENTED } });
  });
});
