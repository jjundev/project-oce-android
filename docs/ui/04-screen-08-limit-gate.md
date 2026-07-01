# UI 논의 — 화면: 일일 한도 게이트

> 상태: 논의용 스켈레톤 · 작성일: 2026-06-30 · 상위: [README](README.md) (표 4)
> 정본: [daily-limit-ux.md](../ux/daily-limit-ux.md) · PRD §9.7, FR-26·27 · NG1(수익화 비목표)
> 범례: 🔴 결정 필요 · 🟠 신규 설계 · 🟡 구현 대기 · 🟢 QA·위임

## 표면 인벤토리 (LimitReachedPanel surface 3종)

| surface | 상태 | 본문 / 보조 액션 |
|---|:--:|---|
| `dialogue_start_gate` | 🟠 | 도달 문구, 보조 액션 없음 → 홈 복귀 |
| `home` | 🟠 | fresh `remaining==0` 보조 고지, 보조 액션 `기록 보기` |
| `onboarding_first_session` | 🟠 | 도달 문구, 보조 액션 없음 → 홈 |
| 완주 화면(도달 전) | 🟠 | 패널 아님 — 보조 인라인 1줄(축하 1차) |

> 확정: 비상업 중립 — `upgradeSlot` 렌더 null(고스트/티저 없음) · 잔여 수 노출 금지(비숫자) · 리셋 안내는 자연어("내일")만, 시각/카운트다운 배제 · 홈 메인 CTA 항상 활성(탭스루).

## 카피 인벤토리

| 시점 | 문구 |
|---|---|
| 도달 전(remaining→1) | `오늘 한 번 더 할 수 있어요` |
| 도달 기본 | `오늘 무료 학습을 다 했어요. 내일 또 만나요.` |
| 도달 + streak | `…내일 이어가면 N일째예요.` (N=서버 streak+1) |

---

## 논의 (항목별)

### L1 · LimitReachedPanel (surface별 분기) 🟠
- **현황:** daily-limit §2 — 신규 공유 컴포넌트([02](02-shared-components.md) C18). surface별 본문/보조 액션 분기. 차단형 진입 표면(`dialogue_start_gate`)은 차단 게이트[C].
- **쟁점:** 패널 레이아웃(격려 1차 + 정보 보조), streak 넛지([02](02-shared-components.md) C14) 결합.
- **결정(rev2):** OneClickLimitReachedPanel(C18) — 중앙 96dp 아이콘 + 중립 카피 + streak 넛지칩(C14, 🔥 N일). upgradeSlot=null(고스트/티저 없음). 차단형 진입 표면(dialogue_start_gate·onboarding)=전체화면 BlockingGate(C12 공유 레이아웃), home=인라인 보조 고지. 잔여 수 비노출, 리셋="내일"만.

### L2 · 완주 + 한도 도달 동시 🔴
- **현황:** daily-limit §4 — 마지막 무료 세션 완주 시 축하(1차) + remaining==0 도달 문구(보조) 동시(신규 결정). 패널 아닌 인라인 1줄.
- **쟁점:** [요약 화면](04-screen-05-summary.md) SM6과 동일 결정([05](05-open-decisions.md) P6).
- **결정:** **완주 축하 1차 hero + 한도 도달 보조 인라인 1줄**(요약 하단, SM6과 동일) → [05](05-open-decisions.md) P6 확정.

### L3 · 홈 at-limit 고지 방식 🔴
- **현황:** daily-limit §10 — "명확히 안내"가 숫자 N 노출 의무 아님. fresh `remaining==0`만 보조 고지(`기록 보기`는 기록 탭 이동만, 저장카드 복기 모듈 노출 안 함). 거버넌스상 필요 시 ADR 승격.
- **쟁점:** 고지 방식(주석 패턴)·ADR 승격 여부([05](05-open-decisions.md) P7), [홈 H6](04-screen-02-home.md)과 정합.
- **결정:** fresh `remaining==0`일 때만 **비숫자 보조 고지 + `기록 보기`**, **ADR 미승격**(어긋나면 그때 승격) → [05](05-open-decisions.md) P7 확정.

### L4 · 구두점·계측명 정합 🟢
- **현황:** 정본 구두점 = 마침표형. PRD:217 엠대시는 표기 차이로 둠. 계측은 `quota_blocked` → `limit_reached`로 정규화(surface ∈ {home, dialogue_start_gate, onboarding_first_session}).
- **쟁점:** event id 최종은 [analytics-events.md](../ux/analytics-events.md)에서([05](05-open-decisions.md) P17).
- **결정:** 계측 정합 위임.
</content>
