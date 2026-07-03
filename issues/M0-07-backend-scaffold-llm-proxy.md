---
milestone: M0
area: backend
size: M
labels: [milestone:M0, area:backend, ready-for-agent]
blocked_by: []
blocks: [M1-02, M1-05, M1-06, M2-01, M3-03]
---

# [M0-07] 백엔드 스캐폴드 + /llm 프록시 골격

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
모든 Gemini 호출을 단일 스트리밍 프록시로 중계. API 키는 서버에만. 프록시 골격은 프롬프트(B-1) 없이 빌드·단위테스트 가능.

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/design/backend-functions.md](../docs/design/backend-functions.md) §1~6 · [PRD.md](../PRD.md) §10.2·§10.3, NFR-1/3
- 시각: 해당 없음(서버)

## 목표
2nd-gen Cloud Functions `/llm`이 인증·task 디스패치·벤더 시임·SSE 전송 규칙을 갖춘 골격으로 배포된다(핸들러 실동작은 후속).

## 범위
- In: Node 20 + TS, firebase-functions v2, 전 함수 2nd-gen, 리전 asia-northeast3, `/llm` min-instances=1, `GEMINI_API_KEY` Secret(`defineSecret`), `LlmProvider` 시임(`GeminiProvider`), 타입드 SSE 엔벨로프(meta/object/done/error), SSE 전송 규칙(compression 금지·flush·no Content-Length·X-Accel-Buffering).
- Out: dialogue 게이트/파서(M1-02), speaking/tts(M1-05/06), summary(M2-01), 집계 트리거(M3-05), 프롬프트 B-1(외부 의존).

## 의존성
- Blocked by: —
- Blocks: M1-02, M1-05, M1-06, M2-01, M3-03

## 수용 기준
- [ ] `/llm`이 Firebase ID 토큰 검증(익명 허용, 미인증 거부)
- [ ] task ∈ {dialogue, speaking, feedback, summary, tts} 디스패치 스텁
- [ ] `LlmProvider` 인터페이스 + `GeminiProvider` 배선(config/models)
- [ ] SSE 전송이 배치로 회귀하지 않음(수동 스트림 확인)
- [ ] 프롬프트 B-1 없이 빌드·단위테스트 통과

## 검증
Functions 에뮬레이터에서 `/llm` 인증·디스패치 단위테스트. SSE flush 확인.
