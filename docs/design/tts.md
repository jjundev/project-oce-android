# 딸깍영어 v1 — TTS(상대역 음성) 설계

> **상태:** 확정(OQ3) · **작성일:** 2026-06-30 · **근거:** [PRD.md](../../PRD.md) §8.2.2·§10.3·FR-21 · 옛 `GeminiTtsManager`/`DialoguePlaybackCoordinator`/`AppSettings` · `grill-review --deep auto`(0B SHIP)

## 1. 제공자 & 음성
- 기본: **Gemini TTS** `gemini-2.5-flash-preview-tts`, **백엔드 LLM 프록시 경유**(키 서버 보관, [PRD.md](../../PRD.md) NFR-1).
- **2음성 성별 매핑:** 생성기 출력 `opponent_gender` → male=`Puck` / 그 외=`Kore`(기본). (옛 `resolveGeminiVoiceName` 계승 — 카페 남성 점원이 여성 음성으로 말하는 몰입 손상 방지.)
- 사용자는 음성·제공자·로케일을 **고르지 못함**(코드 고정).

## 2. 로케일 · 속도
- 로케일/악센트: **en-US 단일.** (en-GB는 옛 `buildSpeechPrompt`가 프롬프트 산문 힌트로만 주입 → 불안정. v1 제외, 실제 악센트 파라미터 생기면 v1.1.)
- 말하기 속도: 기본 **1.0x**, 범위 0.5~1.5x(설정 슬라이더, 옛 clamp 계승). 0.9x는 오버라이드 후보.

## 3. 합성 · 전달
- **라인당 합성**(전체 대본 일괄 아님). 클라이언트는 `POST /llm` `task=tts`를 호출하고, Gemini TTS → base64 PCM(24kHz) → **일반 JSON HTTPS 응답**(텍스트 SSE 아님) → 클라 디코드 → AudioTrack.
- 페이로드 ≈ 190~380KB/라인(base64). Cloud Run 응답 한계(수십 MB)에 여유. 지연은 합성 시간이 지배(페이로드 아님).
- **TTS 캐시 없음**(세션마다 대사 고유). 세션 내 재생 캐시는 후속.

## 4. 워치독 & 폴백
- **Gemini 워치독 8초:** 8초 내 PCM 미수신이면 **단말 Android TTS**로 폴백(`config` 튜닝 가능). 단말 워치독은 7초(옛 `SCRIPT_TTS_WATCHDOG_ANDROID_MS` 계승). *주: 옛 Gemini 워치독은 30초였으나 프록시 warm + min-instances 전제로 8초로 단축.*
- 단말 폴백 **조건부:** `LANG_MISSING_DATA` 또는 `LANG_NOT_SUPPORTED`(영어 데이터 미설치/미지원)면 음성 없이 **대사 텍스트만** 표시 + 재시도. "완전 오프라인 음성"은 영어 데이터 설치 시에만 성립.
- 중간 세션 음성 불일치(일부 Gemini / 일부 단말) 수용 — 옛 앱도 라인당 폴백(선례).

## 5. 설정 화면 (FR-21)
- **음질 토글 1개:** "자연스러운 발음(서버, 약간 느림)" 기본 / "빠른 발음(단말)" — 벤더 선택을 *품질*로 재표현.
- 말하기 속도 슬라이더 · 전체 음소거.
- (옛 `AppSettings:14` `DEFAULT_TTS_PROVIDER="android"` → v1은 server 기본으로 뒤집음.)

## 6. 비목표 / 노트
- 사용자 음성 선택 · 다국어 로케일 · en-GB 토글 = **v1 제외**.
- 프록시-TTS · 단말 폴백은 **신규**(옛 앱은 Gemini 직접 호출, 폴백 없음). 프록시는 단일 `/llm` endpoint에서 응답 2모드(텍스트 SSE / TTS PCM JSON)를 갖는다.
