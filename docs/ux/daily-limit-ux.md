# 일일 한도 UX — 도달 전/후 문구, 리셋 안내, 비상업 게이트

> 흩어져 있던 한도 UX 결정(`home-learning-entry.md` §6, `gamification-emphasis.md` §7, `dialogue-learning-flow.md` §3·§11, `01-onboarding-first-session.md`)을 단일 정본으로 통합한다. 상위 제약은 `PRD.md` FR-26 / FR-27 / NG1. 데이터 계층은 [firestore-schema.md](../design/firestore-schema.md)가 정본.

## 0. 원칙

- 한도를 "비용 통제 장치"가 아니라 **"오늘 학습을 잘 마쳤다"는 자연스러운 마무리**로 프레이밍한다.
- v1은 수익화가 아니라 **리텐션 검증 단계**다(`PRD.md:37` NG1). 업그레이드 CTA·가격·구독 유도는 전면 비노출(`PRD.md:217` FR-26).
- 한도 카운트·리셋 판정은 **서버(Functions) 권위**, 클라이언트는 신뢰하지 않는다(`PRD.md:218` FR-27).
- 손실형·카운트다운·`무료 횟수 소진`·리셋 타이머는 전면 금지(`gamification-emphasis.md:191`, §6).

## 1. 권위 & 데이터 흐름

- `remaining`의 유일 사실원은 **서버 응답**. 시작 게이트가 `Accepted(sessionId, remaining)` / `Rejected(remaining=0)`을 반환한다(`dialogue-learning-flow.md:25`).
- 클라 `remaining`은 **표시 전용**. 시작 차단은 항상 서버 게이트가 수행한다(`firestore-schema.md:138`).
- 일일 캡은 **dialogue 시작만** 카운트(`firestore-schema.md:138`). 기본 `dailyFreeSessions = 3`, `config/limits`로 원격 라이브 튜닝(`firestore-schema.md:286`).
- 일경계 stale 처리: 클라 `remaining`이 낡아도 무해 — 다음 시작에서 서버가 재검(`PRD.md:218` FR-27). `fresh`의 정의는 §6 참조.

## 2. 한도 표면 & 공유 컴포넌트

`LimitReachedPanel(surface, remaining, streak)` — **신규 공유 컴포넌트**(기존 스펙 코드화가 아닌 본 문서의 신규 설계, DRY 목적).

| surface 값 | 진입 | 본문 | 보조 액션 |
|---|---|---|---|
| `dialogue_start_gate` | `Rejected(0)` → `QuotaBlocked` | §3 도달 문구 | 없음(홈 복귀) |
| `home` | fresh `remaining==0` 보조 고지(§6) | §3 도달 문구 | `기록 보기`(기록 탭 이동만) |
| `onboarding_first_session` | 첫 세션 게이트 `{remaining:0}` | §3 도달 문구 | 없음 → 홈(§7) |

- 차단형 진입 표면(시작 게이트 / 홈 at-limit 고지)에서만 `LimitReachedPanel`을 쓴다. **완주 화면의 도달 전 안내는 패널이 아니라 보조 인라인 텍스트 1줄**이다(§3).
- 온보딩 중 시작 게이트에서 한도 도달 시 surface는 가장 구체적인 맥락인 **`onboarding_first_session`을 우선**한다. 단, 라이브 스냅샷(이어하기) 보유 게스트는 예외로 `dialogue_start_gate`를 쓴다(§7).
- v1.1 업그레이드 훅은 이 컴포넌트의 **비가시 `upgradeSlot`**(렌더 null). v1은 가시적 placeholder(고스트 버튼·"곧 출시" 티저)도 두지 않는다(`PRD.md:217`).
- 홈 보조 액션은 **저장 카드 복기 모듈을 노출하지 않는다**(`home-learning-entry.md:228` 준수). `기록 보기`는 기록 탭으로의 이동만 의미한다.

## 3. 카피 인벤토리 (해요체, 비난·손실 없음)

| 상황 | 카피 | surface / 계측 |
|---|---|---|
| 도달 전(완주로 `remaining`→1) | `오늘 한 번 더 할 수 있어요` (비숫자 어포던스) | 완주 화면 보조 인라인 1줄 · 계측 X |
| 도달(`remaining==0`) 기본 | `오늘 무료 학습을 다 했어요. 내일 또 만나요.` | `LimitReachedPanel` |
| 도달 + streak 보유 | `오늘 무료 학습을 다 했어요. 내일 이어가면 N일째예요.` (N = 서버 현재 streak + 1) | `LimitReachedPanel` |

- 정본 구두점은 **마침표형**("내일 또 만나요.")이다. `PRD.md:217`의 엠대시("— 내일 또 만나요")는 PRD 표기 차이로 둔다.
- streak==0이면 미래형 넛지를 붙이지 않는다(`gamification-emphasis.md:145,188`).

> 회피: 잔여 숫자 배지, `무료 횟수 소진`, 카운트다운/타이머, streak 손실형, 업그레이드/가격 암시.

## 4. 도달 전(pre-limit, `remaining==1`) 동작

- 세션 완주로 `remaining`이 1로 바뀐 **그 순간 1회만** 요약 화면에서 안내한다. 축하가 1차, 한도 안내는 그 하단 **보조 인라인 텍스트 1줄**(`gamification-emphasis.md:35,49` 위계 원칙).
- 문구는 비숫자 어포던스(`오늘 한 번 더 할 수 있어요`) — 잔여 수를 노출하지 않으므로 §0 금지 목록과 양립한다.
- **홈 proactive 배지는 없다.** 같은 날 재방문 홈이 `remaining==1`이어도 무고지·정상 CTA를 유지한다(지속 카운터 인상 차단, `home-learning-entry.md:255-256`).
- 완주+도달이 같은 세션에서 동시 발생(마지막 무료 세션 완주)하는 경우는 **신규 결정**이며, 위와 동일하게 완주 축하 1차 + 도달 문구(`remaining==0`)를 보조로 둔다.

## 5. 도달(at-limit, `remaining==0`) 동작

- 시작 게이트가 `Rejected(0)` → `QuotaBlocked`. 대본 생성으로 진입하지 않는다(`dialogue-learning-flow.md:44`).
- 문구는 §3 도달 카피. 업그레이드 유도로 이어지지 않는다(`dialogue-learning-flow.md:281`).

## 6. 홈 메인 CTA & fresh/stale

- 메인 CTA `오늘 5분 말하기`는 **항상 활성**이다(`home-learning-entry.md` §6.1 보조 정보 원칙 불변). 홈은 사전 차단하지 않고 CTA는 시작 게이트로 탭스루한다.
- `fresh = 현재 포그라운드 세션/스냅샷의 서버 응답값`. 백그라운드 복귀 시 stale 처리.
- `fresh remaining==0` → 홈에 **보조 고지**를 추가로 표시(메인 CTA 위계 불변). 탭 → 시작 게이트 거부 → `QuotaBlocked`.
- 서버 응답 없음(콜드 런칭 등) = `unknown(≠0)` → 홈 고지 억제, 탭 → 시작 게이트가 판정. (§13의 "억제"는 **홈 고지 억제**이지 탭 차단이 아니다.)

## 7. 온보딩 첫 세션 도달

- 구조상 fresh UID는 도달 불가, 재방문 미완주 게스트만 도달(`01-onboarding-first-session.md:63`).
- 중립 문구 표시 → 홈 진입. `한 번 더 하기`류 재시도는 노출하지 않는다(`01-onboarding-first-session.md:103,163`).
- 라이브 스냅샷(이어하기 가능) 보유 게스트는 `onboarding_first_session`에서 제외한다. 스냅샷 재개는 시작 게이트를 거치지 않으므로(`home-learning-entry.md:74`), `새로 시작`을 골라 거부될 때만 한도 패널을 보며 이때 surface는 `dialogue_start_gate`다. `onboarding_first_session`은 **스냅샷 없는** 재방문 미완주 게스트에만 적용한다([01a](01a-onboarding-first-session-followups.md) §4c).

## 8. 리셋 안내

- `usage/{yyyymmdd}`는 KST 일경계 파티션 — 자정에 새 문서로 자동 리셋, 별도 잡 불필요(`firestore-schema.md:111-117`).
- 사용자에겐 **자연어 암묵 표현("내일")만**. 자정/00시 등 시각·카운트다운 표기는 한도 중심 인상을 줘서 배제한다.

## 9. 계측 (NFR-7)

- 정본 단일 이벤트 **`limit_reached {remaining, surface}`** (`01-onboarding-first-session.md:182`, `home-learning-entry.md` 계측 표준).
- surface ∈ `{home, dialogue_start_gate, onboarding_first_session}`. 완주 화면의 도달 전 인라인 안내는 차단이 아니므로 **미계측**.
- **선행 게이트(문서 작업):** `dialogue-learning-flow.md:290`의 `quota_blocked` 계열명을 `limit_reached`로 정규화한 **뒤에** analytics 코드 분기를 시작한다(스플릿 파이프라인 방지). 정본 이벤트명 결정은 본 문서가 권위.

## 10. 거버넌스 — `home-learning-entry.md:258` 재해석

- `home-learning-entry.md:258` "남은 횟수가 1회 이하이거나 한도에 도달했을 때는 명확히 안내한다"의 **"명확히 안내"는 숫자 N 노출 의무가 아니다** — 비숫자 명료 고지로 충족하며, 이는 `gamification-emphasis.md` §6의 카운트다운/압박 금지와 양립한다.
- 홈 proactive 고지는 `remaining==0`에서만, `remaining==1` pre-limit은 완주 화면이 전담(홈 미고지, §4).
- 기록 방식은 `gamification-emphasis.md` §6의 FR-20 deviation **"정본 주석"** 패턴을 따른다: `home-learning-entry.md:258`에 위 재해석 주석을 추가하고, 거버넌스상 필요 시 ADR로 승격한다.

## 11. 결정표

### Confident
| # | 결정 | 답 | 근거 |
|---|---|---|---|
| 1 | 한도 판정 권위 | 서버 전용, 클라 remaining 표시 전용 | FR-27 `PRD.md:218`, `firestore-schema.md:138` |
| 2 | 일일 무료 세션 수 | 기본 3, `config/limits` 원격 튜닝 | `firestore-schema.md:286` |
| 3 | 업그레이드 CTA/가격 | v1 전면 비노출, 가시적 placeholder 없음 | FR-26 `PRD.md:217`, NG1 `PRD.md:37` |
| 4 | 도달 후 기본 문구 | `오늘 무료 학습을 다 했어요. 내일 또 만나요.` (마침표형 정본) | `dialogue-learning-flow.md:53`, `home-learning-entry.md:267` |
| 5 | streak 넛지 | streak>0 → `내일 이어가면 N일째예요` (N=streak+1) / 0 → 미추가 | `gamification-emphasis.md:145,188` |
| 6 | 금지 표현 | 손실형·카운트다운·소진·타이머 금지 | `gamification-emphasis.md:191` |
| 7 | 리셋 안내 | 암묵적("내일")만, 시각 미표기 | `firestore-schema.md:111-117` |
| 8 | 한도 표면 | 신규 공유 `LimitReachedPanel`; surface 3종; 온보딩 우선; `upgradeSlot=null` | 본 문서 §2 |
| 9 | 온보딩 첫 세션 | 중립 문구, 재시도 미노출, 홈 진입 | `01-onboarding-first-session.md:103,163` |
| 9b | 스냅샷 보유 게스트 표면 | 라이브 스냅샷 보유 시 `onboarding_first_session` 제외 → `dialogue_start_gate` | [01a](01a-onboarding-first-session-followups.md) §4c, `home-learning-entry.md:74` |
| 10 | 계측 | 정본 `limit_reached {remaining, surface}`; quota_blocked 정규화 선행 | `01-onboarding-first-session.md:182` |
| 11 | 완주+도달 동시 | 신규 결정 — 완주 1차, 한도 보조 인라인 1줄 | `gamification-emphasis.md:35,49` |
| 12 | 도달 보조 액션 | 홈 `기록 보기`만; 저장 카드 모듈 홈 비노출 | `home-learning-entry.md:228` |
| 13 | stale 처리 | stale 무해, 다음 시작서 서버 재검; fresh=포그라운드 응답값 | `home-learning-entry.md:74` |
| 14a | pre-limit 노출 surface | 완주 화면 1회성만, 홈 배지 없음 | `home-learning-entry.md:255` |
| 14b | pre-limit 문구 | `오늘 한 번 더 할 수 있어요` (비숫자) | `gamification-emphasis.md:191` |
| 14c | pre-limit 트리거 | 완주로 remaining→1 그 순간 1회, 재방문 홈 미반복 | `home-learning-entry.md:255-256` |
| 15a | home:258 재해석 | 홈 proactive 고지는 remaining==0만, ==1은 완주 화면 전담 | 본 문서 §10 |
| 15b | "명확히 안내" 의미 | 숫자 노출 의무 아님 — 비숫자 명료 고지 | `gamification-emphasis.md` §6 |
| 15c | 충돌 기록 거버넌스 | 정본 주석 패턴 + 필요시 ADR | `gamification-emphasis.md` §6 |
| 16 | 홈 메인 CTA | 항상 활성, fresh==0은 보조 고지만 | `home-learning-entry.md` §6.1 |

### 확정된 user 결정
| # | 결정 | 확정값 | 사유 |
|---|---|---|---|
| 14-prod | pre-limit 넛지 존재 자체 | **[confirmed] 완주 화면 1회성 비숫자 넛지 노출** | 제품 결정 확정 — 리텐션 훅. §0 금지목록(잔여 수 노출 금지)과 양립(비숫자 어포던스). |

## 12. v1 제외

- 결제/업그레이드 CTA·가격(`dialogue-learning-flow.md:316`)
- 잔여 횟수 상시 숫자 배지
- 리셋까지 카운트다운/타이머

---

## 교차 참조

- 데이터/리셋 정본: [firestore-schema.md](../design/firestore-schema.md) §4·§6
- 시작 게이트 상태: [dialogue-learning-flow.md](dialogue-learning-flow.md) §3·§11
- 게임화 톤·금지 목록: [gamification-emphasis.md](gamification-emphasis.md) §6·§7
- 홈 노출 정책: [home-learning-entry.md](home-learning-entry.md) §6
- 온보딩 도달 엣지: [01-onboarding-first-session.md](01-onboarding-first-session.md)
- 온보딩 첫 세션 후속: [01a-onboarding-first-session-followups.md](01a-onboarding-first-session-followups.md) §4
