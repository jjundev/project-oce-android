# 딸깍영어 v1 — 접근성 명세 (Accessibility SoT)

> **상태:** 설계 확정(SHIP) · **작성일:** 2026-06-30 · **대상:** PRD §NFR-5(접근성)
> **근거:** [PRD.md](../../PRD.md) §NFR-5·NFR-8·NFR-9 · [design-tokens.md](../design/design_system_src/design-tokens.md) §5 · [turn-feedback-ia.md](turn-feedback-ia.md) §10 · [exception-states.md](exception-states.md) §2 · [딸깍영어_UIUX_분석_보고서.md](../../딸깍영어_UIUX_분석_보고서.md) §9·§14 · 옛 앱 `VennColorContrastGuardTest`(`archive/android`)
> **역할:** 여러 문서에 흩어진 접근성 규약을 통합한 **단일 출처(SoT)**. `turn-feedback-ia.md §10`·`exception-states.md §2`·`design-tokens.md §5`는 본 명세를 참조한다.

---

## 0. 적용 범위 · 목표

- **준수 목표:** 실용적 **WCAG 2.1 AA 지향**. AAA 전수 준수는 **비목표**([PRD.md](../../PRD.md):287).
- **v1 합격선(검수 기준):**
  - 핵심 플로우(온보딩 → 홈 → 대화학습 음성 4상태 → 턴 피드백 → 세션 요약 → 저장 → 설정) **TalkBack 1패스 통과**.
  - **색 단독 신호 0건**.
  - **48dp 터치 타깃 100%**.
  - 시스템 글자 크기 **fontScale 1.3×에서 레이아웃 파손 0**.
  - 시스템 **reduce-motion 존중**.
- **범위 밖:** AAA 전수, in-app 접근성 토글(§A #20), Shorts·게임 탭(v1 컷/플레이스홀더), 다이내믹 컬러(Material You).

---

## A. 결정표 (확정)

### Confident
| # | 결정 | 권장안 | 근거 |
|---|---|---|---|
| 1 | 준수 등급 | AA 지향, AAA 비목표 | `PRD.md:287` |
| 2 | 산출물 | 본 `accessibility.md` 단일 SoT, 기존 3곳 참조화 | `turn-feedback-ia.md:162`·`exception-states.md:29`·`design-tokens.md:109` |
| 3 | 스크린리더 타깃·기술 | **TalkBack** 단일 타깃(Android 전용 앱 전제). **Compose Semantics API 채택은 신설 기술 결정** | `PRD.md:287`(NFR-5). 구현 API는 doc 미존재 |
| 4 | 최소 터치 타깃 | 48×48dp 하한, 마이크 96. 시행 Compose API는 빌드 시 확정(기본 `minimumInteractiveComponentSize()`) | `design-tokens.md:111` |
| 5 | 색 외 보조 단서 — 전역 원칙 | 색은 단독 신호 금지, 형태/아이콘/텍스트/숫자 ≥1 동반 | `turn-feedback-ia.md:164`·`exception-states.md:30` |
| 6 | 음성 4상태 보조 단서 | 상태 텍스트 라벨 의무 + **모든 전환 announce**(assertive) | 4상태=`UIUX:154`. announce는 신설 결정 |
| 7 | 벤다이어그램 텍스트 대안 | 두 단어 + 교집합 의미를 텍스트로 동시 제공 | 본 명세 신설(도출) — `turn-feedback-ia.md:94-95`는 벤 *구성·대비가드*를 기술하며 텍스트 대안 자체의 선례는 아님 |
| 8 | 파형 시맨틱 | 시각 전용 장식, 상태는 마이크가 전달(설계 추론). 시행 API `clearAndSetSemantics`는 구현 결정 | `UIUX:163-164`(파형에 의미정보 없음) |
| 9 | 슬롯머신 시맨틱 | 최종값만 1회 노출, **reduce-motion 시 즉시 스냅**. 신설 결정 | `UIUX:142`(타이밍만) |
| 10 | 다이내믹 타입 | fontScale 존중, sp 유지, 커스텀 뷰 dp 고정 | `design-tokens.md:82`·`UIUX:78` |
| 11 | 모션 감소 | system reduce-motion 존중, 컴포넌트별 대체. 신설 결정. `ANIMATOR_DURATION_SCALE` 감지는 구현 결정 | `UIUX §8`(애니 명세) |
| 12 | 혼합 언어 발음 | 영어 콘텐츠에 `LocaleList(en)` 시맨틱 부여. 신설 결정 | `UIUX §12`는 카피 톤 |
| 13 | 포커스 관리 | 진입 포커스+announce(doc), 닫힘 시 호출 컨트롤 복귀·deep append 탈취금지(신설) | `exception-states.md:31` |
| 14 | 라이브 리전 정중함 | 음성 전환=assertive, 점진 스켈레톤 섹션 확정=polite. 신설 결정 | `UIUX §11`은 시각 로딩 |
| 15 | 스와이프 대안 | 저장카드 행마다 customAction "삭제" | `archive/.../android-design-guidelines/SKILL.md:593-604`(R6.4). UX는 `saved-cards.md` |
| 16 | 오디오 텍스트 동반 | TTS/스피커 재생 시 대응 텍스트 가시. co-presence 규칙은 신설 codify | `dialogue-learning-flow.md:70`(상대역 말풍선 항상 렌더)에서 도출 |
| 17 | 대비 기준 | 텍스트 ≥4.5/보조 ≥3.0(doc), 비텍스트 UI ≥3.0(WCAG 1.4.11 외삽·신설), 벤 가드 유지 | `design-tokens.md:113-114` |
| 18 | 집행·테스트 | Venn 가드 Compose 포트 + Compose a11y 체크 + contentDescription 린트 + TalkBack 수동 1패스 | Venn 테스트=`archive/.../VennColorContrastGuardTest.java` 실존. `PRD.md:290`=대비 가드 일반론 |

### Assumptions / 확정된 needs-you
| # | 결정 | 확정값 | 비고 |
|---|---|---|---|
| 19 | fontScale 상한 | **1.3× 무파손(QA 검수 합격선) + 1.3~2.0× best-effort** | [confirmed] 1.3×에서 클리핑·겹침 0 실측 게이트(사전 보장 아님) |
| 20 | in-app 접근성 토글 | **v1 없음, system 설정에만 의존** | [confirmed] |

### 빌드 단계 위임 (기본값 명시)
| 항목 | 기본값 | 확정 시점 |
|---|---|---|
| 48dp 시행 Compose API(#4) | `minimumInteractiveComponentSize()` 우선, 케이스별 `defaultMinSize` | 컴포넌트 구현 시 |
| 영어 `LocaleList` 적용(#12) | 선제 부여, 한국어 TalkBack 영어 발음은 빌드 후 실측 | 통합 후 QA |
| 포커스 복귀 경로 목록(#13) | 전 바텀시트·모달 닫힘 시 `FocusRequester` 복귀 | 시트 구현 시 |

---

## 1. 터치 타깃

- **48×48dp 하한.** 주요 행위는 더 크게 — 마이크 96×96dp([design-tokens.md](../design/design_system_src/design-tokens.md):111).
- 시각 크기가 48dp 미만인 컨트롤(스피커 토글·해석보기·칩·셰브론 행 등)은 **터치 영역만 확장** — `Modifier.minimumInteractiveComponentSize()` 또는 invisible 패딩으로 ≥48dp 보장(시각은 유지).
- 인접 터치 타깃은 충분한 간격으로 오터치 방지(간격 스케일 `design-tokens.md §4.2` 준용).

## 2. 스크린리더(TalkBack) 시맨틱

기술: Compose **Semantics API**(`contentDescription`·`stateDescription`·`liveRegion`·`customActions`·`mergeDescendants`·`clearAndSetSemantics`). 컴포넌트별 규칙:

| 컴포넌트 | 규칙 |
|---|---|
| 마이크 4상태(MicState) | `stateDescription`(Ready"말할 차례" / Recording"녹음 중" / Analyzing"분석 중" / Complete"완료" — MicState 4값 1:1) + **모든 전환 assertive announce**(§6·§7, 문구=stateDescription 재사용). "들을 차례"는 MicState 아닌 상대역 재생(OpponentPlayback.Playing) → 채팅 말풍선 행 |
| 채팅 말풍선 | 화자+텍스트 `mergeDescendants`. 영어 본문 `LocaleList(en)`(§7). 스피커=customAction "듣기", 해석보기=토글 상태 노출. 상대역 재생(OpponentPlayback.Playing) 진입 시 assertive announce "들을 차례" |
| 피드백 시트 | 점수=숫자+의미 함께(§3). grammar `incorrect`=취소선 의미를 텍스트화. highlight 변경부 설명. 벤=텍스트 대안(§3) |
| 슬롯머신/카운트업 | 최종값만 `stateDescription` 1회, 틱 미노출 |
| 프로그레스 링 | `stateDescription`(퍼센트 또는 "분석 중") |
| 파형(WaveformView) | `clearAndSetSemantics{}`로 장식 처리(포커스 제외). 상태는 마이크가 단독 전달 |
| 벤다이어그램 | 시각은 장식, **두 단어 + 교집합 의미를 텍스트로** 별도 노출 |

## 3. 색 외 보조 단서 매트릭스

색은 **절대 단독 신호가 아니다**. 의미색마다 비색 신호를 동반한다([turn-feedback-ia.md](turn-feedback-ia.md):164, [exception-states.md](exception-states.md):30):

| 의미색 토큰 | 동반 비색 신호 |
|---|---|
| `feedback.natural` / `feedback.correct` | 유형 라벨 텍스트("자연스러운"/"정확한") |
| grammar `incorrect` | 취소선 + 텍스트 |
| 의미색 highlight | 색 + 밑줄/배경 |
| `writingScore`/종합 점수 | 점수 숫자 동반 |
| `voice.*`(음성 4상태) | 형태 변화 + 아이콘 + 상태 라벨 |
| `game.streak`/`game.saveGold` | 아이콘(🔥/코인) + 숫자 |
| `state.error` | 아이콘 + 텍스트 |

## 4. 큰 글자 / 다이내믹 타입

- 모든 텍스트 **sp**([design-tokens.md](../design/design_system_src/design-tokens.md):82). 시스템 fontScale 반영.
- 컨테이너는 **클리핑 대신 스크롤/리플로우** — 바텀시트·피드백 시트는 NestedScroll 유지.
- 커스텀 뷰 치수(파형·마이크 96dp 등)는 **dp 고정**(폰트와 함께 커지지 않음).
- **QA 검수 합격선: fontScale 1.3×에서 파손 0**(#19) — 미구현 UI에 대한 사전 보장이 아니라 구현 후 실측으로 통과시키는 게이트다. 1.3~2.0×는 스크롤로 best-effort.

## 5. 모션 감소 (reduce-motion)

시스템 reduce-motion(`ANIMATOR_DURATION_SCALE=0`) 감지 시 **정보는 유지하고 움직임만 제거**(#9·#11 확정):

| 컴포넌트 | reduce-motion 대체 거동 |
|---|---|
| 슬롯머신 카운트업 | 즉시 최종 숫자 스냅 |
| 스켈레톤 시머 | 정적 플레이스홀더 |
| 리플 3겹 / 파형 crackle | 축소(정적/최소) |
| 화면 전환 300ms 슬라이드 | 크로스페이드/즉시 |

## 6. 포커스 & 라이브 리전

- **진입:** 바텀시트/모달 열림 시 헤더(또는 첫 요소)로 포커스 이동 + announce([exception-states.md](exception-states.md):31). 차단[C] 게이트·중단 모달 포함.
- **이탈:** 닫힘 시 호출 컨트롤로 `FocusRequester` 복귀.
- **피드백 시트:** slim에 포커스 → deep은 아래로 append하되 **포커스 탈취 금지**.
- **라이브 리전 정중함(#14):** 음성 상태 전환=**assertive**(#6의 "모든 전환 announce"와 정합). 점진 스켈레톤=**polite**, 섹션 *확정* 시에만 announce(시머 중간은 announce 안 함).

## 7. 혼합 언어 발음

- 한국어 UI 기본, **영어 학습 콘텐츠(대사·표현·단어·예문)에 영어 로케일 부여**(#12) → TalkBack·TTS가 영어로 정확히 발음. 적용 API는 빌드 시 확정: 안정 Compose에는 `semantics{}` 로케일 노드 속성이 없으므로 텍스트 레벨 `LocaleList`/`AnnotatedString`의 `LocaleSpan`(또는 동등 경로)으로 부여하고, 한국어 TalkBack의 영어 발음은 통합 후 QA 실측으로 검증한다(§A 빌드 위임).
- 적용 지점: 채팅 말풍선 영어 본문, 표현/단어 카드, 피드백 패러프레이징 예문.

## 8. 대비

- **텍스트:** 주요 ≥4.5, 보조 ≥3.0([design-tokens.md](../design/design_system_src/design-tokens.md):113-114).
- **비텍스트 UI/아이콘:** ≥3.0(WCAG 1.4.11 외삽).
- **벤다이어그램 런타임 가드:** 좌우 원 색거리 ≥50, 라이트/다크 양쪽 텍스트 대비 강제([design-tokens.md](../design/design_system_src/design-tokens.md):113, `VennColorContrastGuardTest` 계승).
- 그라데이션 카드 흰 텍스트(#39A0ED→#2B7FBB) ≥4.5 검증. `values-night` 양 테마 검증.

## 9. 집행 · 테스트

- **자동:** `VennColorContrastGuardTest`를 Compose로 포트(대비 가드). Compose 접근성 체크(accessibility-test-framework/Espresso) + contentDescription 누락 린트.
- **수동:** 핵심 플로우(§0 합격선) TalkBack 1패스 체크리스트.
- NFR-8 위험지대(대비 가드) 정렬([PRD.md](../../PRD.md):290). CI 편입 권고.

## 10. 비목표

- WCAG AAA 전수 준수.
- in-app 접근성 토글(모션/글자/햅틱) — v1은 system 설정 의존(#20).
- Shorts·게임 탭 접근성(v1 컷/플레이스홀더).
- 다이내믹 컬러(Material You).

---
결정을 바꾸려면 `#<n>=<value>`로 재호출하세요.
