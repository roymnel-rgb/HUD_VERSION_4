
package com.example.hud_version_4

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val HudBlack = Color(0xFF050505)
private val HudPanel = Color(0xFF141414)
private val HudRed = Color(0xFFFF3535)
private val HudWhite = Color(0xFFF5F5F5)
private val HudGray = Color(0xFF999999)

private val Orbitron = FontFamily(
    Font(R.font.orbitron)
)

class MainActivity : ComponentActivity() {

    private lateinit var gps: GPSManager

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            gps.start()
        }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        gps = GPSManager(this)
        if (
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val prefs = getSharedPreferences(
            "hud_settings",
            MODE_PRIVATE
        )

        setContent {

            MonitorGPS(gps)

            MaterialTheme(
                typography = hudTypography(),
                colorScheme = darkColorScheme(
                    primary = HudRed,
                    background = HudBlack,
                    surface = HudPanel,
                    onBackground = HudWhite,
                    onSurface = HudWhite
                )
            ) {

                HudApplication(
                    gps = gps,
                    initialMph = prefs.getBoolean(
                        "mph", true
                    ),
                    initialDigital = prefs.getBoolean(
                        "digital", false
                    ),
                    onUnitChanged = {
                        prefs.edit()
                            .putBoolean("mph", it)
                            .apply()
                    },
                    onDisplayChanged = {
                        prefs.edit()
                            .putBoolean("digital", it)
                            .apply()
                    },
                    requestLocation = {

                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission
                                    .ACCESS_FINE_LOCATION,
                                Manifest.permission
                                    .ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        gps.start()
    }

    override fun onStop() {
        gps.stop()
        super.onStop()
    }
}

@Composable
fun HudApplication(
    gps: GPSManager,
    initialMph: Boolean,
    initialDigital: Boolean,
    onUnitChanged: (Boolean) -> Unit,
    onDisplayChanged: (Boolean) -> Unit,
    requestLocation: () -> Unit
) {

    var selectedMode by remember {
        mutableStateOf(0)
    }

    var useMph by remember {
        mutableStateOf(initialMph)
    }

    var digitalMode by remember {
        mutableStateOf(initialDigital)
    }

    val unit = if (useMph) "MPH" else "KM/H"

    val speed = gps.speedMps?.let {

        if (useMph) {
            it * 2.2369363f
        } else {
            it * 3.6f
        }
    }

    val modes = listOf(
        "DRIVE",
        "TRIPS",
        "TOOLS",
        "MAPS"
    )

    val safe = WindowInsets.safeDrawing
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val side = with(density) { maxOf(safe.getLeft(density, direction), safe.getRight(density, direction)).toDp() }
    val context = LocalContext.current
    val sensorHeading = rememberSensorHeading()
    val hybridHeading = gps.bearing ?: sensorHeading
    var showNavDebug by remember { mutableStateOf(false) }
    if (showNavDebug) NavDebugDialog { showNavDebug = false }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudBlack)
            .windowInsetsPadding(safe.only(WindowInsetsSides.Vertical))
            .padding(horizontal = side + 6.dp, vertical = 6.dp)
    ) {

        Text("HUD V4.4.0", color = HudRed, fontSize = 14.sp,
            fontFamily = Orbitron, modifier = Modifier.padding(bottom = 4.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {

            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                    val driveSpeedWidth = ((maxWidth - 8.dp) / 2f).coerceAtLeast(0.dp)
                    CompositionLocalProvider(LocalDigitalSpeedWidth provides driveSpeedWidth) {
                    when (selectedMode) {
                        0 -> DriveScreen(unit, speed, digitalMode, hybridHeading)
                        1 -> RecordedTripsScreen(unit, speed)
                        2 -> ToolsScreen(gps)
                        3 -> NavigationDashboard(unit, speed)
                    }
                    }
                }
                Column(
                    Modifier.width(144.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val heading = hybridHeading?.let {
                        listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[( ((it + 22.5f) / 45f).toInt() % 8 + 8) % 8]
                    } ?: "--"
                    Text("HEADING  $heading", color = HudWhite, fontFamily = Orbitron, fontSize = 13.sp)
                    TextButton(onClick = requestLocation, modifier = Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(0.dp)) {
                        Text(gps.gpsStatus, color = if (gps.gpsStatus == "GPS ACTIVE") Color(0xFF66DD88) else HudGray, fontSize = 11.sp)
                    }
                    if (selectedMode == 0) {
                        OutlinedButton(onClick = { digitalMode = !digitalMode; onDisplayChanged(digitalMode) }, modifier = Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(0.dp)) {
                            Text(if (digitalMode) "DIGITAL" else "ANALOGUE", fontSize = 12.sp)
                        }
                    }
                    OutlinedButton(onClick = { useMph = !useMph; onUnitChanged(useMph) }, modifier = Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(0.dp)) {
                        Text(unit, color = HudWhite)
                    }
                    if (selectedMode == 2) {
                        TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, contentPadding = PaddingValues(0.dp)) {
                            Text("NOTIFICATION ACCESS", fontSize = 10.sp)
                        }
                        TextButton(onClick = { showNavDebug = true }, contentPadding = PaddingValues(0.dp)) { Text("NAV DEBUG", fontSize = 10.sp) }
                    }
                }
            }
        }

        if (selectedMode == 3) NavigationGuidance()

        Row(
            modifier = Modifier.fillMaxWidth().background(HudPanel, RoundedCornerShape(8.dp)),
            horizontalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {

            modes.forEachIndexed { index, name ->

                Button(
                    onClick = {
                        selectedMode = index
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (selectedMode == index) {
                                HudRed
                            } else {
                                HudPanel
                            },
                        contentColor = HudWhite
                    )
                ) {

                    Text(
                        text = name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun DriveScreen(
    unit: String,
    speed: Float?,
    digitalMode: Boolean,
    bearing: Float?
) {
    val isMph = unit == "MPH"
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        if (digitalMode) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClockGauge(Modifier.weight(1f).fillMaxHeight().clipToBounds())
                DigitalSpeedometer(
                    speed, unit,
                    Modifier.weight(1f).fillMaxHeight().clipToBounds(),
                    reserveThreeDigits = true
                )
            }
        } else {
            val diameter = minOf(maxHeight * 0.98f, (maxWidth - 6.dp).coerceAtLeast(0.dp) / 2f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                ClockGauge(Modifier.size(diameter).clipToBounds())
                AnalogGauge(value = speed, maxValue = if (isMph) 160f else 240f,
                    majorStep = if (isMph) 20 else 30, title = "SPEED", unit = unit,
                    modifier = Modifier.size(diameter).clipToBounds())
            }
        }
    }
}

@Composable
fun CompassDisplay(bearing: Float?) {

    val directions = listOf(
        "N", "NE", "E", "SE",
        "S", "SW", "W", "NW"
    )

    val heading = bearing?.let {

        val index =
            ((it + 22.5f) / 45f).toInt() % 8

        directions[index]
    }

    Row(
        modifier = Modifier
            .background(
                HudPanel,
                RoundedCornerShape(6.dp)
            )
            .padding(
                horizontal = 20.dp,
                vertical = 2.dp
            ),
        horizontalArrangement =
            Arrangement.spacedBy(16.dp),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text = "HEADING",
            color = HudGray,
            fontSize = 11.sp
        )

        Text(
            text = heading ?: "--",
            color = HudWhite,
            fontFamily = Orbitron,
            fontSize = 20.sp
        )

        Text(
            text = bearing?.let {
                "${it.roundToInt() % 360}°"
            } ?: "--°",
            color = HudWhite,
            fontFamily = Orbitron,
            fontSize = 16.sp
        )
    }
}

@Composable
fun TripScreen(
    unit: String,
    speed: Float?
) {

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement =
            Arrangement.spacedBy(12.dp),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        DigitalSpeedometer(
            speed = speed,
            unit = unit,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                DataPanel(
                    "DISTANCE",
                    "--",
                    Modifier.weight(1f)
                )

                DataPanel(
                    "TOP SPEED",
                    "--",
                    Modifier.weight(1f)
                )
            }

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                DataPanel(
                    "DURATION",
                    "--:--",
                    Modifier.weight(1f)
                )

                DataPanel(
                    "AVERAGE",
                    "--",
                    Modifier.weight(1f)
                )
            }

            Text(
                text = "Trip recording coming next",
                color = HudGray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun OBDScreen() {

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {

        Text(
            text = "OBD-II DISCONNECTED",
            color = HudRed,
            fontWeight = FontWeight.Bold
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            DataPanel(
                "RPM", "--",
                Modifier.weight(1f)
            )

            DataPanel(
                "COOLANT", "-- °C",
                Modifier.weight(1f)
            )

            DataPanel(
                "VOLTAGE", "-- V",
                Modifier.weight(1f)
            )
        }

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            DataPanel(
                "THROTTLE", "-- %",
                Modifier.weight(1f)
            )

            DataPanel(
                "LOAD", "-- %",
                Modifier.weight(1f)
            )

            DataPanel(
                "FUEL", "-- %",
                Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun MapsScreen(
    unit: String,
    speed: Float?
) {

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement =
            Arrangement.spacedBy(6.dp)
    ) {

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {

                DataPanel("NEXT TURN", "--")
                DataPanel("TIME", "--:--")
            }

            DigitalSpeedometer(
                speed = speed,
                unit = unit,
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {

                DataPanel("TIME LEFT", "--:--")
                DataPanel("ETA", "--:--")
                DataPanel("DISTANCE LEFT", "--")
            }
        }

        DataPanel(
            label = "ONTO ROAD",
            value = "Navigation not connected",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun DataPanel(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier
            .background(
                HudPanel,
                RoundedCornerShape(8.dp)
            )
            .padding(8.dp),
        verticalArrangement =
            Arrangement.spacedBy(3.dp)
    ) {

        Text(
            text = label,
            color = HudGray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = value,
            color = HudWhite,
            fontSize = 20.sp,
            fontFamily = Orbitron
        )
    }
}

private fun hudTypography(): Typography {
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = Orbitron),
        displayMedium = t.displayMedium.copy(fontFamily = Orbitron),
        displaySmall = t.displaySmall.copy(fontFamily = Orbitron),
        headlineLarge = t.headlineLarge.copy(fontFamily = Orbitron),
        headlineMedium = t.headlineMedium.copy(fontFamily = Orbitron),
        headlineSmall = t.headlineSmall.copy(fontFamily = Orbitron),
        titleLarge = t.titleLarge.copy(fontFamily = Orbitron),
        titleMedium = t.titleMedium.copy(fontFamily = Orbitron),
        titleSmall = t.titleSmall.copy(fontFamily = Orbitron),
        bodyLarge = t.bodyLarge.copy(fontFamily = Orbitron),
        bodyMedium = t.bodyMedium.copy(fontFamily = Orbitron),
        bodySmall = t.bodySmall.copy(fontFamily = Orbitron),
        labelLarge = t.labelLarge.copy(fontFamily = Orbitron),
        labelMedium = t.labelMedium.copy(fontFamily = Orbitron),
        labelSmall = t.labelSmall.copy(fontFamily = Orbitron)
    )
}
