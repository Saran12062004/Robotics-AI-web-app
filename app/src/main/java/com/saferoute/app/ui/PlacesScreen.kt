package com.saferoute.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferoute.app.SafeRouteViewModel
import com.saferoute.app.UiState
import com.saferoute.app.engine.SafetyAI
import com.saferoute.app.model.Campus
import com.saferoute.app.model.PlaceType
import com.saferoute.app.model.RobotStatus
import com.saferoute.app.model.RouteMode

private enum class PlaceFilter(val label: String, val types: Set<PlaceType>) {
    ALL("All", PlaceType.entries.toSet()),
    GATES("Gates", setOf(PlaceType.GATE)),
    CANTEENS("Canteens", setOf(PlaceType.FOOD)),
    GROUNDS("Grounds", setOf(PlaceType.GROUND, PlaceType.SPORTS)),
    PARKING("Parking", setOf(PlaceType.PARKING)),
    HOSPITAL("Hospital", setOf(PlaceType.MEDICAL)),
    BLOCKS("Blocks", setOf(PlaceType.ACADEMIC, PlaceType.HOSTEL)),
}

fun placeIcon(type: PlaceType): ImageVector = when (type) {
    PlaceType.GATE -> Icons.Filled.MeetingRoom
    PlaceType.PARKING -> Icons.Filled.DirectionsCar
    PlaceType.ACADEMIC -> Icons.Filled.School
    PlaceType.FOOD -> Icons.Filled.Restaurant
    PlaceType.GROUND -> Icons.Filled.Park
    PlaceType.SPORTS -> Icons.Filled.SportsSoccer
    PlaceType.MEDICAL -> Icons.Filled.LocalHospital
    PlaceType.HOSTEL -> Icons.Filled.Hotel
}

@Composable
fun PlacesScreen(state: UiState, vm: SafeRouteViewModel, onNavigate: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf(PlaceFilter.ALL) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Campus locations", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "You are at ${Campus.place(state.from).name}. Live safety comes from robot sensor data.",
            color = Muted,
            fontSize = 13.sp,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaceFilter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
        }

        Campus.places.filter { it.type in filter.types }.forEach { p ->
            val score = SafetyAI.placeSafety(p.id, state.conditions)
            val touching = Campus.adjacency.getValue(p.id)
            val crowd = touching.map { state.conditions[it.id].crowd }.average().toFloat()
            val blocked = touching.count { state.conditions[it.id].obstacle }
            val robotsNear = state.robots.count {
                it.status != RobotStatus.CHARGING &&
                    (it.fromNode == p.id || Campus.segments[it.segmentId].other(it.fromNode) == p.id)
            }
            val trip = SafetyAI.route(state.from, p.id, RouteMode.SAFEST, state.conditions)

            Panel(border = if (p.id == state.from) Safe else PanelBorder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .background(placeColor(p.type).copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(placeIcon(p.type), null, tint = placeColor(p.type))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Text("${p.type.label} · ${p.info}", color = Muted, fontSize = 12.sp)
                    }
                    SafetyBadge(score)
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Pill(
                        when {
                            p.id == state.from -> "You are here"
                            trip != null -> "${fmtDist(trip.distanceM)} · ${trip.etaMin} min"
                            else -> "No open path"
                        },
                        Accent,
                    )
                    Pill("Crowd ${pct(crowd)}", if (crowd > 0.75f) Danger else Muted)
                    if (robotsNear > 0) Pill("$robotsNear robot nearby", RobotColor)
                    if (blocked > 0) Pill("$blocked blocked", Danger)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.navigateTo(p.id)
                            onNavigate()
                        },
                        enabled = p.id != state.from,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Navigation, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Navigate")
                    }
                    OutlinedButton(
                        onClick = { vm.setFrom(p.id) },
                        enabled = p.id != state.from,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.MyLocation, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("I'm here")
                    }
                }
            }
        }
    }
}
