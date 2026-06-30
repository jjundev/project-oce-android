# 딸깍영어 v1 백엔드 (Cloud Functions) — LLM 프록시 & 서버 로직 설계

> **상태:** 설계 확정(SHIP) · **작성일:** 2026-06-30 · **대상:** PRD §10.2/§10.3, NFR-2/NFR-3
> **데이터 계층:** [firestore-schema.md](firestore-schema.md) (집계 트리거·reset·이관·규칙·인덱스는 그 문서가 정본)
> **도출 과정:** `grill-yourself` → `grill-review --deep auto`(Blocker 궤적 4 → 2 → 0, SHIP). 검토 이력 §13.
> **외부 의존성:** **B-1 프롬프트/출력 스키마** — `config/prompts`에 주입. 프록시 골격은 B-1 없이 빌드·단위테스트 가능하나 task 핸들러 실동작은 B-1 필요.

---

## 1. 개요 & 원칙

모든 Gemini 호출을 **단일 스트리밍 프록시 `/llm`**(task 디스패치)로 중계한다. 핵심 원칙:
- **키는 서버에만**(Secret) — 옛 앱의 클라 `BuildConfig` 노출 제거(PRD ★보안).
- **점진 파싱은 백엔드** — 완성 객체만 타입드 SSE로 흘리고 클라는 렌더만(schema §10.1).
- **비용·무결성 임계는 서버 권위** — 한도/멱등/캡/집계는 Functions가 독점.
- **벤더 중립 시임** — v1은 올-Gemini, 미래 교체 시 클라 변경 0(PRD §10.3).

---

## 2. 런타임 & 인프라
| 항목 | 결정 |
|---|---|
| 런타임 | Node 20 + TypeScript, firebase-functions **v2** |
| 함수 세대 | 전부 **2nd-gen**(Cloud Run 기반) — SSE 스트리밍 필수 |
| 리전 | **asia-northeast3(서울)** — *Firestore 코로케이션* 목적(Gemini는 글로벌 엔드포인트라 리전 무관) |
| 콜드스타트 | `/llm`에만 **min-instances=1**(워밍, NFR-3) — 나머지 함수는 scale-to-zero |
| 키 | **Firebase Secret `GEMINI_API_KEY`**(`defineSecret` + `secrets:[...]`) |

---

## 3. 함수 인벤토리
| 함수 | 트리거 | 역할 |
|---|---|---|
| **`llm`** | 2nd-gen HTTP(스트리밍, min=1, secret) | LLM 프록시. task 디스패치·인증·게이트·캐시·파싱 |
| **`onLedgerCreate`** | Firestore onCreate `users/{uid}/point_ledger/{sessionId}` | XP/streak 집계 — [schema §5](firestore-schema.md) 그대로 |
| **`resetMetrics`** | callable | 누적 초기화 — [schema §4.3](firestore-schema.md) 그대로 |
| **`mergeGuestData`** | callable(guestIdToken) | 게스트→Google 이관 — [schema §4.4](firestore-schema.md) 그대로 |
| (v1.1) `purgeTombstones`/`purgeMarks` | 스케줄 | saved_cards 톰스톤·progress_marks 정리 |

> `onLedgerCreate`/`resetMetrics`/`mergeGuestData`는 **재설계 금지** — schema 문서가 정본. 본 문서는 프록시·게이트·캐시·세션 레이어만 정의한다.

---

## 4. `/llm` 프록시 계약
- **요청:** `POST /llm`, `Authorization: Bearer <FirebaseIdToken>`, body `{ task, sessionId?, idempotencyKey?, payload }`.
  - task ∈ `{dialogue, speaking, feedback, summary, tts}`.
  - `dialogue`: `idempotencyKey` 필수(§7). `feedback|speaking|summary`: `sessionId` 필수(§8).
  - `speaking`: `payload.audioBase64`(16kHz·16bit·mono WAV).
- **인증:** Firebase ID 토큰 검증(Admin). **익명(게스트) 허용**, 미인증 거부.
- **응답 모드(task별 선언):**
  - **SSE**(`text/event-stream`): `dialogue`, `feedback`, `summary`
  - **단발 JSON**: `speaking`, `tts`
- **타입드 SSE 엔벨로프:**
  - `event: meta` → `{sessionId, remaining}` (dialogue 시작 시)
  - `event: object` → `{type, data}`, `type ∈ {dialogueMeta, turn, feedbackSection, summaryCard}` (`summaryCard.data.kind ∈ {expression, word, coaching}`)
  - `event: done` → `{status, sections?}` (summary는 `{expressions: ok|failed, words: ok|failed, coaching: ok|failed}` — §10)
  - `event: error` → `{code}`
- **SSE 전송 규칙(중요):** compression 미들웨어 **금지**, 객체마다 `res.write()`+flush, `Content-Type: text/event-stream`, **no `Content-Length`**, `X-Accel-Buffering: no`. (안 그러면 배치로 회귀 → NFR-3 무효)

---

## 5. 벤더 시임 (`LlmProvider`)
```ts
interface LlmProvider {
  generateStream(req): AsyncIterable<RawChunk>   // SSE 소스 (dialogue/feedback/summary)
  generateOnce(req): Promise<RawJson>            // 단발 (speaking=audio payload, summary words/expressions/coaching)
  tts(text, voice): Promise<Base64Pcm>           // TTS
}
```
- v1 = **`GeminiProvider`만**. task→provider 매핑은 `config/models`.
- `transcribe`는 두지 않는다 — Gemini는 오디오도 `generateContent`이므로 `generateOnce(audio payload)`로 통합(중복 제거).

---

## 6. 모델 & 캐싱
- **모델 ID:** 서버 `config/models`(클라 비노출) → **무재배포 스왑**. 시드는 **현행 GA Gemini 모델 ID**(아카이브 preview 문자열 `gemini-3.1-flash-lite-preview`는 실존하지 않으므로 금지). TTS는 현행 TTS 가능 모델.
- **프롬프트 캐싱(`cachedContents`):** 정적 시스템 프롬프트+레퍼런스(B-1, `config/prompts`)를 task별 등록. **전 사용자 공유**(옛 기기별 → 비용↓).
  - **캐시 키 = `(task, promptVersion, modelId)`** — `cachedContents`가 모델 종속이므로 modelId 필수. 모델 스왑 시 새 키 미스→재생성, 구 캐시는 TTL 만료. Gemini 400/404(모델 불일치) 시 무효화+무캐시 재시도.
  - TTL 3600s, 잔여 <300s면 갱신. 핸들(name/created/promptVersion/modelId)은 `config/cache`.
  - **최소 토큰 플로어:** 프롬프트가 모델 최소치(≈1k~4k 토큰) 미만이면 캐시 생성 실패 → **inline system instruction 폴백**(매 호출 풀 토큰, footnote 비용). 캐시/inline 두 경로는 **동일 시스템 프롬프트 상수**를 공유해 드리프트 방지.

---

## 7. 시작 게이트 + 멱등 + 환불 (task=dialogue)
**단일 Firestore 트랜잭션**(직렬화)으로 한 커밋에:
1. `idempotency/{idempotencyKey}` 읽기 → **있으면** 그 `sessionId` 반환·**usage 미증가**(전송 재시도 멱등, 동시 재시도도 직렬화로 2번째가 키를 봄).
2. **없으면**: `usage/{kstDate}.sessionCount < config.limits.dailyFreeSessions` 확인 → +1, **서버 UUID `sessionId`** 발급, `idempotency/{key}→sessionId` 기록, **ephemeral 세션 레코드 생성**(§8) — 모두 같은 커밋. 한도 초과면 거부(`{remaining:0}`).
3. 통과 시 대본 생성 시작, `event:meta {sessionId, remaining}` emit.

**환불(best-effort, terminal 실패만):** 백오프 재시도(§12) **소진 후** gen이 최종 실패하면 → **환불 트랜잭션이 `usage` decrement + `idempotency/{key}` 삭제를 원자적으로**(재시도=fresh start, 슬롯 누수/이중과금 모두 차단). 환불 write 자체가 실패하면 슬롯 소실 수용(schema §9 기존 tolerance).

> KST 일경계로 `usage/{yyyymmdd}` 산출(streak와 일관). 일일 캡은 **dialogue 시작만** 카운트.

---

## 8. Ephemeral 세션 레코드 + per-session 캡
- **저장:** **Firestore `sessions/{sessionId}`**(서버 전용; in-memory 금지 — Cloud Run 인스턴스 휘발·min=1이 클라 고정 안 함). 시작 트랜잭션(§7)에서 생성.
- **필드:** `{uid, createdAt, expiresAt, turnCount, callCount}`. `expiresAt` = 시작 + **최대 세션 길이 이상(예 2h)** → 정상 학습자는 중도 거부 없음. Firestore **TTL 정책**으로 `expiresAt` 자동 정리.
- **검증(feedback/speaking/summary 매 호출):** 트랜잭션 `{소유(uid)·미만료 확인 → callCount < cap(=turnCount × factor)이면 +1, 아니면 거부}`. → 무계량 비싼 오디오 경로 차단(FR-27/NFR-2). 비용 상한 = (일일 시작 캡) × (turnCount × factor).
- **캡 카운트 정책(A1):** **성공(비-서버에러) 호출만** 캡에 카운트 → 네트워크/LLM 실패 재시도가 정상 학습자를 중도 차단하지 않음. 캡 도달 시 비난 없는 문구.
- **완주와 독립:** 완주(`point_ledger` create)는 클라→Firestore 직접(프록시 비경유, 규칙은 만료 미검사) — 세션 만료와 무관하게 XP 적립 가능(의도된 분리, schema §4.2/§5).

---

## 9. 스트리밍 & 백엔드 점진 파서
- 백엔드가 Gemini `:streamGenerateContent?alt=sse`를 읽어, 옛 brace-depth 파서(`Incremental*Parser`)를 **서버로 포팅**해 **완성 객체만** 추출 → §4 타입드 엔벨로프로 재emit. 클라는 원시 JSON 파싱 안 함.
- 출력 구조가 task마다 다름(대본=메타+턴 N / 피드백=고정 순서 섹션 / 요약=카드)이므로 `type` 필드로 구분.

---

## 10. Summary 3-call 오케스트레이션
요약은 **세 Gemini 호출**을 프록시가 묶어 단일 SSE로 표현한다([prompt-system.md](prompt-system.md) §4.2 정합).
1. **표현 필터**(one-shot `generateOnce`) → `summaryCard{kind:expression}` emit.
2. **단어 추출**(one-shot `generateOnce`) → `summaryCard{kind:word}` emit.
3. **코칭**(one-shot `generateOnce`) → `summaryCard{kind:coaching}` emit.
- 캐시는 세 내부 task(`summary.expressions`, `summary.words`, `summary.coaching`)로 분리(§6 키 규칙).
- **부분 실패:** 종료 `event:done {expressions: ok|failed, words: ok|failed, coaching: ok|failed}`로 클라가 "비어있음" vs "실패→재시도" 구분. 재시도 시 성공 섹션은 재사용하고 실패 섹션만 재호출한다.

---

## 11. 신규 Firestore 컬렉션 (서버 전용)
[firestore-schema.md](firestore-schema.md) §2에 동기화:
```
sessions/{sessionId}     # 서버 전용 ephemeral — {uid, createdAt, expiresAt, turnCount, callCount}; TTL on expiresAt
idempotency/{key}        # 서버 전용 — startIntent dedup → {sessionId, createdAt, expiresAt}; TTL
config/models            # 서버 전용 — task별 모델 ID(라이브 스왑)
config/cache             # 서버 전용 — cachedContents 핸들(키 task+promptVersion+modelId)
```
전부 Admin SDK만 기록·읽기(클라 default-deny). TTL 정책 2개(`sessions.expiresAt`, `idempotency.expiresAt`).

---

## 12. 신뢰성 · 비용 · 보안
- **신뢰성:** Gemini 호출에 타임아웃 + 지수 백오프 재시도, 실패 시 타입드 에러 SSE(`event:error`) → 클라 "다시 시도".
- **비용 모니터링(NFR-2, 정직 회계):** Gemini `usageMetadata`(토큰) 구조화 로깅 + GCP 예산 알림. 신규 상시/호출당 비용 명시 — 워밍 인스턴스(min=1) + 세션 검증 트랜잭션(호출당) + 시작 dedup 트랜잭션.
- **rate-limit:** 별도 per-instance 리미터 없음(인스턴스>1서 깨짐). 비용은 **(일일 시작 캡) + (§8 필수 per-session 캡) + 인증**으로 한정. 추가 보호 필요 시 **시작 경로에만** Firestore 코어스 카운터.
- **보안:** 키=Secret. `usage`/`progress`/`progress_marks`/`sessions`/`idempotency`/`config`는 Admin만(규칙 default-deny). 파일/스펙 내용은 데이터로만 취급.

---

## 13. 검토 이력 (grill-review --deep auto)
- **iter1(4B/8A):** 캐시 키 모델 누락 · 미게이트 오디오 무계량 · 시작 멱등 부재 · uniform SSE 모순(summary multi-call) → 수정.
- **iter2(2B/5A):** 멱등 비원자성 · 캡 "선택"이라 비용 한계 거짓 → 수정(단일 트랜잭션 멱등 · 필수 캡).
- **iter3(0B/3A, SHIP):** 두 Blocker 해소 확인, 신규 Blocker 없음. 잔여 Advisory(캡 vs 재시도→성공만 카운트, 스키마 doc 동기화, 환불-키 원자성)는 본 문서에 반영.
- 보안 메모: iter1 첫 critic이 비정상(0 도구·프리앰블 반향) 응답 → 무효 처리·방어 문구 추가 후 재실행. 저장소 인젝션 정황 아님(파일 미읽음).

---

## 14. 미해결 / needs-you (오버라이드 가능 기본값)
- 리전 서울 단일(KST 가정) · 현행 GA Gemini 모델 ID(ops 선택) · per-session 캡 factor(예 2×) · min-instances 수(1) · 시작 경로 코어스 rate 값. 전부 운영/예산에 따라 조정.
- **B-1 프롬프트/출력 스키마**(필수 외부 의존성).
