package com.borrowbuddy.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.borrowbuddy.app.databinding.ActivityMainBinding
import com.borrowbuddy.app.model.CampusItem
import com.borrowbuddy.app.model.GlanceRequest
import com.borrowbuddy.app.ui.ActionChoiceBottomSheetFragment
import com.borrowbuddy.app.ui.ExchangesFragment
import com.borrowbuddy.app.ui.ExploreFragment
import com.borrowbuddy.app.ui.HomeFragment
import com.borrowbuddy.app.ui.ProfileFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var homeFragment: HomeFragment? = null

    private val listItemLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data ?: return@registerForActivityResult
                val newItem = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    data.getSerializableExtra("EXTRA_NEW_ITEM", CampusItem::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    data.getSerializableExtra("EXTRA_NEW_ITEM") as? CampusItem
                }

                // Switch to Home tab to show new listing
                binding.curvedBottomNav.selectTab(0)
                if (newItem != null) {
                    val currentHome = (supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? HomeFragment)
                        ?: homeFragment
                    currentHome?.addNewItem(newItem)
                }
            }
        }

    private val requestItemLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data ?: return@registerForActivityResult
                val item = data.getStringExtra("EXTRA_REQ_ITEM") ?: return@registerForActivityResult
                val location = data.getStringExtra("EXTRA_REQ_LOCATION") ?: "College Library"
                val name = data.getStringExtra("EXTRA_REQ_NAME") ?: "Harsh"
                val avatar = data.getStringExtra("EXTRA_REQ_AVATAR") ?: "H"
                val neededBy = data.getStringExtra("EXTRA_REQ_NEEDED_BY") ?: "ASAP"
                val desc = data.getStringExtra("EXTRA_REQ_DESC").orEmpty()
                val qty = data.getIntExtra("EXTRA_REQ_QTY", 1)
                val exp = data.getIntExtra("EXTRA_REQ_EXP", 24)

                val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "user_current"

                val newReq = GlanceRequest(
                    id = "req_" + System.currentTimeMillis(),
                    name = name,
                    location = location,
                    item = item,
                    avatarLetter = avatar,
                    description = desc,
                    neededBy = neededBy,
                    quantity = qty,
                    expirationHours = exp,
                    state = "ACTIVE",
                    requesterUid = currentUid
                )

                binding.curvedBottomNav.selectTab(0)
                val currentHome = (supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? HomeFragment)
                    ?: homeFragment
                currentHome?.addCampusRequest(newReq)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Reliable startup profile synchronization & local photo initialization
        val startupUid = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid } catch (_: Exception) { null }
        com.borrowbuddy.app.model.StudentProfileRepository.initLocalPhoto(applicationContext, startupUid)
        com.borrowbuddy.app.model.StudentProfileRepository.syncWithFirebase(
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.fragmentContainer) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        setupCurvedNavigation()

        // Handle system back navigation: return to Home (Tab 0) if on secondary tabs
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.curvedBottomNav.getSelectedTab() != 0) {
                    navigateToTab(0)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        if (savedInstanceState == null) {
            val initialHome = HomeFragment()
            homeFragment = initialHome
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, initialHome, "HOME_TAG")
                .commit()
            val initialTab = intent.getIntExtra("EXTRA_NAVIGATE_TAB", -1)
            if (initialTab >= 0) {
                navigateToTab(initialTab)
            }
        } else {
            homeFragment = supportFragmentManager.findFragmentByTag("HOME_TAG") as? HomeFragment
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tab = intent.getIntExtra("EXTRA_NAVIGATE_TAB", -1)
        if (tab >= 0) {
            navigateToTab(tab)
        }
    }

    private fun setupCurvedNavigation() {
        binding.curvedBottomNav.setOnTabSelectedListener { index ->
            when (index) {
                0 -> {
                    val frag = homeFragment ?: HomeFragment().also { homeFragment = it }
                    switchFragment(frag, "HOME_TAG")
                }
                1 -> {
                    switchFragment(ExploreFragment(), "EXPLORE_TAG")
                }
                3 -> {
                    switchFragment(ExchangesFragment(), "EXCHANGES_TAG")
                }
                4 -> {
                    switchFragment(ProfileFragment(), "PROFILE_TAG")
                }
            }
        }

        binding.curvedBottomNav.setOnCenterAddClickListener {
            val choiceSheet = ActionChoiceBottomSheetFragment(
                onChooseLend = {
                    val intent = Intent(this, ListItemActivity::class.java)
                    listItemLauncher.launch(intent)
                },
                onChooseRequest = {
                    val intent = Intent(this, RequestItemActivity::class.java)
                    requestItemLauncher.launch(intent)
                }
            )
            choiceSheet.show(supportFragmentManager, "ActionChoiceBottomSheet")
        }
    }

    fun navigateToTab(index: Int) {
        binding.curvedBottomNav.selectTab(index)
    }

    private fun switchFragment(fragment: Fragment, tag: String) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragmentContainer, fragment, tag)
            .commit()
    }
}
