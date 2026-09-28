package com.saferoute.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saferoute.app.ui.Accent
import com.saferoute.app.ui.Bg
import com.saferoute.app.ui.DashboardScreen
import com.saferoute.app.ui.Danger
import com.saferoute.app.ui.Dot
import com.saferoute.app.ui.MapScreen
import com.saferoute.app.ui.Muted
import com.saferoute.app.ui.PanelColor
import com.saferoute.app.ui.PlacesScreen
import com.saferoute.app.ui.RobotsScreen
import com.saferoute.app.ui.Safe
import com.saferoute.app.ui.SafeRouteTheme
import com.saferoute.app.ui.SosScreen
import com.saferoute.app.ui.TextMain
import com.saferoute.app.ui.Warn
import com.saferoute.app.ui.fmtTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SafeRouteTheme { SafeRouteApp() }
        }
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Map", Icons.Filled.Map),
    Tab("Places", Icons.Filled.Place),
    Tab("Robots", Icons.Filled.SmartToy),
    Tab("Dashboard", Icons.Filled.Dashboard),
    Tab("SOS", Icons.Filled.Warning),
)

@Composable
fun SafeRouteApp(vm: SafeRouteViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Bg,
        topBar = { TopBar(state) },
        bottomBar = {
            NavigationBar(containerColor = PanelColor) {
                tabs.forEachIndexed { i, t ->
                    val isSos = t.label == "SOS"
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            BadgedBox(badge = { if (isSos && state.sos != null) Badge(containerColor = Danger) }) {
                                Icon(t.icon, t.label)
                            }
                        },
                        label = { Text(t.label, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (isSos) Danger else Accent,
                            selectedTextColor = if (isSos) Danger else Accent,
                            unselectedIconColor = if (isSos) Danger else Muted,
                            unselectedTextColor = Muted,
                            indicatorColor = Accent.copy(alpha = 0.15f),
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (tab) {
                0 -> MapScreen(state, vm)
                1 -> PlacesScreen(state, vm, onNavigate = { tab = 0 })
                2 -> RobotsScreen(state, vm)
                3 -> DashboardScreen(state)
                else -> SosScreen(state, vm)
            }
        }
    }
}

@Composable
private fun TopBar(state: UiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Bg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("SafeRoute AI", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Robotics-assisted safe campus navigation", color = Muted, fontSize = 12.sp)
        }
        Row(
            Modifier
                .background(PanelColor, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Dot(if (state.running) Safe else Muted)
            Spacer(Modifier.width(6.dp))
            Text(if (state.running) "LIVE" else "PAUSED", color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Icon(
                if (state.night) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                null,
                tint = if (state.night) Accent else Warn,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(fmtTime(state.minute), color = TextMain, fontSize = 12.sp)
        }
    }
}
