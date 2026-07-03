---
milestone: M1
area: android/backend
size: M
labels: [milestone:M1, area:android, area:backend, ready-for-agent]
blocked_by: [M0-07]
blocks: [M1-06]
---

# [M1-05] TTS 재생 + /llm tts task

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
상대역 턴의 음성 재생. Gemini TTS(서버) + AudioTrack 재생, 워치독으로 지연 보호. 음질 토글(자연스러움-서버/빠름-단말).

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/design/tts.md](../docs/design/tts.md)(정본: 2음성 성별 매핑·en-US·1.0x·워치독 8초·조건부 단말 폴백) · [docs/design/backend-functions.md](../docs/design/backend-functions.md) §4·§5 · [PRD.md](../PRD.md) FR-7, FR-21, NFR-6
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) `session`(재생/다시듣기)

## 목표
상대역 텍스트가 `tts` task로 합성되어 AudioTrack으로 재생되고, 다시듣기·워치독이 동작한다.

## 범위
- In: `/llm tts`(단발 JSON, Base64 PCM), AudioTrack 재생, 다시듣기, 워치독 8초, 음질 토글(서버/단말)·속도, 조건부 단말 TTS 폴백.
- Out: speaking 분석(M1-06), 설정 화면의 TTS 설정 UI(M3-09, 컴포넌트는 M0-06 C8).

## 의존성
- Blocked by: M0-07
- Blocks: M1-06

## 수용 기준
- [ ] 상대역 턴 TTS 합성→재생, 다시듣기 동작
- [ ] 워치독 8초 초과 시 폴백/에러 처리
- [ ] 음질 토글(서버/단말)·속도 반영
- [ ] 제공자·음성·로케일은 코드 고정·비노출(tts.md)

## 검증
TTS 재생 스모크 + 워치독 타임아웃 시뮬레이션.
