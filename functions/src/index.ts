/**
 * Functions entrypoint. `llm` (M0-07) is the /llm proxy; `mergeGuestData` (M3-03) is the
 * guest→Google 이관 콜러블; `onLedgerCreate` (M3-05) is the point_ledger → gamification/progress
 * aggregation trigger; `resetMetrics`·`deleteAccount` (M3-09) are the settings 누적 초기화·계정 삭제 콜러블.
 */
import { initializeApp } from "firebase-admin/app";

initializeApp();

export { llm } from "./llm/handler";
export { mergeGuestData } from "./merge/mergeGuestData";
export { onLedgerCreate } from "./gamification/onLedgerCreate";
export { resetMetrics } from "./metrics/resetMetrics";
export { deleteAccount } from "./account/deleteAccount";
