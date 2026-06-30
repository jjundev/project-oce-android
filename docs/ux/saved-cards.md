# 저장 카드 UX (v1) — 저장 행위 · 기록 탭 · 삭제/오프라인

> 상태: 설계 확정(grill-yourself → grill-review deep auto → grill-me 수렴) · 작성일: 2026-06-30
> 범위: 저장 행위 UX, 카드 데이터 모델 매핑, 기록 탭 관리, 삭제/undo, 오프라인, 계측.
> 제외: 시각 값(컬러·타이포그래피 등)은 [design-tokens.md](../design/design_system_src/design-tokens.md)가, 컴포넌트 외형·모션은 [product-design-system.md](../design/design_system_src/product-design-system.md)가 소유한다.
> 근거: `PRD.md` FR-15~17·§8.3·§10.4 · `docs/design/firestore-schema.md` §3·§7 · `docs/ux/dialogue-learning-flow.md` §7.3·§9 · `docs/ux/home-learning-entry.md` §5.3
> 오프라인 모델 레이어 분리는 `docs/adr/0002-offline-layer-split.md`로 정본화됨(#5, 아래 §6).

## 1. 목표

저장 카드는 사용자가 세션에서 배운 단어·표현·문장을 가볍게 잡아두고, 나중에 기록 탭에서 다시 보는 보관·복기 도구다. v1.1 복습 퀴즈의 입력원이기도 하다(`PRD.md:96`, FR-17).

핵심 원칙:
- 저장은 흐름을 끊지 않는 가벼운 토글이다. 강한 모달/토스트로 학습 리듬을 깨지 않는다.
- 저장은 저위험·고빈도 클라이언트 write다. 비싼 LLM 호출이 아니므로 상한·게이트로 막지 않는다(`firestore-schema.md:16`).
- 관리(목록·삭제)는 기록 탭이 전담한다. 홈에는 저장 카드를 노출하지 않는다(`home-learning-entry.md` §5.3).

## 2. 데이터 모델 (정본: `firestore-schema.md` §3)

`saved_cards/{cardId}` — 카드 1개 = 문서 1개. `cardType` 판별자로 3종을 구분한다.

| cardType | 출처(저장 surface) | 표시 필드 |
|---|---|---|
| `WORD` | 세션 요약 — 신규 단어 카드 | `english`, `korean`, `exampleEnglish`, `exampleKorean` |
| `EXPRESSION` | 세션 요약 — 표현 개선 카드 | `type`, `koreanPrompt`, `before`, `after`, `afterHighlights`, `explanation` |
| `SENTENCE` | 턴 중 deep 피드백 — 패러프레이즈 북마크(`dialogue-learning-flow.md` §7.3) | `english`, `korean` |

공통 필드:
- `createdAt` — 정렬 키(기록 탭 `desc`).
- `deletedAt` — **create 시 반드시 `null`**(쿼리 일관성), 삭제 = 이 필드 set(톰스톤).

### 2.1 cardId 결정성 (중복 저장 방지)

`cardId`(문서 id)를 소스 튜플에서 **결정적으로 파생**한다(정본: `firestore-schema.md` §3, `docs/adr/0001-card-id-determinism.md`).
- WORD/EXPRESSION(요약 출처): `"{sessionId}__{cardType}__{sourceIndex}"`. `sourceIndex`는 `/llm task=summary` SSE 해당 섹션 배열(`summary.words`/`summary.expressions`, `prompt-system.md`) 내 항목 0-기반 순번.
- SENTENCE(턴 중 deep 패러프레이즈): `"{sessionId}__SENTENCE__{turnIndex}__{level}"`. `level`은 패러프레이즈 1/2/3. **`sourceIndex`(=level) 단독은 턴마다 반복되어 같은 세션 내 충돌하므로 `turnIndex`를 반드시 포함**한다(§7.3 origin).
- 같은 항목을 요약 재렌더/화면 재진입/프로세스 재시작에서 다시 저장해도 같은 문서로 수렴 → 중복 카드가 생기지 않는다(멱등). 랜덤 UUID는 프로세스 사망 후 같은 키 재생성이 불가해 멱등이 깨지므로 쓰지 않는다.
- 다른 세션의 동일 내용은 `sessionId`가 달라 별도 카드로 허용한다(v1 단순화). 게스트→Google 이관의 `cardId` union(`firestore-schema.md:164`)은 결정적 id에서 더 강하게 수렴한다.
- 별도 `itemKey` 필드는 두지 않는다. dedup은 문서 id에 산다. 길이 가드: `sessionId`가 서버 UUID(고정 길이)라 문서 id 한도(1500바이트) 안에 든다.

## 3. 저장 행위

### 3.1 상태 축

`SaveState = Unsaved / Saving / Saved / Failed` (항목별 로컬 상태).

- optimistic: 토글 탭 즉시 `Saved` 표시 후 백그라운드 write.
- 성공 → 유지. 실패(`Failed`) → 토글을 `Unsaved`로 되돌리고 비난 없는 재시도 가능 표시. 흐름은 막지 않는다.
- 오프라인 → `Saved`로 표시하고 Firestore 네이티브 영속성이 큐잉(§6).

### 3.2 토글과 revive

저장은 **북마크 토글**이다. 미저장=빈 북마크, 저장=채운 북마크 + 미세 골드(`딸깍영어_UIUX_분석_보고서.md:65` 저장 골드 #FFC107).

- **취소(unsave):** 동기화 전이면 큐의 write를 취소(문서 생성 안 함), 동기화 후면 `deletedAt` 톰스톤 update.
- **재저장(revive):** 같은 `cardId` 문서의 update로 `deletedAt=null` 복원.
  - **정렬 보존은 클라이언트 계약으로 보장한다.** `saved_cards` 보안 규칙(`firestore-schema.md:221-222`)은 `cardType`만 검증하고 `createdAt` 불변을 강제하지 **않는다**(불변 규칙은 `users/{uid}` 루트 doc 한정 — `firestore-schema.md:213`). 따라서 클라이언트는 revive update 시 **`createdAt`을 재기록하지 않아** 원본 정렬 위치를 유지한다.
  - **가드(서버 미강제 보완):** 서버가 `createdAt` 불변을 강제하지 않으므로, revive/undo 경로는 (a) update payload에 `createdAt`을 절대 포함하지 않고, (b) 단위 테스트로 "revive 후 `createdAt` 불변 + 정렬 위치 유지"를 검증한다. 향후 코드가 `createdAt`을 덮으면 조용한 재정렬이 발생하므로 회귀 테스트로 잠근다.

### 3.3 surface별

1. **세션 요약 화면** — 표현 개선(EXPRESSION)·신규 단어(WORD)·북마크 문장(SENTENCE 집계) 각각 토글. 요약 섹션 부분 실패(`expressions/words/coaching`) 시 성공한 섹션의 저장만 활성화한다(`dialogue-learning-flow.md` §9).
   - 북마크 문장 섹션은 §7.3에서 저장한 SENTENCE를 **최신순 최대 8개**로 노출한다(표시 상한이며 저장 자체의 상한이 아니다). 8개 초과분은 기록 탭 문장 탭에 남는다.
2. **턴 중 deep 피드백** — 패러프레이즈 3개에 북마크 토글(SENTENCE origin). deep 요청 취소/다음 턴 이동과 무관하게 이미 저장한 카드는 유지한다(`dialogue-learning-flow.md` §7.3).

## 4. 기록 탭

- **구조:** AppBar + 3탭(표현/단어/문장 = EXPRESSION/WORD/SENTENCE) + 탭별 RecyclerView(`PRD.md` FR-17, `딸깍영어_UIUX_분석_보고서.md:203`).
- **쿼리/정렬:** 탭별 `where cardType==<T> and deletedAt==null orderBy createdAt desc`. 복합 인덱스 `(cardType ASC, deletedAt ASC, createdAt DESC)` 사용(`firestore-schema.md:259`). cursor 증분 로드.
- **전역 누적 상한:** 없음. 무한 누적, 톰스톤 삭제만(#18). 증분 로드라 대량에도 성능 문제 없음. 아카이브/정리 정책은 데이터가 쌓인 뒤 후속 결정.
- **카드 상호작용:** 탭하면 인라인 펼침(설명/예문), 복사 제공. **TTS 다시 듣기는 v1 미제공**(#20) — 저장 카드용 TTS 캐시/합성 경로를 v1에서 열지 않는다. v1.1 복습 퀴즈와 함께 재검토.
- **퀴즈 FAB:** v1 **미노출**. `딸깍영어_UIUX_분석_보고서.md:203`이 기록 탭에 퀴즈 FAB를 두지만, 복습 퀴즈가 v1.1이므로(`PRD.md:96`, `dialogue-learning-flow.md:317`) v1은 이 옛 스펙을 의도적으로 override한다.

### 4.1 빈 상태

- 탭별 빈 상태는 `딸깍영어_UIUX_분석_보고서.md:203`(§10.7) "96dp 아이콘 + 문구" 스펙을 계승한다.
- 카피는 해요체·행동 초대형. 예: 문장 탭 "마음에 든 문장을 저장하면 여기에 모여요."
- 학습 시작 유도는 약하게 — 주 CTA는 홈이 소유한다.

## 5. 삭제 / undo

- 스와이프 → 즉시 `deletedAt = serverTimestamp` 톰스톤 → 리스트에서 제거 + 짧은 **undo 스낵바**(N초).
- 확인 다이얼로그는 두지 않는다(삭제가 비파괴 톰스톤이라 복구는 undo로 처리).
- undo → `deletedAt=null` 복원(§3.2 revive와 동일 경로, 원본 정렬 유지).

## 6. 오프라인 (ADR 선행)

**레이어 분리 — `PRD.md:262` "충돌 시 설계 문서 우선" 규칙으로 정본화(#5, `docs/adr/0002-offline-layer-split.md`):**

| 데이터 | 오프라인 경로 |
|---|---|
| `saved_cards` (저장/삭제/undo) | **Firestore 네이티브 오프라인 영속성**(`firestore-schema.md` §7). 단일 기기 큐잉·재생·충돌 해소를 SDK에 위임. 별도 hand-rolled 큐 없음. |
| 게임화 원장(XP·studytime 등) | **DataStore write-ahead 큐**(`PRD.md` §10.4). 멱등 적립/서버 집계가 필요한 도메인 전용. |

- 저장 카드 오프라인: 읽기(캐시) 가능, 저장/삭제/undo write는 네이티브 영속성이 큐잉 후 복귀 시 재생.
- cross-device/이관 병합(`cardId` union·톰스톤·streak 재유도)은 네이티브 영속성 밖이며 이관 Function이 명시 처리한다(`firestore-schema.md` §7, FR-3b).
- **정본화 완료:** 위 레이어 분리를 `docs/adr/0002-offline-layer-split.md`에 기록함. 코드 작성 전 차단 항목 해소됨.

## 7. 게스트 → Google 이관

`cardId` union + 톰스톤 우선 멱등 머지(`firestore-schema.md:158`, `PRD.md` FR-3b). 자동 보존이라 전용 UI 화면은 없다.

## 8. 계측

확정 event id는 Analytics 설계에서 최종 확정한다. 현재 문서 확인된 것과 제안 확장을 구분한다.

| 이벤트 | 상태 | 파라미터(제안) |
|---|---|---|
| `saved_card_create` | 문서 확인(`home-learning-entry.md:366`, 파라미터 없는 단순명) | `surface`(summary｜deep_feedback), `card_type` |
| `saved_card_delete` | **제안 확장** | `undone`(bool) |
| `record_tab_view` / `record_tab_switch` | **제안 확장** | `cardType` |

## 9. 카피 정책

- 저장 시 별도 문구 없이 토글 상태 변화로 표현한다.
- 빈 상태·삭제 undo는 해요체, 비난 없음.
- 회피: "저장 실패했습니다", 강한 모달.

## 10. 확정 결정 요약

| # | 항목 | 결정 |
|---|---|---|
| 1 | 저장 surface | 요약(WORD/EXPRESSION) + deep 피드백 패러프레이즈(SENTENCE) |
| 2 | 토글/revive | 북마크 토글, revive=`deletedAt=null` update, 클라이언트가 `createdAt` 보존 |
| 3 | 데이터 모델 | `saved_cards` 3종, `deletedAt` create 시 null 강제 |
| 4 | 타입↔출처 | WORD=요약 단어 / EXPRESSION=요약 표현 / SENTENCE=패러프레이즈 북마크 |
| 5 | 오프라인 | saved_cards=네이티브 영속성 / 게임화 원장=write-ahead 큐(ADR 정본화) |
| 6 | cardId dedup | 결정적 파생 doc-id(ADR-0001): WORD/EXPRESSION=`{sessionId}__{cardType}__{sourceIndex}`, SENTENCE=`{sessionId}__SENTENCE__{turnIndex}__{level}`, 신규 필드 없음 |
| 7 | 저장 연출 | 강한 토스트/모달 없이 토글 + 미세 골드 |
| 8 | 기록 탭 | 3탭 + RecyclerView + 스와이프 삭제 |
| 9 | 쿼리/정렬 | `cardType==X and deletedAt==null orderBy createdAt desc`, 복합 인덱스, cursor 증분 |
| 10 | 삭제 UX | 톰스톤 + undo 스낵바, 확인 다이얼로그 없음 |
| 11 | 표시 필드 | 타입별 스키마 필드 그대로(§2) |
| 12 | 빈 상태 | `UIUX:203` 96dp 아이콘+문구 계승, 해요체 |
| 13 | 퀴즈 FAB | v1 미노출(`UIUX:203` override) |
| 14 | 홈 노출 | 미노출, 기록 탭 전담 |
| 15 | 이관 | `cardId` union + 톰스톤 우선 멱등 머지 |
| 16 | 계측 | `saved_card_create`만 확인, 나머지는 제안 확장 |
| 17 | 딥피드백 저장 | §7.3에 패러프레이즈 북마크 토글 신설 |
| 18 | 전역 상한 | 없음 |
| 19 | "북마크 최대 8" | 요약 표시 상한(최신순), 저장 상한 아님 |
| 20 | 저장 카드 TTS | v1 미제공 |

## 11. 후속 결정

- ~~오프라인 레이어 분리 ADR 작성(#5, 선행).~~ 완료: `docs/adr/0002-offline-layer-split.md`.
- Analytics 최종 event id 및 파라미터(#16 제안 확장 확정).
- 저장 카드 아카이브/정리 정책(데이터 누적 후).
- v1.1 복습 퀴즈 도입 시: 기록 탭 퀴즈 진입·저장 카드 TTS 재검토.
