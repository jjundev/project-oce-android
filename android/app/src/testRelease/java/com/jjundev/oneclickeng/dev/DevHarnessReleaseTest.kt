package com.jjundev.oneclickeng.dev

import org.junit.Assert.assertNull
import org.junit.Test

/**
 * release 변이에서 하니스 진입점이 부재함을 반증가능하게 확인한다(M1-09 수용기준 #1 · 검증: 릴리즈
 * 빌드에서 진입점 부재). testRelease 소스셋이라 release 변이의 no-op [harnessStartRoute] 에 링크된다.
 *
 * 파일 위치 주석: KGP 가 변이별 단위테스트 소스셋의 `kotlin` 디렉터리를 컴파일 소스로 자동 등록하지
 * 않으므로, AGP 가 포함하는 `java` 디렉터리에 두어 이 `.kt` 테스트가 실행되게 한다([DevHarnessDebugTest] 참조).
 */
class DevHarnessReleaseTest {
    @Test
    fun startRouteIsNullInRelease() {
        assertNull(harnessStartRoute())
    }
}
