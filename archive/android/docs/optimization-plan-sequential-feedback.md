# Sentence Feedback 최적화 계획서: 오디오 → 텍스트 기반 순차 처리

## 1. 현재 구조 분석

### 현재 플로우 (병렬 처리)
```
사용자 녹음 완료 (PCM 오디오 데이터)
         │
         ├──────────────────────────────┐
         │                              │
         ▼                              ▼
  GeminiFluencyManager            GeminiCachedFeedbackManager
  .analyzeAudio(audioData)        .analyzeAudioWithCache(audioData, originalSentence)
         │                              │
         │  [오디오 → Gemini 2.0 Flash]  │  [오디오 → Gemini 3 Flash Preview]
         │  - fluency 점수               │  - 오디오를 텍스트로 변환 (내부 ASR)
         │  - confidence 점수            │  - 문법 분석
         │  - hesitations 카운트         │  - 자연스러움 분석
         │  - transcript (음성 텍스트)    │  - 톤/스타일 분석
         │                              │  - 패러프레이징
         │  ⏱️ ~2-4초                    │  ⏱️ ~8-15초 (병목)
         │                              │
         ▼                              ▼
  Feedback UI 표시               Sentence Feedback UI 표시
  (빠르게 완료)                   (오래 걸림 ❌)
```

### 문제점
1. **중복 ASR(음성→텍스트 변환)**: 두 API 모두 동일한 오디오를 받아 각각 독립적으로 음성인식 수행
2. **Sentence Feedback 병목**: 오디오 전송 + 내부 ASR + 복잡한 문장 분석을 한 번에 처리 → 응답 시간 8~15초+
3. **불필요한 네트워크 비용**: 동일 오디오 데이터를 두 곳에 전송 (Base64 인코딩된 WAV 파일 × 2)

---

## 2. 최적화된 구조 (순차 처리)

### 제안 플로우
```
사용자 녹음 완료 (PCM 오디오 데이터)
         │
         ▼
  [1단계] GeminiFluencyManager
  .analyzeAudio(audioData)
         │
         │  [오디오 → Gemini 2.0 Flash]
         │  - fluency, confidence, hesitations
         │  - transcript ← 핵심: 여기서 얻은 텍스트를 재사용!
         │
         │  ⏱️ ~2-4초
         ▼
  Fluency Feedback UI 즉시 표시
         │
         │  transcript 텍스트 전달
         ▼
  [2단계] GeminiCachedFeedbackManager
  .analyzeSentenceWithCache(originalSentence, transcript)
         │
         │  [텍스트만 → Gemini 3 Flash Preview]
         │  - ASR 불필요! 텍스트 직접 분석
         │  - 문법, 자연스러움, 톤, 패러프레이징
         │
         │  ⏱️ ~3-5초 (오디오 처리 없이 텍스트만!)
         ▼
  Sentence Feedback UI 표시 ✅
```

### 예상 시간 비교
| 항목 | 현재 (병렬) | 최적화 (순차) |
|------|------------|--------------|
| Fluency 분석 | ~2-4초 | ~2-4초 |
| Sentence 분석 | ~8-15초 (오디오) | ~3-5초 (텍스트) |
| **총 대기 시간** | **~8-15초** (병렬이지만 병목) | **~5-9초** (순차이지만 각 단계 빠름) |
| 네트워크 전송량 | 오디오 × 2 | 오디오 × 1 + 텍스트 × 1 |

---

## 3. 구현 상세

### 3.1 수정 대상 파일

| 파일 | 수정 내용 |
|------|----------|
| `ScriptChatFragment.java` | `transitionToAnalyzingState()` 메서드의 API 호출 순서를 순차로 변경 |

### 3.2 핵심 변경: `transitionToAnalyzingState()` 메서드

#### 현재 코드 (병렬 호출)
```java
private void transitionToAnalyzingState(String sentenceToTranslate) {
    // ... UI 상태 변경 ...

    byte[] audioData = audioAccumulator.toByteArray();

    // 1. Sentence Feedback (오디오) - 동시 시작
    cachedFeedbackManager.analyzeAudioWithCache(audioData, sentenceToTranslate, ...);

    // 2. Fluency Analysis (오디오) - 동시 시작
    geminiFluencyManager.analyzeAudio(audioData, ...);
}
```

#### 변경 코드 (순차 호출)
```java
private void transitionToAnalyzingState(String sentenceToTranslate) {
    // ... UI 상태 변경 (동일) ...

    byte[] audioData = audioAccumulator.toByteArray();
    lastRecordedAudio = audioData.clone();

    // Sentence Feedback 분석 준비
    pendingSentenceFeedback = null;
    isSentenceAnalysisRunning = true;

    // [1단계] Fluency Analysis 먼저 실행 (오디오 전송)
    geminiFluencyManager.analyzeAudio(audioData,
        new GeminiFluencyManager.AnalysisCallback() {
            @Override
            public void onSuccess(GeminiFluencyManager.FluencyResult result) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    // Fluency 결과 UI 즉시 표시
                    transitionToCompleteState(result, sentenceToTranslate, finalRecognizedText);

                    // [2단계] Fluency의 transcript로 Sentence Feedback 시작 (텍스트만 전송)
                    String transcript = (result.transcript != null && !result.transcript.isEmpty())
                            ? result.transcript : finalRecognizedText;
                    startSentenceFeedbackWithText(sentenceToTranslate, transcript);
                });
            }

            @Override
            public void onError(String error) {
                // ... 에러 처리 (동일) ...
            }
        });
}

/**
 * Fluency 분석 결과의 transcript를 사용하여 Sentence Feedback 요청 (텍스트 기반)
 */
private void startSentenceFeedbackWithText(String originalSentence, String userTranscript) {
    if (useSentenceCaching && cachedFeedbackManager.isCacheReady()) {
        cachedFeedbackManager.analyzeSentenceWithCache(originalSentence, userTranscript,
            new GeminiCachedFeedbackManager.FeedbackCallback() {
                @Override
                public void onSuccess(SentenceFeedback feedback, boolean usedCache) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        pendingSentenceFeedback = feedback;
                        isSentenceAnalysisRunning = false;
                        updateSentenceFeedbackUI(feedback);
                    });
                }

                @Override
                public void onError(String error) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        isSentenceAnalysisRunning = false;
                        updateSentenceFeedbackUI(null);
                    });
                }
            });
    } else {
        geminiFeedbackManager.analyzeSentence(originalSentence, userTranscript,
            new GeminiFeedbackManager.FeedbackCallback() {
                @Override
                public void onSuccess(SentenceFeedback feedback) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        pendingSentenceFeedback = feedback;
                        isSentenceAnalysisRunning = false;
                        updateSentenceFeedbackUI(feedback);
                    });
                }

                @Override
                public void onError(String error) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        isSentenceAnalysisRunning = false;
                        updateSentenceFeedbackUI(null);
                    });
                }
            });
    }
}
```

### 3.3 변경하지 않는 부분

| 항목 | 이유 |
|------|------|
| `GeminiFluencyManager.java` | 이미 transcript를 반환하므로 수정 불필요 |
| `GeminiCachedFeedbackManager.java` | `analyzeSentenceWithCache()` 텍스트 기반 메서드가 이미 존재 |
| `GeminiFeedbackManager.java` | `analyzeSentence()` 텍스트 기반 메서드가 이미 존재 |
| 텍스트 입력 모드 (`showFeedbackContent`) | 이미 텍스트 기반으로 동작하므로 영향 없음 |
| UI 레이아웃 파일들 | 변경 불필요 |

### 3.4 Fluency transcript 비어있는 경우 대비 (엣지 케이스)

Fluency API가 transcript를 반환하지 못하는 경우를 대비한 폴백 처리:

```java
String transcript = (result.transcript != null && !result.transcript.isEmpty())
        ? result.transcript
        : "(No speech detected)";

// transcript가 유효하지 않으면 Sentence Feedback 스킵
if (transcript.equals("(No speech detected)") || transcript.equals("(인식된 음성 없음)")) {
    isSentenceAnalysisRunning = false;
    updateSentenceFeedbackUI(null); // 분석 불가 표시
    return;
}
```

---

## 4. UX 고려사항

### 4.1 로딩 상태 표시

사용자 경험 관점에서 순차 처리의 각 단계를 명확히 표시:

```
[녹음 완료] → "채점 중..." (현재와 동일)
    │
    ▼
[Fluency 결과 도착] → Speaking Feedback 표시 + "정밀 분석 중..." (Sentence 분석 대기)
    │
    ▼
[Sentence 결과 도착] → Sentence Feedback 표시 ✅
```

이 UX는 현재 구현과 동일합니다. 차이점은:
- **현재**: Fluency가 먼저 오고, Sentence는 한참 후에 옴 (이미 병렬이지만 Sentence가 느림)
- **최적화 후**: Fluency가 먼저 오고, Sentence가 그 직후 빠르게 따라옴

### 4.2 에러 처리 시나리오

| 시나리오 | 처리 방법 |
|---------|----------|
| Fluency 분석 실패 | 에러 표시, Sentence 분석 시작하지 않음 |
| Fluency 성공 + Sentence 분석 실패 | Fluency 결과는 표시, Sentence 영역에 에러 표시 |
| Fluency 성공 + transcript 비어있음 | Fluency 결과 표시, Sentence 분석 스킵 |

---

## 5. 요약

### 변경 범위
- **수정 파일**: `ScriptChatFragment.java` 1개
- **수정 메서드**: `transitionToAnalyzingState()` 리팩토링 + `startSentenceFeedbackWithText()` 추가
- **신규 파일**: 없음
- **삭제 코드**: Sentence Feedback의 오디오 기반 호출 부분 (`analyzeAudioWithCache`, `analyzeAudio` 등)

### 기대 효과
1. **응답 속도 40~50% 개선**: 총 대기 시간 8~15초 → 5~9초
2. **네트워크 비용 절감**: 오디오 데이터 전송 1회로 감소
3. **API 처리 효율화**: Sentence Feedback API가 ASR 없이 텍스트만 처리
4. **코드 간소화**: 오디오 기반 Sentence 분석 코드 제거 가능
