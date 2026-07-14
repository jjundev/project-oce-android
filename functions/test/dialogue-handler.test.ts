import { handle, HandlerRequest, HandlerResponse } from "../src/llm/handle";
import { StartGate, StartResult } from "../src/llm/start-gate";
import { DailyLimitError } from "../src/llm/start-gate";
import { LlmProvider, RawChunk } from "../src/providers/LlmProvider";
import { ErrorCode } from "../src/types/protocol";
import { parseDialoguePayload, InvalidDialoguePayloadError } from "../src/llm/dialogue";

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
  '{"topic":"커피 주문","opponentName":"Barista","opponentGender":"female",' +
  '"opponentRole":"Barista","script":[' +
  '{"ko":"안녕하세요","en":"Hi there","role":"model"},' +
  '{"ko":"아메리카노 주세요","en":"An americano, please","role":"user"}]}';

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

/** start gate double with a spyable refund and a configurable reserve result/throw. */
function fakeGate(opts: {
  reserve?: Partial<StartResult>;
  throwLimit?: boolean;
}): { gate: StartGate; refunds: Array<[string, string]> } {
  const refunds: Array<[string, string]> = [];
  const gate: StartGate = {
    async reserve() {
      if (opts.throwLimit) {
        throw new DailyLimitError("at limit");
      }
      return {
        sessionId: "sess-1",
        remaining: 2,
        deduped: false,
        usageKey: "20231114",
        ...opts.reserve,
      };
    },
    async refund(idempotencyKey, usageKey) {
      refunds.push([idempotencyKey, usageKey]);
    },
  };
  return { gate, refunds };
}

describe("handle task=dialogue", () => {
  it("400 INVALID_PAYLOAD when idempotencyKey is missing (no stream opened)", async () => {
    const res = recorder();
    const { gate } = fakeGate({});
    await handle(
      req({ task: "dialogue", payload: { level: "easy", topic: "t", length: 10, firstSession: false } }),
      res,
      { startGate: gate, provider: streamProvider([]) }
    );
    expect(res.statusCode).toBe(400);
    expect(res.jsonBody).toEqual({ code: ErrorCode.INVALID_PAYLOAD });
    expect(res.writes).toHaveLength(0);
  });

  it("429 DAILY_LIMIT_EXCEEDED pre-stream when the gate rejects", async () => {
    const res = recorder();
    const { gate } = fakeGate({ throwLimit: true });
    await handle(
      req({
        task: "dialogue",
        idempotencyKey: "k1",
        payload: { level: "easy", topic: "t", length: 10, firstSession: false },
      }),
      res,
      { startGate: gate, provider: streamProvider([]) }
    );
    expect(res.statusCode).toBe(429);
    expect(res.jsonBody).toEqual({ code: ErrorCode.DAILY_LIMIT_EXCEEDED, remaining: 0 });
    expect(res.writes).toHaveLength(0); // never opened the SSE stream
  });

  it("streams meta → dialogueMeta → turns → done on success", async () => {
    const res = recorder();
    const { gate } = fakeGate({});
    await handle(
      req({
        task: "dialogue",
        idempotencyKey: "k1",
        payload: { level: "normal", topic: "커피", length: 10, firstSession: false },
      }),
      res,
      { startGate: gate, provider: streamProvider([FULL_JSON]) }
    );
    const events = parseEvents(res.writes);
    expect(events[0]).toEqual({ event: "meta", data: { sessionId: "sess-1", remaining: 2 } });
    expect(events[1]).toEqual({
      event: "object",
      data: {
        type: "dialogueMeta",
        data: { topic: "커피 주문", opponentName: "Barista", opponentGender: "female", opponentRole: "Barista" },
      },
    });
    expect(events[2]).toEqual({
      event: "object",
      data: { type: "turn", data: { ko: "안녕하세요", en: "Hi there", role: "model" } },
    });
    expect(events[3].data).toMatchObject({ type: "turn", data: { role: "user" } });
    expect(events[events.length - 1]).toEqual({ event: "done", data: { status: "ok" } });
    expect(res.ended).toBe(true);
  });

  it("on generation failure emits error+done, closes, THEN refunds a fresh start", async () => {
    const res = recorder();
    const { gate, refunds } = fakeGate({ reserve: { deduped: false, usageKey: "20231114" } });
    await handle(
      req({
        task: "dialogue",
        idempotencyKey: "k1",
        payload: { level: "easy", topic: "t", length: 10, firstSession: false },
      }),
      res,
      { startGate: gate, provider: streamProvider([], true) }
    );
    const events = parseEvents(res.writes);
    expect(events[0].event).toBe("meta");
    expect(events).toContainEqual({ event: "error", data: { code: ErrorCode.INTERNAL } });
    expect(events[events.length - 1]).toEqual({ event: "done", data: { status: "error" } });
    expect(res.ended).toBe(true);
    expect(refunds).toEqual([["k1", "20231114"]]); // fresh start → refunded
  });

  it("does NOT refund a deduped replay whose generation fails", async () => {
    const res = recorder();
    const { gate, refunds } = fakeGate({ reserve: { deduped: true } });
    await handle(
      req({
        task: "dialogue",
        idempotencyKey: "k1",
        payload: { level: "easy", topic: "t", length: 10, firstSession: false },
      }),
      res,
      { startGate: gate, provider: streamProvider([], true) }
    );
    expect(refunds).toHaveLength(0); // replay slot belongs to the original attempt
  });

  it("falls back to the NOT_IMPLEMENTED stub when startGate is absent", async () => {
    const res = recorder();
    await handle(
      req({ task: "dialogue", idempotencyKey: "k1", payload: {} }),
      res,
      { provider: streamProvider([]) }
    );
    const events = parseEvents(res.writes);
    expect(events).toContainEqual({ event: "error", data: { code: ErrorCode.NOT_IMPLEMENTED } });
  });
});

describe("parseDialoguePayload 5-tier + even length", () => {
  it("accepts the two new level tokens", () => {
    expect(parseDialoguePayload({ level: "starter", topic: "t", length: 6 }).level).toBe("starter");
    expect(parseDialoguePayload({ level: "expert", topic: "t", length: 20 }).level).toBe("expert");
  });
  it("rejects odd or out-of-range length for non-first sessions", () => {
    expect(() => parseDialoguePayload({ level: "normal", topic: "t", length: 5 }))
      .toThrow(InvalidDialoguePayloadError);
    expect(() => parseDialoguePayload({ level: "normal", topic: "t", length: 22 }))
      .toThrow(InvalidDialoguePayloadError);
  });
  it("still coerces first session to easy/5 regardless of input", () => {
    const p = parseDialoguePayload({ level: "expert", topic: "t", length: 20, firstSession: true });
    expect(p).toEqual({ level: "easy", topic: "t", length: 5, firstSession: true });
  });
});
