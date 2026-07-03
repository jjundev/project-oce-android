# prototype — 딸깍영어 v1 실현 프로토타입

> 상태: 실현 완료(2026-07-02) · realization-SoT 등록됨 · Compose 구현은 M0
> 권위: [ADR-0006](../docs/adr/0006-prototype-as-realization-sot.md) · [PROVENANCE §2.1](../docs/design/design_system_src/PROVENANCE.md)

문서 6종(F8 골격·컴포넌트 계약·아이콘 매핑 등)을 입력으로 생성된 **standalone HTML 프로토타입** 2개다. 문서 → 생성 번들 → 프로토타입의 하류 산출물이며, **실현된 시각/컴포넌트 외형에 한해서만 정본(realization-SoT)** 이다. 토큰 값·컴포넌트 논거·플로우·IA·접근성 등은 여전히 [`../docs/`](../docs/)의 마크다운 스펙이 소유한다.

각 파일은 브라우저로 직접 열면 되는 자족(self-contained) HTML이다(`__bundler` 런타임이 임베드된 번들/템플릿을 언팩해 렌더).

---

## 파일

| 파일 | 내용 | DS 번들 임베드 |
|---|---|---|
| [`Prototype Flow (standalone).html`](Prototype%20Flow%20(standalone).html) | 온보딩 → 홈 → 대화 → (턴)피드백 → 요약 → 홈 연결 플로우 + 하단 3탭(학습·기록·설정) 화면 + 시나리오(오답·만점·생성실패·오프라인·한도·알림) 토글 + 라이트/다크 + 에러 [A]~[E] 택소노미. 렌더 상태(`level·topic·generating·session·summary·summaryRich·home·history·settings·limit·notif`)가 9개 화면 문서에 각각 대응(= `04-screen-*` `prototype-verified` 마커 근거) | **O** — `DesignSystem_498c1d` 번들(컴포넌트 19종) 임베드 |
| [`Foundations - Scaffold & Icons (standalone).html`](Foundations%20-%20Scaffold%20%26%20Icons%20(standalone).html) | F8 공통 Scaffold 골격 + [01a 아이콘 매핑](../docs/ui/01a-icon-mapping.md) 26 glyph 시각 실현 | **X** — DS 번들 미포함. Material Symbols 폰트로 glyph를 **직접 렌더**(DS `Icon` seam 우회) |

---

## 읽는 이가 유의할 점

- **realization-SoT는 "실현된 것"에만 적용된다.** 산출물의 문서화된 갭(번들 `Icon`의 BLANK seam, 미빌드 컴포넌트 등)은 권위 퇴행이 아니라 **M0 구현 항목**이다. 예: 아이콘 세트는 Material Symbols로 확정([05 F1](../docs/ui/05-open-decisions.md))되어 있고, 실제 vector 임포트만 M0에 남아 있다.
- **렌더된 예시 수치는 데모 placeholder.** 첫완주 보상(20 XP·5분·1일 등) 같은 값은 문서에 확정값으로 고정하지 않는다.
- **번들 임베드 교차링크는 `Prototype Flow`에만 적용된다.** `Foundations`는 번들을 담지 않으므로 §2 번들과 동일 링크를 붙이지 않는다.
- **상충 시 판정:** 시각/외형은 이 프로토타입/번들이 우선, 그 외(논거·상태 축·behavior·a11y·토큰 값)는 스펙-SoT가 우선([ADR-0006](../docs/adr/0006-prototype-as-realization-sot.md) 타이브레이크).

## 관련 문서

- 생성 DS 번들(19종): [`../docs/design_system/`](../docs/design_system/)
- 계보·권위 모델: [PROVENANCE](../docs/design/design_system_src/PROVENANCE.md)
- UI 논의·화면 정본: [`../docs/ui/README.md`](../docs/ui/README.md)
