# ADR-0002 — 오프라인 레이어 분리 (saved_cards vs 게임화 원장)

> 상태: 수용(Accepted) · 작성일: 2026-06-30
> 관련: `docs/design/firestore-schema.md` §7 · `docs/ui, ux/saved-cards.md` §6 · `PRD.md` §10.4 · FR-3b
> 근거 규칙: `PRD.md` "충돌 시 설계 문서가 우선한다"(`PRD.md:262`)

## 맥락

오프라인에서 두 종류의 write가 발생한다: (1) 저장 카드 토글/삭제/undo, (2) 게임화 원장(XP·studytime 등) 적립. 둘은 일관성 요구가 다르다 — 저장 카드는 단일 기기 큐잉·재생으로 충분하지만, 게임화 적립은 멱등·서버 집계가 필요하다. 하나의 오프라인 전략으로 묶으면 한쪽이 과설계되거나 다른 쪽이 부정확해진다.

## 결정

데이터 도메인별로 오프라인 경로를 **분리**한다.

| 데이터 | 오프라인 경로 |
|---|---|
| `saved_cards`(저장/삭제/undo) | **Firestore 네이티브 오프라인 영속성**(`firestore-schema.md` §7). 단일 기기 큐잉·재생·충돌 해소를 SDK에 위임. 별도 hand-rolled 큐 없음. |
| 게임화 원장(XP·studytime 등) | **DataStore write-ahead 큐**(`PRD.md` §10.4). 멱등 적립·서버 집계가 필요한 도메인 전용. |

- 저장 카드 오프라인: 읽기(캐시) 가능, 저장/삭제/undo write는 네이티브 영속성이 큐잉 후 복귀 시 재생.
- cross-device/이관 병합(`cardId` union·톰스톤·streak 재유도)은 네이티브 영속성 밖이며 이관 Function이 명시 처리(`firestore-schema.md` §7, FR-3b).

## 귀결

- `saved-cards.md` §6의 "선행 조건: ADR로 정본화"가 본 ADR로 해소된다.
- 코드 작성 전 차단 항목 없음(본 ADR이 마지막 선행 항목이었다).
