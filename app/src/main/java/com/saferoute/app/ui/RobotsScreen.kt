package com.saferoute.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferoute.app.SafeRouteViewModel
import com.saferoute.app.UiState
import com.saferoute.app.model.Campus
import com.saferoute.app.model.Robot
import com.saferoute.app.model.RobotStatus

@Composable
fun RobotsScreen(state: UiState, vm: SafeRouteViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Robot simulator", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Patrol robots sense crowd density, lighting and obstacles, and feed the AI route planner.",
            color = Muted,
            fontSize = 13.sp,
        )

        Panel {
            SectionTitle("Simulation controls")
            ToggleRow("Simulation running", state.running) { vm.toggleRunning() }
            ToggleRow("Force night mode", state.forceNight) { vm.setForceNight(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { vm.crowdSurge(8) }, modifier = Modifier.weight(1f)) {
                    Text("Crowd @ Canteen 2", fontSize = 12.sp)
                }
                FilledTonalButton(onClick = { vm.crowdSurge(7) }, modifier = Modifier.weight(1f)) {
                    Text("Crowd @ Ground", fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = vm::dropObstacle, modifier = Modifier.weight(1f)) {
                    Text("Drop obstacle", fontSize = 12.sp)
                }
                OutlinedButton(onClick = vm::clearObstacles, modifier = Modifier.weight(1f)) {
                    Text("Clear obstacles", fontSize = 12.sp)
                }
            }
        }

        SectionTitle("Fleet (${state.activeRobots}/${state.robots.size} active)")
        state.robots.forEach { RobotCard(it, state) }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextMain, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RobotCard(r: Robot, state: UiState) {
    val seg = Campus.segments[r.segmentId]
    val c = state.conditions[seg.id]
    val next = seg.other(r.fromNode)
    val statusColor = when (r.status) {
        RobotStatus.PATROLLING -> RobotColor
        RobotStatus.RESPONDING -> Warn
        RobotStatus.CHARGING -> Muted
    }
    val where = when (r.status) {
        RobotStatus.CHARGING -> "Docked at ${Campus.place(r.fromNode).name}"
        RobotStatus.RESPONDING -> "Heading to ${Campus.place(r.targetNode ?: next).name}"
        RobotStatus.PATROLLING -> "${Campus.place(r.fromNode).name} → ${Campus.place(next).name}"
    }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.SmartToy, null, tint = statusColor)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.id, color = TextMain, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(where, color = Muted, fontSize = 12.sp)
            }
            Pill(r.status.label, statusColor)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Battery", color = Muted, fontSize = 12.sp, modifier = Modifier.width(60.dp))
            LinearProgressIndicator(
                progress = { r.battery / 100f },
                modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
                color = if (r.battery < 25f) Danger else Safe,
                trackColor = PanelBorder,
            )
            Text(pct(r.battery / 100f), color = TextMain, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Row(Modifier.fillMaxWidth()) {
            Metric(pct(c.crowd), "Crowd", Modifier.weight(1f), if (c.crowd > 0.75f) Danger else TextMain)
            Metric(pct(c.lighting), "Light", Modifier.weight(1f), if (c.lighting < 0.45f) Warn else TextMain)
            Metric(if (c.obstacle) "Yes" else "None", "Obstacle", Modifier.weight(1f), if (c.obstacle) Danger else Safe)
            Metric("${r.scans}", "Scans", Modifier.weight(1f))
        }
    }
}
