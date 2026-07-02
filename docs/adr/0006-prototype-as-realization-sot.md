# ADR-0006 — 프로토타입/번들 = 실현 산출물 정본 (realization-SoT), 범위 한정

> 상태: 수용(Accepted) · 작성일: 2026-07-03
> 관련: `prototype/` · `docs/design/design_system_src/PROVENANCE.md` §2·§2.1 · `docs/ui/README.md` · PRD v1

## 맥락

2026-07-02 문서(6종 스펙 + UI 논의 문서)를 입력으로 두 산출물이 생성되었다: 생성 DS 번들(`DesignSystem_498c1d`, 컴포넌트 19종)과 두 standalone 프로토타입 HTML. 이들은 문서의 **하류 실현체**다(문서 → 번들 → 프로토타입).

이 산출물이 생기면서 "이제 무엇이 정본인가"를 정해야 했다. 사용자는 "프로토타입/번들을 정본으로 승격"을 선택했다. 그러나 `docs/`에는 **131개의 "정본" 상호참조**가 있어, 토큰(→`design-tokens.md §6`)·플로우(→`ux/*`)·a11y(→`accessibility.md`)·상태 축/behavior·컴포넌트 논거(→`02-shared-components.md`)의 소유를 나눈 anti-drift ownership 그래프를 형성한다. 프로토타입은 rationale·상태 축·a11y 결정표를 **소유할 수 없으므로**, "번들=정본"을 문자 그대로 전역 적용하면 이 아키텍처가 붕괴한다.

## 결정

**"프로토타입/번들 = 정본"은 실현된 시각/컴포넌트 외형에 한정해 적용한다(realization-SoT). 그 외 전 영역은 기존 마크다운 스펙 정본 그래프가 소유를 유지한다.**

| 영역 | 정본 |
|---|---|
| 실현된 **시각/컴포넌트 외형**(렌더 결과·컴포넌트 실재·형태) | **생성 번들 + `prototype/Prototype Flow`** (realization-SoT) |
| 토큰 값 | `design-tokens.md` (§6 = 모션 정본) |
| 컴포넌트 계약·상태 축 | `product-design-system.md` / `foundations.md` |
| 컴포넌트 결정·논거 | `02-shared-components.md` |
| 플로우·IA·카피·예외 | `ux/*` |
| a11y | `accessibility.md` |

### 타이브레이크

산출물(번들/프로토타입)과 정본 rationale 문서가 **같은 referent에서 상충**하면:

- **시각/외형**(예: 컴포넌트 렌더 형태, 실재 여부) → **번들이 우선**한다.
- **그 외**(rationale·상태 축·behavior·a11y·토큰 값) → **스펙-SoT가 우선**한다.

### 문서화된 갭 ≠ 권위 퇴행

realization-SoT는 **시연·실현된 것에 한해서만** 정본이다. 산출물에 존재하는 문서화된 갭(번들 `Icon`의 BLANK seam, 미빌드 C-series 컴포넌트 등)은 realization-SoT의 권위 퇴행이 아니라 **M0 구현 항목**이다. 예: 아이콘 세트는 Material Symbols로 확정([05](../ui/05-open-decisions.md) F1)되어 있고, 번들 `Icon`이 아직 BLANK인 것은 vector 임포트가 M0에 남아 있다는 뜻이지 "미정"으로의 회귀가 아니다.

## 대안과 기각 사유

- **번들 전역 정본(리터럴 역전):** 131개 "정본" 참조와 `design_system_src` 전체를 "파생"으로 재작성해야 하고, 프로토타입이 소유할 수 없는 rationale·상태 축·a11y까지 강제 이관하게 된다. 아키텍처 파괴 + 사용자 의도와 불일치로 기각.
- **문서 전역 정본 유지(현상 유지):** 실현 산출물이 생겼는데도 정본으로 등록하지 않으면, 실제 렌더와 문서가 어긋날 때 판정 기준이 없다. 사용자 결정(번들 승격)과 배치되어 기각.
- **ADR 미승격(PROVENANCE 각주로만):** 권위 모델 변경은 ADR-0004(브랜드 색 명칭)·ADR-0005(로딩 vs 복습 퀴즈)와 동급의 "무엇이 우선인가" 결정이다. 리포 관행상 ADR이 적절하여 각주 대안 기각.

## 귀결

- `PROVENANCE.md` §1·§2가 이원 권위 모델로 개정되고, §2.1이 프로토타입 2파일을 등록한다(`Prototype Flow`=번들 임베드, `Foundations`=비-DS-번들 렌더 + Material Symbols 직접 렌더).
- 131개 "정본" 상호참조는 **대량 재작성하지 않는다**(각 정본은 자기 소유 영역에서 유효 유지).
- 산출물↔문서 충돌 시 위 타이브레이크로 판정하고, 재발 시 본 ADR을 참조한다.
