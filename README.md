# 딸깍영어 (OneClickEng)

> 상황을 고르고, 영어로 말하고, 바로 피드백받는 AI 영어회화 연습 앱입니다.

딸깍영어는 짧고 부담 없는 역할극 대화로 실제 영어회화를 연습하게 돕습니다. 학습자는 상황과 난이도를 고른 뒤 AI와 대화하고, 말한 표현에 대한 피드백을 받은 다음 핵심 표현과 다음 학습 포인트를 요약으로 확인합니다.

## 핵심 학습 흐름

1. 상황 선택 — 일상·입문, 여행, 업무·커리어, 생활·서비스 주제에서 대본을 고릅니다.
2. 역할극 대화 — 안내에 따라 말하거나 입력하며 대화를 이어갑니다.
3. 즉시 피드백 — 문법과 자연스러운 표현을 확인합니다.
4. 학습 요약 — 핵심 표현을 저장하고 다음 학습을 준비합니다.

## 프로젝트 구성

| 경로 | 역할 |
| --- | --- |
| [`android/`](android/) | Kotlin·Jetpack Compose 기반 Android 앱 |
| [`PRD.md`](PRD.md) | 제품 요구사항과 핵심 사용자 경험 |
| [`docs/`](docs/) | 디자인 시스템, UX 정책, 아키텍처 결정 기록 |
| [`prototype/`](prototype/) | 화면·인터랙션 실현 기준 프로토타입 |
| [`functions/`](functions/) | Firebase Cloud Functions |
| [`play-console-assets/`](play-console-assets/) | Google Play 등록용 로고, 아이콘, 피처 그래픽, 스크린샷 |

## 시작하기

요구 사항:

- Android Studio 및 Android SDK
- JDK 17
- 개발용 Firebase 구성(`android/app/google-services.json`) — 저장소에는 포함하지 않습니다.

```bash
cd android
./gradlew :app:assembleDebug
```

## 테스트와 스크린샷

```bash
cd android
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest -Proborazzi.record
```

Roborazzi가 기록한 이미지의 기본 출력 위치는 `android/app/build/outputs/roborazzi/`입니다. Play Console에 올릴 최종 파일과 각 파일의 설명은 [`play-console-assets/README.md`](play-console-assets/README.md)에서 확인합니다.

## 문서 안내

- 제품·UX·디자인 문서 인덱스: [`docs/README.md`](docs/README.md)
- 도메인 용어와 현재 문맥: [`CONTEXT.md`](CONTEXT.md)
- 중요한 설계 결정: [`docs/adr/`](docs/adr/)

## 라이선스

별도 라이선스가 아직 정의되지 않았습니다. 외부 배포 또는 재사용 전 프로젝트 소유자에게 문의하세요.
