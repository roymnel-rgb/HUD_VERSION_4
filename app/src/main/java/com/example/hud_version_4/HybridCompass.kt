package com.example.hud_version_4

import android.content.Context
import android.hardware.*
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlin.math.*

@Composable
internal fun rememberSensorHeading(): Float? {
    val context = LocalContext.current
    var heading by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(context) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val window = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val rotation = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) heading = null
            }
            override fun onSensorChanged(event: SensorEvent) {
                if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) { heading = null; return }
                val matrix = FloatArray(9)
                val adjusted = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                @Suppress("DEPRECATION")
                val axes = when (window.defaultDisplay.rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                if (!SensorManager.remapCoordinateSystem(matrix, axes.first, axes.second, adjusted)) return
                val raw = ((Math.toDegrees(SensorManager.getOrientation(adjusted, FloatArray(3))[0].toDouble()) + 360) % 360).toFloat()
                val previous = heading
                heading = if (previous == null) raw else (previous + 0.2f * ((raw - previous + 540f) % 360f - 180f) + 360f) % 360f
            }
        }
        if (rotation != null) manager.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager.unregisterListener(listener) }
    }
    return heading
}
