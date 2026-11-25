package com.borrowbuddy.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import java.io.File
import java.io.FileOutputStream

/**
 * Utility helper for central profile avatar management:
 * - Remote Cloudinary profile image loading via Glide with disk and memory caching
 * - Local image persistence (saving, loading, deletion) strictly within app internal storage
 * - UID-scoped filenames: `profile_avatar_{uid}.jpg`
 * - Memory-efficient bitmap downsampling to prevent out-of-memory errors
 * - Dynamic fallback to student initials when no photo is configured or load fails
 */
object AvatarHelper {

    private const val AVATAR_DIR = "profile_photos"
    private const val AVATAR_FILE_PREFIX = "profile_avatar_"
    private const val AVATAR_FILE_EXT = ".jpg"
    private const val MAX_DIMENSION = 800

    /**
     * Resolves the UID-specific profile avatar filename.
     * Never falls back to a shared file when UID is null or empty.
     */
    fun resolveAvatarFileName(uid: String?): String? {
        val cleanUid = uid?.trim().orEmpty()
        if (cleanUid.isEmpty()) return null
        return "$AVATAR_FILE_PREFIX$cleanUid$AVATAR_FILE_EXT"
    }

    /**
     * Generate 1-2 uppercase initials from a student's full name.
     */
    fun getInitials(name: String): String {
        return name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifBlank { "HM" }
    }

    /**
     * Safely clears any active or previous Glide request associated with the ImageView.
     * Prevents recycled ImageViews from displaying stale or in-flight requests.
     */
    fun clearGlide(context: Context, imageView: ImageView?) {
        if (imageView == null) return
        try {
            if (context is android.app.Activity && (context.isDestroyed || context.isFinishing)) {
                return
            }
            Glide.with(context).clear(imageView)
        } catch (_: Exception) {
            // Context destroyed or running in headless unit test
        }
    }

    /**
     * Safely load an avatar into an ImageView, or fallback to the initials TextView.
     * Supports:
     * 1. Remote Cloudinary HTTPS URLs (rendered with Glide, cached to disk/memory)
     * 2. Local internal storage profile photos (UID-scoped: `profile_avatar_{uid}.jpg`)
     * 3. Offline fallback from remote HTTPS URL to local UID-scoped avatar file
     * 4. Dynamic initials fallback when no photo is configured or loading fails
     */
    fun loadAvatar(
        context: Context,
        photoUri: String?,
        photoImageView: ImageView?,
        initialsTextView: TextView?,
        name: String,
        targetSizeDp: Int = 80,
        uid: String? = null
    ) {
        val initials = getInitials(name)
        initialsTextView?.text = initials

        // 1. If photoUri is a remote HTTPS Cloudinary URL, load via Glide
        if (!photoUri.isNullOrBlank() && photoUri.startsWith("https://", ignoreCase = true)) {
            try {
                // Show initials while loading begins
                initialsTextView?.visibility = View.VISIBLE
                photoImageView?.visibility = View.INVISIBLE

                Glide.with(context)
                    .load(photoUri)
                    .circleCrop()
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            // Remote load failed (device offline or network error):
                            // Fall back to the UID-specific local avatar file before showing initials
                            loadLocalAvatarOrInitials(
                                context = context,
                                photoImageView = photoImageView,
                                initialsTextView = initialsTextView,
                                targetSizeDp = targetSizeDp,
                                uid = uid
                            )
                            return true
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            photoImageView?.visibility = View.VISIBLE
                            initialsTextView?.visibility = View.GONE
                            return false
                        }
                    })
                    .into(photoImageView ?: return)
                return
            } catch (_: Exception) {
                // Headless test or destroyed context fallback
                loadLocalAvatarOrInitials(
                    context = context,
                    photoImageView = photoImageView,
                    initialsTextView = initialsTextView,
                    targetSizeDp = targetSizeDp,
                    uid = uid
                )
                return
            }
        }

        // 2. Fallback: attempt to resolve local file from user's UID on local disk
        loadLocalAvatarOrInitials(
            context = context,
            photoImageView = photoImageView,
            initialsTextView = initialsTextView,
            targetSizeDp = targetSizeDp,
            uid = uid,
            fallbackPath = photoUri
        )
    }

    /**
     * Attempts to load a UID-scoped local avatar file (`profile_avatar_{uid}.jpg`).
     * Loads via Glide with circular crop and background decoding to prevent main-thread lag.
     * Falls back to initials if the local file does not exist, cannot be decoded, or UID is empty.
     */
    private fun loadLocalAvatarOrInitials(
        context: Context,
        photoImageView: ImageView?,
        initialsTextView: TextView?,
        targetSizeDp: Int,
        uid: String?,
        fallbackPath: String? = null
    ) {
        val cleanUid = uid?.trim().orEmpty()
        val resolvedLocalPath = if (cleanUid.isNotEmpty()) {
            getLocalProfilePhotoPath(context, cleanUid)
        } else if (!fallbackPath.isNullOrBlank() && !fallbackPath.startsWith("http://", ignoreCase = true) && !fallbackPath.startsWith("https://", ignoreCase = true)) {
            try {
                val parsed = Uri.parse(fallbackPath)
                if (parsed.scheme == "file") parsed.path ?: fallbackPath else fallbackPath
            } catch (_: Exception) {
                fallbackPath
            }
        } else {
            null
        }

        if (!resolvedLocalPath.isNullOrBlank()) {
            val localFile = File(resolvedLocalPath)
            if (localFile.exists() && localFile.canRead() && localFile.length() > 0) {
                // Clear any prior Glide request before switching to local avatar
                clearGlide(context, photoImageView)

                try {
                    Glide.with(context)
                        .load(localFile)
                        .circleCrop()
                        .listener(object : RequestListener<Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<Drawable>,
                                isFirstResource: Boolean
                            ): Boolean {
                                showInitialsFallback(context, photoImageView, initialsTextView)
                                return true
                            }

                            override fun onResourceReady(
                                resource: Drawable,
                                model: Any,
                                target: Target<Drawable>?,
                                dataSource: DataSource,
                                isFirstResource: Boolean
                            ): Boolean {
                                photoImageView?.visibility = View.VISIBLE
                                initialsTextView?.visibility = View.GONE
                                return false
                            }
                        })
                        .into(photoImageView ?: return)
                    return
                } catch (_: Exception) {
                    // Fallback to sampled bitmap decoding if Glide fails (e.g. unit test or destroyed context)
                    val density = try { context.resources.displayMetrics.density } catch (_: Exception) { 1f }
                    val targetPx = (targetSizeDp * density).toInt().coerceAtLeast(64)
                    val bitmap = decodeSampledBitmap(localFile, targetPx, targetPx)
                    if (bitmap != null) {
                        photoImageView?.setImageBitmap(bitmap)
                        photoImageView?.visibility = View.VISIBLE
                        initialsTextView?.visibility = View.GONE
                        return
                    }
                }
            }
        }

        // 3. Fallback to initials if no valid image exists
        showInitialsFallback(context, photoImageView, initialsTextView)
    }

    /**
     * Clears previous Glide request, resets ImageView drawable, and displays initials.
     */
    private fun showInitialsFallback(
        context: Context,
        photoImageView: ImageView?,
        initialsTextView: TextView?
    ) {
        clearGlide(context, photoImageView)
        photoImageView?.setImageDrawable(null)
        photoImageView?.visibility = View.GONE
        initialsTextView?.visibility = View.VISIBLE
    }

    /**
     * Reads from a photo picker URI, decodes with downsampling (max 800px), and produces
     * a temporary compressed JPEG file in the application's cache directory ready for Cloudinary upload.
     * Returns null if reading or decoding fails.
     */
    fun prepareUploadFileFromUri(context: Context, sourceUri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            val scaledBitmap = if (originalBitmap.width > MAX_DIMENSION || originalBitmap.height > MAX_DIMENSION) {
                val ratio = minOf(
                    MAX_DIMENSION.toFloat() / originalBitmap.width,
                    MAX_DIMENSION.toFloat() / originalBitmap.height
                )
                val targetW = (originalBitmap.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (originalBitmap.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true).also {
                    if (it != originalBitmap) originalBitmap.recycle()
                }
            } else {
                originalBitmap
            }

            val tempFile = File(context.cacheDir, "upload_avatar_${System.currentTimeMillis()}.jpg")
            val fos = FileOutputStream(tempFile)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            fos.flush()
            fos.close()
            scaledBitmap.recycle()

            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Read from a photo picker URI, decode with downsampling, and persist to app internal storage
     * using a UID-specific filename (`profile_avatar_{uid}.jpg`).
     * Returns the absolute path of the persisted image file, or null on failure / missing UID.
     */
    fun saveProfilePhotoFromUri(context: Context, sourceUri: Uri, uid: String?): String? {
        val fileName = resolveAvatarFileName(uid) ?: return null
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null

            val scaledBitmap = if (originalBitmap.width > MAX_DIMENSION || originalBitmap.height > MAX_DIMENSION) {
                val ratio = minOf(
                    MAX_DIMENSION.toFloat() / originalBitmap.width,
                    MAX_DIMENSION.toFloat() / originalBitmap.height
                )
                val targetW = (originalBitmap.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (originalBitmap.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)
            } else {
                originalBitmap
            }

            val dir = File(context.filesDir, AVATAR_DIR)
            if (!dir.exists()) dir.mkdirs()

            val targetFile = File(dir, fileName)
            val fos = FileOutputStream(targetFile)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            fos.flush()
            fos.close()

            targetFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Delete the locally persisted profile photo file for a specific UID.
     * Does NOT affect any other user's photo file.
     */
    fun deleteProfilePhoto(context: Context, uid: String?): Boolean {
        val fileName = resolveAvatarFileName(uid) ?: return false
        return try {
            val targetFile = File(context.filesDir, "$AVATAR_DIR/$fileName")
            if (targetFile.exists()) {
                targetFile.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if a locally persisted profile photo exists for a specific UID and return its absolute path.
     * Never falls back to a shared file for null or missing UIDs.
     */
    fun getLocalProfilePhotoPath(context: Context, uid: String?): String? {
        val fileName = resolveAvatarFileName(uid) ?: return null
        val targetFile = File(context.filesDir, "$AVATAR_DIR/$fileName")
        return if (targetFile.exists() && targetFile.canRead() && targetFile.length() > 0) {
            targetFile.absolutePath
        } else {
            null
        }
    }

    private fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (!file.exists() || !file.canRead()) return null
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
