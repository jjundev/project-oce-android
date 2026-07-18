package com.jjundev.oneclickeng.core.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

/**
 * Play 스토어 강제 업데이트 게이트 seam(archive `MainActivity.checkForAppUpdate` 재구현). Play Core
 * 타입을 이 인터페이스 뒤에 완전히 숨겨 [com.jjundev.oneclickeng.ui.root.UpdateGateViewModel] 을 Play
 * Core 없이 단위테스트할 수 있게 한다(`AuthRepository`/`ProfileRepository` 와 동일 관례).
 */
interface AppUpdateChecker {
    /** IMMEDIATE 타입 업데이트가 스토어에 있으면 true. 조회 실패(Play 서비스 미탑재 등)는 false(fail-open). */
    suspend fun isImmediateUpdateRequired(): Boolean

    /** 직전에 시작된 IMMEDIATE 업데이트가 아직 진행 중이면 true(onResume 재개 판정용). */
    suspend fun isUpdateInProgress(): Boolean

    /** IMMEDIATE 업데이트 플로우를 시작한다. [launcher] 결과 처리는 호출부 책임이다. */
    suspend fun launchImmediateUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>)
}
