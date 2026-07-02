# ADR-0005 — 로딩 퀴즈 ≠ 복습 퀴즈 (v1 스코프 구분)

> 상태: 수용(Accepted) · 작성일: 2026-07-02
> 관련: `PRD.md` NG4 · `docs/ux/loading-quiz-interstitial.md` · `docs/ux/dialogue-learning-flow.md` §14

## 맥락

PRD NG4는 "복습 퀴즈, FCM 서버 푸시 → v1.1 보류"를 v1.0 확정 비목표로 둔다. `dialogue-learning-flow.md` §14 "v1 제외"도 "복습 퀴즈 진입"을 명시하고, `saved-cards.md`·`gamification-emphasis.md`는 레거시 퀴즈 FAB를 v1에서 의도적으로 override한다.

이 상황에서 "대본 생성 대기 중 무채점 로딩 퀴즈"([loading-quiz-interstitial.md](../ux/loading-quiz-interstitial.md))를 v1에 도입하려 하면, 표면적으로 "퀴즈를 v1에 넣는다"가 되어 NG4와 충돌하는 것처럼 보인다. 두 "퀴즈"가 같은 기능인지 판정이 필요하다.

## 결정

**로딩 퀴즈와 복습 퀴즈는 별개 기능이며, NG4는 로딩 퀴즈에 적용되지 않는다.**

| | 복습 퀴즈 (NG4, v1.1 보류) | 로딩 퀴즈 (본 결정, v1) |
|---|---|---|
| 목적 | 학습 루프 리텐션(저장 카드 복습) | 생성 대기 이탈 방지(웨이트 필러) |
| 데이터 결합 | 저장 카드·진도·streak에 결합 | 점수·streak·저장에 결합 0 |
| 콘텐츠 | 사용자 학습 이력 기반 | 런타임과 무관한 로컬 정적 뱅크 |
| 채점 | 학습 성취 측정 | 무채점(결과 없음) |
| 표면 | 홈/기록 등 학습 진입 | 대본 생성 대기 로딩 화면 한정 |

`dialogue-learning-flow.md` §14의 제외 조항이 "복습 퀴즈 진입"으로 **특정**되어 있어, 퀴즈 일반 금지가 아니라는 점이 이 구분을 문서상 뒷받침한다.

## 대안과 기각 사유

- **v1.1로 descope(NG4를 퀴즈 일반 금지로 해석):** 로딩 퀴즈는 학습 루프 기능이 아니라 대기 UX라 NG4의 리스크(학습 기능 범위 확장)와 무관하다. 과도한 보수 해석으로 기각.
- **NG4를 명시적으로 override:** 애초에 다른 기능이라 override가 필요 없다. 불필요한 결정 부채로 기각.

## 귀결

- `loading-quiz-interstitial.md`가 로딩 퀴즈 정본이 된다.
- `dialogue-learning-flow.md:66`의 "생성 중 중립 카피만" 규칙에 웨이트 인터스티셜 예외 카브아웃을 추가한다(반영 완료). 해당 규칙의 취지는 가짜 대화 placeholder 금지이지 대기 콘텐츠 전면 금지가 아니다.
- 향후 "퀴즈" 언급 시 두 기능을 혼동하지 않도록 본 ADR을 참조한다.
