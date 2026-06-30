# 딸깍영어 v1 — 오디오 파이프라인 설계 (B-3)

> **상태:** 설계 확정(v1, 수렴·SHIP) · **작성일:** 2026-06-30 · **대상:** PRD FR-7/8/9/10/12 · NFR-3/6
> **근거:** [PRD.md](../../PRD.md) §8.2(대화 루프)·§10.1/10.3·§11(파형 시그니처) · 옛 앱 오디오 코드(`archive/android`: `AudioRecorder`·`RecordedAudioSilenceDetector`·`RecordingAudioPlayer`·`GeminiTtsManager`·`WaveformView`·2 coordinator) · TTS 음성·로케일·속도·폴백 정책은 [tts.md](tts.md)(OQ3 확정) 소유
> **도출 과정:** `grill-yourself`(자율 설계 20결정) → `grill-review --deep auto`(Blocker 5 → 0 SHIP, 2 iteration). 옛 앱 2-coordinator·TTS 워치독·2 feedback manager 구조와 일치 확인.

---

## 1. 범위 & 원칙

**B-3 범위:** 마이크 입력(녹음·무음·파형) → 백엔드 전송(전사) · 상대역 음성 출력(TTS 재생) · 음성 4상태(마이크 버튼). 전부 Kotlin Coroutines/Flow + Hilt 주입 인터페이스로 재구현(테스트 용이).

**범위 밖(명시):** **슬림 문장 피드백(작문 점수·문법·표현, 스트리밍) 호출은 B-3가 아님** — 대화 feature + B-1(프롬프트) 소관. B-3는 `{transcript, feedbackMessage}`까지 만들고 transcript를 feature 레이어로 핸드오프한다. (옛 앱도 `ISpeakingFeedbackManager`[오디오·단항]와 `ISentenceFeedbackManager`[텍스트·스트리밍]가 분리돼 있었음.)

**원칙:** 모든 LLM/TTS 호출은 백엔드 프록시 경유(키 서버). 학습자 마이크 상태와 상대역 재생 상태는 **별도 상태기계**(옛 2-coordinator 계승). 늦은 콜백은 토큰 가드로 차단(FR-14).

---

## 2. 상태기계 (3-holder)

| 상태기계 | 값 | 소유자 | 의미 |
|---|---|---|---|
| **MicState** | Ready / Recording / Analyzing / Complete | `SpeakingController` | FR-12 마이크 버튼 4상태(학습자 전용) |
| **OpponentPlayback** | Idle / Playing | `SpeechPlayer` | 상대역 TTS 재생 여부 |
| **TurnPhase** | OpponentTurn / LearnerTurn | ViewModel | 턴 오케스트레이션 |

**불변식(반드시 명시 구현):**
- **TurnPhase가 MicState를 게이트한다** — `OpponentTurn` 동안 마이크 탭은 무효이고, MicState는 `TurnPhase==LearnerTurn`일 때만 `Ready`를 벗어난다. (3-holder race 방지)
- **`MicState.Complete`는 `/llm task=speaking` 반환 시점**에 발동한다 — 범위 밖 문장 피드백 호출 완료와 **무관**(안 그러면 Complete를 영영 못 기다림).
- 상대역 재생 진실 = `SpeechPlayer.isPlaying` / 마이크 진실 = `MicState`. 단일 소유, 중복 없음.
- `VoiceState`(UI가 마이크 버튼에 바인딩) ≡ `MicState`.
- 권한 요청 중, 녹음 컨트롤러 시작 대기, empty transcript 복구 같은 세부 사유는 `MicState` 값을 늘리지 않는다. 제품 UX 문서가 UI-local transient reason으로 소유하고, 오디오 레이어는 4상태와 typed 결과만 노출한다.

---

## 3. 컴포넌트 & 파일 레이아웃 (Kotlin)
```
core/audio/
  RecordingController.kt (interface)   AudioRecordRecordingController.kt (impl)
  SpeechPlayer.kt        (interface)   AudioTrackSpeechPlayer.kt        (impl)
  AudioMath.kt           (normalizedRms, voicedFraction)
  MicState.kt / OpponentPlayback.kt (sealed)
data/audio/
  TtsClient.kt / SpeakingAnalysisClient.kt  (프록시 호출)
  TtsCache.kt            (LruCache)
feature/dialogue/audio/
  SpeakingController.kt  (record→silence→/llm task=speaking→MicState 오케스트레이션)
ui/audio/
  WaveformCanvas.kt      (@Composable, StateFlow<FloatArray> 관찰)
  MicButton.kt           (MicState 4상태 렌더)
di/ AudioModule.kt       (Hilt)
```

## 4. 인터페이스 (계약 — typed error + cancel/token)
```kotlin
interface RecordingController {
  val waveform: StateFlow<FloatArray>          // 40 막대(crackle)
  val state: StateFlow<RecordingState>         // Idle/Recording
  suspend fun start()                          // 권한 보유 전제. AudioInitError 던질 수 있음
  suspend fun stop(): RecordingResult          // 취소 가능(코루틴)
}
sealed interface RecordingResult {
  data class Captured(val pcm: ByteArray, val sampleRate: Int, val durationMs: Long): RecordingResult
  data object TooQuiet: RecordingResult
  data class Failed(val error: AudioError): RecordingResult
}
interface SpeechPlayer {                         // TTS 재생 전용(녹음 리플레이 없음)
  val isPlaying: StateFlow<Boolean>
  suspend fun play(pcm: ByteArray, sampleRate: Int)  // 완료까지 suspend, 취소 가능, PlaybackError 던짐
  fun stop()
}
interface TtsClient {                            // 백엔드 프록시 + 워치독(timeout)
  suspend fun synthesize(text:String, voice:String, locale:String, speechRate:Float): TtsClip  // {pcm, sampleRate}
}
interface SpeakingAnalysisClient {               // 백엔드 프록시
  suspend fun analyze(sessionId:String, wav:ByteArray): SpeakingResult          // {transcript, feedbackMessage}
}
```
- 모든 suspend는 typed 에러(`AudioError`/`PlaybackError`/네트워크) 또는 코루틴 취소로 종료 → FR-14 stale-guard·중도 abort 모델.

## 5. 녹음 서브시스템
- **API/포맷:** `AudioRecord`(raw PCM), `MediaRecorder.AudioSource.VOICE_RECOGNITION`, **16kHz·mono·PCM 16-bit**, CHUNK 1024B. (`AudioRecorder.java:18-20,62`)
- **캡처:** `Dispatchers.IO` 단일 루프(Flow), `read(1024)` → `ByteArrayOutputStream` 누적 + 청크별 정규화 RMS 산출. 협조적 취소(`isActive`) + `finally` 해제. 1024B=512샘플≈32ms → ~31fps.
- **최대 길이:** **20초 기본**(단문 발화 충분), 초과 시 자동 정지. *(needs-you: 캡 값.)*
- **무음 게이트(정지 후):** 전체-버퍼 평균 금지(선·후행 무음 희석). 청크 RMS(파형용 이미 계산) 재사용 →
  - `voicedFraction` = RMS > **0.02**인 청크 비율
  - **TooQuiet 판정:** `voicedFraction < 0.1` **또는** `maxChunkRMS < 0.05` → 전송 안 함, "다시 말해볼까요" + Ready 복귀.
- **무음 알고리즘:** 정규화 RMS([0,1]), 리틀엔디안 16-bit 디코드. (`RecordedAudioSilenceDetector.java:16-35`)

## 6. 파형 (crackle 모델)
- **40 막대**(barSpacing/cornerRadius 4dp), 그레이 세로 그라데이션. (`WaveformView.java:20`)
- **crackle:** 매 프레임 **전 막대**를 현재 청크 진폭으로 세팅 + 막대별 **±랜덤 지터**(스크롤 아님). 옛 `addAmplitude()` = `amplitude ± (rand−0.5)*0.6` 계승.
- **진폭 매핑:** RMS × gain(≈3.0) clamp [0,1], **floor 0.05**(초기 0.1 아님). (`WaveformView.java:114` 등)
- Compose Canvas가 `RecordingController.waveform: StateFlow<FloatArray>` 관찰.

## 7. 재생 서브시스템 (상대역 TTS)
> TTS의 음성·로케일·속도·워치독·폴백·캐시 정책은 **[tts.md](tts.md)가 소유(OQ3 확정)**. 여기서는 오디오 파이프라인 측 **재생 메커니즘**만 다룬다.
- **TTS fetch:** `TtsClient.synthesize(text, voice, locale, speechRate)` → 프록시 `POST /llm` `task=tts` → `{pcmBase64, sampleRate}` — **일반 JSON 응답**(텍스트 SSE 아님), base64 PCM 24kHz ~190-380KB/라인. `voice`=생성기 `opponent_gender` 매핑(male=`Puck`/else=`Kore`), `locale`=en-US, `speechRate`(0.5~1.5, 백엔드 프롬프트 베이크). 상세 [tts.md](tts.md) §1-3.
- **재생(Gemini PCM 경로):** `AudioTrack MODE_STATIC`(클립당 재생성), `setUsage(USAGE_MEDIA)+setContentType(CONTENT_TYPE_SPEECH)`, sampleRate는 응답값(24k). end-marker(`len/2` 프레임)→`onComplete`. `playbackToken` stale 가드. (`RecordingAudioPlayer.java:59-78,86`)
- **단말 폴백 경로:** Gemini 워치독(8s) 초과/실패 시 **단말 `android.speech.tts.TextToSpeech`** 로 폴백(영어 데이터 있을 때), 없으면(`LANG_MISSING_DATA`/`LANG_NOT_SUPPORTED`) 음성 없이 텍스트만. 이 경로는 AudioTrack이 아닌 시스템 TTS가 재생 — [tts.md](tts.md) §4. (∴ 재생기는 PCM 경로 + 시스템 TTS 경로 2개.)
- **캐시:** **없음**(라인마다 대사 고유 — [tts.md](tts.md) §3). 세션 내 재생(스피커 버튼) 캐시는 후속.
- **워치독·진행(NFR-6):** 폴백 체인 **Gemini 8s → 단말 7s → (불가 시) 텍스트만**. **실패/타임아웃/음소거 모두 단일 진행 경로**(`onComplete`)로 수렴 → 자동 진행(stall 금지). 상세 [tts.md](tts.md) §4. (옛 `scheduleScriptWatchdog`·`blockPlaybackIfMuted→onComplete` 계승.)

## 8. 데이터 흐름
**상대역 턴(OpponentTurn):**
```
TtsClient.synthesize(캐시 우선) → SpeechPlayer.play → 완료/실패/타임아웃/음소거 → onComplete → TurnPhase=LearnerTurn
```
**학습자 턴 — 음성(LearnerTurn):**
```
마이크 탭(권한 확인) → MicState=Recording → 청크 RMS→waveform
재탭/20s → stop():
  TooQuiet → MicState=Ready(재시도, 전송 없음 → 프록시/usage 낭비 없음)
  Captured → 클라가 PCM16을 WAV 래핑 → MicState=Analyzing → SpeakingAnalysisClient.analyze(wav)
           → {transcript, feedbackMessage} → MicState=Complete
           → transcript를 feature 레이어로 핸드오프(범위 밖 문장 피드백 호출)
```
- `transcript == ""`인 정상 반환은 오디오 레이어 관점에서는 `MicState.Complete`다. 이후 slim 피드백을 시작할지, 재시도/텍스트 입력으로 복구할지는 제품 UX 레이어가 결정한다.
**학습자 턴 — 텍스트(FR-9):**
```
"채팅으로 입력하기" → 녹음/analyze 스킵, MicState=Ready 유지
타이핑 텍스트 = transcript로 feature 레이어 핸드오프
턴 진행은 TurnPhase(feature) 소유(MicState.Complete를 기다리지 않음)
```

## 9. 백엔드 계약 (정본)
백엔드 호출 정본은 [backend-functions.md](backend-functions.md)의 **단일 `/llm` 프록시**다. `/speaking`·`/tts` 별도 endpoint는 v1 정본이 아니다.

- `POST /llm` `{task:"speaking", sessionId, payload:{audioBase64}}` → `{transcript, feedbackMessage}`. `audioBase64`는 **16kHz·16bit·mono WAV**다. 클라이언트는 녹음한 PCM16을 WAV 컨테이너로 감싼 뒤 전송한다. 20s ≈ 640KB PCM + WAV 헤더(베이스64 약 853KB)로 업로드 예산에 포함한다. (압축 Opus는 캡 상향 시.)
- `POST /llm` `{task:"tts", sessionId?, payload:{text, voice, locale, speechRate}}` → `{pcmBase64, sampleRate}`. TTS는 일반 JSON 응답이며 SSE가 아니다.

## 10. 횡단 규칙
- **권한:** 첫 마이크 탭 시 `RECORD_AUDIO` 요청(근거 표시) + 사전 체크. (`AudioRecorder.java:36-40`, PRD 온보딩)
- **오디오 포커스(net-new — 옛 앱엔 없음):** TTS 재생 전 transient 요청, 완료/정지 시 abandon. **record·play 양쪽** 포커스 상실(전화 등) 시 정지.
- **겹침 없음:** 상대역 TTS와 학습자 녹음은 턴 순차(에코 방지 자연 해결).
- **라이프사이클:** stop/pause/이탈 시 `record.release()`·`track.release()`·스코프 취소, 토큰 가드. (`AudioRecorder.java:132-161`)
- **녹음 원본 보관:** 사용자 원본 오디오는 영구 저장하지 않는다. 현재 턴 처리와 재시도에 필요한 메모리 임시 버퍼만 허용하며, 세션 완료·중단·앱 종료 시 폐기한다. Firestore 저장 카드·세션 요약에는 transcript/피드백 텍스트만 남긴다.
- **음소거(FR-21):** TTS 합성·재생 스킵, 텍스트만 + 자동 진행(§7 단일 경로).
- **에러:** 마이크 초기화 실패·권한 거부·네트워크 실패 → 비난 없는 카피 + 재시도(Ready 복귀).

## 11. 의사결정 로그 (요지)
| 결정 | 선택 | 근거 |
|---|---|---|
| 녹음 API | AudioRecord raw PCM | RMS·파형·전사 (`AudioRecorder.java:60-66`) |
| 포맷/소스 | 16k mono PCM16 / VOICE_RECOGNITION | ASR 요구·튜닝 |
| 스레딩 | Coroutines/Flow(IO) | 옛 Thread 모던화 |
| 상태기계 | **2분리 + TurnPhase 게이트** | 옛 2-coordinator, FR-12 마이크 전용 |
| 무음 게이트 | voiced-fraction/peak(전체평균 아님) | 희석 오거부 방지 |
| 파형 | crackle(전 막대 ±지터), floor 0.05 | PRD §11 시그니처, `WaveformView` |
| 업로드 | WAV base64 → `/llm task=speaking` | `prompt-system.md`의 `audio/wav만` 계약과 정합 |
| 캡 | 20s | NFR-3 예산 정합 |
| TTS | MODE_STATIC, speechRate(프롬프트 베이크)≠sampleRate(MIME) | `RecordingAudioPlayer`·`GeminiTtsManager` |
| 워치독 | 합성·재생 타임아웃 → 단일 진행 경로 | NFR-6, 옛 `scheduleScriptWatchdog` |
| 범위 | transcript+feedbackMessage까지(문장 피드백 제외) | 옛 2 feedback manager 이음새 |

## 12. 미해결 가정 & 의존성 & v1 한계
**needs-you:**
- 최대 녹음 캡 = **20s**(제품 판단, 단문엔 충분).

**해결됨(OQ3 → [tts.md](tts.md)):** TTS 음성(2음성 성별 매핑 Puck/Kore)·로케일(en-US 단일)·속도(1.0x, 0.5~1.5)·**단말 TTS 폴백은 v1 포함**(Gemini 8s 워치독 → 단말 → 텍스트만). *(본 문서 초안의 "Gemini 전용·폴백 후속"은 tts.md가 폴백 포함으로 확정 — 상위 정합.)*

**의존성:**
- **B-2(LLM 프록시):** `/llm task=speaking|tts` 정본 계약 구현 필요(§9).
- **B-1(프롬프트):** transcript를 소비하는 슬림 문장 피드백 호출(범위 밖).

**v1 한계:** 무음 게이트 임계(0.02/0.1/0.05)·gain(3.0)·캡(20s)·워치독(7s)은 단말 편차로 운영 보정. auto-VAD(말 끝나면 자동 정지)는 후속. WAV(무압축 PCM 컨테이너) — 대역폭 이슈 시 Opus.

## 13. 검토 이력
`grill-review --deep auto` (2 iteration):
- **iter1:** Blocker 5(VoiceState 4상태가 상대역 재생 못 담음 · FR-10 2번째 호출 미명세 · TTS 워치독 누락 · 60s base64 vs NFR-3 · 별도 오디오 endpoint 의존) + Advisory 9 → 수정.
- **iter2:** **Blocker 0 / Advisory 4 → SHIP.** 잔여는 암묵 불변식 명시(TurnPhase 게이트·Complete 시점·무음 파라미터·텍스트 턴 진행 소유) — 본 문서에 반영. 옛 앱 2-coordinator·워치독·2 feedback manager 구조와 일치 확인.
