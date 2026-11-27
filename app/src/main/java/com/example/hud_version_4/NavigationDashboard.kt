package com.example.hud_version_4

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NavigationDashboard(unit: String, speed: Float?) {
    val nav by navigationReading.collectAsState()
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        DigitalSpeedometer(speed, unit, Modifier.weight(1.5f).fillMaxHeight())
        Column(Modifier.weight(1.3f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            NavigationPanel("CURRENT TIME", clock, Modifier.weight(1f))
            NavigationPanel("ETA", nav.eta, Modifier.weight(1f))
            NavigationPanel("REM TIME", if (nav.time == "--" && nav.active) estimatedRemaining(nav.eta, now) else nav.time, Modifier.weight(1f))
            NavigationPanel("REM DIST", if (nav.distance == "--") nav.estimatedDistanceMetres?.let { formatEstimatedDistance(it, unit) } ?: "--" else nav.distance, Modifier.weight(1f))
        }
    }
}

@Composable
fun NavigationGuidance() {
    val nav by navigationReading.collectAsState()
    Column(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("LANE GUIDANCE", fontSize = 10.sp)
                if (nav.lanes.isEmpty()) {
                    Text(nav.laneHint.ifBlank { "Not available" }, Modifier.weight(1f), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else {
                    nav.lanes.take(8).forEach { lane ->
                        Surface(Modifier.weight(1f), color = if (lane.recommended) Color(0xFF173526) else Color(0xFF252525), shape = MaterialTheme.shapes.small) {
                            Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
                                Text(lane.arrows, color = if (lane.recommended) Color(0xFF66DD88) else Color.LightGray, fontSize = 20.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif)
                            }
                        }
                    }
                }
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
            Text(nav.instruction.ifBlank { if (nav.active) "--" else "Start a Google Maps route" },
                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                fontSize = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NavigationPanel(label: String, value: String, modifier: Modifier, size: Int = 26) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val fontSize = with(density) { (maxHeight * 0.60f).toSp().value.coerceIn(1f, size.toFloat()).sp }
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Top) {
                Text(label, fontSize = 9.sp, lineHeight = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, fontSize = fontSize, lineHeight = fontSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}


private fun estimatedRemaining(eta: String, now: Long): String {
    // Only estimate when the ETA is unambiguous and reasonably near.
    val cleaned = eta.trim().replace(".", "").uppercase(Locale.US)
    val format = if (cleaned.endsWith("AM") || cleaned.endsWith("PM")) "h:mm a" else "H:mm"
    val normalized = cleaned.replace(Regex("""(?<=\d)(AM|PM)$"""), " $1")
    val parser = SimpleDateFormat(format, Locale.US).apply { isLenient = false }
    val parsed = runCatching { parser.parse(normalized) }.getOrNull() ?: return "--"
    val target = java.util.Calendar.getInstance().apply { time = parsed }
    val current = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val arrival = current.clone() as java.util.Calendar
    arrival.set(java.util.Calendar.HOUR_OF_DAY, target.get(java.util.Calendar.HOUR_OF_DAY))
    arrival.set(java.util.Calendar.MINUTE, target.get(java.util.Calendar.MINUTE))
    arrival.set(java.util.Calendar.SECOND, 0)
    arrival.set(java.util.Calendar.MILLISECOND, 0)
    if (arrival.timeInMillis < now - 60000) arrival.add(java.util.Calendar.DAY_OF_YEAR, 1)
    val minutes = ((arrival.timeInMillis - now + 59999) / 60000).coerceAtLeast(0)
    if (minutes > 720) return "--"
    return if (minutes >= 60) "~${minutes / 60}h ${minutes % 60}m" else "~$minutes min"
}

@Composable
internal fun NavDebugDialog(onClose: () -> Unit) {
    val raw by navDebug.collectAsState()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    AlertDialog(onDismissRequest = onClose, title = { Text("NAV DEBUG") }, text = {
        androidx.compose.foundation.text.selection.SelectionContainer {
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) { Text(raw, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }
        }
    }, confirmButton = { TextButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(raw)) }) { Text("COPY") } }, dismissButton = { TextButton(onClick = onClose) { Text("CLOSE") } })
}
