package com.example.hud_version_4

import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlin.math.*

@Composable
fun RecordedTripsScreen(unit: String, speed: Float?) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<File?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtime(); delay(500) } }
    val mph = unit == "MPH"
    val factor = if (mph) 2.2369363 else 3.6
    val divisor = if (mph) 1609.344 else 1000.0
    val distanceUnit = if (mph) "mi" else "km"
    fun duration(s: Long) = "%02d:%02d:%02d".format(s/3600, s/60%60, s%60)
    fun stats(m: Double, seconds: Long, top: Double) = "%.2f %s   •   %s\nTop %.1f %s   •   Avg %.1f %s".format(m/divisor, distanceUnit, duration(seconds), top*factor, unit, if(seconds>0) m/seconds*factor else 0.0, unit)
    val files = remember(TripState.revision, history) { TripState.folder(context).listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.name } ?: emptyList() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { history = !history; selected = null }) { Text(if(history) "LIVE TRIP" else "HISTORY") }
            if (!history) Button(onClick = {
                try {
                    val i = Intent(context, TripService::class.java)
                    if (TripState.active) context.startService(i.setAction("STOP")) else ContextCompat.startForegroundService(context, i)
                } catch(e: Exception) { TripState.error = e.message ?: "Cannot start recorder" }
            }) { Text(if(TripState.active) "STOP & SAVE" else "START TRIP") }
            if(history) OutlinedButton(onClick = { confirm = true }, enabled = files.isNotEmpty()) { Text(if(selected != null) "DELETE TRIP" else "DELETE ALL") }
        }
        if (TripState.error.isNotEmpty()) Text(TripState.error, color = Color(0xFFFF5555), fontSize = 12.sp)
        if (!history) {
            val seconds = if(TripState.active) ((now-TripState.started)/1000).coerceAtLeast(0) else 0L
            Row(Modifier.weight(1f).fillMaxWidth()) {
                DigitalSpeedometer(speed, unit, Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if(TripState.active) "RECORDING" else "READY", color = Color(0xFFFF3535))
                    Text(stats(TripState.meters, seconds, TripState.top.toDouble()), fontSize = 18.sp)
                }
            }
        } else if(selected == null) {
            LazyColumn(Modifier.weight(1f)) {
                items(files) { file ->
                    val data = remember(file) { runCatching { JSONObject(file.readText()) }.getOrNull() }
                    if(data != null) TextButton(onClick = { selected = file }) {
                        Text(DateFormat.getDateTimeInstance().format(Date(data.getLong("start"))) + "\n" + stats(data.getDouble("meters"),data.getLong("seconds"),data.getDouble("top")))
                    }
                }
            }
        } else {
            val data = remember(selected) { runCatching { JSONObject(selected!!.readText()) }.getOrNull() }
            if(data != null) {
                Text(stats(data.getDouble("meters"),data.getLong("seconds"),data.getDouble("top")), fontSize = 12.sp)
                val points = remember(data) { val a=data.getJSONArray("points"); (0 until a.length()).map { val p=a.getJSONArray(it); Triple(p.getDouble(0),p.getDouble(1),p.getLong(2)) } }
                Text("Recorded GPS trace • no street basemap", fontSize = 11.sp)
                Canvas(Modifier.weight(1f).fillMaxWidth()) {
                    if(points.isNotEmpty()) {
                        val lat=points.map{it.first}.average(); val c=cos(lat*PI/180).coerceAtLeast(0.01)
                        val xs=points.map{it.second*c}; val ys=points.map{it.first}; val x0=xs.minOrNull()!!; val y0=ys.minOrNull()!!
                        val dx=(xs.maxOrNull()!!-x0).coerceAtLeast(0.00001); val dy=(ys.maxOrNull()!!-y0).coerceAtLeast(0.00001)
                        val scale=min((size.width-24).coerceAtLeast(1f)/dx,(size.height-24).coerceAtLeast(1f)/dy)
                        val offsets=points.indices.map { Offset((size.width/2+(xs[it]-x0-dx/2)*scale).toFloat(),(size.height/2-(ys[it]-y0-dy/2)*scale).toFloat()) }
                        for(i in 1 until offsets.size) if(points[i].third-points[i-1].third <= 30000) drawLine(Color(0xFF4499FF),offsets[i-1],offsets[i],4f)
                        drawCircle(Color.Green,6f,offsets.first()); drawCircle(Color.Red,6f,offsets.last())
                    }
                }
                TextButton(onClick = { selected = null }) { Text("BACK TO HISTORY") }
            }
        }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false}, title={Text("Delete saved trip data?")}, confirmButton={TextButton(onClick={ if(selected!=null) selected?.delete() else files.forEach{it.delete()}; selected=null; TripState.revision++; confirm=false }){Text("DELETE")}}, dismissButton={TextButton(onClick={confirm=false}){Text("CANCEL")}})
}
