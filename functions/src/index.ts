/**
 * Functions entrypoint. `llm` (M0-07) is the /llm proxy; `onLedgerCreate` (M3-05) is the
 * point_ledger → gamification/progress aggregation trigger. resetMetrics / mergeGuestData remain
 * out of scope here (backend-functions.md:40, owned by M3-03 / the migration issue).
 */
import { initializeApp } from "firebase-admin/app";

initializeApp();

export { llm } from "./llm/handler";
export { onLedgerCreate } from "./gamification/onLedgerCreate";
