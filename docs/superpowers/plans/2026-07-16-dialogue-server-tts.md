# Dialogue Server (LLM/Gemini) TTS Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the live dialogue screen honor the user's "자연스러운 발음" (`TtsQuality.SERVER`) setting so opponent lines are spoken via the already-built server (Gemini/LLM) TTS path instead of always using device TTS.

**Architecture:** The entire server-TTS stack already exists and is unit-tested end-to-end — the Cloud Functions `/llm` `task=tts` endpoint, `TtsPlaybackCoordinator.playFromServer` (8s watchdog → device fallback), and the Review flow all use it. The setting is stored in DataStore and honored *by the coordinator*. The one gap: `GeneratedDialogueSessionViewModel.speakOpponent` / `replayOpponent` pass a hardcoded `deviceOnly = true`, which short-circuits the coordinator's `settings.quality == TtsQuality.SERVER` branch. This plan removes that override so the dialogue inherits the coordinator's already-correct, already-tested routing. On manual "다시 듣기" re-taps the line is re-synthesized honoring the current quality (decision A — simplest, non-regressing; no in-memory replay reuse).

**Tech Stack:** Kotlin, Jetpack Compose, Hilt DI, Retrofit (`/llm` proxy), DataStore Preferences, JUnit4 + kotlinx-coroutines-test. Single Gradle module `:app`. Backend: Firebase Cloud Functions (TypeScript), Gemini `gemini-2.5-flash-preview-tts`, region `asia-northeast3`.

## Global Constraints

- Single Gradle module `:app`; `core`/`feature` are **package** boundaries, not modules. Namespace/appId `com.jjundev.oneclickeng`.
- All Gradle verification runs through `scripts/verify-android.sh` (never bare `./gradlew` in a worktree — shared `~/.gradle` cache/daemon pollution, missing `google-services.json`, KGP variant-test source-set gotchas). See [docs/agents/android-verification.md](docs/agents/android-verification.md).
- Variant unit tests live under `src/test/kotlin/` (shared source set); this plan only touches existing shared-source-set tests.
- Server TTS provider/voice/locale are code-fixed (en-US; male→`Puck`, else→`Kore`). Do not add user-facing voice/locale controls (tts.md §6 non-goals).
- `TtsQuality.SERVER` = "자연스러운 발음", `TtsQuality.DEVICE` = "빠른 발음". `SERVER` is the default quality (tts.md §5).
- Keep the coordinator's `deviceOnly` parameter and its test (`deviceOnly skips server synthesis even when quality is SERVER`) — it remains a legitimate coordinator capability even though no production caller passes `true` after this change.

---

## File Structure

**No new files.** This is a wiring change plus verification.

- **Modify:** `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt` — remove `deviceOnly = true` from `speakOpponent` (line 431) and `replayOpponent` (line 438); update the two KDoc comments (lines 429, 434–435) so they no longer claim "디바이스 자동발화" and instead reflect quality-aware routing.
- **Modify (docs):** `docs/design/tts.md` — add one note that the live dialogue now honors the 음질 setting (folded into Task 2).
- **Unchanged, relied upon:**
  - `.../feature/session/tts/TtsPlaybackCoordinator.kt` — `playTurn(text, gender, deviceOnly = false, advanceOnDone = true)`; the `!deviceOnly && quality == SERVER` branch at line 97 already routes to `playFromServer`.
  - `.../core/network/LlmApi.kt` — `suspend fun tts(body: TtsRequest): TtsResponse`.
  - `.../feature/review/ReviewViewModel.kt:48` — already server-capable (`speak = { text -> tts.playTurn(text, gender = null, advanceOnDone = false) }`), used as the pre-flight probe in Task 1.
  - `.../app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt` — existing suite that pins the coordinator contract the dialogue now inherits.

---

## Task 1: Pre-flight — confirm the server (LLM) TTS path is live

**Why first:** The whole feature assumes the backend `/llm` `task=tts` endpoint is deployed, warm, reachable, and returns decodable PCM. If it is not, removing `deviceOnly` in Task 2 will silently fall back to device TTS after an 8s wait every turn — a worse UX than today, and a wasted change. The **Review flow already uses the server path** with no `deviceOnly` override, so it is a zero-code probe of the exact code path Task 2 turns on for dialogue. This task de-risks Task 2 by proving the backend end-to-end before touching any code.

**Files:**
- None modified. Verification only.

**Interfaces:**
- Consumes: `ReviewViewModel.speak` → `TtsPlaybackCoordinator.playTurn(...)` → `LlmApi.tts(TtsRequest(payload = TtsPayload(text, gender=null, speechRate)))` → `POST https://asia-northeast3-oce-v1.cloudfunctions.net/llm` with body `{"task":"tts","payload":{...}}` → `TtsResponse(pcmBase64, sampleRate, mimeType)`.
- Produces: a go/no-go signal for Task 2. No code artifact.

- [ ] **Step 1: Confirm the setting is `SERVER` and locate the Review speak trigger**

Read [ReviewViewModel.kt:48](android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewViewModel.kt:48) and confirm the lambda is `speak = { text -> tts.playTurn(text = text, gender = null, advanceOnDone = false) }` (no `deviceOnly`). Read [SettingsScreen.kt](android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreen.kt) `VoiceCardBody` and confirm the 음질 segmented control offers "자연스러운 발음" (`TtsQuality.SERVER`). Default quality is already `SERVER` per [TtsSettings.kt](android/app/src/main/kotlin/com/jjundev/oneclickeng/core/settings/TtsSettings.kt).

- [ ] **Step 2: Run the app and drive the Review flow with quality = SERVER**

Use the `run` skill (or `verify` skill) to launch the app on an emulator/device. In Settings, ensure 음질 = "자연스러운 발음". Not muted. Then open a Review item and trigger playback (the speaker/play control that calls `ReviewViewModel.playTts`).

- [ ] **Step 3: Confirm the server path actually fired (objective signal)**

Observe **both**:
1. A network request to `POST .../llm` whose request body contains `"task":"tts"` and whose response is JSON with a non-empty `pcmBase64` and a `sampleRate` (24000 expected). Capture via the emulator's network inspector, Logcat (OkHttp), or Charles/mitmproxy.
2. Audible natural (Gemini) speech — distinguishable from the flat device voice.

Expected: request succeeds within the 8s `SERVER_WATCHDOG_MS`; audio plays.

- [ ] **Step 4: Decision gate**

- **If the server request succeeds and natural audio plays → PASS.** The backend is live; proceed to Task 2.
- **If it times out / errors / falls back to device (no `/llm` `task=tts` 2xx with PCM) → STOP.** The backend, auth token, or region is the blocker, not the dialogue caller. Do **not** proceed to Task 2 (it would only add an 8s stall + device fallback to dialogue). Escalate: check the Cloud Functions deployment (`functions/`, project `oce-v1`, region `asia-northeast3`), `AuthInterceptor`/`FirebaseTokenProvider` token, and Gemini TTS quota. This is out of scope for this plan — report the failing signal and stop.

No commit (verification-only task).

---

## Task 2: Route dialogue opponent auto-speak + replay through the quality setting

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt` (lines 429–438)
- Modify: `docs/design/tts.md`
- Test (relied upon, unchanged): `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/session/tts/TtsPlaybackCoordinatorTest.kt`

**Interfaces:**
- Consumes: `TtsPlaybackCoordinator.playTurn(text: String, gender: String?, deviceOnly: Boolean = false, advanceOnDone: Boolean = true)`. After this task, dialogue calls it **without** `deviceOnly`, so the default `false` applies and the coordinator's `quality == SERVER` branch routes to the server.
- Produces: no new public API. `speakOpponent(text: String)` and `replayOpponent(text: String)` keep their signatures; only their delegated `playTurn` arguments change.

- [ ] **Step 1: Confirm the current (bug) behavior in code**

Read [GeneratedDialogueSession.kt:429-438](android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt:429). Confirm both `speakOpponent` and `replayOpponent` pass `deviceOnly = true`. This is why "자연스러운 발음" has no effect in the dialogue screen even though Task 1 proved the server path works.

- [ ] **Step 2: Confirm the coordinator already pins the target contract (no new test needed)**

Run the existing coordinator suite and confirm these green (they encode the behavior dialogue inherits once `deviceOnly` is dropped — default args + `SERVER` → server; server failure → device fallback; `deviceOnly` still forces device):

Run: `scripts/verify-android.sh :app:testDebugUnitTest --tests '*TtsPlaybackCoordinatorTest*'`
Expected: PASS. Specifically these tests exist and pass:
- `server path synthesizes decodes and plays at server sample rate` — `playTurn("Hello", "female")` with **default** args + `SERVER` → `api.callCount == 1`, device untouched. (This is the exact call shape `speakOpponent` will use.)
- `server watchdog timeout falls back to device tts` — server failure → device fallback (dialogue keeps working if the server stalls).
- `deviceOnly skips server synthesis even when quality is SERVER` — the retained `deviceOnly` capability.

Rationale: the VM's `speakOpponent`/`replayOpponent` are thin delegations to the final `TtsPlaybackCoordinator`, which has no fake/lambda seam in the dialogue VM (unlike Review). Confirmed by convention: no test in this repo instantiates `GeneratedDialogueSessionViewModel` (its 9-dependency `@HiltViewModel` ctor at [GeneratedDialogueSession.kt:174](android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt:174)); the `.../feature/session/turn/` tests all exercise pure functions / extracted state holders (`GeneratedDialogueState`, `shouldRestoreDurableSnapshot`) instead. The correctness of "dialogue honors `SERVER`" is fully captured by the coordinator's default-argument routing, already tested above. Adding a synthetic VM test here (no seam exists) would be test theater; the behavioral guarantee is the coordinator contract + the end-to-end check in Step 6. Do **not** fabricate a VM-level unit test.

If the team later wants VM-level coverage of "dialogue calls `playTurn` without `deviceOnly`", the idiomatic, precedented option is to add a Review-style seam: an `internal` test constructor on `GeneratedDialogueSessionViewModel` that wraps the TTS calls behind lambdas (mirroring `ReviewViewModel`'s `speak: (String) -> Unit` at [ReviewViewModel.kt:36](android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/review/ReviewViewModel.kt:36)). That is a separate refactor, out of scope here.

- [ ] **Step 3: Remove the `deviceOnly` override and update the KDoc (auto-speak)**

In [GeneratedDialogueSession.kt](android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt), change the `speakOpponent` KDoc (line 429) and body (line 431):

```kotlin
        /** 상대역 대사 자동발화(Route 가 commitReveal 직후 호출). 음질 설정을 따른다 — SERVER 면 서버(Gemini)
         *  합성(8초 워치독 후 단말 폴백), DEVICE 면 단말 TTS. 완료 시 completions→자동진행. */
        fun speakOpponent(text: String) {
            tts.playTurn(text, gender = opponentSpeaker?.gender, advanceOnDone = true)
        }
```

- [ ] **Step 4: Remove the `deviceOnly` override and update the KDoc (replay)**

Change the `replayOpponent` KDoc (lines 434–435) and body (line 438):

```kotlin
        /** 말풍선 "다시 듣기" 재발화. 자동발화 중(OpponentTurn)엔 no-op — 라이브 발화 취소·조기전진을 막는다.
         *  음질 설정을 따라 재합성한다(SERVER 면 서버 재합성 — 캐시 재사용 아님, 결정 A). advanceOnDone=false 라
         *  재발화 완료가 턴 전진을 구동하지 않는다(경쟁 봉인, 결정 #9). */
        fun replayOpponent(text: String) {
            if (turnState.turnPhase == TurnPhase.OpponentTurn) return
            tts.playTurn(text, gender = opponentSpeaker?.gender, advanceOnDone = false)
        }
```

Confirm afterward that `grep -rn "deviceOnly" android/app/src/main --include="*.kt"` returns **only** the coordinator's own definition/usage (`TtsPlaybackCoordinator.kt` lines ~78, 83, 97) and no longer any call site in `GeneratedDialogueSession.kt`.

- [ ] **Step 5: Run the full verification set (compile + detekt + unit tests)**

Run: `scripts/verify-android.sh`
Expected: PASS — compiles, detekt clean, both variant unit-test source sets green (including the coordinator suite from Step 2). No test was added or changed, so failures here mean the edit broke compilation or an unrelated regression — fix before continuing.

- [ ] **Step 6: End-to-end verify in the dialogue screen (the actual feature)**

Use the `verify` (or `run`) skill to launch the app. With Settings 음질 = "자연스러운 발음", not muted:
1. Start a dialogue session. When an opponent line auto-speaks, confirm a `POST .../llm` `"task":"tts"` request fires and **natural (Gemini) audio** plays — not the flat device voice. Confirm the opponent skeleton stays up during synthesis (LOADING) and the bubble reveals when audio starts (`audioReady`), i.e. the "minimum skeleton dwell" floor still holds and reveal isn't premature.
2. Tap "다시 듣기" on a revealed opponent bubble → confirm it re-synthesizes via the server (a **new** `/llm` `task=tts` request) and does **not** auto-advance the turn.
3. Switch Settings 음질 = "빠른 발음" (`DEVICE`), start/continue a dialogue → confirm auto-speak uses device TTS with **no** `/llm` `task=tts` request. (Regression guard: `DEVICE` mode unchanged.)
4. (Optional sanity) Toggle 전체 음소거 on → confirm no audio and the turn still advances.

Expected: SERVER mode plays natural audio in dialogue (the fix); DEVICE mode unchanged.

- [ ] **Step 7: Update the design doc**

In [docs/design/tts.md](docs/design/tts.md), append a short note under §5 (or §3) recording that the live dialogue screen now routes opponent auto-speak and "다시 듣기" through the 음질 setting (previously hardcoded to device). One or two lines; do not restructure the doc. Example line to add:

```markdown
- **구현 노트(2026-07-16):** 라이브 대화 화면의 상대역 자동발화·"다시 듣기"가 이제 음질 설정을 따른다(이전엔 `deviceOnly=true` 로 단말 고정). "다시 듣기"는 SERVER 에서 서버 재합성한다(세션 내 PCM 캐시 재사용은 후속 — [TtsPlaybackCoordinator.replay] 의 turn-advance 잠복 이슈 선결 필요).
```

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/turn/GeneratedDialogueSession.kt docs/design/tts.md
git commit -m "feat(dialogue): honor 자연스러운 발음(SERVER) TTS in live dialogue

상대역 자동발화·다시 듣기가 하드코딩된 deviceOnly=true 로 단말 TTS 에 고정돼
있어 음질 설정이 무시됐다. 오버라이드를 제거해 이미 구현·검증된 코디네이터의
SERVER 라우팅(서버 합성→8초 워치독→단말 폴백)을 대화 화면이 상속하도록 한다.
다시 듣기는 결정 A 대로 음질을 따라 재합성한다(캐시 재사용 아님).

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Non-Goals / Follow-ups

- **In-memory replay reuse for "다시 듣기"** (decision B, not taken): reuse the just-synthesized PCM via `TtsPlaybackCoordinator.replay()` for instant, free, identical-audio re-taps. Blocked on a latent bug — `replay()` calls `startNewSession()` which resets `advanceOnDone = true` and never overrides it (unlike `playTurn`/`playClip`), so it would emit `completions` and auto-advance the turn; the existing test `replay reuses retained pcm without re-synthesizing` does not assert on `completions`, so this bug is uncovered today. `replay()` also has no re-synth fallback for DEVICE mode (null `lastPcm`). Fix (override `advanceOnDone` in `replay()` + assert it) + a `hasReplayableAudio()` seam is a separate change.
- **Offline fast-path:** when disconnected in `SERVER` mode, every opponent turn waits the full 8s `SERVER_WATCHDOG_MS` before device fallback. A `ConnectivityMonitor`-gated short-circuit (skip the server attempt when offline) is a UX follow-up; tts.md §4 currently accepts the watchdog fallback.
- **Removing the coordinator `deviceOnly` parameter:** intentionally kept as a tested capability; no production caller uses `true` after this change.
- **TTS caching across sessions / en-GB accent / user voice selection:** explicit v1 non-goals (tts.md §6).

---

## Self-Review

**Spec coverage:** The user's request — "when 자연스러운 발음 is set, the LLM-API pronunciation feature is not implemented; implement it" — is satisfied by Task 2 removing the `deviceOnly` override so dialogue uses the server path. Task 1 proves the backend is live first. The replay decision (A) is folded into Task 2 Step 4. DEVICE-mode regression, mute, and reveal-timing are covered in Task 2 Step 6.

**Placeholder scan:** No TBD/"handle edge cases"/"add validation" placeholders. Every code step shows the full replacement code. The one place without a new unit test (Task 2 Step 2) explicitly justifies why (no VM test seam; coordinator contract already covers it) rather than hand-waving.

**Type consistency:** `speakOpponent(text: String)` / `replayOpponent(text: String)` signatures unchanged; `playTurn(text, gender, deviceOnly = false, advanceOnDone = true)` used with named args matching the coordinator definition. `TtsQuality.SERVER` / `TtsQuality.DEVICE` used consistently. `SERVER_WATCHDOG_MS` referenced by its actual constant name.
