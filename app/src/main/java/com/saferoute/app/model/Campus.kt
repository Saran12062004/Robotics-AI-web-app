package com.saferoute.app.model

import kotlin.math.hypot

enum class PlaceType(val label: String) {
    GATE("Gate"),
    PARKING("Parking"),
    ACADEMIC("Academic"),
    FOOD("Canteen"),
    GROUND("Ground"),
    SPORTS("Sports"),
    MEDICAL("Hospital"),
    HOSTEL("Hostel"),
}

data class Place(
    val id: Int,
    val name: String,
    val x: Float,
    val y: Float,
    val type: PlaceType,
    val info: String,
) {
    /** Places with staff on duty that an SOS can route a user to. */
    val isHelpPoint: Boolean get() = type == PlaceType.GATE || type == PlaceType.MEDICAL
}

/** A walkable path between two places. */
data class Segment(
    val id: Int,
    val a: Int,
    val b: Int,
    val lengthM: Float,
    val lampQuality: Float,
    val cctv: Boolean,
) {
    fun other(node: Int) = if (node == a) b else a
}

object Campus {
    /** Map coordinates are normalised 0..1; this converts them to metres. */
    private const val SCALE_M = 900f

    val places = listOf(
        Place(0, "Gate 1", 0.50f, 0.96f, PlaceType.GATE, "Main entrance · security desk 24x7"),
        Place(1, "Gate 2", 0.05f, 0.55f, PlaceType.GATE, "West entrance · guard on duty"),
        Place(2, "Gate 3", 0.95f, 0.50f, PlaceType.GATE, "East entrance · guard on duty"),
        Place(3, "Parking", 0.22f, 0.86f, PlaceType.PARKING, "Two & four wheeler parking"),
        Place(4, "Admin Block", 0.50f, 0.78f, PlaceType.ACADEMIC, "Office, principal room"),
        Place(5, "Canteen 1", 0.78f, 0.80f, PlaceType.FOOD, "Main canteen near Gate 1"),
        Place(6, "Library", 0.30f, 0.64f, PlaceType.ACADEMIC, "Central library & reading hall"),
        Place(7, "Main Ground", 0.55f, 0.56f, PlaceType.GROUND, "Assembly & events ground"),
        Place(8, "Canteen 2", 0.78f, 0.60f, PlaceType.FOOD, "Snacks & juice corner"),
        Place(9, "Hospital", 0.18f, 0.40f, PlaceType.MEDICAL, "Campus hospital · first aid 24x7"),
        Place(10, "Sports Ground", 0.55f, 0.32f, PlaceType.SPORTS, "Cricket, football & track"),
        Place(11, "Canteen 3", 0.84f, 0.30f, PlaceType.FOOD, "Hostel-side canteen"),
        Place(12, "Hostel", 0.30f, 0.10f, PlaceType.HOSTEL, "Student hostel blocks"),
        Place(13, "Lab Block", 0.72f, 0.10f, PlaceType.ACADEMIC, "Robotics & AI labs"),
    )

    private data class Link(val a: Int, val b: Int, val lamp: Float, val cctv: Boolean)

    private val links = listOf(
        Link(0, 3, 0.90f, true),
        Link(0, 4, 0.95f, true),
        Link(0, 5, 0.80f, true),
        Link(3, 6, 0.60f, false),
        Link(3, 1, 0.45f, false),
        Link(4, 6, 0.85f, true),
        Link(4, 7, 0.80f, true),
        Link(4, 5, 0.85f, true),
        Link(5, 8, 0.75f, true),
        Link(5, 2, 0.50f, false),
        Link(6, 7, 0.70f, true),
        Link(6, 9, 0.75f, true),
        Link(6, 1, 0.55f, true),
        Link(7, 8, 0.70f, true),
        Link(7, 10, 0.40f, false),
        Link(8, 2, 0.70f, true),
        Link(8, 11, 0.60f, true),
        Link(9, 1, 0.60f, true),
        Link(9, 10, 0.45f, false),
        Link(9, 12, 0.70f, true),
        Link(10, 11, 0.35f, false),
        Link(10, 12, 0.25f, false),
        Link(10, 13, 0.30f, false),
        Link(11, 2, 0.55f, true),
        Link(11, 13, 0.65f, true),
        Link(12, 13, 0.30f, false),
    )

    val segments: List<Segment> = links.mapIndexed { i, l ->
        val pa = places[l.a]
        val pb = places[l.b]
        Segment(i, l.a, l.b, hypot(pa.x - pb.x, pa.y - pb.y) * SCALE_M, l.lamp, l.cctv)
    }

    val adjacency: Map<Int, List<Segment>> = places.associate { p ->
        p.id to segments.filter { it.a == p.id || it.b == p.id }
    }

    fun place(id: Int) = places[id]

    fun segmentName(s: Segment) = "${places[s.a].name} ↔ ${places[s.b].name}"

    /** Robot position on the map in normalised coordinates. */
    fun robotXY(r: Robot): Pair<Float, Float> {
        val a = places[r.fromNode]
        val b = places[segments[r.segmentId].other(r.fromNode)]
        return (a.x + (b.x - a.x) * r.progress) to (a.y + (b.y - a.y) * r.progress)
    }
}
