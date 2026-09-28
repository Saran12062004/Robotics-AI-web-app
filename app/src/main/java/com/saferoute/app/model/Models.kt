package com.saferoute.app.model

import kotlin.math.roundToInt

/** Live sensor readings for one path, as reported by the robots. */
data class Conditions(
    val crowd: Float,
    val lighting: Float,
    val obstacle: Boolean,
)

enum class RobotStatus(val label: String) {
    PATROLLING("Patrolling"),
    RESPONDING("Responding"),
    CHARGING("Charging"),
}

data class Robot(
    val id: String,
    val segmentId: Int,
    val fromNode: Int,
    val progress: Float,
    val battery: Float,
    val status: RobotStatus,
    val targetNode: Int? = null,
    val scans: Int = 0,
)

enum class AlertLevel { INFO, WARNING, CRITICAL }

data class Alert(
    val id: Long,
    val minute: Int,
    val level: AlertLevel,
    val message: String,
)

enum class RouteMode(val label: String) {
    SAFEST("Safest (AI)"),
    SHORTEST("Shortest"),
}

data class Route(
    val mode: RouteMode,
    val nodes: List<Int>,
    val segmentIds: List<Int>,
    val distanceM: Float,
    val safety: Int,
    val worstSafety: Int,
) {
    val etaMin: Int get() = maxOf(1, (distanceM / WALK_M_PER_MIN).roundToInt())

    companion object {
        const val WALK_M_PER_MIN = 80f
    }
}

data class SosInfo(
    val node: Int,
    val helpRoute: Route?,
    val robotId: String?,
    val robotEtaMin: Int?,
    val raisedAt: Int,
)
