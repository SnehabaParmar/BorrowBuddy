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
import com.borrowbuddy.app.databinding.ActivityRegisterBinding
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private var isPasswordVisible = false
    private var isConfirmPasswordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegisterBinding.inflate(layoutInflater)
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

        // Toggle Confirm Password Visibility
        binding.ivConfirmPasswordToggle.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            if (isConfirmPasswordVisible) {
                binding.etConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.ivConfirmPasswordToggle.setImageResource(R.drawable.ic_eye)
            } else {
                binding.etConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.ivConfirmPasswordToggle.setImageResource(R.drawable.ic_eye_off)
            }
            binding.etConfirmPassword.setSelection(binding.etConfirmPassword.text?.length ?: 0)
        }

        // Login Link
        binding.tvLoginLink.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Create Account Submit Button
        binding.btnCreateAccountSubmit.setOnClickListener {
            handleRegistration()
        }
    }

    private fun handleRegistration() {
        val fullName = binding.etFullName.text?.toString()?.trim().orEmpty()
        val email = binding.etEmail.text?.toString()?.trim().orEmpty()
        val password = binding.etPassword.text?.toString()?.trim().orEmpty()
        val confirmPassword = binding.etConfirmPassword.text?.toString()?.trim().orEmpty()

        if (fullName.isEmpty()) {
            binding.etFullName.error = "Please enter your full name"
            binding.etFullName.requestFocus()
            return
        }

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
            binding.etPassword.error = "Please create a password"
            binding.etPassword.requestFocus()
            return
        }

        if (password.length < 6) {
            binding.etPassword.error = "Password must be at least 6 characters"
            binding.etPassword.requestFocus()
            return
        }

        if (password != confirmPassword) {
            binding.etConfirmPassword.error = "Passwords do not match"
            binding.etConfirmPassword.requestFocus()
            return
        }

        // Execute live Firebase Registration & Cloud Firestore profile creation
        performLiveFirebaseRegistration(fullName, email, password)
    }

    /**
     * Creates Firebase Auth account, updates user profile displayName,
     * stores user document inside users/{uid} in Cloud Firestore, and handles errors.
     */
    private fun performLiveFirebaseRegistration(fullName: String, email: String, password: String) {
        setLoadingState(true)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val uid = user?.uid.orEmpty()

                    // Update display name in Firebase Auth
                    val profileUpdates = userProfileChangeRequest {
                        displayName = fullName
                    }
                    user?.updateProfile(profileUpdates)

                    // Create user profile document in Firestore: users/{uid}
                    val userData = hashMapOf(
                        "uid" to uid,
                        "name" to fullName,
                        "email" to email,
                        "createdAt" to Timestamp.now(),
                        "profileImage" to "",
                        "accountStatus" to "active"
                    )

                    firestore.collection("users").document(uid).set(userData)
                        .addOnSuccessListener {
                            setLoadingState(false)
                            Toast.makeText(this, "Account created successfully! Welcome, $fullName", Toast.LENGTH_SHORT).show()

                            val intent = Intent(this, MainActivity::class.java).apply {
                                putExtra("USER_NAME", fullName)
                                putExtra("USER_EMAIL", email)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener { firestoreEx ->
                            // Roll back Firebase Auth account creation if Firestore profile write fails
                            // This prevents orphaned accounts and allows user to retry registration without "email already in use" collision
                            user?.delete()?.addOnCompleteListener {
                                setLoadingState(false)
                                val errorDetail = firestoreEx.localizedMessage ?: "Database write failed."
                                Toast.makeText(this@RegisterActivity, "Registration failed during profile setup: $errorDetail. Please try again.", Toast.LENGTH_LONG).show()
                            } ?: run {
                                setLoadingState(false)
                                Toast.makeText(this@RegisterActivity, "Registration failed: ${firestoreEx.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    setLoadingState(false)
                    val exception = task.exception
                    val errorMessage = when (exception) {
                        is FirebaseAuthUserCollisionException -> {
                            "An account with this email already exists. Please log in."
                        }
                        is FirebaseAuthWeakPasswordException -> {
                            "The password is too weak. Please use at least 6 characters with mixed letters/numbers."
                        }
                        is FirebaseAuthInvalidCredentialsException -> {
                            "The email address format is invalid. Please check your email."
                        }
                        is FirebaseNetworkException -> {
                            "Network connection failed. Please check your internet connection."
                        }
                        else -> {
                            exception?.localizedMessage ?: "Registration failed. Please try again."
                        }
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun setLoadingState(isLoading: Boolean) {
        binding.btnCreateAccountSubmit.isEnabled = !isLoading
        binding.btnCreateAccountSubmit.text = if (isLoading) "Creating Account..." else getString(R.string.btn_create_account_submit)
    }
}
