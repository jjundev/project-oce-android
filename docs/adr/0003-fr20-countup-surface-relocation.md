# ADR-0003 — FR-20 카운트업 surface 재배치 (대시보드/홈 → 완주·기록)

> 상태: 수용(Accepted) · 작성일: 2026-06-30
> 관련: `PRD.md` FR-20(`PRD.md:205`) · `docs/design/design_system_src/design-tokens.md` §6(:122) · `docs/ux/gamification-emphasis.md` §6 · `docs/ux/home-learning-entry.md` §5.2
> 근거 규칙: `PRD.md` "충돌 시 설계 문서가 우선한다"(`PRD.md:262`)

## 맥락

PRD FR-20(`PRD.md:205`)은 "대시보드 지표는 슬롯머신 카운트업으로 연출한다"를 규정하며, 명명된 surface는 "대시보드"(= 홈)뿐이다. 그러나 v1 게임화 정책은 "조용한 동기 + 완주 순간 보상 집중" 모델을 채택했다(`gamification-emphasis.md` §1). 홈은 학습 시작을 최우선으로 지원하는 허브이며(`home-learning-entry.md` §1), 강한 보상 연출은 완주의 특별함을 보존하기 위해 한 곳에 집중해야 한다.

`design-tokens.md:122`는 카운트업의 *대상 지표*(XP·학습시간, 그리고 완주 한정 streak)만 규정하고 *surface*는 규정하지 않는다. 이 사실은 surface 이동을 *허용*할 뿐 *근거*가 되지는 않는다 — 완주·기록 배치는 UX 정책의 신규 결정이며 PRD 본문(FR-20)과 표면상 충돌한다.

## 결정

슬롯머신 카운트업 연출을 **홈(대시보드)에서 완주 보상 surface와 기록 탭으로 재배치**한다.

| surface | 카운트업 |
|---|---|
| 완주 보상(요약 라우트 진입) | 적용 — XP·학습시간·streak(완주 한정) 롤업 |
| 기록 탭 상단 통계 | 적용 — 세션당 최초 진입 1회만 |
| 홈(재방문) | **미적용** — 정적 2지표(학습시간 ≥ streak), XP 백스테이지 |

- PRD 본문(FR-20)은 개정하지 않고, 본 ADR로 deviation을 정본 기록한다.
- `design-tokens.md` §6은 카운트업 대상에 "완주 surface 한정 streak"를 포함하도록 정합화한다(본 ADR과 함께).

## 귀결

- `gamification-emphasis.md` §6의 "FR-20 재해석 — 필요 시 ADR로 승격" 항목이 본 ADR로 해소된다(UX 문서 내 "정본 주석"에서 ADR 거버넌스로 승격).
- CLAUDE.md의 ADR 기록 의무(`docs/adr/`)를 PRD-deviation 결정에 대해 충족한다.
- 홈을 정적으로 두는 결정은 `home-learning-entry.md` §5.2 / `gamification-emphasis.md` §6와 정합한다.
