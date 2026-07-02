# UI 논의 — 화면: 세션 요약

> 상태: 논의용 스켈레톤 · 작성일: 2026-06-30 · **prototype-verified(2026-07-02)**: 본 화면 결정을 `Prototype Flow` 대응 상태와 육안 대조 · 상위: [README](README.md) (표 4)
> 정본: PRD §8.3, FR-15 · [gamification-emphasis.md](../ux/gamification-emphasis.md) §4 · [saved-cards.md](../ux/saved-cards.md) §3.3
> 범례: 🔴 결정 필요 · 🟠 신규 설계 · 🟡 구현 대기 · 🟢 QA·위임

## 요소 인벤토리 (점진 스켈레톤 렌더)

| 블록 | 상태 | 비고 |
|---|:--:|---|
| 종합 점수 (56sp, brand.primary) | 🟠 | 작문 품질 숫자 |
| "오늘의 적립" 스트립 | 🟠 | **종합점수와 별도 블록**(같은 행 금지) |
| 하이라이트 (최대 1) | 🟠 | 가장 잘한 순간 |
| 표현 개선 카드 (최대 8) | 🟠 | before/after, 저장 토글 |
| 신규 단어 카드 (최대 12) | 🟠 | 저장 토글 |
| 북마크 문장 (최대 8, 최신순) | 🟠 | deep 패러프레이즈가 소스 |
| 코칭 (잘한 점/개선점) | 🟠 | 첫 세션도 노출 |
| 부분 실패 재시도 | 🟠 | 성공 섹션 먼저 노출 |

> 확정: gamification §4.2 — 종합 점수와 게임화 적립 스트립은 **별도 블록**. 적립 순서 = ① streak `🔥` → ② 학습시간 → ③ XP. 슬롯머신 카운트업([I3](03-signature-interactions.md))은 완주 보상 surface 한정.

---

## 논의 (항목별)

### SM1 · 종합 점수 + 적립 스트립 레이아웃 🟠
- **현황:** 종합점수 56sp `type.scoreDisplay`. 적립 스트립(3지표)은 아래 별도 블록, 슬롯머신 롤업. same-day 2번째 세션은 streak 정적(라벨 유지).
- **쟁점:** 두 블록 시각 위계·간격, 격려 1차 + 정보 보조(ux-writing 위계).
- **결정(rev2):** 종합 점수 블록(56sp type.scoreDisplay, brand.primary) → 세로 24dp(space-section-gap) → 적립 스트립 별도 블록(같은 행 금지). 격려 1차 + 정보 보조(ux-writing 위계). same-day 2번째 세션은 streak 정적.

### SM2 · 섹션별 카드 (하이라이트/표현/단어/북마크/코칭) 🟠
- **현황:** 각 시머 스켈레톤 + "더 보기" 토글(레거시). 표현 8·단어 12·북마크 8 상한. 저장 토글(WORD/EXPRESSION은 요약, SENTENCE는 deep에서).
- **쟁점:** Card 재사용 범위, "더 보기" 토글 유지 여부, 상한 초과 표시.
- **결정(rev2):** 각 섹션 OneClickCard 재사용 + 시머 스켈레톤 점진 렌더 + "더 보기" 토글 유지. 상한(표현8·단어12·북마크8)까지만 렌더, 초과 미표시.

### SM3 · 저장 토글 (SaveState) 🟠
- **현황:** saved-cards §3.1 — Unsaved/Saving/Saved/Failed 항목별. optimistic, 부분 실패 시 성공 섹션만 저장 활성화. 북마크 토글(빈/채운 + 미세 골드 `#FFC107`).
- **쟁점:** 토글 상태 시각([02](02-shared-components.md) C14 인접), Saving 스피너/Failed 재시도.
- **결정(rev2):** 항목별 SaveState(Unsaved/Saving/Saved/Failed), optimistic. 북마크 토글(빈/채운 + game.save-gold). Saving=미세 스피너, Failed=인라인 재시도. 부분 실패 시 성공 섹션만 저장 활성.

### SM4 · coaching 노출 (첫 세션 포함) 🟠
- **현황:** 잘한 점/개선점. 온보딩 첫 세션 요약에도 노출(부분 실패 계약 포함). 첫 세션은 격려 2배.
- **쟁점:** 일반 vs 첫 세션 차등 표현.
- **결정(rev2):** 잘한 점/개선점 표준 노출. 첫 세션(온보딩)은 코칭 격려 2배(카피 위계), 구조 동일.

### SM5 · 첫 완주 특례 + 마일스톤 카피 🔴
- **현황:** gamification §4.5 — 첫 완주 `1일째 시작! 🔥 첫 대화 완주했어요`(미검증 디자인). 마일스톤 1·3·7·14·30일 축하 변주(전용 뱃지/화면 없음). 카피 최종 검수 미완.
- **쟁점:** 문구 확정·검수([05](05-open-decisions.md) P12).
- **결정:** **초안 톤 그대로 채택** [confirmed] → 첫 완주 `첫 대화 완주했어요! 🔥 오늘부터 1일째예요.` · 마일스톤 1일 `🔥 1일째 — 시작이 좋아요!` / 3일 `🔥 3일째 — 리듬이 붙고 있어요!` / 7일 `🔥 7일째 — 일주일 완성했어요!` / 14일 `🔥 14일째 — 2주 연속이에요!` / 30일 `🔥 30일째 — 한 달 동안 매일 말했어요!` → [05](05-open-decisions.md) P12.

### SM6 · 완주 + 한도 도달 동시 🔴
- **현황:** daily-limit §4 — 마지막 무료 세션 완주 시 축하 1차 + remaining==0 도달 문구 보조 동시 표시(신규 결정).
- **쟁점:** 축하/한도 보조 1줄 배치·우선순위([05](05-open-decisions.md) P6).
- **결정:** **완주 축하/적립 스트립 1차 hero + 한도 도달 보조 인라인 1줄**(요약 하단, ux-writing 위계) → [05](05-open-decisions.md) P6 확정.

### SM7 · 요약 복원/재시도 상태 🟠
- **현황:** dialogue §10 위임 — turn buffer 지속성 / summary handoff 복원 / 부분 실패 후 재시도 상태(본 화면 밖 미설계).
- **쟁점:** SummaryPreparing/SummaryViewing 상태 표현.
- **결정(rev2):** SummaryPreparing=시머 스켈레톤, SummaryViewing=실데이터. turn buffer 복원 실패/부분 실패 시 성공 섹션 먼저 노출 + 실패 섹션 인라인 재시도.
</content>
