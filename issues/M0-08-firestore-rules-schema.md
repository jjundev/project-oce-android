---
milestone: M0
area: backend
size: M
labels: [milestone:M0, area:backend, ready-for-agent]
blocked_by: [M0-02]
blocks: [M1-02, M2-04, M3-04, M3-05]
---

# [M0-08] Firestore 스키마 + 보안 규칙 + TTL

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
확정된 greenfield 스키마·규칙을 배포. 사용자 데이터 격리 + 서버 전용 carve-out(한도 우회 차단).

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/design/firestore-schema.md](../docs/design/firestore-schema.md)(정본, v3.1) · [docs/design/backend-functions.md](../docs/design/backend-functions.md) §11 · [PRD.md](../PRD.md) §10.4, NFR-1 · [ADR-0001](../docs/adr/0001-card-id-determinism.md) · [ADR-0002](../docs/adr/0002-offline-layer-split.md)
- 시각: 해당 없음

## 목표
스키마·보안규칙·인덱스·TTL 정책이 배포되어 클라 default-deny + 서버 전용 컬렉션이 강제된다.

## 범위
- In: `users/{uid}`(profile·saved_cards·gamification progress/studytime·point_ledger·progress_marks·usage) + `sessions`·`idempotency`·`config` 컬렉션, 보안 규칙(usage/config/sessions/idempotency = Admin 전용 carve-out), TTL 2정책(sessions.expiresAt·idempotency.expiresAt), 필요한 인덱스.
- Out: 집계 트리거 로직(M3-05), 이관 함수(M3-03).

## 의존성
- Blocked by: M0-02
- Blocks: M1-02, M2-04, M3-04, M3-05

## 수용 기준
- [ ] 규칙: 사용자별 데이터 격리, `usage`/`config`/`sessions`/`idempotency` 클라 쓰기 불가
- [ ] TTL 2정책 설정 확인
- [ ] 규칙 단위테스트(격리·carve-out) 통과
- [ ] 스키마가 firestore-schema.md v3.1과 일치

## 검증
`firebase emulators:exec`로 규칙 테스트. carve-out 위반 시나리오 거부 확인.
