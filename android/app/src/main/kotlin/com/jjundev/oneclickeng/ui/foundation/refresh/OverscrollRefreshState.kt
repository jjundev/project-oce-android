package com.jjundev.oneclickeng.ui.foundation.refresh

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * 실제 컴포지션(rememberCoroutineScope)의 코루틴 컨텍스트에는 Recomposer 가 제공하는
 * MonotonicFrameClock 이 이미 있어 [Animatable.animateTo] 가 정상 동작한다. 반면 순수
 * `TestScope`(코루틴 단위 테스트)에는 프레임 클럭이 없어 즉시 [IllegalStateException] 이 난다.
 * 앰비언트 클럭이 없을 때만 합성 클럭으로 폴백해 애니메이션이 결정적으로 완료되게 한다 —
 * 프로덕션 경로(항상 앰비언트 클럭 보유)의 타이밍에는 영향이 없다.
 */
private val syntheticFrameClock: MonotonicFrameClock = object : MonotonicFrameClock {
    private var frameTimeNanos = 0L
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
        frameTimeNanos += 16_000_000L // 가상 16ms 틱
        return onFrame(frameTimeNanos)
    }
}

private suspend fun <R> withAnimationFrameClock(block: suspend () -> R): R =
    if (coroutineContext[MonotonicFrameClock] != null) block()
    else withContext(syntheticFrameClock) { block() }

/**
 * 오버스크롤 당김의 제스처/오프셋 상태 소유자.
 *
 * 드래그 중엔 [dragOffsetPx] 가 값의 유일한 소유자다 — onPreScroll/onPostScroll 안에서 매 이벤트마다
 * *동기적으로* 갱신되며 어떤 코루틴도 거치지 않는다. 정착(스냅백 또는 릴리스 시퀀스, [OverscrollRefreshBox]
 * 가 [releaseRequest] 를 보고 구동)이 시작되는 순간 [offset](Animatable)이 [dragOffsetPx] 의 마지막
 * 값을 그대로 이어받고 [dragOffsetPx] 는 0으로 비워진다 — 항상 둘 중 하나만 0이 아니므로
 * [currentPullPx] 는 단순 합으로 어느 쪽이 값을 들고 있든 정확히 읽힌다.
 *
 * (이전 구현은 onPreScroll/onPostScroll 이 `scope.launch { offset.snapTo(target) }` 로 오프셋을
 * *비동기* 반영했는데, onPreFling 이 그 사이 아직 반영되지 않은 offset.value 를 동기적으로 읽어
 * 오판할 수 있었다. 이 레이스는 드래그 소유값을 코루틴 없이 동기 필드로 옮겨 제거했지만, 실기기
 * 재현에서 밝혀진 진짜 원인은 따로 있었다: 릴리스/스냅백 판단이 [onPreFling] 안에서만 이뤄졌는데,
 * "리스트 안쪽에서 빠르게 위로 튕기는" 제스처는 그 fling 이 아직 진행 중일 때(경계에 아직 안 닿았을 때)
 * onPreFling 이 한 번 호출되고(그 시점엔 dragOffsetPx==0 이라 정상적으로 통과) — 그 *같은* fling 이
 * 감속하며 상단 경계에 부딪히면 틈이 [onPostScroll](source=SideEffect)로만 열린다. 이 시나리오의
 * 종료 이벤트는 onPreFling 이 아니라 [onPostFling] 인데, 이전 구현엔 onPostFling 오버라이드가 아예
 * 없어 그 틈을 닫을(릴리스하거나 스냅백할) 기회가 영영 없었다 — 실기기 adb 재현(연속 위 스와이프 3회
 * → 빠른 아래 스와이프 1회)에서 `dragOffsetPx` 가 504px 로 열린 채 고착되는 것으로 확인. [resolvePull]
 * 을 onPreFling/onPostFling 양쪽에서 호출해 이 경로를 닫는다.)
 */
class OverscrollRefreshState(
    private val thresholdPx: Float,
    private val maxPullPx: Float,
) {
    val offset = Animatable(0f)
    val wave = RefreshWaveState()

    var burstKey: Int by mutableIntStateOf(0)
        private set
    var releaseRequest: Int by mutableIntStateOf(0)
        private set

    /** spin 인디케이터 표시 여부(릴리스 시퀀스 진행 중에만 true). 제스처 재진입 가드는 [settling] 이 맡는다. */
    var busy: Boolean by mutableStateOf(false)

    /** 드래그 중 매 이벤트마다 동기적으로 갱신되는 당김 오프셋(px). 정착 시퀀스가 값을 넘겨받는
     *  순간 0으로 비워진다. 자세한 설명은 클래스 문서 참고. */
    var dragOffsetPx: Float by mutableFloatStateOf(0f)
        private set

    /** 정착(스냅백/릴리스) 애니메이션이 진행 중인 동안 새 제스처가 끼어들지 못하게 막는 재진입 가드.
     *  release 시퀀스 동안엔 [busy] 도 함께 true 이지만, 임계값 미달의 스냅백에서는 [busy] 는 계속
     *  false 이므로 이 가드가 별도로 필요하다. */
    private var settling = false

    private var accumulatedDrag = 0f

    /** 콘텐츠/인디케이터가 그릴 현재 당김량. [offset]과 [dragOffsetPx] 중 항상 정확히 하나만
     *  0이 아니므로 단순 합으로 어느 쪽이 값을 들고 있든 정확히 읽힌다(테스트가 [offset] 을 직접
     *  스냅해 특정 시각 상태를 재현하는 경우에도 별도 분기 없이 자연히 반영됨). */
    fun currentPullPx(): Float = offset.value + dragOffsetPx

    fun fireBurst() {
        burstKey += 1
    }

    /** 릴리스 시퀀스 종료 시 Box 가 호출(다음 제스처 준비). */
    fun onCycleFinished() {
        busy = false
        settling = false
        accumulatedDrag = 0f
        dragOffsetPx = 0f
    }

    suspend fun snapBackNoRefresh() {
        settling = true
        try {
            withAnimationFrameClock {
                offset.snapTo(dragOffsetPx)
                dragOffsetPx = 0f
                val snapBackSpring = spring<Float>(
                    dampingRatio = OverscrollDefaults.SpringDampingRatio,
                    stiffness = OverscrollDefaults.SpringStiffness,
                )
                offset.animateTo(0f, snapBackSpring)
            }
        } finally {
            accumulatedDrag = 0f
            settling = false
        }
    }

    /**
     * 현재 [dragOffsetPx] 를 보고 릴리스할지 스냅백할지 결정한다. [onPreFling] 과 [onPostFling]
     * 양쪽에서 호출된다 — 틈이 "손가락이 아직 fling 중일 때" onPreFling 이전에 이미 닫혀 있었다면
     * (dragOffsetPx<=0) 아무 것도 하지 않고, fling 이 경계에 부딪히며 onPostScroll(SideEffect)로
     * 뒤늦게 열렸다면 이 함수가 onPostFling 호출 시점에 그 틈을 마저 처리한다. [settling] 가드 덕에
     * 두 호출 지점 중 어느 한쪽이 이미 처리를 시작했으면 다른 쪽은 즉시 no-op.
     */
    private suspend fun resolvePull() {
        if (settling || dragOffsetPx <= 0f) return
        if (dragOffsetPx >= thresholdPx) {
            settling = true
            try {
                offset.snapTo(dragOffsetPx) // 릴리스 시퀀스가 정확히 이 당김 위치에서 이어받도록 시드
                dragOffsetPx = 0f
                busy = true
                releaseRequest += 1 // Box 가 전체 리프레시 시퀀스 구동 → onCycleFinished() 가 이후 settling 을 정리한다
            } catch (e: CancellationException) {
                // snapTo 가 취소/실패하면 releaseRequest 가 절대 증가하지 않아 Box 의 release
                // LaunchedEffect 가 실행되지 않는다 — 즉 onCycleFinished() 가 호출되지 않아 settling 이
                // true 로 고착된다. 여기서 직접 정리해 다음 제스처를 받을 수 있는 상태로 되돌린다.
                settling = false
                accumulatedDrag = 0f
                dragOffsetPx = 0f
                throw e
            }
        } else {
            snapBackNoRefresh()
        }
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // 당겨져 있을 때 위로 드래그하면 먼저 틈을 닫는다.
            if (settling || available.y >= 0f || dragOffsetPx <= 0f) return Offset.Zero
            accumulatedDrag = (accumulatedDrag + available.y).coerceAtLeast(0f)
            val target = rubberBand(accumulatedDrag, maxPullPx)
            val delta = target - dragOffsetPx
            dragOffsetPx = target
            return Offset(0f, delta)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // 리스트가 더 못 내려갈 때(available.y>0) 남은 아래 방향 드래그로 틈을 연다.
            // source 는 UserInput(직접 드래그)뿐 아니라 SideEffect(fling 이 감속하며 경계에 부딪히는
            // 경우)로도 온다 — 후자의 종료는 onPreFling 이 아니라 onPostFling 이므로, 거기서 열린
            // 틈은 onPostFling 의 resolvePull() 호출이 책임진다.
            if (settling || available.y <= 0f) return Offset.Zero
            accumulatedDrag = (accumulatedDrag + available.y).coerceAtLeast(0f)
            dragOffsetPx = rubberBand(accumulatedDrag, maxPullPx)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (settling || dragOffsetPx <= 0f) return Velocity.Zero
            resolvePull()
            return available // 남은 fling 소비(리스트로 흘리지 않음)
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            // fling 이 진행 중일 때(onPreFling 호출 시점) 아직 열려 있지 않았던 틈이, 그 fling 이
            // 경계에 부딪혀 감속하는 동안 onPostScroll(SideEffect)로 뒤늦게 열렸을 수 있다 — 이게 이
            // 함수가 최종적으로 그 틈을 마무리(릴리스/스냅백)할 유일한 기회다.
            resolvePull()
            return Velocity.Zero
        }
    }
}

@Composable
fun rememberOverscrollRefreshState(): OverscrollRefreshState {
    val density = LocalDensity.current
    val thresholdPx = with(density) { OverscrollDefaults.Threshold.toPx() }
    val maxPullPx = with(density) { OverscrollDefaults.MaxPull.toPx() }
    return remember { OverscrollRefreshState(thresholdPx, maxPullPx) }
}
