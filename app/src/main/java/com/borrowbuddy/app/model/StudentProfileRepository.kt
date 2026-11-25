package com.borrowbuddy.app.model

import com.borrowbuddy.app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Central repository managing BorrowBuddy Student Profiles and providing the single
 * source of truth for both private account management and public peer profiles.
 *
 * DATA ARCHITECTURE & SECURITY:
 * 1. Private Account Data: Stored in Firestore collection `users/{uid}` (email, accountStatus, createdAt).
 * 2. Public Profile Data: Stored in Firestore collection `public_profiles/{uid}` (fullName, college,
 *    department, yearSemester, bio, photoUri, isVerifiedCampusStudent).
 * 3. Emails and credentials are NEVER written to or exposed via `public_profiles`.
 * 4. Campus verification defaults strictly to FALSE until verified by an authoritative campus source.
 */
object StudentProfileRepository {

    // Thread-safe listener collection
    private val profileChangeListeners = CopyOnWriteArrayList<(StudentProfile) -> Unit>()

    // In-memory cache for remote public student profiles
    private val cachedPublicProfiles = ConcurrentHashMap<String, StudentProfile>()

    // =========================================================================
    // 1. Logged-in Student Central Profile (Single Source of Truth)
    // =========================================================================
    private var currentUserProfile = StudentProfile(
        id = "user_current",
        fullName = "Harsh Mistry",
        email = "hkmistry125@gmail.com", // Strictly private! Never in public_profiles
        college = "Gujarat University • Campus North",
        department = "IMSc.IT",
        yearSemester = "4th Semester",
        bio = "IMSc.IT student passionate about tech & robotics. Happy to share calculators, cables & textbooks!",
        photoUri = "",
        isVerifiedCampusStudent = false, // Defaults to false
        completedLends = 0,
        completedBorrows = 0,
        rating = "★ 5.0"
    )

    // Central profile seed factory
    fun createSeedItems(): List<CampusItem> = listOf(
        CampusItem(
            id = "c_user_1",
            title = "Presentation Clicker (Wireless 2.4GHz)",
            description = "Ergonomic wireless presenter with red laser pointer and plug-and-play USB receiver. Perfect for lecture hall slides.",
            category = "TECH",
            priceLabel = "₹20/day",
            isFree = false,
            pickupSpot = "IT Block Entrance",
            ownerName = "Harsh Mistry",
            ownerUid = "user_current",
            rating = "★ 5.0",
            drawableRes = R.drawable.item_clicker,
            department = "IMSc.IT",
            condition = "Like New",
            maxDuration = "Up to 3 days",
            securityDeposit = "None",
            completedLends = 1,
            ownerCourse = "IMSc.IT Sem 4"
        ),
        CampusItem(
            id = "c_user_2",
            title = "Engineering Mechanics Reference Book",
            description = "Standard semester textbook with worked problem sets and step-by-step solutions for mechanical principles.",
            category = "BOOKS",
            priceLabel = "FREE",
            isFree = true,
            pickupSpot = "Campus Central Library",
            ownerName = "Harsh Mistry",
            ownerUid = "user_current",
            rating = "★ 5.0",
            drawableRes = R.drawable.item_notes,
            department = "IMSc.IT",
            condition = "Good Condition",
            maxDuration = "Up to 5 days",
            securityDeposit = "None",
            completedLends = 1,
            ownerCourse = "IMSc.IT Sem 4"
        )
    )

    // Logged-in student's personal listings (Strictly isolated per account, empty for new accounts)
    private val currentUserItems = mutableListOf<CampusItem>()

    // =========================================================================
    // 2. Peer Campus Student Profiles Catalog (Mock / Offline Seed)
    // =========================================================================
    private val peerProfiles = mutableMapOf(
        "u1" to StudentProfile(
            id = "u1",
            fullName = "Priya Patel",
            email = "", // Never exposed
            college = "Gujarat University • Campus North",
            department = "IMSc.IT",
            yearSemester = "4th Semester",
            bio = "Electronics & IoT enthusiast. Always carrying spare calculators and lab tools.",
            isVerifiedCampusStudent = false,
            completedLends = 0,
            completedBorrows = 0,
            rating = "★ 5.0"
        ),
        "u2" to StudentProfile(
            id = "u2",
            fullName = "Rahul Sharma",
            email = "",
            college = "Gujarat University • Campus North",
            department = "IMBA",
            yearSemester = "3rd Semester",
            bio = "Management student. Presentation clickers, dongles & business cases available.",
            isVerifiedCampusStudent = false,
            completedLends = 0,
            completedBorrows = 0,
            rating = "★ 4.9"
        ),
        "u3" to StudentProfile(
            id = "u3",
            fullName = "Aman Verma",
            email = "",
            college = "Gujarat University • Campus North",
            department = "IMSc.IT",
            yearSemester = "6th Semester",
            bio = "Senior IMSc.IT student. Sharing handwritten DSA & algorithms notes with juniors.",
            isVerifiedCampusStudent = false,
            completedLends = 0,
            completedBorrows = 0,
            rating = "★ 5.0"
        ),
        "u4" to StudentProfile(
            id = "u4",
            fullName = "Sneha Joshi",
            email = "",
            college = "Gujarat University • Campus North",
            department = "IMSc.IT",
            yearSemester = "4th Semester",
            bio = "Robotics & microcontrollers lab assistant. Arduino and sensors available.",
            isVerifiedCampusStudent = false,
            completedLends = 0,
            completedBorrows = 0,
            rating = "★ 4.8"
        ),
        "u5" to StudentProfile(
            id = "u5",
            fullName = "Karan Shah",
            email = "",
            college = "Gujarat University • Campus North",
            department = "IMBA",
            yearSemester = "2nd Semester",
            bio = "Clean lab coats and stationery for campus practical exams.",
            isVerifiedCampusStudent = false,
            completedLends = 0,
            completedBorrows = 0,
            rating = "★ 4.7"
        ),
        "u6" to StudentProfile(
            id = "u6",
            fullName = "Ananya Mehta",
            email = "",
            college = "Gujarat University • Campus North",
            department = "IMBA",
            yearSemester = "4th Semester",
            bio = "Campus event coordinator. DSLR tripods & AV equipment.",
            isVerifiedCampusStudent = false,
            completedLends = 0,
            completedBorrows = 0,
            rating = "★ 4.9"
        ),
        "u7" to StudentProfile(
            id = "u7",
            fullName = "Dev Patel",
            email = "",
            college = "Gujarat University • Campus North",
            department = "IMSc.IT",
            yearSemester = "4th Semester",
            bio = "Cables, chargers & dongles for emergency needs between classes.",
            isVerifiedCampusStudent = false,
            completedLends = 0,
            completedBorrows = 0,
            rating = "★ 5.0"
        ),
        "u8" to StudentProfile(
            id = "u8",
            fullName = "Meera Desai",
            email = "",
            college = "Gujarat University • Campus North",
            department = "IMSc.IT",
            yearSemester = "4th Semester",
            bio = "Textbooks & reference materials for computer science courses.",
            isVerifiedCampusStudent = false,
            completedLends = 0,
            completedBorrows = 0,
            rating = "★ 4.9"
        ),
        "u9" to StudentProfile(
            id = "u9",
            fullName = "Rohan V.",
            email = "",
            college = "Gujarat University • Campus North",
            department = "IMBA",
            yearSemester = "3rd Semester",
            bio = "Sports enthusiast. Badminton sets and campus gym accessories.",
            isVerifiedCampusStudent = false,
            completedLends = 0,
            completedBorrows = 0,
            rating = "★ 4.9"
        ),
        "u_rahul_dave" to StudentProfile(
            id = "u_rahul_dave",
            fullName = "Rahul Dave",
            email = "",
            college = "Gujarat University • Campus North",
            department = "IMSc.IT",
            yearSemester = "4th Semester",
            bio = "Tech and campus sharing enthusiast. Calculators & adapters.",
            isVerifiedCampusStudent = false,
            completedLends = 0,
            completedBorrows = 0,
            rating = "★ 4.8"
        ),
        "u_karan_patel" to StudentProfile(
            id = "u_karan_patel",
            fullName = "Karan Patel",
            email = "",
            college = "Gujarat University • Campus North",
            department = "IMSc.IT",
            yearSemester = "6th Semester",
            bio = "Senior IT student. Notes and reference materials.",
            isVerifiedCampusStudent = false,
            completedLends = 0,
            completedBorrows = 0,
            rating = "★ 5.0"
        ),
        "u_aarav" to StudentProfile(
            id = "u_aarav",
            fullName = "Aarav",
            email = "",
            college = "Gujarat University • Campus North",
            department = "IMSc.IT",
            yearSemester = "4th Semester",
            bio = "Computer science lab student. Adapters and study materials.",
            isVerifiedCampusStudent = false,
            completedLends = 0,
            completedBorrows = 0,
            rating = "★ 4.8"
        )
    )

    // Master items catalog mapped by owner identifier or owner name
    private val peerCatalogItems = mutableListOf(
        CampusItem(
            id = "c1",
            title = "Casio fx-991ES Plus Calculator",
            description = "Standard scientific calculator with 417 functions. Perfect for engineering, statistics, and mathematics courses.",
            category = "TECH",
            priceLabel = "FREE",
            isFree = true,
            pickupSpot = "Main Library Entrance",
            ownerName = "Priya Patel",
            ownerUid = "u1",
            rating = "★ 5.0",
            drawableRes = R.drawable.item_calculator,
            department = "IMSc.IT",
            condition = "Excellent",
            maxDuration = "Up to 3 days",
            securityDeposit = "None",
            completedLends = 18,
            ownerCourse = "IMSc.IT Sem 4"
        ),
        CampusItem(
            id = "c2",
            title = "Type-C Multiport Dongle",
            description = "HDMI, 2x USB 3.0, and SD Card adapter. Essential for laptop presentations and lab assignments.",
            category = "TECH",
            priceLabel = "₹20/day",
            isFree = false,
            pickupSpot = "IMBA Block Entrance",
            ownerName = "Rahul Sharma",
            ownerUid = "u2",
            rating = "★ 4.9",
            drawableRes = R.drawable.item_dongle,
            department = "IMBA",
            condition = "Pristine",
            maxDuration = "Up to 2 days",
            securityDeposit = "₹100 (Refundable)",
            completedLends = 24,
            ownerCourse = "IMBA Sem 3"
        ),
        CampusItem(
            id = "c3",
            title = "DSA in Java Notes",
            description = "Comprehensive handwritten Data Structures & Algorithms notes covering trees, graphs, and dynamic programming.",
            category = "BOOKS",
            priceLabel = "FREE",
            isFree = true,
            pickupSpot = "Main Library • 2nd Floor",
            ownerName = "Aman Verma",
            ownerUid = "u3",
            rating = "★ 5.0",
            drawableRes = R.drawable.item_notes,
            department = "IMSc.IT",
            condition = "Handwritten • Complete",
            maxDuration = "Up to 7 days",
            securityDeposit = "None",
            completedLends = 32,
            ownerCourse = "IMSc.IT Sem 6"
        ),
        CampusItem(
            id = "c4",
            title = "Arduino Uno Kit",
            description = "Complete robotics starter set with breadboard, sensors, jumper cables, and LEDs.",
            category = "LAB",
            priceLabel = "₹30/day",
            isFree = false,
            pickupSpot = "IMSc.IT Lab 2",
            ownerName = "Sneha Joshi",
            ownerUid = "u4",
            rating = "★ 4.8",
            drawableRes = R.drawable.item_arduino,
            department = "IMSc.IT",
            condition = "Fully Working",
            maxDuration = "Up to 5 days",
            securityDeposit = "College ID",
            completedLends = 12,
            ownerCourse = "IMSc.IT Sem 4"
        ),
        CampusItem(
            id = "c5",
            title = "White Lab Coat (M)",
            description = "Clean, freshly washed cotton lab coat compliant with campus laboratory guidelines.",
            category = "LAB",
            priceLabel = "FREE",
            isFree = true,
            pickupSpot = "Science Block",
            ownerName = "Karan Shah",
            ownerUid = "u5",
            rating = "★ 4.7",
            drawableRes = R.drawable.item_lab_coat,
            department = "IMBA",
            condition = "Freshly Washed",
            maxDuration = "Up to 1 day",
            securityDeposit = "None",
            completedLends = 15,
            ownerCourse = "IMBA Sem 2"
        ),
        CampusItem(
            id = "c6",
            title = "DSLR Tripod Stand",
            description = "Adjustable 50-inch camera tripod with 360-degree ball head and mobile phone clamp.",
            category = "EVENT",
            priceLabel = "₹50/day",
            isFree = false,
            pickupSpot = "Campus Canteen",
            ownerName = "Ananya Mehta",
            ownerUid = "u6",
            rating = "★ 4.9",
            drawableRes = R.drawable.item_tripod,
            department = "IMBA",
            condition = "Excellent",
            maxDuration = "Up to 2 days",
            securityDeposit = "₹200 (Refundable)",
            completedLends = 9,
            ownerCourse = "IMBA Sem 4"
        ),
        CampusItem(
            id = "c7",
            title = "Braided Charging Cables",
            description = "1.5m braided quick-charge cables (Type-C & Lightning) for emergency phone charging between classes.",
            category = "TECH",
            priceLabel = "FREE",
            isFree = true,
            pickupSpot = "Campus Canteen",
            ownerName = "Dev Patel",
            ownerUid = "u7",
            rating = "★ 5.0",
            drawableRes = R.drawable.item_cables,
            department = "IMSc.IT",
            condition = "Good",
            maxDuration = "Up to 4 hours",
            securityDeposit = "None",
            completedLends = 28,
            ownerCourse = "IMSc.IT Sem 4"
        ),
        CampusItem(
            id = "c8",
            title = "DBMS 7th Edition",
            description = "Database System Concepts reference textbook by Silberschatz and Korth.",
            category = "BOOKS",
            priceLabel = "FREE",
            isFree = true,
            pickupSpot = "Main Library Reading Room",
            ownerName = "Meera Desai",
            ownerUid = "u8",
            rating = "★ 4.9",
            drawableRes = R.drawable.item_dbms_book,
            department = "IMSc.IT",
            condition = "Crisp Pages",
            maxDuration = "Up to 5 days",
            securityDeposit = "None",
            completedLends = 19,
            ownerCourse = "IMSc.IT Sem 4"
        ),
        CampusItem(
            id = "c9",
            title = "Yonex Badminton Set",
            description = "Pair of lightweight carbon badminton rackets and Mavis 350 shuttles for university sports ground.",
            category = "SPORTS",
            priceLabel = "₹15/day",
            isFree = false,
            pickupSpot = "Sports Ground",
            ownerName = "Rohan V.",
            ownerUid = "u9",
            rating = "★ 4.9",
            drawableRes = R.drawable.item_badminton,
            department = "IMBA",
            condition = "Good Condition",
            maxDuration = "Up to 2 days",
            securityDeposit = "None",
            completedLends = 11,
            ownerCourse = "IMBA Sem 3"
        )
    )

    // =========================================================================
    // 3. Central Profile Operations
    // =========================================================================

    /**
     * Initialize local profile photo if a photo was previously saved on this device for the current user.
     * Never falls back to a shared avatar file when unauthenticated.
     */
    fun initLocalPhoto(context: android.content.Context? = null, uid: String? = null) {
        // If current profile already has a remote Cloudinary URL, preserve it
        if (currentUserProfile.photoUri.startsWith("https://", ignoreCase = true)) {
            return
        }

        if (context == null) {
            currentUserProfile = currentUserProfile.copy(photoUri = "")
            notifyListeners()
            return
        }
        val targetUid = uid?.trim()?.ifEmpty { null }
            ?: try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
            ?: currentUserProfile.id.takeIf { it.isNotBlank() && it != "user_current" }

        val path = if (!targetUid.isNullOrBlank()) {
            com.borrowbuddy.app.util.AvatarHelper.getLocalProfilePhotoPath(context, targetUid)
        } else {
            null
        }

        // Only override if current profile photoUri is not already an HTTPS cloud URL
        if (!currentUserProfile.photoUri.startsWith("https://", ignoreCase = true)) {
            currentUserProfile = currentUserProfile.copy(photoUri = path.orEmpty())
            notifyListeners()
        }
    }

    /**
     * Get the logged-in student's profile (contains private email for My Profile).
     */
    fun getCurrentUserProfile(): StudentProfile {
        val targetUid = try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        } ?: currentUserProfile.id
        val lends = getCompletedLendsCount(targetUid)
        val borrows = getCompletedBorrowsCount(targetUid)
        return currentUserProfile.copy(
            completedLends = lends,
            completedBorrows = borrows
        )
    }

    /**
     * Update the logged-in student's public and private profiles atomically using a Firestore batch write.
     * Writes to `public_profiles/{uid}` and `users/{uid}` synchronously.
     * Reports success and commits in-memory changes ONLY when the Firestore batch write succeeds.
     * Validates active Firebase UID in callbacks to prevent stale callbacks from previous accounts.
     */
    fun updateCurrentUserProfile(
        name: String,
        college: String,
        department: String,
        yearSemester: String,
        bio: String,
        photoUri: String = currentUserProfile.photoUri,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val cleanName = name.trim()
        val cleanCollege = college.trim()
        val cleanDept = department.trim()
        val cleanYearSem = yearSemester.trim()
        val cleanBio = bio.trim()
        val cleanPhotoUri = photoUri.trim()

        val user = try {
            FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        }
        if (user != null) {
            val requestUid = user.uid

            val firestore = FirebaseFirestore.getInstance()
            val batch = firestore.batch()

            val publicRef = firestore.collection("public_profiles").document(requestUid)
            val userRef = firestore.collection("users").document(requestUid)

            // Only persist valid HTTPS Cloudinary URLs or empty string to Cloud Firestore.
            // Local device file paths and content:// URIs are NEVER written to Cloud Firestore.
            val firestorePhotoUrl = if (cleanPhotoUri.startsWith("https://", ignoreCase = true)) cleanPhotoUri else ""

            val publicUpdates = hashMapOf<String, Any>(
                "uid" to requestUid,
                "fullName" to cleanName,
                "college" to cleanCollege,
                "department" to cleanDept,
                "yearSemester" to cleanYearSem,
                "bio" to cleanBio,
                "photoUri" to firestorePhotoUrl,
                "isVerifiedCampusStudent" to false
            )
            batch.set(publicRef, publicUpdates, SetOptions.merge())

            val userUpdates = hashMapOf<String, Any>(
                "name" to cleanName,
                "profileImage" to firestorePhotoUrl
            )
            batch.set(userRef, userUpdates, SetOptions.merge())

            batch.commit()
                .addOnSuccessListener {
                    // Active UID Validation: Discard callback if user switched accounts or logged out
                    val activeUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
                    if (activeUid != requestUid) {
                        onComplete?.invoke(false)
                        return@addOnSuccessListener
                    }

                    // Commit changes to in-memory state on confirmed write (photoUri holds local device path)
                    currentUserProfile.fullName = cleanName
                    currentUserProfile.college = cleanCollege
                    currentUserProfile.department = cleanDept
                    currentUserProfile.yearSemester = cleanYearSem
                    currentUserProfile.bio = cleanBio
                    currentUserProfile.photoUri = cleanPhotoUri

                    notifyListeners()
                    onComplete?.invoke(true)
                }
                .addOnFailureListener {
                    // Active UID Validation: Discard callback if user switched accounts or logged out
                    val activeUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
                    if (activeUid != requestUid) {
                        onComplete?.invoke(false)
                        return@addOnFailureListener
                    }

                    // Failure reports false; in-memory state is NOT falsely claimed as saved
                    onComplete?.invoke(false)
                }
        } else {
            // Offline / Unauthenticated guest session
            currentUserProfile.fullName = cleanName
            currentUserProfile.college = cleanCollege
            currentUserProfile.department = cleanDept
            currentUserProfile.yearSemester = cleanYearSem
            currentUserProfile.bio = cleanBio
            currentUserProfile.photoUri = cleanPhotoUri

            notifyListeners()
            onComplete?.invoke(true)
        }
    }

    /**
     * Clear all user-specific in-memory session data upon user logout.
     * Prevents Account A's state from leaking into Account B's session.
     * Preserves locally saved avatar files on disk (scoped per UID).
     */
    fun clearSession(context: android.content.Context? = null) {
        currentUserProfile = StudentProfile(
            id = "user_current",
            fullName = "Student",
            email = "",
            college = "Gujarat University • Campus North",
            department = "Campus Student",
            yearSemester = "",
            bio = "",
            photoUri = "",
            isVerifiedCampusStudent = false,
            completedLends = 0,
            completedBorrows = 0,
            rating = "★ 5.0"
        )
        currentUserItems.clear()
        cachedPublicProfiles.clear()
        // Do NOT delete user's saved avatar file on logout; files are isolated on disk per UID
        notifyListeners()
    }

    /**
     * Restore seeded mock profile state for unit tests or guest mode.
     */
    fun resetToMockDefaults() {
        currentUserProfile = StudentProfile(
            id = "user_current",
            fullName = "Harsh Mistry",
            email = "hkmistry125@gmail.com",
            college = "Gujarat University • Campus North",
            department = "IMSc.IT",
            yearSemester = "4th Semester",
            bio = "IMSc.IT student passionate about tech & robotics. Happy to share calculators, cables & textbooks!",
            photoUri = "",
            isVerifiedCampusStudent = false,
            completedLends = 0,
            completedBorrows = 0,
            rating = "★ 5.0"
        )
        currentUserItems.clear()
        currentUserItems.addAll(createSeedItems())
        cachedPublicProfiles.clear()
        notifyListeners()
    }

    /**
     * Sync with Firebase Auth and Firestore upon application startup.
     * Safely reads private data from users/{uid} and public data from public_profiles/{uid}.
     * Implements idempotent migration if public_profiles/{uid} does not exist yet.
     */
    fun syncWithFirebase(user: com.google.firebase.auth.FirebaseUser?) {
        if (user == null) return
        val currentEmail = user.email.orEmpty()
        val currentDisplayName = user.displayName?.ifBlank { "" }.orEmpty()
        currentUserProfile = currentUserProfile.copy(
            id = user.uid,
            email = if (currentEmail.isNotBlank()) currentEmail else currentUserProfile.email,
            fullName = if (currentDisplayName.isNotBlank()) currentDisplayName else currentUserProfile.fullName
        )

        val firestore = FirebaseFirestore.getInstance()

        // 1. Load private account data from users/{uid}
        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { userDoc ->
                val activeUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
                if (activeUid != user.uid) return@addOnSuccessListener

                val storedPrivateEmail = userDoc.getString("email").orEmpty()
                if (storedPrivateEmail.isNotBlank()) {
                    currentUserProfile = currentUserProfile.copy(email = storedPrivateEmail)
                }
                val storedName = userDoc.getString("name").orEmpty()

                // 2. Load public profile from public_profiles/{uid}
                firestore.collection("public_profiles").document(user.uid).get()
                    .addOnSuccessListener { pubDoc ->
                        val currentActiveUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
                        if (currentActiveUid != user.uid) return@addOnSuccessListener

                        if (pubDoc.exists()) {
                            pubDoc.getString("fullName")?.let { if (it.isNotBlank()) currentUserProfile.fullName = it }
                            pubDoc.getString("college")?.let { if (it.isNotBlank()) currentUserProfile.college = it }
                            pubDoc.getString("department")?.let { if (it.isNotBlank()) currentUserProfile.department = it }
                            pubDoc.getString("yearSemester")?.let { if (it.isNotBlank()) currentUserProfile.yearSemester = it }
                            pubDoc.getString("bio")?.let { currentUserProfile.bio = it }
                            val remotePhoto = pubDoc.getString("photoUri").orEmpty()
                            if (remotePhoto.startsWith("https://", ignoreCase = true)) {
                                currentUserProfile.photoUri = remotePhoto
                            }
                            notifyListeners()
                        } else {
                            // Safe, idempotent migration from users/{uid} to public_profiles/{uid}
                            val initialName = if (storedName.isNotBlank()) storedName else (user.displayName ?: currentUserProfile.fullName)
                            val initialCollege = userDoc.getString("college") ?: currentUserProfile.college
                            val initialDept = userDoc.getString("department") ?: currentUserProfile.department
                            val initialYearSem = userDoc.getString("yearSemester") ?: currentUserProfile.yearSemester
                            val initialBio = userDoc.getString("bio") ?: currentUserProfile.bio

                            currentUserProfile.fullName = initialName
                            currentUserProfile.college = initialCollege
                            currentUserProfile.department = initialDept
                            currentUserProfile.yearSemester = initialYearSem
                            currentUserProfile.bio = initialBio

                            val migratedPublicDoc = hashMapOf<String, Any>(
                                "uid" to user.uid,
                                "fullName" to initialName,
                                "college" to initialCollege,
                                "department" to initialDept,
                                "yearSemester" to initialYearSem,
                                "bio" to initialBio,
                                "photoUri" to "",
                                "isVerifiedCampusStudent" to false
                            )
                            firestore.collection("public_profiles").document(user.uid)
                                .set(migratedPublicDoc, SetOptions.merge())

                            notifyListeners()
                        }
                    }
            }

        // 3. Persistent listing retrieval: load current user's published listings from Firestore items collection
        firestore.collection("items")
            .whereEqualTo("ownerUid", user.uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val activeUid = try { FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
                if (activeUid != user.uid) return@addOnSuccessListener

                val remoteItems = mutableListOf<CampusItem>()
                for (doc in snapshot.documents) {
                    val item = ItemRepository.docToCampusItem(doc) ?: docToCampusItem(doc)
                    if (item != null) {
                        remoteItems.add(item)
                    }
                }
                currentUserItems.clear()
                currentUserItems.addAll(remoteItems)
                notifyListeners()
            }
            .addOnFailureListener {
                // Preserves local in-memory fallback on network failure
            }
    }

    /**
     * Map a Firestore document to a CampusItem model.
     */
    private fun docToCampusItem(doc: com.google.firebase.firestore.DocumentSnapshot): CampusItem? {
        return ItemRepository.docToCampusItem(doc)
    }

    /**
     * Fetch a public Student Profile by either student ID or exact student name.
     * Guaranteed to never expose private account credentials or private email!
     * Returns null if student identity cannot be resolved (no fake profiles!).
     */
    fun getPublicStudentProfile(studentId: String?, studentName: String?): StudentProfile? {
        val currentUid = try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        }

        // 1. Logged-in user's profile
        val isSelf = studentId == "user_current" ||
            (studentId != null && studentId == currentUid) ||
            (studentId == null && studentName != null && studentName.equals(currentUserProfile.fullName, ignoreCase = true))

        if (isSelf) {
            val targetUid = currentUid ?: currentUserProfile.id
            val lends = getCompletedLendsCount(targetUid)
            val borrows = getCompletedBorrowsCount(targetUid)
            return currentUserProfile.copy(
                email = "", // Strip private email
                isVerifiedCampusStudent = false,
                completedLends = lends,
                completedBorrows = borrows
            )
        }

        // 2. Peer catalog by student ID
        if (!studentId.isNullOrBlank() && peerProfiles.containsKey(studentId)) {
            val peer = peerProfiles[studentId]!!
            val lends = getCompletedLendsCount(peer.id)
            val borrows = getCompletedBorrowsCount(peer.id)
            return peer.copy(
                email = "",
                isVerifiedCampusStudent = false,
                completedLends = lends,
                completedBorrows = borrows
            )
        }

        // 3. Remote cached public profile
        if (!studentId.isNullOrBlank() && cachedPublicProfiles.containsKey(studentId)) {
            return cachedPublicProfiles[studentId]
        }

        // 4. Exact full name match in peer catalog
        if (studentId == null && !studentName.isNullOrBlank()) {
            val cleanName = studentName.trim()
            val match = peerProfiles.values.firstOrNull { it.fullName.equals(cleanName, ignoreCase = true) }
            if (match != null) {
                val lends = getCompletedLendsCount(match.id)
                val borrows = getCompletedBorrowsCount(match.id)
                return match.copy(
                    email = "",
                    isVerifiedCampusStudent = false,
                    completedLends = lends,
                    completedBorrows = borrows
                )
            }
        }

        // Identity cannot be resolved reliably -> DO NOT generate synthetic profiles!
        return null
    }

    /**
     * Fetch a public profile from Firestore collection `public_profiles/{studentId}`.
     */
    fun fetchPublicProfileFromFirestore(studentId: String?, onResult: (StudentProfile?) -> Unit) {
        if (studentId.isNullOrBlank() || studentId == "user_current") {
            onResult(getPublicStudentProfile(studentId, null))
            return
        }

        // Check cache first
        cachedPublicProfiles[studentId]?.let {
            onResult(it)
            return
        }

        // Check mock catalog
        if (peerProfiles.containsKey(studentId)) {
            val p = getPublicStudentProfile(studentId, null)
            onResult(p)
            return
        }

        // Query Firestore public_profiles collection
        FirebaseFirestore.getInstance().collection("public_profiles").document(studentId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val fullName = doc.getString("fullName") ?: "Campus Student"
                    val college = doc.getString("college") ?: "Gujarat University • Campus North"
                    val dept = doc.getString("department") ?: "IMSc.IT"
                    val yearSem = doc.getString("yearSemester") ?: "Semester"
                    val bio = doc.getString("bio") ?: ""
                    val photoUri = doc.getString("photoUri") ?: ""

                    // UID-based metrics lookup
                    val lends = getCompletedLendsCount(studentId)
                    val borrows = getCompletedBorrowsCount(studentId)

                    val profile = StudentProfile(
                        id = studentId,
                        fullName = fullName,
                        email = "", // Never expose email
                        college = college,
                        department = dept,
                        yearSemester = yearSem,
                        bio = bio,
                        photoUri = photoUri,
                        isVerifiedCampusStudent = false,
                        completedLends = lends,
                        completedBorrows = borrows,
                        rating = "★ 5.0"
                    )
                    cachedPublicProfiles[studentId] = profile
                    onResult(profile)
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    /**
     * Genuine exchange metric calculation: counts completed lends for a student UID.
     * Uses immutable Firebase UIDs as the identity source.
     */
    fun getCompletedLendsCount(studentUid: String?): Int {
        if (studentUid.isNullOrBlank()) return 0
        return ExchangeRepository.getAll().count { exchange ->
            exchange.type == ExchangeType.LEND &&
                exchange.status == ExchangeStatus.COMPLETED &&
                exchange.ownerUid == studentUid
        }
    }

    /**
     * Genuine exchange metric calculation: counts completed borrows for a student UID.
     * Uses immutable Firebase UIDs as the identity source.
     */
    fun getCompletedBorrowsCount(studentUid: String?): Int {
        if (studentUid.isNullOrBlank()) return 0
        return ExchangeRepository.getAll().count { exchange ->
            exchange.type == ExchangeType.BORROW &&
                exchange.status == ExchangeStatus.COMPLETED &&
                exchange.borrowerUid == studentUid
        }
    }

    /**
     * Get the logged-in student's items (single source of truth).
     */
    fun getCurrentUserItems(): List<CampusItem> = currentUserItems.toList()

    /**
     * Fetch all currently available listings for a given student.
     * Filters out items currently involved in active exchanges.
     */
    fun getAvailableItemsForStudent(studentId: String?, studentName: String?): List<CampusItem> {
        val currentUid = try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (_: Exception) {
            null
        }

        // 1. If logged-in user
        val isSelf = studentId == "user_current" ||
            (studentId != null && studentId == currentUid) ||
            (studentName != null && studentName.equals(currentUserProfile.fullName, ignoreCase = true))

        val rawList: List<CampusItem> = if (isSelf) {
            currentUserItems.toList()
        } else {
            val targetId = studentId?.trim().orEmpty()
            if (targetId.isNotBlank()) {
                peerCatalogItems.filter { it.ownerUid == targetId }
            } else {
                emptyList()
            }
        }

        // Return only items that are genuinely available (not committed to an active exchange)
        return rawList.filter { ExchangeRepository.isItemAvailable(it.id) }
    }

    /**
     * Fetch published listings for a peer student from Firestore by their UID.
     */
    fun fetchItemsForStudentFromFirestore(studentUid: String, onResult: (List<CampusItem>) -> Unit) {
        if (studentUid.isBlank() || studentUid.startsWith("mock_") || studentUid == "user_current") {
            onResult(emptyList())
            return
        }
        FirebaseFirestore.getInstance().collection("items")
            .whereEqualTo("ownerUid", studentUid)
            .get()
            .addOnSuccessListener { snapshot ->
                val list = mutableListOf<CampusItem>()
                for (doc in snapshot.documents) {
                    val item = docToCampusItem(doc)
                    if (item != null && ExchangeRepository.isItemAvailable(item.id)) {
                        list.add(item)
                    }
                }
                onResult(list)
            }
            .addOnFailureListener {
                onResult(emptyList())
            }
    }

    /**
     * Add a newly listed item by the current user.
     * Note: Listing an item does NOT increment completed lends!
     */
    fun addCurrentUserItem(item: CampusItem) {
        currentUserItems.removeAll { it.id == item.id }
        currentUserItems.add(0, item)
        notifyListeners()
    }

    // =========================================================================
    // 4. Change Listeners (Thread-safe)
    // =========================================================================
    fun addProfileChangeListener(listener: (StudentProfile) -> Unit) {
        if (!profileChangeListeners.contains(listener)) {
            profileChangeListeners.add(listener)
        }
    }

    fun removeProfileChangeListener(listener: (StudentProfile) -> Unit) {
        profileChangeListeners.remove(listener)
    }

    private fun notifyListeners() {
        profileChangeListeners.forEach { it.invoke(currentUserProfile) }
    }
}
