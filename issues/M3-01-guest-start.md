---
milestone: M3
area: android
size: S
labels: [milestone:M3, area:android, ready-for-agent]
blocked_by: [M0-02]
blocks: [M3-02, M3-03]
---

# [M3-01] 게스트 시작 (Firebase Anonymous)

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
앱 최초 실행 시 로그인 없이 게스트로 자동 시작. 회원가입·투어·권한은 첫 성공 이후로 미룬다.

## SoT (재결정 금지 — 인용만)
- 스펙: [PRD.md](../PRD.md) §8.1, FR-1 · [docs/design/firestore-schema.md](../docs/design/firestore-schema.md)(profile.isGuest)
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) 온보딩 진입

## 목표
최초 실행 시 Firebase Anonymous로 자동 로그인되어 게스트 UID·profile이 생성된다.

## 범위
- In: 익명 자동 로그인, `profile` 문서 생성(isGuest=true, createdAt), 세션 지속.
- Out: 온보딩 2문항(M3-02), Google 연결(M3-03).

## 의존성
- Blocked by: M0-02
- Blocks: M3-02, M3-03

## 수용 기준
- [ ] 최초 실행 시 로그인 화면 없이 게스트 시작(FR-1)
- [ ] 익명 UID + profile(isGuest) 생성
- [ ] 재실행 시 동일 게스트 세션 유지

## 전제(환경)
- 실 `android/app/google-services.json` 반입 필요(`.example`만 커밋, 실파일은 `.gitignore` 처리 — M0-02).
- Firebase 콘솔에서 **Anonymous 인증 provider 활성화** 필수 — 미활성 시 `signInAnonymously()`가 `auth/admin-restricted-operation`으로 실패해 아래 스모크 검증이 불가하다.

## 검증
클린 설치 시 익명 로그인 스모크. profile 문서 생성 확인.
