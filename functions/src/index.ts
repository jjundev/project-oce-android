/**
 * Functions entrypoint. `llm` (M0-07 scaffold) + `mergeGuestData` (M3-03, guest→Google
 * 이관 콜러블). 나머지 집계 함수(onLedgerCreate, resetMetrics)는 아직 범위 밖(M3-05 소유).
 */
import { initializeApp } from "firebase-admin/app";

initializeApp();

export { llm } from "./llm/handler";
export { mergeGuestData } from "./merge/mergeGuestData";
