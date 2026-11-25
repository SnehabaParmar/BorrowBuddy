package com.borrowbuddy.app.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.borrowbuddy.app.R
import com.borrowbuddy.app.databinding.FragmentBottomSheetAddItemBinding
import com.borrowbuddy.app.model.CampusItem
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AddItemBottomSheetFragment(
    private val onItemAdded: (CampusItem) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: FragmentBottomSheetAddItemBinding? = null
    private val binding get() = _binding!!
    private var selectedImageUri: Uri? = null

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    // Image Picker Launcher
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                selectedImageUri = uri
                binding.ivSelectedImage.setImageURI(uri)
                binding.ivSelectedImage.visibility = View.VISIBLE
                binding.layoutUploadPrompt.visibility = View.GONE
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBottomSheetAddItemBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Image picker click
        binding.cardImagePicker.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        // Pricing toggle
        binding.rgPricing.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbPaid) {
                binding.etDailyPrice.visibility = View.VISIBLE
            } else {
                binding.etDailyPrice.visibility = View.GONE
            }
        }

        // Submit button
        binding.btnSubmitItem.setOnClickListener {
            handleSubmit()
        }
    }

    private fun handleSubmit() {
        val title = binding.etTitle.text?.toString()?.trim().orEmpty()
        val description = binding.etDescription.text?.toString()?.trim().orEmpty()
        val pickupSpot = binding.etPickupSpot.text?.toString()?.trim().orEmpty()

        if (title.isEmpty()) {
            binding.etTitle.error = "Please enter item title"
            binding.etTitle.requestFocus()
            return
        }

        if (description.isEmpty()) {
            binding.etDescription.error = "Please describe the item"
            binding.etDescription.requestFocus()
            return
        }

        if (pickupSpot.isEmpty()) {
            binding.etPickupSpot.error = "Please enter pickup spot"
            binding.etPickupSpot.requestFocus()
            return
        }

        val isFree = binding.rbFree.isChecked
        val priceLabel = if (isFree) {
            "FREE"
        } else {
            val amount = binding.etDailyPrice.text?.toString()?.trim().orEmpty()
            if (amount.isNotEmpty()) "₹$amount/day" else "₹20/day"
        }

        val category = when (binding.chipGroupCategory.checkedChipId) {
            R.id.chipBooks -> "BOOKS"
            R.id.chipLab -> "LAB"
            R.id.chipEvent -> "EVENT"
            else -> "TECH"
        }

        val currentUser = auth.currentUser
        if (currentUser == null || currentUser.uid.isBlank()) {
            Toast.makeText(requireContext(), "Please sign in with your campus account to list an item", Toast.LENGTH_LONG).show()
            return
        }
        val ownerName = currentUser.displayName ?: "Student"
        val ownerUid = currentUser.uid

        val newItem = CampusItem(
            id = System.currentTimeMillis().toString(),
            title = title,
            description = description,
            category = category,
            priceLabel = priceLabel,
            isFree = isFree,
            pickupSpot = pickupSpot,
            ownerName = ownerName,
            ownerUid = ownerUid,
            rating = "5.0",
            imageUri = selectedImageUri?.toString().orEmpty(),
            department = "IMSc.IT"
        )

        // Save to Firestore (async, non-blocking)
        val itemMap = hashMapOf(
            "title" to newItem.title,
            "description" to newItem.description,
            "category" to newItem.category,
            "priceLabel" to newItem.priceLabel,
            "isFree" to newItem.isFree,
            "pickupSpot" to newItem.pickupSpot,
            "ownerName" to newItem.ownerName,
            "ownerUid" to newItem.ownerUid,
            "rating" to newItem.rating,
            "imageUri" to newItem.imageUri,
            "department" to newItem.department,
            "createdAt" to newItem.createdAt
        )
        firestore.collection("items").add(itemMap)

        Toast.makeText(requireContext(), "Item listed successfully for lending!", Toast.LENGTH_SHORT).show()
        onItemAdded(newItem)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

