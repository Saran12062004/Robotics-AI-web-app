package com.saferoute.app.engine

import com.saferoute.app.model.Campus
import com.saferoute.app.model.Conditions
import com.saferoute.app.model.Route
import com.saferoute.app.model.RouteMode
import com.saferoute.app.model.Segment
import java.util.PriorityQueue
import kotlin.math.roundToInt

/**
 * Scores every path from live robot readings and plans routes with Dijkstra.
 *
 * risk = 0.5 × darkness + 0.3 × crowd risk + 0.2 × (no CCTV)
 * Crowd risk is U-shaped: an empty path (isolated) and an overcrowded path are both unsafe.
 * The safest route minimises  length × (1 + 6 × risk); the shortest route minimises length.
 * Paths blocked by an obstacle are skipped in both modes.
 */
object SafetyAI {

    fun risk(seg: Segment, c: Conditions): Float {
        val darkness = 1f - c.lighting
        val crowdRisk = when {
            c.crowd < 0.12f -> 0.6f
            c.crowd > 0.75f -> c.crowd
            else -> c.crowd * 0.3f
        }
        val noCamera = if (seg.cctv) 0f else 1f
        return (0.5f * darkness + 0.3f * crowdRisk + 0.2f * noCamera).coerceIn(0f, 1f)
    }

    fun safety(seg: Segment, c: Conditions): Int = ((1f - risk(seg, c)) * 100).roundToInt()

    fun reasons(seg: Segment, c: Conditions): List<String> = buildList {
        if (c.obstacle) add("blocked")
        if (c.lighting < 0.45f) add("poorly lit")
        if (c.crowd < 0.12f) add("isolated")
        if (c.crowd > 0.75f) add("overcrowded")
        if (!seg.cctv) add("no CCTV")
    }

    fun route(from: Int, to: Int, mode: RouteMode, cond: List<Conditions>): Route? {
        if (from == to) return null
        val n = Campus.places.size
        val dist = DoubleArray(n) { Double.POSITIVE_INFINITY }
        val via = IntArray(n) { -1 }
        val queue = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        dist[from] = 0.0
        queue.add(0.0 to from)

        while (queue.isNotEmpty()) {
            val (d, u) = queue.poll()!!
            if (d > dist[u]) continue
            if (u == to) break
            for (seg in Campus.adjacency.getValue(u)) {
                val c = cond[seg.id]
                if (c.obstacle) continue
                val w = when (mode) {
                    RouteMode.SHORTEST -> seg.lengthM.toDouble()
                    RouteMode.SAFEST -> seg.lengthM * (1.0 + 6.0 * risk(seg, c))
                }
                val v = seg.other(u)
                if (d + w < dist[v]) {
                    dist[v] = d + w
                    via[v] = seg.id
                    queue.add(dist[v] to v)
                }
            }
        }
        if (dist[to].isInfinite()) return null

        val segs = ArrayList<Int>()
        val nodes = arrayListOf(to)
        var cur = to
        while (cur != from) {
            val s = Campus.segments[via[cur]]
            segs.add(s.id)
            cur = s.other(cur)
            nodes.add(cur)
        }
        segs.reverse()
        nodes.reverse()
        return build(mode, nodes, segs, cond)
    }

    private fun build(mode: RouteMode, nodes: List<Int>, segIds: List<Int>, cond: List<Conditions>): Route {
        val segs = segIds.map { Campus.segments[it] }
        val length = segs.sumOf { it.lengthM.toDouble() }.toFloat()
        val weighted = segs.sumOf { (safety(it, cond[it.id]) * it.lengthM).toDouble() }
        val worst = segs.minOf { safety(it, cond[it.id]) }
        return Route(mode, nodes, segIds, length, (weighted / length).roundToInt(), worst)
    }

    /** Safest route to the closest gate or hospital; null when already at one. */
    fun nearestHelp(from: Int, cond: List<Conditions>): Route? =
        if (Campus.place(from).isHelpPoint) null
        else Campus.places
            .filter { it.isHelpPoint }
            .mapNotNull { route(from, it.id, RouteMode.SAFEST, cond) }
            .minByOrNull { it.distanceM }

    /** Average safety of all paths touching a place. */
    fun placeSafety(placeId: Int, cond: List<Conditions>): Int {
        val segs = Campus.adjacency.getValue(placeId)
        return segs.map { safety(it, cond[it.id]) }.average().roundToInt()
    }
}
