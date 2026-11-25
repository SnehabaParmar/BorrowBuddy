package com.borrowbuddy.app.model

import java.io.Serializable

/**
 * Central data model representing a BorrowBuddy Student Profile.
 *
 * NOTE ON DATA PRIVACY & SEPARATION:
 * [email] is an account-level attribute strictly reserved for private account
 * management in "My Profile" (stored in Firestore collection: `users/{uid}`).
 * It must NEVER be stored in the public collection `public_profiles/{uid}`
 * or exposed on the public Student Profile!
 *
 * [isVerifiedCampusStudent] defaults to FALSE until a genuine campus verification
 * mechanism is implemented and validated by an authoritative source.
 */
data class StudentProfile(
    val id: String = "",
    var fullName: String = "",
    val email: String = "", // PRIVATE: Only displayed in My Profile, never stored in public_profiles!
    var college: String = "Gujarat University • Campus North",
    var department: String = "IMSc.IT",
    var yearSemester: String = "4th Semester",
    var bio: String = "", // Optional short bio (max 100 characters)
    var photoUri: String = "", // Optional local or remote photo URI
    val isVerifiedCampusStudent: Boolean = false, // Defaults to FALSE until genuine verification exists
    var completedLends: Int = 0,
    var completedBorrows: Int = 0,
    var rating: String = "" // Displayed only if genuine ratings exist
) : Serializable
