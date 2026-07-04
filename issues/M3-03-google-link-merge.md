---
milestone: M3
area: android/backend
size: M
labels: [milestone:M3, area:android, area:backend, ready-for-agent]
blocked_by: [M3-01, M3-02, M0-07, M3-05]
blocks: []
---

# [M3-03] Google 연결 + 게스트 데이터 이관

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
첫 성공 후 계정 저장 제안. `linkWithCredential` 결과에 따라 인플레이스 승격(FR-3a) 또는 충돌 시 명시 이관(FR-3b)으로 분기.

## SoT (재결정 금지 — 인용만)
- 스펙: [PRD.md](../PRD.md) §9.1 FR-3/3a/3b · [docs/design/firestore-schema.md](../docs/design/firestore-schema.md) §4.4(mergeGuestData) · [docs/design/backend-functions.md](../docs/design/backend-functions.md) §3
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) 저장 제안

## 목표
"Google로 계속하기" 시 신규 신원은 인플레이스 승격(데이터 자동 보존), 복귀 사용자는 saved_cards·gamification을 멱등 머지로 이관한다.

## 범위
- In: Credential Manager + Google ID 로그인, `linkWithCredential`, FR-3a(인플레이스 승격), FR-3b(`credential-already-in-use` → 기존 계정 로그인 + `mergeGuestData` callable 호출 + 게스트 doc 폐기), usage는 이관 안 함. **`mergeGuestData` 콜러블 서버 함수 구현도 포함**(firestore-schema §4.4 정본 — grill 리뷰에서 어느 이슈도 미소유로 확인돼 M3-03 범위로 승격). 서버가 saved_cards union·point_ledger 복사(awardedAt 보존)·studytime 가산·게스트 서브트리+Auth 레코드 삭제를 소유한다.
- Out: 집계 트리거 `onLedgerCreate`(M3-05 소유 — FR-3b 게임화 이관은 이 트리거 배포 후에만 재유도됨, blocked_by 참조), 설정 계정 관리(M3-09).

## 의존성
- Blocked by: M3-01, M3-02, M0-07, **M3-05**(onLedgerCreate — point_ledger 복사가 게임화 progress 를 재유도하려면 필요)
- Blocks: —

## 수용 기준
- [ ] 신규 신원: 인플레이스 승격, 게스트 데이터 자동 보존(FR-3a)
- [ ] 충돌: 기존 계정 로그인 + mergeGuestData 이관 + 게스트 폐기(FR-3b)
- [ ] usage 쿼터 이관 안 함
- [ ] 이관 멱등(중복 적립 없음)

## 검증
FR-3a/3b 분기 계측 테스트(신규/복귀 시나리오, NFR-8 머지 정책).
