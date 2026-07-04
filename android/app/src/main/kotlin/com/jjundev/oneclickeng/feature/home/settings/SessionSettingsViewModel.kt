package com.jjundev.oneclickeng.feature.home.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjundev.oneclickeng.core.auth.AuthRepository
import com.jjundev.oneclickeng.core.auth.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 접힌 세션 설정 화면의 기본 레벨 소스(M3-08, #6). `profile.level`(suspend Firestore 왕복)을 **설정 화면이
 * 직접** 해소한다 — 홈 CTA 가 레벨을 nav-arg 로 캡처하지 않으므로, readLevel 미해소 중 빠른 CTA 탭이 저장
 * 레벨 대신 `easy` 를 흘리는 누출을 원천 차단한다(H1 CTA 항상 활성 유지 + 레벨 정확성 동시 충족).
 *
 * [defaultLevel] 3-상태: null=미로딩(화면은 로딩 표시, 시작 비활성) → 해소값(부재/오류 시 `easy` 폴백).
 */
@HiltViewModel
class SessionSettingsViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
        private val profileRepository: ProfileRepository,
    ) : ViewModel() {
        private val _defaultLevel = MutableStateFlow<String?>(null)
        val defaultLevel: StateFlow<String?> = _defaultLevel.asStateFlow()

        init {
            viewModelScope.launch {
                val level =
                    runCatching { authRepository.currentUid?.let { profileRepository.readLevel(it) } }
                        .onFailure { Log.d(TAG, "readLevel failed — defaulting easy: ${it.message}") }
                        .getOrNull()
                _defaultLevel.value = level ?: DEFAULT_LEVEL
            }
        }

        private companion object {
            const val TAG = "SessionSettingsVM"
            const val DEFAULT_LEVEL = "easy"
        }
    }
