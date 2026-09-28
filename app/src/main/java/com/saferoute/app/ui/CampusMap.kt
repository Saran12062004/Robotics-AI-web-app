package com.saferoute.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferoute.app.UiState
import com.saferoute.app.engine.SafetyAI
import com.saferoute.app.model.Campus
import com.saferoute.app.model.RobotStatus
import com.saferoute.app.model.Route
import kotlin.math.hypot

private const val PAD = 0.07f

private fun toPx(x: Float, y: Float, w: Float, h: Float) =
    Offset(PAD * w + x * w * (1 - 2 * PAD), PAD * h + y * h * (1 - 2 * PAD))

private fun nearestPlace(o: Offset, w: Float, h: Float, maxDist: Float): Int? =
    Campus.places
        .map { it.id to toPx(it.x, it.y, w, h) }
        .map { (id, p) -> id to hypot(p.x - o.x, p.y - o.y) }
        .filter { it.second <= maxDist }
        .minByOrNull { it.second }
        ?.first

@Composable
fun CampusMap(
    state: UiState,
    modifier: Modifier = Modifier,
    route: Route? = state.route,
    altRoute: Route? = state.altRoute,
    startNode: Int? = state.from,
    endNode: Int? = state.to,
    onTapPlace: ((Int) -> Unit)? = null,
    onLongPressPlace: ((Int) -> Unit)? = null,
) {
    val measurer = rememberTextMeasurer()
    val tap by rememberUpdatedState(onTapPlace)
    val longPress by rememberUpdatedState(onLongPressPlace)
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Restart),
        label = "pulse",
    )
    val labelStyle = TextStyle(color = Color(0xFFCBD5E1), fontSize = 9.sp, fontWeight = FontWeight.Medium)

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(0.88f)
            .clip(RoundedCornerShape(18.dp))
            .background(MapBg)
            .pointerInput(Unit) {
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val reach = 36.dp.toPx()
                detectTapGestures(
                    onTap = { o -> nearestPlace(o, w, h, reach)?.let { tap?.invoke(it) } },
                    onLongPress = { o -> nearestPlace(o, w, h, reach)?.let { longPress?.invoke(it) } },
                )
            },
    ) {
        val w = size.width
        val h = size.height
        fun at(id: Int) = Campus.place(id).let { toPx(it.x, it.y, w, h) }

        // Faint grid
        val grid = Color.White.copy(alpha = 0.04f)
        for (i in 1 until 10) {
            drawLine(grid, Offset(w * i / 10f, 0f), Offset(w * i / 10f, h), 1f)
            drawLine(grid, Offset(0f, h * i / 10f), Offset(w, h * i / 10f), 1f)
        }

        // Selected route glow under the paths
        route?.segmentIds?.forEach { id ->
            val s = Campus.segments[id]
            drawLine(Accent.copy(alpha = 0.45f), at(s.a), at(s.b), 18.dp.toPx(), StrokeCap.Round)
        }

        // Paths coloured by live AI safety score
        Campus.segments.forEach { s ->
            val c = state.conditions[s.id]
            drawLine(riskColor(SafetyAI.risk(s, c)), at(s.a), at(s.b), 5.dp.toPx(), StrokeCap.Round)
        }

        // Alternative route dashed on top
        altRoute?.takeIf { it.segmentIds != route?.segmentIds }?.segmentIds?.forEach { id ->
            val s = Campus.segments[id]
            drawLine(
                Color.White.copy(alpha = 0.55f), at(s.a), at(s.b), 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f)),
            )
        }

        // Obstacles
        Campus.segments.filter { state.conditions[it.id].obstacle }.forEach { s ->
            val m = (at(s.a) + at(s.b)) / 2f
            val r = 9.dp.toPx()
            drawCircle(Danger, r, m)
            val k = r * 0.45f
            drawLine(Color.White, m + Offset(-k, -k), m + Offset(k, k), 2.dp.toPx(), StrokeCap.Round)
            drawLine(Color.White, m + Offset(-k, k), m + Offset(k, -k), 2.dp.toPx(), StrokeCap.Round)
        }

        // Places
        Campus.places.forEach { p ->
            val o = at(p.id)
            drawCircle(Bg, 9.dp.toPx(), o)
            drawCircle(placeColor(p.type), 6.dp.toPx(), o)
            if (p.id == startNode) drawCircle(Safe, 12.dp.toPx(), o, style = Stroke(3.dp.toPx()))
            if (p.id == endNode) drawCircle(Accent, 12.dp.toPx(), o, style = Stroke(3.dp.toPx()))

            val layout = measurer.measure(AnnotatedString(p.name), labelStyle)
            val below = p.y < 0.9f
            val top = if (below) o.y + 13.dp.toPx() else o.y - 13.dp.toPx() - layout.size.height
            val left = (o.x - layout.size.width / 2f).coerceIn(2f, w - layout.size.width - 2f)
            drawText(layout, topLeft = Offset(left, top))
        }

        // SOS pulse
        state.sos?.let { sos ->
            val o = at(sos.node)
            drawCircle(Danger.copy(alpha = 1f - pulse), 10.dp.toPx() + 28.dp.toPx() * pulse, o, style = Stroke(3.dp.toPx()))
            drawCircle(Danger, 7.dp.toPx(), o)
        }

        // Robots
        state.robots.forEach { r ->
            val (x, y) = Campus.robotXY(r)
            val o = toPx(x, y, w, h)
            val color = when (r.status) {
                RobotStatus.PATROLLING -> RobotColor
                RobotStatus.RESPONDING -> Warn
                RobotStatus.CHARGING -> Muted
            }
            drawCircle(color.copy(alpha = 0.25f), 11.dp.toPx(), o)
            drawCircle(color, 6.dp.toPx(), o)
            drawCircle(Color.White, 6.dp.toPx(), o, style = Stroke(1.5.dp.toPx()))
        }
    }
}
