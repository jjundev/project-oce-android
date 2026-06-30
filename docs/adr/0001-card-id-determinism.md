# ADR-0001 — saved_cards cardId 결정성

> 상태: 수용(Accepted) · 작성일: 2026-06-30
> 관련: `docs/design/firestore-schema.md` §3 saved_cards · `docs/ui, ux/saved-cards.md` §2.1 · FR-15~17

## 맥락

`saved_cards/{cardId}`는 저빈도가 아니라 **고빈도 클라이언트 write**이며, 사용자는 같은 저장 항목을 요약 재렌더·화면 재진입·앱 프로세스 재시작 뒤 다시 토글할 수 있다. 중복 카드가 생기지 않으려면 "같은 논리 카드 = 같은 문서"가 멱등으로 보장돼야 한다.

기존 스키마 초안은 `cardId = 클라이언트 UUID`(랜덤)였다. 랜덤 UUID는 한 프로세스 안에서 메모리에 id를 들고 있는 동안에만 같은 문서를 가리킬 수 있고, **프로세스 사망/재진입 후에는 같은 키를 재생성할 수 없어** 같은 항목을 다시 저장하면 새 문서가 생긴다(멱등 깨짐). union-by-cardId 머지는 키가 랜덤이어도 정확하지만, 그것은 dedup 멱등을 제공하지 않는다.

## 결정

cardId를 소스 튜플에서 **결정적으로 파생**한다(경로 A — 정본 스키마 개정).

- WORD/EXPRESSION(요약 출처): `"{sessionId}__{cardType}__{sourceIndex}"`.
  - `sourceIndex` = `/llm task=summary` SSE의 해당 섹션 배열(`summary.words` / `summary.expressions`) 내 항목 0-기반 순번.
- SENTENCE(턴 중 deep 패러프레이즈): `"{sessionId}__SENTENCE__{turnIndex}__{level}"`.
  - `level` = 패러프레이즈 1/2/3(Beginner/Intermediate/Advanced).
  - `sourceIndex`(=level) 단독은 턴마다 반복되어 같은 세션 내 충돌하므로 `turnIndex`를 반드시 포함한다.
- 별도 `itemKey`/소스 필드는 두지 않는다 — dedup은 문서 id 자체에 산다.
- 길이 가드: `sessionId`는 서버 UUID(고정 길이)이므로 위 문자열은 Firestore 문서 id 한도(1500바이트)를 넘지 않는다.

## 대안과 기각 사유

- **경로 B(UUID 유지 + sessionId/sourceIndex/turnIndex 필드 추가 + create 전 query-dedup):** 매 저장마다 읽기 왕복과 스키마 필드 추가 비용. 결정적 id가 같은 효과를 추가 read 없이 제공해 기각.
- **클라 로컬 dedup만(스키마 불변):** 프로세스 사망 후 재진입 시 중복 위험 잔존. 기각.

## 귀결

- `firestore-schema.md` §3 saved_cards 헤더·필드 명세를 결정적 cardId로 개정(반영 완료).
- 게스트→Google 이관 `saved_cards` union(`firestore-schema.md` §6 `cardId 기준`, 톰스톤 우선)은 결정적 id에서 더 강하게 수렴한다.
- 보안 규칙(`firestore-schema.md` saved_cards create/update)은 문서 id 형식을 검증하지 않으므로, 합성·길이 규약은 클라이언트 계약으로 보장한다.
