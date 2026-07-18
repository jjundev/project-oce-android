package com.jjundev.oneclickeng.core.update

import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AppUpdateChecker] 의 Play Core 구현(archive/android `MainActivity.checkForAppUpdate` 이식). Play
 * 스토어로 설치된 실기기에서만 `UPDATE_AVAILABLE` 신호를 준다 — 에뮬레이터/사이드로드/조회 실패는
 * [isImmediateUpdateAvailable] 이 false 라 fail-open(강제 업데이트 없이 부팅 진행).
 *
 * `AppUpdateManager.getAppUpdateInfo()` 는 split(2.x) 라이브러리에서 `com.google.android.gms.tasks.Task`
 * 를 반환한다(레거시 모놀리식 `com.google.android.play:core`의 자체 Task 타입이 아니다) — 이미 이 모듈에
 * 있는 `kotlinx-coroutines-play-services` 의 `.await()`(AppViewModel 의 게스트 익명 로그인 브릿지와
 * 동일 의존성)로 바로 suspend 화한다. 별도 coroutine 브릿지 코드가 필요 없다.
 */
@Singleton
class PlayAppUpdateChecker
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : AppUpdateChecker {
        private val manager: AppUpdateManager = AppUpdateManagerFactory.create(context)

        @Suppress("TooGenericExceptionCaught")
        override suspend fun isImmediateUpdateRequired(): Boolean =
            try {
                val info = manager.appUpdateInfo.await()
                isImmediateUpdateAvailable(
                    availability = info.updateAvailability(),
                    isImmediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to query app update info — fail-open", e)
                false
            }

        @Suppress("TooGenericExceptionCaught")
        override suspend fun isUpdateInProgress(): Boolean =
            try {
                isImmediateUpdateInProgress(manager.appUpdateInfo.await().updateAvailability())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check update in progress — fail-open", e)
                false
            }

        override suspend fun launchImmediateUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
            val info = manager.appUpdateInfo.await()
            manager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
            )
        }

        private companion object {
            const val TAG = "PlayAppUpdateChecker"
        }
    }

/**
 * IMMEDIATE 업데이트 필요 판정(archive `checkForAppUpdate` 조건 이식). 프레임워크 비의존 순수 함수라
 * Play Core 상수만으로 진리표를 고정한다: `UPDATE_AVAILABLE` && IMMEDIATE 허용 둘 다여야 true.
 */
internal fun isImmediateUpdateAvailable(
    availability: Int,
    isImmediateAllowed: Boolean,
): Boolean = availability == UpdateAvailability.UPDATE_AVAILABLE && isImmediateAllowed

/**
 * 직전 IMMEDIATE 업데이트가 아직 진행 중인지(onResume 재개 판정, archive `onResume` 이식). 이름을
 * `isImmediateUpdateInProgress`로 둔다 — [AppUpdateChecker.isUpdateInProgress] 멤버 함수와 이름이
 * 겹치면(오버로드로 컴파일은 되지만) 호출부에서 어느 쪽이 불리는지 헷갈린다.
 */
internal fun isImmediateUpdateInProgress(availability: Int): Boolean =
    availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
