package com.borrowbuddy.app

import com.borrowbuddy.app.model.CampusItem
import com.borrowbuddy.app.util.CampusLocationHelper
import org.junit.Assert.*
import org.junit.Test

class CampusLocationHelperTest {

    @Test
    fun testCampusZonesAreDefined() {
        assertTrue("Campus zones list should not be empty", CampusLocationHelper.CAMPUS_ZONES.isNotEmpty())
        assertTrue("Should include Library", CampusLocationHelper.CAMPUS_ZONES.any { it.name.contains("Library") })
        assertTrue("Should include Canteen", CampusLocationHelper.CAMPUS_ZONES.any { it.name.contains("Canteen") })
    }

    @Test
    fun testFindZone() {
        val lib = CampusLocationHelper.findZone("Main Library")
        assertNotNull(lib)
        assertEquals("LIB", lib?.id)

        val canteen = CampusLocationHelper.findZone("Campus Canteen")
        assertNotNull(canteen)
        assertEquals("CANTEEN", canteen?.id)

        val unknown = CampusLocationHelper.findZone("Mars Station 9")
        assertNull(unknown)
    }

    @Test
    fun testCalculateWalkMinutes_returnsSensibleDuration() {
        val minutes = CampusLocationHelper.calculateWalkMinutes(
            CampusLocationHelper.CAMPUS_CENTER_LAT,
            CampusLocationHelper.CAMPUS_CENTER_LON,
            "Main Library"
        )
        assertTrue("Walking minutes should be >= 1", minutes >= 1)
        assertTrue("Walking minutes should be <= 20", minutes <= 20)
    }

    @Test
    fun testCalculateWalkMinutes_withZoneWithDot() {
        // e.g. "Main Library • 2m"
        val minutes = CampusLocationHelper.calculateWalkMinutes(
            null,
            null,
            "Main Library • 2m"
        )
        assertTrue("Walking minutes should be parsed cleanly", minutes >= 1)
    }

    @Test
    fun testFormatZoneWithWalkTime() {
        val formatted = CampusLocationHelper.formatZoneWithWalkTime(
            null,
            null,
            "Main Library"
        )
        assertTrue(formatted.startsWith("Main Library • "))
        assertTrue(formatted.endsWith("m"))
    }

    @Test
    fun testSortItemsByProximity() {
        val item1 = CampusItem(
            id = "item_near",
            title = "Near Item",
            pickupSpot = "Main Library"
        )
        val item2 = CampusItem(
            id = "item_far",
            title = "Far Item",
            pickupSpot = "Sports Ground"
        )

        val items = listOf(item2, item1)
        val sorted = CampusLocationHelper.sortItemsByProximity(
            items,
            CampusLocationHelper.CAMPUS_CENTER_LAT,
            CampusLocationHelper.CAMPUS_CENTER_LON
        )
        assertEquals(2, sorted.size)
        // Library is closer to campus center than sports ground
        assertEquals("item_near", sorted[0].id)
    }
}
