# 딸깍영어 v1 — UI 논의사항 인덱스

> 상태: **정책 결정 + 화면 레이아웃 설계 확정**(2026-07-01, grill-review deep auto rev2 SHIP) · Compose 구현 진행전 · 작성일: 2026-06-30 · 갱신: 2026-07-03(프로토타입 실현 반영) · 범위: PRD v1(3탭 재집중)
> 목적: `docs/design_system`(토큰·컴포넌트 계약)과 `docs/ux`(플로우·IA·카피·예외) 사이에 남은
> **화면·시각·인터랙션 수준의 미결정 사항**을 한곳에 모아 논의·확정한다.
> 근거: [PRD.md](../../PRD.md) · [docs/ux/](../ux/) 13문서 · [design_system_src](../design/design_system_src/) · [docs/adr/](../adr/) · [prototype/](../../prototype/)(실현 산출물, 2026-07-02 — 생성 번들 임베드 플로우 + F8 스캐폴드/아이콘. 계보·권위는 [PROVENANCE §2·§2.1](../design/design_system_src/PROVENANCE.md) · [ADR-0006](../adr/0006-prototype-as-realization-sot.md))

---

## 0. 범례 (상태)

| 마크 | 의미 | 누가/무엇 |
|---|---|---|
| 🔴 **결정 필요** | 사람이 골라야 진행 가능 | 제품·디자인·사업 의사결정 |
| 🟠 **신규 설계** | 외형/명세를 새로 만들어야 함 | 디자인시스템·UX에 외형 미정 |
| 🟡 **구현 대기** | 의사결정·IA는 끝, Compose 구현·시각만 | 엔지니어링 |
| 🟢 **QA·위임** | 빌드 시 확정/실측 게이트 | 구현+QA 체크리스트 |

## 0.1 이미 확정 — 논의 불필요 (참고)

- **디자인 토큰**: 컬러(라이트/다크)·타이포(Pretendard 5웨이트)·간격(4dp 스케일)·코너(4~24·pill)·모션 전부 값 확정 ([design-tokens.md](../design/design_system_src/design-tokens.md))
- **컴포넌트 계약**: 스펙 17종(Toss참고 subset 9 + 앱네이티브 `BottomSheet` 1(net-new) + 제품고유 7 · Button~SavedCard) 외형·상태축 명세 존재 ([product-design-system.md](../design/design_system_src/product-design-system.md)). **생성 번들은 여기에 `BottomNav`·`FeedbackSheet` 2종을 승격해 19종을 실현**([PROVENANCE §2](../design/design_system_src/PROVENANCE.md)) — 단, 웹 JSX → **Compose 재구현 필요**
- **정보구조**: 하단 **3탭(학습·기록·설정)** 확정, 쇼츠·게임 컷 (PRD §11)
- **ADR 6건**: cardId 결정성 · 오프라인 레이어 분리 · 카운트업 surface 재배치 · 브랜드 색 명칭 · 로딩≠복습 퀴즈 · 프로토타입 realization-SoT ([docs/adr/](../adr/))
- **턴 피드백 IA**: 단일 시트 slim 3 + deep 3 섹션 순서·게이팅 확정 ([turn-feedback-ia.md](../ux/turn-feedback-ia.md))
- **카피 표준**: 해요체·비난없는 에러·격려 위계·한글화 경계 확정 ([ux-writing.md](../ux/ux-writing.md))

---

## 0.2 작업 현황 (완료됨 / 진행전)

> 범례(작업 진행): ✅ 완료됨 · 🟨 부분(골격·결정만) · ⬜ 진행전. (아래 §1~§6의 🔴🟠🟡🟢는 "결정 유형"으로 별개 축)

| 워크스트림 | 현황 | 비고 |
|---|:--:|---|
| UI 논의 문서 골격 (15개) | ✅ 완료됨 | README + 01~06 + 화면 9개 생성 |
| **정책 결정 18건 (§5)** | ✅ 완료됨 | grill-review deep auto SHIP(4회 수렴) · [05](05-open-decisions.md) 확정 + 화면 문서 전파 완료 |
| 파운데이션 F1 아이콘 세트 | 🟨 부분 | 결정 = **Material Symbols** 확정 · **glyph 매핑표 26종 확정** ([01a](01a-icon-mapping.md), grill-review SHIP + grill-verify PASS) · **`Foundations` 프로토타입이 26 glyph를 Material Symbols로 직접 렌더(시각 실현)**, 단 flow 번들의 DS `Icon` seam은 BLANK 유지 → 실제 vector 임포트 ⬜(M0) |
| **파운데이션 F2 토큰→Compose 매핑** | 🟨 부분 | **명세 확정**(grill-review deep auto rev.3 SHIP) · [01-foundations](01-foundations.md) F2 · Compose 코드/gradle/에셋은 M0 |
| 파운데이션 F3~F7 | ⬜ 진행전 | 컴포넌트 스텁 · 모션 · 다크 · a11y 기술 |
| **파운데이션 F8 화면 레이아웃 골격** | 🟨 부분 | **명세 확정**(grill-review deep auto rev2 SHIP) · [01-foundations](01-foundations.md) F8 — Scaffold·거터·내비 범위·타이틀·단일 LazyColumn 스크롤 · Compose 구현 ⬜(M0) |
| 공통 컴포넌트 C1~C19 (§2) | 🟨 부분 | **외형 명세 19종 확정**(grill-yourself → grill-review deep auto SHIP → grill-verify VERIFIED, 2026-07-01) · [02](02-shared-components.md) 정본 + [product-design-system.md](../design/design_system_src/product-design-system.md) §2.4/§3.4 편입 완료 · Compose 구현 ⬜(M0) |
| 시그니처 인터랙션 I1~I4 (§3) | 🟨 부분 | **명세 확정**(grill-yourself → AskUserQuestion #17·#18 → grill-review deep auto rev.3 SHIP, 2026-07-01) · [03](03-signature-interactions.md) 정본 · I2 파형 정본 [audio-pipeline.md](../design/audio-pipeline.md) 편입 · Compose Canvas 구현 ⬜(M0) |
| 화면 레이아웃 9개 (§4) | ✅ 완료됨 | **rev2 레이아웃 결정 전파 완료**(grill-yourself → grill-review deep auto rev2 SHIP · Blocker 0 · 사용자 확정 O1·O2·D1) · 각 화면 문서 `결정(rev2)` 라인 + [F8](01-foundations.md) · 시각 폴리시/Compose 구현 ⬜(M0) |
| 접근성 규약 A1~A7 (§6) | 🟨 부분 | **적용 매트릭스 확정**(grill-yourself → grill-review deep auto SHIP rev.2, 2026-07-01) · [06](06-accessibility-impl.md) 산출물 A(컴포넌트×규약)·B(화면×합격선)·C(집행) + D(SoT 정합 in-place 반영 완료) · 구현·실측은 M0/QA 게이트 |
| **프로토타입 실현 (`prototype/`)** | ✅ 완료됨 | 2026-07-02 · `Prototype Flow`(번들 19종 임베드, 온보딩→홈→대화→피드백→요약 플로우 + 시나리오/에러 [A]~[E]) · `Foundations`(F8 스캐폴드 + 아이콘 26 glyph 직접 렌더) · realization-SoT 등록 [PROVENANCE §2.1](../design/design_system_src/PROVENANCE.md)·[ADR-0006](../adr/0006-prototype-as-realization-sot.md) · Compose 구현은 M0 |

> 다음 착수 후보: **v1 Compose 프로젝트 스캐폴딩(M0)** → F1 실제 vector 임포트(매핑표 [01a](01a-icon-mapping.md) 입력) → F2 토큰 매핑 반입 → §4 화면 레이아웃 Compose 구현(설계는 rev2 확정 완료).

---

## 1. 파운데이션 & 시스템 공통

| ID | 논의 항목 | 상태 | 쟁점 / 결정해야 할 것 | 출처 |
|---|---|:--:|---|---|
| F1 | **공식 아이콘 세트 선정** | 🔴→✅ | **확정: Material Symbols (Rounded·Filled)** (자체 조달 24-grid solid 계약 충족). `Foundations` 프로토타입이 26 glyph를 직접 렌더(시각 실현); 번들 DS `Icon` seam은 BLANK 유지, 실제 vector 임포트는 M0. | product-design-system §4 · [05](05-open-decisions.md) F1 |
| F2 | 디자인 토큰 → Compose 테마 매핑 | 🟠→🟡 | **명세 확정**(grill-review deep auto rev.3 SHIP). `OneClickTheme` + `OneClickColors`/`Typography`/`Shapes`/`Motion`/`Spacing`, M3 슬롯 10 write·나머지 default, detekt hex 가드. Compose 구현은 M0 | [01-foundations](01-foundations.md) F2 · design-tokens 부록 B·C |
| F3 | 비파일럿 컴포넌트 "TBD 스텁" 채우기 | 🟠 | IconButton·Card·ListRow·Input·Switch·SegmentedControl·Badge·BottomSheet·RewardStrip·SavedCard = 빈 계약(값 TBD). M0에서 M3 default+토큰으로 | buildspec.md:91 |
| F4 | 화면 전환·모션 패턴 + reduce-motion 대체 | 🟠 | 300ms 슬라이드/크로스페이드 전역 패턴; reduce-motion 시 정적 대체(슬롯→스냅·시머→정적·리플/파형 축소) | accessibility #9·#11 |
| F5 | fontScale 1.3× 무파손 게이트 | 🟢 | 사전 보장 아닌 **구현 후 실측 합격선**. 1.3~2.0× best-effort(스크롤), 클리핑 금지 | accessibility #19 |
| F6 | 다크모드 화면별 대비 검증 | 🟢 | 라이트/다크 양 테마, 벤·그라데이션 카드 흰텍스트 ≥4.5 | accessibility |
| F7 | Compose Semantics API / 스크린리더 경로 | 🟢 | announce·LocaleList(영어 발음)·focus 관리 = 신규 기술 결정, QA 실측 의존 | accessibility #3·#12 |

## 2. 신규 공통 컴포넌트 (디자인시스템 19종에 없음)

| ID | 컴포넌트 | 상태 | 용처 / 메모 | 출처 |
|---|---|:--:|---|---|
| C1 | 확인 다이얼로그·알럿 | 🟠 | 초기화·로그아웃·카드삭제·세션중단. **DS에 Dialog 부재** | settings · exception[C] |
| C2 | 2단계 위험 확인 + "삭제" 타이핑 입력 | 🟠 | 계정 삭제 전용 | settings §8 |
| C3 | 스낵바 (+undo) | 🟠 | 저장 카드 삭제 undo, transient 확인 | saved-cards §5 · exception[E] |
| C4 | 글로벌 오프라인 배너 | 🟠 | 상단 얇은 지속 배너, 전 화면 공존 | exception[D] |
| C5 | 빈 상태(Empty state) | 🟠 | 기록 탭 3종·홈. 96dp 아이콘+문구 | saved-cards §4.1 · home · exception |
| C6 | 로딩 스켈레톤/시머 + 정적 대체 | 🟠 | slim/deep/요약 섹션 점진 렌더 | turn-feedback §3 · accessibility |
| C7 | 프로그레스 링 | 🟠 | 분석 중(Analyzing) 표현 | accessibility · dialogue |
| C8 | 슬라이더 | 🟠 | 말하기 속도(0.5~1.5x), **톤 5단계** | settings §4 · turn-feedback toneStyle |
| C9 | 음질 2지선다 (SegmentedControl 재사용) | 재사용 | 신규 컴포넌트 불필요, `OneClickSegmentedControl` 재사용 | settings §4 |
| C10 | 시간 선택기(TimePicker) | 🟠 | 리마인더 시각, M3 분단위 | notification §6 · settings |
| C11 | 인라인 재시도 에러 상태 [A] | 🟠 | 섹션/카드 자리 에러+재시도, 반복 시 건너뛰기 | exception[A] |
| C12 | 차단 게이트/전체화면 에러 [C] | 🟠 | 생성실패·로그인실패·한도·오프라인 새세션 | exception[C] |
| C13 | 권한 요청 프라이밍 + 설정 딥링크 | 🟠 | 마이크(맥락요청)·알림(POST_NOTIFICATIONS 13+) | onboarding · notification §3 |
| C14 | streak 칩/배지 + XP 카운터(백스테이지) | 🟠 | 홈·완주·한도·리마인더 반복 등장 | gamification · home |
| C15 | EN+KO 이중 노출 블록 + 세그먼트/취소선 렌더러 | 🟠 | grammar(취소선·코랄·highlight)·naturalExpression·톤 문장 | ux-writing 한글화경계 · turn-feedback · accessibility |
| C16 | 슬롯머신 카운트업 (RewardStrip과 별개 명시) | 🟠 | XP·시간·streak 롤업, same-day 정적 규칙 | gamification §4.4 · accessibility |
| C17 | 미완 세션 "이어하기" 프롬프트 | 🟠 | snapshot 복귀. 카드/배너/시트 형태 미정 | home · exception |
| C18 | **LimitReachedPanel** (surface 3종) | 🟠 | dialogue_start_gate·home·onboarding. 비상업 중립 문구+streak 넛지, upgradeSlot=null | daily-limit §2 |
| C19 | **ReminderOptInSheet** + ReminderSettingRow | 🟠 | 2nd 세션 후 opt-in 시트, 설정 조건부 시각 행 | notification §2·§6 |

## 3. 시그니처 인터랙션 (Compose Canvas 재구현)

| ID | 인터랙션 | 상태 | 메모 | 출처 |
|---|---|:--:|---|---|
| I1 | 음성 4상태 마이크 | 🟡 | Ready/Recording/Analyzing/Complete · 96dp · 리플3겹 · 프로그레스링. 상태축·announce 확정 | PRD §8.2.3 · product-design-system §3.1 |
| I2 | 실시간 파형 | 🟡 | crackle 40바 ±0.3, Recording 한정 | DS · dialogue |
| I3 | 슬롯머신 카운트업 | 🟡 | 1260ms 스프링반동, 완주·기록 surface 한정 | ADR-0003 · gamification |
| I4 | 벤다이어그램 + 런타임 대비 가드 | 🟡 | 30% 겹침, 라이트/다크 대비 자동보정 | DS §6 · turn-feedback |

## 4. 화면별 UI 확정 (UI-kit "진행 중" → 명세 완성)

> **[rev2 확정 · 2026-07-01]** 9개 화면 레이아웃 결정 전파 완료(grill-yourself → grill-review deep auto rev2 SHIP, Blocker 0; 사용자 확정 O1 레벨카드·O2 상황리스트·D1 발판카드). 각 화면 문서의 `**결정(rev2):**` 라인이 정본이며, 전역 골격(Scaffold·거터·내비 범위·타이틀·단일 LazyColumn 스크롤)은 [01-foundations F8](01-foundations.md)이 정본. 아래 표 상태열(🟠/🟡)은 초기 "결정 유형" 기록이다.

| 화면 | 상태 | 핵심 논의 / 확정필요 요소 | 정본 |
|---|:--:|---|---|
| **온보딩** | 🟠 | 레벨 3지선다 UI(평가처럼 안 보이게)·상황 6카드(첫카드 비강조)·생성중 로딩·Google 저장 제안 3버튼·한도초과(중립) | [01-onboarding-first-session.md](../ux/01-onboarding-first-session.md) |
| **홈·주제선택** | 🟠 | 메인 CTA hero·게임화 요약 스트립(시간>streak>XP)·주제선택(추천/4그룹/직접입력/새로고침)·접힌 세션설정·미완복귀·오프라인 CTA | [home-learning-entry.md](../ux/home-learning-entry.md) |
| **대화학습** | 🟠 | 채팅+한국어 발판 카드·4상태 마이크 시트·TTS 재생/다시듣기·transcript 표시(+수정 미정)·완료 화면 | [dialogue-learning-flow.md](../ux/dialogue-learning-flow.md) |
| **턴 피드백 시트** | 🟡 | 단일 시트 slim3+deep3 IA 확정. 시머·세그먼트 렌더·톤 슬라이더·패러프레이즈 저장카드. 점수 크기 = 신규 토큰 `type.turnScore` 28sp 확정(시각 세부만 미정) | [turn-feedback-ia.md](../ux/turn-feedback-ia.md) |
| **세션 요약** | 🟠 | 종합점수(56sp)+적립 스트립 **별도 블록**·하이라이트/표현/단어/북마크/코칭 섹션·부분실패 재시도 | PRD §8.3 · gamification §4 |
| **기록 탭** | 🟠 | 평생통계 헤더·3종 SegmentedControl·카드 인라인펼침/복사·스와이프삭제+undo·빈상태 | [saved-cards.md](../ux/saved-cards.md) |
| **설정** | 🟠 | 단일스크롤 6섹션·셰브론 행·적응형 계정섹션·위험확인 차등 | [settings-data-account.md](../ux/settings-data-account.md) |
| **한도 게이트** | 🟠 | LimitReachedPanel surface별 분기·완주+도달 동시 처리 | [daily-limit-ux.md](../ux/daily-limit-ux.md) |
| **리마인더·알림** | 🟠 | opt-in 시트·권한 priming·설정 행·알림 콘텐츠 | [notification-reminder.md](../ux/notification-reminder.md) |

## 5. 제품·디자인 정책 결정 (✅ 전 항목 확정)

> **18건 전부 확정** — 확정값·근거는 [05-open-decisions.md](05-open-decisions.md)가 정본(F1·P12·P13 사용자 확정 완료). 아래 표의 "현재 가정" 열은 확정 전 기록(이력)이다.

| ID | 결정 항목 | 소유 | 현재 가정 / 선택지 (→ 확정은 [05](05-open-decisions.md)) | 출처 |
|---|---|---|---|---|
| P1 | 홈 XP 표시 여부 | 제품 | 작게(· N XP) vs 접힘 — 베타 보정 | gamification §6 |
| P2 | 턴 내 writingScore 점수 크기 | 디자인 | 요약(56sp)보다 작게(가정) → **신규 토큰 `type.turnScore` 28sp 확정** | turn-feedback §11 · [05](05-open-decisions.md) P2 |
| P3 | deep 재호출/캐시 정책 | 제품/비용 | 같은 턴 1회 후 캐시(가정) | turn-feedback §11 |
| P4 | "반복 실패" 임계 횟수 | 제품 | 미명시(몇 회=반복?) slim/deep 공통 | dialogue §7.1 · turn-feedback §9.1 |
| P5 | 인라인 transcript 수정 UI | UX | 고정 안 함(다시말하기/채팅만) — 명시적 논의 항목 | dialogue §6.1 |
| P6 | 완주+한도 동시 발생 표현 | 제품 | 축하 1차 + 도달 보조(신규 결정) | daily-limit §4 |
| P7 | 홈 at-limit 고지 방식 | 거버넌스 | ADR 승격 여부 미정 | daily-limit §10 |
| P8 | 오프라인 새 학습 CTA | 디자인 | 비활성화 vs 안내 표시(택일) | home §8.1 |
| P9 | 최근 주제(recent) 노출 | 제품 | v1 제외, 로컬 저장 정책 별도 | home §3.2 |
| P10 | 카드 정리 프리셋 값 | 제품 | 30/90일·전체(권장) | settings §12 |
| P11 | 리마인더 기본 시간 | 제품 | 20:00(가정) | settings · notification |
| P12 | 첫 완주 특례·마일스톤 카피 | 카피리뷰 | 미검증/검수 미완 | gamification §4.5·§5 |
| P13 | 정책 본문 3종(개인정보·약관·삭제) | 창업/법무 | 미작성 | settings §12 |
| P14 | 설정 "진도 다시 옮기기" 행 | 제품 | 2회 연속 실패 시 조건부(미확정) | exception #19 |
| P15 | save_opportunity_shown 이벤트 | 제품 | v1 미정(저장 기회 노출 계측) | analytics #18 |
| P16 | 이모지 팔레트 확장 | 브랜드 | 🔥만, 확장 보류 | ux-writing §5.3 |
| P17 | Analytics 최종 event id 정합 | 계측 | 각 문서 제안명 → analytics-events에서 확정 | 전 문서 공통 |

## 6. 접근성 구현 규약 (🟢 체크리스트 — 각 화면 적용)

| ID | 규약 | 출처 |
|---|---|---|
| A1 | 터치타깃 ≥48dp(시각<48은 터치영역만 확장), 마이크 96dp | accessibility §2 |
| A2 | 색 단독 신호 0건 — 의미색마다 비색 신호(형태/아이콘/텍스트) | accessibility §3 |
| A3 | 마이크 4상태 assertive announce + stateDescription | accessibility §6 |
| A4 | 영어 콘텐츠 LocaleList(en) — 말풍선·카드·예문 | accessibility §7 |
| A5 | 포커스 관리 — 시트/모달 진입 시 헤더 포커스, 닫힘 시 호출부 복귀 | accessibility §6 |
| A6 | 라이브리전 정중함 — 음성=assertive, 스켈레톤=polite | accessibility §6 |
| A7 | 커스텀뷰 dp 고정 — 파형·마이크 96dp(폰트스케일 무관) | accessibility §2 |

---

## 7. 문서 구조

공통(파운데이션·컴포넌트·정책)은 묶고, **화면 9개는 각각 분리**한다.
각 문서 = **표 + 항목별 논의 스켈레톤**(현황 / 쟁점 / 선택지 / 제안 / 결정).

| 파일 | 다루는 내용 |
|---|---|
| [`README.md`](README.md) (본 문서) | 전체 인덱스 |
| [`01-foundations.md`](01-foundations.md) | 표 1 — 아이콘·토큰매핑·모션·다크·접근성·**화면 레이아웃 골격(F8)** |
| [`01a-icon-mapping.md`](01a-icon-mapping.md) | F1 아이콘 매핑표 — 시맨틱 seam ↔ Material Symbols glyph 26종(확정) |
| [`02-shared-components.md`](02-shared-components.md) | 표 2 — 신규 공통 컴포넌트 19종 |
| [`03-signature-interactions.md`](03-signature-interactions.md) | 표 3 — 마이크·파형·슬롯머신·벤 |
| [`04-screen-01-onboarding.md`](04-screen-01-onboarding.md) | 온보딩 & 첫 세션 |
| [`04-screen-02-home.md`](04-screen-02-home.md) | 홈 & 주제 선택 |
| [`04-screen-03-dialogue.md`](04-screen-03-dialogue.md) | 대화 학습 |
| [`04-screen-04-feedback-sheet.md`](04-screen-04-feedback-sheet.md) | 턴 피드백 시트 |
| [`04-screen-05-summary.md`](04-screen-05-summary.md) | 세션 요약 |
| [`04-screen-06-history.md`](04-screen-06-history.md) | 기록 탭(저장 카드) |
| [`04-screen-07-settings.md`](04-screen-07-settings.md) | 설정 |
| [`04-screen-08-limit-gate.md`](04-screen-08-limit-gate.md) | 일일 한도 게이트 |
| [`04-screen-09-reminder-notification.md`](04-screen-09-reminder-notification.md) | 리마인더 & 알림 |
| [`05-open-decisions.md`](05-open-decisions.md) | 표 5 — 🔴 결정 필요 |
| [`06-accessibility-impl.md`](06-accessibility-impl.md) | 표 6 — 접근성 구현 체크리스트 |

> 집계(결정 유형): 🔴 결정 필요 18건 → **✅ 전부 확정** · 🟠 신규 설계 ~27건(⬜ 진행전) · 🟡 구현 대기 ~6건(F2 명세 확정 포함) · 🟢 QA·위임 ~9건.
> 작업 현황(완료됨/진행전)은 위 **§0.2 작업 현황** 표 참조.
</content>
</invoke>
