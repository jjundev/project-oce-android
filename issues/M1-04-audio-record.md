---
milestone: M1
area: android
size: M
labels: [milestone:M1, area:android, ready-for-agent]
blocked_by: [M0-01]
blocks: [M1-06, M1-08]
---

# [M1-04] 오디오 녹음 + 실시간 파형 (I2)

> 정본 트래커는 GitHub Issues(`gh`) — 본 파일은 승격 전 계획 백로그(`/to-issues`).

## 컨텍스트
학습자 발화 녹음. 16kHz PCM + 실시간 파형(crackle) + 무음 감지(너무 조용하면 재시도 유도).

## SoT (재결정 금지 — 인용만)
- 스펙: [docs/design/audio-pipeline.md](../docs/design/audio-pipeline.md)(I2 파형 정본) · [docs/ui/03-signature-interactions.md](../docs/ui/03-signature-interactions.md) I2 · [PRD.md](../PRD.md) §8.2.2, FR-8, NFR-6
- 시각(외형 정본): [prototype/Prototype Flow (standalone).html](../prototype/) `session`(Recording)

## 목표
마이크 탭 시 16kHz PCM 녹음 + 실시간 파형이 그려지고, 정지 시 무음이면 재시도를 유도한다.

## 범위
- In: 16kHz·16bit·mono PCM 캡처, 실시간 파형 Canvas(40바 ±0.3, Recording 한정), 무음 감지, 파형 dp 고정(96dp, 폰트스케일 무관, A7).
- Out: 마이크 4상태 통합/announce(M1-08), 전사·분석(M1-06), 권한 프라이밍 UI(C13, M0-06에서 컴포넌트).

## 의존성
- Blocked by: M0-01
- Blocks: M1-06, M1-08

## 수용 기준
- [ ] 16kHz PCM WAV 캡처(스펙 포맷)
- [ ] 실시간 파형이 Recording에서만 렌더, crackle 스펙 일치
- [ ] 무음 감지 시 재시도 유도
- [ ] 파형 dp 고정(A7)

## 검증
녹음 → WAV 포맷 검증, 무음 케이스 유닛/계측 테스트.
