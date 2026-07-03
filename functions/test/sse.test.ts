import { openSse, writeEvent } from "../src/llm/sse";
import { ErrorCode } from "../src/types/protocol";

function mockRes() {
  const headers: Record<string, string> = {};
  const writes: string[] = [];
  const state = { flushes: 0 };
  return {
    headers,
    writes,
    state,
    set(field: string, value: string) {
      headers[field] = value;
    },
    write(chunk: string) {
      writes.push(chunk);
      return true;
    },
    flush() {
      state.flushes += 1;
    },
  };
}

describe("SSE transport rules (backend-functions.md:58)", () => {
  it("sets event-stream headers and never sets Content-Length", () => {
    const res = mockRes();
    openSse(res);
    expect(res.headers["Content-Type"]).toBe("text/event-stream");
    expect(res.headers["Cache-Control"]).toBe("no-cache");
    expect(res.headers["Connection"]).toBe("keep-alive");
    expect(res.headers["X-Accel-Buffering"]).toBe("no");
    expect(res.headers["Content-Length"]).toBeUndefined();
  });

  it("writes a typed event as event/data lines and flushes each", () => {
    const res = mockRes();
    writeEvent(res, {
      event: "error",
      data: { code: ErrorCode.NOT_IMPLEMENTED },
    });
    expect(res.writes[0]).toBe("event: error\n");
    expect(res.writes[1]).toBe('data: {"code":"NOT_IMPLEMENTED"}\n\n');
    expect(res.writes[1].endsWith("\n\n")).toBe(true);
    expect(res.state.flushes).toBe(1);
  });

  it("emits meta then object then done in order without batching", () => {
    const res = mockRes();
    writeEvent(res, {
      event: "meta",
      data: { sessionId: "s1", remaining: 2 },
    });
    writeEvent(res, {
      event: "object",
      data: { type: "turn", data: { n: 1 } },
    });
    writeEvent(res, { event: "done", data: { status: "ok" } });
    expect(res.writes.filter((w) => w.startsWith("event:"))).toEqual([
      "event: meta\n",
      "event: object\n",
      "event: done\n",
    ]);
    expect(res.state.flushes).toBe(3);
  });
});
