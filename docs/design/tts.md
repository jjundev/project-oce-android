# 딸깍영어 v1 — TTS(상대역 음성) 설계

> **상태:** 확정(OQ3) · **작성일:** 2026-06-30 · **근거:** [PRD.md](../../PRD.md) §8.2.2·§10.3·FR-21 · 옛 `GeminiTtsManager`/`DialoguePlaybackCoordinator`/`AppSettings` · `grill-review --deep auto`(0B SHIP)

## 1. 제공자 & 음성
- 기본: **Gemini TTS** `gemini-2.5-flash-preview-tts`, **백엔드 LLM 프록시 경유**(키 서버 보관, [PRD.md](../../PRD.md) NFR-1).
- **2음성 성별 매핑:** 생성기 출력 `opponent_gender` → male=`Puck` / 그 외=`Kore`(기본). (옛 `resolveGeminiVoiceName` 계승 — 카페 남성 점원이 여성 음성으로 말하는 몰입 손상 방지.)
- 사용자는 음성·제공자·로케일을 **고르지 못함**(코드 고정).

## 2. 로케일 · 속도
- 로케일/악센트: **en-US 단일.** (en-GB는 옛 `buildSpeechPrompt`가 프롬프트 산문 힌트로만 주입 → 불안정. v1 제외, 실제 악센트 파라미터 생기면 v1.1.)
- 말하기 속도: 기본 **1.0x**, 범위 0.5~1.5x(설정 슬라이더, 옛 clamp 계승). 0.9x는 오버라이드 후보.
- **경로별 속도 보정(2026-07-17):** 같은 슬라이더 값이 경로마다 다른 절대 속도를 냈다 — DEVICE 는
  `setSpeechRate` 로 엔진 배속을 직접 먹지만, SERVER(Gemini TTS `generateContent`)는 구조적 속도
  파라미터가 없어 산문 힌트("Aim for speaking speed multiplier N")에 의존했고 모델이 이를 느슨하게만
  따랐다. 이제:
  - SERVER 는 **항상 중립(1.0)으로 합성**하고(`TtsPlaybackCoordinator.NEUTRAL_SYNTHESIS_RATE`),
    받은 PCM 을 `AudioTrack.setPlaybackParams`(피치 보존)로 **재생 시점에 배속**한다. 산문 힌트는
    중립값이 나가 사실상 무력화된다(서버 계약·`functions/` 는 미변경 — 힌트 문장 제거는 후속).
  - 두 경로 모두 `TtsSpeedCalibration` 의 실측 계수를 곱해 절대 속도를 맞춘다.
    **기준점 = Gemini `Kore` 자연 속도 = 1.0x**(Gemini 를 억지로 가속하지 않아 자연스러움 보존).
    계수 `w = D_source / D_reference`(동일 문장·중립 속도·무음 트림 후 길이의 비). **계수는 보이스별
    4개**(`WEIGHT_SERVER_FEMALE`=Kore=1.0 고정 · `WEIGHT_SERVER_MALE`=Puck · `WEIGHT_DEVICE_FEMALE` ·
    `WEIGHT_DEVICE_MALE`) — 단말도 성별별로 다른 보이스를 쓰므로 한쪽만 재서 양쪽에 적용하면 안 된다.
  - **클램프 경계는 "슬라이더 범위 × 그럴듯한 계수 범위"** 로 정한다(`MIN_EFFECTIVE_RATE`=0.25 /
    `MAX_EFFECTIVE_RATE`=2.0). 하한을 슬라이더 최소(0.5)와 같게 두면 계수가 1.0 미만인 순간 슬라이더
    하단 전체가 조용히 미보정으로 되돌아가는 결함이 있었다(최종 코드 리뷰에서 발견·수정).
  - 부수 효과: 배속이 캐시 키에서 빠져 **합성 1회가 모든 배속을 커버**한다(§3 의 세션 내 캐시 —
    세션 중 속도 변경이 재합성을 유발하지 않는다).
  - **실측 결과 및 한계(2026-07-17, `TtsCalibrationProbe`, 실기기 Samsung SM-S911N, 참조 문장 3개 ×
    3회 반복):** `WEIGHT_DEVICE_FEMALE`=0.855, `WEIGHT_DEVICE_MALE`=0.707, `WEIGHT_SERVER_MALE`=0.99.
    단말 합성은 **완전히 결정론적**이라(3회 반복 시 문장별 길이가 1ms도 다르지 않음) 두 단말 계수는
    재현 가능한 신호다. 반면 **Gemini TTS 는 중립 속도로 고정해도 같은 텍스트의 합성 길이 자체가
    호출마다 최대 ~12% 흔들렸고**, Puck/Kore 비율은 회차별로 0.87~1.16 까지 벌어졌다(9개 표본 평균은
    1.0 에 가까워 재현 가능한 성별 간 속도차는 검출되지 않음) — 즉 **"Gemini 가 라인마다 속도를
    다르게 따른다"는 원래 문제는 산문 힌트를 없앤 지금도 백엔드 자체의 비결정성으로 남아있고, 클라
    상수로는 근본적으로 고칠 수 없다.** SERVER 경로의 개별 발화는 보정 후에도 이 잡음만큼(±10~20%)
    빠르거나 느리게 들릴 수 있다 — 알려진 한계이지 회귀가 아니다. 상수 근거는
    `TtsSpeedCalibration.kt` 각 필드 KDoc 참고.

## 3. 합성 · 전달
- **라인당 합성**(전체 대본 일괄 아님). 클라이언트는 `POST /llm` `task=tts`를 호출하고, Gemini TTS → base64 PCM(24kHz) → **일반 JSON HTTPS 응답**(텍스트 SSE 아님) → 클라 디코드 → AudioTrack.
- 페이로드 ≈ 190~380KB/라인(base64). Cloud Run 응답 한계(수십 MB)에 여유. 지연은 합성 시간이 지배(페이로드 아님).
- **TTS 캐시 없음**(세션마다 대사 고유). 세션 내 재생 캐시는 후속.

## 4. 워치독 & 폴백
- **Gemini 워치독은 2단 분리(2026-07-16, 온디바이스 진단):** 세션의 **첫** Gemini-TTS 합성은 콜드(>8초, 서버 `gemini.ts` `REQUEST_TIMEOUT_MS` 7초 × `MAX_ATTEMPTS` 2회 재시도까지 걸릴 수 있음) — 이후 라인은 웜(~5-6초). 단일 8초 워치독이던 시절엔 첫 상대 대사가 실패했다(백엔드 keep-warm 은 반려, 클라이언트 단독 대응 확정).
  - **`SYNTH_WATCHDOG_MS` 16초** — *합성 잡 자체*의 예산. 서버 7초×2 재시도(최악 14초)를 덮어, 콜드 콜도 끝까지 완주해 캐시된다. 로딩퀴즈 워밍/다음 턴 프리페치처럼 **화면 뒤에 가려진 경로**만 이 예산을 쓴다 — 늦어도 사용자가 못 본다.
  - **`SERVER_WATCHDOG_MS` 8초** — **라이브 재생 대기**만의 상한(`playFromServer`). 합성 잡은 코디네이터 스코프의 형제 코루틴이라, 이 타임아웃은 *대기*만 포기할 뿐 합성은 백그라운드에서 계속 진행해 캐시를 채운다 — 라이브는 제때 단말 폴백, 콜드 합성 결과는 다음 재생에 재사용.
  - 단말 워치독은 7초(옛 `SCRIPT_TTS_WATCHDOG_ANDROID_MS` 계승).
- 단말 폴백 **조건부:** `LANG_MISSING_DATA` 또는 `LANG_NOT_SUPPORTED`(영어 데이터 미설치/미지원)면 음성 없이 **대사 텍스트만** 표시 + 재시도. "완전 오프라인 음성"은 영어 데이터 설치 시에만 성립.
- **모델 예열(2026-07-16):** 텍스트 태스크(`gemini-3.1-flash-lite`)와 TTS(`gemini-2.5-flash-preview-tts`)는
  **다른 모델**이라 대본 생성이 음성 모델을 데우지 못한다. 세션 첫 합성은 콜드(>7초)라 서버가 자체
  per-attempt 타임아웃(`gemini.ts` REQUEST_TIMEOUT_MS 7초 × 2회)으로 포기 → 첫 대사가 단말 폴백됐다.
  대응: 앱 전면 진입 시 throwaway 합성으로 모델을 예열(`warmUpModel`, SERVER·비음소거 한정, 결과 폐기·
  캐시 미적재)하고, 워밍 경로는 1회 재시도한다(`awaitWarm` — 실패한 콜드 호출도 모델을 데우므로 재시도가
  성사된다). 후속 근본 수정: 서버 `REQUEST_TIMEOUT_MS` 를 콜드 호출(~10초)보다 길게(예: 20초) 올리면
  재시도는 거의 쓰이지 않는 안전망이 된다(상시 비용 없음).
- 중간 세션 음성 불일치(일부 Gemini / 일부 단말) 수용 — 옛 앱도 라인당 폴백(선례).

## 5. 설정 화면 (FR-21)
- **음질 토글 1개:** "자연스러운 발음(서버, 약간 느림)" / "빠른 발음(단말)" **기본** — 벤더 선택을 *품질*로 재표현.
- 말하기 속도 슬라이더 · 전체 음소거.
- (옛 `AppSettings:14` `DEFAULT_TTS_PROVIDER="android"`. v1 초기엔 server 기본으로 뒤집었으나, §4 콜드 스타트
  지연 문제로 **2026-07-17 DEVICE 로 재복귀** — 신규 사용자가 첫 세션부터 콜드 지연을 겪지 않게. SERVER 는
  옵트인 유지.)
- **구현 노트(2026-07-16):** 라이브 대화 화면의 상대역 자동발화·"다시 듣기"가 이제 음질 설정을 따른다(이전엔 `deviceOnly=true` 로 단말 고정). "다시 듣기"는 SERVER 에서 서버 재합성한다(세션 내 PCM 캐시 재사용은 후속 — [TtsPlaybackCoordinator.replay] 의 turn-advance 잠복 이슈 선결 필요).
- **구현 노트(2026-07-17):** `TtsQuality` 기본값을 SERVER→DEVICE 로 변경(`TtsSettings.kt`·`TtsSettingsRepository.kt`
  폴백·`SettingsUiState.kt`). 온보딩(신규 사용자)은 별도 로직 없이 이 기본값을 그대로 물려받으므로 함께
  DEVICE 로 시작한다. §4 워밍업·재시도(2026-07-16)로 SERVER 콜드스타트가 완화됐지만, 신규 사용자의 첫
  세션 지연 리스크를 없애기 위해 기본값 자체를 안전한 쪽으로 되돌렸다.

## 6. 비목표 / 노트
- 사용자 음성 선택 · 다국어 로케일 · en-GB 토글 = **v1 제외**.
- 프록시-TTS · 단말 폴백은 **신규**(옛 앱은 Gemini 직접 호출, 폴백 없음). 프록시는 단일 `/llm` endpoint에서 응답 2모드(텍스트 SSE / TTS PCM JSON)를 갖는다.
