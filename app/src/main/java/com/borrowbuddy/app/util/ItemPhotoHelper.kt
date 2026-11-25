package com.borrowbuddy.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.View
import android.widget.ImageView
import com.borrowbuddy.app.R
import com.borrowbuddy.app.model.CampusItem
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import java.io.File
import java.io.FileOutputStream

/**
 * Utility helper for central item photo management:
 * - Remote Cloudinary HTTPS image loading via Glide with disk and memory caching
 * - Image downsampling and temporary file preparation for unsigned Cloudinary uploads
 * - Local image persistence strictly within internal app storage (`context.filesDir/item_photos/item_<itemId>.jpg`)
 * - Item ID-scoped filenames to avoid collisions
 * - Graceful fallback to bundled drawables or category icons when remote/local photos are absent or fail
 */
object ItemPhotoHelper {

    private const val ITEM_PHOTOS_DIR = "item_photos"
    private const val ITEM_FILE_PREFIX = "item_"
    private const val ITEM_FILE_EXT = ".jpg"
    private const val MAX_DIMENSION = 800

    /**
     * Resolves the item-specific photo filename.
     */
    fun resolveItemPhotoFileName(itemId: String?): String? {
        val cleanId = itemId?.trim().orEmpty()
        if (cleanId.isEmpty()) return null
        return "$ITEM_FILE_PREFIX$cleanId$ITEM_FILE_EXT"
    }

    /**
     * Directory for persisted item photos inside internal filesDir.
     */
    fun getItemPhotosDir(context: Context): File {
        val dir = File(context.filesDir, ITEM_PHOTOS_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Returns the absolute path of the local item photo file if it exists and has content.
     */
    fun getLocalItemPhotoPath(context: Context, itemId: String?): String? {
        val fileName = resolveItemPhotoFileName(itemId) ?: return null
        val file = File(getItemPhotosDir(context), fileName)
        return if (file.exists() && file.canRead() && file.length() > 0) {
            file.absolutePath
        } else {
            null
        }
    }

    /**
     * Reads a selected photo URI, downsamples to MAX_DIMENSION (800px), and compresses to JPEG
     * (~88 quality) into a temporary cache file ready for unsigned Cloudinary upload.
     * Returns null if reading or decoding fails.
     */
    fun prepareUploadFileFromUri(context: Context, sourceUri: Uri, index: Int = 0): File? {
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

            val tempFile = File(context.cacheDir, "upload_item_${System.currentTimeMillis()}_$index.jpg")
            val fos = FileOutputStream(tempFile)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 88, fos)
            fos.flush()
            fos.close()
            scaledBitmap.recycle()

            if (tempFile.exists() && tempFile.length() > 0) tempFile else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Read from a photo picker or gallery URI, decode with downsampling, and persist to app internal storage
     * using item ID-scoped filename (`item_<itemId>.jpg`).
     * Returns the absolute path of the persisted image file, or null on failure.
     */
    fun saveItemPhotoFromUri(context: Context, sourceUri: Uri, itemId: String?): String? {
        val fileName = resolveItemPhotoFileName(itemId) ?: return null
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

            val dir = getItemPhotosDir(context)
            val destinationFile = File(dir, fileName)
            val outputStream = FileOutputStream(destinationFile)
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.flush()
            outputStream.close()
            scaledBitmap.recycle()

            destinationFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Deletes the item's locally stored photo file if present.
     */
    fun deleteItemPhoto(context: Context, itemId: String?): Boolean {
        val fileName = resolveItemPhotoFileName(itemId) ?: return false
        val file = File(getItemPhotosDir(context), fileName)
        return if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }

    /**
     * Safely clears any active or previous Glide request associated with the ImageView.
     */
    fun clearGlide(context: Context, imageView: ImageView?) {
        if (imageView == null) return
        try {
            if (context is android.app.Activity && (context.isDestroyed || context.isFinishing)) {
                return
            }
            Glide.with(context).clear(imageView)
        } catch (_: Exception) {
            // Ignored in headless tests or destroyed context
        }
    }

    /**
     * Memory-safe bitmap decoding with inSampleSize subsampling.
     */
    fun decodeSampledBitmap(file: File, reqWidth: Int, reqHeight: Int): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, boundsOptions)

            var sampleSize = 1
            if (boundsOptions.outHeight > reqHeight || boundsOptions.outWidth > reqWidth) {
                val halfHeight = boundsOptions.outHeight / 2
                val halfWidth = boundsOptions.outWidth / 2
                while ((halfHeight / sampleSize) >= reqHeight && (halfWidth / sampleSize) >= reqWidth) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Central item image loader:
     * 1. If item has a valid remote HTTPS URL ([CampusItem.primaryImageUrl]), load via Glide with disk caching.
     * 2. If remote load is absent or fails (offline), fall back to local on-device file `item_<itemId>.jpg`.
     * 3. Fall back to bundled drawable or category icon placeholder.
     * 4. Properly clears previous Glide requests to prevent recycled view artifacting.
     */
    fun loadItemPhoto(
        context: Context,
        imageView: ImageView?,
        item: CampusItem,
        fallbackContainer: View? = null,
        fallbackIcon: ImageView? = null,
        targetSizeDp: Int = 140
    ) {
        if (imageView == null) return

        val remoteUrl = item.primaryImageUrl
        if (remoteUrl.isNotBlank()) {
            try {
                imageView.visibility = View.VISIBLE
                fallbackContainer?.visibility = View.GONE

                Glide.with(context)
                    .load(remoteUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.bg_image_fallback)
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            loadLocalOrFallback(context, imageView, item, fallbackContainer, fallbackIcon, targetSizeDp)
                            return true
                        }

                        override fun onResourceReady(
                            resource: Drawable,
                            model: Any,
                            target: Target<Drawable>?,
                            dataSource: DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            imageView.visibility = View.VISIBLE
                            fallbackContainer?.visibility = View.GONE
                            return false
                        }
                    })
                    .into(imageView)
                return
            } catch (_: Exception) {
                // Glide unavailable or activity destroyed
            }
        }

        // Remote URL absent or failed to initiate -> local / fallback
        loadLocalOrFallback(context, imageView, item, fallbackContainer, fallbackIcon, targetSizeDp)
    }

    private fun loadLocalOrFallback(
        context: Context,
        imageView: ImageView,
        item: CampusItem,
        fallbackContainer: View?,
        fallbackIcon: ImageView?,
        targetSizeDp: Int
    ) {
        clearGlide(context, imageView)

        val localPath = getLocalItemPhotoPath(context, item.id)
        if (!localPath.isNullOrBlank()) {
            val localFile = File(localPath)
            val density = try { context.resources.displayMetrics.density } catch (_: Exception) { 1f }
            val targetPx = (targetSizeDp * density).toInt().coerceAtLeast(64)
            val bitmap = decodeSampledBitmap(localFile, targetPx, targetPx)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                imageView.visibility = View.VISIBLE
                fallbackContainer?.visibility = View.GONE
                return
            }
        }

        // Bundled drawable fallback
        val res = if (item.drawableRes != 0) item.drawableRes else getFallbackDrawable(item)
        if (res != 0) {
            imageView.setImageResource(res)
            imageView.visibility = View.VISIBLE
            fallbackContainer?.visibility = View.GONE
        } else if (fallbackContainer != null && fallbackIcon != null) {
            imageView.visibility = View.GONE
            fallbackContainer.visibility = View.VISIBLE
            fallbackIcon.setImageResource(getCategoryIcon(item.category))
        } else {
            imageView.setImageResource(R.drawable.bg_image_fallback)
            imageView.visibility = View.VISIBLE
            fallbackContainer?.visibility = View.GONE
        }
    }

    /**
     * Resolves an appropriate bundled fallback drawable for an item based on its title and category.
     */
    fun getFallbackDrawable(item: CampusItem): Int {
        val titleLower = item.title.lowercase()
        val descLower = item.description.lowercase()
        val cat = item.category.uppercase()

        return when {
            titleLower.contains("calc") || descLower.contains("calc") -> R.drawable.item_calculator
            titleLower.contains("dongle") || titleLower.contains("hdmi") || titleLower.contains("hub") || descLower.contains("type-c") -> R.drawable.item_dongle
            titleLower.contains("note") || titleLower.contains("dsa") || titleLower.contains("study") -> R.drawable.item_notes
            titleLower.contains("arduino") || titleLower.contains("robot") || titleLower.contains("sensor") -> R.drawable.item_arduino
            titleLower.contains("coat") || titleLower.contains("apron") || titleLower.contains("lab") -> R.drawable.item_lab_coat
            titleLower.contains("tripod") || titleLower.contains("camera") || titleLower.contains("mount") -> R.drawable.item_tripod
            titleLower.contains("cable") || titleLower.contains("charger") || titleLower.contains("usb") -> R.drawable.item_cables
            titleLower.contains("book") || titleLower.contains("dbms") || titleLower.contains("concept") -> R.drawable.item_dbms_book
            titleLower.contains("badminton") || titleLower.contains("racket") || titleLower.contains("sport") -> R.drawable.item_badminton
            titleLower.contains("clicker") || titleLower.contains("presenter") || titleLower.contains("remote") -> R.drawable.item_clicker
            cat == "BOOKS" -> R.drawable.item_notes
            cat == "LAB" -> R.drawable.item_lab_coat
            cat == "EVENT" -> R.drawable.item_tripod
            cat == "SPORTS" -> R.drawable.item_badminton
            cat == "STATIONERY" -> R.drawable.item_calculator
            else -> R.drawable.item_calculator
        }
    }

    /**
     * Resolves category icon for soft fallback placeholder.
     */
    fun getCategoryIcon(category: String): Int {
        return when (category.uppercase()) {
            "TECH" -> R.drawable.ic_cat_tech
            "BOOKS" -> R.drawable.ic_cat_books
            "LAB" -> R.drawable.ic_cat_lab
            "EVENT" -> R.drawable.ic_cat_event
            "STATIONERY" -> R.drawable.ic_cat_stationery
            "SPORTS" -> R.drawable.ic_cat_sports
            "BUSINESS" -> R.drawable.ic_cat_business
            "CAMPUS" -> R.drawable.ic_cat_campus
            else -> R.drawable.ic_cat_tech
        }
    }

    /**
     * Legacy overload: safely load a local item photo into an ImageView, or fallback to a resource drawable.
     */
    fun loadItemPhoto(
        context: Context,
        imageView: ImageView?,
        itemId: String?,
        fallbackDrawableRes: Int = 0,
        targetSizeDp: Int = 120
    ): Boolean {
        if (imageView == null) return false

        clearGlide(context, imageView)

        val localPath = getLocalItemPhotoPath(context, itemId)
        if (!localPath.isNullOrBlank()) {
            val localFile = File(localPath)
            val density = try { context.resources.displayMetrics.density } catch (_: Exception) { 1f }
            val targetPx = (targetSizeDp * density).toInt().coerceAtLeast(64)
            val bitmap = decodeSampledBitmap(localFile, targetPx, targetPx)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                return true
            }
        }

        if (fallbackDrawableRes != 0) {
            try {
                imageView.setImageResource(fallbackDrawableRes)
            } catch (_: Exception) {
                imageView.setImageDrawable(null)
            }
        } else {
            imageView.setImageDrawable(null)
        }
        return false
    }
}
