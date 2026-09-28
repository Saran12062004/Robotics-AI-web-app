package com.saferoute.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saferoute.app.engine.CampusSimulator
import com.saferoute.app.engine.SafetyAI
import com.saferoute.app.engine.SimEvent
import com.saferoute.app.model.Alert
import com.saferoute.app.model.AlertLevel
import com.saferoute.app.model.Campus
import com.saferoute.app.model.Conditions
import com.saferoute.app.model.Robot
import com.saferoute.app.model.RobotStatus
import com.saferoute.app.model.Route
import com.saferoute.app.model.RouteMode
import com.saferoute.app.model.SosInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class UiState(
    val minute: Int,
    val night: Boolean,
    val forceNight: Boolean,
    val conditions: List<Conditions>,
    val robots: List<Robot>,
    val alerts: List<Alert>,
    val activeUsers: Int,
    val from: Int,
    val to: Int,
    val mode: RouteMode,
    val route: Route?,
    val altRoute: Route?,
    val running: Boolean,
    val sos: SosInfo?,
) {
    val obstacles: Int get() = conditions.count { it.obstacle }
    val activeRobots: Int get() = robots.count { it.status != RobotStatus.CHARGING }
    val criticalAlerts: Int get() = alerts.count { it.level == AlertLevel.CRITICAL }
    val campusSafety: Int
        get() = Campus.segments.map { SafetyAI.safety(it, conditions[it.id]) }.average().roundToInt()
}

class SafeRouteViewModel : ViewModel() {
    private val sim = CampusSimulator()
    private var alertId = 0L

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(1500)
                if (_state.value.running) publish(sim.step())
            }
        }
    }

    private fun initialState() = withRoutes(
        UiState(
            minute = sim.minute,
            night = sim.isNight,
            forceNight = sim.forceNight,
            conditions = sim.conditions,
            robots = sim.robots,
            alerts = listOf(Alert(alertId++, sim.minute, AlertLevel.INFO, "System online: ${sim.robots.size} robots patrolling")),
            activeUsers = sim.activeUsers,
            from = 0,
            to = 12,
            mode = RouteMode.SAFEST,
            route = null,
            altRoute = null,
            running = true,
            sos = null,
        ),
    )

    private fun withRoutes(s: UiState): UiState {
        val other = if (s.mode == RouteMode.SAFEST) RouteMode.SHORTEST else RouteMode.SAFEST
        return s.copy(
            route = SafetyAI.route(s.from, s.to, s.mode, s.conditions),
            altRoute = SafetyAI.route(s.from, s.to, other, s.conditions),
            sos = s.sos?.copy(helpRoute = SafetyAI.nearestHelp(s.sos.node, s.conditions)),
        )
    }

    private fun describe(r: Route): String {
        val middle = r.nodes.drop(1).dropLast(1)
        return if (middle.isEmpty()) "direct path" else "via " + middle.joinToString(", ") { Campus.place(it).name }
    }

    /** Pulls the latest simulator state into the UI and records alerts. */
    private fun publish(events: List<SimEvent>) {
        val old = _state.value
        val s = withRoutes(
            old.copy(
                minute = sim.minute,
                night = sim.isNight,
                forceNight = sim.forceNight,
                conditions = sim.conditions,
                robots = sim.robots,
                activeUsers = sim.activeUsers,
            ),
        )
        val fresh = events.map { (level, msg) -> Alert(alertId++, sim.minute, level, msg) }.toMutableList()
        val sameTrip = old.from == s.from && old.to == s.to && old.mode == s.mode
        if (sameTrip && old.route != null && s.route != null && old.route.segmentIds != s.route.segmentIds) {
            fresh += Alert(alertId++, sim.minute, AlertLevel.INFO, "Live re-route: ${describe(s.route)} (safety ${s.route.safety})")
        }
        if (sameTrip && old.route != null && s.route == null) {
            fresh += Alert(alertId++, sim.minute, AlertLevel.WARNING, "No open path to ${Campus.place(s.to).name}: all routes blocked")
        }
        _state.value = s.copy(alerts = (fresh.reversed() + old.alerts).take(80))
    }

    fun setFrom(id: Int) {
        _state.value = withRoutes(_state.value.copy(from = id))
    }

    fun setTo(id: Int) {
        _state.value = withRoutes(_state.value.copy(to = id))
    }

    fun setMode(mode: RouteMode) {
        _state.value = withRoutes(_state.value.copy(mode = mode))
    }

    fun swap() {
        val s = _state.value
        _state.value = withRoutes(s.copy(from = s.to, to = s.from))
    }

    fun navigateTo(id: Int) {
        val s = _state.value
        val from = when {
            s.from != id -> s.from
            id == 0 -> 3
            else -> 0
        }
        _state.value = withRoutes(s.copy(from = from, to = id))
    }

    fun setForceNight(on: Boolean) {
        sim.forceNight = on
        sim.refresh()
        publish(listOf(AlertLevel.INFO to if (on) "Night simulation enabled" else "Night simulation disabled"))
    }

    fun crowdSurge(node: Int) {
        sim.triggerSurge(node)
        publish(listOf(AlertLevel.WARNING to "Crowd surge reported at ${Campus.place(node).name}"))
    }

    fun dropObstacle() {
        publish(sim.spawnObstacle())
    }

    fun clearObstacles() {
        sim.clearObstacles()
        publish(listOf(AlertLevel.INFO to "All obstacles cleared"))
    }

    fun toggleRunning() {
        _state.value = _state.value.copy(running = !_state.value.running)
    }

    fun triggerSos(node: Int) {
        val dispatched = sim.dispatch(node)
        _state.value = _state.value.copy(
            sos = SosInfo(node, null, dispatched?.first?.id, dispatched?.second, sim.minute),
        )
        val robotText = dispatched?.let { "${it.first.id} dispatched, ETA ${it.second} min" } ?: "no robot available"
        publish(listOf(AlertLevel.CRITICAL to "SOS raised at ${Campus.place(node).name}: $robotText"))
    }

    fun resolveSos() {
        val sos = _state.value.sos ?: return
        _state.value = _state.value.copy(sos = null)
        publish(listOf(AlertLevel.INFO to "SOS at ${Campus.place(sos.node).name} resolved: user marked safe"))
    }
}
