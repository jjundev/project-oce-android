#!/usr/bin/env bash
#
# M0-08 — Firestore TTL 2정책 적용 (sessions.expiresAt, idempotency.expiresAt)
#
# 실행 주체: 운영자(1회). CI 미포함 — gcloud 자격증명이 필요하다.
# TTL 은 firebase.json/보안규칙으로 표현할 수 없는 Firestore 필드 레벨 설정이며,
# 에뮬레이터는 TTL 만료를 시뮬레이트하지 않으므로 규칙 단위테스트 범위 밖이다(firestore-schema.md §9).
# 필드 설정은 규칙/인덱스 재배포와 독립적으로 영속한다 — 컬렉션 스키마가 바뀔 때만 재실행하면 된다.
#
# 사전조건: gcloud CLI 인증 + oce-v1 프로젝트의 Datastore/Firestore Admin 권한.
# 검증: 하단 `ttl list` 출력에서 두 필드가 state: ACTIVE 인지 확인.
#
set -euo pipefail

PROJECT="${FIRESTORE_PROJECT:-oce-v1}"
DATABASE="${FIRESTORE_DATABASE:-(default)}"

echo "▶ TTL 정책 적용 — project=${PROJECT} database=${DATABASE}"

gcloud firestore fields ttl update expiresAt \
  --collection-group=sessions \
  --enable-ttl \
  --project="${PROJECT}" \
  --database="${DATABASE}"

gcloud firestore fields ttl update expiresAt \
  --collection-group=idempotency \
  --enable-ttl \
  --project="${PROJECT}" \
  --database="${DATABASE}"

echo "▶ 적용 결과 확인(두 필드 모두 state: ACTIVE 여야 함):"
gcloud firestore fields ttl list \
  --project="${PROJECT}" \
  --database="${DATABASE}"
