/**
 * onRequest binding for /llm — runtime options + the pure pipeline in handle.ts.
 * SoT: backend-functions.md:25-27 (region, min-instances, secret).
 * The concrete option values live in options.ts (unit-tested there).
 */
import { onRequest } from "firebase-functions/v2/https";
import { defineInt, defineSecret } from "firebase-functions/params";
import { handle, HandlerRequest, HandlerResponse } from "./handle";
import { createGeminiProvider } from "../providers/gemini";
import {
  LLM_MIN_INSTANCES_DEFAULT,
  LLM_MIN_INSTANCES_PARAM,
  LLM_REGION,
  LLM_SECRET_NAME,
} from "./options";

/** Gemini API key — server-only Firebase Secret (backend-functions.md:27). */
export const GEMINI_API_KEY = defineSecret(LLM_SECRET_NAME);

/**
 * Warming instances for /llm. Default is 1 per SoT (backend-functions.md:26,
 * M0-07 issue:25) for cold-start mitigation (NFR-3). `LLM_MIN_INSTANCES` is a
 * NON-PROD knob (test/staging) only — production 0 re-violates NFR-3 and requires
 * an SoT amendment. The M0-07 scaffold does not deploy, so no warming cost yet.
 */
export const LLM_MIN_INSTANCES = defineInt(LLM_MIN_INSTANCES_PARAM, {
  default: LLM_MIN_INSTANCES_DEFAULT,
});

export const llm = onRequest(
  {
    region: LLM_REGION,
    secrets: [GEMINI_API_KEY],
    minInstances: LLM_MIN_INSTANCES,
  },
  async (req, res) => {
    // Construct the provider here — the Gemini Secret is only resolvable in the
    // onRequest context. `.value()` is lazy, so no cost until a tts call reads it.
    const provider = createGeminiProvider(GEMINI_API_KEY.value());
    await handle(
      req as unknown as HandlerRequest,
      res as unknown as HandlerResponse,
      { provider }
    );
  }
);
