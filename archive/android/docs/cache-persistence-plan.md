# Gemini Prompt Cache 로컬 저장 및 재사용 구현 계획

## 현재 문제점

`GeminiCachedFeedbackManager`는 앱이 시작될 때마다(`onViewCreated`) 매번 새로운 캐시를 생성하고, Fragment가 파괴될 때(`onDestroyView`) 캐시를 삭제한다.

- `cachedContentName`이 메모리에만 저장되어 앱 종료 시 유실됨
- TTL이 1시간(3600초)임에도 불구하고 앱을 다시 열면 기존 캐시를 버리고 새로 생성
- 불필요한 API 호출로 인한 비용 낭비 및 초기화 지연 시간 발생

## 구현 목표

SharedPreferences에 캐시 정보를 저장하고, 앱 재시작 시 기존 캐시의 남은 유효기간을 확인하여 재사용함으로써 불필요한 캐시 생성을 제거한다.

---

## 상세 구현 계획

### 1단계: SharedPreferences에 캐시 메타데이터 저장

**파일:** `GeminiCachedFeedbackManager.java`

SharedPreferences에 저장할 항목:

| Key | Type | 설명 |
|-----|------|------|
| `gemini_cache_name` | String | 캐시 리소스 이름 (e.g., `cachedContents/abc123`) |
| `gemini_cache_created_at` | long | 캐시 생성 시각 (`System.currentTimeMillis()`) |
| `gemini_cache_ttl_seconds` | int | 설정된 TTL (현재 3600초) |

**변경 사항:**
- 생성자에 `Context` 파라미터 추가 (SharedPreferences 접근용)
- `SharedPreferences` 인스턴스를 필드로 보유 (이름: `"gemini_cache_prefs"`)
- `initializeCache()` 성공 시 위 3개 값을 SharedPreferences에 저장
- `clearCache()` 호출 시 SharedPreferences 데이터도 함께 삭제

### 2단계: 캐시 유효성 확인 API 호출 메서드 추가

**파일:** `GeminiCachedFeedbackManager.java`

Gemini API의 `GET cachedContents/{name}` 엔드포인트를 호출하여 서버 측 캐시 상태를 확인하는 메서드를 추가한다.

```
GET https://generativelanguage.googleapis.com/v1beta/{cachedContentName}?key={apiKey}
```

**새 메서드: `validateCacheFromServer(String cacheName, ValidationCallback callback)`**

- 서버 응답에서 `expireTime` 필드를 파싱하여 남은 TTL 계산
- 응답이 404이면 캐시가 이미 만료/삭제된 것으로 판단
- 응답이 성공이면 남은 시간을 반환

**새 콜백 인터페이스:**
```
interface ValidationCallback {
    void onValid(String cacheName, long remainingSeconds);
    void onInvalid();  // 캐시가 존재하지 않거나 만료됨
    void onError(String error);
}
```

### 3단계: initializeCache() 로직 변경

**파일:** `GeminiCachedFeedbackManager.java`

기존 `initializeCache()` 메서드의 흐름을 다음과 같이 변경한다:

```
initializeCache(callback)
│
├─ SharedPreferences에서 캐시 정보 로드
│   ├─ 저장된 캐시 없음 → 새 캐시 생성 (기존 로직)
│   │
│   └─ 저장된 캐시 있음
│       │
│       ├─ 로컬 TTL 계산으로 빠른 사전 검증
│       │   (현재시각 - 생성시각 > TTL 이면 → 만료로 판단, 새 캐시 생성)
│       │
│       └─ 로컬 검증 통과 시 → 서버에 GET 요청으로 실제 유효성 확인
│           │
│           ├─ 유효 & 남은 시간 > MIN_REMAINING_TTL (300초=5분)
│           │   → 기존 캐시 재사용 (cachedContentName 설정, cacheReady = true)
│           │
│           ├─ 유효 & 남은 시간 <= MIN_REMAINING_TTL
│           │   → 기존 캐시 삭제 후 새 캐시 생성
│           │
│           └─ 유효하지 않음 (404 등)
│               → SharedPreferences 정리 후 새 캐시 생성
```

**새 상수:**
- `MIN_REMAINING_TTL_SECONDS = 300` (5분) — 이 값 이하로 남으면 새로 생성

### 4단계: onDestroyView에서 캐시 삭제 로직 제거

**파일:** `ScriptChatFragment.java`

현재 `onDestroyView()`에서 `cachedFeedbackManager.clearCache()`를 호출하는데, 캐시를 재사용하려면 이 호출을 제거해야 한다.

**변경 사항:**
- `onDestroyView()`에서 `clearCache()` 호출 제거
- 캐시는 서버 측 TTL에 의해 자동 만료되도록 위임
- 명시적 삭제는 사용자 로그아웃 또는 설정 초기화 시에만 수행

### 5단계: GeminiCachedFeedbackManager 생성자 변경에 따른 호출부 수정

**파일:** `ScriptChatFragment.java`

```java
// 변경 전
cachedFeedbackManager = new GeminiCachedFeedbackManager(GEMINI_API_KEY);

// 변경 후
cachedFeedbackManager = new GeminiCachedFeedbackManager(requireContext(), GEMINI_API_KEY);
```

---

## 전체 변경 파일 목록

| 파일 | 변경 내용 |
|------|----------|
| `GeminiCachedFeedbackManager.java` | Context 파라미터 추가, SharedPreferences 저장/로드, 서버 검증 메서드 추가, initializeCache 흐름 변경 |
| `ScriptChatFragment.java` | 생성자 호출 변경, onDestroyView에서 clearCache 제거 |

---

## 예상 동작 시나리오

### 시나리오 1: 최초 앱 실행
1. SharedPreferences에 저장된 캐시 없음
2. 새 캐시 생성 → 성공 시 SharedPreferences에 저장
3. 정상 사용

### 시나리오 2: 앱 재시작 (캐시 유효기간 충분)
1. SharedPreferences에서 캐시 정보 로드
2. 로컬 TTL 사전 검증 통과 (e.g., 생성 후 20분 경과, 40분 남음)
3. 서버 GET 요청 → 유효 확인, 남은 시간 40분 > 5분
4. 기존 캐시 재사용 → **새 캐시 생성 없이 즉시 준비 완료**

### 시나리오 3: 앱 재시작 (캐시 유효기간 거의 만료)
1. SharedPreferences에서 캐시 정보 로드
2. 로컬 TTL 사전 검증 통과 (e.g., 생성 후 56분 경과, 4분 남음)
3. 서버 GET 요청 → 유효하지만 남은 시간 4분 < 5분
4. 기존 캐시 삭제 → 새 캐시 생성 → SharedPreferences 업데이트

### 시나리오 4: 앱 재시작 (캐시 이미 만료)
1. SharedPreferences에서 캐시 정보 로드
2. 로컬 TTL 사전 검증에서 만료로 판단 (e.g., 생성 후 70분 경과)
3. 서버 요청 없이 바로 새 캐시 생성 → SharedPreferences 업데이트

---

## 주의사항

- **스레드 안전성:** SharedPreferences 읽기/쓰기는 `apply()` (비동기) 사용, 읽기는 메인 스레드에서도 가능하지만 캐시 검증 전체 흐름은 백그라운드 스레드에서 실행
- **expireTime 파싱:** Gemini API는 `expireTime`을 ISO 8601 형식(e.g., `2024-01-01T12:00:00Z`)으로 반환하므로 `Instant.parse()` 또는 `SimpleDateFormat`으로 파싱 필요 (Min SDK 31이므로 `Instant` 사용 가능)
- **에러 처리:** 서버 검증 실패 시 무조건 새 캐시 생성으로 폴백 (기존 동작과 동일하게 보장)
- **clearCache()는 유지:** 메서드 자체는 삭제하지 않고, 수동 캐시 무효화가 필요한 경우(디버그, 설정 변경 등)를 위해 보존. SharedPreferences도 함께 정리하도록 수정
