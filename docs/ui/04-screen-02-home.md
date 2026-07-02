# UI 논의 — 화면: 홈 & 주제 선택

> 상태: 논의용 스켈레톤 · 작성일: 2026-06-30 · **prototype-verified(2026-07-02)**: 본 화면 결정을 `Prototype Flow` 대응 상태와 육안 대조 · 상위: [README](README.md) (표 4)
> 정본: [home-learning-entry.md](../ux/home-learning-entry.md)(Draft) · [gamification-emphasis.md](../ux/gamification-emphasis.md) §6 · [daily-limit-ux.md](../ux/daily-limit-ux.md)
> 범례: 🔴 결정 필요 · 🟠 신규 설계 · 🟡 구현 대기 · 🟢 QA·위임

## 화면/요소 인벤토리

| 요소 | 상태 | 비고 |
|---|:--:|---|
| 메인 CTA hero `오늘 5분 말하기` | 🟠 | 항상 활성(사전 차단 안 함) |
| 게임화 요약 스트립 | 🟠 | CTA **아래**, 낮은 위계, 카운트업 미사용 |
| 주제 선택(추천/4그룹/직접입력/새로고침) | 🟠 | 4그룹: 일상·입문/여행/업무·커리어/생활·서비스 |
| 접힌(collapsed) 세션 설정 | 🟠 | 레벨(profile.level 자동)·길이(5/10턴) |
| 미완 세션 복귀 프롬프트 | 🟠 | [02](02-shared-components.md) C17 |
| 빈 상태 | 🟠 | 크게 강조 안 함, CTA가 대체 |
| 한도 보조 표시 | 🟠 | fresh `remaining` 있을 때만 |
| 오프라인 안내 | 🔴 | 비활성 vs 안내(택일) |
| 생성 중 / 생성 실패 | 🟠 | `불러오지 못했어요. 다시 시도해볼까요?` |

> 확정: 홈은 대시보드 아닌 "학습 시작 허브" · 첫 실행 시 홈 미노출(온보딩 직행) · 저장 카드 복기 모듈 v1 홈 미노출 · 한도 판정=생성 버튼 탭 직후 서버 · 생성 중 취소 미제공.

---

## 논의 (항목별)

### H1 · 메인 CTA hero 🟠
- **현황:** `오늘 5분 말하기`(행동 중심). 우선순위 #1. 항상 활성 → 한도는 시작 게이트에서 탭스루.
- **쟁점:** hero 위계 대형 CTA 외형(Button 변형 vs 전용 블록), 그라데이션 카드 사용 여부.
- **결정(rev2):** brand.gradient 카드 블록(radius.24, text.on-primary 흰 텍스트, pressed=brand.primary-pressed). 히어로 우선순위 #1 시각 강조. 항상 활성(한도는 시작 게이트 탭스루). (형태 사용자 확정)

### H2 · 게임화 요약 스트립 🟠
- **현황:** gamification §6 — CTA 아래 정적 스트립 = `오늘 N분`(중심) + `🔥 N일`. XP는 줄 끝 작게(`· N XP`) 또는 접힘. 카운트업 미사용. streak 0은 홈에서 숨김(초대 카피 대체).
- **쟁점:** 시간 vs streak 상대 위계("동급 또는 그 이하"로만 정의), XP 표시 여부([05](05-open-decisions.md) P1), RewardStrip 재사용 여부.
- **결정:** **XP 홈 미표시(접힘)** — 홈은 학습시간 + `🔥 N일` **2지표만**, XP는 완주 보상·기록 탭 헤더에서만 → [05](05-open-decisions.md) P1 확정. (시간 vs streak 위계, RewardStrip 재사용은 논의 유지)

### H3 · 주제 선택 (추천 / 4그룹 / 직접입력 / 새로고침) 🟠
- **현황:** 추천 = 단순 회전(결정적, seed 16개 날짜/인덱스 순환), `오늘의 추천`으로 고정 안 함. 4그룹 확정. 직접입력=하단 보조(`원하는 상황 직접 입력`).
- **쟁점:** 캐러셀 vs 그리드, 4그룹 탭 vs 섹션, 추천 새로고침 컨트롤 위치.
- **결정(rev2):** 추천 strip(LazyRow 가로 스크롤 칩 + 우측 새로고침 아이콘) → 4그룹 OneClickSegmentedControl(radius.pill) 전환 리스트 → 하단 "원하는 상황 직접 입력" ghost 행. 4그룹 확정(현황), 형태 사용자 확정.

### H4 · 접힌 세션 설정 🟠
- **현황:** collapsed — 현재값 작게 표시, 펼쳐 변경. 레벨 3옵션(재방문은 profile.level 자동), 길이 기본 5턴/옵션 10턴.
- **쟁점:** accordion/expand 컨테이너 신규, SegmentedControl 래핑.
- **결정(rev2):** 확장 아코디언 카드(OneClickCard). 접힘=현재값(레벨·길이) 소형 표시, 펼침=레벨/길이 SegmentedControl. 레벨은 재방문 시 profile.level 자동, 길이 기본 5턴/옵션 10턴.

### H5 · 미완 세션 복귀 프롬프트 🟠
- **현황:** `이어서 할 수 있는 대화가 있어요.` + `이어하기`/`새로 시작`. snapshot은 시간 만료 없음, 새 세션 시작 시에만 폐기.
- **쟁점:** 카드 vs 배너 vs 시트([02](02-shared-components.md) C17).
- **결정(rev2):** OneClickResumePrompt(C17) — 홈 상단 OneClickCard, "이어서 할 수 있는 대화가 있어요." + 이어하기(primary)/새로 시작(ghost). HasSnapshot일 때만.

### H6 · 한도 보조 표시 🔴
- **현황:** 남은 세션 수 상시 표시 안 함. fresh `remaining` 있을 때만 보조 표시, 도달 시 명확히 안내. daily-limit §10: "명확히 안내"가 숫자 N 노출 의무 아님. ux-writing: 잔여 수 노출 금지(비숫자 어포던스).
- **쟁점:** fresh value 캐시/만료 표시 규칙 미정. 고지 방식 ADR 승격 여부([05](05-open-decisions.md) P7). [한도 게이트](04-screen-08-limit-gate.md)와 정합.
- **결정:** fresh `remaining==0`일 때만 **비숫자 보조 고지 + `기록 보기`**, ADR 미승격 → [05](05-open-decisions.md) P7 확정.

### H7 · 오프라인 시 새 학습 CTA 🔴
- **현황:** home §8.1 — "새 학습 CTA는 **비활성화하거나** 온라인 필요 안내를 보여준다"(택일 미확정). 카피 `새 대화는 인터넷 연결이 필요해요.` 기록/통계 열람은 가능.
- **쟁점:** 비활성 vs 안내 표시 택일([05](05-open-decisions.md) P8). 오프라인 배너([02](02-shared-components.md) C4)와 관계.
- **결정:** **CTA 비활성(alpha 0.38) + `semantics{ disabled() }`**(클릭은 `onClick` 미부여/no-op 가드, 48dp 유지) + **비색 신호**(오프라인 아이콘 + CTA 인접 헬퍼 `새 대화는 인터넷 연결이 필요해요.`) + 글로벌 배너[D] 병행 → [05](05-open-decisions.md) P8 확정.

### H8 · 최근 주제(recent topics) 노출 🔴
- **현황:** home §3.2 — v1 core 제외. 확정 Firestore 스키마에 최근 주제 저장소 없음. 필요 시 로컬 전용 저장 정책 별도 결정.
- **쟁점:** 주제 선택 화면에 "최근" 섹션 노출 여부([05](05-open-decisions.md) P9).
- **결정:** **v1 미노출**(Firestore 스키마 부재, 로컬 정책 후속) → [05](05-open-decisions.md) P9 확정.
</content>
