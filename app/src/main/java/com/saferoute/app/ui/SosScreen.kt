package com.saferoute.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferoute.app.SafeRouteViewModel
import com.saferoute.app.UiState
import com.saferoute.app.model.Campus
import com.saferoute.app.model.RobotStatus
import com.saferoute.app.model.SosInfo
import kotlinx.coroutines.delay

@Composable
fun SosScreen(state: UiState, vm: SafeRouteViewModel) {
    val context = LocalContext.current
    var countdown by remember { mutableStateOf<Int?>(null) }
    val from by rememberUpdatedState(state.from)

    LaunchedEffect(countdown) {
        val c = countdown ?: return@LaunchedEffect
        if (c == 0) {
            vibrate(context)
            vm.triggerSos(from)
            countdown = null
        } else {
            delay(1000)
            countdown = c - 1
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val sos = state.sos
        if (sos == null) {
            Text("Emergency SOS", color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
            PlaceDropdown("You are at", state.from, vm::setFrom, Modifier.fillMaxWidth())
            Spacer(Modifier.size(8.dp))
            Box(
                Modifier
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFFFF6B6B), Danger, Color(0xFF991B1B))))
                    .clickable(enabled = countdown == null) { countdown = 3 },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(countdown?.toString() ?: "SOS", color = Color.White, fontSize = 60.sp, fontWeight = FontWeight.Black)
                    Text(if (countdown == null) "Tap to send alert" else "Sending…", color = Color.White.copy(alpha = 0.85f))
                }
            }
            if (countdown != null) {
                OutlinedButton(onClick = { countdown = null }) { Text("Cancel") }
            }
            Panel {
                SectionTitle("What happens")
                listOf(
                    "The nearest patrol robot is dispatched to you",
                    "Security at the gates is alerted with your location",
                    "AI shows the safest path to the nearest gate or hospital",
                    "You can call 112 or share the alert in one tap",
                ).forEachIndexed { i, t ->
                    Row {
                        Text("${i + 1}.", color = Accent, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                        Text(t, color = Muted, fontSize = 13.sp)
                    }
                }
            }
        } else {
            ActiveSos(sos, state, vm, context)
        }
    }
}

@Composable
private fun ActiveSos(sos: SosInfo, state: UiState, vm: SafeRouteViewModel, context: Context) {
    val place = Campus.place(sos.node)
    val robot = state.robots.firstOrNull { it.id == sos.robotId }
    val help = sos.helpRoute

    Panel(border = Danger) {
        Text("SOS ACTIVE", color = Danger, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text("Raised at ${fmtTime(sos.raisedAt)} from ${place.name}", color = TextMain)
        Text(
            when {
                robot == null -> "No robot available: security has been notified"
                robot.status == RobotStatus.RESPONDING -> "${robot.id} is on the way · ETA ${sos.robotEtaMin} min"
                else -> "${robot.id} has reached you"
            },
            color = Warn,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            if (help == null) "You are at ${place.name}, a staffed help point. Stay near the security/medical staff."
            else "Nearest help: ${Campus.place(help.nodes.last()).name} · ${fmtDist(help.distanceM)} (${help.etaMin} min) · safety ${help.safety}",
            color = Muted,
            fontSize = 13.sp,
        )
    }

    CampusMap(
        state,
        route = help,
        altRoute = null,
        startNode = sos.node,
        endNode = help?.nodes?.last(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { launch(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) },
            colors = ButtonDefaults.buttonColors(containerColor = Danger),
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Filled.Call, null)
            Spacer(Modifier.width(6.dp))
            Text("Call 112")
        }
        FilledTonalButton(
            onClick = {
                val text = "SOS from SafeRoute AI: I need help near ${place.name} (campus). Time ${fmtTime(sos.raisedAt)}."
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                launch(context, Intent.createChooser(send, "Share SOS"))
            },
            modifier = Modifier.weight(1f),
        ) {
            Icon(Icons.Filled.Share, null)
            Spacer(Modifier.width(6.dp))
            Text("Share")
        }
    }
    Button(
        onClick = vm::resolveSos,
        colors = ButtonDefaults.buttonColors(containerColor = Safe),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.CheckCircle, null)
        Spacer(Modifier.width(6.dp))
        Text("I'm safe now")
    }
}

private fun launch(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}

private fun vibrate(context: Context) {
    val vibrator = context.getSystemService(Vibrator::class.java) ?: return
    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 600), -1))
}
