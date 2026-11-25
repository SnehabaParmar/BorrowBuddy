package com.borrowbuddy.app.util

/**
 * Central configuration for BorrowBuddy Cloudinary integration.
 *
 * ARCHITECTURAL CONSTRAINTS:
 * 1. Uses direct HTTPS unsigned uploads.
 * 2. Strictly NO Cloudinary API Secret or master signing credentials embedded in Android code.
 * 3. Overwrite is disabled; unique filenames are enabled on Cloudinary.
 * 4. Presets disallow custom public IDs.
 */
object CloudinaryConfig {

    const val CLOUD_NAME = "t2nvzeyq"
    const val PROFILE_UPLOAD_PRESET = "borrowbuddy_profiles"
    const val ITEM_UPLOAD_PRESET = "borrowbuddy_items"

    private const val BASE_API_URL = "https://api.cloudinary.com/v1_1"

    /**
     * Constructs the unsigned image upload REST endpoint URL for Cloudinary.
     * Format: https://api.cloudinary.com/v1_1/{cloud_name}/image/upload
     */
    fun getUploadUrl(cloudName: String = CLOUD_NAME): String {
        require(cloudName.isNotBlank()) { "Cloudinary cloud name must not be blank" }
        return "$BASE_API_URL/$cloudName/image/upload"
    }

    /**
     * Validates whether a given preset is one of the recognized BorrowBuddy unsigned presets.
     */
    fun isValidPreset(preset: String?): Boolean {
        return preset == PROFILE_UPLOAD_PRESET || preset == ITEM_UPLOAD_PRESET
    }
}
