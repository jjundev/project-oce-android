/**
 * SSE transport rules — backend-functions.md:58 (CRITICAL).
 * No compression middleware anywhere, one res.write() + flush per object,
 * text/event-stream, NO Content-Length, X-Accel-Buffering: no. Violating any of
 * these regresses the stream to a single batched response and voids NFR-3.
 */
import { SseEnvelope } from "../types/sse";

/** minimal response surface used by the SSE writer (Express Response satisfies it) */
export interface SseWritable {
  set(field: string, value: string): unknown;
  write(chunk: string): unknown;
  /** Node http.ServerResponse.flushHeaders — sends the header block immediately. */
  flushHeaders?(): void;
  /**
   * Compat hook only. `flush()` exists on the response ONLY when the `compression`
   * middleware monkey-patches it (which we forbid) or under the emulator; on a
   * plain Cloud Functions v2 Express response it is absent, and its absence is a
   * SAFE no-op. Per-object delivery does NOT depend on it — see writeEvent.
   */
  flush?(): void;
}

/** Set streaming headers and commit them immediately. Never sets Content-Length. */
export function openSse(res: SseWritable): void {
  res.set("Content-Type", "text/event-stream");
  res.set("Cache-Control", "no-cache");
  res.set("Connection", "keep-alive");
  res.set("X-Accel-Buffering", "no");
  // Flush the header block so the client sees an open stream before the first
  // object. This is the real "stream is live" signal; flush() below is optional.
  if (typeof res.flushHeaders === "function") {
    res.flushHeaders();
  }
}

/**
 * Serialize one typed envelope and push it. With no Content-Length and no
 * compression, Node sends each `res.write()` as its own chunk immediately, so
 * per-object streaming is guaranteed by chunked transfer encoding. The optional
 * `flush()` is belt-and-suspenders for environments that buffer.
 */
export function writeEvent(res: SseWritable, envelope: SseEnvelope): void {
  res.write(`event: ${envelope.event}\n`);
  res.write(`data: ${JSON.stringify(envelope.data)}\n\n`);
  if (typeof res.flush === "function") {
    res.flush();
  }
}
