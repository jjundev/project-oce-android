---
milestone: M0
area: android
size: S
labels: [milestone:M0, area:android, ready-for-agent]
blocked_by: [M0-01]
blocks: [M0-08, M3-01]
---

# [M0-02] Firebase 새 프로젝트 연결

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
greenfield 백엔드. 옛 데이터 미승계. 클라이언트에 Firebase SDK를 연결한다(키·규칙은 서버/M0-08).

## SoT (재결정 금지 — 인용만)
- 스펙: [PRD.md](../PRD.md) §10.2, 부록 A #8·#9 · [docs/design/firestore-schema.md](../docs/design/firestore-schema.md)
- 시각: 해당 없음

## 목표
Firebase 프로젝트(신규)에 앱이 연결되어 Auth·Firestore·Analytics SDK가 초기화된다.

## 범위
- In: Firebase 새 프로젝트 생성, `google-services.json` 반입, Auth(Anonymous·Google) / Firestore / Analytics SDK 의존성·초기화, 리전 asia-northeast3 정합.
- Out: 실제 익명 로그인 플로우(M3-01), 보안 규칙·스키마 배포(M0-08), Google 연결(M3-03).

## 의존성
- Blocked by: M0-01
- Blocks: M0-08, M3-01

## 수용 기준
- [ ] 앱 부팅 시 Firebase 초기화 로그 확인(크래시 없음)
- [ ] Firestore/Auth/Analytics 인스턴스 DI 제공
- [ ] `google-services.json`은 VCS 정책에 맞게 관리(비밀 노출 금지)

## 검증
디버그 실행 시 FirebaseApp 초기화 성공, Analytics 디버뷰에 세션 이벤트 도달.
