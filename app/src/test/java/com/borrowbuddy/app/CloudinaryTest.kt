package com.borrowbuddy.app

import com.borrowbuddy.app.util.CloudinaryConfig
import com.borrowbuddy.app.util.CloudinaryUploadException
import com.borrowbuddy.app.util.CloudinaryUploader
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream

class CloudinaryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // =========================================================================
    // 1. CloudinaryConfig Tests
    // =========================================================================

    @Test
    fun testCloudinaryConfig_valuesAreCorrect() {
        assertEquals("t2nvzeyq", CloudinaryConfig.CLOUD_NAME)
        assertEquals("borrowbuddy_profiles", CloudinaryConfig.PROFILE_UPLOAD_PRESET)
        assertEquals("borrowbuddy_items", CloudinaryConfig.ITEM_UPLOAD_PRESET)
    }

    @Test
    fun testCloudinaryConfig_uploadUrlConstruction() {
        val expected = "https://api.cloudinary.com/v1_1/t2nvzeyq/image/upload"
        assertEquals(expected, CloudinaryConfig.getUploadUrl())

        // Custom cloud name support
        val custom = CloudinaryConfig.getUploadUrl("custom_cloud")
        assertEquals("https://api.cloudinary.com/v1_1/custom_cloud/image/upload", custom)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCloudinaryConfig_blankCloudNameThrows() {
        CloudinaryConfig.getUploadUrl("   ")
    }

    @Test
    fun testCloudinaryConfig_presetValidation() {
        assertTrue(CloudinaryConfig.isValidPreset("borrowbuddy_profiles"))
        assertTrue(CloudinaryConfig.isValidPreset("borrowbuddy_items"))
        assertFalse(CloudinaryConfig.isValidPreset("unsigned_preset_xyz"))
        assertFalse(CloudinaryConfig.isValidPreset(null))
        assertFalse(CloudinaryConfig.isValidPreset(""))
        assertFalse(CloudinaryConfig.isValidPreset("   "))
    }

    // =========================================================================
    // 2. CloudinaryUploader Response Parsing Tests
    // =========================================================================

    @Test
    fun testParseUploadResponse_validJsonExtractsAllFields() {
        val json = """
            {
                "secure_url": "https://res.cloudinary.com/t2nvzeyq/image/upload/v12345/borrowbuddy/items/clicker_1.jpg",
                "public_id": "borrowbuddy/items/clicker_1",
                "format": "jpg",
                "width": 800,
                "height": 600,
                "bytes": 94250,
                "created_at": "2026-11-24T18:00:00Z"
            }
        """.trimIndent()

        val result = CloudinaryUploader.parseUploadResponse(json)
        assertEquals("https://res.cloudinary.com/t2nvzeyq/image/upload/v12345/borrowbuddy/items/clicker_1.jpg", result.secureUrl)
        assertEquals("borrowbuddy/items/clicker_1", result.publicId)
        assertEquals("jpg", result.format)
        assertEquals(800, result.width)
        assertEquals(600, result.height)
        assertEquals(94250L, result.bytes)
        assertEquals("2026-11-24T18:00:00Z", result.createdAt)
    }

    @Test
    fun testParseUploadResponse_minimalValidJson() {
        val json = """
            {
                "secure_url": "https://res.cloudinary.com/t2nvzeyq/image/upload/v12345/borrowbuddy/profiles/avatar.png",
                "public_id": "borrowbuddy/profiles/avatar"
            }
        """.trimIndent()

        val result = CloudinaryUploader.parseUploadResponse(json)
        assertEquals("https://res.cloudinary.com/t2nvzeyq/image/upload/v12345/borrowbuddy/profiles/avatar.png", result.secureUrl)
        assertEquals("borrowbuddy/profiles/avatar", result.publicId)
        assertNull(result.format)
        assertNull(result.width)
        assertNull(result.height)
        assertNull(result.bytes)
        assertNull(result.createdAt)
    }

    @Test(expected = CloudinaryUploadException::class)
    fun testParseUploadResponse_missingSecureUrlThrows() {
        val json = """
            {
                "public_id": "borrowbuddy/items/item_1"
            }
        """.trimIndent()
        CloudinaryUploader.parseUploadResponse(json)
    }

    @Test(expected = CloudinaryUploadException::class)
    fun testParseUploadResponse_nonHttpsUrlThrows() {
        val json = """
            {
                "secure_url": "http://res.cloudinary.com/t2nvzeyq/image/upload/v1/item.jpg",
                "public_id": "borrowbuddy/items/item_1"
            }
        """.trimIndent()
        CloudinaryUploader.parseUploadResponse(json)
    }

    @Test(expected = CloudinaryUploadException::class)
    fun testParseUploadResponse_missingPublicIdThrows() {
        val json = """
            {
                "secure_url": "https://res.cloudinary.com/t2nvzeyq/image/upload/v1/item.jpg"
            }
        """.trimIndent()
        CloudinaryUploader.parseUploadResponse(json)
    }

    @Test
    fun testParseUploadResponse_cloudinaryErrorPayload() {
        val json = """
            {
                "error": {
                    "message": "Upload preset must be specified when using unsigned upload"
                }
            }
        """.trimIndent()

        try {
            CloudinaryUploader.parseUploadResponse(json)
            fail("Expected CloudinaryUploadException for error JSON")
        } catch (e: CloudinaryUploadException) {
            assertTrue("Exception message should contain the error detail", e.message?.contains("Upload preset must be specified") == true)
        }
    }

    @Test(expected = CloudinaryUploadException::class)
    fun testParseUploadResponse_malformedJsonThrows() {
        CloudinaryUploader.parseUploadResponse("Not a valid json response string")
    }

    @Test(expected = CloudinaryUploadException::class)
    fun testParseUploadResponse_emptyStringThrows() {
        CloudinaryUploader.parseUploadResponse("")
    }

    // =========================================================================
    // 3. File & Input Validation Tests
    // =========================================================================

    @Test(expected = IllegalArgumentException::class)
    fun testValidateImageFile_nonExistentFileThrows() {
        val nonExistent = File(tempFolder.root, "does_not_exist_file.jpg")
        CloudinaryUploader.validateImageFile(nonExistent)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testValidateImageFile_emptyFileThrows() {
        val emptyFile = tempFolder.newFile("empty_image.png")
        CloudinaryUploader.validateImageFile(emptyFile)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testValidateImageFile_unsupportedFormatThrows() {
        val pdfFile = tempFolder.newFile("document.pdf")
        FileOutputStream(pdfFile).use { it.write("fake pdf content".toByteArray()) }
        CloudinaryUploader.validateImageFile(pdfFile)
    }

    @Test
    fun testValidateImageFile_supportedFormatsPass() {
        val extensions = listOf("jpg", "jpeg", "png", "webp", "JPG", "PNG", "WEBP")
        extensions.forEachIndexed { index, ext ->
            val file = tempFolder.newFile("sample_format_${index}_file.$ext")
            FileOutputStream(file).use { it.write("fake image binary content".toByteArray()) }
            // Should not throw
            CloudinaryUploader.validateImageFile(file)
        }
    }

    @Test
    fun testValidatePreset() {
        CloudinaryUploader.validatePreset("borrowbuddy_profiles")
        CloudinaryUploader.validatePreset("borrowbuddy_items")

        try {
            CloudinaryUploader.validatePreset("random_preset")
            fail("Expected IllegalArgumentException for unknown preset")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invalid or unrecognized upload preset") == true)
        }
    }

    @Test
    fun testMediaTypeResolution() {
        assertEquals("image/jpeg", CloudinaryUploader.getMediaTypeForExtension("jpg").toString())
        assertEquals("image/jpeg", CloudinaryUploader.getMediaTypeForExtension("jpeg").toString())
        assertEquals("image/png", CloudinaryUploader.getMediaTypeForExtension("png").toString())
        assertEquals("image/webp", CloudinaryUploader.getMediaTypeForExtension("webp").toString())
        assertEquals("image/jpeg", CloudinaryUploader.getMediaTypeForExtension("JPG").toString())
    }
}
