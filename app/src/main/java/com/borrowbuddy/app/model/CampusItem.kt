package com.borrowbuddy.app.model

import java.io.Serializable

/**
 * Central model representing an item listed for peer borrowing/lending on campus.
 * Supports up to 3 remote HTTPS image URLs hosted on Cloudinary, while retaining
 * legacy single-image [imageUri] compatibility.
 */
data class CampusItem(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "TECH", // TECH, BOOKS, LAB, EVENT, STATIONERY, SPORTS, BUSINESS, CAMPUS
    val priceLabel: String = "FREE", // "FREE" or "₹20/day"
    val isFree: Boolean = true,
    val pickupSpot: String = "Main Library • 2m",
    val ownerName: String = "Priya Patel",
    val ownerUid: String = "",
    val rating: String = "★ 5.0",
    val imageUri: String = "", // Primary remote HTTPS URL or legacy single-image URI
    val imageUris: List<String> = emptyList(), // Up to 3 remote Cloudinary HTTPS image URLs
    val imagePublicIds: List<String> = emptyList(), // Cloudinary asset public IDs corresponding to imageUris
    val drawableRes: Int = 0,
    val department: String = "IMSc.IT",
    val condition: String = "Like New",
    val maxDuration: String = "Up to 3 days",
    val securityDeposit: String = "None",
    val completedLends: Int = 0,
    val ownerCourse: String = "IMSc.IT Sem 4",
    val createdAt: Long = System.currentTimeMillis(),
    val isAvailable: Boolean = true,
    val updatedAt: Long = createdAt
) : Serializable {

    /**
     * Resolves the primary remote HTTPS image URL for display.
     * Prefers the first valid HTTPS URL from [imageUris], falling back to [imageUri] if valid HTTPS, or empty string.
     */
    val primaryImageUrl: String
        get() {
            val firstFromList = imageUris.firstOrNull { it.startsWith("https://", ignoreCase = true) }
            if (!firstFromList.isNullOrBlank()) return firstFromList
            if (imageUri.startsWith("https://", ignoreCase = true)) return imageUri
            return ""
        }

    /**
     * Returns all valid remote HTTPS image URLs for this item (maximum 3).
     * If [imageUris] is empty but legacy [imageUri] is a valid HTTPS URL, returns listOf(imageUri).
     */
    val validImageUrls: List<String>
        get() {
            val filtered = imageUris.filter { it.startsWith("https://", ignoreCase = true) }.take(3)
            if (filtered.isNotEmpty()) return filtered
            if (imageUri.startsWith("https://", ignoreCase = true)) return listOf(imageUri)
            return emptyList()
        }
}
