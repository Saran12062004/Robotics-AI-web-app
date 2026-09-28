package com.saferoute.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.saferoute.app.model.AlertLevel
import com.saferoute.app.model.PlaceType
import kotlin.math.roundToInt

val Bg = Color(0xFF0B1220)
val PanelColor = Color(0xFF111A2E)
val PanelBorder = Color(0xFF1F2A44)
val MapBg = Color(0xFF0E1628)
val Accent = Color(0xFF4F8CFF)
val RobotColor = Color(0xFF22D3EE)
val Safe = Color(0xFF22C55E)
val Warn = Color(0xFFF59E0B)
val Danger = Color(0xFFEF4444)
val TextMain = Color(0xFFE2E8F0)
val Muted = Color(0xFF94A3B8)

private val scheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    secondary = RobotColor,
    background = Bg,
    onBackground = TextMain,
    surface = PanelColor,
    onSurface = TextMain,
    surfaceVariant = PanelBorder,
    onSurfaceVariant = Muted,
    secondaryContainer = Color(0xFF1E3A6E),
    onSecondaryContainer = TextMain,
    error = Danger,
)

@Composable
fun SafeRouteTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

fun riskColor(risk: Float): Color = when {
    risk < 0.3f -> Safe
    risk < 0.5f -> Warn
    else -> Danger
}

fun safetyColor(score: Int): Color = riskColor(1f - score / 100f)

fun alertColor(level: AlertLevel): Color = when (level) {
    AlertLevel.INFO -> Accent
    AlertLevel.WARNING -> Warn
    AlertLevel.CRITICAL -> Danger
}

fun placeColor(type: PlaceType): Color = when (type) {
    PlaceType.GATE -> Color(0xFFF8FAFC)
    PlaceType.MEDICAL -> Color(0xFFF472B6)
    PlaceType.FOOD -> Color(0xFFFBBF24)
    PlaceType.HOSTEL -> Color(0xFFA78BFA)
    PlaceType.GROUND, PlaceType.SPORTS -> Color(0xFF4ADE80)
    PlaceType.PARKING -> Color(0xFF60A5FA)
    PlaceType.ACADEMIC -> Color(0xFF94A3B8)
}

fun fmtTime(minute: Int) = "%02d:%02d".format(minute / 60, minute % 60)

fun fmtDist(m: Float) = if (m >= 1000f) "%.1f km".format(m / 1000f) else "${m.roundToInt()} m"

fun pct(v: Float) = "${(v * 100).roundToInt()}%"
