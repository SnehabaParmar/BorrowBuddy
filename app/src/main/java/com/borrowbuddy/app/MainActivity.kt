package com.borrowbuddy.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.borrowbuddy.app.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.root.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        loadUserProfile()
        setupListeners()
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser

        // 1. Initial display from Auth / Intent extras
        val passedName = intent.getStringExtra("USER_NAME")
        val passedEmail = intent.getStringExtra("USER_EMAIL")

        val initialName = currentUser?.displayName ?: passedName
        val initialEmail = currentUser?.email ?: passedEmail

        if (!initialName.isNullOrBlank()) {
            binding.tvWelcomeTitle.text = "Welcome, $initialName!"
        } else {
            binding.tvWelcomeTitle.text = "Welcome to BorrowBuddy!"
        }

        if (!initialEmail.isNullOrBlank()) {
            binding.tvUserEmail.text = "Logged in as: $initialEmail"
        }

        // 2. Fetch fresh user data from Cloud Firestore users/{uid}
        currentUser?.uid?.let { uid ->
            firestore.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val firestoreName = document.getString("name")
                        if (!firestoreName.isNullOrBlank()) {
                            binding.tvWelcomeTitle.text = "Welcome, $firestoreName!"
                        }
                    }
                }
                .addOnFailureListener {
                    // Silently preserve display name from Auth if Firestore offline
                }
        }
    }

    private fun setupListeners() {
        // Logout Button: Signs out of Firebase and returns to WelcomeActivity
        binding.btnLogout.setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "Signed out of BorrowBuddy", Toast.LENGTH_SHORT).show()

            val intent = Intent(this, WelcomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            finish()
        }
    }
}
