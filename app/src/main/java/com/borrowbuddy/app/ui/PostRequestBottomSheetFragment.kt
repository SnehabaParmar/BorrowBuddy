package com.borrowbuddy.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.borrowbuddy.app.R
import com.borrowbuddy.app.databinding.FragmentBottomSheetPostRequestBinding
import com.borrowbuddy.app.model.GlanceRequest
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class PostRequestBottomSheetFragment(
    private val onRequestPosted: (GlanceRequest) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: FragmentBottomSheetPostRequestBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBottomSheetPostRequestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBroadcastRequest.setOnClickListener {
            handleBroadcast()
        }
    }

    private fun handleBroadcast() {
        val item = binding.etRequestItem.text?.toString()?.trim().orEmpty()
        val location = binding.etRequestLocation.text?.toString()?.trim().orEmpty()

        if (item.isEmpty()) {
            binding.etRequestItem.error = "Please specify what you need"
            binding.etRequestItem.requestFocus()
            return
        }

        if (location.isEmpty()) {
            binding.etRequestLocation.error = "Please specify where you are"
            binding.etRequestLocation.requestFocus()
            return
        }

        val user = auth.currentUser
        val displayName = user?.displayName
        val userName = if (!displayName.isNullOrBlank()) {
            displayName.split(" ").firstOrNull() ?: displayName
        } else {
            "Student"
        }
        val avatarLetter = userName.firstOrNull()?.toString()?.uppercase() ?: "S"

        val currentUid = user?.uid ?: "user_current"

        val glanceRequest = GlanceRequest(
            name = userName,
            location = location,
            item = item,
            avatarLetter = avatarLetter,
            requesterUid = currentUid
        )

        // Try writing to Firestore requests collection gracefully
        try {
            val reqMap = hashMapOf(
                "userName" to userName,
                "location" to location,
                "item" to item,
                "avatarLetter" to avatarLetter,
                "uid" to (user?.uid ?: ""),
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection("campus_requests").add(reqMap)
        } catch (_: Exception) {}

        Toast.makeText(
            requireContext(),
            "Request broadcasted! It now appears in the live Glance banner.",
            Toast.LENGTH_LONG
        ).show()

        onRequestPosted(glanceRequest)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
