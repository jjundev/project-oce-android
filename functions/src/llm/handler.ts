/**
 * onRequest binding for /llm — runtime options + the pure pipeline in handle.ts.
 * SoT: backend-functions.md:25-27 (region, min-instances, secret).
 * The concrete option values live in options.ts (unit-tested there).
 */
import { onRequest } from "firebase-functions/v2/https";
import { defineInt, defineSecret } from "firebase-functions/params";
import { handle, HandlerRequest, HandlerResponse } from "./handle";
import { firestoreSessionGate } from "./session-cap";
import { firestoreLimitProvider, firestoreStartGate } from "./start-gate";
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
 * Warming instances for /llm. Set to 0 default for scale-to-zero cost optimization.
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
    // Firestore-backed per-session cap gate for speaking (§8); getFirestore() is lazy.
    const sessionGate = firestoreSessionGate();
    // Dialogue start gate (§7): single-txn dedup + daily limit + session create; limit from
    // config/limits with fallback. getFirestore() is lazy (initializeApp() ran in index.ts).
    const startGate = firestoreStartGate(firestoreLimitProvider());
    await handle(
      req as unknown as HandlerRequest,
      res as unknown as HandlerResponse,
      { provider, sessionGate, startGate }
    );
  }
);
