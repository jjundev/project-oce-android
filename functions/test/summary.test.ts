import { handle, HandlerRequest, HandlerResponse } from "../src/llm/handle";
import {
  GenerateRequest,
  LlmProvider,
  RawJson,
} from "../src/providers/LlmProvider";
import { parseSummaryPayload, resolveTotalScore } from "../src/llm/summary";
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

function req(headers: Record<string, string>, body: unknown): HandlerRequest {
  return { headers, body };
}

/** parse the recorder's raw SSE writes into {event, data} objects. */
function parseEvents(writes: string[]): Array<{ event: string; data: unknown }> {
  const joined = writes.join("");
  const events: Array<{ event: string; data: unknown }> = [];
  for (const block of joined.split("\n\n")) {
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

/** provider stub: records generateOnce requests; fails or shapes results per subtask. */
function summaryProvider(opts?: {
  fail?: Set<string>;
  results?: Record<string, RawJson>;
}): { provider: LlmProvider; calls: GenerateRequest[] } {
  const calls: GenerateRequest[] = [];
  const provider: LlmProvider = {
    generateStream() {
      throw new Error("unused");
    },
    async generateOnce(request: GenerateRequest): Promise<RawJson> {
      calls.push(request);
      if (opts?.fail?.has(request.task)) {
        throw new Error("boom");
      }
      if (opts?.results?.[request.task]) {
        return opts.results[request.task];
      }
      if (request.task === "summary.coaching") {
        return { futureSelfFeedback: { positive: "잘했어요", toImprove: "" } };
      }
      return { items: [{ note: request.task }] };
    },
    tts() {
      throw new Error("unused");
    },
  };
  return { provider, calls };
}

const FULL_PAYLOAD = {
  expressionCandidates: [
    { type: "natural", koreanPrompt: "안녕", before: "hi", after: "hello", explanation: "x" },
  ],
  words: ["nuance"],
  sentences: ["It has a subtle nuance."],
  userOriginalSentences: ["I like it."],
  turns: [{ koreanPrompt: "안녕", before: "hi", after: "hello", score: 80 }],
  totalScore: 90,
};

describe("summary orchestration (SSE)", () => {
  it("emits a card per section (kind-distinct) then done with all sections ok", async () => {
    const res = recorder();
    const { provider } = summaryProvider();
    await handle(
      req({ authorization: "Bearer valid" }, { task: "summary", payload: FULL_PAYLOAD }),
      res,
      { provider }
    );

    expect(res.headers["Content-Type"]).toBe("text/event-stream");
    const events = parseEvents(res.writes);
    const cards = events.filter((e) => e.event === "object");
    const kinds = cards.map((c) => (c.data as { data: { kind: string } }).data.kind);
    expect(kinds.sort()).toEqual(["coaching", "expression", "word"]);

    const done = events.find((e) => e.event === "done");
    expect(done?.data).toEqual({
      status: "ok",
      sections: { expressions: "ok", words: "ok", coaching: "ok" },
    });
    expect(res.ended).toBe(true);
    expect(res.statusCode).toBeUndefined(); // SSE path sets no status
  });

  it("on a partial failure omits that card and marks only it failed in done", async () => {
    const res = recorder();
    const { provider } = summaryProvider({ fail: new Set(["summary.words"]) });
    await handle(
      req({ authorization: "Bearer valid" }, { task: "summary", payload: FULL_PAYLOAD }),
      res,
      { provider }
    );

    const events = parseEvents(res.writes);
    const kinds = events
      .filter((e) => e.event === "object")
      .map((c) => (c.data as { data: { kind: string } }).data.kind);
    expect(kinds).not.toContain("word"); // failed card not emitted
    expect(kinds.sort()).toEqual(["coaching", "expression"]);

    const done = events.find((e) => e.event === "done");
    expect(done?.data).toEqual({
      status: "ok",
      sections: { expressions: "ok", words: "failed", coaching: "ok" },
    });
  });

  it("an empty items result is ok (still emits the card)", async () => {
    const res = recorder();
    const { provider } = summaryProvider({
      results: { "summary.expressions": { items: [] } },
    });
    await handle(
      req({ authorization: "Bearer valid" }, { task: "summary", payload: FULL_PAYLOAD }),
      res,
      { provider }
    );
    const events = parseEvents(res.writes);
    const exprCard = events
      .filter((e) => e.event === "object")
      .map((c) => c.data as { data: { kind: string; items?: unknown[] } })
      .find((d) => d.data.kind === "expression");
    expect(exprCard?.data.items).toEqual([]);
    const done = events.find((e) => e.event === "done");
    expect((done?.data as { sections: Record<string, string> }).sections.expressions).toBe("ok");
  });

  it("retry with a sections filter runs only the requested subset", async () => {
    const res = recorder();
    const { provider, calls } = summaryProvider();
    await handle(
      req(
        { authorization: "Bearer valid" },
        { task: "summary", payload: { ...FULL_PAYLOAD, sections: ["coaching"] } }
      ),
      res,
      { provider }
    );
    expect(calls.map((c) => c.task)).toEqual(["summary.coaching"]);
    const done = parseEvents(res.writes).find((e) => e.event === "done");
    expect(done?.data).toEqual({ status: "ok", sections: { coaching: "ok" } });
  });

  it("merges totalScore into expressions + coaching slices but not words", async () => {
    const res = recorder();
    const { provider, calls } = summaryProvider();
    await handle(
      req({ authorization: "Bearer valid" }, { task: "summary", payload: FULL_PAYLOAD }),
      res,
      { provider }
    );
    const byTask = Object.fromEntries(calls.map((c) => [c.task, c.payload as Record<string, unknown>]));
    expect(byTask["summary.expressions"].totalScore).toBe(90);
    expect(byTask["summary.coaching"].totalScore).toBe(90);
    expect(byTask["summary.words"]).not.toHaveProperty("totalScore");
  });

  it("resolves the model to modelFor('summary') via req.modelId (not the subtask)", async () => {
    const res = recorder();
    const { provider, calls } = summaryProvider();
    await handle(
      req({ authorization: "Bearer valid" }, { task: "summary", payload: FULL_PAYLOAD }),
      res,
      { provider }
    );
    // every sub-call carries a defined, identical model id (never undefined)
    const models = new Set(calls.map((c) => c.modelId));
    expect(models.size).toBe(1);
    expect([...models][0]).toBeTruthy();
  });

  it("rejects an empty sections array with 400 before opening the stream", async () => {
    const res = recorder();
    const { provider } = summaryProvider();
    await handle(
      req(
        { authorization: "Bearer valid" },
        { task: "summary", payload: { ...FULL_PAYLOAD, sections: [] } }
      ),
      res,
      { provider }
    );
    expect(res.statusCode).toBe(400);
    expect(res.jsonBody).toEqual({ code: ErrorCode.INVALID_PAYLOAD });
    expect(res.writes).toHaveLength(0); // stream never opened
  });

  it("rejects an unknown section with 400", async () => {
    const res = recorder();
    const { provider } = summaryProvider();
    await handle(
      req(
        { authorization: "Bearer valid" },
        { task: "summary", payload: { ...FULL_PAYLOAD, sections: ["bogus"] } }
      ),
      res,
      { provider }
    );
    expect(res.statusCode).toBe(400);
    expect(res.jsonBody).toEqual({ code: ErrorCode.INVALID_PAYLOAD });
  });

  it("falls back to the SSE stub when no provider is injected", async () => {
    const res = recorder();
    await handle(
      req({ authorization: "Bearer valid" }, { task: "summary", payload: FULL_PAYLOAD }),
      res
    );
    const events = parseEvents(res.writes);
    expect(events.find((e) => e.event === "error")?.data).toEqual({
      code: ErrorCode.NOT_IMPLEMENTED,
    });
    expect(events.find((e) => e.event === "done")?.data).toEqual({ status: "error" });
  });
});

describe("summary payload parsing + helpers", () => {
  it("dedupes and validates the sections filter; drops unknown data types", () => {
    const parsed = parseSummaryPayload({
      words: ["a", 1, "b"],
      sections: ["words", "words"],
    });
    expect(parsed.words).toEqual(["a", "b"]);
    expect(parsed.sections).toEqual(["words"]);
  });

  it("averages turn scores when totalScore is absent", () => {
    expect(
      resolveTotalScore({
        expressionCandidates: [],
        words: [],
        sentences: [],
        userOriginalSentences: [],
        turns: [{ score: 80 }, { score: 90 }, { score: 100 }],
      })
    ).toBe(90);
  });

  it("prefers an explicit totalScore over the turn average", () => {
    expect(
      resolveTotalScore({
        expressionCandidates: [],
        words: [],
        sentences: [],
        userOriginalSentences: [],
        turns: [{ score: 10 }],
        totalScore: 55,
      })
    ).toBe(55);
  });
});
