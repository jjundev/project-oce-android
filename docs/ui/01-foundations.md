# UI 논의 — 파운데이션 & 시스템 공통

> 상태: 논의용 스켈레톤 · 작성일: 2026-06-30 · 갱신: 2026-07-03(프로토타입 실현 반영) · 상위: [README](README.md) (표 1)
> 정본: [design-tokens.md](../design/design_system_src/design-tokens.md) · [product-design-system.md](../design/design_system_src/product-design-system.md) · [accessibility.md](../ux/accessibility.md)
> 범례: 🔴 결정 필요 · 🟠 신규 설계 · 🟡 구현 대기 · 🟢 QA·위임

## 표

| ID | 논의 항목 | 상태 | 쟁점 |
|---|---|:--:|---|
| F1 | 공식 아이콘 세트 선정 | 🔴 | BLANK 플레이스홀더 → M0 블로커 |
| F2 | 토큰 → Compose 테마 매핑 | 🟡 | **명세 확정**(grill-review deep auto SHIP) · Compose 구현만 M0 |
| F3 | 비파일럿 컴포넌트 "TBD 스텁" | 🟠 | 10종 빈 계약 채우기 |
| F4 | 화면 전환·모션 + reduce-motion | 🟠 | 전역 전환 패턴 + 정적 대체 |
| F5 | fontScale 1.3× 무파손 | 🟢 | 구현 후 실측 게이트 |
| F6 | 다크모드 화면별 대비 검증 | 🟢 | 양 테마 대비 |
| F7 | Compose Semantics/스크린리더 | 🟢 | 신규 기술 결정 |
| F8 | 화면 레이아웃 파운데이션 | 🟠→✅ | **명세 확정**(rev2 · 9개 화면 공통 골격·거터·내비·타이틀·스크롤) |

---

## 논의 (항목별)

### F1 · 공식 아이콘 세트 선정 🔴
- **현황:** `Icon` 컴포넌트가 BLANK 플레이스홀더(glyph box만 예약). 모든 호출부는 의미 name(`mic`·`bookmark`·`speaker`·`chevron_right`…)을 이미 전달 → 교체는 `Icon.jsx`(Compose에선 단일 Icon 컴포저블) 한 곳. Toss 참조 Heroicons-solid는 웹 번들 자산이라 제품 미반입.
- **쟁점:** 24-grid solid 세트를 어디서 조달(라이브러리 vs 자체 제작)? 라이선스? 필요 glyph 전수 목록(내비 3·음성·표현·액션·게임화)?
- **선택지:** _(논의)_
- **제안:** _(논의)_
- **결정:** _(미정)_

### F2 · 디자인 토큰 → Compose 테마 매핑 🟡 · [명세 확정]
> 상태: **명세 확정**(grill-yourself → grill-review deep auto, rev.3 SHIP · 2026-07-01) · Compose 코드/gradle/에셋 반입은 M0.
> 값 정본: [design-tokens.md](../design/design_system_src/design-tokens.md)(§2~6·부록 B·C) · [buildspec Part A/D](../design/design_system_src/product-design-system-buildspec.md) · 번들 [colors.css](../design_system/design-system-498c1d19-9547-4cdb-ae4c-3313705391fb/tokens/colors.css).

- **현황:** 컬러(라이트/다크)·타이포·간격·코너·모션 값 확정. 웹은 CSS 변수, 생성 번들(`docs/design_system/.../tokens/*.css`) 존재. 신규 Compose 프로젝트는 **아직 부재**(비아카이브 `.kt` 0개) → F2는 문서 산출물, M0가 모듈 스캐폴딩부터 선행.

#### 결정 (rev.3 확정)

1. **테마 진입점 `OneClickTheme`** — `MaterialTheme`를 감싸 고정 `light/darkColorScheme`(다이내믹 컬러 OFF, Android 12+ 분기 없음)을 싣고, `colors·typography·shapes·motion·spacing`을 **전부 CompositionLocal-backed**로 provide. 접근자 `object OneClickTheme { val …: @Composable get() = Local*.current }`. (buildspec Part D:144-148 정합)
2. **`OneClickTheme(darkTheme = isSystemInDarkTheme())`** — 라이트/다크는 color provider만 스왑, 타이포/형태/모션/간격은 테마 불변 단일 인스턴스.
3. **M3 슬롯 매핑 — 8개 semantic 토큰 → 10개 슬롯 write:** `brand.primary→primary`, `#FFFFFF→onPrimary`, `surface.background→background`, `surface.card→surface`, `text.primary→onBackground`+`onSurface`(2개), `text.secondary→onSurfaceVariant`, `border.hairline→outlineVariant`, `state.error→error`, `#FFFFFF→onError`. **나머지 ~38 슬롯은 M3 default 유지**(값 발명 금지). `brand.primaryPressed`·`text.tertiary`는 M3 슬롯 아님 → `OneClickColors`.
4. **`OneClickColors`**(`@Immutable data class` + `staticCompositionLocalOf`) — 슬롯 없는 의미색: `primaryPressed·textTertiary·borderStrong·scrim·feedbackNatural/Correct(accent·bg)·voice 4상태·gameStreak·gameSaveGold·gradientStart/End·waveformTop/Bottom`. 헬퍼 `brandGradient()`(135° 선형)·`waveformGradient()`(세로). 파형 **렌더/anatomy는 I2**, F2는 색토큰+Brush까지.
5. **타이포 `OneClickTypography` — 9개 TextStyle**(`screenTitle·scoreDisplay·turnScore·dialogHeader·body·sectionLabel·helper·tabActive·tabInactive`; tabActive 13sp Bold ≠ tabInactive 11sp Normal 분리). lineHeight/letterSpacing은 buildspec A.3 주입(`(-0.02).em`). turnScore≠screenTitle 시맨틱 분리(둘 다 28sp).
6. **폰트** — `FontFamily(Pretendard 400/500/600/700/800)`. **아카이브 `.otf` 재사용**: `archive/android/app/src/main/res/font/pretendard_*.otf` 5웨이트를 신모듈 `res/font/`로 복사(`pretendard.xml` 참조). **woff2 변환 불필요.**
7. **형태 `OneClickShapes` — 8단 전부**(`4/8/12/14/16/18/24/pill=100`). 앱 컴포넌트는 `OneClickTheme.shapes`만 소비. M3 `Shapes` 브릿지(small12/med16/large24)는 **M3 제공 컴포넌트(ModalBottomSheet 등) 전용**. `radius.18`(채팅 말풍선)은 OneClickShapes 직접 read(M3 large 금지).
8. **간격 `OneClickSpacing`** — 7단(xs6~huge40) + 시맨틱 별칭(`sheetPadding24·sectionGap24·actionGap12·loadingPadding40`).
9. **모션 `OneClickMotion`** — durations(fast100/base200)·easings(standard/out)·spring(damping .55)·slotMachine(fast800/total1260/snap .92→1)·ripple600×3·shimmer1200. (buildspec A.2)
10. **elevation `OneClickElevation`** — default 0.dp, nav 8.dp.
11. **hex 하드코딩 가드** — **detekt 커스텀 룰**[confirmed]: `Color(0x…)`·`#RRGGBB` 리터럴 플래그, allowlist = `ui/theme/Color.kt` 단일 경로. 활성화 M0.
12. **파일 레이아웃** — `ui/theme/`: `Color.kt`(raw palette private)·`OneClickColors.kt`·`Type.kt`·`Shape.kt`·`Spacing.kt`·`Motion.kt`·`Elevation.kt`·`Theme.kt`.
13. **라이브러리 버전**[confirmed] — M0 스캐폴딩 시점 **최신 안정 Compose BOM + Material3**. 명세 기준일 뿐, 실제 핀은 M0 gradle.

- **선행조건(M0 전):** `design-tokens.md`에 `border.strong`·`surface.overlay-dim`·`waveform` 3토큰 부재 → **부록 C로 추가 완료**(줄 보존 규칙, 파일 말미 append). 값 출처 = [colors.css:38,48,72-73](../design_system/design-system-498c1d19-9547-4cdb-ae4c-3313705391fb/tokens/colors.css)(라이트) / :79,86(다크).
- **스코프 경계:** 다크 대비 실측(F6)·Venn 런타임 보정(I4)·컴포넌트 외형 상태축(F3)·파형 렌더(I2)는 F2 밖. F2 = 토큰→테마 바인딩 + Brush 헬퍼만.

### F3 · 비파일럿 컴포넌트 "TBD 스텁" 채우기 🟠
- **현황:** [buildspec.md:91](../design/design_system_src/product-design-system-buildspec.md) — IconButton·Card·ListRow·Input·Switch·SegmentedControl·Badge·BottomSheet·RewardStrip·SavedCard는 헤더만 있는 **빈 계약(값 TBD)**. 기본값 출처 = M3 default + 토큰. M0에서 채움.
- **쟁점:** 각 컴포넌트 anatomy(치수·상태·패딩) 확정. M3 기본을 어디까지 따르고 어디서 토큰 override?
- **선택지 / 제안 / 결정:** _(논의)_

### F4 · 화면 전환·모션 패턴 + reduce-motion 대체 🟠
- **현황:** 모션 토큰(duration·ease) 확정. 레거시는 300ms 슬라이드. accessibility #9·#11: reduce-motion 시 슬롯→즉시 스냅, 시머→정적, 리플 3겹/파형 crackle→축소, 전환→크로스페이드/즉시.
- **쟁점:** 전역 Nav 전환 패턴(슬라이드 방향·duration) 확정. `ANIMATOR_DURATION_SCALE=0` 감지 방식.
- **선택지 / 제안 / 결정:** _(논의)_

### F5 · fontScale 1.3× 무파손 게이트 🟢
- **현황:** accessibility #19 — 1.3× 무파손이 QA 합격선, 1.3~2.0× best-effort(스크롤). 텍스트 sp, 커스텀뷰(파형·마이크 96dp) dp 고정. 클리핑 금지·리플로우.
- **쟁점:** (사전 보장 아님) 각 화면 구현 후 실측. 체크리스트화 필요.
- **결정:** 구현+QA 위임.

### F6 · 다크모드 화면별 대비 검증 🟢
- **현황:** 다크 토큰셋 존재(`[data-theme="dark"]`). 벤·그라데이션 카드 흰텍스트 ≥4.5 요구.
- **쟁점:** 화면별 라이트/다크 실측 체크리스트.
- **결정:** 구현+QA 위임.

### F7 · Compose Semantics API / 스크린리더 경로 🟢
- **현황:** accessibility #3·#12 — announce·`LocaleList`(영어 발음)·focus 관리는 신규 기술 결정. 안정 Compose에 로케일 시맨틱 노드 없음 → 텍스트 레벨 `LocaleList`/`LocaleSpan`. 한국어 TalkBack 영어 발음은 통합 후 QA 실측.
- **쟁점:** 구현 API 확정 시점(빌드). 실측 검증 항목.
- **결정:** 구현+QA 위임.

### F8 · 화면 레이아웃 파운데이션 🟠 · [rev2 확정]
> 상태: **명세 확정**(grill-yourself → grill-review deep auto, rev2 SHIP · Blocker 0 · 2026-07-01) · 9개 화면 공통 골격 · Compose 구현 M0.
> 정본: 값 = [tokens/*.css](../design_system/design-system-498c1d19-9547-4cdb-ae4c-3313705391fb/tokens/) · 컴포넌트 계약 = [product-design-system.md](../design/design_system_src/product-design-system.md). 화면별 반영은 [04-screen-*](README.md) 참조.

- **현황:** 토큰·컴포넌트 계약 확정. 화면 레벨 골격/스크롤 아키텍처는 SoT(화면 문서)에 부재였음 → rev2가 신규 명세(작성자 제안, 코드 근거 검증됨). 신규 Compose 프로젝트 부재 → 문서 산출물, M0가 스캐폴딩부터 선행.

#### 결정 (rev2 확정)

1. **골격·거터** — Compose `Scaffold`(topBar?/bottomBar?/content). 화면 가로 거터 20dp(`space-xl`), 섹션 세로 갭 24dp(`space-section-gap`), 액션 버튼 갭 12dp(`space-action-gap`), 로딩 영역 40dp(`space-loading-padding`). elevation 0 + `border.hairline` 기조.
2. **BottomNav 노출 범위** — 학습·기록·설정 3탭에만 `bottomBar`(첫 탭 라벨은 `학습`; `Foundations` 프로토타입 스캐폴드 캡션 "하단 3탭 · 학습·기록·설정" 기준). 온보딩·대화·피드백시트·요약·한도게이트·리마인더는 내비 없는 전체화면/오버레이. 컴포넌트 = M3 `NavigationBar` 래핑 + type token(`tabActive` 13sp Bold / `tabInactive` 11sp), `elevation-nav`(상단 hairline + 상승 그림자 오프셋, `radius.css` `--elevation-nav`; M3 elevation dp 값 아님). ~~`OneClickBottomNav`는 DS 카탈로그 미비준 → 신규 컴포넌트 필요(플래그).~~ → **해소: 생성 번들이 `BottomNav`를 실현·export**(`_ds_manifest.json`, 19종)하여 카탈로그에 편입됨.
3. **타이틀 패턴** — 상시탭 = 인라인 대형 타이틀 `type.screenTitle`(28sp Bold); 플로우 = 48dp 최소 top bar(뒤로/닫기 + 진행률); 시트 = 드래그핸들 + `type.dialogHeader`(22sp). 요약 = 48dp 탑바 + 아래 56sp 점수 블록(점수 ≠ 탑바, 시맨틱 분리).
4. **스크롤 컨테이너** — 화면별 **단일 `LazyColumn` 호스트**(홈·요약·기록·설정 포함; 정적 섹션은 item). 화면 내 가로 스트립(추천·SegmentedControl)은 `LazyRow`로 격리. 세로 `verticalScroll` + `LazyColumn` 중첩 금지(Compose 풋건). 스크롤 중 고정 컨트롤(기록 탭 3탭 SegmentedControl)은 `stickyHeader`.

- **스코프 경계:** 컴포넌트 외형 상태축(F3)·모션 전환(F4)·토큰 매핑(F2)은 F8 밖. F8 = 화면 레벨 골격·거터·내비 범위·타이틀·스크롤 호스트만.
- **문서 부채:** 위 스크롤/골격 결정은 화면 문서(`04-screen-*`)에 아직 back-prop 안 됨(각 화면 spec은 결정 라인으로 반영 완료, 골격 레벨은 본 F8이 정본).
</content>
</invoke>
