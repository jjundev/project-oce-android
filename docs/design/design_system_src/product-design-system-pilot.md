# 제품 디자인 시스템 — 첫 QA 파일럿: 대화 학습 + 턴 피드백 시트

> **상태:** 설계 초안 · **작성일:** 2026-06-30
> **목적:** [product-design-system.md](product-design-system.md)의 컴포넌트 계약을 **첫 실검증 케이스**로 고정한다. 대상 플로우는 `대화 학습 + 턴 피드백 바텀시트`다.
> **산출물:** 컴포넌트 × 상태 축 **mock state 매트릭스** + 각 mock의 QA 13게이트 체크.
> **위임:** 상태 축·시트 IA·접근성은 [foundations.md](foundations.md)(§1·§3·§2, 외부 정본의 인폴더 mirror) · 토큰 [design-tokens.md](design-tokens.md).

---

## 1. 대상 컴포넌트

`MicButton` · `Waveform` · `ChatBubble` · `OneClickFeedbackSheet`/`Section` · `VennDiagram` · `OneClickButton`(다음/더 보기/재시도).

---

## 2. Mock state 매트릭스

각 행 = 렌더해야 할 mock 1개. 구현자는 이 셋을 그대로 미리보기/스크린샷 케이스로 만든다.

### 2.1 MicButton × `MicState`

| mock | `MicState` | 외형(토큰) | announce | 게이트 초점 |
|---|---|---|---|---|
| MIC-1 | `Ready` | `voice.ready` 동심원 | "녹음 시작" | #2(96dp)·#7 |
| MIC-2 | `Recording` | `voice.recording` + 리플 3겹 · Waveform 활성 | "녹음 중" | #5(reduce-motion)·#7 |
| MIC-3 | `Analyzing` | `voice.analyzing` + 프로그레스 링 | "분석 중" | #4·#7 |
| MIC-4 | `Complete` | `voice.complete` | "완료" | #1 |

### 2.2 ChatBubble × `TurnPhase` / `OpponentPlayback`

| mock | 조합 | 외형 |
|---|---|---|
| BUB-1 | `OpponentTurn` + `Playing` | Opponent 버블 `surface.card`, 재생 표시 |
| BUB-2 | `OpponentTurn` + `Idle` | Opponent 버블 정지 |
| BUB-3 | `LearnerTurn` | User 버블 `brand.primary`, 본체 `radius.18`/꼬리 `radius.4` |

### 2.3 FeedbackSheet/Section × `FeedbackSlimRequest` / `FeedbackDeepSideRequest`

| mock | 상태 | 시트 외형 | 게이트 초점 |
|---|---|---|---|
| FB-1 | slim `Loading` | 3섹션 시머 스켈레톤 | #5 |
| FB-2 | slim `Ready` | writingScore→grammar→naturalExpression 실데이터, `더 보기`/`다음` 활성 | #1·#4 |
| FB-3 | slim 부분 실패 `ErrorRecoverable` | 해당 섹션 인라인 재시도 | #11(포커스) |
| FB-4 | slim `ErrorBlocked` | "건너뛰고 다음으로" | #4 |
| FB-5 | deep `Loading`(더 보기 후) | conceptualBridge→toneStyle→paraphrasing 시머, `다음` 여전히 활성 | #5 |
| FB-6 | deep `Ready` | 3블록 실데이터, paraphrasing 저장 버튼 | #2 |
| FB-7 | deep `Canceled`(다음 이동) | 인라인 영역만 정리, slim/`다음` 무영향 | — |

> 색 단독 금지(#4): grammar `incorrect`=취소선+텍스트, 의미색 highlight=색+밑줄/배경, writingScore=점수 숫자 동반(`turn-feedback-ia.md` §6·§10).

### 2.4 VennDiagram (conceptualBridge)

| mock | 조건 | 게이트 초점 |
|---|---|---|
| VEN-1 | 라이트 + 대비 가드 통과 | #1·#8(텍스트 대안) |
| VEN-2 | 다크 + 대비 가드 통과 | #1·#8 |
| VEN-3 | 색거리 부족 입력 → 런타임 보정 발동 | #9(`VennColorContrastGuardTest` 포트) |

---

## 3. QA 13게이트 체크 (파일럿 적용)

각 mock은 [product-design-system.md §7](product-design-system.md)의 13게이트를 통과해야 한다. 파일럿에서 특히 검증할 항목:

- **#9·#12·#13(lint/테스트 도구)** 는 M0 구현물이므로, 파일럿은 *요구사항이 충족되는 mock 형태*만 고정하고 도구 실행은 M0에서.
- **#10 영어 콘텐츠 `LocaleList`**: ChatBubble/Section의 영어 대사·표현에 영어 로케일 부여 검증(`accessibility.md:122`).
- **#7 음성 상태 announce**: MIC-1~4의 announce 문자열 실측.

---

## 4. 비목표

- 실제 Compose 구현·스크린샷 자동화 = M0.
- 본 파일럿은 **계약 검증 케이스의 명세**이며, 통과/실패 판정은 M0 구현 후 수행.
