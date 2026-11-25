package com.borrowbuddy.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.borrowbuddy.app.ActivityHistoryActivity
import com.borrowbuddy.app.AppPreferencesActivity
import com.borrowbuddy.app.ListItemActivity
import com.borrowbuddy.app.MainActivity
import com.borrowbuddy.app.MyListingsActivity
import com.borrowbuddy.app.NotificationsActivity
import com.borrowbuddy.app.PrivacySecurityActivity
import com.borrowbuddy.app.R
import com.borrowbuddy.app.StudentProfileActivity
import com.borrowbuddy.app.WelcomeActivity
import com.borrowbuddy.app.databinding.FragmentProfileBinding
import com.borrowbuddy.app.databinding.ItemProfileActivityBinding
import com.borrowbuddy.app.databinding.ItemProfileListingBinding
import com.borrowbuddy.app.model.ExchangeItem
import com.borrowbuddy.app.model.ExchangeRepository
import com.borrowbuddy.app.model.ExchangeStatus
import com.borrowbuddy.app.model.ExchangeType
import com.borrowbuddy.app.model.NotificationRepository
import com.borrowbuddy.app.model.StudentProfile
import com.borrowbuddy.app.model.StudentProfileRepository
import com.borrowbuddy.app.util.ActivityHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val profileChangeListener: (StudentProfile) -> Unit = {
        if (_binding != null) {
            loadProfileData()
            populateMyListings()
        }
    }

    private val exchangeChangeListener: () -> Unit = {
        activity?.runOnUiThread {
            if (_binding != null) {
                populateMyActivity()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        StudentProfileRepository.addProfileChangeListener(profileChangeListener)
        ExchangeRepository.registerListener(exchangeChangeListener)
        loadProfileData()
        populateMyListings()
        populateMyActivity()
        setupPreferencesAndHelp()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        populateMyActivity()
    }

    // =========================================================================
    // SECTION A: Profile Identity Header
    // =========================================================================
    private fun loadProfileData() {
        val profile = StudentProfileRepository.getCurrentUserProfile()
        val user = auth.currentUser
        val email = user?.email?.ifBlank { profile.email } ?: profile.email

        binding.tvProfileName.text = profile.fullName
        binding.tvProfileEmail.text = email
        binding.tvProfileDept.text = "${profile.department} • ${profile.yearSemester}"
        binding.tvProfileUniversity.text = profile.college

        if (profile.bio.isNotBlank()) {
            binding.tvProfileBio.visibility = View.VISIBLE
            binding.tvProfileBio.text = "\"${profile.bio}\""
        } else {
            binding.tvProfileBio.visibility = View.GONE
        }

        // Render profile photo or initials avatar
        com.borrowbuddy.app.util.AvatarHelper.loadAvatar(
            requireContext(),
            profile.photoUri,
            binding.ivProfileAvatar,
            binding.tvProfileInitials,
            profile.fullName,
            targetSizeDp = 54,
            uid = user?.uid ?: profile.id
        )

        // Sync with Firebase if present
        StudentProfileRepository.syncWithFirebase(user)
    }

    private fun updateInitials(name: String) {
        binding.tvProfileInitials.text = com.borrowbuddy.app.util.AvatarHelper.getInitials(name)
    }

    private fun showEditProfileDialog() {
        val editSheet = EditProfileBottomSheetFragment()
        editSheet.show(childFragmentManager, "EditProfileBottomSheet")
    }

    // =========================================================================
    // SECTION C: My Listings
    // =========================================================================
    private fun populateMyListings() {
        val container = binding.layoutMyListingsContainer
        container.removeAllViews()

        val items = StudentProfileRepository.getCurrentUserItems()

        if (items.isEmpty()) {
            binding.layoutListingsEmptyState.visibility = View.VISIBLE
            binding.layoutAddListingRow.visibility = View.GONE
            return
        }

        binding.layoutListingsEmptyState.visibility = View.GONE
        binding.layoutAddListingRow.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(requireContext())
        items.forEachIndexed { index, item ->
            val rowBinding = ItemProfileListingBinding.inflate(inflater, container, false)
            rowBinding.tvItemTitle.text = item.title
            rowBinding.tvItemCategoryAndPrice.text = "${item.category} • ${item.priceLabel}"
            val isAvail = com.borrowbuddy.app.model.ExchangeRepository.isItemAvailable(item.id)
            if (isAvail) {
                rowBinding.tvItemStatus.text = "Available"
                rowBinding.tvItemStatus.setBackgroundResource(R.drawable.bg_badge_free)
                rowBinding.tvItemStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_navy))
            } else {
                rowBinding.tvItemStatus.text = "Lent Out"
                rowBinding.tvItemStatus.setBackgroundResource(R.drawable.bg_badge_active)
                rowBinding.tvItemStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.brand_blue))
            }

            if (item.drawableRes != 0) {
                rowBinding.ivItemThumb.setImageResource(item.drawableRes)
            } else {
                rowBinding.ivItemThumb.setImageResource(R.drawable.bg_image_fallback)
            }

            rowBinding.root.setOnClickListener {
                Toast.makeText(
                    requireContext(),
                    "${item.title} (${if (isAvail) "Available" else "Lent Out"})",
                    Toast.LENGTH_SHORT
                ).show()
            }

            container.addView(rowBinding.root)

            // Add divider between items
            if (index < items.size - 1) {
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    ).apply {
                        setMargins(0, 4, 0, 4)
                    }
                    setBackgroundColor(ContextCompat.getColor(context, R.color.border_light))
                }
                container.addView(divider)
            }
        }
    }

    // =========================================================================
    // SECTION D: My Activity (Recent Activity - Latest 4 Items)
    // =========================================================================
    private fun populateMyActivity() {
        val container = binding.layoutMyActivityContainer
        container.removeAllViews()

        val allExchanges = ExchangeRepository.getAll()

        if (allExchanges.isEmpty()) {
            binding.btnViewAllActivity.visibility = View.GONE
            binding.layoutActivityEmptyState.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }

        binding.btnViewAllActivity.visibility = View.VISIBLE
        binding.layoutActivityEmptyState.visibility = View.GONE
        container.visibility = View.VISIBLE

        // Sort newest activities first and display a maximum of 4 latest activities
        val sortedActivities = allExchanges.sortedByDescending {
            ActivityHelper.parseActivityTimestamp(ActivityHelper.getActivityDate(it))
        }
        val displayActivities = sortedActivities.take(4)

        val inflater = LayoutInflater.from(requireContext())
        displayActivities.forEachIndexed { index, exchange ->
            val rowBinding = ItemProfileActivityBinding.inflate(inflater, container, false)

            rowBinding.tvActivityItemTitle.text = exchange.title
            rowBinding.tvActivityDescription.text = ActivityHelper.getActivityDescription(exchange)
            rowBinding.tvActivityDate.text = ActivityHelper.getActivityDate(exchange)

            // Thumbnail / Category Icon
            var imageLoaded = false
            if (exchange.imageUri.isNotBlank()) {
                try {
                    rowBinding.ivActivityThumb.setImageURI(Uri.parse(exchange.imageUri))
                    rowBinding.ivActivityThumb.visibility = View.VISIBLE
                    rowBinding.layoutFallback.visibility = View.GONE
                    imageLoaded = true
                } catch (_: Exception) {
                    imageLoaded = false
                }
            }

            if (!imageLoaded) {
                if (exchange.drawableRes != 0) {
                    rowBinding.ivActivityThumb.setImageResource(exchange.drawableRes)
                    rowBinding.ivActivityThumb.visibility = View.VISIBLE
                    rowBinding.layoutFallback.visibility = View.GONE
                } else {
                    rowBinding.ivActivityThumb.visibility = View.GONE
                    rowBinding.layoutFallback.visibility = View.VISIBLE
                    rowBinding.ivFallbackIcon.setImageResource(ActivityHelper.getCategoryIcon(exchange.category))
                }
            }

            // Divider: hide for last displayed activity
            rowBinding.dividerActivity.visibility = if (index == displayActivities.size - 1) View.GONE else View.VISIBLE

            // Click interaction: navigate to Exchanges tab & relevant category
            rowBinding.root.setOnClickListener {
                val isHistory = exchange.status in listOf(
                    ExchangeStatus.COMPLETED,
                    ExchangeStatus.DECLINED,
                    ExchangeStatus.CANCELLED
                )
                val category = if (isHistory) "HISTORY" else "ACTIVE"
                ExchangesFragment.navigateTo(exchange.type, category)
                (activity as? MainActivity)?.navigateToTab(3)
            }

            container.addView(rowBinding.root)
        }
    }

    // =========================================================================
    // SECTION E & F: Preferences, Support & Help
    // =========================================================================
    private fun setupPreferencesAndHelp() {
        binding.rowPrefNotifications.setOnClickListener {
            val intent = Intent(requireContext(), NotificationsActivity::class.java)
            startActivity(intent)
        }

        binding.rowPrefPrivacy.setOnClickListener {
            val intent = Intent(requireContext(), PrivacySecurityActivity::class.java)
            startActivity(intent)
        }

        binding.rowPrefAppSettings.setOnClickListener {
            val intent = Intent(requireContext(), AppPreferencesActivity::class.java)
            startActivity(intent)
        }

        binding.rowHelpSupport.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Help & Campus Guidelines")
                .setMessage("BorrowBuddy Campus Rules:\n\n1. Treat peer items with care.\n2. Return items by the agreed return time.\n3. Verify items in person at designated safe campus pickup zones.\n\nNeed support? Contact campus-support@borrowbuddy.app")
                .setPositiveButton("Close", null)
                .show()
        }

        binding.rowAbout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("About BorrowBuddy")
                .setMessage("BorrowBuddy v1.2.0 (Build 42)\n\nA student-first peer borrowing and lending utility for campus communities.\n\nBuilt with Kotlin, Material 3, and Firebase.")
                .setPositiveButton("OK", null)
                .show()
        }

        binding.rowTerms.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Terms & Privacy Policy")
                .setMessage("By using BorrowBuddy on campus, you agree to student honor guidelines and peer responsibility for borrowed property.")
                .setPositiveButton("Agree", null)
                .show()
        }
    }

    // =========================================================================
    // User Action Listeners
    // =========================================================================
    private fun setupListeners() {
        // Edit Profile
        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        // View Public Profile preview
        binding.btnViewPublicProfile.setOnClickListener {
            val profile = StudentProfileRepository.getCurrentUserProfile()
            val targetUid = profile.id.ifBlank { "user_current" }
            StudentProfileActivity.start(requireContext(), targetUid, profile.fullName)
        }

        // View All Listings
        binding.btnViewAllListings.setOnClickListener {
            val intent = Intent(requireContext(), MyListingsActivity::class.java)
            startActivity(intent)
        }

        // View All Activity History
        binding.btnViewAllActivity.setOnClickListener {
            val intent = Intent(requireContext(), ActivityHistoryActivity::class.java)
            startActivity(intent)
        }

        // Quick add new item listing
        binding.layoutAddListingRow.setOnClickListener {
            val intent = Intent(requireContext(), ListItemActivity::class.java)
            startActivity(intent)
        }

        binding.btnEmptyListFirstItem.setOnClickListener {
            val intent = Intent(requireContext(), ListItemActivity::class.java)
            startActivity(intent)
        }

        // Log Out
        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out of BorrowBuddy?")
                .setPositiveButton("Sign Out") { _, _ ->
                    // 1. Clear user-specific in-memory session caches and state
                    val appContext = requireContext().applicationContext
                    StudentProfileRepository.clearSession(appContext)
                    ExchangeRepository.clearSession()
                    NotificationRepository.clearSession(appContext)

                    // 2. Sign out of Firebase Authentication
                    auth.signOut()
                    Toast.makeText(requireContext(), "Signed out of BorrowBuddy", Toast.LENGTH_SHORT).show()

                    val intent = Intent(requireContext(), WelcomeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    requireActivity().finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onStart() {
        super.onStart()
        StudentProfileRepository.syncWithFirebase(auth.currentUser)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        StudentProfileRepository.removeProfileChangeListener(profileChangeListener)
        ExchangeRepository.unregisterListener(exchangeChangeListener)
        _binding = null
    }
}
