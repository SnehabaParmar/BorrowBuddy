package com.borrowbuddy.app

import com.borrowbuddy.app.model.CampusItem
import com.borrowbuddy.app.model.ItemRepository
import com.borrowbuddy.app.model.StudentProfileRepository
import com.borrowbuddy.app.util.ItemPhotoHelper
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ItemRepositoryTest {

    @Before
    fun setUp() {
        StudentProfileRepository.resetToMockDefaults()
    }

    @Test
    fun testCampusItemToMap_containsAllPersistedFields() {
        val now = 1700000000000L
        val item = CampusItem(
            id = "test_item_1",
            title = "Arduino Uno Kit",
            description = "Microcontroller kit with breadboard and sensors",
            category = "TECH",
            priceLabel = "₹30/day",
            isFree = false,
            pickupSpot = "Lab 3 • 2m",
            ownerName = "Harsh Mistry",
            ownerUid = "uid_harsh",
            rating = "★ 5.0",
            imageUri = "content://media/picker/1", // Should become "" in Firestore
            department = "IMSc.IT",
            condition = "Like New",
            maxDuration = "Up to 3 days",
            securityDeposit = "₹100 (Refundable)",
            completedLends = 5,
            ownerCourse = "IMSc.IT Sem 4",
            createdAt = now,
            isAvailable = true,
            updatedAt = now + 1000L
        )

        val map = ItemRepository.campusItemToMap(item)

        assertEquals("Arduino Uno Kit", map["title"])
        assertEquals("Microcontroller kit with breadboard and sensors", map["description"])
        assertEquals("TECH", map["category"])
        assertEquals("₹30/day", map["priceLabel"])
        assertEquals(false, map["isFree"])
        assertEquals("Lab 3 • 2m", map["pickupSpot"])
        assertEquals("Harsh Mistry", map["ownerName"])
        assertEquals("uid_harsh", map["ownerUid"])
        assertEquals("★ 5.0", map["rating"])
        // Local device photo architecture: imageUri must be stored as empty string in Firestore
        assertEquals("", map["imageUri"])
        assertEquals("IMSc.IT", map["department"])
        assertEquals("Like New", map["condition"])
        assertEquals("Up to 3 days", map["maxDuration"])
        assertEquals("₹100 (Refundable)", map["securityDeposit"])
        assertEquals(5, map["completedLends"])
        assertEquals("IMSc.IT Sem 4", map["ownerCourse"])
        assertEquals(true, map["isAvailable"])
        assertEquals(now, map["createdAt"])
        assertEquals(now + 1000L, map["updatedAt"])
    }

    @Test
    fun testMapToCampusItem_parsesAllFieldsCorrectly() {
        val now = 1700000000000L
        val data = hashMapOf<String, Any>(
            "title" to "Engineering Calculator",
            "description" to "Scientific calculator for mathematics",
            "category" to "TECH",
            "priceLabel" to "FREE",
            "isFree" to true,
            "pickupSpot" to "Main Library",
            "ownerName" to "Priya Patel",
            "ownerUid" to "uid_priya",
            "rating" to "★ 4.9",
            "imageUri" to "",
            "department" to "IMSc.IT",
            "condition" to "Excellent",
            "maxDuration" to "1 Week",
            "securityDeposit" to "None",
            "completedLends" to 12L,
            "ownerCourse" to "IMSc.IT Sem 4",
            "isAvailable" to true,
            "createdAt" to now,
            "updatedAt" to now
        )

        val item = ItemRepository.mapToCampusItem("calc_doc_99", data)
        assertNotNull(item)
        assertEquals("calc_doc_99", item?.id)
        assertEquals("Engineering Calculator", item?.title)
        assertEquals("Scientific calculator for mathematics", item?.description)
        assertEquals("TECH", item?.category)
        assertEquals("FREE", item?.priceLabel)
        assertEquals(true, item?.isFree)
        assertEquals("Main Library", item?.pickupSpot)
        assertEquals("Priya Patel", item?.ownerName)
        assertEquals("uid_priya", item?.ownerUid)
        assertEquals("★ 4.9", item?.rating)
        assertEquals("", item?.imageUri)
        assertEquals("Excellent", item?.condition)
        assertEquals("1 Week", item?.maxDuration)
        assertEquals(12, item?.completedLends)
        assertEquals(now, item?.createdAt)
        assertEquals(now, item?.updatedAt)
    }

    @Test
    fun testMapToCampusItem_handlesMissingFieldsWithSafeDefaults() {
        val data = hashMapOf<String, Any>(
            "title" to "Chemistry Lab Coat"
        )

        val item = ItemRepository.mapToCampusItem("lab_coat_1", data)
        assertNotNull(item)
        assertEquals("lab_coat_1", item?.id)
        assertEquals("Chemistry Lab Coat", item?.title)
        assertEquals("", item?.description)
        assertEquals("TECH", item?.category)
        assertEquals("FREE", item?.priceLabel)
        assertEquals(true, item?.isFree)
        assertEquals("Campus Library", item?.pickupSpot)
        assertEquals("Student", item?.ownerName)
        assertEquals("", item?.ownerUid)
        assertEquals("★ 5.0", item?.rating)
        assertEquals("", item?.imageUri)
        assertEquals("IMSc.IT", item?.department)
        assertEquals("Like New", item?.condition)
        assertEquals("Up to 3 days", item?.maxDuration)
        assertEquals("None", item?.securityDeposit)
        assertEquals(0, item?.completedLends)
        assertEquals("IMSc.IT Sem 4", item?.ownerCourse)
        assertEquals(true, item?.isAvailable)
    }

    @Test
    fun testMapToCampusItem_blankTitleReturnsNull() {
        val nullTitleData = hashMapOf<String, Any>("description" to "Some item without title")
        val blankTitleData = hashMapOf<String, Any>("title" to "   ")

        assertNull(ItemRepository.mapToCampusItem("doc_1", nullTitleData))
        assertNull(ItemRepository.mapToCampusItem("doc_2", blankTitleData))
        assertNull(ItemRepository.mapToCampusItem("doc_3", null))
    }

    @Test
    fun testItemPhotoHelper_filenameResolution() {
        assertNull(ItemPhotoHelper.resolveItemPhotoFileName(null))
        assertNull(ItemPhotoHelper.resolveItemPhotoFileName(""))
        assertNull(ItemPhotoHelper.resolveItemPhotoFileName("   "))

        assertEquals("item_c123.jpg", ItemPhotoHelper.resolveItemPhotoFileName("c123"))
        assertEquals("item_doc_abc.jpg", ItemPhotoHelper.resolveItemPhotoFileName("doc_abc"))
    }

    @Test
    fun testItemPhotoHelper_differentItemIdsProduceDifferentFiles() {
        val file1 = ItemPhotoHelper.resolveItemPhotoFileName("item_aaa")
        val file2 = ItemPhotoHelper.resolveItemPhotoFileName("item_bbb")

        assertNotNull(file1)
        assertNotNull(file2)
        assertNotEquals("Different items must never resolve to the same photo filename", file1, file2)
    }

    @Test
    fun testNewUser_isolatedEmptyListings() {
        // When session is cleared for logout, user items must be empty
        StudentProfileRepository.clearSession(null)
        val emptyItems = StudentProfileRepository.getCurrentUserItems()
        assertTrue("Newly logged in or cleared user session must start with empty listings", emptyItems.isEmpty())

        // Adding an item adds strictly to that user's session
        val testItem = CampusItem(
            id = "isolated_item_1",
            title = "Isolated TextBook",
            ownerUid = "uid_isolated_user"
        )
        StudentProfileRepository.addCurrentUserItem(testItem)
        val userItems = StudentProfileRepository.getCurrentUserItems()
        assertEquals(1, userItems.size)
        assertEquals("isolated_item_1", userItems[0].id)

        // Restoring defaults
        StudentProfileRepository.resetToMockDefaults()
    }

    @Test
    fun testCampusItemToMap_withOneRemoteImage() {
        val url = "https://res.cloudinary.com/t2nvzeyq/image/upload/v1/borrowbuddy/item-images/item1.jpg"
        val publicId = "borrowbuddy/item-images/item1"
        val item = CampusItem(
            id = "item_single",
            title = "Lab Coat",
            imageUri = url,
            imageUris = listOf(url),
            imagePublicIds = listOf(publicId)
        )

        val map = ItemRepository.campusItemToMap(item)
        assertEquals(url, map["imageUri"])
        assertEquals(listOf(url), map["imageUris"])
        assertEquals(listOf(publicId), map["imagePublicIds"])
    }

    @Test
    fun testCampusItemToMap_withThreeRemoteImages() {
        val urls = listOf(
            "https://res.cloudinary.com/t2nvzeyq/image/upload/v1/item_a.jpg",
            "https://res.cloudinary.com/t2nvzeyq/image/upload/v2/item_b.jpg",
            "https://res.cloudinary.com/t2nvzeyq/image/upload/v3/item_c.jpg"
        )
        val publicIds = listOf("pid_a", "pid_b", "pid_c")
        val item = CampusItem(
            id = "item_triple",
            title = "Arduino Kit",
            imageUri = urls.first(),
            imageUris = urls,
            imagePublicIds = publicIds
        )

        val map = ItemRepository.campusItemToMap(item)
        assertEquals(urls[0], map["imageUri"])
        assertEquals(urls, map["imageUris"])
        assertEquals(publicIds, map["imagePublicIds"])
    }

    @Test
    fun testCampusItemToMap_enforcesMaxThreeImages() {
        val urls = listOf(
            "https://res.cloudinary.com/t2nvzeyq/image/upload/v1/1.jpg",
            "https://res.cloudinary.com/t2nvzeyq/image/upload/v2/2.jpg",
            "https://res.cloudinary.com/t2nvzeyq/image/upload/v3/3.jpg",
            "https://res.cloudinary.com/t2nvzeyq/image/upload/v4/4.jpg",
            "https://res.cloudinary.com/t2nvzeyq/image/upload/v5/5.jpg"
        )
        val item = CampusItem(
            id = "item_over_limit",
            title = "Books Bundle",
            imageUris = urls
        )

        val map = ItemRepository.campusItemToMap(item)
        @Suppress("UNCHECKED_CAST")
        val savedUris = map["imageUris"] as List<String>
        assertEquals("imageUris must be capped at 3", 3, savedUris.size)
        assertEquals(urls[0], savedUris[0])
        assertEquals(urls[2], savedUris[2])
    }

    @Test
    fun testCampusItemToMap_filtersNonHttpsUrls() {
        val urls = listOf(
            "content://media/external/images/media/99",
            "file:///data/user/0/cache/temp.jpg",
            "http://insecure.site/photo.jpg",
            "https://res.cloudinary.com/t2nvzeyq/image/upload/v1/valid.jpg"
        )
        val item = CampusItem(
            id = "item_sanitization",
            title = "Test Item",
            imageUri = "content://media/picker/local",
            imageUris = urls
        )

        val map = ItemRepository.campusItemToMap(item)
        @Suppress("UNCHECKED_CAST")
        val savedUris = map["imageUris"] as List<String>
        assertEquals("Only valid HTTPS URLs must be persisted", 1, savedUris.size)
        assertEquals("https://res.cloudinary.com/t2nvzeyq/image/upload/v1/valid.jpg", savedUris[0])
        assertEquals("Primary imageUri must be the valid HTTPS URL", "https://res.cloudinary.com/t2nvzeyq/image/upload/v1/valid.jpg", map["imageUri"])
    }

    @Test
    fun testMapToCampusItem_legacySingleImageDeserialization() {
        val legacyUrl = "https://res.cloudinary.com/t2nvzeyq/image/upload/v999/legacy_single.jpg"
        val data = hashMapOf<String, Any>(
            "title" to "Legacy Single Photo Item",
            "imageUri" to legacyUrl
        )

        val item = ItemRepository.mapToCampusItem("legacy_1", data)
        assertNotNull(item)
        assertEquals(legacyUrl, item?.imageUri)
        assertEquals("imageUris must be populated from legacy imageUri", listOf(legacyUrl), item?.imageUris)
        assertEquals(legacyUrl, item?.primaryImageUrl)
        assertEquals(listOf(legacyUrl), item?.validImageUrls)
    }

    @Test
    fun testMapToCampusItem_missingImageUrisAndEmptyImageUri() {
        val data = hashMapOf<String, Any>(
            "title" to "Item Without Photos"
        )

        val item = ItemRepository.mapToCampusItem("no_photo_1", data)
        assertNotNull(item)
        assertEquals("", item?.imageUri)
        assertTrue(item?.imageUris?.isEmpty() == true)
        assertEquals("", item?.primaryImageUrl)
        assertTrue(item?.validImageUrls?.isEmpty() == true)
    }

    @Test
    fun testMapToCampusItem_nonHttpsLegacyImageSanitizedToEmpty() {
        val data = hashMapOf<String, Any>(
            "title" to "Item With Local Path",
            "imageUri" to "/data/user/0/com.borrowbuddy.app/files/item_photos/item_1.jpg",
            "imageUris" to listOf("content://media/1", "/storage/emulated/0/photo.jpg")
        )

        val item = ItemRepository.mapToCampusItem("local_path_item", data)
        assertNotNull(item)
        assertEquals("Local paths must be sanitized to empty string", "", item?.imageUri)
        assertTrue("Local URIs in imageUris must be filtered out", item?.imageUris?.isEmpty() == true)
        assertEquals("", item?.primaryImageUrl)
    }

    @Test
    fun testMapToCampusItem_threeImagesWithPublicIds() {
        val urls = listOf(
            "https://res.cloudinary.com/t2nvzeyq/image/upload/v1/1.jpg",
            "https://res.cloudinary.com/t2nvzeyq/image/upload/v2/2.jpg",
            "https://res.cloudinary.com/t2nvzeyq/image/upload/v3/3.jpg"
        )
        val pids = listOf("borrowbuddy/items/pid1", "borrowbuddy/items/pid2", "borrowbuddy/items/pid3")
        val data = hashMapOf<String, Any>(
            "title" to "Multi Photo Item",
            "imageUri" to urls[0],
            "imageUris" to urls,
            "imagePublicIds" to pids
        )

        val item = ItemRepository.mapToCampusItem("multi_doc", data)
        assertNotNull(item)
        assertEquals(urls[0], item?.imageUri)
        assertEquals(urls, item?.imageUris)
        assertEquals(pids, item?.imagePublicIds)
        assertEquals(urls[0], item?.primaryImageUrl)
        assertEquals(urls, item?.validImageUrls)
    }

    @Test
    fun testCampusItem_primaryImageUrlSelectionPriority() {
        // Priority 1: first HTTPS URL in imageUris
        val item1 = CampusItem(
            imageUri = "https://example.com/legacy.jpg",
            imageUris = listOf("https://example.com/new_first.jpg", "https://example.com/new_second.jpg")
        )
        assertEquals("https://example.com/new_first.jpg", item1.primaryImageUrl)

        // Priority 2: fallback to legacy imageUri if imageUris empty
        val item2 = CampusItem(
            imageUri = "https://example.com/legacy_only.jpg",
            imageUris = emptyList()
        )
        assertEquals("https://example.com/legacy_only.jpg", item2.primaryImageUrl)

        // Priority 3: empty string if neither is HTTPS
        val item3 = CampusItem(
            imageUri = "content://local/image",
            imageUris = listOf("file:///local/path.jpg")
        )
        assertEquals("", item3.primaryImageUrl)
    }
}
