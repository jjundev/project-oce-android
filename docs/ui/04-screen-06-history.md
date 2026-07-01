# UI 논의 — 화면: 기록 탭 (저장 카드)

> 상태: 논의용 스켈레톤 · 작성일: 2026-06-30 · 상위: [README](README.md) (표 4)
> 정본: [saved-cards.md](../ux/saved-cards.md) · [gamification-emphasis.md](../ux/gamification-emphasis.md) §8 · PRD §8.4, FR-16·17 · [ADR-0001](../adr/0001-card-id-determinism.md)
> 범례: 🔴 결정 필요 · 🟠 신규 설계 · 🟡 구현 대기 · 🟢 QA·위임

## 요소 인벤토리

| 요소 | 상태 | 비고 |
|---|:--:|---|
| 평생 통계 헤더 | 🟠 | `누적 N XP · 총 N시간 N분 · N일 학습` |
| 3종 탭 (SegmentedControl) | 🟠 | 표현/단어/문장 (EXPRESSION/WORD/SENTENCE) |
| 카드 리스트 (탭별) | 🟡 | SavedCard 3종 |
| 카드 인라인 펼침 + 복사 | 🟠 | 탭 → 설명/예문, 복사 |
| 스와이프 삭제 + undo 스낵바 | 🟠 | 톰스톤, 확인 다이얼로그 없음 |
| 빈 상태 (96dp) | 🟠 | 탭별, 유도 약하게 |

> 확정: 기록 탭 진입 시 통계 헤더 카운트업 세션당 1회 · 저장=북마크 토글(빈/채운+미세 골드) · 퀴즈 FAB v1 미노출 · 홈에 저장카드 비노출 · 삭제 undo 스낵바 N초.

---

## 논의 (항목별)

### R1 · 평생 통계 헤더 🟠
- **현황:** gamification §8 — 기록 탭 상단 `누적 N XP · 총 N시간 N분 · N일 학습`. 세션당 최초 진입 1회 카운트업([I3](03-signature-interactions.md)).
- **쟁점:** 헤더 레이아웃, 3지표 위계, 홈 스트립과 표현 일관성.
- **결정(rev2):** 인라인 통계 헤더 행(카드 아님) — 누적 N XP · 총 N시간 N분 · N일 학습 3지표, 진입 1회 카운트업(I3). 홈 스트립과 표현 일관(같은 토큰).

### R2 · 3종 탭 + 카드 리스트 🟠
- **현황:** SegmentedControl 3종(표현/단어/문장). 타입별 표시 필드는 스키마 그대로. 정렬 최신순(`createdAt`).
- **쟁점:** SegmentedControl(DS) 적용, 타입별 카드 외형 차이.
- **결정(rev2):** OneClickSegmentedControl 3종(표현/단어/문장, radius.pill)을 stickyHeader로 고정, 아래 LazyColumn. 타입별 카드 외형은 스키마 필드대로, 정렬 최신순(createdAt).

### R3 · 카드 인라인 펼침 + 복사 🟠
- **현황:** 탭 → 인라인 펼침(설명/예문), 복사 제공. TTS 다시 듣기는 v1 미제공(#20).
- **쟁점:** 펼침 애니메이션, 복사 affordance.
- **결정(rev2):** 탭 시 인라인 펼침(설명/예문) + 복사 IconButton. TTS 다시 듣기는 v1 미제공(#20).

### R4 · 스와이프 삭제 + undo 🟠
- **현황:** 스와이프 → 톰스톤 → 리스트 제거 + undo 스낵바([02](02-shared-components.md) C3). 확인 다이얼로그 없음. accessibility: 행마다 customAction "삭제"(스와이프 대안).
- **쟁점:** 스와이프 제스처 시각(배경/아이콘), undo 시간 N.
- **결정(rev2):** 스와이프 배경=state.error+삭제 아이콘 → 톰스톤 → 리스트 제거 + undo 스낵바(C3, N초). 확인 다이얼로그 없음. 행마다 customAction "삭제"(스와이프 대안).

### R5 · 빈 상태 🟠
- **현황:** saved-cards §4.1 — 탭별 96dp 아이콘 + 해요체 문구(예: `마음에 든 문장을 저장하면 여기에 모여요.`). 학습 시작 유도는 **약하게**(주 CTA는 홈 소유).
- **쟁점:** 유도 강도 구체화([05](05-open-decisions.md)), 아이콘(F1 의존).
- **결정(rev2):** OneClickEmptyState(C5) 탭별 96dp 아이콘 + 해요체 문구. 학습 시작 유도는 약(ctaStrength none/ghost, 주 CTA는 홈 소유).

### R6 · 정렬 가드 / 아카이브 정책 🟢
- **현황:** ADR-0001 cardId 결정성. revive 시 `createdAt` 정렬 보존이 서버 미강제 → 단위 테스트로 잠금(코드가 createdAt 덮으면 조용한 재정렬). 아카이브/정리 정책은 데이터 쌓인 뒤 후속 결정.
- **쟁점:** 대량 누적 시 UI 전략(TBD), 회귀 테스트.
- **선택지 / 제안 / 결정:** _(논의/구현)_
</content>
