package com.example.hud_version_4

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow

internal data class LaneReading(val arrows: String, val recommended: Boolean)
internal data class NavigationReading(val eta: String = "--:--", val time: String = "--", val distance: String = "--", val instruction: String = "", val active: Boolean = false, val lanes: List<LaneReading> = emptyList(), val laneHint: String = "", val estimatedDistanceMetres: Int? = null)
internal val navigationReading = MutableStateFlow(NavigationReading())
internal val navDebug = MutableStateFlow("Waiting for Google Maps notifications")

class MapsNotificationService : NotificationListenerService() {
    override fun onListenerConnected() { refresh() }
    override fun onListenerDisconnected() { navigationReading.value = NavigationReading(); navDebug.value = "Notification listener disconnected" }
    override fun onNotificationPosted(sbn: StatusBarNotification) { if (sbn.packageName == "com.google.android.apps.maps") refresh() }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { if (sbn.packageName == "com.google.android.apps.maps") refresh() }
    private fun refresh() {
        val maps = runCatching { activeNotifications.toList() }.getOrDefault(emptyList()).filter { it.packageName == "com.google.android.apps.maps" }.sortedByDescending { it.postTime }
        val routes = maps.filter { it.notification.category == Notification.CATEGORY_NAVIGATION && it.isOngoing }
        navDebug.value = routes.joinToString("\n\n") { sbn ->
            val n = sbn.notification
            "category=${n.category}; ongoing=${sbn.isOngoing}\n" + "Candidate distance: ${progressDistance(n)?.let { "$it m / ${formatEstimatedDistance(it, "MPH")}" } ?: "unavailable"}\n" + n.extras.keySet().filter { it.startsWith("android.") && it != "android.appInfo" }.sorted().joinToString("\n") { key ->
                @Suppress("DEPRECATION")
                val v = runCatching { n.extras.get(key) }.getOrNull()
                "$key: " + when (v) { is CharSequence -> v.toString(); is Array<*> -> v.filterIsInstance<CharSequence>().joinToString(" | "); is Number, is Boolean -> v.toString(); is List<*> -> v.take(30).joinToString(" | ") { describeProgressItem(it) }; else -> v?.javaClass?.simpleName.orEmpty() }
            }
        }.ifBlank { "No active Google Maps notifications" }.take(20000)
        val readings = routes.mapNotNull { sbn ->
            val n = sbn.notification
            val e = n.extras
            val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = listOf(Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_SUMMARY_TEXT, Notification.EXTRA_INFO_TEXT, Notification.EXTRA_TITLE_BIG).mapNotNull { e.getCharSequence(it)?.toString() } + (e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() } ?: emptyList())
            val additional = e.keySet().filter { key -> Regex("(?i)(distance|duration|arrival|remaining|eta)").containsMatchIn(key) }.mapNotNull { key ->
                @Suppress("DEPRECATION")
                val value = runCatching { e.get(key) }.getOrNull()
                if (value is CharSequence) "$key: $value" else null
            }
            val instruction = title.ifBlank { e.getCharSequence("android.ongoingActivityNoti.nowbarPrimaryInfo")?.toString().orEmpty() }
            parseNavigation(instruction, text + additional, true)?.let { reading ->
                reading.copy(estimatedDistanceMetres = if (reading.distance == "--") progressDistance(n) else null)
            }
        }
        navigationReading.value = readings.maxByOrNull { (if (it.eta != "--:--") 2 else 0) + (if (it.time != "--") 2 else 0) + (if (it.distance != "--" || it.estimatedDistanceMetres != null) 2 else 0) + (if (it.instruction.isNotBlank()) 1 else 0) } ?: NavigationReading()
    }
}

internal fun parseNavigation(title: String, text: List<String>, navigationCategory: Boolean): NavigationReading? {
    val duration = Regex("""(?i)\b(?:\d+\s*(?:hours?|hrs?|h)\b(?:\s*\d+\s*(?:minutes?|mins?|m)\b)?|\d+\s*(?:minutes?|mins?)\b)""")
    val distance = Regex("""(?i)\b\d+(?:[.,]\d+)?\s*(?:miles?|mi|kilometers?|km|feet|ft|meters?|metres?|m)\b""")
    val eta = Regex("""(?i)\b(?:[01]?\d|2[0-3]):[0-5]\d(?:\s*[ap]\.?m\.?)?""")
    val fields = (text + title).map { it.replace('\u00a0', ' ').replace('\u202f', ' ') }.distinct()
    val blob = fields.joinToString("\n")
    val arrival = Regex("""(?i)(?:arriv(?:e|al|es)(?:\s+(?:at|by))?|eta)\s*[: ]\s*(${eta.pattern.removePrefix("(?i)")})""").find(blob)?.groupValues?.get(1) ?: eta.find(blob)?.value
    val time = duration.find(blob)?.value
    val d = distance.pattern.removePrefix("(?i)")
    val explicit = Regex("""(?i)(?:remaining(?:\s+distance)?|distance left|to destination)\s*[: ]\s*($d)|($d)\s*(?:remaining|left|to destination)""").find(blob)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
    val paired = Regex("""(?i)${duration.pattern.removePrefix("(?i)")}\s*[(·•,]\s*($d)""").find(blob)?.groupValues?.get(1)
    val summary = fields.firstOrNull { duration.containsMatchIn(it) && !Regex("""(?i)\b(turn|exit|keep|merge)\b""").containsMatchIn(it) }
    val routeUnits = Regex("""(?i)\b\d+(?:[.,]\d+)?\s*(?:miles?|mi|kilometers?|km)\b""")
    val reversePair = Regex("""(?i)(${routeUnits.pattern.removePrefix("(?i)")})\s*[·•,|()–—-]+\s*${duration.pattern.removePrefix("(?i)")}""").find(blob)?.groupValues?.get(1)
    val arrivalSummary = fields.filter { eta.containsMatchIn(it) && !Regex("""(?i)\b(turn|exit|keep|merge|head|continue|in)\b""").containsMatchIn(it) }
        .flatMap { routeUnits.findAll(it).map { m -> m.value }.toList() }.distinct().singleOrNull()
    val routeDistance = explicit ?: paired ?: reversePair ?: summary?.let { distance.findAll(it).map { m -> m.value }.distinct().toList().singleOrNull() } ?: arrivalSummary
    if (!navigationCategory && (arrival == null && (time == null || routeDistance == null))) return null
    val hint = fields.firstOrNull { Regex("""(?i)\b(?:use|stay in|keep to|take)\b.*\blanes?\b""").containsMatchIn(it) }.orEmpty()
    return NavigationReading(arrival ?: "--:--", time ?: "--", routeDistance ?: "--", title, true, laneHint = hint)
}

// Experimental: Google Maps appears to use metres for ProgressStyle on the tested route.
// Android does not guarantee this unit. Never present this as a verified distance.
private fun progressDistance(n: Notification): Int? {
    val e = n.extras
    val template = e.getString("android.template").orEmpty()
    if (!template.endsWith("ProgressStyle") || e.getBoolean("android.progressIndeterminate")) return null
    if (!e.containsKey("android.progress") || !e.containsKey("android.progressMax")) return null
    val max = e.getInt("android.progressMax")
    val progress = e.getInt("android.progress")
    if (max <= 0 || progress !in 0..max) return null
    return max - progress
}

internal fun formatEstimatedDistance(metres: Int, unit: String): String {
    val miles = unit.equals("MPH", ignoreCase = true)
    return String.format(java.util.Locale.US, "~%.1f %s", metres / (if (miles) 1609.344 else 1000.0), if (miles) "mi" else "km")
}

// Read only public, zero-argument progress getters when present on this Android version.
private fun describeProgressItem(item: Any?): String {
    if (item == null) return "null"
    val values = listOf("getPosition", "getLength", "getColor").mapNotNull { name ->
        runCatching { item.javaClass.getMethod(name).invoke(item) }.getOrNull()?.let { "$name=$it" }
    }
    return item.javaClass.simpleName + values.joinToString(prefix = "(", postfix = ")")
}
