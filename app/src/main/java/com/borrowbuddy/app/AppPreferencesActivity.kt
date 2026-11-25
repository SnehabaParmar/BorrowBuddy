package com.borrowbuddy.app

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.borrowbuddy.app.databinding.ActivityAppPreferencesBinding
import com.borrowbuddy.app.model.AppPreferencesManager

class AppPreferencesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppPreferencesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityAppPreferencesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootAppPreferences) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        setupHeader()
        setupDisplayPreferences()
        setupHapticFeedback()
        setupAppVersion()
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun setupDisplayPreferences() {
        binding.rowPrefTheme.setOnClickListener {
            Toast.makeText(this, "BorrowBuddy is optimized for the Campus Clean Light theme.", Toast.LENGTH_SHORT).show()
        }

        binding.rowPrefLanguage.setOnClickListener {
            Toast.makeText(this, "English is currently the standard campus language.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupHapticFeedback() {
        // Restore saved preference
        val isEnabled = AppPreferencesManager.isHapticFeedbackEnabled(this)
        binding.switchHapticFeedback.isChecked = isEnabled

        binding.switchHapticFeedback.setOnCheckedChangeListener { view, isChecked ->
            AppPreferencesManager.setHapticFeedbackEnabled(this, isChecked)
            if (isChecked) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
    }

    private fun setupAppVersion() {
        val versionName = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                ).versionName ?: "1.0"
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
            }
        } catch (_: Exception) {
            "1.0"
        }

        binding.tvAppVersion.text = "v$versionName (Build 1)"
    }
}
