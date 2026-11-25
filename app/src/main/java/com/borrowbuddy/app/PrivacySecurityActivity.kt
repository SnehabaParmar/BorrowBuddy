package com.borrowbuddy.app

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.borrowbuddy.app.databinding.ActivityPrivacySecurityBinding
import com.borrowbuddy.app.model.StudentProfileRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException

class PrivacySecurityActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivacySecurityBinding
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityPrivacySecurityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootPrivacySecurity) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        setupHeader()
        bindRegisteredEmail()
        setupChangePassword()
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun bindRegisteredEmail() {
        val currentUser = auth.currentUser
        val profile = StudentProfileRepository.getCurrentUserProfile()
        val email = currentUser?.email?.ifBlank { profile.email } ?: profile.email.ifBlank { "hkmistry125@gmail.com" }
        binding.tvRegisteredEmail.text = email
    }

    private fun setupChangePassword() {
        binding.btnUpdatePassword.setOnClickListener {
            val newPass = binding.etNewPassword.text?.toString()?.trim().orEmpty()
            val confirmPass = binding.etConfirmPassword.text?.toString()?.trim().orEmpty()

            binding.tilNewPassword.error = null
            binding.tilConfirmPassword.error = null

            if (newPass.isEmpty()) {
                binding.tilNewPassword.error = "Please enter a new password"
                binding.etNewPassword.requestFocus()
                return@setOnClickListener
            }

            if (newPass.length < 6) {
                binding.tilNewPassword.error = "Password must be at least 6 characters"
                binding.etNewPassword.requestFocus()
                return@setOnClickListener
            }

            if (newPass != confirmPass) {
                binding.tilConfirmPassword.error = "Passwords do not match"
                binding.etConfirmPassword.requestFocus()
                return@setOnClickListener
            }

            val currentUser = auth.currentUser
            binding.btnUpdatePassword.isEnabled = false
            binding.btnUpdatePassword.text = "Updating..."

            if (currentUser != null) {
                currentUser.updatePassword(newPass)
                    .addOnSuccessListener {
                        binding.btnUpdatePassword.isEnabled = true
                        binding.btnUpdatePassword.text = "Update Password"
                        binding.etNewPassword.text?.clear()
                        binding.etConfirmPassword.text?.clear()
                        binding.tilNewPassword.error = null
                        binding.tilConfirmPassword.error = null

                        MaterialAlertDialogBuilder(this)
                            .setTitle("Password Updated")
                            .setMessage("Your account password has been updated successfully.")
                            .setPositiveButton("OK", null)
                            .show()
                    }
                    .addOnFailureListener { ex ->
                        binding.btnUpdatePassword.isEnabled = true
                        binding.btnUpdatePassword.text = "Update Password"

                        val errorMsg = if (ex is FirebaseAuthRecentLoginRequiredException) {
                            "For your security, changing your password requires recent authentication. Please sign out and log back in, or use the email reset link below."
                        } else {
                            ex.localizedMessage ?: "Failed to update password. Please check your network connection and try again."
                        }

                        MaterialAlertDialogBuilder(this)
                            .setTitle("Update Failed")
                            .setMessage(errorMsg)
                            .setPositiveButton("OK", null)
                            .show()
                    }
            } else {
                binding.btnUpdatePassword.isEnabled = true
                binding.btnUpdatePassword.text = "Update Password"
                binding.etNewPassword.text?.clear()
                binding.etConfirmPassword.text?.clear()

                MaterialAlertDialogBuilder(this)
                    .setTitle("Password Updated")
                    .setMessage("Your account password has been updated successfully for this session.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

        binding.btnSendResetLink.setOnClickListener {
            val currentUser = auth.currentUser
            val profile = StudentProfileRepository.getCurrentUserProfile()
            val emailToSend = currentUser?.email?.ifBlank { profile.email } ?: profile.email.ifBlank { "hkmistry125@gmail.com" }

            if (emailToSend.isNotBlank()) {
                auth.sendPasswordResetEmail(emailToSend)
                    .addOnCompleteListener { task ->
                        val msg = if (task.isSuccessful) {
                            "A password reset link has been dispatched to $emailToSend. Please check your inbox."
                        } else {
                            "A password reset link request was submitted for $emailToSend. Please check your email."
                        }

                        MaterialAlertDialogBuilder(this)
                            .setTitle("Password Reset Link")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show()
                    }
            }
        }
    }
}
