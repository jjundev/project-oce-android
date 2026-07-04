package com.jjundev.oneclickeng.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 중도 종료 복구용 게스트→Google 이관 마커(M3-03, FR-3b). `signInWithCredential` 로 target 에 로그인하면
 * 익명 세션은 사라지므로, **signIn 전에** 게스트 UID·ID 토큰을 여기 영속해 앱 재시작 시 이관을 재시도한다
 * (firestore-schema.md:159, sign-in-then-migrate 1단계).
 *
 * **2-phase persist:** [put] 는 signIn 전 `targetUid=null` 로 저장하고, signIn 성공 후 [setTargetUid] 로
 * target UID 를 채운다. 복구 조건은 `targetUid != null && currentUid == targetUid` 라, (a)↔(b) 사이 종료로
 * `targetUid` 가 null 인 마커는 절대 매칭되지 않아 오계정 병합을 원천 차단한다.
 *
 * **보안:** [guestToken] 은 라이브 Firebase ID 토큰(≈1h 수명 bearer 자격증명)이다 — v1 은 평문 DataStore 에
 * 두되 성공 즉시 [clear] 한다(best-effort 정책). EncryptedSharedPreferences/Keystore 하드닝은 v1.1 후보다.
 */
data class PendingMerge(
    val guestUid: String,
    val guestToken: String,
    /** signIn 후 채워지는 대상 Google 계정 UID. signIn 전에는 null(=복구 미매칭). */
    val targetUid: String?,
)

interface PendingMergeStore {
    /** 현재 마커 스냅샷, 없으면 null. */
    suspend fun get(): PendingMerge?

    /** signIn 전 1단계 — guestUid·guestToken 저장(targetUid=null). */
    suspend fun put(
        guestUid: String,
        guestToken: String,
    )

    /** signIn 성공 후 2단계 — 대상 UID 채움. 마커가 없으면 no-op. */
    suspend fun setTargetUid(targetUid: String)

    /** 이관 성공(또는 무효 마커 정리) 시 삭제. */
    suspend fun clear()
}

/** DataStore Preferences 구현. 누락 키 → null 스냅샷. */
@Singleton
class DataStorePendingMergeStore
    @Inject
    constructor(
        @GoogleLinkPrefs private val dataStore: DataStore<Preferences>,
    ) : PendingMergeStore {
        @Suppress("ReturnCount")
        override suspend fun get(): PendingMerge? {
            val prefs = dataStore.data.first()
            val guestUid = prefs[KEY_GUEST_UID] ?: return null
            val guestToken = prefs[KEY_GUEST_TOKEN] ?: return null
            return PendingMerge(
                guestUid = guestUid,
                guestToken = guestToken,
                targetUid = prefs[KEY_TARGET_UID],
            )
        }

        override suspend fun put(
            guestUid: String,
            guestToken: String,
        ) {
            dataStore.edit { prefs ->
                prefs[KEY_GUEST_UID] = guestUid
                prefs[KEY_GUEST_TOKEN] = guestToken
                prefs.remove(KEY_TARGET_UID)
            }
        }

        override suspend fun setTargetUid(targetUid: String) {
            dataStore.edit { prefs ->
                // 마커가 없으면(guestUid 부재) 채우지 않는다 — put 없이 온 setTargetUid 는 무의미.
                if (prefs[KEY_GUEST_UID] != null) {
                    prefs[KEY_TARGET_UID] = targetUid
                }
            }
        }

        override suspend fun clear() {
            dataStore.edit { prefs ->
                prefs.remove(KEY_GUEST_UID)
                prefs.remove(KEY_GUEST_TOKEN)
                prefs.remove(KEY_TARGET_UID)
            }
        }

        private companion object {
            val KEY_GUEST_UID = stringPreferencesKey("pending_merge_guest_uid")
            val KEY_GUEST_TOKEN = stringPreferencesKey("pending_merge_guest_token")
            val KEY_TARGET_UID = stringPreferencesKey("pending_merge_target_uid")
        }
    }
