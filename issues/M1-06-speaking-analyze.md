---
milestone: M1
area: android/backend
size: M
labels: [milestone:M1, area:android, area:backend, ready-for-agent]
blocked_by: [M1-04, M1-05, M0-07]
blocks: [M1-07, M1-08]
---

# [M1-06] 스피킹 분석 (전사 + 한 줄 격려)

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
녹음 후 첫 단계. Gemini 네이티브 오디오로 전사 + 정성 한 줄 격려. **숫자 점수 없음**(R3 위험 설계 회피). transcript는 텍스트로 재사용(중복 인식 제거).

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/design/prompts/speaking-analyze.md](../docs/design/prompts/speaking-analyze.md) · [docs/design/backend-functions.md](../docs/design/backend-functions.md) §4·§8(per-session 캡) · [PRD.md](../PRD.md) §8.2.2, FR-10, R3
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) `session`(분석 결과)

## 목표
녹음 오디오가 `speaking` task로 전사+격려되어 표시되고, transcript가 후속 피드백에 재사용된다.

## 범위
- In: `/llm speaking`(단발, audioBase64), per-session 캡 검증(성공 호출만 카운트), 전사 + 한 줄 격려 표시(숫자 점수 없음), transcript 상태 보관·재사용, (인라인 transcript 수정은 미제공 — 다시말하기/채팅만).
- Out: 슬림 피드백(M1-07), 마이크 4상태(M1-08).

## 의존성
- Blocked by: M1-04, M1-05, M0-07
- Blocks: M1-07

## 수용 기준
- [ ] 오디오→전사+격려 표시, 숫자 점수 노출 0
- [ ] transcript가 M1-07 피드백에 재사용(중복 인식 없음)
- [ ] per-session 캡 검증(무계량 오디오 차단, NFR-2)

## 검증
speaking task 계약 테스트 + 캡 카운트(성공만) 유닛.
