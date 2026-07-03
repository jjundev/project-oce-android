/**
 * Runtime-option constants for the `llm` function, kept free of firebase-functions
 * imports so they are directly unit-testable (a typo in region or a wrong secret
 * name would otherwise build/lint/test clean — see options.test.ts).
 * SoT: backend-functions.md:25-27, issue M0-07:25.
 */

/** Firestore-colocation region (Gemini is global) — backend-functions.md:25 */
export const LLM_REGION = "asia-northeast3";

/** Firebase Secret holding the Gemini API key — backend-functions.md:27 */
export const LLM_SECRET_NAME = "GEMINI_API_KEY";

/** param name for the (non-prod) min-instances override knob */
export const LLM_MIN_INSTANCES_PARAM = "LLM_MIN_INSTANCES";

/**
 * Production min-instances default. SoT mandates 1 for warming / cold-start
 * mitigation (backend-functions.md:26, NFR-3). The param above may lower this in
 * NON-PROD only; production 0 re-violates NFR-3 and requires an SoT amendment.
 */
export const LLM_MIN_INSTANCES_DEFAULT = 1;
