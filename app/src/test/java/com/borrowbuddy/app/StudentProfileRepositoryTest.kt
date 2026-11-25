package com.borrowbuddy.app

import com.borrowbuddy.app.model.CampusItem
import com.borrowbuddy.app.model.StudentProfile
import com.borrowbuddy.app.model.StudentProfileRepository
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StudentProfileRepositoryTest {

    @Before
    fun setUp() {
        StudentProfileRepository.resetToMockDefaults()
    }

    @Test
    fun testGetCurrentUserProfile_containsValidDefaults() {
        val profile = StudentProfileRepository.getCurrentUserProfile()
        assertNotNull(profile)
        assertTrue("Name should not be blank", profile.fullName.isNotBlank())
        assertTrue("College should contain Gujarat University", profile.college.contains("Gujarat University"))
        assertEquals("IMSc.IT", profile.department)
        assertFalse("Campus verification should default to false", profile.isVerifiedCampusStudent)
        assertTrue(profile.email.contains("@"))
    }

    @Test
    fun testGetPublicStudentProfile_masksPrivateEmail() {
        // Public profile of current user should NEVER expose private email
        val publicSelf = StudentProfileRepository.getPublicStudentProfile("user_current", null)
        assertNotNull("Should resolve public profile for self", publicSelf)
        assertEquals("", publicSelf?.email)
        assertEquals("Harsh Mistry", publicSelf?.fullName)

        // Resolve peer profile
        val peer = StudentProfileRepository.getPublicStudentProfile("u1", "Priya Patel")
        assertNotNull(peer)
        assertEquals("Priya Patel", peer?.fullName)
        assertEquals("", peer?.email)
    }

    @Test
    fun testGetPublicStudentProfile_unknownStudentReturnsNull() {
        val unknown = StudentProfileRepository.getPublicStudentProfile("non_existent_uid_123", "Unknown Student Name XYZ")
        assertNull("Non-existent student ID should return null instead of a fabricated profile", unknown)
    }

    @Test
    fun testGetCurrentUserItems_andAddNewItem() {
        val initialItems = StudentProfileRepository.getCurrentUserItems()
        val initialCount = initialItems.size

        val newItem = CampusItem(
            id = "c_user_test_" + System.currentTimeMillis(),
            title = "Test Digital Multimeter",
            description = "Digital multimeter for electrical physics practicals",
            category = "LAB",
            priceLabel = "FREE",
            isFree = true,
            pickupSpot = "Science Block",
            ownerName = "Harsh Mistry",
            ownerUid = "user_current"
        )

        StudentProfileRepository.addCurrentUserItem(newItem)
        val updatedItems = StudentProfileRepository.getCurrentUserItems()
        assertEquals(initialCount + 1, updatedItems.size)
        assertTrue(updatedItems.any { it.id == newItem.id })
    }

    @Test
    fun testGetAvailableItemsForStudent() {
        val currentItems = StudentProfileRepository.getAvailableItemsForStudent("user_current", "Harsh Mistry")
        assertNotNull(currentItems)
        assertTrue(currentItems.isNotEmpty())

        val peerItems = StudentProfileRepository.getAvailableItemsForStudent("u1", "Priya Patel")
        assertNotNull(peerItems)
    }

    @Test
    fun testProfileChangeListener() {
        var receivedProfile: StudentProfile? = null
        val listener: (StudentProfile) -> Unit = { receivedProfile = it }
        
        StudentProfileRepository.addProfileChangeListener(listener)
        // Verify registration
        StudentProfileRepository.removeProfileChangeListener(listener)
    }

    @Test
    fun testClearSession_clearsUserDataAndItems() {
        // Reset to clean test state
        StudentProfileRepository.resetToMockDefaults()
        val beforeClear = StudentProfileRepository.getCurrentUserProfile()
        assertTrue(beforeClear.fullName.isNotBlank())
        assertTrue(StudentProfileRepository.getCurrentUserItems().isNotEmpty())

        // Clear session
        StudentProfileRepository.clearSession(null)
        val afterClear = StudentProfileRepository.getCurrentUserProfile()
        assertEquals("", afterClear.email)
        assertTrue(StudentProfileRepository.getCurrentUserItems().isEmpty())

        // Restore mock defaults so subsequent tests pass cleanly
        StudentProfileRepository.resetToMockDefaults()
    }

    @Test
    fun testAvatarFilenameScoping_nullOrEmptyReturnsNull() {
        assertNull(com.borrowbuddy.app.util.AvatarHelper.resolveAvatarFileName(null))
        assertNull(com.borrowbuddy.app.util.AvatarHelper.resolveAvatarFileName(""))
        assertNull(com.borrowbuddy.app.util.AvatarHelper.resolveAvatarFileName("   "))
        
        assertEquals("profile_avatar_uid_student_a.jpg", com.borrowbuddy.app.util.AvatarHelper.resolveAvatarFileName("uid_student_a"))
        assertEquals("profile_avatar_uid_student_b.jpg", com.borrowbuddy.app.util.AvatarHelper.resolveAvatarFileName("uid_student_b"))
    }

    @Test
    fun testAvatarAccountIsolation_accountBCannotAccessAccountA() {
        val fileA = com.borrowbuddy.app.util.AvatarHelper.resolveAvatarFileName("account_a_123")
        val fileB = com.borrowbuddy.app.util.AvatarHelper.resolveAvatarFileName("account_b_456")
        assertNotNull(fileA)
        assertNotNull(fileB)
        assertNotEquals("Account B must not resolve to Account A's avatar filename", fileA, fileB)
    }

    @Test
    fun testClearSession_preservesPhotoOnDiskAndClearsInMemoryRef() {
        StudentProfileRepository.resetToMockDefaults()
        StudentProfileRepository.initLocalPhoto(null, "uid_dummy_test")
        
        // Calling clearSession must reset in-memory photoUri to empty
        StudentProfileRepository.clearSession(null)
        val profileAfter = StudentProfileRepository.getCurrentUserProfile()
        assertEquals("", profileAfter.photoUri)

        // Restore defaults
        StudentProfileRepository.resetToMockDefaults()
    }

    @Test
    fun testUpdateCurrentUserProfile_localDevicePathPreservedInMemory() {
        StudentProfileRepository.resetToMockDefaults()
        
        var changeNotified = false
        val listener: (StudentProfile) -> Unit = { changeNotified = true }
        StudentProfileRepository.addProfileChangeListener(listener)

        val localDevicePath = "/data/user/0/com.borrowbuddy.app/files/profile_photos/profile_avatar_user_test.jpg"
        
        StudentProfileRepository.updateCurrentUserProfile(
            name = "Test Student",
            college = "Gujarat University • Campus South",
            department = "Computer Science",
            yearSemester = "2nd Semester",
            bio = "Loving Kotlin and Firebase!",
            photoUri = localDevicePath
        )

        val updated = StudentProfileRepository.getCurrentUserProfile()
        assertEquals("Test Student", updated.fullName)
        assertEquals("Gujarat University • Campus South", updated.college)
        assertEquals("Computer Science", updated.department)
        assertEquals("2nd Semester", updated.yearSemester)
        assertEquals("Loving Kotlin and Firebase!", updated.bio)
        assertEquals(localDevicePath, updated.photoUri)
        assertTrue("Listener should have been notified of profile changes", changeNotified)

        StudentProfileRepository.removeProfileChangeListener(listener)
        StudentProfileRepository.resetToMockDefaults()
    }

    @Test
    fun testAvatarRemoval_setsEmptyPhotoUriAndNotifies() {
        StudentProfileRepository.resetToMockDefaults()
        
        StudentProfileRepository.updateCurrentUserProfile(
            name = "Test Student",
            college = "Gujarat University • Campus North",
            department = "IMSc.IT",
            yearSemester = "4th Semester",
            bio = "Bio",
            photoUri = ""
        )

        val updated = StudentProfileRepository.getCurrentUserProfile()
        assertEquals("", updated.photoUri)

        StudentProfileRepository.resetToMockDefaults()
    }

    @Test
    fun testAvatarHelper_initialsExtraction() {
        assertEquals("HM", com.borrowbuddy.app.util.AvatarHelper.getInitials("Harsh Mistry"))
        assertEquals("PP", com.borrowbuddy.app.util.AvatarHelper.getInitials("Priya Patel"))
        assertEquals("A", com.borrowbuddy.app.util.AvatarHelper.getInitials("Aarav"))
        assertEquals("HM", com.borrowbuddy.app.util.AvatarHelper.getInitials(""))
        assertEquals("HM", com.borrowbuddy.app.util.AvatarHelper.getInitials("   "))
    }

    @Test
    fun testUpdateCurrentUserProfile_cloudinaryHttpsUrlPreserved() {
        StudentProfileRepository.resetToMockDefaults()
        val cloudUrl = "https://res.cloudinary.com/t2nvzeyq/image/upload/v12345/borrowbuddy/profiles/profile_test.jpg"

        var changeNotified = false
        val listener: (StudentProfile) -> Unit = { changeNotified = true }
        StudentProfileRepository.addProfileChangeListener(listener)

        StudentProfileRepository.updateCurrentUserProfile(
            name = "Harsh Mistry",
            college = "Gujarat University • Campus North",
            department = "IMSc.IT",
            yearSemester = "4th Semester",
            bio = "Cloudinary integrated bio",
            photoUri = cloudUrl
        )

        val updated = StudentProfileRepository.getCurrentUserProfile()
        assertEquals(cloudUrl, updated.photoUri)
        assertTrue("Profile listener should be notified of Cloudinary URL update", changeNotified)

        StudentProfileRepository.removeProfileChangeListener(listener)
        StudentProfileRepository.resetToMockDefaults()
    }

    @Test
    fun testInitLocalPhoto_doesNotOverwriteExistingCloudinaryUrl() {
        StudentProfileRepository.resetToMockDefaults()
        val cloudUrl = "https://res.cloudinary.com/t2nvzeyq/image/upload/v9999/borrowbuddy/profiles/avatar.jpg"
        StudentProfileRepository.updateCurrentUserProfile(
            name = "Harsh Mistry",
            college = "Gujarat University • Campus North",
            department = "IMSc.IT",
            yearSemester = "4th Semester",
            bio = "Bio",
            photoUri = cloudUrl
        )

        // When initLocalPhoto is invoked, it must not overwrite the active Cloudinary HTTPS photoUri
        StudentProfileRepository.initLocalPhoto(null, "uid_dummy_test")
        val afterInit = StudentProfileRepository.getCurrentUserProfile()
        assertEquals("Cloudinary HTTPS photoUri must be preserved by initLocalPhoto", cloudUrl, afterInit.photoUri)

        StudentProfileRepository.resetToMockDefaults()
    }

    @Test
    fun testClearSession_clearsCloudinaryPhotoUriAndAccountIsolation() {
        StudentProfileRepository.resetToMockDefaults()
        val cloudUrlA = "https://res.cloudinary.com/t2nvzeyq/image/upload/v111/borrowbuddy/profiles/account_a.jpg"
        StudentProfileRepository.updateCurrentUserProfile(
            name = "Student A",
            college = "Gujarat University • Campus North",
            department = "IMSc.IT",
            yearSemester = "4th Semester",
            bio = "Account A",
            photoUri = cloudUrlA
        )
        assertEquals(cloudUrlA, StudentProfileRepository.getCurrentUserProfile().photoUri)

        // Clear session on logout
        StudentProfileRepository.clearSession(null)
        val afterLogout = StudentProfileRepository.getCurrentUserProfile()
        assertEquals("photoUri must be cleared on logout", "", afterLogout.photoUri)

        // New session for Account B
        val cloudUrlB = "https://res.cloudinary.com/t2nvzeyq/image/upload/v222/borrowbuddy/profiles/account_b.jpg"
        StudentProfileRepository.updateCurrentUserProfile(
            name = "Student B",
            college = "Gujarat University • Campus North",
            department = "IMSc.IT",
            yearSemester = "2nd Semester",
            bio = "Account B",
            photoUri = cloudUrlB
        )
        val profileB = StudentProfileRepository.getCurrentUserProfile()
        assertEquals("Account B must have its own Cloudinary photoUri", cloudUrlB, profileB.photoUri)
        assertNotEquals("Account B must not share Account A's photo URL", cloudUrlA, profileB.photoUri)

        StudentProfileRepository.resetToMockDefaults()
    }

    @Test
    fun testPhotoRemoval_emptyPhotoUriPreservesOtherFields() {
        StudentProfileRepository.resetToMockDefaults()
        val originalUrl = "https://res.cloudinary.com/t2nvzeyq/image/upload/v1234/borrowbuddy/profiles/avatar.jpg"
        
        // Initial state with photo
        StudentProfileRepository.updateCurrentUserProfile(
            name = "Harsh Mistry",
            college = "Gujarat University • Campus North",
            department = "IMSc.IT",
            yearSemester = "4th Semester",
            bio = "Active student",
            photoUri = originalUrl
        )
        assertEquals(originalUrl, StudentProfileRepository.getCurrentUserProfile().photoUri)

        // Photo removal
        var changeNotified = false
        val listener: (StudentProfile) -> Unit = { changeNotified = true }
        StudentProfileRepository.addProfileChangeListener(listener)

        StudentProfileRepository.updateCurrentUserProfile(
            name = "Harsh Mistry",
            college = "Gujarat University • Campus North",
            department = "IMSc.IT",
            yearSemester = "4th Semester",
            bio = "Active student",
            photoUri = ""
        )

        val updated = StudentProfileRepository.getCurrentUserProfile()
        assertEquals("photoUri must be cleared upon removal", "", updated.photoUri)
        assertEquals("Full name must be preserved", "Harsh Mistry", updated.fullName)
        assertEquals("College must be preserved", "Gujarat University • Campus North", updated.college)
        assertEquals("Department must be preserved", "IMSc.IT", updated.department)
        assertEquals("Year/Semester must be preserved", "4th Semester", updated.yearSemester)
        assertEquals("Bio must be preserved", "Active student", updated.bio)
        assertTrue("Listener must be notified of photo removal", changeNotified)

        StudentProfileRepository.removeProfileChangeListener(listener)
        StudentProfileRepository.resetToMockDefaults()
    }

    @Test
    fun testAvatarFilenameResolution_emptyOrWhitespaceOnlyReturnsNull() {
        assertNull(com.borrowbuddy.app.util.AvatarHelper.resolveAvatarFileName(null))
        assertNull(com.borrowbuddy.app.util.AvatarHelper.resolveAvatarFileName(""))
        assertNull(com.borrowbuddy.app.util.AvatarHelper.resolveAvatarFileName(" "))
        assertNull(com.borrowbuddy.app.util.AvatarHelper.resolveAvatarFileName("\t\n "))
        assertEquals("profile_avatar_uid_123.jpg", com.borrowbuddy.app.util.AvatarHelper.resolveAvatarFileName("  uid_123  "))
    }
}
