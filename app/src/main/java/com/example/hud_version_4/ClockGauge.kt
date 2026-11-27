package com.example.hud_version_4

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.*

@Composable
fun ClockGauge(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Calendar.getInstance()) }
    LaunchedEffect(Unit) { while (true) { now = Calendar.getInstance(); delay(250) } }
    val context = LocalContext.current
    val font = remember { ResourcesCompat.getFont(context, R.font.orbitron) }
    Canvas(modifier) {
        val r = size.minDimension * .48f
        val c = center
        drawCircle(Brush.linearGradient(listOf(Color.LightGray, Color.DarkGray, Color.White)), r, c)
        drawCircle(Color(0xFF101010), r*.95f,c)
        for (i in 0 until 60) {
            val a = i * PI / 30 - PI/2
            fun point(k: Float) = c + Offset((cos(a)*r*k).toFloat(),(sin(a)*r*k).toFloat())
            drawLine(Color.White,point(.91f),point(if(i%5==0) .80f else .87f),r*if(i%5==0) .025f else .009f)
            if(i%5==0) drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize=r*.14f; typeface=font; textAlign=android.graphics.Paint.Align.CENTER }
                val q=point(.67f)
                canvas.nativeCanvas.drawText(if(i==0) "12" else (i/5).toString(),q.x,q.y-(paint.ascent()+paint.descent())/2,paint)
            }
        }
        fun hand(angle: Float, length: Float, width: Float, color: Color) {
            rotate(angle,c) { drawLine(color,c+Offset(0f,r*.19f),c-Offset(0f,r*length),r*width,StrokeCap.Round) }
        }
        val minute=now.get(Calendar.MINUTE)+now.get(Calendar.SECOND)/60f
        hand((now.get(Calendar.HOUR)+minute/60)*30,.48f,.045f,Color.White)
        hand(minute*6,.72f,.025f,Color.White)
        hand(now.get(Calendar.SECOND)*6f,.86f,.012f,Color(0xFFFF3535))
        drawCircle(Color.Gray,r*.065f,c)
    }
}

@Composable
fun ToolsScreen(gps: GPSManager) {
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("GPS TOOLS",color=Color(0xFFFF3535),fontSize=18.sp)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            DataPanel("ACCURACY",gps.accuracy?.let { "%.1f m".format(it) } ?: "--",Modifier.weight(1f))
            DataPanel("ALTITUDE",if(gps.gpsStatus=="GPS ACTIVE") gps.altitude?.let { "%.0f m".format(it) } ?: "--" else "--",Modifier.weight(1f))
        }
        Text(if(gps.gpsStatus=="GPS ACTIVE") "LAT ${gps.latitude}  /  LON ${gps.longitude}" else "Waiting for GPS fix",color=Color.White,fontSize=12.sp)
        Text("OBD-II support pending",color=Color.Gray,fontSize=12.sp)
    }
}
