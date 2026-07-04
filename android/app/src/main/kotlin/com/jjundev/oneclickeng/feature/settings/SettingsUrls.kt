package com.jjundev.oneclickeng.feature.settings

/**
 * 정책 페이지 URL(M3-09, settings-data-account.md §9). Firebase Hosting 정적 페이지 플레이스홀더 — 본문·실
 * 호스팅은 공개(프로덕션) 승격 전 법무 확정(§12 needs-you, 사용자 확정 #21). 계정삭제 웹 경로(/delete-account)는
 * Play 콘솔 data-safety 폼에 등록하며 인앱 링크는 두지 않는다.
 */
object SettingsUrls {
    const val PRIVACY = "https://oneclickeng.web.app/privacy"
    const val TERMS = "https://oneclickeng.web.app/terms"
}
