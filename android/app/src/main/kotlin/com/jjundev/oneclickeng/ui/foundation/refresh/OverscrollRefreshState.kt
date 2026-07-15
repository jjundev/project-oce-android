package com.jjundev.oneclickeng.ui.foundation.refresh

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.getValue
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
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
 * 오버스크롤 당김의 제스처/오프셋 상태 소유자. 드래그 회계(고무줄)와 릴리스 신호만 담당하고,
 * 릴리스 후 시퀀스(스냅→물결/폭죽→onRefresh→홀드→스프링)는 [OverscrollRefreshBox] 가 [releaseRequest] 를 보고 구동한다.
 */
class OverscrollRefreshState(
    private val scope: CoroutineScope,
    private val thresholdPx: Float,
    private val maxPullPx: Float,
) {
    val offset = Animatable(0f)
    val wave = RefreshWaveState()

    var burstKey: Int by mutableIntStateOf(0)
        private set
    var releaseRequest: Int by mutableIntStateOf(0)
        private set

    /** true 동안(릴리스 시퀀스 실행 중) 새 드래그를 무시한다. Box 가 시퀀스 시작/끝에서 토글.
     *  관측 가능(mutableStateOf)이라 인디케이터 spinning 갱신이 명시적으로 리컴포지션을 트리거한다. */
    var busy: Boolean by mutableStateOf(false)

    private var accumulatedDrag = 0f

    fun currentPullPx(): Float = offset.value

    fun fireBurst() {
        burstKey += 1
    }

    /** 릴리스 시퀀스 종료 시 Box 가 호출(다음 제스처 준비). */
    fun onCycleFinished() {
        busy = false
        accumulatedDrag = 0f
    }

    suspend fun snapBackNoRefresh() {
        withAnimationFrameClock {
            val snapBackSpring = spring<Float>(
                dampingRatio = OverscrollDefaults.SpringDampingRatio,
                stiffness = OverscrollDefaults.SpringStiffness,
            )
            offset.animateTo(0f, snapBackSpring)
        }
        accumulatedDrag = 0f
    }

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // 당겨져 있을 때 위로 드래그하면 먼저 틈을 닫는다.
            if (busy || available.y >= 0f || offset.value <= 0f) return Offset.Zero
            accumulatedDrag = (accumulatedDrag + available.y).coerceAtLeast(0f)
            val target = rubberBand(accumulatedDrag, maxPullPx)
            val delta = target - offset.value
            scope.launch { offset.snapTo(target) }
            return Offset(0f, delta)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            // 리스트가 더 못 내려갈 때(available.y>0) 남은 아래 방향 드래그로 틈을 연다.
            if (busy || available.y <= 0f) return Offset.Zero
            accumulatedDrag = (accumulatedDrag + available.y).coerceAtLeast(0f)
            val target = rubberBand(accumulatedDrag, maxPullPx)
            scope.launch { offset.snapTo(target) }
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (busy || offset.value <= 0f) return Velocity.Zero
            if (offset.value >= thresholdPx) {
                busy = true
                releaseRequest += 1 // Box 가 전체 리프레시 시퀀스 구동
            } else {
                snapBackNoRefresh()
            }
            return available // 남은 fling 소비(리스트로 흘리지 않음)
        }
    }
}

@Composable
fun rememberOverscrollRefreshState(): OverscrollRefreshState {
    val density = LocalDensity.current
    val thresholdPx = with(density) { OverscrollDefaults.Threshold.toPx() }
    val maxPullPx = with(density) { OverscrollDefaults.MaxPull.toPx() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return remember { OverscrollRefreshState(scope, thresholdPx, maxPullPx) }
}
