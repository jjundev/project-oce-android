package com.jjundev.oneclickeng.feature.settings

/**
 * 정책 페이지 URL(M3-09, settings-data-account.md §9). Task 2에서 확인한 실제 Firebase Hosting 배포 호스트를
 * 사용한다(`oneclickeng.web.app`는 404이므로 사용하지 않음). 계정삭제 웹 경로(/delete-account)는 Play 콘솔
 * data-safety 폼에 등록하며 인앱 링크는 두지 않는다.
 */
object SettingsUrls {
    const val PRIVACY = "https://oce-v1.web.app/privacy"
    const val TERMS = "https://oce-v1.web.app/terms"
}
