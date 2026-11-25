package com.borrowbuddy.app

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.borrowbuddy.app.adapter.SelectedPhotoAdapter
import com.borrowbuddy.app.databinding.ActivityListItemBinding
import com.borrowbuddy.app.model.CampusItem
import com.borrowbuddy.app.model.ItemRepository
import com.borrowbuddy.app.model.StudentProfileRepository
import com.borrowbuddy.app.util.CloudinaryConfig
import com.borrowbuddy.app.util.CloudinaryUploader
import com.borrowbuddy.app.util.ItemPhotoHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

class ListItemActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListItemBinding
    private val selectedPhotos = mutableListOf<Uri>()
    private lateinit var photoAdapter: SelectedPhotoAdapter

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    // Multi-Image Picker Launcher (enforcing maximum of 3 item photos)
    private val pickMultipleImagesLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri>? ->
            if (!uris.isNullOrEmpty()) {
                val availableSlots = 3 - selectedPhotos.size
                if (availableSlots <= 0) {
                    Toast.makeText(this, "Maximum 3 photos allowed per listing", Toast.LENGTH_SHORT).show()
                    return@registerForActivityResult
                }
                val toAdd = uris.take(availableSlots)
                if (uris.size > availableSlots) {
                    Toast.makeText(this, "Only up to 3 photos can be selected", Toast.LENGTH_SHORT).show()
                }
                selectedPhotos.addAll(toAdd)
                photoAdapter.notifyDataSetChanged()
                binding.rvSelectedPhotos.visibility = View.VISIBLE
            }
        }

    // Availability State
    private var availDateStr = "Today"
    private var availStartTimeStr = "9:00 AM"
    private var availEndTimeStr = "5:00 PM"
    private var availStartDateStr = "Today"
    private var availEndDateStr = "In 3 Days"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListItemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHeader()
        setupPhotoPicker()
        setupPricingToggle()
        setupAvailabilityControls()
        setupPublishAction()
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupPhotoPicker() {
        photoAdapter = SelectedPhotoAdapter(selectedPhotos) { position ->
            if (position in selectedPhotos.indices) {
                selectedPhotos.removeAt(position)
                photoAdapter.notifyItemRemoved(position)
                photoAdapter.notifyItemRangeChanged(position, selectedPhotos.size - position)
                if (selectedPhotos.isEmpty()) {
                    binding.rvSelectedPhotos.visibility = View.GONE
                }
            }
        }

        binding.rvSelectedPhotos.apply {
            layoutManager = LinearLayoutManager(this@ListItemActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = photoAdapter
        }

        binding.cardImagePicker.setOnClickListener {
            if (selectedPhotos.size >= 3) {
                Toast.makeText(this, "Maximum 3 photos reached. Remove a photo to replace.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pickMultipleImagesLauncher.launch("image/*")
        }
    }

    private fun setupPricingToggle() {
        binding.rgPricing.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbPaid) {
                binding.layoutPaidInputs.visibility = View.VISIBLE
                binding.layoutFreeIndicator.visibility = View.GONE
            } else {
                binding.layoutPaidInputs.visibility = View.GONE
                binding.layoutFreeIndicator.visibility = View.VISIBLE
            }
        }
    }

    private fun setupAvailabilityControls() {
        binding.rgAvailability.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbAvailTime -> {
                    binding.layoutAvailTimePicker.visibility = View.VISIBLE
                    binding.layoutAvailDatePicker.visibility = View.GONE
                }
                R.id.rbAvailDate -> {
                    binding.layoutAvailTimePicker.visibility = View.GONE
                    binding.layoutAvailDatePicker.visibility = View.VISIBLE
                }
                else -> {
                    // Manual pause
                    binding.layoutAvailTimePicker.visibility = View.GONE
                    binding.layoutAvailDatePicker.visibility = View.GONE
                }
            }
        }

        val cal = Calendar.getInstance()

        // Time Mode Pickers
        binding.btnAvailTimeDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    availDateStr = "$dayOfMonth/${month + 1}/$year"
                    binding.btnAvailTimeDate.text = "Date: $availDateStr"
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.btnAvailStartTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    val amPm = if (hourOfDay < 12) "AM" else "PM"
                    val hour12 = if (hourOfDay % 12 == 0) 12 else hourOfDay % 12
                    val minStr = if (minute < 10) "0$minute" else "$minute"
                    availStartTimeStr = "$hour12:$minStr $amPm"
                    binding.btnAvailStartTime.text = "From: $availStartTimeStr"
                },
                9, 0, false
            ).show()
        }

        binding.btnAvailEndTime.setOnClickListener {
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    val amPm = if (hourOfDay < 12) "AM" else "PM"
                    val hour12 = if (hourOfDay % 12 == 0) 12 else hourOfDay % 12
                    val minStr = if (minute < 10) "0$minute" else "$minute"
                    availEndTimeStr = "$hour12:$minStr $amPm"
                    binding.btnAvailEndTime.text = "Until: $availEndTimeStr"
                },
                17, 0, false
            ).show()
        }

        // Date Mode Pickers
        binding.btnAvailStartDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    availStartDateStr = "$dayOfMonth/${month + 1}/$year"
                    binding.btnAvailStartDate.text = "Start: $availStartDateStr"
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.btnAvailEndDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    availEndDateStr = "$dayOfMonth/${month + 1}/$year"
                    binding.btnAvailEndDate.text = "End: $availEndDateStr"
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH) + 3
            ).show()
        }
    }

    private fun setupPublishAction() {
        binding.btnPublishItem.setOnClickListener {
            handlePublish()
        }
    }

    private fun handlePublish() {
        val itemName = binding.etItemName.text?.toString()?.trim().orEmpty()
        val location = binding.etLendingLocation.text?.toString()?.trim().orEmpty()
        val description = binding.etDescription.text?.toString()?.trim().orEmpty()
        val brandModel = binding.etBrandModel.text?.toString()?.trim().orEmpty()

        // Mandatory Photo Check: At least one item photo required
        if (selectedPhotos.isEmpty()) {
            Toast.makeText(this, "Please upload at least one item photo", Toast.LENGTH_SHORT).show()
            binding.cardImagePicker.requestFocus()
            return
        }

        if (itemName.isEmpty()) {
            binding.etItemName.error = "Please enter item name"
            binding.etItemName.requestFocus()
            return
        }

        if (location.isEmpty()) {
            binding.etLendingLocation.error = "Please specify lending location"
            binding.etLendingLocation.requestFocus()
            return
        }

        val isPaid = binding.rbPaid.isChecked
        val dailyPriceStr = binding.etDailyPrice.text?.toString()?.trim().orEmpty()
        if (isPaid && (dailyPriceStr.isEmpty() || (dailyPriceStr.toIntOrNull() ?: 0) <= 0)) {
            binding.etDailyPrice.error = "Please enter valid daily price"
            binding.etDailyPrice.requestFocus()
            return
        }

        val priceLabel = if (isPaid) "₹$dailyPriceStr/day" else "FREE"
        val depositStr = binding.etDepositPrice.text?.toString()?.trim().orEmpty().let {
            if (it.isNotEmpty()) "₹$it (Refundable)" else "None"
        }

        val category = when (binding.chipGroupCategory.checkedChipId) {
            R.id.chipCatBooks -> "BOOKS"
            R.id.chipCatLab -> "LAB"
            R.id.chipCatEvent -> "EVENT"
            R.id.chipCatStationery -> "STATIONERY"
            R.id.chipCatSports -> "SPORTS"
            R.id.chipCatBusiness -> "BUSINESS"
            R.id.chipCatCampus -> "CAMPUS"
            else -> "TECH"
        }

        val condition = when (binding.chipGroupCondition.checkedChipId) {
            R.id.chipCondBrandNew -> "Brand New"
            R.id.chipCondGood -> "Good"
            R.id.chipCondFair -> "Fair"
            R.id.chipCondNeedsAttention -> "Needs Attention"
            else -> "Like New"
        }

        val maxDuration = when (binding.chipGroupDuration.checkedChipId) {
            R.id.chipDurHours -> "A Few Hours"
            R.id.chipDur1Day -> "1 Day"
            R.id.chipDur3Days -> "Up to 3 days"
            R.id.chipDur1Week -> "1 Week"
            R.id.chipDurFlexible -> "Flexible"
            else -> "Up to 2 days"
        }

        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "You must be signed in to list an item", Toast.LENGTH_SHORT).show()
            return
        }

        val currentProfile = StudentProfileRepository.getCurrentUserProfile()
        val ownerUid = user.uid
        val ownerName = user.displayName?.ifBlank { currentProfile.fullName } ?: currentProfile.fullName
        val ownerCourse = if (currentProfile.department.isNotBlank() && currentProfile.yearSemester.isNotBlank()) {
            "${currentProfile.department} ${currentProfile.yearSemester}"
        } else {
            "IMSc.IT Sem 4"
        }

        val fullDescription = buildString {
            if (description.isNotEmpty()) append(description)
            if (brandModel.isNotEmpty()) {
                if (isNotEmpty()) append(" • ")
                append("Brand/Model: $brandModel")
            }
        }

        // Generate canonical Firestore document ID upfront
        val docRef = firestore.collection(ItemRepository.COLLECTION_ITEMS).document()
        val itemId = docRef.id

        // Lock publish button to prevent duplicate submissions
        binding.btnPublishItem.isEnabled = false
        binding.btnPublishItem.text = "Preparing photos..."

        // Launch lifecycle-scoped coroutine for background compression and sequential uploads
        lifecycleScope.launch {
            val uploadedUrls = mutableListOf<String>()
            val uploadedPublicIds = mutableListOf<String>()
            val tempFiles = mutableListOf<File>()

            try {
                val photosToUpload = selectedPhotos.take(3)
                for ((index, photoUri) in photosToUpload.withIndex()) {
                    binding.btnPublishItem.text = "Uploading photo ${index + 1} of ${photosToUpload.size}..."

                    val tempFile = withContext(Dispatchers.IO) {
                        ItemPhotoHelper.prepareUploadFileFromUri(this@ListItemActivity, photoUri, index)
                    }

                    if (tempFile == null) {
                        binding.btnPublishItem.isEnabled = true
                        binding.btnPublishItem.text = "Publish Listing"
                        Toast.makeText(
                            this@ListItemActivity,
                            "Unable to process photo ${index + 1} for upload.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }
                    tempFiles.add(tempFile)

                    val uploadResult = withContext(Dispatchers.IO) {
                        CloudinaryUploader.uploadImage(
                            file = tempFile,
                            preset = CloudinaryConfig.ITEM_UPLOAD_PRESET
                        )
                    }

                    if (uploadResult.isFailure) {
                        binding.btnPublishItem.isEnabled = true
                        binding.btnPublishItem.text = "Publish Listing"
                        val errorMsg = uploadResult.exceptionOrNull()?.message ?: "Upload failed"
                        Toast.makeText(
                            this@ListItemActivity,
                            "Failed to upload photo ${index + 1}: $errorMsg. Please retry.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }

                    val payload = uploadResult.getOrThrow()
                    uploadedUrls.add(payload.secureUrl)
                    uploadedPublicIds.add(payload.publicId)
                }

                // Verify authenticated account has not switched during network operations
                val currentUidNow = auth.currentUser?.uid
                if (currentUidNow != ownerUid) {
                    binding.btnPublishItem.isEnabled = true
                    binding.btnPublishItem.text = "Publish Listing"
                    Toast.makeText(
                        this@ListItemActivity,
                        "Account switched during upload. Publication aborted.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Verify that all uploaded URLs are valid HTTPS URLs
                if (uploadedUrls.isEmpty() || uploadedUrls.any { !it.startsWith("https://", ignoreCase = true) }) {
                    binding.btnPublishItem.isEnabled = true
                    binding.btnPublishItem.text = "Publish Listing"
                    Toast.makeText(
                        this@ListItemActivity,
                        "Uploaded photo URL validation failed. Please retry.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Save primary photo to local device storage for instantaneous local access
                withContext(Dispatchers.IO) {
                    ItemPhotoHelper.saveItemPhotoFromUri(this@ListItemActivity, selectedPhotos.first(), itemId)
                }

                binding.btnPublishItem.text = "Publishing Listing..."

                val now = System.currentTimeMillis()
                val newItem = CampusItem(
                    id = itemId,
                    title = itemName,
                    description = if (fullDescription.isNotEmpty()) fullDescription else "Campus peer lending item.",
                    category = category,
                    priceLabel = priceLabel,
                    isFree = !isPaid,
                    pickupSpot = "$location • 2m",
                    ownerName = ownerName,
                    ownerUid = ownerUid,
                    rating = "★ 5.0",
                    imageUri = uploadedUrls.first(),
                    imageUris = uploadedUrls,
                    imagePublicIds = uploadedPublicIds,
                    drawableRes = 0,
                    department = currentProfile.department.ifBlank { "IMSc.IT" },
                    condition = condition,
                    maxDuration = maxDuration,
                    securityDeposit = depositStr,
                    completedLends = 0,
                    ownerCourse = ownerCourse,
                    createdAt = now,
                    isAvailable = true,
                    updatedAt = now
                )

                // Write complete item data to Firestore via ItemRepository
                ItemRepository.createItem(newItem) { success, errorMsg ->
                    if (isFinishing || isDestroyed) return@createItem
                    if (success) {
                        Toast.makeText(this@ListItemActivity, "Item \"$itemName\" listed successfully!", Toast.LENGTH_SHORT).show()

                        val resultIntent = Intent().apply {
                            putExtra("EXTRA_NEW_ITEM", newItem)
                            putExtra("EXTRA_NEW_ITEM_ID", newItem.id)
                            putExtra("EXTRA_NEW_ITEM_TITLE", newItem.title)
                            putExtra("EXTRA_NEW_ITEM_CATEGORY", newItem.category)
                            putExtra("EXTRA_NEW_ITEM_PRICE", newItem.priceLabel)
                            putExtra("EXTRA_NEW_ITEM_SPOT", newItem.pickupSpot)
                            putExtra("EXTRA_NEW_ITEM_FREE", newItem.isFree)
                            putExtra("EXTRA_NEW_ITEM_IMAGE", newItem.imageUri)
                            putExtra("EXTRA_NEW_ITEM_DESC", newItem.description)
                            putExtra("EXTRA_NEW_ITEM_COND", newItem.condition)
                            putExtra("EXTRA_NEW_ITEM_DUR", newItem.maxDuration)
                            putExtra("EXTRA_NEW_ITEM_DEP", newItem.securityDeposit)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    } else {
                        // Rollback local photo if write failed
                        ItemPhotoHelper.deleteItemPhoto(this@ListItemActivity, itemId)
                        binding.btnPublishItem.isEnabled = true
                        binding.btnPublishItem.text = "Publish Listing"
                        Toast.makeText(this@ListItemActivity, errorMsg ?: "Failed to publish listing. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            } finally {
                // Clean up all temporary compressed cache files
                withContext(Dispatchers.IO) {
                    for (tempFile in tempFiles) {
                        try {
                            if (tempFile.exists()) tempFile.delete()
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }
}
