package com.borrowbuddy.app

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.util.Patterns
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.borrowbuddy.app.databinding.ActivityLoginBinding
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private var isPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLoginBinding.inflate(layoutInflater)
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

        // Toggle Password Visibility
        binding.ivPasswordToggle.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                binding.etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.ivPasswordToggle.setImageResource(R.drawable.ic_eye)
            } else {
                binding.etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.ivPasswordToggle.setImageResource(R.drawable.ic_eye_off)
            }
            binding.etPassword.setSelection(binding.etPassword.text?.length ?: 0)
        }

        // Forgot Password?
        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        // Create Account Link
        binding.tvCreateAccountLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        // Login Submit Button
        binding.btnLoginSubmit.setOnClickListener {
            handleLogin()
        }
    }

    private fun handleLogin() {
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString()?.trim().orEmpty()

        if (email.isEmpty()) {
            binding.etEmail.error = "Please enter your email"
            binding.etEmail.requestFocus()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Please enter a valid email address"
            binding.etEmail.requestFocus()
            return
        }

        if (password.isEmpty()) {
            binding.etPassword.error = "Please enter your password"
            binding.etPassword.requestFocus()
            return
        }

        if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters"
            binding.etPassword.requestFocus()
            return
        }

        // Execute live Firebase Authentication
        performLiveFirebaseLogin(email, password)
    }

    /**
     * Authenticates user with Firebase Authentication and handles errors gracefully.
     */
    private fun performLiveFirebaseLogin(email: String, password: String) {
        setLoadingState(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                setLoadingState(false)
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val displayName = user?.displayName
                    val greeting = if (!displayName.isNullOrBlank()) "Welcome back, $displayName!" else "Welcome back to BorrowBuddy!"
                    Toast.makeText(this, greeting, Toast.LENGTH_SHORT).show()

                    // Reset previous in-memory caches to prepare clean state for newly logged-in account
                    com.borrowbuddy.app.model.StudentProfileRepository.clearSession(applicationContext)
                    com.borrowbuddy.app.model.ExchangeRepository.clearSession()
                    com.borrowbuddy.app.model.NotificationRepository.clearSession(applicationContext)

                    val intent = Intent(this, MainActivity::class.java).apply {
                        putExtra("USER_NAME", displayName)
                        putExtra("USER_EMAIL", user?.email ?: email)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                } else {
                    val exception = task.exception
                    val errorMessage = when (exception) {
                        is FirebaseAuthInvalidUserException -> {
                            "No account found with this email. Please create an account."
                        }
                        is FirebaseAuthInvalidCredentialsException -> {
                            "Incorrect password or email format. Please check your credentials."
                        }
                        is FirebaseNetworkException -> {
                            "Network error. Please check your internet connection and try again."
                        }
                        else -> {
                            exception?.localizedMessage ?: "Authentication failed. Please check your credentials."
                        }
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnLoginSubmit.isEnabled = !isLoading
        binding.btnLoginSubmit.text = if (isLoading) "Signing in..." else getString(R.string.btn_login)
    }
}
