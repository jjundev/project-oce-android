package com.jjundev.oneclickeng.core.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.jjundev.oneclickeng.R

/**
 * Credential Manager 로 Google ID 토큰을 받아오는 UI-side 어댑터(M3-03). **Activity Context 를 요구하는 유일한
 * 조각** — 그래서 ViewModel 이 아니라 여기(호출 지점=컴포저블)에서 Context 를 받고, 결과 ID 토큰 **문자열만**
 * VM 으로 넘긴다(결정 B3: Activity 는 VM 에 절대 들어가지 않는다). DI 의존이 없어 컴포저블에서 직접 호출한다.
 *
 * `serverClientId` 는 google-services 플러그인이 `google-services.json`(Web OAuth 클라이언트)에서 생성하는
 * `R.string.default_web_client_id` 를 쓴다 — 수동 상수/하드코딩 없음.
 *
 * 취소: 사용자가 Google 피커를 닫으면 `GetCredentialCancellationException` 이 던져진다 — 호출자가 무음 처리한다
 * (결정 B4). 그 외 실패는 [GoogleCredentialException] 으로 감싸 던진다.
 */
object GoogleCredentialProvider {
    /**
     * Google 계정을 선택시켜 raw Google ID 토큰을 반환한다. 호출자는 이 값을 Firebase 자격증명으로 변환해
     * [GoogleAccountLinker.linkGuest] 에 넘긴다.
     *
     * @throws androidx.credentials.exceptions.GetCredentialCancellationException 사용자 취소(무음 처리)
     * @throws GoogleCredentialException 그 외 취득 실패
     */
    suspend fun getGoogleIdToken(context: Context): String {
        val request =
            GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setServerClientId(context.getString(R.string.default_web_client_id))
                        // 최초 연결 흐름이라 인가된 계정만 필터하지 않는다(신규 로그인 허용).
                        .setFilterByAuthorizedAccounts(false)
                        .build(),
                )
                .build()

        val response = CredentialManager.create(context).getCredential(context, request)
        val credential = response.credential
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw GoogleCredentialException("unexpected credential type: ${credential.type}")
        }
        return GoogleIdTokenCredential.createFrom(credential.data).idToken
    }
}

/** Google 자격증명 취득 실패(취소 제외) — 호출자가 실패 UI 로 매핑한다. */
class GoogleCredentialException(message: String) : Exception(message)
