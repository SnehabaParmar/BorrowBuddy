package com.borrowbuddy.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.borrowbuddy.app.databinding.FragmentBottomSheetEditProfileBinding
import com.borrowbuddy.app.model.StudentProfileRepository
import com.borrowbuddy.app.util.AvatarHelper
import com.borrowbuddy.app.util.CloudinaryConfig
import com.borrowbuddy.app.util.CloudinaryUploader
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class EditProfileBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentBottomSheetEditProfileBinding? = null
    private val binding get() = _binding!!

    // Local photo state
    private var currentPhotoUri: String = ""
    private var pendingSelectedUri: Uri? = null
    private var photoRemoved: Boolean = false

    // System Photo Picker (Modern Android, requires zero storage permissions)
    private val pickPhotoLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingSelectedUri = uri
            photoRemoved = false
            // Immediately render circular preview
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    binding.ivEditAvatarPhoto.setImageBitmap(bitmap)
                    binding.ivEditAvatarPhoto.visibility = View.VISIBLE
                    binding.tvEditAvatarInitials.visibility = View.GONE
                    binding.btnRemovePhoto.visibility = View.VISIBLE
                }
            } catch (_: Exception) {}
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBottomSheetEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val profile = StudentProfileRepository.getCurrentUserProfile()
        currentPhotoUri = profile.photoUri

        // 1. Prepopulate existing profile values
        binding.etFullName.setText(profile.fullName)
        binding.etCollege.setText(profile.college)
        binding.etDepartment.setText(profile.department)
        binding.etYearSemester.setText(profile.yearSemester)
        binding.etBio.setText(profile.bio)

        val activeUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            ?: profile.id.takeIf { it.isNotBlank() && it != "user_current" }

        // Initial avatar display (photo or initials)
        AvatarHelper.loadAvatar(
            requireContext(),
            currentPhotoUri,
            binding.ivEditAvatarPhoto,
            binding.tvEditAvatarInitials,
            profile.fullName,
            targetSizeDp = 64,
            uid = activeUid
        )
        binding.btnRemovePhoto.visibility = if (currentPhotoUri.isNotBlank()) View.VISIBLE else View.GONE

        // 2. Photo Actions (Change / Remove Photo)
        val openPicker = {
            pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnChangePhoto.setOnClickListener { openPicker() }
        binding.layoutAvatarContainer.setOnClickListener { openPicker() }

        binding.btnRemovePhoto.setOnClickListener {
            pendingSelectedUri = null
            photoRemoved = true
            currentPhotoUri = ""
            AvatarHelper.clearGlide(requireContext(), binding.ivEditAvatarPhoto)
            binding.ivEditAvatarPhoto.setImageDrawable(null)
            binding.ivEditAvatarPhoto.visibility = View.GONE
            binding.tvEditAvatarInitials.visibility = View.VISIBLE
            binding.btnRemovePhoto.visibility = View.GONE
            updateInitials(binding.etFullName.text?.toString().orEmpty())
        }

        // 3. Real-time avatar initial preview
        binding.etFullName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // If initials are currently showing, update them live
                if (binding.tvEditAvatarInitials.visibility == View.VISIBLE) {
                    updateInitials(s?.toString().orEmpty())
                }
                binding.tilFullName.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etCollege.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tilCollege.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etDepartment.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tilDepartment.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.etYearSemester.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.tilYearSemester.error = null
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 4. Save Changes Action with Validation
        binding.btnSaveProfile.setOnClickListener {
            val name = binding.etFullName.text?.toString()?.trim().orEmpty()
            val college = binding.etCollege.text?.toString()?.trim().orEmpty()
            val department = binding.etDepartment.text?.toString()?.trim().orEmpty()
            val yearSemester = binding.etYearSemester.text?.toString()?.trim().orEmpty()
            val bio = binding.etBio.text?.toString()?.trim().orEmpty()

            var isValid = true

            if (name.isBlank()) {
                binding.tilFullName.error = "Full name is required"
                isValid = false
            }
            if (college.isBlank()) {
                binding.tilCollege.error = "College / University is required"
                isValid = false
            }
            if (department.isBlank()) {
                binding.tilDepartment.error = "Department / Course is required"
                isValid = false
            }
            if (yearSemester.isBlank()) {
                binding.tilYearSemester.error = "Year / Semester is required"
                isValid = false
            }

            if (!isValid) return@setOnClickListener

            binding.btnSaveProfile.isEnabled = false
            binding.btnCancelEdit.isEnabled = false
            binding.btnSaveProfile.text = "Saving..."

            val activeUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                ?: StudentProfileRepository.getCurrentUserProfile().id.takeIf { it.isNotBlank() && it != "user_current" }

            if (photoRemoved) {
                // 1. Photo Removal: Keep local avatar file intact until Firestore confirms successful commit
                binding.btnSaveProfile.text = "Saving..."
                StudentProfileRepository.updateCurrentUserProfile(
                    name = name,
                    college = college,
                    department = department,
                    yearSemester = yearSemester,
                    bio = bio,
                    photoUri = ""
                ) { success ->
                    if (isAdded) {
                        // Validate active UID has not changed during network call
                        val currentUidNow = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                        if (currentUidNow != activeUid) {
                            binding.btnSaveProfile.isEnabled = true
                            binding.btnCancelEdit.isEnabled = true
                            binding.btnSaveProfile.text = "Save Changes"
                            return@updateCurrentUserProfile
                        }

                        if (success) {
                            // Only after successful Firestore commit, delete the local avatar file
                            if (activeUid != null) {
                                AvatarHelper.deleteProfilePhoto(requireContext(), activeUid)
                            }
                            AvatarHelper.clearGlide(requireContext(), binding.ivEditAvatarPhoto)
                            binding.ivEditAvatarPhoto.setImageDrawable(null)
                            binding.ivEditAvatarPhoto.visibility = View.GONE
                            binding.tvEditAvatarInitials.visibility = View.VISIBLE

                            binding.btnSaveProfile.isEnabled = true
                            binding.btnCancelEdit.isEnabled = true
                            binding.btnSaveProfile.text = "Save Changes"
                            Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                            dismiss()
                        } else {
                            // If Firestore fails: retain local file, preserve in-memory photo, keep editor open, allow retry
                            binding.btnSaveProfile.isEnabled = true
                            binding.btnCancelEdit.isEnabled = true
                            binding.btnSaveProfile.text = "Save Changes"
                            Toast.makeText(
                                requireContext(),
                                "Failed to save profile. Please check your network connection.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } else if (pendingSelectedUri != null && activeUid != null) {
                // 2. Photo Upload: Compress locally and upload to Cloudinary using unsigned preset
                viewLifecycleOwner.lifecycleScope.launch {
                    binding.btnSaveProfile.text = "Uploading photo..."
                    val tempUploadFile = AvatarHelper.prepareUploadFileFromUri(requireContext(), pendingSelectedUri!!)
                    if (tempUploadFile == null) {
                        binding.btnSaveProfile.isEnabled = true
                        binding.btnCancelEdit.isEnabled = true
                        binding.btnSaveProfile.text = "Save Changes"
                        Toast.makeText(requireContext(), "Unable to process selected image.", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val uploadResult = CloudinaryUploader.uploadImage(
                        file = tempUploadFile,
                        preset = CloudinaryConfig.PROFILE_UPLOAD_PRESET
                    )
                    tempUploadFile.delete() // Clean up temporary cache file

                    if (uploadResult.isFailure) {
                        binding.btnSaveProfile.isEnabled = true
                        binding.btnCancelEdit.isEnabled = true
                        binding.btnSaveProfile.text = "Save Changes"
                        val errorDetail = uploadResult.exceptionOrNull()?.message ?: "Upload failed"
                        Toast.makeText(requireContext(), "Photo upload failed: $errorDetail", Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    // Active account validation: ensure account hasn't switched during network call
                    val currentUidNow = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    if (currentUidNow != activeUid) {
                        binding.btnSaveProfile.isEnabled = true
                        binding.btnCancelEdit.isEnabled = true
                        binding.btnSaveProfile.text = "Save Changes"
                        return@launch
                    }

                    val uploadedPhotoUrl = uploadResult.getOrThrow().secureUrl
                    // Save local cached copy for fast local access
                    AvatarHelper.saveProfilePhotoFromUri(requireContext(), pendingSelectedUri!!, activeUid)

                    binding.btnSaveProfile.text = "Saving profile..."
                    StudentProfileRepository.updateCurrentUserProfile(
                        name = name,
                        college = college,
                        department = department,
                        yearSemester = yearSemester,
                        bio = bio,
                        photoUri = uploadedPhotoUrl
                    ) { success ->
                        if (isAdded) {
                            binding.btnSaveProfile.isEnabled = true
                            binding.btnCancelEdit.isEnabled = true
                            binding.btnSaveProfile.text = "Save Changes"
                            if (success) {
                                Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                                dismiss()
                            } else {
                                Toast.makeText(
                                    requireContext(),
                                    "Failed to save profile. Please check your network connection.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }
            } else {
                // 3. Text-only changes: Preserve existing photoUri (Cloudinary URL or empty)
                binding.btnSaveProfile.text = "Saving..."
                StudentProfileRepository.updateCurrentUserProfile(
                    name = name,
                    college = college,
                    department = department,
                    yearSemester = yearSemester,
                    bio = bio,
                    photoUri = currentPhotoUri
                ) { success ->
                    if (isAdded) {
                        binding.btnSaveProfile.isEnabled = true
                        binding.btnCancelEdit.isEnabled = true
                        binding.btnSaveProfile.text = "Save Changes"
                        if (success) {
                            Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                            dismiss()
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "Failed to save profile. Please check your network connection.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }

        binding.btnCancelEdit.setOnClickListener {
            dismiss()
        }

        binding.btnCloseEditProfile.setOnClickListener {
            dismiss()
        }
    }

    private fun updateInitials(name: String) {
        binding.tvEditAvatarInitials.text = AvatarHelper.getInitials(name)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
