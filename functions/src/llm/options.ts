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
 * Production min-instances default. Set to 0 for single-user scale-to-zero
 * cost optimization.
 */
export const LLM_MIN_INSTANCES_DEFAULT = 0;
