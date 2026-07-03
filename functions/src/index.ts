/**
 * Functions entrypoint. Only `llm` is exported in the M0-07 scaffold — the
 * aggregation/callable functions (onLedgerCreate, resetMetrics, mergeGuestData)
 * are out of scope here (backend-functions.md:40, owned by M3-03/M3-05).
 */
import { initializeApp } from "firebase-admin/app";

initializeApp();

export { llm } from "./llm/handler";
