# docs — 딸깍영어 v1 문서 인덱스

> 상태: 설계·정책 확정 · Compose 구현 진행전(M0) · 갱신: 2026-07-03
> 범위: PRD v1(핵심 루프 재집중 — 대화 → 피드백 → 요약, 하단 3탭)
> 상위 근거: [`../PRD.md`](../PRD.md) · 실현 산출물: [`../prototype/`](../prototype/)

이 폴더는 딸깍영어 v1의 **설계 정본(스펙-SoT)** 을 담는다. 토큰·컴포넌트 계약·플로우·IA·카피·예외·접근성·논거는 여기 마크다운이 소유하고, **실현된 시각/컴포넌트 외형**만 [`../prototype/`](../prototype/)·생성 번들이 정본(realization-SoT)이다. 권위 모델·타이브레이크 규칙 = [ADR-0006](adr/0006-prototype-as-realization-sot.md).

---

## 하위 폴더 지도

| 폴더 | 내용 | 진입점 |
|---|---|---|
| [`ui/`](ui/) | 화면·시각·인터랙션 수준의 UI 논의·확정(파운데이션 F1~F8 · 공통 컴포넌트 19종 · 시그니처 인터랙션 · 화면 9개 · 정책 결정 18건 · 접근성 규약) | [ui/README.md](ui/README.md) |
| [`ux/`](ux/) | 플로우·IA·카피·예외·분석 이벤트(온보딩·홈·대화·턴 피드백·저장 카드·설정·한도·알림·게임화·접근성·UX writing 등 13+문서) | 각 문서 |
| [`design/`](design/) | 시스템 설계 정본 — 오디오 파이프라인·백엔드 함수·Firestore 스키마·프롬프트 시스템·TTS·토픽 시드, 그리고 디자인 시스템 스펙 소스([`design_system_src/`](design/design_system_src/)) | [design/design_system_src/README.md](design/design_system_src/README.md) |
| [`design_system/`](design_system/) | **생성 산출물** — `claude design`이 스펙에서 만든 DS 번들(토큰 CSS·`_ds_bundle.js`·Pretendard·컴포넌트 19종). realization-SoT | [design_system/…/readme.md](design_system/design-system-498c1d19-9547-4cdb-ae4c-3313705391fb/readme.md) |
| [`adr/`](adr/) | 아키텍처 결정 기록 6건(cardId 결정성 · 오프라인 레이어 분리 · 카운트업 재배치 · 브랜드 색 명칭 · 로딩≠복습 퀴즈 · 프로토타입 realization-SoT) | [adr/](adr/) |
| [`agents/`](agents/) | 에이전트 작업 규약 — 이슈 트래커·트리아지 라벨·도메인 문서 | [agents/domain.md](agents/domain.md) |

---

## 권위 모델 (SoT)

문서 → 생성 번들 → 프로토타입의 하류 관계이며, 정본은 영역별로 나뉜다(anti-drift ownership 그래프).

| 영역 | 정본 |
|---|---|
| 토큰 값 | [design/design_system_src/design-tokens.md](design/design_system_src/design-tokens.md) |
| 컴포넌트 계약·상태 축 | [design/design_system_src/product-design-system.md](design/design_system_src/product-design-system.md) / [foundations.md](design/design_system_src/foundations.md) |
| 컴포넌트 결정·논거 | [ui/02-shared-components.md](ui/02-shared-components.md) |
| 플로우·IA·카피·예외 | [ux/](ux/) |
| 접근성 | [ux/accessibility.md](ux/accessibility.md) |
| **실현 시각/컴포넌트 외형** | 생성 번들 + [`../prototype/Prototype Flow`](../prototype/) (realization-SoT, [ADR-0006](adr/0006-prototype-as-realization-sot.md)) |
| 권위 모델·타이브레이크 | [ADR-0006](adr/0006-prototype-as-realization-sot.md) · [PROVENANCE](design/design_system_src/PROVENANCE.md) |

> 산출물(번들/프로토타입)과 스펙이 같은 대상에서 상충하면: **시각/외형은 번들 우선, 그 외(논거·상태 축·behavior·a11y·토큰 값)는 스펙 우선.**

---

## 다음 착수 후보

**v1 Compose 프로젝트 스캐폴딩(M0)** → F1 실제 vector 임포트(매핑표 [ui/01a-icon-mapping.md](ui/01a-icon-mapping.md)) → F2 토큰→Compose 매핑 반입 → 화면 9개 Compose 구현(설계 rev2 확정 완료). 상세 현황은 [ui/README.md §0.2](ui/README.md) 참조.
