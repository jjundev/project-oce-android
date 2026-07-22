package com.jjundev.oneclickeng.ui.root

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRootLoadingCopyTest {
    @Test
    fun `anonymous or unauthenticated user sees app preparation copy`() {
        assertEquals(
            BootLoadingCopy(
                text = "잠시만 기다려주세요...",
                contentDescription = "앱 준비 중",
            ),
            bootLoadingCopyFor(isAnonymous = true),
        )
    }

    @Test
    fun `linked account sees sign-in copy`() {
        assertEquals(
            BootLoadingCopy(
                text = "로그인 하는 중이에요...",
                contentDescription = "로그인 하는 중",
            ),
            bootLoadingCopyFor(isAnonymous = false),
        )
    }
}
