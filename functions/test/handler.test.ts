import { handle, HandlerRequest, HandlerResponse } from "../src/llm/handle";
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

describe("handle() pipeline", () => {
  it("rejects a missing/invalid token with 401", async () => {
    const res = recorder();
    await handle(req({}, { task: "dialogue" }), res);
    expect(res.statusCode).toBe(401);
    expect(res.jsonBody).toEqual({ code: ErrorCode.UNAUTHENTICATED });
  });

  it("rejects an unknown task with 400", async () => {
    const res = recorder();
    await handle(req({ authorization: "Bearer valid" }, { task: "bogus" }), res);
    expect(res.statusCode).toBe(400);
    expect(res.jsonBody).toEqual({ code: ErrorCode.UNKNOWN_TASK });
  });

  it("rejects a missing body with 400", async () => {
    const res = recorder();
    await handle(req({ authorization: "Bearer valid" }, undefined), res);
    expect(res.statusCode).toBe(400);
    expect(res.jsonBody).toEqual({ code: ErrorCode.UNKNOWN_TASK });
  });

  it("stubs an SSE task with error+done events over the stream", async () => {
    const res = recorder();
    await handle(
      req({ authorization: "Bearer valid" }, { task: "dialogue" }),
      res
    );
    expect(res.headers["Content-Type"]).toBe("text/event-stream");
    expect(res.writes).toContain("event: error\n");
    expect(res.writes).toContain("event: done\n");
    expect(res.ended).toBe(true);
    expect(res.statusCode).toBeUndefined(); // SSE path does not set a status code
  });

  it("stubs a JSON task with 501 and a typed body", async () => {
    const res = recorder();
    await handle(
      req({ authorization: "Bearer valid" }, { task: "speaking" }),
      res
    );
    expect(res.statusCode).toBe(501);
    expect(res.jsonBody).toEqual({ code: ErrorCode.NOT_IMPLEMENTED });
  });

  it("maps a mid-dispatch throw to 500 INTERNAL when nothing was committed", async () => {
    const res = recorder();
    // Force openSse to throw before any header/byte is committed.
    res.set = () => {
      throw new Error("boom");
    };
    await handle(
      req({ authorization: "Bearer valid" }, { task: "dialogue" }),
      res
    );
    expect(res.statusCode).toBe(500);
    expect(res.jsonBody).toEqual({ code: ErrorCode.INTERNAL });
  });
});
