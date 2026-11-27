package com.example.hud_version_4

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.location.*
import android.os.*
import androidx.compose.runtime.*
import org.json.*
import java.io.File

object TripState {
    var active by mutableStateOf(false)
    var meters by mutableStateOf(0.0)
    var top by mutableStateOf(0f)
    var started by mutableStateOf(0L)
    var error by mutableStateOf("")
    var revision by mutableStateOf(0)
    fun folder(c: Context) = File(c.filesDir, "trips").apply { mkdirs() }
}
class TripService : Service(), LocationListener {
    private lateinit var manager: LocationManager
    private var points = JSONArray()
    private var last: Location? = null
    private var wallStart = 0L
    override fun onBind(intent: Intent?) : IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { finishTrip(); return START_NOT_STICKY }
        if (TripState.active) return START_NOT_STICKY
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            TripState.error = "Grant precise location first"; stopSelf(); return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("trip", "Trip recording", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 1, Intent(this, TripService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "trip") else Notification.Builder(this)
        startForeground(41, builder.setSmallIcon(android.R.drawable.ic_menu_mylocation).setContentTitle("HUD trip recording").setContentText("GPS route is being recorded").setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop & save", stop).build())
        manager = getSystemService(LOCATION_SERVICE) as LocationManager
        points = JSONArray(); last = null; wallStart = System.currentTimeMillis()
        TripState.started = SystemClock.elapsedRealtime(); TripState.meters = 0.0; TripState.top = 0f; TripState.error = ""; TripState.active = true
        try { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper()) }
        catch (e: Exception) { TripState.error = "GPS unavailable: ${e.message}"; TripState.active = false; stopSelf() }
        return START_NOT_STICKY
    }
    override fun onLocationChanged(location: Location) {
        if (!TripState.active || !location.hasAccuracy() || location.accuracy > 35f) return
        if (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos > 10_000_000_000L) return
        val prev = last
        if (prev != null) {
            val dt = (location.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1e9
            if (dt <= 0) return
            val d = prev.distanceTo(location)
            if (d / dt > 100) return
            if (dt <= 30 && d >= 3 && (!location.hasSpeed() || location.speed > 0.7f)) TripState.meters += d
            if (d < 3 && dt < 15) return
        }
        if (location.hasSpeed()) TripState.top = maxOf(TripState.top, location.speed)
        points.put(JSONArray().put(location.latitude).put(location.longitude).put(location.time))
        last = Location(location)
    }
    override fun onProviderDisabled(provider: String) { TripState.error = "GPS disabled — route may have gaps"; last = null }
    override fun onProviderEnabled(provider: String) { TripState.error = "" }
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    private fun finishTrip() {
        if (TripState.active) {
            val data = JSONObject().put("start", wallStart).put("end", System.currentTimeMillis()).put("seconds", (SystemClock.elapsedRealtime()-TripState.started)/1000).put("meters", TripState.meters).put("top", TripState.top.toDouble()).put("points", points)
            try { File(TripState.folder(this), "$wallStart.json").writeText(data.toString()); TripState.revision++ }
            catch (e: Exception) { TripState.error = "Save failed: ${e.message}" }
        }
        TripState.active = false; stopForeground(true); stopSelf()
    }
    override fun onDestroy() { if (::manager.isInitialized) manager.removeUpdates(this); TripState.active = false; super.onDestroy() }
}
