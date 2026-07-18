# 딸깍영어 v1 — 프롬프트 시스템 설계 (B-1)

> **상태:** 설계 확정(SHIP) · **작성일:** 2026-06-30 · **대상:** PRD B-1 (AI/프롬프트)
> **근거:** [PRD.md](../../PRD.md) §8(주요 플로우)·§10.3(AI)·A8(스피킹 숫자 제거)·Q6(슬림 피드백) · [firestore-schema.md](firestore-schema.md) · 옛 프롬프트(`archive/android/app/src/main/assets/prompts/`)
> **도출 과정:** `grill-yourself`(자율 설계) → `grill-review --deep auto` 2라운드(Blocker 4 → 0 SHIP). 변경 이력은 §9.

---

## 1. 설계 원칙

- 모든 프롬프트는 **서버 보관·버전드**(`config/prompts`), 클라 비번들 — 프롬프트=IP, [firestore-schema.md](firestore-schema.md) §2/§6.
- 모든 LLM 호출은 **백엔드 LLM 프록시 경유**(키 서버, 벤더 추상화 시임, [PRD.md](../../PRD.md) §10.2). v1 모델은 전부 **Gemini Flash 계열**.
- 출력은 **호출별 구조화 JSON**(`responseMimeType=application/json` + `responseSchema`). 학습자-대면 텍스트는 한국어(해요체), 영어 학습 콘텐츠만 영어.
- **캐싱:** 명시적 `cachedContents`는 폐기됨 — 프로젝트+리전 스코프 리소스라 이 백엔드의 Vertex AI Express Mode(API 키, 프로젝트 ID 없음) 인증과 근본적으로 호환 불가([backend-functions.md](backend-functions.md) §6). Gemini 2.5+/3.x의 **암묵 캐싱(implicit, 기본 on)**에만 의존한다 — 콜별 **전체 시스템 프롬프트**(공유 prefix + 콜 고유부)를 요청 머리에 동일하게 두면 반복 호출 간 캐시 히트가 기대되나, 코드 계측으로 검증하지는 않았다(의도적 결정, [backend-functions.md](backend-functions.md) §6).
- **점진 렌더:** 백엔드가 부분 JSON에서 완성 객체만 추출해 SSE emit([PRD.md](../../PRD.md) §10.1 B1). `responseSchema`(검증)와 중괄호-깊이 파서(점진 emit)는 **직교** — 스트림 콜도 둘 다 쓴다.

> ⚠️ **모델 고정 주의:** 위 캐싱·키순서 보장은 **Gemini 2.5+ 문서 기준**. 실제 모델 ID(옛 `gemini-3.1-flash-lite-preview` 등) 확정 후 그 모델에서 동작 재확인할 것.

---

## 2. 공유 컴포넌트 (캐시 prefix — 생성 콜 공통)

[`prompts/_shared/`](prompts/_shared/)에 분리, 콜 시스템 프롬프트 머리에 동일 순서로 결합 → 암묵 캐싱 히트.

| 파일 | 내용 | 적용 콜 |
|---|---|---|
| [`tone-and-style.md`](prompts/_shared/tone-and-style.md) | 5계명(Casual&Easy·Concise≤2줄·Benefit-First·Respect&Emotional·Predictable Hint), 해요체 | feedback.*, speaking, summary.* |
| [`difficulty-bands.md`](prompts/_shared/difficulty-bands.md) | easy=A2 / normal=B1 / hard=B1+(B2 입문 헤드룸, C1 금지) | dialogue, feedback.* |
| [`korean-error-reference.md`](prompts/_shared/korean-error-reference.md) | 한국인 8대 오류 | feedback.* |
| [`safety-scope.md`](prompts/_shared/safety-scope.md) | 영어 학습 한정·유해/오프토픽 차단·PII 미반향 | 전 콜 |

---

## 3. 콜 카탈로그 (7종)

| 콜 | 모델 | 스트림 | responseSchema | 입력(가변) | 출력(핵심) |
|---|---|:---:|:---:|---|---|
| [`dialogue.generate`](prompts/dialogue-generate.md) | Flash | ✅ 턴 | ✅ | level·topic·length·firstSession | `{topic, opponentName, opponentGender, opponentRole, script:[{ko,en,role}]}` |
| [`speaking.analyze`](prompts/speaking-analyze.md) | Flash | — | ✅ | **audio/wav만**(정답 미주입) | `{transcript, feedbackMessage}` |
| [`feedback.slim`](prompts/feedback-slim.md) | Flash | ✅ 섹션 | ✅ | transcript·대상 한국어·정답 영어·level | `{writingScore:{score,encouragementMessage}, grammar:{segments,explanation}, naturalExpression:{segments,reason}}` |
| [`feedback.deep`](prompts/feedback-deep.md) | Flash | ✅ 섹션 | ✅ | 동일 턴 | `{conceptualBridge:{literalTranslation,explanation,venn}, toneStyle:{levels[5]}, paraphrasing[3]}` |
| [`summary.words`](prompts/summary-words.md) | Flash | — | ✅ | words[]·sentences[]·userOriginalSentences[] | `{items:[{en,ko,partOfSpeech,level,example,collocationNote?,confusionNote?}]}` |
| [`summary.expressions`](prompts/summary-expressions.md) | Flash | — | ✅ | totalScore·expressionCandidates[] | 자연/정확 2분류 필터 결과 |
| [`summary.coaching`](prompts/summary-coaching.md) | Flash | — | ✅ | 턴 점수·before/after 쌍 | `{futureSelfFeedback:{positive, toImprove}}` |

**propertyOrdering:** 스트림 콜(dialogue/slim/deep)은 출력 순서를 결정적으로 만들기 위해 `responseSchema`에 `propertyOrdering` 고정(slim: writingScore→grammar→naturalExpression). **deep 스키마는 중첩 깊이 한계 사전 검증**.

---

## 4. 핵심 데이터 흐름

### 4.1 턴 루프 (순차, transcript 재사용)
```
[학습자 턴] 녹음(16kHz PCM) → 프록시가 WAV 래핑
  → speaking.analyze(audio/wav만)  → {transcript, feedbackMessage}     # 정답 미주입(전사 편향 차단)
  → feedback.slim(transcript + 대상 한국어 + 정답 영어 + level)         # transcript를 텍스트로 재사용
  → "더 보기" 탭 시에만 feedback.deep
클라이언트가 매 턴 버퍼에 적재: {koreanPrompt, userText(before), correctedText(after), naturalExpression, slimScore}
```

### 4.2 세션 요약 (완주 시, 클라 버퍼 → 프록시)
```
클라가 완주 시 턴 버퍼 번들을 프록시로 전송
  totalScore = 슬림 writingScore 평균(클라/백엔드 산출)        # deep 미실행과 무관
  병렬:
   - summary.words(words/sentences/userOriginalSentences)
   - summary.expressions(totalScore + expressionCandidates)   # totalScore가 필터 강도 입력
   - summary.coaching(턴 점수 + before/after)
백엔드 조립: highlights = 슬림 점수 ≥90 턴, likedSentences = 북마크
```
> 서버 측 턴별 저장소 없음(stateless 친화). 미완 세션 = 버퍼 미전송 = 요약/XP 없음(일관).
> **신뢰:** 클라 버퍼는 *표시용* 요약만 만든다. XP/streak는 서버 권위 `point_ledger`로 분리되어 위조 불가([firestore-schema.md](firestore-schema.md) §5). 단 프록시는 버퍼를 그대로 echo하지 않고 `summary.*`로 재가공(필터·dedupe).
> **부분 실패 계약:** `/llm task=summary`는 세 내부 콜의 결과를 단일 SSE로 묶고, 종료 시 `done.sections = {expressions, words, coaching}`를 반환한다([backend-functions.md](backend-functions.md) §10).

---

## 5. 슬림/깊이 분할 (옛 6단계 절단)
옛 `sentence_feedback`의 단일 6섹션을 2콜로:
- **`feedback.slim`(매 턴)** = 옛 Step 1(작문 점수) + Step 2(문법 교정) + Step 4(자연스러움, 이유 1개)
- **`feedback.deep`("더 보기"만)** = 옛 Step 3(개념 브릿지+벤) + Step 5(톤 5단) + Step 6(패러프레이즈 3단)

---

## 6. 색 산출 (탈-LLM)
- **점수 색:** LLM 미출력. 클라가 점수로 산출 — **정본 임계 green ≥70 / orange 50–69 / red <50**(옛 프롬프트 Rule 4와 동일). *(옛 클라 모델의 80/60은 폐기. expression_filter의 80/50 strictness 밴드는 색과 무관하므로 별개 유지.)*
- **Venn 색:** LLM은 `word`+`items`만 출력. 클라가 대비 가드로 계산(라이트/다크 ≥4.5/≥3.0, 좌우 구분) — 옛 `VennDiagramView`의 런타임 가드를 Compose로 재구현(B-3 백로그).

---

## 7. 견고성
- 콜별 `responseSchema` + "JSON only" → 파싱 실패 시 **repair 재시도 1회**(원문+오류 첨부) → 반복 실패 시 해당 섹션 **graceful skip/에러 상태**.
- 스트림 콜: 중괄호-깊이 점진 파서가 완성 객체만 추출(부분 JSON 안전), 키 순서는 `propertyOrdering`로 고정.
- `speaking.analyze` 출력은 **`{transcript, feedbackMessage}`로 좁힘** — 옛 `fluency/confidence/hesitations` 3필드 능동 제거(A8). 옛 프로소디 파생 점수 경로(`BottomSheetSceneRenderer.java:232`) **재도입 금지**.

---

## 8. 비용
암묵 캐싱(공통 prefix) + transcript 재사용(중복 인식 제거) + 슬림 매 턴/깊이 온디맨드 + Flash + 콜별 max-tokens 상한. 10턴 세션 ≈ speaking·slim 각 10 + 요약 3 ≈ 23콜(일일 한도 3세션으로 상한).

---

## 9. 의사결정 로그 & 검토 이력

**핵심 결정:** 서버 버전드 프롬프트 · 호출별 JSON+responseSchema · 공유 톤/난이도/8오류/안전 prefix + 암묵 캐싱 · 슬림(턴)/깊이(온디맨드) 분할 · 스피킹 숫자 제거(전사+격려만) · 색 탈-LLM · 클라 버퍼 요약 입력 · 3티어(A2/B1/B1+).

**확정(2026-07-18):** feedback/feedbackDeep temperature = `0` — `functions/eval/`의 골든셋 14케이스 × 온도(0/0.1/0.2/0.3) × 5회 반복(280콜, `gemini-3.1-flash-lite`)으로 측정해 확정했다. 모든 온도에서 구조 위반 0건·기대치 불일치 0건이었고, 점수 표준편차(평균)는 t=0에서 0.6, 그 외 온도에서 1.2–1.4로 t=0이 분산을 절반 가까이 줄였다(0.1~0.3 세 온도는 서로 통계적으로 구분 안 됨). 이전에 스윕 범위에서 빠진 t=0.7에서는 학습자 오류를 아예 놓친 사례가 있어 더 높은 온도는 추가 탐색하지 않았다. `functions/src/config/generation.ts`가 SoT이며, 이 결정은 아래 두 줄 전에 있던 `피드백·요약 0.3` 가정(미검증)을 대체한다. dialogue/summary는 여전히 eval 커버리지가 없어 미설정(프로바이더 기본값)이며 아직 needs-you다.
**가정(needs-you/튜닝):** dialogue/summary temperature·max-tokens·실제 문안 authoring·deep coaching 배열 vs 문자열(→ 레거시 `{positive, toImprove}` 채택).

**확정(2026-07-18, 프롬프트 문안):** feedback 프롬프트에 (a) 금지 어미를 이름으로 지목한 해요체 규칙(`-입니다`/`-습니다`/`-ㅂ니다` 금지, `-답니다`/`-랍니다`는 허용, 칭찬 문맥을 명시 경고), (b) 5레벨 LEVEL 블록(`explanation`/`reason.description`과 제안 문장만 레벨에 따라 적응시키고 `writingScore.score`는 절대 유지) (c) `korean-error-reference`의 8개 오류 계열을 COMMON ERRORS로 접어 넣었다. `functions/eval/`로 동일 명령(`--temps=0 --repeats=5 --task=feedback`, 70콜)을 수정 전후로 재서 측정: 말투 위반 40건 → 0건, 레벨 민감도 실패(비교된 5회 중 설명 동일 0회·제안 문장 동일 5회) → 통과(5회 모두 설명·제안 문장이 레벨에 따라 달라짐), 구조 위반은 전후 모두 0건 유지. 리포트: `functions/eval/out/feedback-2026-07-18T12-56-04-939Z.md`(기준선), `functions/eval/out/feedback-2026-07-18T13-23-07-699Z.md`(수정 후). `FEEDBACK_PROMPT_VERSION = "2026-07-18"`.

부수 효과로 점수가 14케이스 중 8케이스에서 5~10점 하향 이동했고(상향은 0건), COMMON ERRORS 블록이 새로 접히면서 모델이 콩글리시·관사·전치사 같은 오류 계열을 이전엔 넘어가던 사례에서도 잡아내 더 엄격하게 채점한 것이 원인으로 보인다. 사용자에게 보이고 채점 밴드를 재조정하지 않은 채 수용하기로 한 의도적 결정이다 — "hand phone"이 95점을 받던 것이 과했다는 판단이며, 아직 사용자가 없어 기준선을 옮기기 가장 저렴한 시점이라는 점도 근거로 들었다.

**grill-review 이력:** R1 Blocker 4(① 스피킹에 정답 주입→전사 편향 ② 요약 입력 누적 미정의 ③ 캐시 바닥 ④ responseSchema/스트림 이분법) → 수정 → R2 **0 Blocker SHIP**. 잔여 5 Advisory(캐시 바닥 수치 1024/2048·모델 ID 고정·coaching `{positive,toImprove}` 레거시 계약·propertyOrdering·스피킹 출력 좁힘+totalScore 전달) 전부 반영.

---

## 부록 — 파일 맵
- 공유: [`prompts/_shared/`](prompts/_shared/) (tone-and-style · difficulty-bands · korean-error-reference · safety-scope)
- 콜: [`prompts/`](prompts/) (dialogue-generate · speaking-analyze · feedback-slim · feedback-deep · summary-words · summary-expressions · summary-coaching)
- 레거시 참고: `archive/android/app/src/main/assets/prompts/`, `archive/android/docs/schemas/summary_fragment_payload.schema.json`

> 이 .md들은 **설계/초안**이다. 구현 시 `config/prompts`(서버)로 이관하고 모델 ID·responseSchema(코드)·max-tokens를 확정한다.
