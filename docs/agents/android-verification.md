# Android 검증(gradle) — 워크트리 환경 함정과 부트스트랩

git 워크트리(`.claude/worktrees/<name>`)에서 Android 모듈을 gradle 로 검증할 때, **코드와 무관하게**
재발하는 환경 문제가 있다. 결론부터: 검증은 `scripts/verify-android.sh` 로 돌린다.

```bash
scripts/verify-android.sh                                   # 기본 검증 세트
scripts/verify-android.sh :app:testDebugUnitTest --tests '*DevHarness*'   # 특정 태스크
```

## 재발하는 함정

| 함정 | 재발성 | 증상 | 원인 |
|---|---|---|---|
| `google-services.json` 부재 | **매번(새 워크트리)** | `processDebugGoogleServices … File google-services.json is missing` | gitignore 파일이라 워크트리 생성 시 복사되지 않음. 변이 어셈블/단위테스트가 이 태스크에 의존 |
| 워크트리 간 캐시/증분 오염 | **높음** | 스테일 `UP-TO-DATE`, 타 워크트리 절대경로가 박힌 리포트, **내용 변경을 못 잡음** | 여러 워크트리가 `~/.gradle`(GRADLE_USER_HOME)·데몬·`build-cache`(`org.gradle.caching=true`)를 공유 |
| KGP 변이 단위테스트 소스셋 미컴파일 | **매번** | `src/test{Debug,Release}/kotlin` 의 테스트가 조용히 미실행 | 이 KGP 버전이 변이별 단위테스트 소스셋의 `kotlin/` 를 컴파일 소스로 자동 등록하지 않음(공유 `src/test`·메인 변이 `src/debug`만 됨) |

### 오염이 얼마나 질긴가 (실증)
`--rerun-tasks`, `--no-build-cache`, `-Pkotlin.incremental=false`, gradle/Kotlin 데몬 kill,
`app/build`·`.gradle` 삭제를 **전부** 해도 `~/.gradle` 공유 상태에선 재현됐다. `AppRoot.kt` 에 일부러
`val x: Int = "not_an_int"` 타입 에러를 넣어도 컴파일이 통과할 정도. **워크트리 전용 GRADLE_USER_HOME**
격리로만 확실히 사라졌다. 즉 gradle `BUILD SUCCESSFUL` 을 액면 그대로 믿지 말 것.

## 부트스트랩이 하는 일 (`scripts/verify-android.sh`)
1. `google-services.json` 이 없으면 **메인 워크트리**(`git worktree list` 첫 항목)에서 복사(gitignore — 커밋 안 됨).
2. `ANDROID_HOME` 미설정 시 `~/Library/Android/sdk` 주입.
3. `GRADLE_USER_HOME` 을 **워크트리 전용**(`~/.gradle-oce/<worktree>`)으로 고정·재사용 → 오염 원천 차단.
4. `--no-build-cache` 로 검증 태스크 실행(인자 없으면 detekt + androidTest 컴파일 + 양 변이 단위테스트).

> 워크트리별 gradle 홈은 최초 1회 의존성 프로비저닝(네트워크·수 분·수백 MB)이 필요하고 이후 재사용된다.
> 디스크가 부담되면 `GRADLE_USER_HOME` 를 공유 경로로 오버라이드할 수 있으나 오염 위험을 감수해야 한다.

## 규약
- **변이별 단위테스트(`testDebug`/`testRelease`)는 `src/test<Variant>/java/` 에 둔다** — `.kt` 여도
  AGP 가 포함하는 `java` 소스 디렉터리라 KGP 가 컴파일·실행한다(예: `DevHarnessDebugTest.kt`).
- `ktlintMainSourceSetCheck` 는 기본 검증 세트에서 제외한다: master 에 선존재하는 위반
  (`DialogueTurnScreen.kt` import 정렬)이 있어 항상 실패한다(신규 변경과 무관 — 정리 시 별도 처리).
