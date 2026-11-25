package com.borrowbuddy.app.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Data class encapsulating the successful Cloudinary upload response.
 * Guaranteed to provide a valid HTTPS URL and non-empty public ID.
 */
data class CloudinaryUploadResult(
    val secureUrl: String,
    val publicId: String,
    val format: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bytes: Long? = null,
    val createdAt: String? = null
)

/**
 * Custom exception representing a Cloudinary upload or validation failure.
 */
class CloudinaryUploadException(
    message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Reusable utility for uploading image files to Cloudinary via direct HTTPS multipart POST.
 *
 * ARCHITECTURAL CONSTRAINTS:
 * 1. Unsigned uploads only (uses upload_preset).
 * 2. Overwrite is disabled; unique filenames are enabled on Cloudinary.
 * 3. Presets disallow custom public IDs; never sends a public_id parameter.
 * 4. Maximum client-side upload size limit of 5 MB.
 * 5. Strictly no Cloudinary API Secret or master credentials.
 */
object CloudinaryUploader {

    const val MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024 // 5 MB
    val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    // Reusable OkHttpClient with connection pooling and reasonable timeouts
    val defaultHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Resolves the appropriate MediaType for supported image file extensions.
     */
    fun getMediaTypeForExtension(extension: String): MediaType {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg".toMediaType()
            "png" -> "image/png".toMediaType()
            "webp" -> "image/webp".toMediaType()
            else -> "application/octet-stream".toMediaType()
        }
    }

    /**
     * Validates that an image file exists, is readable, has non-zero length,
     * matches supported image formats (JPEG, JPG, PNG, WebP), and is within the 5 MB limit.
     * Throws [IllegalArgumentException] on validation failure.
     */
    fun validateImageFile(file: File) {
        if (!file.exists()) {
            throw IllegalArgumentException("Image file does not exist: ${file.absolutePath}")
        }
        if (!file.canRead()) {
            throw IllegalArgumentException("Image file cannot be read: ${file.absolutePath}")
        }
        val fileLength = file.length()
        if (fileLength <= 0L) {
            throw IllegalArgumentException("Image file is empty (0 bytes): ${file.name}")
        }
        if (fileLength > MAX_FILE_SIZE_BYTES) {
            val sizeMb = String.format(java.util.Locale.US, "%.2f", fileLength / (1024.0 * 1024.0))
            throw IllegalArgumentException("Image file exceeds the 5 MB limit ($sizeMb MB): ${file.name}")
        }
        val extension = file.extension.lowercase()
        if (extension !in SUPPORTED_EXTENSIONS) {
            throw IllegalArgumentException("Unsupported image format: '.$extension'. Allowed formats: ${SUPPORTED_EXTENSIONS.joinToString()}")
        }
    }

    /**
     * Validates that an upload preset is recognized and allowed.
     */
    fun validatePreset(preset: String) {
        if (!CloudinaryConfig.isValidPreset(preset)) {
            throw IllegalArgumentException("Invalid or unrecognized upload preset: '$preset'. Expected '${CloudinaryConfig.PROFILE_UPLOAD_PRESET}' or '${CloudinaryConfig.ITEM_UPLOAD_PRESET}'.")
        }
    }

    /**
     * Parses and validates a JSON response string returned by Cloudinary.
     * Isolatable for deterministic unit testing without requiring network calls.
     *
     * @throws CloudinaryUploadException if the response contains an error, is malformed,
     * or lacks a valid secure HTTPS URL or public ID.
     */
    fun parseUploadResponse(jsonString: String): CloudinaryUploadResult {
        if (jsonString.isBlank()) {
            throw CloudinaryUploadException("Empty response body received from Cloudinary")
        }

        val json: JSONObject = try {
            JSONObject(jsonString)
        } catch (e: JSONException) {
            throw CloudinaryUploadException("Malformed JSON response from Cloudinary: ${e.message}", cause = e)
        }

        // Check for Cloudinary error payload: {"error": {"message": "..."}}
        if (json.has("error")) {
            val errorObj = json.optJSONObject("error")
            val errorMessage = errorObj?.optString("message") ?: "Cloudinary upload was rejected"
            throw CloudinaryUploadException("Cloudinary rejected upload: $errorMessage")
        }

        // Validate secure_url
        val secureUrl = json.optString("secure_url").trim()
        if (secureUrl.isEmpty()) {
            throw CloudinaryUploadException("Cloudinary response missing required 'secure_url'")
        }
        if (!secureUrl.startsWith("https://", ignoreCase = true)) {
            throw CloudinaryUploadException("Cloudinary 'secure_url' must use HTTPS protocol: '$secureUrl'")
        }

        // Validate public_id
        val publicId = json.optString("public_id").trim()
        if (publicId.isEmpty()) {
            throw CloudinaryUploadException("Cloudinary response missing required 'public_id'")
        }

        val format = if (json.has("format")) json.optString("format").takeIf { it.isNotBlank() } else null
        val width = if (json.has("width")) json.optInt("width").takeIf { it > 0 } else null
        val height = if (json.has("height")) json.optInt("height").takeIf { it > 0 } else null
        val bytes = if (json.has("bytes")) json.optLong("bytes").takeIf { it > 0 } else null
        val createdAt = if (json.has("created_at")) json.optString("created_at").takeIf { it.isNotBlank() } else null

        return CloudinaryUploadResult(
            secureUrl = secureUrl,
            publicId = publicId,
            format = format,
            width = width,
            height = height,
            bytes = bytes,
            createdAt = createdAt
        )
    }

    /**
     * Executes an unsigned multipart HTTPS upload of an image file to Cloudinary.
     *
     * @param file The image file on disk to upload.
     * @param preset The Cloudinary unsigned upload preset ([CloudinaryConfig.PROFILE_UPLOAD_PRESET] or [CloudinaryConfig.ITEM_UPLOAD_PRESET]).
     * @param client Optional OkHttpClient for network execution (defaults to [defaultHttpClient]).
     * @return [Result.success] with [CloudinaryUploadResult] or [Result.failure] with error details.
     */
    suspend fun uploadImage(
        file: File,
        preset: String,
        client: OkHttpClient = defaultHttpClient
    ): Result<CloudinaryUploadResult> = withContext(Dispatchers.IO) {
        try {
            // 1. Client-side input validation
            validateImageFile(file)
            validatePreset(preset)

            // 2. Coroutine cancellation checkpoint
            ensureActive()

            // 3. Construct multipart request payload
            val mediaType = getMediaTypeForExtension(file.extension)
            val fileBody = file.asRequestBody(mediaType)

            // Strictly do NOT send custom public_id; presets disallow it
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("upload_preset", preset)
                .addFormDataPart("file", file.name, fileBody)
                .build()

            val uploadUrl = CloudinaryConfig.getUploadUrl()
            val request = Request.Builder()
                .url(uploadUrl)
                .post(multipartBody)
                .build()

            // 4. Coroutine cancellation checkpoint before I/O
            ensureActive()

            // 5. Execute HTTP call
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val parsedCloudMessage = try {
                    JSONObject(responseBody).optJSONObject("error")?.optString("message")
                } catch (_: Exception) {
                    null
                }
                val errorDetails = parsedCloudMessage ?: "HTTP ${response.code}: ${response.message.ifBlank { "Upload failed" }}"
                throw CloudinaryUploadException("Cloudinary upload failed: $errorDetails", response.code)
            }

            // 6. Parse and validate Cloudinary response payload
            val result = parseUploadResponse(responseBody)
            Result.success(result)
        } catch (ce: CancellationException) {
            // Never swallow Coroutine CancellationException
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
