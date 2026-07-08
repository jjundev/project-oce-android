# F1 아이콘 매핑표 — 시맨틱 seam ↔ Material Symbols glyph

> 상태: **확정 v3**(grill-yourself → grill-review deep auto, SHIP 1회 수렴) · 작성일: 2026-07-01 · 갱신: 2026-07-03 · **prototype-verified(2026-07-02)**: `Foundations` 프로토타입이 본 표 26 glyph를 Material Symbols로 직접 렌더 · 상위: [README](README.md) 표 1(F1) · [01-foundations](01-foundations.md) F1
> 정본 근거: [05-open-decisions.md](05-open-decisions.md) F1 · DS 생성 번들 `docs/design_system/design-system-498c1d19-9547-4cdb-ae4c-3313705391fb/_ds_bundle.js`(호출부 정본) · [product-design-system.md](../design/design_system_src/product-design-system.md) §4 · [06-accessibility-impl](06-accessibility-impl.md) A2·A3·A6
> 목적: 05 F1에서 **Material Symbols (Rounded·Filled·optical 24·weight 400)** 세트가 확정된 뒤, 전 화면·컴포넌트가 소비하는 아이콘을 glyph 이름·fill 상태·사용처·라벨로 매핑한 **vector 임포트 리스트**. glyph 실제 임포트 시 그대로 입력이 된다.

---

## 0. 결정 (10)

| # | 결정 | 근거 |
|---|---|---|
| 1 | 개별 Material Symbols XML vector drawable을 **필요분만** 임포트 (variable font/확장 아이콘셋 아님) | [05](05-open-decisions.md):36 "필요 glyph만 vector 임포트" |
| 2 | 시맨틱 snake_case name을 seam 공개 API로 유지, **단일 `Icon` 컴포저블**이 name→glyph 매핑 | `_ds_bundle.js`:104-139 · [01-foundations](01-foundations.md) F1 현황 |
| 3 | 축 고정 = **Rounded / fill=1(기본) / weight=400 / opsz=24**. `grade`는 05 F1 미지정 → **기본 0 가정**(추론). fill=1은 기본값이며 #4·#5 상태 아이콘이 override | [05](05-open-decisions.md):36 (grade는 미기재) |
| 4 | 저장 토글 = 저장 `bookmark`(fill1) / 미저장 `bookmark_border`(fill0). fill 차이 = 비색 형태 신호 | `_ds_bundle.js`:446,449 · A2 |
| 5 | BottomNav 활성 신호 = size(13/11sp)+weight(Bold/Normal)+color+fill을 **동시** 변경. fill은 그중 하나(비활성 0/활성 1) | `_ds_bundle.js`:1442-1443,1477,1483 · A2 |
| 6 | 크기 24dp 기본, 16/20 허용. 마이크 96dp는 glyph 아님(styled button + Canvas ring) | product-design-system.md:101(크기) · :52(마이크 96dp) |
| 7 | 아이콘은 `aria-hidden`(장식). 접근성 라벨은 **부모 컨트롤**의 `contentDescription`/`aria-label`이 보유 | `readme.md`:87-94 · `_ds_bundle.js`:436 · product-design-system.md:101 |
| 8 | Google 버튼 = 멀티컬러 브랜드 마크(Material Symbols 아님), text color 상속 금지, 표에서 분리. 파일명 미정 | 브랜드 마크 |
| 9 | Canvas(타깃 Compose) = 파형(I2)·Analyzing ring(C7) 뿐. **단** MicButton Analyzing은 glyph `autorenew`도 함께 렌더 → A에 포함 | `_ds_bundle.js`:581 · [03-signature-interactions](03-signature-interactions.md) |
| 10 | 매칭 glyph 없으면 **"커스텀 vector 필요" 플래그**, 임의 대체 금지 | `readme.md`:92 |

---

## A. 확정 glyph — 26종

> 20종 = DS 번들 호출부 실재(정본). 6종(nav 3종 `forum`·`history`·`settings` + `cloud_off`(offline) + `account_circle`(account) + `hourglass_empty`(limit))은 grill-review Needs-you로 사용자 확정. 라벨 열은 **부모 컨트롤**이 보유(#7).

| glyph | fill | 사용처 | 부모 컨트롤 라벨(예) | 근거 |
|---|:--:|---|---|---|
| `chevron_right` | 1 | ListRow (size 22) | (행 라벨이 보유) | `_ds_bundle.js` ListRow |
| `error` | 1 | FeedbackSection(sz16) · Input(sz15) | (에러 텍스트가 보유) | :893 · :1322 |
| `edit_note` | 1 | Feedback 작문 점수 | "작문 점수" | FeedbackSheet |
| `spellcheck` | 1 | Feedback 문법 | "문법" | FeedbackSheet |
| `auto_awesome` | 1 | Feedback 자연스러운 표현 | "자연스러운 표현" | FeedbackSheet |
| `format_paint` | 1 | Feedback 톤·스타일 | "톤·스타일" | FeedbackSheet |
| `hub` | 1 | Feedback 개념 브릿지 | "개념 브릿지" | FeedbackSheet |
| `bookmark` | 1 | 저장 토글 — 저장됨 (Feedback·SavedCard) | "저장됨" | :446,449 |
| `bookmark_border` | 0 | 저장 토글 — 미저장 | "저장" | :446,449 |
| `local_fire_department` | 1 | RewardStrip streak | "연속 학습"(번들 실측, `:1140`) | RewardStrip |
| `schedule` | 1 | RewardStrip 학습시간 | "학습 시간" | :1124 |
| `bolt` | 1 | RewardStrip XP | "경험치" | RewardStrip |
| `match_word` | 1 | SavedCard WORD | "단어" | SavedCard TYPE_META |
| `notes` | 1 | SavedCard SENTENCE | "문장" | SavedCard TYPE_META |
| `format_quote` | 1 | SavedCard EXPRESSION | "표현" | SavedCard TYPE_META |
| `mic` | 1 | MicButton Ready/Recording | (마이크 stateDescription) | :581 |
| `autorenew` | 1 | MicButton Analyzing | (동상) | :581 |
| `check` | 1 | MicButton Complete | (동상) | :581 |
| `volume_up` | 1 | ChatBubble TTS idle | "재생" | :535 |
| `graphic_eq` | 1 | ChatBubble TTS playing | "재생 중" | :535 |
| `forum` | 0/1 | BottomNav 학습 | "학습" | Needs-you 확정 |
| `history` | 0/1 | BottomNav 기록 | "기록" | Needs-you 확정 |
| `settings` | 0/1 | BottomNav 설정 | "설정" | Needs-you 확정 |
| `cloud_off` | 1 | 오프라인 배너(C4)·비활성 CTA 헬퍼(P8) | "오프라인" | Needs-you 확정 |
| `account_circle` | 1 | 설정 계정 섹션 | "계정" | Needs-you 확정 |
| `hourglass_empty` | 1 | LimitReachedPanel(C18) | "내일 다시 이어가요" | Needs-you 확정 |

> 임포트 파일 수 = glyph×(필요 fill 상태). 저장 토글은 `bookmark`+`bookmark_border` 2벌, nav 3종은 fill0/fill1 2벌씩.

---

## B. 잔여 추정 — 번들·소스 근거 없음, 화면 레이아웃 설계(§4) 시 확정

| 표면 | 후보 glyph |
|---|---|
| 채팅 입력·전송 | `keyboard` · `send` |
| 카드 복사·삭제·펼침 | `content_copy` · `delete` · `expand_more`(✅ 실현: 세션 요약 "더 보기" 원형 chevron, `OceIcon.ExpandMore`→`ic_expand_more`, ADR-0006)/`expand_less`(전개 시 180° 회전으로 대체) |
| 알림 | `notifications` |
| 계정·정책 | `login` · `logout` · `open_in_new` |
| 공통 | `arrow_back` · `close` · `refresh` · `edit` · `warning` · `info` |
| Google 버튼 | 브랜드 자산(Material 아님, #8) |

> **삭제됨(조작):** `mic_off` · `check_circle` · `replay` — repo 전역 0건. 실제는 각각 미사용 / `check` / `graphic_eq`.

---

## C. 제외 (glyph 아님 — 참조만)

- 파형(I2)·마이크 링 3겹·프로그레스링(I1/C7)·슬롯머신(I3/C16)·벤다이어그램(I4) = Compose Canvas ([03-signature-interactions](03-signature-interactions.md))
- 스켈레톤/시머(C6)
- streak 이모지 🔥 = 카피 전용(P16), UI glyph는 `local_fire_department`로 분리
