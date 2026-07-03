package com.jjundev.oneclickeng

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp

/**
 * 앱 Application. Hilt DI 그래프의 루트.
 *
 * FirebaseApp 은 google-services 플러그인이 심는 FirebaseInitProvider(ContentProvider)가
 * onCreate 이전에 자동 초기화한다. 여기서는 초기화 성공을 로그로만 확인한다(수용기준 #1).
 */
@HiltAndroidApp
class OceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Firebase initialized: ${FirebaseApp.getInstance().name}")
    }

    private companion object {
        const val TAG = "OceApp"
    }
}
