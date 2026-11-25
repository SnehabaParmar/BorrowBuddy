package com.borrowbuddy.app.model

import java.io.Serializable

data class GlanceRequest(
    val id: String = System.currentTimeMillis().toString(),
    val name: String = "Student",
    val location: String = "Main Library", // Needed At location
    val item: String = "",
    val avatarLetter: String = "S",
    val description: String = "",
    val category: String = "",
    val quantity: Int = 1,
    val neededBy: String = "ASAP", // e.g. "Today, 2:00 PM", "ASAP", "Within 2 Hours"
    val expirationHours: Int = 24, // 6, 12, 24, 48
    val state: String = "ACTIVE", // ACTIVE, FULFILLED, CANCELLED, EXPIRED
    val createdAt: Long = System.currentTimeMillis(),
    val requesterUid: String = "" // Reliable student identity UID
) : Serializable
