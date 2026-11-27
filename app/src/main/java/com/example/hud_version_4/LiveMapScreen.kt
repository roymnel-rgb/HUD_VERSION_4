package com.example.hud_version_4

import android.location.Address
import android.location.Geocoder
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.Marker
import java.util.Locale

@Suppress("DEPRECATION")
@Composable
fun LiveMapScreen(gps: GPSManager, unit: String, speed: Float?) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val view = remember(context) { MapLibre.getInstance(context); MapView(context).apply { onCreate(null) } }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var follow by remember { mutableStateOf(true) }
    var centered by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Address>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var marker by remember { mutableStateOf<Marker?>(null) }
    var destination by remember { mutableStateOf("") }
    fun search() {
        val term = query.trim()
        if (busy || term.length < 3) return
        keyboard?.hide()
        busy = true
        results = emptyList()
        message = "Searching…"
        scope.launch {
            try {
                val found = withContext(Dispatchers.IO) {
                    if (!Geocoder.isPresent()) throw IllegalStateException("Address search is unavailable on this device")
                    Geocoder(context, Locale.getDefault()).getFromLocationName(term, 8).orEmpty()
                        .filter { it.hasLatitude() && it.hasLongitude() }
                }
                results = found
                message = if (found.isEmpty()) "No matches. Try adding city and state." else "Select a destination"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "Search unavailable. Check your connection and try again." }
            finally { busy = false }
        }
    }
    DisposableEffect(view, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when(event) {
                Lifecycle.Event.ON_START -> view.onStart()
                Lifecycle.Event.ON_RESUME -> view.onResume()
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_STOP -> view.onStop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        view.getMapAsync { m ->
            m.setStyle(Style.Builder().fromUri("https://demotiles.maplibre.org/style.json")) { map = m }
        }
        onDispose { owner.lifecycle.removeObserver(observer); if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.onPause(); if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) view.onStop(); view.onDestroy() }
    }
    LaunchedEffect(map,gps.latitude,gps.longitude,gps.gpsStatus,follow) {
        val lat=gps.latitude; val lon=gps.longitude
        if(follow && gps.gpsStatus=="GPS ACTIVE" && lat!=null && lon!=null) {
            map?.let { m ->
                if(!centered) { m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat,lon), 14.0)); centered=true }
                else m.moveCamera(CameraUpdateFactory.newLatLng(LatLng(lat,lon)))
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(value=query, onValueChange={query=it}, modifier=Modifier.weight(1f), singleLine=true,
                placeholder={Text("Address or place")}, enabled=!busy,
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search), keyboardActions=KeyboardActions(onSearch={search()}))
            Button(onClick={search()}, enabled=!busy && query.trim().length>=3) { Text("SEARCH") }
            TextButton(onClick={follow=true; centered=false; results=emptyList(); message=""; val lat=gps.latitude; val lon=gps.longitude; if(gps.gpsStatus=="GPS ACTIVE" && lat!=null && lon!=null) map?.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat,lon),14.0))}) { Text("MY GPS") }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("${speed?.toInt() ?: "--"} $unit")
            TextButton(onClick={follow=!follow}) { Text(if(follow) "FOLLOW ON" else "FOLLOW OFF") }
        }
        if(message.isNotEmpty()) Text(message, style=MaterialTheme.typography.labelSmall)
        if(results.isNotEmpty()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(results) { address ->
                    val label = address.getAddressLine(0) ?: address.featureName ?: "Destination"
                    TextButton(enabled=map!=null, onClick={
                        map?.let { m ->
                            follow=false
                            marker?.let { m.removeMarker(it) }
                            val point=LatLng(address.latitude,address.longitude)
                            marker=m.addMarker(MarkerOptions().position(point).title(label))
                            m.moveCamera(CameraUpdateFactory.newLatLngZoom(point,16.0))
                            destination=label
                            results=emptyList()
                            message=""
                        }
                    }) { Text(label) }
                }
            }
            TextButton(onClick={results=emptyList(); message=""}) { Text("CLOSE RESULTS") }
        }
        AndroidView(factory={view},modifier=Modifier.weight(1f).fillMaxWidth())
        if(destination.isNotEmpty()) Text(destination, style=MaterialTheme.typography.labelSmall, maxLines=1)
        Text("MapLibre demo map • ${gps.gpsStatus}",style=MaterialTheme.typography.labelSmall)
    }
}
