# PROVENANCE — 딸깍영어 디자인 시스템 계보

> **상태:** 정본(이 폴더의 출처·계보 기록) · **개정:** 2026-07-03
> **위치:** `design_system_src/` 폴더 내부(자족 유지). 이전 버전은 삭제된 `docs/design-system/`(Toss 참조)에 있었으나 그 폴더가 제거되어 본 위치로 재작성했다.
> **권위 모델:** 스펙-SoT ↔ realization-SoT 이원. 토큰·컴포넌트 계약·플로우·a11y·논거는 마크다운 스펙 정본 그래프가 소유하고, **실현된 시각/컴포넌트 외형**은 생성 번들·프로토타입이 사실상 정본(realization-SoT)이다. 결정·타이브레이크 규칙 = [ADR-0006](../../adr/0006-prototype-as-realization-sot.md).

---

## 1. 이 폴더(`design_system_src/`)의 위상

`design_system_src/`는 딸깍영어 디자인 시스템의 **자족(self-contained) 스펙**이며, **비실현 영역(토큰 값·컴포넌트 계약·상태 축·플로우·a11y·논거)의 SoT**다. 6개 md만으로 디자인 시스템을 빌드할 수 있다(README 참조). 단 **실현된 시각/컴포넌트 외형**에 한해서는 생성 번들·프로토타입이 realization-SoT다(§2·[ADR-0006](../../adr/0006-prototype-as-realization-sot.md)).

## 2. 생성된 디자인 시스템 (output)

이 스펙을 입력으로 **"claude design"**으로 제품 디자인 시스템 번들이 생성되었다:

- 위치: `docs/design_system/design-system-498c1d19-9547-4cdb-ae4c-3313705391fb/` (namespace `DesignSystem_498c1d`)
- 내용: 토큰 CSS(colors·typography·spacing·radius·motion·fonts)·`_ds_bundle.js`·`_ds_manifest.json`·Pretendard 5종·readme.
- 관계: 스펙은 **토큰·컴포넌트 계약·논거 등 비실현 영역의 SoT**이며, 스펙이 바뀌면 번들을 다시 생성한다(번들 디렉터리명은 생성 시점마다 달라질 수 있음). 단 **실현된 시각/컴포넌트 외형에 한해서는 이 번들이 realization-SoT**다([ADR-0006](../../adr/0006-prototype-as-realization-sot.md)). 스펙↔번들이 같은 대상에서 상충하면 외형은 번들, 그 외는 스펙이 우선한다.
- 실현 컴포넌트 = **19종**(`_ds_manifest.json`): 스펙 계약 17종 + 빌드 시 승격 2종(`BottomNav`·`FeedbackSheet` — 후자는 `BottomSheet`+`FeedbackSection` convenience composition). `01-foundations.md:90`이 플래그했던 `OneClickBottomNav`는 이 번들에 `BottomNav`로 실현되어 "DS 카탈로그 미비준" 플래그가 해소되었다.
- 문서화된 갭: 번들 `Icon` 컴포넌트는 아직 BLANK 플레이스홀더를 렌더한다(공식 세트는 Material Symbols로 확정, 실제 vector 임포트는 M0). 이는 realization-SoT의 **권위 퇴행이 아니라 M0 구현 갭**이다([05](../../ui/05-open-decisions.md) F1).
- 검증: 번들 `tokens/motion.css`가 본 스펙 [buildspec](product-design-system-buildspec.md) Part A.2(100/200ms·ease 3종·slot 800/1260·ripple 600·shimmer 1200)와 일치, `colors.css` 주조색 `#39A0ED`(제품 브랜드 블루)로 실체화됨.

## 2.1 프로토타입 (`prototype/`, 2026-07-02)

문서 6종(F8 골격·컴포넌트 계약·아이콘 매핑 등)을 입력으로 두 standalone HTML 프로토타입이 생성되었다(문서 → 번들 → 프로토타입의 하류 산출물). 실현된 시각/컴포넌트 외형에 한해 §2 번들과 동일하게 realization-SoT에 포함된다.

| 파일 | 내용 | DS 번들 임베드 |
|---|---|---|
| `Prototype Flow (standalone).html` | 온보딩→홈→대화→(턴)피드백→요약→홈 연결 플로우 + 하단 3탭(학습·기록·설정) 화면 + 시나리오(오답·만점·생성실패·오프라인·한도·알림) 토글로 도달하는 상태(`limit`·`notif` 등) + 라이트/다크 + 에러 [A]~[E] 택소노미. 렌더 상태 전체: `level·topic·generating·session·summary·summaryRich·home·history·settings·limit·notif` → 9개 화면 문서 각각에 대응 상태가 존재(= 04-screen-* `prototype-verified` 마커 전면 적용의 근거, 생략 케이스 없음) | **O** — `DesignSystem_498c1d` 번들(19종) 임베드(=§2 번들과 동일 namespace·컴포넌트셋) |
| `Foundations - Scaffold & Icons (standalone).html` | F8 공통 Scaffold 골격 + 01a 아이콘 매핑표 26 glyph 시각 실현 | **X** — DS 컴포넌트 번들 미포함. 별도 렌더 런타임 + Material Symbols 폰트로 glyph를 **직접 렌더**(DS `Icon` seam 우회) |

- 교차링크(§2 번들 = 임베드 번들)는 **`Prototype Flow`에만** 적용된다. `Foundations`는 번들을 담지 않으므로 동일 링크를 붙이지 않는다.
- 첫완주 보상 수치(20 XP·5분·1일 등) 등 프로토타입 렌더의 예시값은 **데모 placeholder**이며 문서에 확정값으로 고정하지 않는다.

## 3. Toss 참조 (제거됨)

초기 설계 시 **Toss Securities 디자인 시스템**을 *정제감(미니멀리즘) 참조*로 사용했다(옛 `docs/design-system/` 번들). 그 참조 폴더는 현재 **repo에서 제거**되었고, 거기서 발췌한 **대조 키값만** [design-tokens.md](design-tokens.md) 부록 A에 인라인으로 남아 있다(예: 실제 Toss 블루 `#3182F6`).

## 4. 원칙 — Toss-like, not Toss-branded

Toss의 정제감만 차용하고 **Toss 브랜드·금융 문맥·Tossface·금융 방향색(up/down)은 가져오지 않는다.** 제품 주조색은 `#39A0ED`("제품 브랜드 블루")이며 **Toss `#3182F6`과 다른 색**이다(명칭 정정 정본 [ADR-0004](../../adr/0004-brand-color-naming.md)). 이모지는 시스템 이모지(Tossface 미번들).

## 5. SoT 지도

| 영역 | 정본 |
|---|---|
| 토큰 값 | [design-tokens.md](design-tokens.md) |
| 컴포넌트 계약 | [product-design-system.md](product-design-system.md) |
| 상태 축·a11y·시트 IA (mirror) | [foundations.md](foundations.md) (외부 정본은 `../../ux/`) |
| M0 빌드 사양 | [product-design-system-buildspec.md](product-design-system-buildspec.md) |
| **실현 시각/컴포넌트 외형(realization-SoT)** | 생성 번들 + `prototype/Prototype Flow` ([ADR-0006](../../adr/0006-prototype-as-realization-sot.md)) |
| 권위 모델·타이브레이크 | [ADR-0006](../../adr/0006-prototype-as-realization-sot.md) |
| 생성 번들(산출물, 19종) | `docs/design_system/design-system-498c1d19-…/` |
| 프로토타입(산출물) | `prototype/` (§2.1) |
