package com.borrowbuddy.app

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.borrowbuddy.app.databinding.ActivityRequestItemBinding
import com.borrowbuddy.app.model.GlanceRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class RequestItemActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRequestItemBinding
    private var quantity = 1
    private var customNeededByStr = "Today, 5:00 PM"

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRequestItemBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupHeader()
        setupQuantityControls()
        setupNeededByCustomPicker()
        setupPublishAction()
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupQuantityControls() {
        binding.tvQuantity.text = quantity.toString()

        binding.btnQtyMinus.setOnClickListener {
            if (quantity > 1) {
                quantity--
                binding.tvQuantity.text = quantity.toString()
            }
        }

        binding.btnQtyPlus.setOnClickListener {
            if (quantity < 10) {
                quantity++
                binding.tvQuantity.text = quantity.toString()
            }
        }
    }

    private fun setupNeededByCustomPicker() {
        binding.chipNeededCustom.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    val amPm = if (hourOfDay < 12) "AM" else "PM"
                    val hour12 = if (hourOfDay % 12 == 0) 12 else hourOfDay % 12
                    val minStr = if (minute < 10) "0$minute" else "$minute"
                    customNeededByStr = "Today, $hour12:$minStr $amPm"
                    binding.chipNeededCustom.text = customNeededByStr
                },
                cal.get(Calendar.HOUR_OF_DAY) + 1, 0, false
            ).show()
        }
    }

    private fun setupPublishAction() {
        binding.btnPublishRequest.setOnClickListener {
            handlePublish()
        }
    }

    private fun handlePublish() {
        val itemName = binding.etRequestItemName.text?.toString()?.trim().orEmpty()
        val neededAt = binding.etRequestNeededAt.text?.toString()?.trim().orEmpty()
        val description = binding.etRequestDescription.text?.toString()?.trim().orEmpty()

        // SUBMISSION VALIDATION: Only TWO fields are mandatory: Item Name & Needed At
        if (itemName.isEmpty()) {
            binding.etRequestItemName.error = "Please specify what item you need"
            binding.etRequestItemName.requestFocus()
            return
        }

        if (neededAt.isEmpty()) {
            binding.etRequestNeededAt.error = "Please enter where you need the item"
            binding.etRequestNeededAt.requestFocus()
            return
        }

        // Optional Category
        val category = when (binding.chipGroupRequestCategory.checkedChipId) {
            R.id.chipReqCatTech -> "TECH"
            R.id.chipReqCatBooks -> "BOOKS"
            R.id.chipReqCatLab -> "LAB"
            R.id.chipReqCatEvent -> "EVENT"
            R.id.chipReqCatStationery -> "STATIONERY"
            R.id.chipReqCatSports -> "SPORTS"
            else -> "" // Optional
        }

        // Optional Needed By
        val neededBy = when (binding.chipGroupNeededBy.checkedChipId) {
            R.id.chipNeededAsap -> "ASAP"
            R.id.chipNeeded2Hours -> "Within 2 Hours"
            R.id.chipNeededToday -> "Today"
            R.id.chipNeededTomorrow -> "Tomorrow"
            R.id.chipNeededCustom -> customNeededByStr
            else -> "ASAP"
        }

        // Expiration
        val expirationHours = when (binding.chipGroupExpiration.checkedChipId) {
            R.id.chipExp6h -> 6
            R.id.chipExp12h -> 12
            R.id.chipExp48h -> 48
            else -> 24 // 24 Hours default
        }

        val user = auth.currentUser
        val displayName = user?.displayName
        val userName = if (!displayName.isNullOrBlank()) {
            displayName.split(" ").firstOrNull() ?: displayName
        } else {
            "Harsh"
        }
        val avatarLetter = userName.firstOrNull()?.toString()?.uppercase() ?: "H"

        val glanceRequest = GlanceRequest(
            id = "req_" + System.currentTimeMillis(),
            name = userName,
            location = neededAt,
            item = itemName,
            avatarLetter = avatarLetter,
            description = description,
            category = category,
            quantity = quantity,
            neededBy = neededBy,
            expirationHours = expirationHours,
            state = "ACTIVE"
        )

        // Try writing to Firestore requests collection non-blocking
        try {
            val reqMap = hashMapOf(
                "name" to glanceRequest.name,
                "location" to glanceRequest.location,
                "item" to glanceRequest.item,
                "avatarLetter" to glanceRequest.avatarLetter,
                "description" to glanceRequest.description,
                "category" to glanceRequest.category,
                "quantity" to glanceRequest.quantity,
                "neededBy" to glanceRequest.neededBy,
                "expirationHours" to glanceRequest.expirationHours,
                "state" to glanceRequest.state,
                "uid" to (user?.uid ?: ""),
                "createdAt" to glanceRequest.createdAt
            )
            firestore.collection("campus_requests").add(reqMap)
        } catch (_: Exception) {}

        Toast.makeText(
            this,
            "Request for \"$itemName\" broadcasted to Glance feed!",
            Toast.LENGTH_SHORT
        ).show()

        val resultIntent = Intent().apply {
            putExtra("EXTRA_REQ_ID", glanceRequest.id)
            putExtra("EXTRA_REQ_NAME", glanceRequest.name)
            putExtra("EXTRA_REQ_ITEM", glanceRequest.item)
            putExtra("EXTRA_REQ_LOCATION", glanceRequest.location)
            putExtra("EXTRA_REQ_NEEDED_BY", glanceRequest.neededBy)
            putExtra("EXTRA_REQ_AVATAR", glanceRequest.avatarLetter)
            putExtra("EXTRA_REQ_DESC", glanceRequest.description)
            putExtra("EXTRA_REQ_QTY", glanceRequest.quantity)
            putExtra("EXTRA_REQ_EXP", glanceRequest.expirationHours)
            putExtra("EXTRA_REQ_STATE", glanceRequest.state)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
