# 딸깍영어 v1 — 구현 이슈 인덱스 (M0~M4)

> 상태: 계획 확정(grill-yourself → grill-review --deep auto SHIP) · 작성 2026-07-03
> 범위: PRD.md §14 마일스톤 M0~M4의 태스크 단위 분해 (37 이슈 + 본 인덱스)
> 상위 근거: [../PRD.md](../PRD.md) · 설계 정본 [../docs/](../docs/) · 시각 정본 [../prototype/](../prototype/)

## 트래커 이탈 고지 (중요)

**정본 트래커는 GitHub Issues(`gh`)** 다 — [CLAUDE.md](../CLAUDE.md) L7, [docs/agents/issue-tracker.md](../docs/agents/issue-tracker.md).
본 `issues/*.md`는 **승격 전 계획 백로그**이며, `/to-issues`로 GitHub Issues화한다.
라벨(`milestone:*`·`area:*`·트리아지)은 각 파일 YAML 프론트매터 + 아래 표에 보관하되, gh 승격 시 `gh issue edit --add-label`로 실효화된다. 로컬 파일 자체는 트리아지 대상이 아니다.

## 권위 모델 (재litigation 금지)

각 이슈는 **설계를 재결정하지 않고 SoT를 인용만** 한다.
- **behavior·토큰 값·논거·상태축·플로우·IA·a11y** → `docs/` 스펙-SoT.
- **실현된 시각/컴포넌트 외형** → `prototype/` + 생성 DS 번들 (realization-SoT, [ADR-0006](../docs/adr/0006-prototype-as-realization-sot.md)).
- 상충 시: 시각/외형은 프로토타입 우선, 그 외는 스펙 우선.
- UI 이슈는 SoT 링크에 **스펙 문서 + 프로토타입 렌더 상태**를 둘 다 건다.

프로토타입 렌더 상태 ↔ 화면 대응(시각 정본): `level·topic·generating`=온보딩 / `home`=홈 / `session`=대화 / (turn feedback)=피드백 시트 / `summary·summaryRich`=요약 / `history`=기록 / `settings`=설정 / `limit`=한도 / `notif`=리마인더. 골격·아이콘은 `Foundations - Scaffold & Icons`.

## 라벨 범례

- `milestone:M0`~`M4` — 릴리즈 단계(PRD §14).
- `area:android` / `area:backend` / `area:design` — 워크스트림.
- 트리아지: `ready-for-agent` 기본(설계 확정, 구현 성격).

## 이슈 목록 + 의존성 (전 37개 Blocked-by)

> "—" = 선행 없음(마일스톤 시작 시 병렬 착수 가능).

### M0 — 기반
| 이슈 | area | size | Blocked by |
|---|---|---|---|
| [M0-01 compose-scaffold](M0-01-compose-scaffold.md) | android | M | — |
| [M0-02 firebase-project-wiring](M0-02-firebase-project-wiring.md) | android | S | M0-01 |
| [M0-03 design-tokens-theme](M0-03-design-tokens-theme.md) | design/android | M | M0-01 |
| [M0-04 icon-vectors](M0-04-icon-vectors.md) | design/android | S | M0-01 |
| [M0-05 core-components](M0-05-core-components.md) | android | **L** | M0-03, M0-04 |
| [M0-06 product-components](M0-06-product-components.md) | android | **L** | M0-03, M0-04 |
| [M0-07 backend-scaffold-llm-proxy](M0-07-backend-scaffold-llm-proxy.md) | backend | M | — |
| [M0-08 firestore-rules-schema](M0-08-firestore-rules-schema.md) | backend | M | M0-02 |
| [M0-09 app-shell-nav](M0-09-app-shell-nav.md) | android | M | M0-01, M0-03 |

### M1 — 핵심 루프
| 이슈 | area | size | Blocked by |
|---|---|---|---|
| [M1-01 dialogue-sse-client](M1-01-dialogue-sse-client.md) | android | M | M0-09, M1-02 |
| [M1-02 be-dialogue-task](M1-02-be-dialogue-task.md) | backend | L | M0-07, M0-08 |
| [M1-03 dialogue-turn-ui](M1-03-dialogue-turn-ui.md) | android | M | M0-09, M0-05, M0-06 |
| [M1-04 audio-record](M1-04-audio-record.md) | android | M | M0-01 |
| [M1-05 tts-playback](M1-05-tts-playback.md) | android/backend | M | M0-07 |
| [M1-06 speaking-analyze](M1-06-speaking-analyze.md) | android/backend | M | M1-04, M1-05, M0-07 |
| [M1-07 slim-feedback](M1-07-slim-feedback.md) | android/backend | M | M1-06, M1-02 |
| [M1-08 mic-4state-statehold](M1-08-mic-4state-statehold.md) | android | M | M1-04, M1-06, M1-07 |
| [M1-09 dev-harness](M1-09-dev-harness.md) | android | S | M0-09 |

### M2 — 요약 & 저장
| 이슈 | area | size | Blocked by |
|---|---|---|---|
| [M2-01 be-summary-orchestration](M2-01-be-summary-orchestration.md) | backend | M | M0-07 |
| [M2-02 summary-screen](M2-02-summary-screen.md) | android | M | M2-01, M1-07 |
| [M2-03 deep-feedback-ondemand](M2-03-deep-feedback-ondemand.md) | android | M | M1-07 |
| [M2-04 saved-cards](M2-04-saved-cards.md) | android | M | M0-08, M2-02 |
| [M2-05 history-tab](M2-05-history-tab.md) | android | M | M2-04 |

### M3 — 온보딩 & 인증 & 한도
| 이슈 | area | size | Blocked by |
|---|---|---|---|
| [M3-01 guest-start](M3-01-guest-start.md) | android | S | M0-02 |
| [M3-02 onboarding-first-session](M3-02-onboarding-first-session.md) | android | M | M3-01, M1-07 |
| [M3-03 google-link-merge](M3-03-google-link-merge.md) | android/backend | M | M3-01, M0-07 |
| [M3-04 daily-limit](M3-04-daily-limit.md) | android/backend | M | M0-08, M0-06 |
| [M3-05 gamification](M3-05-gamification.md) | android/backend | L | M0-08, M2-04 |
| [M3-06 countup-integration](M3-06-countup-integration.md) | android | S | M3-05, M0-06 |
| [M3-07 local-reminder](M3-07-local-reminder.md) | android | M | M0-06 |
| [M3-08 home-entry](M3-08-home-entry.md) | android | L | M1-07, M3-04, M3-05, M0-06 |
| [M3-09 settings](M3-09-settings.md) | android | L | M3-05, M3-07, M0-06 |

### M4 — 마감 (횡단)
| 이슈 | area | size | Blocked by |
|---|---|---|---|
| [M4-01 analytics-instrumentation](M4-01-analytics-instrumentation.md) | android | M | M1~M3 기능 표면 |
| [M4-02 accessibility-QA-audit](M4-02-accessibility-QA-audit.md) | android | M | M0~M3 UI 이슈 |
| [M4-03 error-empty-loading](M4-03-error-empty-loading.md) | android | M | M0-05, M0-06 |
| [M4-04 offline-support](M4-04-offline-support.md) | android | M | M2-04, M3-05 |
| [M4-05 qa-beta](M4-05-qa-beta.md) | android/backend | L | M4-01~04 |

## 임계 경로 (요약)

- M0-01 → (03·04 → 05·06), (02 → 08), 07(독립), 09
- M0-09/07/08 → M1-02 → M1-01 ; M1-04 → M1-06 → M1-07 → M1-08
- M1-07 → M2-02/03 ; M2-01 → M2-02 ; M2-04 → M2-05
- M2 스키마 → M3-05 ; M3-01 → M3-02/03 ; M3-04/05 → M3-08(홈, **M1-09 하니스 폐기**)
- 전 기능 → M4 횡단
- **하니스 교체 엣지**: M1-09 → M3-08 (사용자 대면 홈 진입이 하니스를 제거).

## 문서 정합 메모

- 신규 공통 컴포넌트 카탈로그 정본 = **C1~C20**([02-shared-components.md](../docs/ui/02-shared-components.md), 20행). `docs/ui/README.md` L42·161의 stale "C1~C19/19종"은 2026-07-03 C1~C20/20종으로 정정 완료. (README L64 "디자인시스템 19종"은 DS 번들 프리미티브 참조로 정상.)
