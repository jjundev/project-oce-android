# UI 논의 — 화면: 턴 피드백 시트

> 상태: 논의용 스켈레톤 · 작성일: 2026-06-30 · **prototype-verified(2026-07-02)**: 본 화면 결정을 `Prototype Flow` 대응 상태와 육안 대조 · 상위: [README](README.md) (표 4)
> 정본: [turn-feedback-ia.md](../ux/turn-feedback-ia.md)(초안, 19결정 중 17 확정·2 추측) · PRD §8.2.2, FR-10·11
> 범례: 🔴 결정 필요 · 🟠 신규 설계 · 🟡 구현 대기(IA 확정)

## 섹션 인벤토리 (단일 시트 1장)

| 순서 | 섹션 | 영역 | 상태 | 비고 |
|:--:|---|:--:|:--:|---|
| ① | writingScore | slim | 🟡 | 점수+클라 파생색+격려 1장 |
| ② | grammar | slim | 🟠 | 세그먼트 렌더(취소선·코랄) |
| ③ | naturalExpression | slim | 🟠 | highlight 색+밑줄, **저장 없음** |
| — | `더 보기` | — | 🟡 | deep 인라인 확장 |
| ④ | conceptualBridge | deep | 🟠 | 직역+설명+벤다이어그램 |
| ⑤ | toneStyle | deep | 🟠 | 5단계 슬라이더(default=2) |
| ⑥ | paraphrasing | deep | 🟠 | 3카드+북마크(유일 저장처) |

> 확정(🟡): 단일 BottomSheet(24dp·드래그핸들·NestedScroll), 6섹션 순서·게이팅, 점진 시머 렌더, `다음`은 slim 3섹션 Ready 시 활성(점수 gate 없음, deep 로딩 중에도 활성), 의미색 소유(코랄=grammar.correction/초록=naturalExpression.highlight), 음성 숫자 점수 3열 카드 **제거 확정**.

---

## 논의 (항목별)

### FB1 · writingScore 표시 🔴
- **현황:** 점수 숫자 + 클라이언트 파생 색(모델 미출력) + 격려 1장. 점수 숫자 항상 동반(색각). FeedbackSection 존재.
- **쟁점:** 턴 내 점수 크기 — 요약(56sp)보다 작게(가정)([05](05-open-decisions.md) P2). 파생 색 구간.
- **결정:** **신규 토큰 `type.turnScore` = 28sp Bold, tracking -0.02em**(`scoreDisplay` 56sp 하위, 시맨틱 분리) → [05](05-open-decisions.md) P2 확정. (파생 색 구간은 논의 유지)

### FB2 · grammar 세그먼트 렌더러 🟠
- **현황:** 학습자 문장을 segments로 재구성. `normal`/`incorrect`(취소선+텍스트)/`correction`(코랄)/`highlight`. explanation ≤2줄. 색+형태 이중 신호.
- **쟁점:** rich text 스팬 렌더([02](02-shared-components.md) C15).
- **결정(rev2):** OneClickRichText(C15) 세그먼트 렌더 — normal / incorrect(취소선+텍스트) / correction(feedback.correct.accent 코랄) / highlight. explanation ≤2줄. 색+형태 이중 신호(A2).

### FB3 · naturalExpression 🟠
- **현황:** native 1개 + segments(`normal`/`highlight`=색+밑줄/배경) + reason{keyword, description}. **읽기 전용(저장 버튼 없음)**.
- **쟁점:** highlight 시각, EN+KO 노출.
- **결정(rev2):** native 1개 + segments(normal / highlight=feedback.natural.accent 색+밑줄) + reason{keyword, description}. EN(위)+KO(아래) 이중 블록(C15). 읽기 전용(저장 버튼 없음).

### FB4 · conceptualBridge (직역+설명+벤) 🟠
- **현황:** literalTranslation + explanation + VennDiagram(대비 가드 [I4](03-signature-interactions.md)).
- **쟁점:** 벤 래퍼 레이아웃, 텍스트 대안 배치.
- **결정(rev2):** literalTranslation + explanation + OneClickVennDiagram(I4 대비 가드)를 경계 카드로 래핑, 색 비의존 텍스트 대안을 벤 하단 배치(A2).

### FB5 · toneStyle 5단계 슬라이더 🟠
- **현황:** 5단계 시크바, defaultLevel=2(Neutral), 각 레벨 = 영어 문장 + 한국어 번역. SegmentedControl과 다른 연속/이산 시크바.
- **쟁점:** 슬라이더 컴포넌트([02](02-shared-components.md) C8), 단계 이동 시 문장 전환.
- **결정(rev2):** OneClickSlider(C8) 5스톱 discrete(stops), defaultLevel=2(Neutral). 단계 이동 시 영어 문장+한국어 번역 전환. setProgress announce.

### FB6 · paraphrasing 3카드 + 북마크 🟠
- **현황:** 3카드(Beginner/Intermediate/Advanced) 각 저장 토글. 저장 패러프레이즈가 요약 "북마크 문장"의 **유일한 소스**. SENTENCE 카드로 저장(optimistic·톰스톤). 저장 affordance 1원화(다른 섹션 저장 없음).
- **쟁점:** "저장 가능한 카드" vs DS의 SavedCard("저장된 카드") 구분, 난이도 라벨.
- **결정(rev2):** 3카드(Beginner/Intermediate/Advanced) 각 북마크 토글(빈/채운 + game.save-gold). 저장 affordance 일원화(다른 섹션 저장 없음), SENTENCE 카드로 optimistic 저장. "저장 가능한 카드"(패러프레이즈)와 DS SavedCard("저장된 카드")는 구분.

### FB7 · deep 재호출/캐시 정책 🔴
- **현황:** turn-feedback §11 needs-you(추측값) — "같은 턴 1회 호출 후 캐시(접었다 펴도 재호출 없음)". 소유 = 제품/비용.
- **쟁점:** `더 보기` 접기/펴기 시 재호출 비용([05](05-open-decisions.md) P3).
- **결정:** **턴당 1회 호출 후 turn buffer 캐시**(재펼침 재호출 0) → [05](05-open-decisions.md) P3 확정.

### FB8 · 실패 IA / "반복 실패" 임계 🔴
- **현황:** slim 섹션 인라인 재시도 → 반복 실패 시 "건너뛰고 다음으로"(buffer 키 null). deep 실패는 인라인 영역만(slim·`다음` 무영향).
- **쟁점:** "반복" 횟수 임계 미명시([05](05-open-decisions.md) P4, dialogue와 공통). 터치타깃은 DS 최소 따름.
- **결정:** **섹션 단위 독립 카운터, 동일 섹션 누적 2회**(최초 + 재시도 1회) 후 "건너뛰고 다음으로". 재시도는 사용자 탭 수동 → [05](05-open-decisions.md) P4 확정.
</content>
