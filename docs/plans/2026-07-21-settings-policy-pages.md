# 설정 정책 페이지 및 Firebase Hosting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use subagent-driven-development (recommended) or executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 설정 탭의 개인정보 처리방침·이용약관 링크가 실제 Firebase Hosting 정책 페이지를 열도록 만들고, Google Play 계정 삭제 요건을 만족하는 웹 삭제 안내 페이지까지 함께 게시한다.

**Architecture:** 저장소 루트에 Firebase Hosting 전용 `web/` 정적 사이트를 두고 `/privacy`, `/terms`, `/delete-account`를 각각 독립 flat HTML 문서로 제공한다. Android는 `SettingsUrls`와 외부 브라우저 실행 코드를 실제 배포 호스트와 맞추고, 링크·페이지·배포 설정을 통합 검증한다. 정책 문구는 베타용 경량 초안으로 작성하되, 앱의 실제 Firebase/Auth/Firestore/AI·음성 처리 동작만 반영하고 법무 확정 전 공개 승격은 막는다.

**Tech Stack:** Firebase Hosting, 정적 HTML/CSS, Firebase CLI, Kotlin/Jetpack Compose, Android JVM/Compose UI tests

## Global Constraints

- 검증된 배포 URL은 `https://oce-v1.web.app/privacy`, `https://oce-v1.web.app/terms`, `https://oce-v1.web.app/delete-account`를 사용한다. 원래 의도한 `oneclickeng.web.app`은 Firebase Hosting에 매핑되지 않아 404이므로 사용하지 않는다.
- 설정 정보 섹션에서는 개인정보처리방침·이용약관만 외부 브라우저로 열고, 계정 삭제 웹 경로는 Google Play data-safety/스토어 등록용으로 제공한다.
- 정책 본문은 `docs/ux/settings-data-account.md` §9의 “표준 템플릿 기반 베타용 경량 초안” 범위를 따르며, 법무 확정 문구를 가장하지 않는다.
- Firebase Hosting은 기존 Firebase 프로젝트 별칭 `oce-v1`을 사용하며 Firestore/Functions 설정은 변경하지 않는다.
- Android의 기존 `SettingsUrls` 공개 경로와 `SettingsScreen`의 외부 브라우저 동작을 불필요하게 재설계하지 않는다.
- 모든 공개 문서에는 최종 수정일과 문의 채널을 표시하며, 실제 사업자명·대표자·주소·이메일은 법무/운영 담당자가 제공한 확정값만 사용한다.

---

## File Structure

- Create: `web/index.html` — Hosting 루트에서 세 정책 페이지로 이동할 수 있는 안내/검증용 랜딩 페이지.
- Create: `web/privacy.html` — 개인정보 처리방침 경량 초안. 수집 항목, 이용 목적, 보관/삭제, 제3자 서비스, 이용자 권리, 문의를 설명한다.
- Create: `web/terms.html` — 이용약관 경량 초안. 서비스 이용, 계정, AI 결과의 성격, 금지행위, 서비스 변경/중단, 책임 제한, 문의를 설명한다.
- Create: `web/delete-account.html` — 웹 계정 삭제 요청 경로. 앱 내 삭제 경로와 서버 처리 범위/문의 절차를 안내한다.
- Create: `web/styles.css` — 세 페이지가 공유하는 반응형·접근성 중심 스타일.
- Modify: `firebase.json` — Hosting `public: "web"` 및 clean URL/정적 파일 설정 추가. 기존 Firestore/Functions 설정 보존.
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsUrls.kt` — 실제 게시 경로가 코드에 고정되어 있음을 확인하고, 필요할 때만 주석/상수 계약을 정리한다.
- Test: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsUrlsTest.kt` — URL path/host 계약을 고정한다.
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt` — 정보 섹션의 두 정책 행과 외부 링크 affordance를 캡처/검증한다.
- Optional documentation update: `docs/ux/settings-data-account.md` §9 — 실제 Hosting 배포 상태와 법무 확정 전 공개 제한을 기록한다.

## Execution Notes: Firebase에 웹페이지를 게시하는 방법

구현자는 아래 순서로 로컬에서 확인한 뒤 배포한다.

```bash
firebase use oce-v1
firebase emulators:start --only hosting
curl -I http://127.0.0.1:5000/privacy
curl -I http://127.0.0.1:5000/terms
curl -I http://127.0.0.1:5000/delete-account
firebase deploy --only hosting
curl -I https://oce-v1.web.app/privacy
curl -I https://oce-v1.web.app/terms
curl -I https://oce-v1.web.app/delete-account
```

배포 전 `firebase login`이 필요하고, 실제 운영 정책 문구가 법무 확정되지 않았다면 배포는 미리보기/클로즈드 베타 검증까지만 수행한다. `firebase deploy --only hosting`은 Firestore rules나 Cloud Functions를 배포하지 않으므로 이번 작업의 외부 변경 범위를 Hosting으로 제한한다.

## Precondition: 공개 문구에 필요한 운영 입력

Task 1 Step 1을 시작하기 전에 운영/법무 담당자는 다음 값을 구체적으로 확정해 작업자에게 전달해야 한다. 값이 없으면 페이지 골격과 베타 초안만 만들고 공개 배포/Play 등록 단계로 진행하지 않는다.

- 정책에 표시할 법적 사업자/운영 주체명과 주소
- 이용자 문의 이메일 또는 실제 운영 중인 문의 폼 URL
- 이용약관의 준거법과 관할 법원
- 음성 녹음과 AI 요청 입력의 저장 여부·보관 기간·제공 대상
- Firebase Analytics 및 Google/Firebase 서비스에서 실제 활성화된 수집·위탁 항목
- 웹 계정 삭제 요청의 본인 확인 방식과 처리 완료 목표 기간

이 값들은 코드나 저장소에서 안전하게 추론할 수 없는 외부 사실이므로 임의의 이메일, 주소, 법률 문구로 채우지 않는다. 이 선행 조건을 계획에 포함하는 것은 구현을 중단하기 위한 모호한 TODO가 아니라, 정책 문서가 사실과 다른 상태로 공개되지 않도록 하는 명시적 실행 게이트다.

### Task 1: 정책 콘텐츠 계약 및 공통 정적 사이트 골격

**Files:**
- Create: `web/index.html`
- Create: `web/privacy.html`
- Create: `web/terms.html`
- Create: `web/delete-account.html`
- Create: `web/styles.css`

**Interfaces:**
- Consumes: `docs/ux/settings-data-account.md` §8–§12, `docs/design/firestore-schema.md`, `docs/design/backend-functions.md`, 현재 `functions/src/account/deleteAccount.ts`의 실제 삭제 범위.
- Produces: `/privacy`, `/terms`, `/delete-account`에서 200 응답하는 정적 문서와 공통 스타일.

- [ ] **Step 1: 실제 데이터 처리 사실을 먼저 대조한다**

  문서에 넣을 사실을 코드/설계 문서에서 확정한다. 최소한 익명/Google Firebase Authentication, 사용자 프로필·학습 진도·저장 카드·통계의 Firestore 저장, AI 대화/피드백 요청, TTS 음성 처리, Firebase Analytics 사용 여부, 계정 삭제 시 `users/{uid}` 하위 데이터 삭제와 만료성 루트 문서의 보존 정책을 확인한다. 확인되지 않은 SDK나 수집 항목은 정책에 추가하지 않는다.

- [ ] **Step 2: 공통 스타일과 문서 내비게이션을 작성한다**

  `web/styles.css`에는 모바일 우선 본문 폭, 충분한 대비, 키보드 포커스, `prefers-reduced-motion`, `main/nav/footer` 스타일을 작성한다. 각 페이지 상단에는 서비스명 `딸깍영어 (OneClickEng)`, 제목, 최종 수정일, 세 페이지 링크를 넣고 모든 문서가 상대 링크로 서로 이동되게 한다.

- [ ] **Step 3: 개인정보 처리방침 페이지를 작성한다**

  `web/privacy.html`에 다음 순서의 실제 초안 섹션을 넣는다: 목적/적용 범위, 수집·처리 항목과 수집 시점, 이용 목적, 보관 및 삭제, Firebase/Google 등 수탁·제3자 서비스, 이용자 권리 및 계정 삭제, 안전성 조치, 정책 변경, 문의. “음성 녹음이 서버에 저장되는지”, “AI 제공자에게 전달되는 입력 범위”, “Analytics 수집 항목”은 Step 1에서 확인한 사실만 기재한다.

- [ ] **Step 4: 이용약관 페이지를 작성한다**

  `web/terms.html`에 서비스 정의, 이용 자격/계정, 학습용 AI 결과는 정확성·전문성 보장을 하지 않는다는 고지, 금지행위, 지식재산권, 서비스 변경/중단, 이용 제한/탈퇴, 책임 범위, 약관 변경, 준거법/관할(운영 담당자 확정값), 문의를 작성한다. 법률상 효력을 단정하는 과도한 표현은 베타 초안 표시와 함께 법무 검토 대상으로 명시한다.

- [ ] **Step 5: 웹 계정 삭제 안내 페이지를 작성한다**

  `web/delete-account.html`에 “로그인 사용자는 앱 설정에서 계정 삭제를 완료할 수 있다”는 인앱 경로, 앱 접근이 불가능할 때의 웹 요청 방법(`ufo4hyun@gmail.com`), 본인 확인에 필요한 최소 정보, 삭제되는 `users/{uid}` 하위 데이터 범위, 삭제 완료 예상 절차, 이미 만료 정책에 맡기는 데이터가 있다면 그 보존 사유를 명시한다. 비밀번호/인증 토큰을 HTML 폼으로 직접 받지 않는다.

- [ ] **Step 6: 로컬 정적 문서 검증**

  ```bash
  test -f web/index.html
  test -f web/privacy.html
  test -f web/terms.html
  test -f web/delete-account.html
  rg -n "privacy|terms|delete-account|최종 수정|문의" web
  ```

  Expected: 네 문서가 존재하고 세 경로·최종 수정일·문의 섹션이 모두 검색된다. 브라우저에서 각 페이지를 열어 깨진 상대 링크, 수평 스크롤, 포커스 불가 링크가 없는지 확인한다.

- [ ] **Step 7: Commit**

  ```bash
  git add web
  git commit -m "feat: add policy and account deletion pages"
  ```

### Task 2: Firebase Hosting 설정 및 배포 검증

**Files:**
- Modify: `firebase.json`
- Modify: `docs/ux/settings-data-account.md` §9 (배포/운영 상태 기록)

**Interfaces:**
- Consumes: Task 1의 `web/` 정적 사이트.
- Produces: Firebase Hosting이 루트 `/`, `/privacy`, `/terms`, `/delete-account`를 `web/`에서 제공하는 배포 가능한 구성.

- [ ] **Step 1: Firebase 설정 변경 전 회귀 검증**

  ```bash
  firebase use oce-v1
  firebase deploy --only firestore:rules --dry-run
  firebase deploy --only functions --dry-run
  ```

  Expected: 기존 Firestore/Functions 설정이 별도 배포 대상으로 인식되고, Hosting 추가가 그 설정을 대체하지 않는다. dry-run 지원 버전이 아니면 `firebase deploy --only hosting --debug` 전에 `firebase.json` diff로 기존 키 보존을 확인한다.

- [ ] **Step 2: `firebase.json`에 Hosting만 추가한다**

  기존 JSON을 유지하면서 다음 Hosting 블록을 추가한다.

  ```json
  "hosting": {
    "public": "web",
    "cleanUrls": true,
    "ignore": ["firebase.json", "**/.*", "**/node_modules/**"]
  }
  ```

  `rewrites`로 모든 경로를 `index.html`에 덮어쓰지 않는다. 디렉터리의 `index.html`이 `/privacy`, `/terms`, `/delete-account`를 자연스럽게 처리해야 하며, 정책 페이지가 서로 독립적으로 열려야 한다.

- [ ] **Step 3: Hosting 에뮬레이터에서 경로를 확인한다**

  ```bash
  firebase emulators:start --only hosting --project oce-v1
  curl -fsS -o /tmp/oce-privacy.html -w "%{http_code}\n" http://127.0.0.1:5000/privacy
  curl -fsS -o /tmp/oce-terms.html -w "%{http_code}\n" http://127.0.0.1:5000/terms
  curl -fsS -o /tmp/oce-delete.html -w "%{http_code}\n" http://127.0.0.1:5000/delete-account
  ```

  Expected: 각 명령이 `200`을 출력하고 저장된 HTML에 해당 페이지 제목이 포함된다. 에뮬레이터는 검증 후 종료한다.

- [ ] **Step 4: 실제 Hosting에 배포한다**

  ```bash
  firebase login
  firebase use oce-v1
  firebase deploy --only hosting
  ```

  Expected: CLI가 Hosting release 성공과 `https://oce-v1.web.app` 또는 연결된 기본 도메인을 출력한다. 이번 명령은 Hosting만 배포하므로 Firestore rules/Functions는 변경되지 않는다.

- [ ] **Step 5: 공개 URL smoke test**

  ```bash
  for path in privacy terms delete-account; do
    curl -fsS -o "/tmp/oce-${path}.html" -w "${path}: %{http_code}\n" "https://oce-v1.web.app/${path}"
  done
  rg -n "개인정보|이용약관|계정 삭제" /tmp/oce-privacy.html /tmp/oce-terms.html /tmp/oce-delete-account.html
  ```

  Expected: 세 URL이 모두 `200`이고 각 문서의 핵심 제목이 검색된다. 404/인증서/리다이렉트 오류가 있으면 Android URL 상수를 바꾸기 전에 Firebase Hosting site/domain 설정을 먼저 교정한다.

- [ ] **Step 6: 운영 문서를 갱신한다**

  `docs/ux/settings-data-account.md` §9에 실제 배포 도메인, `/delete-account`의 Play Console 등록 목적, 법무 확정 전 베타 제한, 정책 변경 시 재배포 절차를 기록한다. 법무 확정 전이라면 상태를 “베타 초안/공개 승격 전 검토 필요”로 남긴다.

- [ ] **Step 7: Commit**

  ```bash
  git add firebase.json docs/ux/settings-data-account.md
  git commit -m "chore: configure Firebase Hosting for policy pages"
  ```

### Task 3: Android 설정 링크 계약 및 UI 회귀 검증

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsUrls.kt` (필요한 주석/계약만)
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsUrlsTest.kt`
- Modify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt`

**Interfaces:**
- Consumes: Task 2에서 배포한 `https://oce-v1.web.app/privacy`와 `/terms`.
- Produces: Android에서 설정 > 정보의 두 행이 동일 URL을 외부 브라우저에 전달하며, 계정 삭제 웹 페이지는 앱 정보 행에 잘못 노출되지 않는다는 회귀 보장.

- [ ] **Step 1: URL 계약 테스트를 작성한다**

  ```kotlin
  @Test
  fun policyUrlsUseThePublishedFirebaseHostingPaths() {
      assertEquals("https", Uri.parse(SettingsUrls.PRIVACY).scheme)
      assertEquals("oce-v1.web.app", Uri.parse(SettingsUrls.PRIVACY).host)
      assertEquals("/privacy", Uri.parse(SettingsUrls.PRIVACY).path)
      assertEquals("https", Uri.parse(SettingsUrls.TERMS).scheme)
      assertEquals("oce-v1.web.app", Uri.parse(SettingsUrls.TERMS).host)
      assertEquals("/terms", Uri.parse(SettingsUrls.TERMS).path)
  }
  ```

  필요한 import는 `android.net.Uri`, `com.jjundev.oneclickeng.feature.settings.SettingsUrls`, `kotlin.test.Test`, `kotlin.test.assertEquals`로 한다.

- [ ] **Step 2: URL 테스트를 실행해 현재 계약을 확인한다**

  ```bash
  cd android
  ./gradlew :app:testDebugUnitTest --tests '*SettingsUrlsTest'
  ```

  Expected: 기존 상수가 게시 경로와 일치하면 PASS. 불일치하면 `SettingsUrls.kt`만 수정해 Task 2의 공개 URL과 정확히 맞춘다.

- [ ] **Step 3: 설정 정보 섹션 스크린샷 회귀를 추가한다**

  기존 `SettingsScreenScreenshotTest`의 공통 `SettingsContent` 테스트 패턴을 따라 정보 섹션이 `settings_info_privacy`, `settings_info_terms` 문자열을 렌더링하고 각각 외부 링크 아이콘을 사용하는 상태를 캡처한다. `SettingsContent`의 실제 seam은 `onPrivacy: () -> Unit`, `onTerms: () -> Unit`이므로 테스트에서는 두 callback을 recorder 람다로 주입해 클릭 시 각각 한 번 호출되는지만 검증한다. URL 값 자체는 `SettingsUrlsTest`가 `SettingsUrls.PRIVACY`의 `/privacy`, `SettingsUrls.TERMS`의 `/terms` 경로로 고정한다. 앱 테스트에서 실제 `ACTION_VIEW`를 실행하지 않는다.

- [ ] **Step 4: Android 테스트와 정적 분석을 실행한다**

  ```bash
  cd android
  ./gradlew :app:testDebugUnitTest --tests '*SettingsUrlsTest' --tests '*SettingsScreenScreenshotTest'
  ./gradlew :app:check
  ```

  Expected: URL 계약과 설정 UI 테스트 PASS, `checkNoRawHexColors`, detekt, ktlint를 포함한 `:app:check` PASS.

- [ ] **Step 5: Commit**

  ```bash
  git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsUrls.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsUrlsTest.kt android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/settings/SettingsScreenScreenshotTest.kt
  git commit -m "test: verify settings policy links"
  ```

## Self-Review

- Spec coverage: §9의 세 Hosting 경로, 설정 정보의 두 링크, 계정 삭제 웹 경로, 베타 초안/법무 확정 전 게이트, Firebase Hosting 배포 명령, Android 링크 회귀를 Tasks 1–3에 매핑했다.
- Placeholder scan: 코드·명령·경로는 구체적으로 적었고 `TODO`/`TBD`를 사용하지 않았다. 법무/운영 담당자 제공값은 구현 전에 반드시 확정해야 하는 외부 입력이며, 확정되지 않은 개인정보를 임의로 만들지 않도록 Global Constraints와 Task 1에 명시했다.
- Type/URL consistency: `SettingsUrls.PRIVACY`→`/privacy`, `SettingsUrls.TERMS`→`/terms`, Hosting `public`→`web`, 세 디렉터리 `index.html` 경로가 일치한다.

## Open Input Required Before Public Release

구현은 현재 저장소 사실을 기준으로 진행할 수 있지만, 공개 승격 전에는 다음 값을 운영/법무 담당자가 확정해야 한다: 사업자/운영 주체의 법적 명칭과 주소, 문의 이메일 또는 웹 폼, 적용 국가의 준거법/관할, 음성 녹음·AI 입력의 실제 보관/제공 범위, Firebase Analytics/Google 서비스의 실제 수집·위탁 고지, 계정 삭제 요청의 본인 확인 및 처리 SLA. 이 값이 없으면 페이지를 “베타 초안”으로만 게시하고 Play 공개 출시에는 사용하지 않는다.
