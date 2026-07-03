package com.jjundev.oneclickeng

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.google.firebase.FirebaseApp
import com.jjundev.oneclickeng.feature.reminder.ReminderOrchestrator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 앱 Application. Hilt DI 그래프의 루트이자 WorkManager 온디맨드 초기화 진입점([Configuration.Provider]).
 *
 * FirebaseApp 은 google-services 플러그인이 심는 FirebaseInitProvider(ContentProvider)가
 * onCreate 이전에 자동 초기화한다.
 *
 * WorkManager: 기본 초기화 provider 는 매니페스트에서 제거하고, 여기서 [HiltWorkerFactory] 를 실은
 * [Configuration] 을 제공한다(@HiltWorker 주입 활성화). 또한 앱 시작 시 리마인더가 켜져 있으면 고유작업을
 * 재예약(REPLACE)해 끊긴 자기재예약 체인을 복구한다(notification-reminder.md §4 헬스체크, 결정 #11).
 */
@HiltAndroidApp
class OceApp :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var reminderOrchestrator: ReminderOrchestrator

    @Inject lateinit var appScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() =
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Firebase initialized: ${FirebaseApp.getInstance().name}")
        appScope.launch {
            reminderOrchestrator.repairSchedule()
        }
    }

    private companion object {
        const val TAG = "OceApp"
    }
}
