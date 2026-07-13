# 주제 카탈로그

Android의 정본 주제 카탈로그는 [`android/app/src/main/assets/topics.json`](../../android/app/src/main/assets/topics.json)이다. 앱은 이 번들 자산을 런타임에 읽으며, 별도의 JSON·CSV·Kotlin 목록을 유지하지 않는다.

## 스키마

최상위 객체는 `version: 1`과 `topics` 배열을 가진다. 각 주제는 다음 필드를 가진다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | string | 고유한 kebab-case 식별자 |
| `emoji` | string | 표시용 이모지 |
| `icon` | string | Android 전용 아이콘. 반드시 `OceIcon.name`과 정확히 일치 |
| `titleKo` | string | 한국어 표시 제목 |
| `group` | string | `daily`·`travel`·`work`·`life` 중 하나 |
| `beginnerFriendly` | boolean | 온보딩 첫 선택 후보 여부 |
| `promptSeed` | string | 대화 생성기에 전달하는 한 줄 영어 시나리오 |

## 수량과 온보딩

카탈로그는 총 300개 주제를 가지며, 각 그룹은 정확히 75개다. 온보딩 후보는 다음 여섯 ID뿐이다: `cafe-order`, `hobby-intro`, `hotel-checkin`, `restaurant`, `taxi`, `weather-smalltalk`.

## Firestore 내보내기

Firestore `config/topics`로 카탈로그를 내보낼 때는 Android 전용 `icon` 필드를 포함하지 않는다. 내보내기 projection은 `id`, `emoji`, `titleKo`, `group`, `beginnerFriendly`, `promptSeed` (및 필요 시 표시 순서)만 사용한다. Android 클라이언트의 런타임 정본은 언제나 이 자산이다.
