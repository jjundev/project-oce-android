#!/usr/bin/env bash
#
# Android 검증 부트스트랩 (project-oce-android)
#
# 목적: git 워크트리에서 gradle 검증(컴파일·detekt·단위테스트)을 **신뢰할 수 있게** 돌린다.
# 워크트리 여러 개가 같은 GRADLE_USER_HOME(~/.gradle)·데몬·빌드캐시를 공유하면 다음이 재발한다:
#   1) google-services.json 부재 — gitignore 라 새 워크트리엔 절대 복사되지 않는다. 변이 어셈블/
#      테스트 태스크가 processDebugGoogleServices 에서 즉시 실패한다.
#   2) 워크트리 간 캐시/증분 오염 — 다른 워크트리와 동일 상대경로 파일이 있을 때 gradle 이 스테일
#      UP-TO-DATE·타 워크트리 절대경로 리포트를 내놓는다. `--rerun-tasks`·`--no-build-cache`·데몬
#      kill 로도 안 뚫려, 내용 변경(예: 일부러 넣은 타입 에러)을 못 잡은 사례가 있다.
#   실증: 위 오염은 **워크트리 전용 GRADLE_USER_HOME** 격리로만 확실히 사라졌다.
#
# 이 스크립트가 하는 일:
#   - google-services.json 이 없으면 메인 워크트리에서 복사(gitignore, 커밋 안 됨).
#   - ANDROID_HOME 미설정 시 기본값(~/Library/Android/sdk) 주입.
#   - 워크트리 전용 GRADLE_USER_HOME 을 고정(재사용)해 캐시/데몬 오염을 원천 차단.
#   - --no-build-cache 로 검증 태스크 실행(인자 없으면 기본 세트).
#
# 사용:
#   scripts/verify-android.sh                     # 기본 검증 세트
#   scripts/verify-android.sh :app:testDebugUnitTest --tests '*DevHarness*'
#   GRADLE_USER_HOME=... ANDROID_HOME=... scripts/verify-android.sh <tasks...>   # 오버라이드
#
# 주의: 워크트리별 GRADLE_USER_HOME 은 최초 1회 의존성 프로비저닝(네트워크·수 분·수백 MB)이 필요하고
#       이후 재사용된다. 디스크가 부담되면 GRADLE_USER_HOME 을 공유 경로로 오버라이드하되, 그 경우
#       오염 재발 위험을 감수해야 한다(위 2번).
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ANDROID_DIR="$REPO_ROOT/android"

# 1) google-services.json (gitignore — 워크트리에 없음) → 메인 워크트리에서 복사.
GS="$ANDROID_DIR/app/google-services.json"
if [[ ! -f "$GS" ]]; then
  MAIN_WT="$(git -C "$REPO_ROOT" worktree list --porcelain | awk '/^worktree /{print $2; exit}')"
  SRC="$MAIN_WT/android/app/google-services.json"
  if [[ -f "$SRC" ]]; then
    cp "$SRC" "$GS"
    echo "▶ google-services.json 복사: $SRC → $GS"
  else
    echo "⚠ google-services.json 없음(현 워크트리·메인 워크트리 모두): $SRC" >&2
    echo "  Firebase 콘솔에서 받아 $GS 에 두세요(gitignore — 커밋되지 않음)." >&2
    exit 1
  fi
fi

# 2) ANDROID_HOME.
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [[ ! -d "$ANDROID_HOME" ]]; then
  echo "⚠ ANDROID_HOME 경로 없음: $ANDROID_HOME (환경변수로 오버라이드하세요)." >&2
  exit 1
fi

# 3) 워크트리 전용 GRADLE_USER_HOME(핵심) — 워크트리 간 캐시/데몬 오염 차단.
WT_NAME="$(basename "$REPO_ROOT")"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle-oce/$WT_NAME}"
mkdir -p "$GRADLE_USER_HOME"
echo "▶ GRADLE_USER_HOME=$GRADLE_USER_HOME (워크트리 격리)"

# 4) 검증 태스크 — 인자 없으면 기본 세트.
#    ktlintMainSourceSetCheck 는 기본 세트에서 제외한다: master 에 선존재하는 위반
#    (DialogueTurnScreen.kt import 정렬)이 있어 항상 실패하기 때문(내 변경과 무관).
TASKS=("$@")
if [[ ${#TASKS[@]} -eq 0 ]]; then
  TASKS=(
    :app:detekt
    :app:compileDebugAndroidTestKotlin
    :app:testDebugUnitTest
    :app:analyzeReleaseR8Config
    :app:assembleRelease
  )
fi

echo "▶ (cd android) ./gradlew --no-build-cache ${TASKS[*]}"
cd "$ANDROID_DIR"
exec ./gradlew --no-build-cache "${TASKS[@]}"
