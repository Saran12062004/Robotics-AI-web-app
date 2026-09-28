package com.saferoute.app.engine

import com.saferoute.app.model.AlertLevel
import com.saferoute.app.model.Campus
import com.saferoute.app.model.Conditions
import com.saferoute.app.model.PlaceType
import com.saferoute.app.model.Robot
import com.saferoute.app.model.RobotStatus
import com.saferoute.app.model.RouteMode
import com.saferoute.app.model.Segment
import kotlin.math.hypot
import kotlin.random.Random

typealias SimEvent = Pair<AlertLevel, String>

/**
 * Simulates the campus environment and a fleet of patrol robots.
 * Each step() advances the campus clock by 2 minutes.
 */
class CampusSimulator(seed: Long = 7L) {
    private val rnd = Random(seed)
    private val segs = Campus.segments

    var minute = 18 * 60 + 10
        private set
    var forceNight = false
    var activeUsers = 260
        private set

    private val obstacleTtl = IntArray(segs.size)
    private val lampFaultTtl = IntArray(segs.size)
    private var surgeNode = -1
    private var surgeTtl = 0

    var conditions: List<Conditions> = segs.map { Conditions(0.4f, 1f, false) }
        private set

    var robots: List<Robot> = listOf(1, 10, 14, 19, 24).mapIndexed { i, s ->
        Robot("R-${i + 1}", s, segs[s].a, rnd.nextFloat(), 55f + rnd.nextFloat() * 45f, RobotStatus.PATROLLING)
    }
        private set

    init {
        refresh(initial = true)
    }

    val daylight: Float
        get() {
            if (forceNight) return 0f
            val h = minute / 60f
            return when {
                h < 5.5f -> 0f
                h < 6.5f -> h - 5.5f
                h < 17.5f -> 1f
                h < 19f -> (19f - h) / 1.5f
                else -> 0f
            }
        }

    val isNight: Boolean get() = daylight < 0.5f

    private fun activity(): Float = when (minute / 60) {
        in 7..8 -> 0.6f
        in 9..16 -> 0.5f
        in 17..20 -> 0.4f
        in 21..22 -> 0.2f
        else -> 0.06f
    }

    private fun weight(t: PlaceType) = when (t) {
        PlaceType.FOOD -> 1.5f
        PlaceType.GATE -> 1.2f
        PlaceType.ACADEMIC -> 1.2f
        PlaceType.GROUND -> 1.0f
        PlaceType.HOSTEL -> 1.0f
        PlaceType.PARKING -> 0.6f
        PlaceType.SPORTS -> 0.7f
        PlaceType.MEDICAL -> 0.6f
    }

    private fun busyness(s: Segment) =
        (weight(Campus.place(s.a).type) + weight(Campus.place(s.b).type)) / 2f

    fun step(): List<SimEvent> {
        val events = mutableListOf<SimEvent>()
        val wasNight = isNight
        minute = (minute + 2) % 1440
        if (!wasNight && isNight) events += AlertLevel.INFO to "Night mode: AI now prioritises lit paths"
        if (wasNight && !isNight) events += AlertLevel.INFO to "Daylight: all paths well lit"

        if (isNight && rnd.nextFloat() < 0.05f) {
            val s = rnd.nextInt(segs.size)
            if (lampFaultTtl[s] == 0) {
                lampFaultTtl[s] = 20 + rnd.nextInt(20)
                events += AlertLevel.WARNING to
                    "${nearestRobot(s).id} detected streetlight failure: ${Campus.segmentName(segs[s])}"
            }
        }
        if (rnd.nextFloat() < 0.06f) events += spawnObstacle()

        for (s in segs.indices) {
            if (obstacleTtl[s] > 0) {
                obstacleTtl[s]--
                if (obstacleTtl[s] == 0) events += AlertLevel.INFO to "Obstacle cleared: ${Campus.segmentName(segs[s])}"
            }
            if (lampFaultTtl[s] > 0) lampFaultTtl[s]--
        }
        if (surgeTtl > 0) {
            surgeTtl--
            if (surgeTtl == 0) {
                events += AlertLevel.INFO to "Crowd back to normal near ${Campus.place(surgeNode).name}"
                surgeNode = -1
            }
        }

        refresh()
        robots = robots.map { advance(it, events) }
        activeUsers = (activeUsers + rnd.nextInt(-12, 13)).coerceIn(60, 480)
        return events
    }

    /** Recomputes crowd and lighting on every path. */
    fun refresh(initial: Boolean = false) {
        val day = daylight
        val act = activity()
        conditions = segs.map { s ->
            val old = conditions[s.id]
            var target = act * busyness(s)
            if (surgeTtl > 0 && (s.a == surgeNode || s.b == surgeNode)) target += 0.6f
            val crowd = if (initial) target else old.crowd + (target - old.crowd) * 0.35f + (rnd.nextFloat() - 0.5f) * 0.08f
            val lamp = if (lampFaultTtl[s.id] > 0) 0.05f else s.lampQuality
            val lighting = day + (1f - day) * lamp
            Conditions(crowd.coerceIn(0f, 1f), lighting.coerceIn(0f, 1f), obstacleTtl[s.id] > 0)
        }
    }

    fun spawnObstacle(): List<SimEvent> {
        val s = rnd.nextInt(segs.size)
        if (obstacleTtl[s] > 0) return emptyList()
        obstacleTtl[s] = 12 + rnd.nextInt(18)
        conditions = conditions.mapIndexed { i, c -> if (i == s) c.copy(obstacle = true) else c }
        return listOf(
            AlertLevel.WARNING to "${nearestRobot(s).id} detected obstacle: ${Campus.segmentName(segs[s])} blocked",
        )
    }

    fun clearObstacles() {
        obstacleTtl.fill(0)
        conditions = conditions.map { it.copy(obstacle = false) }
    }

    fun triggerSurge(node: Int) {
        surgeNode = node
        surgeTtl = 15
    }

    private fun nearestRobot(segId: Int): Robot {
        val s = segs[segId]
        val mx = (Campus.place(s.a).x + Campus.place(s.b).x) / 2
        val my = (Campus.place(s.a).y + Campus.place(s.b).y) / 2
        return robots.minBy {
            val (x, y) = Campus.robotXY(it)
            hypot(x - mx, y - my)
        }
    }

    private fun advance(r: Robot, events: MutableList<SimEvent>): Robot {
        if (r.status == RobotStatus.CHARGING) {
            val b = r.battery + 4f
            if (b < 100f) return r.copy(battery = b)
            events += AlertLevel.INFO to "${r.id} fully charged, resuming patrol"
            return r.copy(battery = 100f, status = RobotStatus.PATROLLING)
        }
        val seg = segs[r.segmentId]
        val responding = r.status == RobotStatus.RESPONDING
        val speed = if (responding) 90f else 40f
        var robot = r.copy(
            progress = r.progress + speed / seg.lengthM,
            battery = r.battery - if (responding) 0.8f else 0.35f,
            scans = r.scans + 1,
        )
        if (robot.progress < 1f) return robot

        val node = seg.other(r.fromNode)
        if (responding && node == r.targetNode) {
            events += AlertLevel.INFO to "${r.id} reached ${Campus.place(node).name} and is assisting"
            robot = robot.copy(status = RobotStatus.PATROLLING, targetNode = null)
        }
        if (robot.status == RobotStatus.PATROLLING && robot.battery < 15f) {
            events += AlertLevel.WARNING to "${r.id} low battery, docking at ${Campus.place(node).name}"
            return robot.copy(fromNode = node, progress = 0f, status = RobotStatus.CHARGING)
        }
        return robot.copy(segmentId = nextSegment(robot, node), fromNode = node, progress = 0f)
    }

    private fun nextSegment(r: Robot, node: Int): Int {
        val target = r.targetNode
        if (r.status == RobotStatus.RESPONDING && target != null) {
            SafetyAI.route(node, target, RouteMode.SHORTEST, conditions)?.segmentIds?.firstOrNull()?.let { return it }
        }
        val all = Campus.adjacency.getValue(node)
        val options = all.filter { !conditions[it.id].obstacle && it.id != r.segmentId }.ifEmpty { all }
        return options[rnd.nextInt(options.size)].id
    }

    /** Sends the closest available robot to [node]. Returns the robot and its ETA in minutes. */
    fun dispatch(node: Int): Pair<Robot, Int>? {
        val best = robots
            .filter { it.status != RobotStatus.CHARGING }
            .mapNotNull { r ->
                val seg = segs[r.segmentId]
                val next = seg.other(r.fromNode)
                val remaining = (1f - r.progress) * seg.lengthM
                val rest = if (next == node) 0f
                else SafetyAI.route(next, node, RouteMode.SHORTEST, conditions)?.distanceM ?: return@mapNotNull null
                r to remaining + rest
            }
            .minByOrNull { it.second } ?: return null
        val (robot, meters) = best
        robots = robots.map {
            if (it.id == robot.id) it.copy(status = RobotStatus.RESPONDING, targetNode = node) else it
        }
        val etaMin = maxOf(1, (meters / 90f * 2f).toInt())
        return robot to etaMin
    }
}
