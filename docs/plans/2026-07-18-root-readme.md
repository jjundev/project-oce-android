# Root README Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a Korean root README that orients contributors to the 딸깍영어 Android app and links its product, implementation, documentation, and Play Console assets.

**Architecture:** Add one repository-level entry document only; no Android code, Gradle configuration, or Firebase configuration changes are needed. The README will describe the app's learning loop, point contributors to the authoritative PRD and docs index, identify the Android module as the runnable app, and provide exact verified commands for tests and screenshot capture.

**Tech Stack:** Markdown, Android/Kotlin/Jetpack Compose, Gradle, Robolectric, Roborazzi, Firebase.

## Global Constraints

- Create `README.md` at the repository root because no root README currently exists.
- Write user-facing documentation in Korean and keep product claims aligned with `PRD.md` and `docs/README.md`.
- Link the generated submission files through `play-console-assets/`; do not move or duplicate any image files.
- Use the Android Gradle wrapper from `android/` and Java 17-compatible tooling.
- Do not include secrets, Firebase project identifiers, or a `google-services.json` file in documentation.

---

### Task 1: Create the repository entry README

**Files:**
- Create: `README.md`
- Reference: `PRD.md`
- Reference: `docs/README.md`
- Reference: `android/app/build.gradle.kts:18-24`
- Reference: `play-console-assets/README.md`

**Interfaces:**
- Consumes: existing repository structure and verified Play Console asset paths.
- Produces: the repository's primary contributor and product entry point at `README.md`.

- [ ] **Step 1: Write the README acceptance checklist**

Capture the required sections before authoring: app purpose, key learning flow, repository map, local run/test commands, documentation links, and Play Console asset link. The root file must not claim the legacy `docs/README.md` implementation status as current app status.

```markdown
## README acceptance checklist

- Korean product overview names 딸깍영어 (OneClickEng).
- The core flow is 상황 선택 → 역할극 대화 → 즉시 피드백 → 학습 요약.
- The Android app entry, product spec, docs index, prototype, Firebase functions, and Play assets each have a repository-relative link.
- Commands run from `android/` and use `./gradlew`.
- The document contains no credentials or copied image binaries.
```

- [ ] **Step 2: Create the root README with the verified content**

Create `README.md` with the following content:

```markdown
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
```

- [ ] **Step 3: Verify links and commands are internally consistent**

Run:

```bash
test -f README.md
for path in android PRD.md docs prototype functions play-console-assets play-console-assets/README.md CONTEXT.md docs/adr; do
  test -e "$path"
done
rg -n 'google-services\.json|assembleDebug|testDebugUnitTest|Roborazzi' README.md
```

Expected: every path check exits successfully; the README contains the three Gradle-related command references and describes `google-services.json` without including any credential value.

- [ ] **Step 4: Review the rendered Markdown and repository diff**

Run:

```bash
sed -n '1,240p' README.md
git diff --check -- README.md
git status --short README.md
```

Expected: the README headings, table, fenced commands, and relative links are legible; `git diff --check` produces no output; `README.md` appears as an untracked file ready for review.

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: add repository README"
```

Expected: one documentation-only commit containing the root README. Do not stage unrelated logo, asset, Android, or test changes from the working tree.

## Self-Review

- Spec coverage: Task 1 covers creation of the missing root README, links the app and documentation entry points, and surfaces the newly organized Play Console assets.
- Placeholder scan: no `TBD`, `TODO`, or undefined implementation instruction remains.
- Type consistency: no code interfaces are introduced; every referenced path exists in the repository at plan-writing time.

## Automatic Plan Review

Skipped: this is a single-task documentation plan, so an additional reviewer round-trip is not warranted.

## Execution Handoff

Plan complete and saved to `docs/plans/2026-07-18-root-readme.md`. Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
