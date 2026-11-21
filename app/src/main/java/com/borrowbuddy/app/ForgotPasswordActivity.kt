package com.borrowbuddy.app

import android.os.Bundle
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.borrowbuddy.app.databinding.ActivityForgotPasswordBinding
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        setupListeners()
    }

    private fun setupListeners() {
        // Back Button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Back to Login Button
        binding.btnBackToLogin.setOnClickListener {
            finish()
        }

        // Send Reset Link Submit Button
        binding.btnSendReset.setOnClickListener {
            handleResetPassword()
        }
    }

    private fun handleResetPassword() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()

        if (email.isEmpty()) {
            binding.etEmail.error = "Please enter your registered email"
            binding.etEmail.requestFocus()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Please enter a valid email address"
            binding.etEmail.requestFocus()
            return
        }

        // Execute live Firebase password reset
        performLiveFirebaseReset(email)
    }

    /**
     * Sends password reset email through Firebase Authentication with robust error feedback.
     */
    private fun performLiveFirebaseReset(email: String) {
        setLoadingState(true)

        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener(this) { task ->
                setLoadingState(false)
                if (task.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Password reset link sent to $email. Please check your inbox!",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    val exception = task.exception
                    val errorMessage = when (exception) {
                        is FirebaseAuthInvalidUserException -> {
                            "No account found with this email. Please verify your address."
                        }
                        is FirebaseAuthInvalidCredentialsException -> {
                            "The email address format is invalid."
                        }
                        is FirebaseNetworkException -> {
                            "Network connection failed. Please check your internet connection."
                        }
                        else -> {
                            exception?.localizedMessage ?: "Failed to send reset link. Please try again."
                        }
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnSendReset.isEnabled = !isLoading
        binding.btnSendReset.text = if (isLoading) "Sending link..." else getString(R.string.btn_send_reset_link)
    }
}
