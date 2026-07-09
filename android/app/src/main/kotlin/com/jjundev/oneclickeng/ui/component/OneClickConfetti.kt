package com.jjundev.oneclickeng.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.jjundev.oneclickeng.ui.foundation.rememberReduceMotion
import com.jjundev.oneclickeng.ui.theme.OceTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 세션 요약 진입 폭죽(프로토 fireConfetti, oc-confetti keyframes) — 점수 hero 부근에서 1회 버스트로 흩날리는
 * 색종이 오버레이. 입력 차단 없음(순수 장식 Canvas). [reduceMotion] 이면 아예 그리지 않는다(프로토도
 * prefers-reduced-motion 에서 미발사). 애니메이션 종료 후엔 캔버스가 빈 상태로 남는다(0 비용에 가깝게 조기 반환).
 *
 * 파티클 기하는 고정 시드 [Random] 으로 결정적(스크린샷 테스트 재현 가능).
 */
@Composable
fun OneClickConfettiBurst(
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = rememberReduceMotion(),
) {
    if (reduceMotion) return
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = DURATION_MS, easing = LinearEasing))
    }
    val palette =
        listOf(
            MaterialTheme.colorScheme.primary,
            OceTheme.colors.gameStreak,
            OceTheme.colors.gameSaveGold,
            OceTheme.colors.feedbackNaturalAccent,
            OceTheme.colors.feedbackCorrectAccent,
        )
    val particles =
        remember {
            val rnd = Random(SEED)
            List(PARTICLE_COUNT) {
                ConfettiParticle(
                    angleRad = (rnd.nextFloat() * 360f) * DEG_TO_RAD,
                    speed = MIN_SPEED + rnd.nextFloat() * (MAX_SPEED - MIN_SPEED),
                    sizeFactor = MIN_SIZE_FACTOR + rnd.nextFloat() * (1f - MIN_SIZE_FACTOR),
                    spinTurns = rnd.nextFloat() * MAX_SPIN_TURNS,
                    colorIndex = rnd.nextInt(palette.size.coerceAtLeast(1)),
                )
            }
        }
    val t = progress.value
    if (t >= 1f) return
    Canvas(modifier = modifier) {
        val origin = Offset(size.width / 2f, size.height * ORIGIN_Y_RATIO)
        val reach = size.width * REACH_RATIO
        val pieceBase = PIECE_SIZE.toPx()
        particles.forEach { p ->
            val dist = p.speed * t * reach
            val x = origin.x + cos(p.angleRad) * dist
            val y = origin.y + sin(p.angleRad) * dist + GRAVITY_RATIO * size.height * t * t
            val alpha = ((1f - t) * FADE_GAIN).coerceIn(0f, 1f)
            val piece = pieceBase * p.sizeFactor
            rotate(degrees = p.spinTurns * FULL_TURN_DEG * t, pivot = Offset(x, y)) {
                drawRect(
                    color = palette[p.colorIndex].copy(alpha = alpha),
                    topLeft = Offset(x - piece / 2f, y - piece / 2f),
                    size = Size(piece, piece * PIECE_ASPECT),
                )
            }
        }
    }
}

private data class ConfettiParticle(
    val angleRad: Float,
    val speed: Float,
    val sizeFactor: Float,
    val spinTurns: Float,
    val colorIndex: Int,
)

private const val SEED = 42
private const val PARTICLE_COUNT = 48
private const val DURATION_MS = 1800
private const val DEG_TO_RAD = (Math.PI / 180.0).toFloat()
private const val MIN_SPEED = 0.35f
private const val MAX_SPEED = 1f
private const val MIN_SIZE_FACTOR = 0.6f
private const val MAX_SPIN_TURNS = 2f
private const val FULL_TURN_DEG = 360f

/** 버스트 원점 y(화면 높이 비율) — 점수 hero 부근. */
private const val ORIGIN_Y_RATIO = 0.18f

/** 최대 도달 반경(화면 폭 비율). */
private const val REACH_RATIO = 0.55f

/** 낙하 가속(화면 높이 비율 · t²). */
private const val GRAVITY_RATIO = 0.25f

/** 후반 페이드 게인 — t≈0.6 까지 불투명 유지 후 감쇠. */
private const val FADE_GAIN = 2.5f

/** 색종이 조각 기준 크기. */
private val PIECE_SIZE = 8.dp

/** 조각 세로 비율(직사각 색종이). */
private const val PIECE_ASPECT = 0.6f
