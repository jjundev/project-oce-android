# 기록 탭 업데이트: 세그먼트 테두리 정합 + 저장 카드 노출 수정 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 기록 탭의 "표현/단어/문장" 세그먼트를 프로토타입처럼 테두리 없는 흰 알약으로 바꾸고, 세션 요약에서 저장한 카드가 실기기 기록 탭에 실제로 보이지 않는 런타임 원인을 진단·수정한다.

**Architecture:** 두 갈래다. (A) 공유 프리미티브 `OneClickSegmentedControl` 의 선택 세그먼트에서 1dp hairline 보더를 제거해 프로토타입의 "흰 채움 + 굵은 글자" 알약과 정합한다(그림자는 추가하지 않아 앱 elevation 규칙 유지). (B) 저장→읽기 코드 경로는 이미 정상이나 실패가 전부 `Log.d` 로 삼켜져 실기기에서 원인이 보이지 않는다 — 실패를 진단 가능하게 드러내고(계측), 실기기 로그로 근본 원인을 특정한 뒤, 가장 유력한 원인(Firestore 복합 인덱스/보안 규칙 미배포)과 내구성 결함(uid 미확정 시 1회성 읽기)을 각각 고친다.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Hilt, Firebase Firestore + Anonymous Auth, JUnit4 + Robolectric + Roborazzi(스크린샷), Firebase CLI(배포).

## Global Constraints

- **gradle 검증은 반드시 `scripts/verify-android.sh` 로 실행한다.** 워크트리에서 직접 `./gradlew` 를 돌리면 공유 `~/.gradle` 캐시 오염·`google-services.json` 부재로 재발 함정이 있다(CLAUDE.md, `docs/agents/android-verification.md`). 단일 테스트: `scripts/verify-android.sh :app:testDebugUnitTest --tests '<패턴>'`. 인자 없이 실행 시 기본 세트(`:app:detekt :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest :app:testReleaseUnitTest`).
- **프로토타입 = 실현 정본(realization-SoT)** (ADR-0006, `docs/adr/0006-prototype-as-realization-sot.md`). 프로토타입의 `SegmentedControl` 은 앱 전역이 공유하는 **단일 컴포넌트**다 — Compose 의 `OneClickSegmentedControl` 도 마찬가지로 단일 프리미티브이며, 스타일 변경은 이를 쓰는 **모든** 화면(기록/설정/홈/주제선택)에 동시 적용된다. 이는 의도된 정합이다(원오프 포크 금지).
- **Elevation 규칙: "그림자 금지, 깊이 = surface + hairline"** (`ui/theme/OceElevation.kt`). Task A 는 보더를 제거하되 그림자를 **추가하지 않는다** — 선택 어포던스는 `surface`(흰색) 채움 + `tabActive`(굵은) 글자만으로 낸다. 대비는 토큰이 보장한다: light `background=#F3F4F6` vs `surface=#FFFFFF`, dark `background=#0E0F12` vs `surface=#1A1B20`.
- **한국어 UI 카피는 절대 바꾸지 않는다** — 라벨 "표현"/"단어"/"문장", 카운트 "N개 · 최신순", 빈 상태 문구 등은 그대로 둔다.
- **Firestore 계약 불변:** 저장 카드는 `users/{uid}/saved_cards/{cardId}`, 소프트삭제는 `deletedAt` 톰스톤, 읽기 쿼리는 복합 인덱스 `(cardType ASC, deletedAt ASC, createdAt DESC)`(`firestore.indexes.json`). 스키마 필드명·`cardType` wire 값(`WORD`/`EXPRESSION`/`SENTENCE`)은 이미 저장(`SavedCard.contentMap()`)↔읽기(`toEntry()`)가 일치하므로 **건드리지 않는다**.

---

## File Structure

**Part A — 세그먼트 테두리 제거 (공유 컴포넌트)**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickSegmentedControl.kt` — 선택 세그먼트 보더 제거, KDoc·import·상수 정리.
- Verify(생성/육안): `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreenScreenshotTest.kt`(기존, 수정 없음 — 재생성 대조).

**Part B — 저장 카드 미노출 진단·수정**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardQuerySource.kt` — 삼킨 예외를 `Log.w` 로 승격(진단 가시성) + uid 미확정 시 익명 로그인 대기(내구성 수정).
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/saved/SavedCardRepository.kt` — 저장/톰스톤 실패 로그를 `Log.w` 로 승격(진단 가시성).
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardReadAuthTest.kt` — uid 해석 헬퍼 단위 테스트.
- Operational(코드 아님): `firestore.indexes.json` + `firestore.rules` 를 라이브 프로젝트에 배포(가장 유력한 근본 원인). `firebase.json` 이 두 파일을 이미 가리킨다.

---

## Part A — 세그먼트 테두리 제거

### Task A1: 선택 세그먼트의 hairline 보더 제거

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickSegmentedControl.kt`
- Verify: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/RecordsScreenScreenshotTest.kt` (기존, 수정 없음)

**Interfaces:**
- Consumes: `OceTheme.shapes.pill`, `OceTheme.colors.textTertiary`, `MaterialTheme.colorScheme.{surface,onSurface,background}`.
- Produces: `OneClickSegmentedControl` 의 시그니처는 **불변**(`options/selected/onSelect/label/modifier`). 렌더 결과만 변경 — 선택 세그먼트가 보더 없는 흰 알약이 된다. 기록/설정/홈/주제선택 모든 호출부가 자동으로 새 스타일을 받는다.

- [ ] **Step 1: 현재 선택 세그먼트가 보더를 가짐을 확인(육안 red)**

이 컴포넌트에는 보더 유무를 검증하는 순수 단위 테스트가 없다(보더는 시맨틱 트리에 없음). 대신 기존 스크린샷 테스트로 현재 상태를 캡처해 육안 기준선을 잡는다.

Run: `cd android && ../scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.records.RecordsScreenScreenshotTest'`
그다음 생성물 확인: `android/app/build/outputs/roborazzi/records_light_expression.png` 를 열어 "표현" 세그먼트에 얇은 회색 알약 **테두리**가 있음을 확인한다(이게 제거 대상).
Expected: 테스트 PASS, PNG 의 선택 세그먼트에 hairline 보더 존재.

- [ ] **Step 2: 보더 제거 — 컴포넌트 수정**

`OneClickSegmentedControl.kt` 에서 (a) 미사용될 import·상수 제거, (b) 선택 세그먼트의 `.then(border)` 블록 제거.

먼저 파일 상단 import(4행)와 상수(23~24행)를 제거한다.

```kotlin
// 제거: import androidx.compose.foundation.border
// 제거: private val SegmentedSelectedBorder = 1.dp  (그 위 KDoc 주석 한 줄 포함)
```

KDoc(31~34행 부근)의 "번들의 미세 그림자는 … hairline 보더로 대체한다" 문장을 실제 동작에 맞게 교체한다. 기존:

```kotlin
 * 실현 정본(realization-SoT) 번들 `SegmentedControl.jsx`: 트랙 `surface.background`(= colorScheme.background)
 * + `radius.pill`, 선택 세그먼트는 `surface.card`(흰색) 채움 + `text.primary` 글자 + hairline 보더 알약이다.
 * 번들의 미세 그림자는 앱 elevation 정본(OceElevation "그림자 금지, 깊이=surface+hairline")에 맞춰 hairline
 * 보더로 대체한다. 비선택 글자는 `text.tertiary`. 접근성은 [selectableGroup] + 세그먼트별 [Role.RadioButton]
 * [selectable] 로 라디오 그룹으로 노출한다(탭 라벨은 [label] 이 문자열화).
```

교체 후:

```kotlin
 * 실현 정본(realization-SoT) 번들 `SegmentedControl.jsx`: 트랙 `surface.background`(= colorScheme.background)
 * + `radius.pill`, 선택 세그먼트는 `surface.card`(흰색) 채움 + `text.primary` 글자 알약이다(테두리 없음).
 * 프로토타입은 미세 그림자로 선택을 표시하지만, 앱 elevation 정본(OceElevation "그림자 금지, 깊이=surface+
 * hairline")에 맞춰 그림자·보더 없이 `background`↔`surface` 토큰 대비 + `tabActive` 굵은 글자만으로 선택을
 * 낸다. 비선택 글자는 `text.tertiary`. 접근성은 [selectableGroup] + 세그먼트별 [Role.RadioButton]
 * [selectable] 로 라디오 그룹으로 노출한다(탭 라벨은 [label] 이 문자열화).
```

다음으로 선택 세그먼트 modifier 에서 보더 블록을 제거한다. 기존:

```kotlin
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(OceTheme.shapes.pill)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = SegmentedSelectedBorder,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = OceTheme.shapes.pill,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(option) },
                        )
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        )
                        .padding(vertical = OceTheme.spacing.sm),
```

교체 후(`.then(border)` 블록만 삭제, 나머지 동일):

```kotlin
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(OceTheme.shapes.pill)
                        .selectable(
                            selected = isSelected,
                            role = Role.RadioButton,
                            onClick = { onSelect(option) },
                        )
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        )
                        .padding(vertical = OceTheme.spacing.sm),
```

- [ ] **Step 3: 컴파일·정적분석 통과 확인**

Run: `cd android && ../scripts/verify-android.sh :app:detekt :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.records.RecordsScreenScreenshotTest'`
Expected: PASS. (detekt 는 미사용 import `border`·미사용 상수가 남아 있으면 실패하므로, Step 2 에서 둘 다 제거됐는지 자동 검증된다.)

- [ ] **Step 4: 재생성 스크린샷으로 보더 사라짐 육안 확인**

`android/app/build/outputs/roborazzi/records_light_expression.png` 와 `records_dark_expression.png` 를 다시 연다.
Expected: 선택 "표현" 세그먼트가 **테두리 없는** 흰(라이트)·`#1A1B20`(다크) 알약이고, 글자는 굵다. 트랙 대비로 알약이 식별 가능하다. `prototype/screenshot/records.png` 의 세그먼트와 형태가 일치한다.

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/ui/component/OneClickSegmentedControl.kt
git commit -m "fix(segmented): drop selected-segment hairline border for prototype parity"
```

---

## Part B — 세션 요약 저장 카드가 기록 탭에 안 보이는 문제 진단·수정

> **배경(실행자 필독):** 저장(`SummaryCoordinator.toggleSaveWord/Expression` → `SavedCardRepository.save` → Firestore)과 읽기(`RecordsViewModel` → `SavedCardQuerySource.page`)는 코드상 이미 완전하고 필드·인덱스·`cardType` 이 정합한다(커밋 `4fd642f` M2-04, `5d70ead` M2-05). 그런데 실기기에서 저장 카드가 안 보인다. 원인은 코드 로직이 아니라 **런타임/배포/타이밍**일 가능성이 크고, 결정적으로 **모든 실패가 `Log.d`(debug) 로 삼켜져** 원인이 로그에 드러나지 않는다. 그래서 B1(가시화) → B2(실기기 진단) → B3(가장 유력 원인 = 인덱스/규칙 배포) → B4(uid 타이밍 내구성 수정) 순으로 간다. B1·B4 는 원인과 무관하게 항상 적용하는 하드닝이고, B3 는 B2 의 진단 결과에 따라 적용한다.

### Task B1: 삼킨 실패를 진단 가능하게 승격

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardQuerySource.kt:91-94`
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/saved/SavedCardRepository.kt:83-85, 103-105`

**Interfaces:**
- Consumes: `android.util.Log`(이미 import), 각 파일의 `TAG` 상수(이미 존재).
- Produces: 강등 계약(실패 시 빈 페이지 / no-op)은 **불변**. 오직 로그 레벨을 `Log.d`→`Log.w` 로 올리고 예외 클래스명을 문구에 포함해, `adb logcat` 기본 필터에서 원인이 보이게 한다. 다운스트림 동작(빈 페이지 강등)은 그대로다.

- [ ] **Step 1: 강등 계약이 로그 변경에 영향받지 않음을 확인(기존 테스트 회귀 방지)**

읽기 소스의 "예외 → 빈 종단 페이지" 계약은 `SavedCardQuerySource` 를 fake 로 대체하는 VM 테스트가 간접 커버한다. 로그 레벨만 바꾸므로 신규 테스트는 불필요하고, 기존 스위트가 초록임을 먼저 확인한다.

Run: `cd android && ../scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.records.*' --tests 'com.jjundev.oneclickeng.feature.session.saved.*'`
Expected: PASS.

- [ ] **Step 2: 읽기 소스의 삼킨 로그 승격**

`SavedCardQuerySource.kt` 의 catch(91~94행):

```kotlin
            } catch (e: Exception) {
                Log.d(TAG, "saved_card page query failed (offline/permission/index): ${e.message}")
                SavedCardPage(emptyList(), null, endReached = true)
            }
```

교체:

```kotlin
            } catch (e: Exception) {
                // 표시 전용 강등은 유지하되, 실기기 진단을 위해 예외 종류를 warn 으로 드러낸다
                // (인덱스 미비=FAILED_PRECONDITION, 규칙/권한=PERMISSION_DENIED 를 logcat 기본 필터에서 식별).
                Log.w(TAG, "saved_card page query failed [${e::class.simpleName}]: ${e.message}")
                SavedCardPage(emptyList(), null, endReached = true)
            }
```

- [ ] **Step 3: 쓰기 리포지토리의 삼킨 로그 승격**

`SavedCardRepository.kt` 의 `save` catch(83~85행):

```kotlin
                } catch (e: Exception) {
                    Log.d(TAG, "saved_card save skipped (offline/permission): ${e.message}")
                }
```

교체:

```kotlin
                } catch (e: Exception) {
                    Log.w(TAG, "saved_card save skipped [${e::class.simpleName}]: ${e.message}")
                }
```

`setDeleted` catch(103~105행):

```kotlin
                } catch (e: Exception) {
                    Log.d(TAG, "saved_card setDeleted skipped (offline/permission): ${e.message}")
                }
```

교체:

```kotlin
                } catch (e: Exception) {
                    Log.w(TAG, "saved_card setDeleted skipped [${e::class.simpleName}]: ${e.message}")
                }
```

- [ ] **Step 4: 컴파일·기존 테스트 통과 확인**

Run: `cd android && ../scripts/verify-android.sh :app:detekt :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.records.*' --tests 'com.jjundev.oneclickeng.feature.session.saved.*'`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardQuerySource.kt \
        android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/session/saved/SavedCardRepository.kt
git commit -m "chore(saved): surface swallowed saved-card failures at warn for on-device diagnosis"
```

---

### Task B2: 실기기 재현·근본 원인 특정 (진단 런북)

> 이 태스크는 코드 변경이 아니라 **진단**이다. 산출물은 "근본 원인 판정"이며, 그 결과가 B3 적용 여부를 결정한다. B1 이 선행돼야 로그가 보인다.

**Files:** 없음(런북). 필요 시 `firestore.indexes.json`, `firestore.rules` 를 **읽어** 라이브 배포본과 대조.

- [ ] **Step 1: B1 포함 debug 빌드를 실기기에 설치**

```bash
cd android && ../scripts/verify-android.sh :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Expected: 설치 성공. (`scripts/verify-android.sh` 가 `google-services.json` 을 메인 워크트리에서 복사한다.)

- [ ] **Step 2: 로그 스트림을 열고 저장→읽기를 재현**

터미널에서:
```bash
adb logcat -c && adb logcat | grep -E "SavedCardQuerySource|SavedCardRepository"
```
앱에서: 세션을 하나 완료 → 요약 화면에서 단어/표현 카드의 저장(북마크) 토글 → 기록 탭으로 이동 → 표현/단어 세그먼트 전환.

- [ ] **Step 3: 로그로 근본 원인 판정(결정표)**

관측된 로그에 따라 원인을 특정하고 후속 태스크를 고른다:

| logcat 관측 | 근본 원인 | 조치 |
|---|---|---|
| `saved_card page query failed [FirebaseFirestoreException]: ... requires an index ...` (FAILED_PRECONDITION) | 복합 인덱스 미배포(또는 빌드 중) | **Task B3**(인덱스 배포) |
| `... [FirebaseFirestoreException]: PERMISSION_DENIED` | 보안 규칙 미배포/스테일(saved_cards read 규칙 부재) | **Task B3**(규칙 배포) + uid 오너십 확인 |
| `saved_card save skipped [...]` 가 저장 토글 때 찍힘 | 저장 자체가 실패(권한/오프라인/uid null) | 메시지의 예외로 세분: PERMISSION_DENIED→B3, uid null→**Task B4** 확장(쓰기 경로) |
| **로그 없음** + 기록 탭 빈 상태 | 쿼리가 예외 없이 0건(=uid null 로 조기 반환, 또는 실제 문서 없음, 또는 `createdAt` 미해결로 orderBy 제외) | **Task B4**(uid 대기) 적용 후 재현. 여전히 0건이면 Firebase 콘솔에서 `users/{uid}/saved_cards` 문서 존재·`deletedAt==null`·`createdAt` 값 유무를 직접 확인 |

> 참고: uid 는 `adb logcat | grep FirebaseAuth` 또는 앱 로그로 확인. 저장 시 uid 와 읽기 시 uid 가 같아야 한다(익명 세션은 디스크 영속 — 정상적으로 동일).

- [ ] **Step 4: 판정 기록(커밋 메시지/PR 본문용)**

특정된 원인 한 줄을 적어 둔다(예: "FAILED_PRECONDITION — saved_cards 복합 인덱스가 라이브 프로젝트에 미배포"). 이 문장이 B3 커밋/PR 설명의 근거가 된다. 코드 변경이 없으므로 커밋은 없다.

---

### Task B3: Firestore 복합 인덱스 + 보안 규칙 배포 (가장 유력한 수정)

> **적용 조건:** B2 가 FAILED_PRECONDITION(인덱스) 또는 PERMISSION_DENIED(규칙)를 가리킬 때. 저장 카드 미노출의 전형적 원인은 `firestore.indexes.json`/`firestore.rules` 가 레포에는 있으나 **라이브 프로젝트에 배포되지 않은** 상태다. 읽기 쿼리(`where cardType== AND deletedAt==null ORDER BY createdAt DESC`)는 복합 인덱스가 없으면 FAILED_PRECONDITION 을 던지고, B1 이전에는 그게 `Log.d` 로 삼켜져 빈 화면으로만 보였다.

**Files:**
- Deploy(수정 아님): `firestore.indexes.json`(복합 인덱스 이미 선언됨), `firestore.rules`(saved_cards read/create/update 규칙 이미 선언됨). `firebase.json` 이 둘을 가리킨다.

**Interfaces:**
- Consumes: Firebase CLI, 라이브 프로젝트 접근 권한(로그인).
- Produces: 라이브 Firestore 에 인덱스 `(cardType ASC, deletedAt ASC, createdAt DESC)` 활성화 + 최신 규칙 반영. 읽기 쿼리가 더는 FAILED_PRECONDITION 을 던지지 않는다.

> **주의:** 이 태스크는 **레포 밖 라이브 리소스를 변경**하는 외부 작업이다. 실행 전 사용자에게 어떤 프로젝트(alias/projectId)에 배포하는지 확인받고 진행한다. 워크트리/CI 에 Firebase 인증이 없으면 이 단계는 사용자가 직접 수행해야 한다.

- [ ] **Step 1: 배포 대상 프로젝트 확인**

Run: `firebase projects:list` 및 `firebase use`
Expected: 활성 프로젝트가 앱의 `google-services.json` 의 `project_id` 와 일치. 불일치 시 `firebase use <alias>` 로 맞춘다.

- [ ] **Step 2: 배포 전 dry 확인 — 선언된 인덱스/규칙 검토**

레포의 `firestore.indexes.json` 에 saved_cards 복합 인덱스가, `firestore.rules` 에 `match /saved_cards/{cardId}` read 규칙이 있는지 눈으로 확인(둘 다 이미 존재해야 한다). 없으면 이 태스크가 아니라 상위 스키마 문제이므로 중단하고 사용자에게 알린다.

- [ ] **Step 3: 인덱스 + 규칙 배포**

```bash
firebase deploy --only firestore:indexes,firestore:rules
```
Expected: 배포 성공 로그. 인덱스는 빌드에 수 분 걸릴 수 있다.

- [ ] **Step 4: 인덱스 빌드 완료 확인**

Firebase 콘솔 → Firestore → 색인(Indexes) 에서 `saved_cards` 복합 인덱스 상태가 **사용 설정됨(Enabled)** 인지 확인(빌드 중이면 대기).
Expected: 상태 = Enabled.

- [ ] **Step 5: 실기기 재검증**

B2 Step 2 의 재현을 다시 수행. `adb logcat | grep SavedCardQuerySource` 에 더는 실패 로그가 없고, 기록 탭에 저장한 카드가 최신순으로 표시된다.
Expected: 저장한 표현/단어 카드가 해당 세그먼트에 노출. 로그 무경고.

- [ ] **Step 6: 판정 기록(선택 커밋)**

배포는 레포 변경이 아니므로 코드 커밋은 없다. PR 본문/이슈에 "라이브 프로젝트에 saved_cards 인덱스/규칙 배포로 기록 탭 미노출 해소"를 남긴다. (레포의 `firestore.indexes.json`/`firestore.rules` 를 손댔다면 그 변경만 커밋.)

---

### Task B4: 기록 탭 첫 읽기를 익명 로그인 완료까지 대기 (내구성 수정)

> **적용 조건:** 원인과 무관하게 **항상 적용**하는 하드닝. `AuthRepository.currentUid` 는 "**최초 사인인 완료 전 null**"이다(첫 실행 시 익명 로그인은 비동기; 재실행 시 디스크에서 동기 복원). `SavedCardQuerySource.page` 는 `currentUid ?: return 빈페이지` 로 즉시 강등하고, `RecordsViewModel` 의 읽기는 `init{}` 1회성이라 재시도가 없다 — 첫 실행에서 사인인 전에 기록 탭에 들어가면 uid null 로 영영 빈 화면이 될 수 있다. 읽기 시 uid 가 없으면 `ensureSignedIn()`(멱등·single-flight)으로 **대기**한 뒤 쿼리하도록 고쳐 이 창을 닫는다. 오프라인 등으로 사인인이 실패하면 기존과 동일하게 빈 페이지로 강등한다.

**Files:**
- Modify: `android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardQuerySource.kt` — uid 해석 헬퍼 추가 + `page()` 의 uid 획득부 교체.
- Create: `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardReadAuthTest.kt` — 헬퍼 단위 테스트.

**Interfaces:**
- Consumes: `AuthRepository`(`currentUid: String?`, `suspend ensureSignedIn(): String`), 이미 `FirestoreSavedCardQuerySource` 에 주입됨.
- Produces: `internal suspend fun AuthRepository.uidForSavedCardRead(): String?` — `currentUid` 가 있으면 그대로, 없으면 `ensureSignedIn()` 을 시도해 uid 반환, 사인인 실패 시 null. `page()` 는 이 헬퍼로 uid 를 얻는다(반환 계약·시그니처 불변).

- [ ] **Step 1: 실패하는 테스트 작성**

Create `android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardReadAuthTest.kt`:

```kotlin
package com.jjundev.oneclickeng.feature.records

import com.jjundev.oneclickeng.core.auth.AuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * 기록 탭 읽기의 uid 해석 규율: currentUid 가 있으면 사인인을 트리거하지 않고, 없으면 ensureSignedIn 으로
 * 대기하며, 사인인이 실패(throw)하면 null 로 강등(표시 전용 — 화면에 실패를 노출하지 않음).
 */
class SavedCardReadAuthTest {
    private class FakeAuth(
        private val uid: String?,
        private val ensuredUid: String? = null,
        private val ensureThrows: Boolean = false,
    ) : AuthRepository {
        var ensureCalls = 0
        override val currentUid: String? = uid

        override suspend fun ensureSignedIn(): String {
            ensureCalls++
            if (ensureThrows) error("offline")
            return ensuredUid ?: error("no uid")
        }
    }

    @Test
    fun `returns currentUid without signing in when present`() = runTest {
        val auth = FakeAuth(uid = "uid-1")
        assertEquals("uid-1", auth.uidForSavedCardRead())
        assertEquals(0, auth.ensureCalls)
    }

    @Test
    fun `awaits anonymous sign-in when uid is null`() = runTest {
        val auth = FakeAuth(uid = null, ensuredUid = "uid-ensured")
        assertEquals("uid-ensured", auth.uidForSavedCardRead())
        assertEquals(1, auth.ensureCalls)
    }

    @Test
    fun `degrades to null when sign-in fails`() = runTest {
        val auth = FakeAuth(uid = null, ensureThrows = true)
        assertNull(auth.uidForSavedCardRead())
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `cd android && ../scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.records.SavedCardReadAuthTest'`
Expected: 컴파일 실패 — `uidForSavedCardRead` 미정의(unresolved reference).

- [ ] **Step 3: 헬퍼 추가 + `page()` 배선**

`SavedCardQuerySource.kt` 에 최상위 `internal suspend` 확장 헬퍼를 추가한다(interface `SavedCardQuerySource` 선언 위, import 아래).

```kotlin
/**
 * 기록 탭 읽기용 uid 해석. currentUid 가 이미 있으면(재실행·정상 경로) 그대로 쓰고, 없으면(첫 실행에서
 * 익명 사인인이 아직 진행 중) [AuthRepository.ensureSignedIn] 으로 대기한다(멱등·single-flight). 사인인이
 * 실패하면(오프라인 첫 실행 등) null 로 강등해 호출부가 빈 페이지로 degrade 하게 한다 — 표시 전용.
 */
internal suspend fun AuthRepository.uidForSavedCardRead(): String? =
    currentUid ?: runCatching { ensureSignedIn() }.getOrNull()
```

필요 import 추가: `import com.jjundev.oneclickeng.core.auth.AuthRepository` 는 이미 있으니 추가 불필요.

그다음 `page()` 의 uid 획득부(72행)를 교체한다. 기존:

```kotlin
            val uid = authRepository.currentUid ?: return SavedCardPage(emptyList(), null, endReached = true)
```

교체:

```kotlin
            val uid = authRepository.uidForSavedCardRead()
                ?: return SavedCardPage(emptyList(), null, endReached = true)
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `cd android && ../scripts/verify-android.sh :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.records.SavedCardReadAuthTest'`
Expected: 3개 테스트 PASS.

- [ ] **Step 5: 회귀·정적분석 통과 확인**

Run: `cd android && ../scripts/verify-android.sh :app:detekt :app:testDebugUnitTest --tests 'com.jjundev.oneclickeng.feature.records.*'`
Expected: PASS(기존 `RecordsViewModelTest` 는 fake 쿼리소스를 쓰므로 이 변경에 영향 없음).

- [ ] **Step 6: 커밋**

```bash
git add android/app/src/main/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardQuerySource.kt \
        android/app/src/test/kotlin/com/jjundev/oneclickeng/feature/records/SavedCardReadAuthTest.kt
git commit -m "fix(records): await anonymous sign-in before first saved-card read"
```

---

## 최종 검증 (전체)

- [ ] **전체 검증 세트 실행**

Run: `cd android && ../scripts/verify-android.sh`
Expected: detekt + debug/release 단위 테스트 전부 PASS.

- [ ] **실기기 종단 확인**

세션 완료 → 요약에서 단어·표현 카드 저장 → 기록 탭에서 해당 세그먼트에 카드가 최신순으로 보임 + 세그먼트가 테두리 없는 알약. 스와이프 삭제/undo 도 정상.

---

## 실행자를 위한 메모(원인 불확실성)

- Part A 는 결정적이다(순수 UI 변경).
- Part B 의 **가장 유력한 근본 원인은 라이브 프로젝트의 인덱스/규칙 미배포(B3)** 이며, B1(가시화)·B4(uid 대기)는 원인과 무관하게 옳은 하드닝이라 항상 적용한다. B2 진단이 "로그 없음 + 실제 문서 없음"을 가리키면 저장 경로 자체(요약 저장 UI 도달성, 저장 시 uid)를 추가 조사해야 하며, 그 경우 B4 의 대기 로직을 쓰기 경로(`SavedCardRepository.save`/`setDeleted` 의 `currentUid ?: return`)에도 동일하게 확장하는 후속 태스크가 필요할 수 있다 — 진단 결과가 나오기 전에는 쓰기 경로를 건드리지 않는다(YAGNI).
