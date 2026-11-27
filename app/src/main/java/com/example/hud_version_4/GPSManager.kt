
package com.example.hud_version_4

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

class GPSManager(context: Context) {

    private val appContext = context.applicationContext

    private val locationManager =
        appContext.getSystemService(
            Context.LOCATION_SERVICE
        ) as LocationManager

    var latitude by mutableStateOf<Double?>(null)
        private set
    var longitude by mutableStateOf<Double?>(null)
        private set
    var altitude by mutableStateOf<Double?>(null)
        private set

    var speedMps by mutableStateOf<Float?>(null)
        private set

    var bearing by mutableStateOf<Float?>(null)
        private set

    var accuracy by mutableStateOf<Float?>(null)
        private set

    var gpsStatus by mutableStateOf("GPS STANDBY")
        private set

    private var lastFixTime = 0L
    private var listening = false

    private val listener = object : LocationListener {

        override fun onLocationChanged(location: Location) {

            lastFixTime = SystemClock.elapsedRealtime()

            accuracy = if (location.hasAccuracy()) {
                location.accuracy
            } else {
                null
            }

            if (location.hasAccuracy() &&
                location.accuracy > 50f
            ) {
                speedMps = null
                bearing = null
                gpsStatus = "WEAK GPS"
                return
            }

            speedMps = if (location.hasSpeed()) {
                location.speed.coerceAtLeast(0f)
            } else {
                null
            }

            bearing = if (
                location.hasBearing() &&
                (speedMps ?: 0f) > 1.5f
            ) {
                location.bearing
            } else {
                null
            }

            latitude = location.latitude
            longitude = location.longitude
            altitude = if (location.hasAltitude()) location.altitude else null
            gpsStatus = "GPS ACTIVE"
        }

        override fun onProviderDisabled(provider: String) {
            speedMps = null
            bearing = null
            gpsStatus = "GPS DISABLED"
        }

        override fun onProviderEnabled(provider: String) {
            gpsStatus = "ACQUIRING GPS"
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {

        if (listening) return

        val permissionGranted =
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

        if (!permissionGranted) {
            gpsStatus = "LOCATION PERMISSION REQUIRED"
            return
        }

        try {

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                listener,
                Looper.getMainLooper()
            )

            listening = true

            gpsStatus = if (
                locationManager.isProviderEnabled(
                    LocationManager.GPS_PROVIDER
                )
            ) {
                "ACQUIRING GPS"
            } else {
                "GPS DISABLED"
            }

        } catch (e: Exception) {

            gpsStatus = "GPS UNAVAILABLE"
        }
    }

    fun checkFreshness() {

        if (
            lastFixTime != 0L &&
            SystemClock.elapsedRealtime() -
            lastFixTime > 10000L
        ) {
            speedMps = null
            bearing = null
            accuracy = null

            if (gpsStatus != "GPS DISABLED") {
                gpsStatus = "GPS SIGNAL LOST"
            }
        }
    }

    fun stop() {

        locationManager.removeUpdates(listener)

        listening = false
        lastFixTime = 0L
        speedMps = null
        bearing = null
        accuracy = null
        gpsStatus = "GPS STANDBY"
    }
}

@Composable
fun MonitorGPS(gps: GPSManager) {

    LaunchedEffect(gps) {

        while (true) {

            gps.checkFreshness()

            delay(1000L)
        }
    }
}
