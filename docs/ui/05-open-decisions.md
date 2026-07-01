# UI 논의 — 제품·디자인 정책 결정 (확정)

> 상태: **확정**(grill-yourself → grill-review deep auto, 4회 수렴 SHIP) · 작성일: 2026-06-30 · 갱신: 2026-07-01 · 상위: [README](README.md) (표 5)
> 목적: 화면/컴포넌트 문서 곳곳의 결정 항목을 한곳에 모아 **결정 → 해당 문서로 반영**한다.
> Needs-you 3건(F1·P12·P13)은 사용자 확정 완료([confirmed]). 각 결정은 관련 화면 문서에 전파됨.

## 표

| ID | 결정 항목 | 소유 | 확정 결정 | 관련 문서 |
|---|---|---|---|---|
| F1 | 공식 아이콘 세트 | 디자인 | **Material Symbols (Rounded·Filled)** [confirmed] | [01-foundations](01-foundations.md) |
| P1 | 홈 XP 표시 여부 | 제품 | **미표시(접힘)**, 홈 2지표 | [02-home](04-screen-02-home.md) |
| P2 | 턴 내 점수 크기 | 디자인 | **신규 토큰 `type.turnScore` 28sp** | [04-feedback](04-screen-04-feedback-sheet.md) |
| P3 | deep 재호출/캐시 | 제품/비용 | **턴당 1회 후 캐시** | [04-feedback](04-screen-04-feedback-sheet.md) |
| P4 | "반복 실패" 임계 | 제품 | **섹션별 누적 2회** | [03-dialogue](04-screen-03-dialogue.md)·[04-feedback](04-screen-04-feedback-sheet.md) |
| P5 | transcript 수정 UI | UX | **v1 없음** | [03-dialogue](04-screen-03-dialogue.md) |
| P6 | 완주+한도 동시 표현 | 제품 | **축하 1차 + 한도 보조 1줄** | [05-summary](04-screen-05-summary.md)·[08-limit](04-screen-08-limit-gate.md) |
| P7 | 홈 at-limit 고지 방식 | 거버넌스 | **비숫자 보조 고지, ADR 미승격** | [08-limit](04-screen-08-limit-gate.md) |
| P8 | 오프라인 새 학습 CTA | 디자인 | **비활성 + 배너 + 헬퍼** | [02-home](04-screen-02-home.md) |
| P9 | 최근 주제 노출 | 제품 | **v1 미노출** | [02-home](04-screen-02-home.md) |
| P10 | 카드 정리 프리셋 | 제품 | **30/90일/전체** | [07-settings](04-screen-07-settings.md) |
| P11 | 리마인더 기본 시간 | 제품 | **20:00** | [09-reminder](04-screen-09-reminder-notification.md) |
| P12 | 첫완주·마일스톤·알림 카피 | 카피리뷰 | **초안 톤 그대로 채택** [confirmed] | [05-summary](04-screen-05-summary.md)·[09-reminder](04-screen-09-reminder-notification.md) |
| P13 | 정책 본문 3종 | 창업/법무 | **표준 템플릿 기반 초안** [confirmed] | [07-settings](04-screen-07-settings.md) |
| P14 | "진도 다시 옮기기" 행 | 제품 | **조건부 추가(`guestMergeFailStreak≥2`)** | [07-settings](04-screen-07-settings.md) |
| P15 | save_opportunity_shown | 제품 | **v1 미계측, v1.1** | [analytics-events.md](../ux/analytics-events.md) |
| P16 | 이모지 팔레트 확장 | 브랜드 | **🔥만** | [ux-writing.md](../ux/ux-writing.md) |
| P17 | Analytics event id 정합 | 계측 | **analytics-events.md 정본 고정** | [analytics-events.md](../ux/analytics-events.md) |

---

## 결정 (항목별)

### F1 · 공식 아이콘 세트 — 디자인/M0 · [confirmed]
- **현황:** Icon BLANK 플레이스홀더. 교체는 단일 `OneClickIcon` seam. product-design-system.md:99-100 "자체 조달 24-grid solid, M0 확정"만 규정(특정 세트 미지목).
- **결정:** **Material Symbols (Rounded · Filled · optical 24 · weight 400)**. 24-grid solid 계약 충족 + Apache-2.0 + Compose 네이티브. 필요 glyph만 vector 임포트, 단일 seam으로 추후 교체 가능. (사용자 확정)

### P1 · 홈 XP 표시 여부 — 제품
- **현황:** gamification §2·§6 — XP는 백스테이지(완주·기록 헤더만).
- **결정:** **B) 홈 미표시(접힘)**. 홈 게임화 스트립은 학습시간+🔥streak 2지표만. XP는 완주 보상 + 기록 탭 헤더에서만.

### P2 · 턴 내 writingScore 점수 크기 — 디자인
- **현황:** turn-feedback §11 needs-you("요약 56sp보다 작게"). 토큰 스케일에 32sp 없음.
- **결정:** **신규 시맨틱 토큰 `type.turnScore` = 28sp Bold, tracking -0.02em** 정의. `scoreDisplay`(56sp) 하위. `screenTitle`(28sp, 화면 제목)과 값이 같을 수 있으나 **시맨틱 분리**(점수 ≠ 화면 제목, 컴포넌트가 올바른 의미 토큰 소비, 향후 독립 조정). 리터럴 하드코딩 0.

### P3 · deep 재호출/캐시 정책 — 제품/비용
- **현황:** turn-feedback §11 needs-you.
- **결정:** **A) 같은 턴 1회 호출 후 turn buffer 캐시** — `더 보기` 접기/펴기 시 재호출 0. NFR-2 비용 정렬 기본값(간접 근거).

### P4 · "반복 실패" 임계 횟수 — 제품
- **현황:** dialogue §7.1·turn-feedback §9.1 — 횟수 미명시.
- **결정:** **섹션 단위 독립 카운터, 동일 섹션 누적 2회**(최초 실패 + 재시도 1회) 시 "건너뛰고 다음으로" 노출. 재시도는 **사용자 탭 기반 수동**(자동 재요청 아님 → NFR-2 무영향). 카운터 turn buffer 스코프(턴 종료 리셋), dialogue/feedback 비공유.

### P5 · 인라인 transcript 수정 UI — UX
- **현황:** dialogue §6.1 — 고정 안 함.
- **결정:** **A) v1 수정 UI 없음** — `다시 말하기`/`채팅으로 입력하기`만. "회화 리듬" 원칙 유지.

### P6 · 완주 + 한도 도달 동시 표현 — 제품
- **현황:** daily-limit §4.
- **결정:** **완주 축하/적립 스트립 1차 hero + 한도 도달 보조 인라인 1줄**(요약 하단). 별도 게이트 없음. ux-writing 위계(격려 1차·정보 2차).

### P7 · 홈 at-limit 고지 방식 — 거버넌스
- **현황:** daily-limit §6·§11(#15b).
- **결정:** **A) 비숫자 보조 고지 유지, ADR 현재 미승격**. fresh `remaining==0`일 때만 보조 고지 + `기록 보기`. 홈 고지가 daily-limit 정본과 어긋나면 그때 ADR 승격.

### P8 · 오프라인 새 학습 CTA — 디자인
- **현황:** home §8.1 · exception[D] 글로벌 배너.
- **결정:** **CTA 시각 비활성(alpha 0.38) + `Modifier.semantics{ disabled() }`**(TalkBack "비활성" 안내, `enabled=false` 금지 → 클릭은 `clickable`/`onClick` 미부여 또는 no-op 가드로 차단) + 48dp 유지 + **비색 신호**(오프라인 아이콘 + CTA 인접 헬퍼 `새 대화는 인터넷 연결이 필요해요.` exception-states.md:131) + 글로벌 배너[D] 병행.

### P9 · 최근 주제(recent) 노출 — 제품
- **현황:** home §3.2, Firestore 저장소 없음.
- **결정:** **A) v1 미노출**. 스키마 부재·스코프 재집중. 로컬 전용 정책은 후속.

### P10 · 카드 정리 프리셋 값 — 제품
- **현황:** settings §12.
- **결정:** **30일 지난 카드 / 90일 지난 카드 / 전체** 3종.

### P11 · 리마인더 기본 시간 — 제품
- **현황:** settings-data-account.md:84 DataStore 키 기본값 기록.
- **결정:** **20:00(오후 8시)**, 설정에서 변경. (Assumptions → Confident: 이미 설계 문서에 기본값 존재)

### P12 · 첫완주·마일스톤·알림 body 카피 — 카피리뷰 · [confirmed]
- **현황:** gamification §4.5(미검증)·§5·notification §5.1.
- **결정:** **초안 톤 그대로 채택**(활기찬 해요체, 🔥·느낌표는 축하 한정 — ux-writing 부합). (사용자 확정)
  - 첫 완주: `첫 대화 완주했어요! 🔥 오늘부터 1일째예요.`
  - 마일스톤: 1일 `🔥 1일째 — 시작이 좋아요!` · 3일 `🔥 3일째 — 리듬이 붙고 있어요!` · 7일 `🔥 7일째 — 일주일 완성했어요!` · 14일 `🔥 14일째 — 2주 연속이에요!` · 30일 `🔥 30일째 — 한 달 동안 매일 말했어요!`
  - 알림 body: 신규 `오늘 시작하면 1일째예요` · gap==1 `🔥 N일째 — 오늘 이어가면 N+1일째예요` · gap≥2 `🔥 오늘 5분 이어가볼까요?`

### P13 · 정책 본문 3종 — 창업/법무 · [confirmed]
- **현황:** settings §9(URL/호스팅 확정), 본문 미작성.
- **결정:** **표준 템플릿 기반 베타용 경량 초안** 작성, 공개 승격 전 법무 확정. URL/호스팅(`/privacy`·`/terms`·`/delete-account`)은 settings §9 확정. 법무 확정 대상 = 본문 내용만. (사용자 확정)

### P14 · 설정 "진도 다시 옮기기" 행 — 제품
- **현황:** exception #19 — "실행 카운트 기반, 시간창 없음".
- **결정:** **A) 조건부 추가**. 로컬 DataStore `guestMergeFailStreak: Int`(기본 0) 신설(Firestore 아님). GuestMergeFailed로 앱 실행 종료 시 +1, 이관 성공 시 0 리셋. **`≥2`일 때** 설정 계정 섹션에 행 렌더. 그 외 무음 재시도.

### P15 · save_opportunity_shown 이벤트 — 제품
- **현황:** analytics #18.
- **결정:** **v1 미계측(보류), v1.1** — v1은 세션 레벨 저장만. NFR-7 5대 지표 집중.

### P16 · 이모지 팔레트 확장 — 브랜드
- **현황:** ux-writing §5.3.
- **결정:** **🔥만(확장 없음)**. 확장은 표준 승인 필요.

### P17 · Analytics 최종 event id 정합 — 계측
- **현황:** 전 UX 문서 제안명 상태.
- **결정:** **analytics-events.md 정본으로 고정**. 단 정규화 선행작업(`quota_blocked→limit_reached`, `cardType→card_type`, `session_id` 부착, `session_start→learning_session_started`) 완료 후 고정. 타 문서 제안명은 정본으로 정규화.
</content>
