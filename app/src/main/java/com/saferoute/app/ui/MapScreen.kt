package com.saferoute.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferoute.app.SafeRouteViewModel
import com.saferoute.app.UiState
import com.saferoute.app.engine.SafetyAI
import com.saferoute.app.model.Campus
import com.saferoute.app.model.Route
import com.saferoute.app.model.RouteMode
import androidx.compose.foundation.background

@Composable
fun MapScreen(state: UiState, vm: SafeRouteViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CampusMap(state, onTapPlace = vm::setTo, onLongPressPlace = vm::setFrom)
        Text("Tap a place to set destination · long-press to set start", color = Muted, fontSize = 12.sp)
        Legend()

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlaceDropdown("From", state.from, vm::setFrom, Modifier.weight(1f))
                IconButton(onClick = vm::swap) { Icon(Icons.Filled.SwapVert, "Swap", tint = Accent) }
                PlaceDropdown("To", state.to, vm::setTo, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RouteMode.entries.forEach { m ->
                    val selected = state.mode == m
                    FilterChip(
                        selected = selected,
                        onClick = { vm.setMode(m) },
                        label = { Text(m.label) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Filled.Check, null, Modifier.size(16.dp)) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        RouteCard(state)
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(Safe, "Safe")
        LegendItem(Warn, "Caution")
        LegendItem(Danger, "Risky")
        LegendItem(RobotColor, "Robot")
        LegendItem(Accent, "Route")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color)
        Spacer(Modifier.width(5.dp))
        Text(label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun RouteCard(state: UiState) {
    val route = state.route
    when {
        state.from == state.to -> Panel { Text("Start and destination are the same. Pick another place.", color = Muted) }
        route == null -> Panel(border = Danger) {
            Text("No open path", color = Danger, fontWeight = FontWeight.Bold)
            Text("Robots report every route is blocked right now. Try again shortly or use SOS.", color = Muted, fontSize = 13.sp)
        }
        else -> Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(if (route.mode == RouteMode.SAFEST) "AI safest route" else "Shortest route", Modifier.weight(1f))
                SafetyBadge(route.safety)
            }
            Row(Modifier.fillMaxWidth()) {
                Metric(fmtDist(route.distanceM), "Distance", Modifier.weight(1f))
                Metric("${route.etaMin} min", "Walking", Modifier.weight(1f))
                Metric("${route.worstSafety}", "Weakest link", Modifier.weight(1f), safetyColor(route.worstSafety))
            }
            LinearProgressIndicator(
                progress = { route.safety / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = safetyColor(route.safety),
                trackColor = PanelBorder,
            )
            Text(comparison(route, state.altRoute), color = Muted, fontSize = 13.sp)
            HorizontalDivider(color = PanelBorder)
            Text("Directions", color = TextMain, fontWeight = FontWeight.SemiBold)
            Steps(route, state)
        }
    }
}

private fun comparison(route: Route, alt: Route?): String {
    if (alt == null) return "Only one open path is available."
    if (alt.segmentIds == route.segmentIds) return "Right now the shortest path is also the safest."
    val extra = route.distanceM - alt.distanceM
    return if (route.mode == RouteMode.SAFEST) {
        "AI avoids risky paths: ${fmtDist(extra)} longer than the shortest route but " +
            "${route.safety - alt.safety} points safer (shortest scores ${alt.safety})."
    } else {
        "The AI safest route scores ${alt.safety} (+${alt.safety - route.safety}) for ${fmtDist(-extra)} extra walking."
    }
}

@Composable
private fun Steps(route: Route, state: UiState) {
    route.nodes.forEachIndexed { i, id ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(22.dp).background(if (i == 0) Safe else if (i == route.nodes.lastIndex) Accent else PanelBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("${i + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(Campus.place(id).name, color = TextMain, fontWeight = FontWeight.Medium)
        }
        if (i < route.segmentIds.size) {
            val seg = Campus.segments[route.segmentIds[i]]
            val c = state.conditions[seg.id]
            val score = SafetyAI.safety(seg, c)
            Row(Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(2.dp).height(30.dp).background(safetyColor(score)))
                Spacer(Modifier.width(18.dp))
                Text(
                    "${fmtDist(seg.lengthM)} · light ${pct(c.lighting)} · crowd ${pct(c.crowd)} · " +
                        (if (seg.cctv) "CCTV" else "no CCTV") + " · safety $score",
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}
