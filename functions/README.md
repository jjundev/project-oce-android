# oce-functions — 백엔드 스캐폴드 (M0-07)

딸깍영어 v1 Cloud Functions. 현재는 **`/llm` 프록시 골격**만 존재한다 — 인증·task
디스패치·벤더 시임·타입드 SSE 전송 규칙을 갖춘 스텁이며, 실제 핸들러 동작(대본/피드백/
요약/스피킹/TTS)과 B-1 프롬프트는 M1+에서 채운다.

정본 설계: [`docs/design/backend-functions.md`](../docs/design/backend-functions.md).

## 구조
```
src/
  index.ts            # 진입점 — llm 만 export
  llm/
    handler.ts        # onRequest 바인딩(region/secret/minInstances)
    handle.ts         # 순수 요청 파이프라인(테스트 대상)
    auth.ts           # verifyIdToken(익명 허용) → 401
    dispatch.ts       # task 검증 + 응답모드(SSE/JSON) 해소
    sse.ts            # SSE 전송 규칙(no Content-Length·flush·X-Accel-Buffering:no)
  providers/
    LlmProvider.ts    # 벤더 중립 시임(3메서드)
    gemini.ts         # GeminiProvider 골격(본문 throw NOT_IMPLEMENTED)
  config/
    models.ts         # task→모델ID(플레이스홀더, ops 확정 시 스왑)
    prompts.ts        # B-1 주입점(빈 레지스트리)
  types/
    protocol.ts       # 공용 타입: Task/ResponseMode/ErrorCode/ErrorBody
    sse.ts            # SSE 엔벨로프 discriminated union
test/                 # Jest offline 단위테스트(에뮬레이터·네트워크 불요)
```

## 명령
- `npm run build` — TypeScript 컴파일(→ `lib/`).
- `npm test` — offline 단위테스트(auth·dispatch·sse·handler).
- `npm run serve` — Functions 에뮬레이터.
- `npm run smoke` — 에뮬레이터 스모크(수동 SSE flush 확인용).
- `npm run deploy` — 배포(프로젝트 프로비저닝 이후에만).

## 운영 주의
- **min-instances:** 프로덕션은 SoT대로 **1 고정**(워밍, NFR-3). `LLM_MIN_INSTANCES`
  파라미터는 비프로덕션(테스트/스테이징) 전용 노브다. 프로덕션 0은 NFR-3 재위반 →
  금지(변경 시 backend-functions.md·NFR-3 동반 개정).
  - **배포 체크:** 프로덕션 배포 env/`.env`에 `LLM_MIN_INSTANCES`가 설정돼 있지
    않은지 확인(미설정 시 코드 기본값 1 사용). 값(region/secret 이름/기본값)은
    `src/llm/options.ts`에 집약돼 있고 `test/options.test.ts`가 회귀를 막는다.
- **DoD(M0-07):** build + 에뮬레이터 스모크 + 단위테스트 green. 실 `firebase deploy`는
  백엔드 Firebase/GCP 프로젝트 프로비저닝 이후. `.firebaserc`의 `default`는
  프로비저닝 시 실 프로젝트 ID로 교체할 플레이스홀더다.

## firebase.json 소유(M0-08 조율)
루트 `firebase.json`은 본 이슈(M0-07)가 `functions`·`emulators` 키로 **신설**한다.
[M0-08](../issues/M0-08-firestore-rules-schema.md)은 이 파일에 `firestore`(rules/
indexes)·TTL 설정을 **확장(추가)**해야 하며 **재생성하지 않는다**.
