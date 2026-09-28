package com.saferoute.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferoute.app.UiState
import com.saferoute.app.engine.SafetyAI
import com.saferoute.app.model.Campus

@Composable
fun DashboardScreen(state: UiState) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Control dashboard", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Active robots", "${state.activeRobots}/${state.robots.size}", Icons.Filled.SmartToy, RobotColor, Modifier.weight(1f))
            StatCard("Obstacles", "${state.obstacles}", Icons.Filled.Block, Warn, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Active users", "${state.activeUsers}", Icons.Filled.People, Accent, Modifier.weight(1f))
            StatCard("SOS alerts", "${state.criticalAlerts}", Icons.Filled.NotificationsActive, Danger, Modifier.weight(1f))
        }

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Campus safety index", color = Muted, fontSize = 13.sp)
                    Text("${state.campusSafety}", color = safetyColor(state.campusSafety), fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
                Pill(if (state.night) "Night · ${fmtTime(state.minute)}" else "Day · ${fmtTime(state.minute)}", if (state.night) Accent else Warn)
            }
            LinearProgressIndicator(
                progress = { state.campusSafety / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = safetyColor(state.campusSafety),
                trackColor = PanelBorder,
            )
        }

        Panel {
            SectionTitle("Risk hotspots")
            Campus.segments
                .sortedBy { SafetyAI.safety(it, state.conditions[it.id]) }
                .take(5)
                .forEach { s ->
                    val c = state.conditions[s.id]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(Campus.segmentName(s), color = TextMain, fontSize = 14.sp)
                            Text(SafetyAI.reasons(s, c).joinToString(" · ").ifEmpty { "moderate risk" }, color = Muted, fontSize = 11.sp)
                        }
                        SafetyBadge(SafetyAI.safety(s, c))
                    }
                }
        }

        Panel {
            SectionTitle("Live alerts")
            state.alerts.take(25).forEach { AlertRow(it) }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Panel(modifier) {
        Icon(icon, null, tint = color)
        Text(value, color = TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 12.sp)
    }
}
