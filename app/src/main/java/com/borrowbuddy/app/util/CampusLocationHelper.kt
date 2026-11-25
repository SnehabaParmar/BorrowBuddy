package com.borrowbuddy.app.util

import android.location.Location
import com.borrowbuddy.app.model.CampusItem
import kotlin.math.roundToInt

data class CampusZone(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val department: String = "All"
)

object CampusLocationHelper {

    val CAMPUS_ZONES = listOf(
        CampusZone("IMBA", "IMBA Block", 23.0365, 72.5450, "IMBA"),
        CampusZone("IMSC", "IMSc.IT Lab", 23.0372, 72.5462, "IMSc.IT"),
        CampusZone("LIB", "Main Library", 23.0380, 72.5448, "All"),
        CampusZone("CANTEEN", "Campus Canteen", 23.0360, 72.5458, "All"),
        CampusZone("SCI", "Science Block", 23.0390, 72.5470, "IMSc.IT"),
        CampusZone("CLOCK", "Clock Tower", 23.0375, 72.5440, "All"),
        CampusZone("GARDEN", "Campus Garden", 23.0355, 72.5445, "All"),
        CampusZone("ADMIN", "Main Office", 23.0385, 72.5435, "All"),
        CampusZone("SPORTS", "Sports Ground", 23.0350, 72.5475, "All")
    )

    // Gujarat University campus center default coordinates
    const val CAMPUS_CENTER_LAT = 23.0368
    const val CAMPUS_CENTER_LON = 72.5452

    fun findZone(name: String): CampusZone? {
        val normalized = name.lowercase()
        return CAMPUS_ZONES.firstOrNull { 
            normalized.contains(it.name.lowercase()) || normalized.contains(it.id.lowercase()) 
        }
    }

    private fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    /**
     * Calculates walking time in minutes based on average 80 meters per minute walk speed.
     */
    fun calculateWalkMinutes(userLat: Double?, userLon: Double?, zoneName: String): Int {
        val cleanZone = if (zoneName.contains("•")) {
            zoneName.split("•").first().trim()
        } else {
            zoneName.trim()
        }
        val zone = findZone(cleanZone) ?: CAMPUS_ZONES[0]
        val lat = userLat ?: CAMPUS_CENTER_LAT
        val lon = userLon ?: CAMPUS_CENTER_LON

        val meters = try {
            val result = FloatArray(1)
            Location.distanceBetween(lat, lon, zone.latitude, zone.longitude, result)
            result[0].toDouble()
        } catch (_: Exception) {
            haversineDistanceMeters(lat, lon, zone.latitude, zone.longitude)
        }
        return (meters / 80.0).roundToInt().coerceIn(1, 20)
    }

    fun isNearby(userLat: Double?, userLon: Double?, zoneName: String, maxMinutes: Int = 5): Boolean {
        return calculateWalkMinutes(userLat, userLon, zoneName) <= maxMinutes
    }

    fun formatZoneWithWalkTime(userLat: Double?, userLon: Double?, zoneName: String): String {
        val cleanZone = if (zoneName.contains("•")) {
            zoneName.split("•").first().trim()
        } else {
            zoneName.trim()
        }
        val mins = calculateWalkMinutes(userLat, userLon, cleanZone)
        return "$cleanZone • ${mins}m"
    }

    fun sortItemsByProximity(
        items: List<CampusItem>,
        userLat: Double?,
        userLon: Double?
    ): List<CampusItem> {
        return items.sortedBy { item ->
            calculateWalkMinutes(userLat, userLon, item.pickupSpot)
        }
    }
}
