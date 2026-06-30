# ADR-0004 — 브랜드 색 명칭 정정 (#39A0ED "토스 블루" → 제품 브랜드 블루)

> 상태: 수용(Accepted) · 작성일: 2026-06-30
> 관련: `PRD.md` §11(`PRD.md:269`) · `docs/design/design_system_src/design-tokens.md` §2.1(:25)·§3(:60,:63)·부록 A · `docs/design/design_system_src/PROVENANCE.md` · `docs/design/design_system_src/product-design-system.md`
> 근거 규칙: "Toss-like, not Toss-branded" — Toss의 정제감은 참고하되 Toss 브랜드/금융 문맥은 가져오지 않는다.

## 맥락

v1 주조색 `#39A0ED`는 `archive` 코드(`teal_200`/`colorSecondary`)와 PRD 본문(`PRD.md:269` "주조색 토스 블루 #39A0ED"), 그리고 `design-tokens.md:25,63`에서 **"토스 블루"로 오칭**돼 왔다. 그러나 실제 Toss 브랜드 블루는 `#3182F6`이다(Toss upstream — 현재 repo 미포함, [PROVENANCE.md](../design/design_system_src/PROVENANCE.md) §3).

제품 색을 "토스 블루"로 부르는 것은 "Toss-like, not Toss-branded" 원칙이 금지하는 **브랜드 누수**이자 사실 오류다. Toss는 정제된 미니멀리즘의 *참조*일 뿐, 제품의 브랜드 정체성이 아니다.

## 결정

`#39A0ED`를 **`brand.primary`(제품 브랜드 블루)**로 명명하고 "토스 블루" 표기를 폐기한다. **색 값(`#39A0ED`)은 불변**이며 **명칭만 정정**한다.

| 항목 | 처리 |
|---|---|
| `design-tokens.md:25`·`:63` "토스 블루" | 동일 라인 내 "제품 브랜드 블루"로 치환(라인 수·값 불변) |
| `design-tokens.md:60` 레거시 별칭 `toss_blue` | **유지** — §3은 옛 이름의 폐기 이력을 기록하는 마이그레이션 표이므로 보존 |
| 실제 Toss `#3182F6` | `design-tokens.md` 부록 A(reference palette)에 "제품 미사용·대조 전용"으로만 기록 |
| `PRD.md:269` 본문 | **개정하지 않음** — 본 ADR로 명칭 정정을 정본 기록(거버넌스: 도큐 → ADR, ADR-0003 선례) |

## 귀결

- 제품 색 명명에서 "토스 블루"가 제거돼 "Toss-like, not Toss-branded" 원칙과 정합한다.
- `#39A0ED`(제품 `brand.primary`)와 `#3182F6`(실제 Toss 블루)의 혼동이 문서 차원에서 종결된다.
- PRD 본문은 v1.0(Confirmed)로 동결 유지하고, deviation/정정은 ADR로 승격하는 기존 거버넌스(ADR-0003)와 일관된다.
- 후속 브랜드 리프레시(팔레트 재정의)는 본 ADR 범위 밖이며 별도 과제다(`design-tokens.md:130`).
