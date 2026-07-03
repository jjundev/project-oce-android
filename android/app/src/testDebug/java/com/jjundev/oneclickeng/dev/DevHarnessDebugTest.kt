package com.jjundev.oneclickeng.dev

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * debug 변이에서 하니스 진입점이 존재함을 반증가능하게 확인한다(M1-09 검증: 플래그 ON 진입).
 * testDebug 소스셋이라 debug 변이의 [harnessStartRoute] 에 링크된다.
 *
 * 파일 위치 주석: 이 리포의 KGP 는 변이별 단위테스트 소스셋의 `kotlin` 디렉터리를 컴파일 소스로 자동
 * 등록하지 않아 `src/testDebug/kotlin` 의 테스트가 실행되지 않는다. AGP 가 포함하는 `java` 디렉터리에
 * 두면 KGP 가 그 안의 `.kt` 도 컴파일해 실행된다(변이별 테스트가 확실히 도는 zero-gradle-change 경로).
 */
class DevHarnessDebugTest {
    @Test
    fun startRouteIsHarnessLauncherInDebug() {
        assertEquals("dev_harness", harnessStartRoute())
    }
}
