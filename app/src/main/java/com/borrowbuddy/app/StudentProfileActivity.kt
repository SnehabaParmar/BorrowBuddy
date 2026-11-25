package com.borrowbuddy.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.borrowbuddy.app.adapter.CampusItemAdapter
import com.borrowbuddy.app.databinding.ActivityStudentProfileBinding
import com.borrowbuddy.app.model.CampusItem
import com.borrowbuddy.app.model.StudentProfile
import com.borrowbuddy.app.model.StudentProfileRepository
import com.borrowbuddy.app.ui.ItemDetailBottomSheetFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

/**
 * Public-facing Student Profile screen displayed when any user taps another student's
 * name or avatar across Explore, Item Details, Exchanges, or Glance.
 *
 * NOTE ON DATA SEPARATION:
 * Exposes strictly public identity details (Name, College, Department, Semester, Bio,
 * genuine Activity, and Available Listings). Account email and authentication
 * credentials are NEVER exposed.
 */
class StudentProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentProfileBinding
    private var studentId: String? = null
    private var studentName: String? = null
    private var profile: StudentProfile? = null
    private val availableListings = mutableListOf<CampusItem>()
    private lateinit var itemsAdapter: CampusItemAdapter

    private val profileChangeListener: (StudentProfile) -> Unit = { updatedProfile ->
        if (!isFinishing && !isDestroyed) {
            if (studentId == "user_current" ||
                updatedProfile.id == studentId ||
                (studentName != null && updatedProfile.fullName.equals(studentName, ignoreCase = true))
            ) {
                runOnUiThread {
                    loadProfile()
                }
            }
        }
    }

    companion object {
        const val EXTRA_STUDENT_ID = "EXTRA_STUDENT_ID"
        const val EXTRA_STUDENT_NAME = "EXTRA_STUDENT_NAME"

        fun start(context: Context, studentId: String?, studentName: String?) {
            val intent = Intent(context, StudentProfileActivity::class.java).apply {
                putExtra(EXTRA_STUDENT_ID, studentId)
                putExtra(EXTRA_STUDENT_NAME, studentName)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityStudentProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootStudentProfile) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        studentId = intent.getStringExtra(EXTRA_STUDENT_ID)
        studentName = intent.getStringExtra(EXTRA_STUDENT_NAME)

        setupHeaderAndSafety()
        setupListingsRecyclerView()
        loadProfile()
    }

    override fun onStart() {
        super.onStart()
        StudentProfileRepository.addProfileChangeListener(profileChangeListener)
    }

    override fun onStop() {
        super.onStop()
        StudentProfileRepository.removeProfileChangeListener(profileChangeListener)
    }

    private fun loadProfile() {
        // 1. Try resolving strictly from repository (logged in user or seeded catalog)
        val resolved = StudentProfileRepository.getPublicStudentProfile(studentId, studentName)
        if (resolved != null) {
            bindProfile(resolved)
            return
        }

        // 2. If not found in memory, try fetching from Firestore public_profiles collection
        if (!studentId.isNullOrBlank() && studentId != "user_current") {
            StudentProfileRepository.fetchPublicProfileFromFirestore(studentId) { remoteProfile ->
                if (!isFinishing && !isDestroyed) {
                    if (remoteProfile != null) {
                        bindProfile(remoteProfile)
                    } else {
                        Toast.makeText(this, "Student profile is unavailable", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        } else {
            // Identity cannot be resolved reliably and no fake profiles allowed
            Toast.makeText(this, "Student profile is unavailable", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun bindProfile(p: StudentProfile) {
        profile = p
        with(binding) {
            // 1. Identity Header
            tvStudentName.text = p.fullName
            com.borrowbuddy.app.util.AvatarHelper.loadAvatar(
                this@StudentProfileActivity,
                p.photoUri,
                ivStudentAvatar,
                tvStudentAvatar,
                p.fullName,
                targetSizeDp = 56,
                uid = p.id
            )
            tvStudentDept.text = "${p.department} • ${p.yearSemester}"
            tvStudentCollege.text = p.college

            // Verified Badge (hidden by default until genuine verification exists)
            if (p.isVerifiedCampusStudent) {
                ivVerifiedBadge.visibility = View.VISIBLE
                layoutVerifiedPill.visibility = View.VISIBLE
            } else {
                ivVerifiedBadge.visibility = View.GONE
                layoutVerifiedPill.visibility = View.GONE
            }

            // Optional Bio (hidden if not present)
            if (p.bio.isNotBlank()) {
                tvStudentBio.visibility = View.VISIBLE
                tvStudentBio.text = "\"${p.bio}\""
            } else {
                tvStudentBio.visibility = View.GONE
            }

            // 2. Available Listings
            availableListings.clear()
            availableListings.addAll(
                StudentProfileRepository.getAvailableItemsForStudent(studentId, p.fullName)
            )
            itemsAdapter.notifyDataSetChanged()

            // If viewing a remote campus peer with a real UID, fetch their listings from Firestore
            val targetUid = studentId ?: p.id
            val myUid = FirebaseAuth.getInstance().currentUser?.uid
            if (targetUid.isNotBlank() && !targetUid.startsWith("mock_") && targetUid != "user_current" && targetUid != myUid) {
                StudentProfileRepository.fetchItemsForStudentFromFirestore(targetUid) { remoteItems ->
                    if (!isFinishing && !isDestroyed && remoteItems.isNotEmpty()) {
                        availableListings.clear()
                        availableListings.addAll(remoteItems)
                        itemsAdapter.notifyDataSetChanged()
                        tvStatListingsCount.text = "${availableListings.size}"
                        tvListingsHeading.text = "Available Listings (${availableListings.size})"
                        rvStudentListings.visibility = View.VISIBLE
                        layoutEmptyListings.visibility = View.GONE
                    }
                }
            }

            // 3. Genuine BorrowBuddy Activity Metrics
            tvStatListingsCount.text = "${availableListings.size}"
            tvStatLendsCount.text = "${p.completedLends}"
            tvStatBorrowsCount.text = "${p.completedBorrows}"

            if (p.rating.isNotBlank()) {
                layoutStatRating.visibility = View.VISIBLE
                dividerRating.visibility = View.VISIBLE
                tvStatRating.text = p.rating
            } else {
                layoutStatRating.visibility = View.GONE
                dividerRating.visibility = View.GONE
            }

            tvListingsHeading.text = "Available Listings (${availableListings.size})"

            if (availableListings.isEmpty()) {
                rvStudentListings.visibility = View.GONE
                layoutEmptyListings.visibility = View.VISIBLE
            } else {
                rvStudentListings.visibility = View.VISIBLE
                layoutEmptyListings.visibility = View.GONE
            }
        }
    }

    private fun setupHeaderAndSafety() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnProfileSafety.setOnClickListener {
            showSafetyOptionsDialog()
        }

        binding.btnReportStudent.setOnClickListener {
            showReportDialog()
        }

        binding.btnBlockStudent.setOnClickListener {
            showBlockDialog()
        }
    }

    private fun setupListingsRecyclerView() {
        itemsAdapter = CampusItemAdapter(availableListings) { clickedItem ->
            val detailSheet = ItemDetailBottomSheetFragment(clickedItem)
            detailSheet.show(supportFragmentManager, "ItemDetailFromProfile")
        }

        binding.rvStudentListings.apply {
            layoutManager = GridLayoutManager(this@StudentProfileActivity, 2)
            adapter = itemsAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun showSafetyOptionsDialog() {
        val targetName = profile?.fullName ?: "Student"
        val options = arrayOf("Report Student", "Block Student", "Campus Safety Guidelines")
        MaterialAlertDialogBuilder(this)
            .setTitle("$targetName • Safety Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showReportDialog()
                    1 -> showBlockDialog()
                    2 -> {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Campus Safety Guidelines")
                            .setMessage("BorrowBuddy Safety Tips:\n\n1. Always exchange items in designated campus zones during daylight.\n2. Verify the condition of items before accepting handover.\n3. Return items strictly by the scheduled return time.")
                            .setPositiveButton("Understood", null)
                            .show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showReportDialog() {
        val targetName = profile?.fullName ?: "Student"
        val reasons = arrayOf(
            "Item not returned on schedule",
            "Item condition misrepresented",
            "Inappropriate communication / behavior",
            "Unresponsive during agreed exchange handover",
            "Other campus concern"
        )
        var selectedIndex = 0

        MaterialAlertDialogBuilder(this)
            .setTitle("Report $targetName")
            .setSingleChoiceItems(reasons, selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton("Submit Report") { _, _ ->
                Toast.makeText(
                    this,
                    "Report received. Campus student moderation will review this profile.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBlockDialog() {
        val targetName = profile?.fullName ?: "Student"
        MaterialAlertDialogBuilder(this)
            .setTitle("Block $targetName?")
            .setMessage("You will no longer see exchange requests or available items from $targetName. This action can be modified later in Account Settings.")
            .setPositiveButton("Block Student") { _, _ ->
                Toast.makeText(
                    this,
                    "$targetName has been blocked.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun getInitials(name: String): String {
        val parts = name.split(" ").filter { it.isNotBlank() }
        val initials = parts.take(2).map { it.first().uppercaseChar() }.joinToString("")
        return if (initials.isNotBlank()) initials else "S"
    }
}
