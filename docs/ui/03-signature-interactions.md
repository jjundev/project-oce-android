# UI 논의 — 시그니처 인터랙션 (Compose Canvas 재구현)

> 상태: **명세 확정**(2026-07-01, grill-review deep auto SHIP · 루프 3회 수렴) · 작성일: 2026-06-30 · 상위: [README](README.md) (표 3)
> 정본: [product-design-system.md](../design/design_system_src/product-design-system.md) §3·§5·§6 · [design-tokens.md](../design/design_system_src/design-tokens.md) §6 · [motion.css](../design_system/design-system-498c1d19-9547-4cdb-ae4c-3313705391fb/tokens/motion.css) · **I2 파형: [audio-pipeline.md](../design/audio-pipeline.md) §5·§6(정본)** · PRD §8.2.3·§10.1 · [ADR-0003](../adr/0003-fr20-countup-surface-relocation.md)
> 범례: 🟡 구현 대기(명세 확정, Compose Canvas 재구현만)
> 확정 경로: grill-yourself(18건) → AskUserQuestion(#17·#18) → grill-review deep auto(Blocker 5→0, rev3). fresh subagent 교차검증(sonnet ×3).

## 표

| ID | 인터랙션 | 상태 | 메모 |
|---|---|:--:|---|
| I1 | 음성 4상태 마이크 | 🟡 | Ready/Recording/Analyzing/Complete · 96dp |
| I2 | 실시간 파형 | 🟡 | crackle 40바 ±0.3 |
| I3 | 슬롯머신 카운트업 | 🟡 | 1260ms 스프링반동 |
| I4 | 벤다이어그램 + 대비 가드 | 🟡 | 30% 겹침, 대비 자동보정 |

> PRD §10.1: 커스텀 뷰 → **Compose Canvas 재구현**(벤·파형·슬롯머신). 값·상태축·시각 디테일 **모두 확정**(아래 항목별 "결정(확정)") → 남은 것은 M0 스캐폴딩((a)Gradle+Compose BOM (b)`ANIMATOR_DURATION_SCALE` 감지 유틸 (c)`AudioMath.kt`+AudioRecord 루프 (d)F1 아이콘) 위 Compose 구현뿐.

---

## 논의 (항목별)

### I1 · 음성 4상태 마이크 🟡
- **현황:** product-design-system §3.1 — `MicState` 4값 + announce 확정. Ready(회색 동심원/"말할 차례") → Recording(빨강+리플 3겹/"녹음 중") → Analyzing(블루그레이+프로그레스 링/"분석 중") → Complete(초록/"완료"). 96×96dp. 권한 중·대기는 UI-local transient(상태축 미확장). (웹 번들 `_ds_bundle.js:554`의 Ready 라벨 "녹음 시작"은 정본 SoT에서 "말할 차례"로 대체됨)
- **결정(확정):**
  - **컨테이너:** `Box(96.dp 고정)`(fontScale 무관, A7). 배경 = `ringColor[state]`. 코어원 0.62×, Icon 0.3×(F1 아이콘 세트 대기 → 임시 blank slot). press `scale 0.96`(100ms), disabled `alpha 0.38`, 상태전환 배경/코어 crossfade 200ms(`motion base 200ms`).
  - **리플 3겹(Recording):** scratch Canvas. 각 `200ms` stagger(loop 600ms÷3, `--motion-ripple-loop`), `scale 0.7→1.35 · opacity 0.55→0` ease-out 무한. (`_ds_bundle.js` MicButton `oc-mic-ripple`)
  - **프로그레스 링(Analyzing):** M3 `CircularProgressIndicator`가 아닌 **scratch Canvas `drawArc`**. 이유 — M3 indeterminate는 가변 sweep 아크라 CSS 스피너의 "고정 1/4 아크 회전"을 재현 못 함(grill-review Advisory #2). 회전 0.9s linear, `-90°`(12시) 시작, sweep ~90°, `voice.analyzing` on `border.hairline` 트랙. C7이 scratch 허용(product-ds §5 표 C7).
  - **reduce-motion(단일 게이트):** `ANIMATOR_DURATION_SCALE==0` 감지 1개가 리플·아크 **동일 경로**로 정적화 — 리플 정적 1겹(opacity 0.3, scale 1.15), 아크 회전정지 정적.
  - **시맨틱:** `role=Button` + `stateDescription`(Ready"말할 차례"/Recording"녹음 중"/Analyzing"분석 중"/Complete"완료" — MicState 4값 1:1) + 모든 전환 **assertive** liveRegion(문구=stateDescription 재사용, A3·A6). "들을 차례"는 MicState 아닌 `OpponentPlayback.Playing`→ChatBubble이 announce(06-accessibility-impl.md A3).

### I2 · 실시간 파형 🟡
- **현황:** 40바(간격 4dp·라운드 4dp), 그레이 세로 그라데이션(`waveform.top/bottom`), crackle. `MicState.Recording` 한정. accessibility: `clearAndSetSemantics{}` 장식(포커스 제외), 상태는 마이크가 단독 전달. dp 고정.
- **결정(확정 — 정본 [audio-pipeline.md](../design/audio-pipeline.md) §5·§6):**
  - **crackle 모델:** 웹 번들의 장식용 sin 엔벨로프(`env(i)=0.35+0.5·sin(iπ/40)`)는 **폐기**. 매 프레임 **전 40바를 현재 청크 진폭 `level`로 세팅** + 막대별 독립 지터 `±(rand−0.5)·0.6`(레거시 `WaveformView.java:136` 등가), clamp `[0.05, 1.0]`. **비스크롤**(좌우 시간축 형태 없음 — 순간 강약만; 정본 의도, `audio-pipeline.md:92`). 데이터 = `waveform: StateFlow<FloatArray>`(40값).
  - **진폭 정규화 `level`:** `level = AudioMath.normalizedRms(chunk) × gain(≈3.0)`, clamp `[0,1]`, floor `0.05`(`audio-pipeline.md:93·41`). gain/floor는 단말 튜닝 상수(`:166`). RMS는 신규작업(레거시엔 RMS 계산 없음 — 포트 아님).
  - **진폭 소스:** `AudioRecord` raw PCM(16kHz·mono·16-bit, CHUNK 1024) → 청크별 정규화 RMS([사용자 확정 #17]).
  - **렌더 케이던스:** `waveform: StateFlow<FloatArray>` emission 구동 = SoT 캡처율 **~31fps/~32ms**(`read(1024)`=512샘플, `audio-pipeline.md:83`). 막대별 지터 프레임간 보간은 선택(`withFrameNanos`). rev1의 "60–90ms 쓰로틀"(웹 목업 `setInterval(90ms)` 잔재)은 **철회**([사용자 재확정 #18]).
  - **치수:** 바 너비 = **반응형** `(가용너비 − 39·4dp − 좌우패딩)/40`(레거시 `WaveformView.java:67-68` 준거; 웹 번들 고정 3px는 목업 단순화로 의도적 divergence). 높이 48dp 고정(fontScale 무관, A7)·gap 4dp·`radius.4`·세로 그라데이션 `waveform.top→bottom`.
  - **reduce-motion:** 정적 저진폭 트레이스(level 0.18). Recording 외에도 정적. `clearAndSetSemantics{}` 장식(A3).

### I3 · 슬롯머신 카운트업 🟡
- **현황:** 1260ms(빠른 회전 800ms → 감속 460ms) + 스프링 반동(Y 0.92 압축). 완주 보상·기록 탭 최초 1회 한정([ADR-0003](../adr/0003-fr20-countup-surface-relocation.md): 홈→완주·기록 재배치). same-day 2번째 세션 streak 정적. reduce-motion → 즉시 최종값 스냅, `stateDescription` 최종값 1회.
- **결정(확정):**
  - **롤 방식:** **단일 숫자 롤업**(자리별 릴 아님), `toLocaleString()`+suffix 표기(`_ds_bundle.js` `useCountUp` 단일 `n`).
  - **타이밍(구체 키프레임):** 단일 `Animatable<Float>` 1260ms — `keyframes{ 0f at 0 with FastOutLinearInEasing; 0.98·target at 800; target at 1260 with LinearOutSlowInEasing }`(빠른 800ms 0→98% + 감속 460ms →100%). **별도** scaleY Animatable `0.92→1.0`(최후 ~200ms, `--motion-ease-spring` cubic-bezier(.34,1.56,.64,1)). 웹 단일 easeOutCubic은 정본 스프링으로 대체(design-tokens §6 · `motion.css:13`(`--motion-ease-spring`)·`:16-17`(fast-phase 800ms/total 1260ms)).
  - **게이팅:** `Completed`(완주 보상) surface 롤 · **기록 탭 상단 통계 = 세션당 최초 진입 1회 롤**(ADR-0003:20) · same-day 2nd streak 정적 · **홈만 정적**(재방문, ADR-0003:21). (product-ds §3.3·ADR-0003)
  - **reduce-motion:** 즉시 최종값 스냅(scaleY 없음) + `stateDescription` 최종값 1회 polite.
  - **배치:** `OneClickCountUp` scratch primitive([02](02-shared-components.md) C16), RewardStrip이 XP·학습시간·streak 3개 합성(22sp ExtraBold, tracking -0.02em).

### I4 · 벤다이어그램 + 런타임 대비 가드 🟡
- **현황:** 동일 반지름 두 원 30% 겹침, 교집합 INTERSECT. 측면 alpha ~0.5·교집합 더 진하게. **런타임 대비 가드**: 라이트/다크 양쪽 주요텍스트 ≥4.5·보조 ≥3.0 만족하도록 색 자동 보정, 좌우 색거리 ≥50. accessibility: 시각은 장식, 두 단어+교집합 의미를 텍스트로 별도 노출.
- **결정(확정):**
  - **기하:** `r = size·0.3`, 중심 `x = size/2 ∓ r·0.62`(중심간 1.24r ≈ 30% 겹침), `y = size·0.37`, `size = 240dp`(dp 고정), 뷰포트 높이 0.75×(`_ds_bundle.js` VennDiagram).
  - **대비 가드(순수 함수):** 측면 원 alpha `128`·교집합 체감 alpha `180`. 좌우 색거리(유클리드 RGB) `≥50` 확보 → 각 원 위 텍스트 대비 `≥4.5`(주요)·`≥3.0`(보조)를 라이트/다크 양쪽 만족하도록 명도 보정 루프. 레거시 `VennColorContrastGuardTest` Compose 포트(M0 게이트 #9, product-ds §6). 테스트 가능하게 렌더와 분리.
  - **렌더:** scratch Canvas 2원(alpha 겹침, BlendMode 불필요) + Text 라벨 + `drawWithCache`, dp 고정.
  - **텍스트 대안(필수):** `공통: <intersection>` figcaption + `aria-label`("<left>와 <right>의 공통 의미: <intersection>"). 색 단독 신호 금지(A2). 피드백 시트 `conceptualBridge` 슬롯이 래핑([04-feedback](04-screen-04-feedback-sheet.md)).
</content>
