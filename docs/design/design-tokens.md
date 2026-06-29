# 딸깍영어 v1 — 디자인 토큰 & 테마 설계

> **상태:** 설계 확정(SHIP) · **작성일:** 2026-06-30 · **대상:** PRD §11(디자인/UI 시스템)·NFR-5(접근성)
> **근거:** [PRD.md](../../PRD.md) §11·NFR-5·NFR-9 · [딸깍영어_UIUX_분석_보고서.md](../../딸깍영어_UIUX_분석_보고서.md) · 옛 앱 `colors.xml`/`themes.xml`/`dimens.xml`(`archive/android`)
> **도출 과정:** UI/UX 분석 보고서의 실측 값(라이트/다크 hex·간격·코너 스케일)을 ground truth로, 레거시 오네이밍을 시맨틱 토큰으로 재정의.

---

## 1. 설계 원칙

- **고정 브랜드 팔레트, 다이내믹 컬러(Material You) OFF.** 점수·음성 4상태·스트릭 등 *의미 색*을 기기 벽지에 빼앗기지 않기 위함([PRD.md](../../PRD.md) §11).
- **시맨틱 토큰만 코드에서 참조.** 컴포넌트는 raw hex나 `purple_500` 같은 팔레트 이름을 직접 쓰지 않고 `brand.primary`·`feedback.natural` 같은 의미 토큰만 본다.
- **라이트/다크 완전 토큰셋.** 모든 시맨틱 토큰은 라이트·다크 두 값을 가지며 Compose `lightColorScheme`/`darkColorScheme` + 커스텀 확장에 함께 정의(NFR-5).
- **레거시 오네이밍 전면 폐기.** 옛 테마의 `colorPrimary=purple_500=#EF767A(실제 코랄)`, `colorSecondary=teal_200=#39A0ED(실제 블루)`처럼 이름과 색이 어긋난 네이밍을 시맨틱으로 교정(§3).
- **하드코딩 hex 박멸.** 신규 코드에 리터럴 색상 금지(린트 가드 권장).

---

## 2. 색상 토큰

### 2.1 브랜드 & 표면 (라이트/다크)

| 시맨틱 토큰 | 라이트 | 다크 | 용도 |
|---|---|---|---|
| `brand.primary` | #39A0ED | #39A0ED | 주조색(토스 블루) — CTA·프로그레스·User 말풍선 |
| `brand.primaryPressed` | #2B7FBB | #2B7FBB | 눌림/그라데이션 종점 |
| `surface.background` | #F3F4F6 | #0E0F12 | 최하단 배경 (옛 `color_background_1/3`) |
| `surface.card` | #FFFFFF | #1A1B20 | 카드/표면 (옛 `color_background_2/4`) |
| `text.primary` | #353C45 | #F2F3F5 | 주요 텍스트 |
| `text.secondary` | #676B73 | #A9ADB6 | 보조 텍스트 |
| `text.tertiary` | #8E9399 | #7C818C | 3차 텍스트 |
| `border.hairline` | #E8EAED | #2A2C32 | 1dp 보더(elevation 대체) |

> 라이트 값은 UI/UX 보고서 §3 실측, 다크 값은 동 보고서의 `values-night` 등가 토큰. 깊이는 그림자가 아니라 **배경 레이어 + 1dp 보더**로 표현(elevation 0 기조).

### 2.2 의미 색 (semantic — 다이내믹 컬러로 대체 불가, 라이트/다크 공통 hue)

| 토큰 | 색 | 의미 |
|---|---|---|
| `feedback.natural` (accent / bg) | #009B72 / #E6F5F0 | "자연스러운 표현"(초록) |
| `feedback.correct` (accent / bg) | #EF767A / #FDEEEE | "정확한 표현"(코랄) — *레거시 주조 코랄을 의미 색으로 강등* |
| `voice.ready` | #55606C(중앙)·#E8EAED(외륜) | 음성 4상태 — Ready(회색 동심원) |
| `voice.recording` | #E53935(중앙)·#FCE4EC(외륜) | Recording(빨강 + 리플 3겹) |
| `voice.analyzing` | #6B7684 | Analyzing(블루그레이 + 프로그레스 링) |
| `voice.complete` | #4CAF50 | Complete(초록) |
| `game.streak` | #FF5C00 | 연속학습 불꽃 🔥 |
| `game.saveGold` | #FFC107 | 저장 카드 골드 |
| `state.error` | #E53935 | 에러 게이지/아이콘 |

> 다크에서도 hue는 보존하되 대비가 깨지면 명도만 보정(§5 대비 가드). 그라데이션 카드는 블루 `#39A0ED→#2B7FBB` 135° 선형 유지.

---

## 3. 레거시 네이밍 → 시맨틱 매핑 (마이그레이션 표)

| 옛 이름(폐기) | 실제 색 | v1 시맨틱 토큰 |
|---|---|---|
| `purple_500` (=`colorPrimary`) | #EF767A 코랄 | `feedback.correct.accent` (주조색 아님) |
| `teal_200` (=`colorSecondary`) | #39A0ED 블루 | `brand.primary` |
| `toss_blue`·`primary_blue`·`button_primary` | #39A0ED | `brand.primary` (3중 별칭 통합) |
| `colorPrimaryVariant` | #D35D61 | `feedback.correct` 다크 변형(상태바 전용 폐기) |

원칙: **v1의 주조색은 토스 블루** 하나로 통일하고, 코랄은 "정확한 표현/교정" 의미 색으로만 쓴다. 옛 코드의 코랄=primary 관습은 계승하지 않는다.

---

## 4. 타이포 · 간격 · 형태 스케일

### 4.1 타이포 (Pretendard 전역, sp 단위)

| 토큰 | 크기/굵기 | 용도 |
|---|---|---|
| `type.screenTitle` | 28sp Bold | 화면 제목 |
| `type.scoreDisplay` | 56sp Bold (`brand.primary`) | 요약 종합 점수 |
| `type.dialogHeader` | 22sp Bold | 다이얼로그/시트 헤더 |
| `type.body` | 16sp | 본문·말풍선 |
| `type.sectionLabel` | 14sp Bold | 섹션 라벨 |
| `type.helper` | 13sp | 보조/도움말 |
| `type.tabActive / tabInactive` | 13sp Bold / 11sp Normal | 하단탭(선택 시 굵기+크기 동시 변화) |

- Pretendard Thin~Black 9단계 전 굵기 번들, 전역 `fontFamily` 적용.
- 모든 텍스트 `sp`(접근성 — 다이내믹 타입 대응).

### 4.2 간격 스케일 (4dp 기반)

`space.xs=6 · sm=8 · md=12 · lg=16 · xl=20 · xxl=24 · huge=40` (dp).
관례: 다이얼로그/시트 내부 패딩 24, 섹션 하단 마진 24, 액션 갭 12, 로딩 패딩 40.

### 4.3 코너 반경 스케일

`radius.4 · 8 · 12 · 14 · 16 · 18 · 24 · pill(100)` (dp).

| 토큰 | 적용 |
|---|---|
| `radius.4` | 말풍선 꼬리, 파형 바, 스켈레톤 |
| `radius.8` | 입력 필드 |
| `radius.12` | 기본/보조/전송 버튼, 마이크 배경 |
| `radius.16` | 다이얼로그 카드, 프로그레스 바 |
| `radius.18` | 채팅 말풍선 본체 |
| `radius.24` | 카드·바텀시트·다이얼로그 컨테이너 |
| `radius.pill` | 알약형 칩 |

### 4.4 elevation

기본 **0dp(플랫)**. 유일 예외: 하단 내비게이션 `elevation.nav=8dp`. 그 외 깊이는 `surface` 레이어 + `border.hairline`로 표현.

---

## 5. 접근성 (NFR-5)

- **터치 타깃 ≥48dp**(마이크 버튼 96×96dp 등 주요 행위는 크게).
- **라이트/다크 완전 대응** — 모든 시맨틱 토큰이 양 테마 값 보유.
- **벤다이어그램 런타임 대비 가드:** 좌우 원 색거리 ≥50, 주요텍스트 대비 ≥4.5·보조 ≥3.0을 라이트/다크 양쪽에서 만족하도록 hue/명도 자동 보정(측면 원 alpha 128, 교집합 180). 옛 가드 계승.
- 아이콘 `contentDescription` 필수. **AAA 전수 준수는 비목표**(NFR-5).

---

## 6. 시그니처 인터랙션 (Compose 재구현 — 토큰 소비처)

- **음성 4상태:** §2.2 `voice.*` 색 + 리플 3겹(Recording)·프로그레스 링(Analyzing).
- **실시간 파형(crackle):** 40개 바, 간격/라운드 `radius.4`, 진폭 0~1, 바마다 ±0.3 랜덤 지글거림, 그레이 세로 그라데이션(#9E9E9E→#757575).
- **슬롯머신 카운트업:** 게임화 지표(XP·학습시간), 스프링 반동.
- **점진 스켈레톤(시머):** AI 응답 섹션별 점진 렌더, 로딩 카피 4단계 회전.

---

## 7. 비목표 / 노트

- 다이내믹 컬러(Material You)·다국어 테마·사용자 테마 커스터마이즈 = **v1 제외**.
- 토큰 값은 옛 앱 실측 계승이 1차 — 브랜드 리프레시(팔레트 재정의)는 후속 과제.
- 본 문서는 **토큰 명세**이며, 실제 Compose `Theme`/`ColorScheme` 구현 시 토큰 이름 그대로 매핑할 것.
