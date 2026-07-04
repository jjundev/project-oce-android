/**
 * Functions entrypoint. `llm` (M0-07) is the /llm proxy; `mergeGuestData` (M3-03) is the
 * guest→Google 이관 콜러블; `onLedgerCreate` (M3-05) is the point_ledger → gamification/progress
 * aggregation trigger. `resetMetrics` remains out of scope here (schema-owned).
 */
import { initializeApp } from "firebase-admin/app";

initializeApp();

export { llm } from "./llm/handler";
export { mergeGuestData } from "./merge/mergeGuestData";
export { onLedgerCreate } from "./gamification/onLedgerCreate";
