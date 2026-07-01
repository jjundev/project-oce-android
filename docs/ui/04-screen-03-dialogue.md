# UI 논의 — 화면: 대화 학습

> 상태: 논의용 스켈레톤 · 작성일: 2026-06-30 · 상위: [README](README.md) (표 4)
> 정본: [dialogue-learning-flow.md](../ux/dialogue-learning-flow.md)(초안) · PRD §8.2, FR-5~14
> 범례: 🔴 결정 필요 · 🟠 신규 설계 · 🟡 구현 대기 · 🟢 QA·위임

## 화면/요소 인벤토리

| 요소 | 상태 | 비고 |
|---|:--:|---|
| 채팅 (ChatBubble) | 🟡 | 상대역=surface.card, 학습자=brand.primary |
| 진행률 헤더 (`N of M`) | 🟠 | 툴바 진행률 바 |
| 한국어 과제(발판) 카드 | 🟠 | "이 한국어를 영어로 말해보세요" |
| 4상태 마이크 시트 | 🟡 | [I1](03-signature-interactions.md) |
| 실시간 파형 | 🟡 | [I2](03-signature-interactions.md) |
| TTS 재생 / 다시 듣기 컨트롤 | 🟠 | 재생 중 마이크 미개방 |
| transcript + 한 줄 격려(transient) | 🟠 | writingScore 렌더 시 해제 |
| `채팅으로 입력하기` (텍스트 대체) | 🟠 | 실패 대체 아닌 선택지로 표현 |
| 턴 피드백 시트 (slim+deep) | 🟡 | [04-feedback](04-screen-04-feedback-sheet.md) |
| 완료 화면 (`요약 보기`) | 🟠 | 완주 트리거(탭이 완주의 일부) |
| 중단 확인 다이얼로그 | 🟠 | 단일 확인 [02](02-shared-components.md) C1 |

> 확정: 8개 상태축 분리(SessionPhase·StartGate·TurnPhase·MicState·OpponentPlayback·FeedbackSlim·FeedbackDeep·SummaryHandoff) · MicState 4값 고정 · slim 3섹션 순서(writingScore→grammar→naturalExpression) · `다음`은 3섹션 완료 후 활성(점수 gate 미계승) · 무음/빈 transcript 미전송.

---

## 논의 (항목별)

### D1 · 한국어 과제(발판) 표시 🟠
- **현황:** 학습자 턴 상단에 한국어 과제 제시. 전용 컴포넌트 미정의.
- **쟁점:** 카드 vs 인라인, ChatBubble과 시각 구분.
- **결정(rev2):** 입력 독 위 고정 카드(OneClickCard, ChatBubble 아님)로 과제≠대화 시각 분리. 항상 노출. (사용자 확정)

### D2 · TTS 재생 / 다시 듣기 컨트롤 🟠
- **현황:** 상대역 턴 = 말풍선 렌더 → TTS 재생 → 자동 진행. 다시 듣기 허용, 재생 중 마이크 미개방. TTS 실패/타임아웃/음소거 → 텍스트만 자동 진행(B1). 다시 듣기는 오디오 실제 있을 때만 노출(캐시 v1 비필수).
- **쟁점:** 재생 인디케이터(IconButton), 다시 듣기 버튼 동작/지연.
- **결정(rev2):** 상대역 말풍선 렌더→TTS 재생(재생 표시 IconButton)→자동 진행. 다시 듣기는 오디오 실재 시만 노출, 재생 중 마이크 미개방. TTS 실패/타임아웃/음소거 시 텍스트만 자동 진행(B1).

### D3 · transcript 표시 + (수정 UI) 🔴
- **현황:** dialogue §6.1 — transcript가 실제 발화와 다르면 **인라인 수정 UI를 고정하지 않는다**. UX 논의 항목 = `다시 말하기`·`채팅으로 입력하기` 재시도 경로. 빈 transcript는 턴 미완료(`MicState.Complete(emptyTranscript)`, 화면 미기록).
- **쟁점:** transcript 표시 방식, 수정 affordance 제공 여부([05](05-open-decisions.md) P5).
- **결정:** **v1 수정 UI 없음** — `다시 말하기`/`채팅으로 입력하기`만 → [05](05-open-decisions.md) P5 확정. (transcript 표시 시각은 논의 유지)

### D4 · transient 음성 격려 🟠
- **현황:** 스피킹 분석 = transcript + 한 줄 격려(숫자 점수 없음). writingScore 첫 섹션 렌더 시점에 해제(동시 노출 금지).
- **쟁점:** 토스트 vs 인라인 표시.
- **결정(rev2):** 인라인 표시(토스트 아님) — transcript 인근 한 줄 격려(숫자 점수 없음). writingScore 첫 섹션 렌더 시점에 해제(동시 노출 금지).

### D5 · 완료 화면 🟠
- **현황:** "학습 완료 상태 표시" + `요약 보기` 버튼. 완주/XP/streak 기준 = 마지막 학습자 턴 이후 요약 라우트 진입 시점. `요약 보기` 탭이 완주의 일부(누르지 않고 이탈 시 미완주).
- **쟁점:** 완료 셀러브레이션 강도(원형 체크 등), 게임화 연출과 분리.
- **결정(rev2):** 절제된 완료 표시(과한 셀러브레이션 없음, 게임화 연출과 분리) + 요약 보기 CTA. 요약 보기 탭이 완주의 일부(미탭 이탈 시 미완주).

### D6 · 중단 확인 다이얼로그 🟠
- **현황:** 단일 중단 확인(더 강한 별도 문구 고정 안 함). snapshot 있으면 이어하기 가능.
- **쟁점:** [02](02-shared-components.md) C1 다이얼로그 적용.
- **결정(rev2):** OneClickDialog(C1) 단일 중단 확인. snapshot 있으면 이어하기 가능. 별도 강조 문구 고정 안 함.

### D7 · 인라인 재시도 / 건너뛰기 + 대본 한도 안내 🔴
- **현황:** slim 섹션 인라인 재시도[A] → 반복 실패 시 `이번 피드백은 건너뛰고 다음으로 갈까요?`. 대본 생성 중 "한도 차감 여부가 애매해 보이지 않는 안내" 필요(구체 미정).
- **쟁점:** "반복 실패" 임계 횟수([05](05-open-decisions.md) P4), 한도 차감 안내 문구/위치, Analyzing 취소 4사유(userCanceled/lifecycleCanceled/staleIgnored/speakingFailed) 사용자 표현.
- **결정(P4):** **섹션 단위 독립 카운터, 동일 섹션 누적 2회** 후 `건너뛰고 다음으로`, 재시도는 사용자 탭 수동 → [05](05-open-decisions.md) P4 확정. (한도 차감 안내·Analyzing 4사유 표현은 논의 유지)
</content>
