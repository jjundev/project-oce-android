# 딸깍영어 v1 — 턴 피드백 정보 구조 (slim + deep)

> 상태: 설계 초안 · 작성일: 2026-06-30
> 범위: 학습자 턴 직후 피드백(slim + deep)의 정보 구조 — surface, 섹션 배치, 렌더 순서, 진입점, 저장 affordance, 실패/접근성 규약.
> 제외: 컬러 hex·타이포 등 시각 값([design-tokens.md](../design/design_system_src/design-tokens.md)), 컴포넌트 외형·모션([product-design-system.md](../design/design_system_src/product-design-system.md)). 피드백 *상태 축*은 `dialogue-learning-flow.md` §7이 소유한다(본 문서는 그 위의 화면 배치를 정의). 단어/표현/문장 *저장 surface*(요약 화면·기록 탭)는 본 IA 범위 밖이다 — `PRD.md` §8.4 / FR-16·17 소관.
> 근거: `PRD.md` §8.2~8.3, FR-10·11·15·16 · `docs/ux/dialogue-learning-flow.md` §6~9 · `docs/design/prompts/feedback-slim.md` · `docs/design/prompts/feedback-deep.md` · `docs/design/firestore-schema.md` §saved_cards · 레거시 `archive/android` 분석(`딸깍영어_UIUX_분석_보고서.md`)

## 1. 목표

턴 피드백은 학습자가 방금 말한/입력한 영어에 대해 **가벼운 즉시 피드백(slim)** 을 한 화면에서 받고, 원할 때만 **깊은 분석(deep)** 을 같은 자리에서 펼치는 구조다. 슬림은 항상, 딥은 온디맨드. 점수가 낮아도 진행을 막지 않는다.

핵심 성공 기준:
- 사용자는 작문 점수 → 문법 교정 → 자연스러운 표현을 항상 같은 순서로 본다.
- 더 깊이 보고 싶을 때만 `더 보기`로 펼치고, 그동안 다음 턴으로 갈 자유를 잃지 않는다.
- 색에 의존하지 않고도(색각 접근성) 무엇이 틀렸고 무엇이 개선인지 안다.
- 점수는 강제가 아니라 정보다 — 틀려도 다음으로 간다.

## 2. Surface — 단일 피드백 시트

턴 피드백은 채팅 RecyclerView 위 **하단 바텀시트** 한 장에 싣는다. 별도 라우트로 전환하지 않는다(레거시 `보고서:189-190`의 4상태 시트 IA 계승, `PRD.md:276` "인터랙션 무대: 주요 행위는 바텀시트 위에서").

```text
[바텀시트 · 24dp 라운드 · 드래그 핸들 · NestedScrollView]
  ├─ ① writingScore        : 점수 + 격려              (slim)
  ├─ ② grammar             : 교정 문장 + 설명          (slim)
  ├─ ③ naturalExpression   : 자연스러운 표현 + 이유     (slim)
  ├─ [ 더 보기 ]  (slim 3섹션 완료 후 활성)
  │     └─ 탭 시 같은 시트에 인라인 확장:
  │        ├─ ④ conceptualBridge : 직역 + 설명 + 벤다이어그램  (deep)
  │        ├─ ⑤ toneStyle        : 5단계 톤 시크바             (deep)
  │        └─ ⑥ paraphrasing     : 3카드 + 각 저장 버튼        (deep)
  └─ [ 다음 ]  (slim 3섹션 완료 시 활성 · 점수 gate 없음)
```

- slim은 `FeedbackSlimRequest` 축, deep은 `FeedbackDeepSideRequest` 축으로 독립(`dialogue-learning-flow.md:29-30,157`). 두 축은 같은 시트를 공유하되 로딩/실패/취소가 서로를 막지 않는다.
- deep는 라우트/시트 전환 없이 `더 보기` 아래로 이어붙인다(레거시 6섹션 단일 시트, `보고서:190`).

## 3. slim 섹션 — 항상 노출

`feedback.slim`은 세 섹션을 고정 순서로 렌더한다. 순서는 프롬프트 `propertyOrdering`이 결정적으로 emit하는 순서와 일치한다(`feedback-slim.md:3`).

```text
writingScore → grammar → naturalExpression
```

각 섹션은 도착 전 **시머 스켈레톤**, SSE 파싱 완료 시 해당 섹션만 실데이터로 교체(섹션별 점진 렌더, `dialogue-learning-flow.md:138`).

### 3.1 writingScore

- 구성: 점수 숫자 + **클라이언트 파생 색** + 격려문 1장(`encouragementMessage`).
- 색은 모델이 출력하지 않고 client가 `score`에서 산출한다(`feedback-slim.md:32`).
- **음성 숫자 점수 카드(유창성/자신감/머뭇거림 3열)는 제거**한다. v1은 음성/prosody 숫자 점수가 없다(`PRD.md:166`, `dialogue-learning-flow.md:113`). 레거시의 3열 카드(`보고서:190`)는 계승하지 않는다.
- 점수 숫자는 항상 함께 노출한다 — 색이 유일한 신호가 되지 않도록(색각 접근성).

#### 음성 격려와 writingScore 격려의 중복 해소

음성 입력 시 분석 직후 보여주는 `transcript + 한 줄 격려`는 **transient**이고, 시트의 `writingScore.encouragement`가 정본이다(`dialogue-learning-flow.md:112`). 텍스트 입력은 음성 격려를 생성하지 않는다(`dialogue-learning-flow.md:130`).

- **해제 트리거**: writingScore가 SSE 첫 섹션으로 시트에 렌더되는 시점에 transient 음성 격려를 해제한다. writingScore는 slim의 첫 섹션이므로 가장 먼저 도착한다 — 두 격려를 **동시에 노출하지 않는다.**

### 3.2 grammar

학습자 문장을 `segments`로 재구성한다(`feedback-slim.md:20`).

| segment type | 렌더 |
|---|---|
| `normal` | 그대로(정상) |
| `incorrect` | **취소선 + 텍스트** 이중 신호 (색 단독 아님) |
| `correction` | 교체어(의미색: 코랄, §6 참조) |
| `highlight` | 정상이나 주목할 부분 |

- `incorrect`는 취소선과 텍스트를 함께 써서 색각 사용자도 인지한다(`feedback-slim.md:34` "rendered strikethrough").
- `explanation`은 *왜 이 교정이 도움이 되는지*를 혜택 우선·전문용어 없이 ≤2줄(`feedback-slim.md:34`).

### 3.3 naturalExpression

더 자연스러운 native 버전 1개를 `segments`(`normal|highlight`) + `reason{keyword, description}`로 제시한다(`feedback-slim.md:23`).

- `highlight`는 자연스러움을 위해 바뀐 부분이다. **색 + 밑줄/배경 이중 신호**로 표시한다 — 이는 plan이 더한 접근성 개선이며, 레거시(`보고서:63` "색만으로 구분")의 단순 계승이 아니다.
- 저장(북마크) 버튼은 두지 않는다(읽기 전용, §5 참조).

## 4. deep 분석 — 온디맨드 인라인 확장

slim 3섹션이 모두 `Ready`가 되면 시트 하단 `더 보기` 버튼 1개가 활성화된다(`dialogue-learning-flow.md:154`). 탭하면 `feedback.deep`를 별도 side request로 호출하고, 같은 시트 안에 세 블록을 고정 순서로 펼친다(`feedback-deep.md:3`).

```text
conceptualBridge → toneStyle → paraphrasing
```

deep 블록도 slim과 동일하게 블록별 시머 스켈레톤으로 점진 렌더한다.

### 4.1 conceptualBridge

- 구성: `literalTranslation`(사용자 영어의 한국어 직역) + `explanation`(의도와 실제 의미의 간극) + **벤다이어그램**.
- 벤 색은 모델이 출력하지 않고 client **대비 가드**가 산출한다(`feedback-deep.md:8`). 레거시 `VennDiagramView`가 라이트/다크 양쪽에서 주요텍스트 대비 ≥4.5, 보조 ≥3.0을 강제하는 구현을 가진다(`보고서:168`).

### 4.2 toneStyle

- **5단계 톤 시크바/슬라이더**, `defaultLevel=2`(Neutral). 각 레벨은 영어 문장 + 한국어 번역(`feedback-deep.md:26`, `보고서:190`).

### 4.3 paraphrasing

- 3카드(Beginner / Intermediate / Advanced), 각 카드에 **저장(북마크) 버튼**(`feedback-deep.md:35`).
- 저장된 패러프레이즈가 요약 화면 "북마크 문장"(SENTENCE 카드)의 **유일한 소스**다(`PRD.md:161`, `dialogue-learning-flow.md:164`·`:214`).

## 5. 저장 affordance — 패러프레이즈 1원화

턴 피드백에서 저장 가능한 단위는 **paraphrasing 카드뿐**이다. slim `naturalExpression`을 포함해 다른 섹션에는 저장 버튼을 두지 않는다(`dialogue-learning-flow.md:164`·`:214`). 레거시도 패러프레이징 전용 저장(`ParaphrasingBookmarkDelegate`)만 가졌고 naturalness 섹션엔 저장이 없다.

설계상의 귀결(의도된 동작):
- `더 보기`를 펼치지 않은 턴은 SENTENCE 북마크에 기여하지 않는다 — 대부분 턴의 "북마크 문장"이 비는 것이 **기본값이며 PRD §8.3 정본과 일치**한다.
- 그래도 요약 화면의 **표현 개선 카드·신규 단어 카드는 비지 않는다.** 이들은 deep가 아니라 요약 파이프라인이 누적 slim 피드백에서 Gemini 어휘추출·표현필터링으로 독립 생성한다(`PRD.md:159-164`). 즉 저장 surface 전체가 비는 게 아니라, 5개 요약 섹션 중 "북마크 문장" 1개만 deep 의존이다.
- 단어/표현/문장 저장 surface 자체(요약 화면 저장 + 기록 탭 3종 탭)는 v1에 존속한다(`PRD.md` FR-16·17, §8.4 / `firestore-schema.md:64` `cardType: WORD|SENTENCE|EXPRESSION`). 본 IA는 그중 턴 단위 패러프레이즈 북마크만 다룬다.

## 6. 의미색 — 섹션별 소유

자연/정확 2색 의미색을 유지하되, **각 색은 한 섹션이 소유**한다. naturalExpression 한 섹션이 두 색을 모두 구동하지 않는다(스키마상 `naturalExpression.segments`는 `normal|highlight` 2타입뿐).

| 의미색 | 소유 섹션 / 타입 | 의미 |
|---|---|---|
| 코랄(정확) | `grammar.correction` segment | 교정된 정확한 표현 |
| 초록(자연) | `naturalExpression.highlight` segment | 더 자연스럽게 바뀐 부분 |

- 모든 의미색 highlight는 **색 + 형태(취소선/밑줄/배경)** 이중 신호를 쓴다 — 색은 단독 신호가 아니다.
- 구현 주의: `naturalExpression` 안에서 코랄(정확) segment 타입을 찾지 말 것. 코랄은 `grammar` 섹션 소유다.

## 7. 다음 버튼 게이팅 — 점수 gate 없음

`다음` 버튼은 **slim 3섹션이 모두 `Ready`(또는 명시적 skip)일 때** 활성화한다. 작문 점수에 따른 gate는 두지 않는다.

- 레거시는 `writingScore < 70`이면 `다음`을 숨겨 재시도를 강제했다(`archive/.../DialogueFeedbackCoordinator.java:443-448`). v1은 이를 **미계승**한다.
- 근거: 정서적 안전 — "점수가 낮아요"보다 "다시 해볼까요?"를 우선하고 대화 리듬을 끊지 않는다(`dialogue-learning-flow.md` §12). 낮은 점수 턴도 turn buffer와 요약 종합 점수에 그대로 반영된다.
- deep 로딩 중에도 `다음`은 활성이다 — deep는 비차단(§8).

## 8. deep 비차단

- deep 로딩 중에도 사용자는 `다음`으로 이동할 수 있다(`dialogue-learning-flow.md:158`).
- 다음 턴으로 넘어가면 진행 중 deep 요청은 취소하거나 늦은 응답을 무시한다.
- deep 미실행/미저장은 요약 품질(종합 점수·표현·단어 카드)에 영향을 주지 않는다 — "북마크 문장" 섹션만 비게 된다(§5).

## 9. 실패 IA

### 9.1 slim 부분 실패

- 섹션 단위 인라인 **재시도**를 먼저 제공한다(`dialogue-learning-flow.md:147`).
- 반복 실패 시 그 자리에 "건너뛰고 다음으로"가 나타난다. 건너뛰면 해당 필드를 null로 turn buffer에 기록하고 요약에서 낮은 신뢰도로 처리한다(`dialogue-learning-flow.md:150`).

섹션 → turn buffer 키 매핑(어느 섹션 실패가 어느 키를 null로 두는지):

| 실패 섹션 | null 처리되는 buffer 키 |
|---|---|
| `writingScore` | `slimScore = null` |
| `grammar` | `correctedText = null` |
| `naturalExpression` | `naturalExpression = null` |

`writingScore`가 없으면 요약 종합 점수·하이라이트 산출이 깨지므로 임의 기본값을 생성하지 않는다(`dialogue-learning-flow.md:149`).

**`grammar.segments → correctedText` 평탄화 계약(요약 입력 경로):** turn buffer의 `correctedText`(평문)는 grammar 섹션의 `correctedSentence.segments[]`(`feedback-slim.md:20`)를 다음 규칙으로 평탄화한다 — **`incorrect`(취소선=제거 대상) 세그먼트는 제외하고, `normal`·`correction`·`highlight` 세그먼트 텍스트를 원순서대로 연결**한다(즉 교정이 반영된 최종 문장). grammar 섹션 실패 시에만 `correctedText=null`. 이 평탄화는 클라이언트가 수행하며, 요약 `/llm task=summary` 입력 turn buffer에 실린다(`dialogue-learning-flow.md` §8).

### 9.2 deep 실패

- 인라인 영역에만 에러 + 재시도를 표시한다. 시트 상단 slim 섹션과 `다음`에는 영향을 주지 않는다(`dialogue-learning-flow.md` §11 "deep 피드백 실패: 턴 진행을 막지 않음").

## 10. 접근성 규약 (IA에 내장)

- **색 단독 금지**: grammar `incorrect`는 취소선+텍스트, 의미색 highlight는 색+밑줄/배경, writingScore는 점수 숫자 동반.
- **벤 대비 가드**: 런타임 대비 계산으로 라이트/다크 양쪽 가독성 강제(`VennColorContrastGuardTest` 계승).
- **터치 타깃**: `더 보기`·`다음`·저장 버튼은 디자인 시스템 최소 터치 크기를 따른다(세부 값은 디자인 시스템 소유).

## 11. 잔여 needs-you (제품/디자인시스템 소유)

본 IA의 19개 결정 중 17개는 코드/PRD ground truth로 확정됐다. 다음 2개는 추측값으로 채웠으며 잠그기 전 확인이 필요하다.

| 항목 | 가정값 | 누가 결정하나 |
|---|---|---|
| deep 재호출/캐시 정책 | 같은 턴 내 1회 호출 후 캐시(접었다 펴도 재호출 없음) | LLM 호출 비용 트레이드오프 — 제품/비용 |
| 턴 내 writingScore 점수 노출 크기 | 요약(56sp)보다 작게 | 점수 가시성의 동기 영향 — 디자인 시스템 |

---
결정을 바꾸려면 `#<n>=<value>`로 재호출하세요.
