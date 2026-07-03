---
milestone: M0
area: android
size: M
labels: [milestone:M0, area:android, ready-for-agent]
blocked_by: []
blocks: [M0-02, M0-03, M0-04, M0-09, M1-04]
---

# [M0-01] Compose/M3 프로젝트 스캐폴드

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
v1 완전 재구축의 M0 첫 착수 항목. 옛 Java/XML/M2 앱을 폐기하고 모던 스택 위에 빈 프로젝트를 세운다.

## SoT (재결정 금지 — 인용만)
- 스펙: [PRD.md](../PRD.md) §10.1, NFR-9(minSdk/targetSdk), 부록 A #3
- 시각: 해당 없음(빌드 인프라)

## 목표
빌드·실행 가능한 단일 Activity Compose 앱 골격. 앱이 빈 화면으로 뜨고 CI가 통과한다.

## 범위
- In: Kotlin 2.x(JDK 17 타깃), Jetpack Compose + Material 3, **단일 Activity**, minSdk 26 / targetSdk 36, Hilt(DI) 셋업, Gradle 버전 카탈로그(libs.versions.toml), detekt/ktlint, MVVM/MVI 기반 패키지 구조 뼈대.
- Out: 실제 화면·내비(M0-09), 테마 토큰(M0-03), Firebase(M0-02).

## 의존성
- Blocked by: —
- Blocks: M0-02, M0-03, M0-04, M0-09, M1-04

## 수용 기준
- [ ] `./gradlew assembleDebug` 성공, 앱이 빈 Compose 화면으로 실행됨
- [ ] minSdk 26 / targetSdk 36 / JDK 17 / Kotlin 2.x 확정 반영
- [ ] Hilt `@HiltAndroidApp` + 단일 `@AndroidEntryPoint` Activity 부팅
- [ ] 버전 카탈로그로 의존성 중앙화, detekt/ktlint 게이트 통과

## 검증
`./gradlew assembleDebug lint detekt` 그린. 에뮬레이터 부팅 확인.
