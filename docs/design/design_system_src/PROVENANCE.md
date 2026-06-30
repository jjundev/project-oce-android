# PROVENANCE — 딸깍영어 디자인 시스템 계보

> **상태:** 정본(이 폴더의 출처·계보 기록) · **개정:** 2026-06-30
> **위치:** `design_system_src/` 폴더 내부(자족 유지). 이전 버전은 삭제된 `docs/design-system/`(Toss 참조)에 있었으나 그 폴더가 제거되어 본 위치로 재작성했다.

---

## 1. 이 폴더(`design_system_src/`)의 위상

`design_system_src/`는 딸깍영어 디자인 시스템의 **자족(self-contained) 스펙이자 SoT**다. 6개 md만으로 디자인 시스템을 빌드할 수 있다(README 참조).

## 2. 생성된 디자인 시스템 (output)

이 스펙을 입력으로 **"claude design"**으로 제품 디자인 시스템 번들이 생성되었다:

- 위치: `docs/design_system/design-system-498c1d19-9547-4cdb-ae4c-3313705391fb/`
- 내용: 토큰 CSS(colors·typography·spacing·radius·motion·fonts)·`_ds_bundle.js`·`_ds_manifest.json`·Pretendard 5종·readme.
- 관계: **스펙이 SoT, 번들은 재생성 가능한 산출물**이다. 스펙이 바뀌면 번들을 다시 생성한다(번들 디렉터리명은 생성 시점마다 달라질 수 있음).
- 검증: 번들 `tokens/motion.css`가 본 스펙 [buildspec](product-design-system-buildspec.md) Part A.2(100/200ms·ease 3종·slot 800/1260·ripple 600·shimmer 1200)와 일치, `colors.css` 주조색 `#39A0ED`(제품 브랜드 블루)로 실체화됨.

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
| 생성 번들(산출물) | `docs/design_system/design-system-498c1d19-…/` |
