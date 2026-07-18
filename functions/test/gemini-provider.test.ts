import { GeminiProvider, StreamFetchFn } from "../src/providers/gemini";
import { GenerateRequest } from "../src/providers/LlmProvider";

/**
 * Fix 1 (config/generation.ts) made every task behaviour-neutral — no task's
 * `GENERATION_TUNING` entry carries a temperature — but that was only ever verified
 * transitively through `tuningFor` unit tests (generation-config.test.ts). This file
 * pins the SAME property at the actual call site: `GeminiProvider.generateStream` builds
 * its request body via `buildGenerateBody(..., tuningFor(req.task))`, so a stub transport
 * lets us capture the serialised body and assert directly on what would be sent to Vertex.
 */

/** an async-iterable stream body that yields no SSE frames. */
async function* emptySseBody(): AsyncIterable<Uint8Array> {
  // yields nothing — this stub only cares about capturing the outgoing request body.
}

/** stub streaming transport that records every request body it was called with. */
function captureStreamFetch(): { fetchFn: StreamFetchFn; bodies: string[] } {
  const bodies: string[] = [];
  const fetchFn: StreamFetchFn = async (_url, init) => {
    bodies.push(init.body);
    // no SSE frames — an empty async-iterable body is enough to let generateStream
    // connect, drain, and return; we only care about the outgoing request body.
    return { ok: true, status: 200, body: emptySseBody() };
  };
  return { fetchFn, bodies };
}

async function drive(task: string, fetchFn: StreamFetchFn): Promise<void> {
  const provider = new GeminiProvider({
    modelFor: () => "test-model",
    apiKey: () => "test-key",
    streamFetchFn: fetchFn,
  });
  const req: GenerateRequest = { task, modelId: "test-model", payload: {} };
  // drain the generator — generateStream does all its work (including the fetch call)
  // as the caller iterates it.
  for await (const _chunk of provider.generateStream(req)) {
    // no-op — this stub yields nothing
  }
}

describe("GeminiProvider — behaviour-neutrality pinned at the call site", () => {
  it("emits a request body with no temperature key for task=dialogue", async () => {
    const { fetchFn, bodies } = captureStreamFetch();
    await drive("dialogue", fetchFn);

    expect(bodies).toHaveLength(1);
    const body = JSON.parse(bodies[0]) as { generationConfig?: Record<string, unknown> };
    expect(body.generationConfig).not.toHaveProperty("temperature");
  });

  it("ALSO emits a request body with no temperature key for task=feedback", async () => {
    // Given Fix 1, every task is temperature-free right now — feedback included. This
    // assertion is what will FAIL the moment a future change puts
    // `{ temperature: FEEDBACK_TEMPERATURE }` back into GENERATION_TUNING.feedback
    // (config/generation.ts) once the sweep confirms the value — that's intentional:
    // whoever flips it is forced to look at this test and update it deliberately,
    // rather than the behaviour change slipping through unnoticed.
    const { fetchFn, bodies } = captureStreamFetch();
    await drive("feedback", fetchFn);

    expect(bodies).toHaveLength(1);
    const body = JSON.parse(bodies[0]) as { generationConfig?: Record<string, unknown> };
    expect(body.generationConfig).not.toHaveProperty("temperature");
  });
});
