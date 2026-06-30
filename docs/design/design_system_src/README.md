# 딸깍영어 디자인 시스템 (`design_system_src/`)

> **이 폴더만으로 디자인 시스템을 빌드할 수 있다.** 빌드에 필요한 모든 사양(토큰 값·컴포넌트 계약·상태 축·접근성·시트 IA·Theme API)이 폴더 안에 있다. 외부 링크는 *참고(provenance)* 용이며 빌드에 필수가 아니다.

## 읽는 순서

1. **[README.md](README.md)** — (이 문서) 진입점·구조.
2. **[foundations.md](foundations.md)** — 상태 축·접근성 기준치·시트 IA·provenance (외부 사양의 인폴더 mirror).
3. **[design-tokens.md](design-tokens.md)** — 색·타이포·간격·반경·모션 *값* SoT (+ 부록 A reference palette·B Compose 매핑).
4. **[product-design-system.md](product-design-system.md)** — 컴포넌트 *계약*(외형·상태·아이콘·QA) SoT.
5. **[product-design-system-buildspec.md](product-design-system-buildspec.md)** — "그대로 빌드 가능"을 위한 8개 갭 해소(다크값·모션 토큰·line-height·풀스펙·anatomy·Theme API).
6. **[product-design-system-pilot.md](product-design-system-pilot.md)** — 첫 QA 파일럿(대화학습+턴피드백 시트 컴포넌트×상태 매트릭스).

## 자족 경계

- **인폴더(빌드 필수):** 위 6개 md 전부. Compose/M3 라이브러리 의존은 정상.
- **외부 정본(참고용 · mirror 원본):** `../../ux/dialogue-learning-flow.md`(상태 축)·`accessibility.md`(a11y)·`turn-feedback-ia.md`(시트 IA). foundations.md가 이들의 빌드필수 부분집합을 mirror하며, 상충 시 정본이 우선한다(foundations §6 동기화).
- **출처·계보:** [PROVENANCE.md](PROVENANCE.md)(본 폴더) — 스펙→생성 번들 관계, Toss 참조(제거됨) 기록.
- **M0 잔여:** 비파일럿 컴포넌트 풀스펙(스텁→채움)·아이콘 세트 선정·폰트/아이콘 바이너리 조달·Compose/Theme/lint 구현.

## 빌드 진입점 (M0)

`product-design-system-buildspec.md` Part D(Theme API)를 시작점으로 `OneClickTheme` + `OneClickColors/Typography/Shapes/Motion`을 구현하고, `design-tokens.md` 부록 B 매핑으로 M3 `ColorScheme`에 싣는다. 컴포넌트는 `product-design-system.md`(계약) + `foundations.md`(상태 축·a11y)로 구현한다.
