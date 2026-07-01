# 딸깍영어 v1 — 온보딩 첫 세션 후속 결정

> 상태: 설계 확정 · 작성일: 2026-07-01 · 부모 문서: [01-onboarding-first-session.md](01-onboarding-first-session.md)
> 범위: 부모 문서(01) 이후 추가 논의에서 확정한 결정 모음. 첫 세션 턴 구조, 레벨 자기평가·계측, 미룬 기능 인계, 복귀·미완주 게스트의 한도 표면.
> 성격: 이 문서는 **온보딩 첫 세션 관점의 후속 결정만** 다룬다. 시각 값·컴포넌트 외형은 design 문서가, 한도 UX 정본은 [daily-limit-ux.md](daily-limit-ux.md)가, 홈·주제선택 표면은 [home-learning-entry.md](home-learning-entry.md)가, 이벤트 계약은 [analytics-events.md](analytics-events.md)가 소유한다. 이 문서는 그 정본들과 충돌하지 않으며, 필요한 인계·개정 항목은 §5에 명시한다.

## 0. 이 문서의 결정 분류

| 분류 | 의미 |
|---|---|
| **신규** | 부모 문서(01)와 인접 정본에 없던 새 결정 |
| **재확인** | 이미 확정된 결정의 명시적 재진술/비목표 기록 (동작 변경 없음) |
| **인계** | 값은 이 문서가 정하지 않고 owning 문서로 위임 |

## 1. 첫 세션 5턴 구조

### 1a. 첫 상대 대사 보장 방식 — 신규

첫 세션의 첫 상대 발화(opponent line)는 **서버 생성 대본(SSE)의 일부로 유지**한다. 클라이언트가 별도의 캔드 라인을 대본에 끼워넣는 방식은 도입하지 않는다. 클라이언트는 백엔드가 SSE로 보낸 완성 턴만 렌더하기 때문이다([dialogue-learning-flow.md:57](dialogue-learning-flow.md:57)).

"쉽게 따라 말할 수 있는 첫 문장" 보장은 다음 두 장치로 처리한다.

1. 첫 세션 난이도는 `쉬움`으로 강제되며, 이는 생성 시 `difficulty-bands.md`의 **easy(A2)** 밴드로 제약된다(별도 문장 고정이 아니라 난이도·길이 제약).
2. 생성이 실패하면 동일 `idempotencyKey`로 재시도한다([dialogue-learning-flow.md:49](dialogue-learning-flow.md:49), [01-onboarding-first-session.md:104](01-onboarding-first-session.md:104)).

즉 "정확히 이 문장"을 리터럴로 보장하지는 않는다. 리터럴 고정 첫 문장이 필요하다는 제품 요구가 생기면 백엔드 생성 계약(given first-line 주입)을 신설해야 하며, 그때 재논의한다.

### 1b. 세션 내 난이도 램프 — 재확인

첫 세션 5턴은 세션 내 난이도 상승 없이 전부 균일 `쉬움`으로 진행한다. 이는 [01-onboarding-first-session.md:21](01-onboarding-first-session.md:21)·[:64](01-onboarding-first-session.md:64)에서 이미 확정된 제약의 **명시적 비목표(no in-session ramp)** 기록이며 새 동작이 아니다. `difficulty`는 세션 단위 파라미터이지 턴 단위 필드가 아니다([01-onboarding-first-session.md:183](01-onboarding-first-session.md:183)).

### 1c. 턴 0 인트로 — 재확인

첫 세션에 별도의 턴 0 인트로/가이드 화면을 두지 않는다. 상대가 바로 말을 걸며 시작한다. [01-onboarding-first-session.md:17](01-onboarding-first-session.md:17)·[:25](01-onboarding-first-session.md:25)에서 이미 확정된 "소개·확인 화면 없음" 결정의 연장이다.

## 2. 레벨 자기평가와 계측

### 2a. 자동 보정 — 재확인 + 신규 명시

v1은 레벨 자동 보정을 넣지 않고 순수 수동(자기신고)을 유지한다. 이는 부모 문서와 정합한다([01-onboarding-first-session.md:20](01-onboarding-first-session.md:20)·[:129](01-onboarding-first-session.md:129)).

추가로 못박는다: **온보딩 첫 세션은 난이도가 `쉬움`으로 강제되므로 레벨 보정의 신호원으로 쓰지 않는다.** 첫 세션 점수는 "쉬움에서의 성능"일 뿐 자기평가가 맞았는지를 알려주지 못한다. 등급업/다운 제안은 난이도가 통제되지 않는 세션 #2 이후 데이터로만 가능하며, 이는 온보딩 스코프 밖이다.

### 2b. 첫 세션 점수 계측 — 인계 (미결 계약 명시)

첫 세션 턴 점수를 향후 보정 판단 근거로 **계측만** 남긴다(레벨 변경에는 쓰지 않는다). 단, 현재 [analytics-events.md](analytics-events.md) §4의 이벤트 매트릭스에는 턴 단위 점수 파라미터가 없다(`turn_completed`는 `input_mode`만 전달). 따라서 이 계측은 **아직 존재하지 않는 신규 이벤트/파라미터 결정**이며, 스키마 확정은 `analytics-events.md`가 소유한다. 이 문서는 "첫 세션 점수를 계측 목적으로만 남긴다"는 의도만 인계한다.

## 3. 미룬 기능 인계 계약 — 인계

온보딩이 미루는 기능(직접입력·닉네임·리마인더)의 **UI 표면·구체 조건은 각 owning 문서가 소유**하고, 이 문서는 "온보딩이 끝날 때 무엇을 어디로 넘기는지"만 계약으로 표기한다.

| 미룬 기능 | 온보딩 중 | 인계 대상 · 재등장 조건 |
|---|---|---|
| 직접입력(직접 상황 입력) | 숨김 | [home-learning-entry.md:139-140](home-learning-entry.md:139) — **재방문 주제선택 화면**의 하단/보조 영역. (특정 세션 오디널이 아니라 "재방문" 조건) |
| 닉네임 | 받지 않음 | [settings-data-account.md](settings-data-account.md) — 설정에서 상시 편집(선택값) |
| 리마인더 opt-in | 묻지 않음 | [notification-reminder.md](notification-reminder.md) — `completedSessionCount == 2` 시점 제안 |

주의: 이전 논의에서 직접입력 재등장을 "세션 #2"로 픽스하려 했으나, owning 문서의 조건은 "재방문"(모든 재방문)으로 더 넓다. 오디널로 고정하면 두 문서가 같은 UI에 다른 트리거를 명시하게 되므로 **owning 조건을 그대로 따른다.**

## 4. 복귀·미완주 게스트와 한도 표면

### 4a. 재개와 시작 한도 — 재확인 (사실 기록)

이어하기(로컬 스냅샷 복원)는 시작 게이트(`/llm task=dialogue`)를 **재호출하지 않으므로** 한도를 재소비하지 않는다. 한도는 세션 생성 시점에 소모되고([home-learning-entry.md:74](home-learning-entry.md:74)), 이어하기는 동일 `sessionId`를 재사용하는 복원이다([01-onboarding-first-session.md:109](01-onboarding-first-session.md:109)).

[01-onboarding-first-session.md:26](01-onboarding-first-session.md:26)의 "첫 세션도 dialogue 시작 한도에 포함"은 **개별 세션 start 카운트**를 뜻하며 재개와 무관하다.

> 각주: "재개가 게이트를 재호출하지 않는다"는 상태도상 추론이다. [daily-limit-ux.md:16](daily-limit-ux.md:16)은 "일일 캡은 dialogue 시작만 카운트"만 명시하고, 재개→게이트 엣지가 상태도에 존재하지 않음으로부터 도출된다. 별도 bypass 코드 경로를 신설하는 것이 아니다(우회할 게이트 호출 자체가 없다).

### 4b. 한도 도달 카피 — 신규 (교정) + 인계

복귀·미완주 게스트가 한도에 도달하면 **기본 `LimitReachedPanel` 중립 문구**([daily-limit-ux.md:39](daily-limit-ux.md:39), "오늘 무료 학습을 다 했어요. 내일 또 만나요.")를 그대로 쓴다. first-time 전용 한도 카피는 존재하지 않는다 — fresh UID는 구조상 한도에 도달하지 못하기 때문이다([01-onboarding-first-session.md:63](01-onboarding-first-session.md:63)).

한도 패널에 "이어하기" 보조 액션을 추가하지 않는다. `LimitReachedPanel`의 액션셋 정본은 [daily-limit-ux.md](daily-limit-ux.md)(§2 surface 표, §11)가 소유하며 "홈 기록 보기"만 열거한다. 게다가 재개 사용자는 §4a에 따라 이 패널에 도달하지 않는다.

### 4c. 라이브 스냅샷 게스트의 표면 경계 — 신규

복귀 미완주 게스트가 어느 한도 표면을 보는지의 경계를 **라이브 스냅샷 유무**로 정한다.

| 상태 | 경로 |
|---|---|
| 라이브 스냅샷 있음 | 이어하기는 게이트 없이 홈에서 진행. "새로 시작"을 골라 서버가 `{remaining:0}`으로 거부할 때만 한도 패널 노출, 이때 fresh UID가 아니므로 `surface=dialogue_start_gate` |
| 라이브 스냅샷 없음 | 재방문 미완주 게스트로서 시작 게이트 거부 시 `surface=onboarding_first_session` |

이 경계는 [daily-limit-ux.md:26-27](daily-limit-ux.md:26)의 두 표면 정의를 전제로 하되, "라이브 스냅샷 보유 게스트를 온보딩 표면에서 제외"한다는 규칙 자체는 **신규 결정**이다. §5에 owning 문서 개정 항목으로 등록한다.

## 5. Owning 문서 개정 (적용 완료)

이 문서의 결정을 발효하기 위한 정본 문서 개정은 아래와 같이 반영됐다. 값은 각 owning 문서가 소유한다.

| 문서 | 반영 내용 | 위치 | 출처 결정 |
|---|---|---|---|
| [analytics-events.md](analytics-events.md) | `turn_completed.writing_score`(int) 신설 · 계측 전용 | §4 매트릭스, §5.4, 결정표 #8b | §2b |
| [daily-limit-ux.md](daily-limit-ux.md) | 라이브 스냅샷 보유 게스트 `onboarding_first_session` 제외 → `dialogue_start_gate` | §2, §7, 결정표 #9b | §4c |

## 6. 검증 메타

이 문서의 7개 결정(+누락 1)은 격리된 fresh-context 리뷰(fact-checker + critic, 이후 재검증 1회)로 5개 축(모순/숨겨진 가정/누락/현실 충돌/모호함)에서 검토됐다. 초안의 Blocker 5건(1a 생성 모델 충돌, 2b 계측 미지정, 3 오디널 충돌, 4b 패널 액션 충돌, 4c 표면 누락)은 위 본문에서 ground truth에 정렬해 해소했다. 최종 판정 SHIP.
