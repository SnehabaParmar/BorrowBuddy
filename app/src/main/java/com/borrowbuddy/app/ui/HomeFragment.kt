package com.borrowbuddy.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.borrowbuddy.app.MainActivity
import com.borrowbuddy.app.R
import com.borrowbuddy.app.adapter.CampusItemAdapter
import com.borrowbuddy.app.databinding.FragmentHomeBinding
import com.borrowbuddy.app.model.CampusItem
import com.borrowbuddy.app.model.GlanceRequest
import com.borrowbuddy.app.util.CampusLocationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var itemAdapter: CampusItemAdapter
    private val allItems = mutableListOf<CampusItem>()
    private val displayedItems = mutableListOf<CampusItem>()

    private var firestoreListener: ListenerRegistration? = null

    // User GPS location for walk distance calculation (80m/min)
    private var userLatitude: Double? = 23.0368
    private var userLongitude: Double? = 72.5452

    // Glance banner auto-rotation (5-second interval)
    private val glanceRequests = mutableListOf(
        GlanceRequest(
            id = "req_1",
            name = "Harsh Mistry",
            location = "College Library",
            item = "Scientific Calculator",
            avatarLetter = "H",
            neededBy = "Today, 2:00 PM",
            description = "Need a scientific calculator for practical examination in Lab 2.",
            category = "TECH",
            quantity = 1,
            state = "ACTIVE",
            requesterUid = "user_current"
        ),
        GlanceRequest(
            id = "req_2",
            name = "Priya Patel",
            location = "Science Department",
            item = "Lab Aprons",
            avatarLetter = "P",
            neededBy = "Tomorrow",
            description = "Clean white lab coat needed for chemistry practical session.",
            category = "LAB",
            quantity = 1,
            state = "ACTIVE",
            requesterUid = "u1"
        ),
        GlanceRequest(
            id = "req_3",
            name = "Dev Patel",
            location = "B-Block Canteen",
            item = "Engineering Drafter",
            avatarLetter = "D",
            neededBy = "ASAP",
            description = "Mini drafter with clamp needed for design graphics sheet.",
            category = "LAB",
            quantity = 1,
            state = "ACTIVE",
            requesterUid = "u7"
        ),
        GlanceRequest(
            id = "req_4",
            name = "Aarav",
            location = "CS Lab 3",
            item = "Type-C HDMI Dongle",
            avatarLetter = "A",
            neededBy = "Today, 3:30 PM",
            description = "Multiport adapter needed for demo presentation to faculty.",
            category = "TECH",
            quantity = 1,
            state = "ACTIVE",
            requesterUid = "u_aarav"
        )
    )
    private var currentGlanceIndex = 0
    private val glanceHandler = Handler(Looper.getMainLooper())
    private val glanceRunnable = object : Runnable {
        override fun run() {
            if (_binding != null) {
                val activeRequests = glanceRequests.filter { it.state == "ACTIVE" }
                if (activeRequests.isNotEmpty()) {
                    currentGlanceIndex = (currentGlanceIndex + 1) % activeRequests.size
                    updateGlanceCard(activeRequests[currentGlanceIndex], currentGlanceIndex)
                    glanceHandler.postDelayed(this, 5000)
                }
            }
        }
    }

    private var selectedCategory: String? = null
    private var filterCriteria = FilterCriteria(price = "ALL", department = "ALL", zone = "ALL")
    private var selectedFilterChip: String = "ALL" // ALL, FREE, PAID, IMBA, IMSC

    // Permission launcher for foreground location
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
            if (fineGranted || coarseGranted) {
                fetchUserLocation()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupGreeting()
        setupGlanceCard()
        setupRecyclerView()
        setupSearchAndFilters()
        setupCategoryTiles()
        setupNotifications()
        setupLocation()
        loadInitialItems()
        listenForFirestoreItems()
    }

    private fun setupGreeting() {
        val user = auth.currentUser
        val displayName = user?.displayName
        if (!displayName.isNullOrBlank()) {
            val firstName = displayName.split(" ").firstOrNull() ?: displayName
            binding.tvGreeting.text = "Hello, $firstName!"
        } else {
            binding.tvGreeting.text = "Hello, Harsh!"
        }

        user?.uid?.let { uid ->
            firestore.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    if (_binding != null && document.exists()) {
                        val name = document.getString("name")
                        if (!name.isNullOrBlank()) {
                            val firstName = name.split(" ").firstOrNull() ?: name
                            binding.tvGreeting.text = "Hello, $firstName!"
                        }
                    }
                }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGlanceCard() {
        val activeRequests = glanceRequests.filter { it.state == "ACTIVE" }
        if (activeRequests.isNotEmpty()) {
            updateGlanceCard(activeRequests[0], 0)
            glanceHandler.postDelayed(glanceRunnable, 5000)
        } else {
            binding.cardGlance.visibility = View.GONE
        }

        binding.btnGlanceHelp.setOnClickListener {
            val currentActive = glanceRequests.filter { it.state == "ACTIVE" }
            if (currentActive.isNotEmpty()) {
                val req = currentActive[currentGlanceIndex % currentActive.size]
                val helpSheet = HelpRequestBottomSheetFragment(req) { fulfilledReq ->
                    val idx = glanceRequests.indexOfFirst { it.id == fulfilledReq.id }
                    if (idx != -1) {
                        glanceRequests[idx] = fulfilledReq
                    }
                    val remaining = glanceRequests.filter { it.state == "ACTIVE" }
                    if (remaining.isNotEmpty()) {
                        currentGlanceIndex %= remaining.size
                        updateGlanceCard(remaining[currentGlanceIndex], currentGlanceIndex)
                    } else {
                        binding.cardGlance.visibility = View.GONE
                    }
                }
                helpSheet.show(childFragmentManager, "HelpRequestSheet")
            }
        }

        // Pause auto-rotation when touched/swiped, resume on release
        binding.cardGlance.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    glanceHandler.removeCallbacks(glanceRunnable)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    glanceHandler.postDelayed(glanceRunnable, 5000)
                }
            }
            false
        }
    }

    private fun updateGlanceCard(req: GlanceRequest, index: Int) {
        val activeRequests = glanceRequests.filter { it.state == "ACTIVE" }
        if (activeRequests.isEmpty()) {
            binding.cardGlance.visibility = View.GONE
            return
        }
        binding.cardGlance.visibility = View.VISIBLE

        // Visual Hero: Requested Item Name
        binding.tvGlanceHeadline.text = req.item.ifBlank { "${req.name} needs an item" }

        // Requester Soft Avatar & Location
        binding.tvGlanceAvatar.text = req.avatarLetter.ifBlank { req.name.firstOrNull()?.uppercase() ?: "S" }
        binding.tvGlanceLocation.text = "${req.name} • ${req.location}"

        // Contextual navigation to Requester's Public Student Profile via reliable requester UID
        val openRequesterProfile: (View) -> Unit = {
            if (req.requesterUid.isNotBlank()) {
                com.borrowbuddy.app.StudentProfileActivity.start(requireContext(), req.requesterUid, req.name)
            } else {
                android.widget.Toast.makeText(requireContext(), "Requester profile is currently unavailable", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        binding.tvGlanceAvatar.setOnClickListener(openRequesterProfile)
        binding.tvGlanceLocation.setOnClickListener(openRequesterProfile)

        // Needed By Information (Contextual)
        if (req.neededBy.isNotBlank()) {
            binding.tvGlanceNeededBy.visibility = View.VISIBLE
            binding.tvGlanceNeededBy.text = "Needed by ${req.neededBy}"
        } else {
            binding.tvGlanceNeededBy.visibility = View.GONE
        }

        // Invisible legacy field for backward compatibility
        binding.tvGlanceText.text = "${req.name} at ${req.location} needs a ${req.item}"

        // Subtle White Carousel Pagination Indicators
        val activeWidth = (12 * resources.displayMetrics.density).toInt()
        val inactiveWidth = (4 * resources.displayMetrics.density).toInt()
        val dotHeight = (4 * resources.displayMetrics.density).toInt()

        val dots = listOf(binding.dot0, binding.dot1, binding.dot2)
        dots.forEachIndexed { i, dot ->
            val layoutParams = dot.layoutParams
            layoutParams.height = dotHeight
            if (i == (index % dots.size)) {
                layoutParams.width = activeWidth
                dot.alpha = 1.0f
                dot.setBackgroundResource(R.drawable.bg_glance_dot_active)
            } else {
                layoutParams.width = inactiveWidth
                dot.alpha = 0.35f
                dot.setBackgroundResource(R.drawable.bg_glance_dot_inactive)
            }
            dot.layoutParams = layoutParams
        }
    }

    fun addCampusRequest(request: GlanceRequest) {
        glanceRequests.add(0, request)
        currentGlanceIndex = 0
        if (_binding != null) {
            updateGlanceCard(request, 0)
            glanceHandler.removeCallbacks(glanceRunnable)
            glanceHandler.postDelayed(glanceRunnable, 5000)
        }
    }

    private fun setupRecyclerView() {
        itemAdapter = CampusItemAdapter(displayedItems) { clickedItem ->
            val detailSheet = ItemDetailBottomSheetFragment(clickedItem)
            detailSheet.show(childFragmentManager, "ItemDetail")
        }

        binding.rvCampusItems.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = itemAdapter
            isNestedScrollingEnabled = false
        }

        // View All button navigates directly to the Explore tab (slot 1)
        binding.btnViewAll.setOnClickListener {
            (activity as? MainActivity)?.navigateToTab(1)
        }
    }

    private fun setupSearchAndFilters() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
        }

        // Filter button toggles the visibility of the chips
        binding.btnFilterToggle.setOnClickListener {
            val isVisible = binding.scrollFilterChips.visibility == View.VISIBLE
            binding.scrollFilterChips.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        binding.btnFilterToggle.setOnLongClickListener {
            val filterSheet = FilterBottomSheetFragment(filterCriteria) { updatedCriteria ->
                filterCriteria = updatedCriteria
                selectedFilterChip = when {
                    updatedCriteria.price == "FREE" -> "FREE"
                    updatedCriteria.price == "PAID" -> "PAID"
                    updatedCriteria.department == "IMBA" -> "IMBA"
                    updatedCriteria.department == "IMSC" -> "IMSC"
                    else -> "ALL"
                }
                syncChipsUI()
                applyFilters()
            }
            filterSheet.show(childFragmentManager, "FilterBottomSheet")
            true
        }

        setupChips()

        binding.btnResetSearch.setOnClickListener {
            resetAllFilters()
        }
    }

    private fun setupChips() {
        val chips = listOf(
            binding.chipAll to "ALL",
            binding.chipFree to "FREE",
            binding.chipPaid to "PAID",
            binding.chipIMBA to "IMBA",
            binding.chipIMSC to "IMSC"
        )

        chips.forEach { (chipView, filterKey) ->
            chipView.setOnClickListener {
                selectedFilterChip = filterKey
                filterCriteria = when (filterKey) {
                    "FREE" -> filterCriteria.copy(price = "FREE")
                    "PAID" -> filterCriteria.copy(price = "PAID")
                    "IMBA" -> filterCriteria.copy(department = "IMBA")
                    "IMSC" -> filterCriteria.copy(department = "IMSC")
                    else -> filterCriteria.copy(price = "ALL", department = "ALL")
                }
                syncChipsUI()
                applyFilters()
            }
        }
    }

    private fun syncChipsUI() {
        val context = context ?: return
        val chips = listOf(
            binding.chipAll to "ALL",
            binding.chipFree to "FREE",
            binding.chipPaid to "PAID",
            binding.chipIMBA to "IMBA",
            binding.chipIMSC to "IMSC"
        )

        chips.forEach { (chip, key) ->
            if (key == selectedFilterChip) {
                chip.setBackgroundResource(R.drawable.bg_chip_selected)
                chip.setTextColor(ContextCompat.getColor(context, R.color.white))
            } else {
                chip.setBackgroundResource(R.drawable.bg_chip_unselected)
                chip.setTextColor(ContextCompat.getColor(context, R.color.brand_navy))
            }
        }
    }

    private fun resetAllFilters() {
        binding.etSearch.setText("")
        selectedCategory = null
        selectedFilterChip = "ALL"
        filterCriteria = FilterCriteria()
        syncChipsUI()
        resetCategoryTileBackgrounds()
        applyFilters()
    }

    private fun setupCategoryTiles() {
        binding.tileCatTech.setOnClickListener { handleCategoryClick("TECH", binding.tileCatTech) }
        binding.tileCatBooks.setOnClickListener { handleCategoryClick("BOOKS", binding.tileCatBooks) }
        binding.tileCatLab.setOnClickListener { handleCategoryClick("LAB", binding.tileCatLab) }
        binding.tileCatEvent.setOnClickListener { handleCategoryClick("EVENT", binding.tileCatEvent) }
        binding.tileCatStationery.setOnClickListener { handleCategoryClick("STATIONERY", binding.tileCatStationery) }
        binding.tileCatSports.setOnClickListener { handleCategoryClick("SPORTS", binding.tileCatSports) }
        binding.tileCatBusiness.setOnClickListener { handleCategoryClick("BUSINESS", binding.tileCatBusiness) }
        binding.tileCatCampus.setOnClickListener { handleCategoryClick("CAMPUS", binding.tileCatCampus) }

        binding.btnSeeAllCategories.setOnClickListener {
            selectedCategory = null
            resetCategoryTileBackgrounds()
            applyFilters()
        }
    }

    private fun handleCategoryClick(category: String, clickedView: View) {
        if (selectedCategory == category) {
            selectedCategory = null
            resetCategoryTileBackgrounds()
        } else {
            selectedCategory = category
            resetCategoryTileBackgrounds()
            clickedView.setBackgroundResource(R.drawable.bg_category_tile_selected)
        }
        applyFilters()
    }

    private fun resetCategoryTileBackgrounds() {
        binding.tileCatTech.setBackgroundResource(R.drawable.bg_category_tile)
        binding.tileCatBooks.setBackgroundResource(R.drawable.bg_category_tile)
        binding.tileCatLab.setBackgroundResource(R.drawable.bg_category_tile)
        binding.tileCatEvent.setBackgroundResource(R.drawable.bg_category_tile)
        binding.tileCatStationery.setBackgroundResource(R.drawable.bg_category_tile)
        binding.tileCatSports.setBackgroundResource(R.drawable.bg_category_tile)
        binding.tileCatBusiness.setBackgroundResource(R.drawable.bg_category_tile)
        binding.tileCatCampus.setBackgroundResource(R.drawable.bg_category_tile)
    }

    private fun setupNotifications() {
        binding.btnNotificationBell.setOnClickListener {
            binding.viewNotifDot.visibility = View.GONE
            val intent = android.content.Intent(requireContext(), com.borrowbuddy.app.NotificationsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupLocation() {
        val finePerm = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val coarsePerm = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
        if (finePerm == PackageManager.PERMISSION_GRANTED || coarsePerm == PackageManager.PERMISSION_GRANTED) {
            fetchUserLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchUserLocation() {
        try {
            val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val location = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (location != null) {
                userLatitude = location.latitude
                userLongitude = location.longitude
                recalculateProximity()
            }
        } catch (_: Exception) {}
    }

    private fun recalculateProximity() {
        val updatedList = allItems.map { item ->
            val zoneName = item.pickupSpot.split("•").first().trim()
            val formatted = CampusLocationHelper.formatZoneWithWalkTime(userLatitude, userLongitude, zoneName)
            item.copy(pickupSpot = formatted)
        }
        allItems.clear()
        allItems.addAll(updatedList)
        applyFilters()
    }

    private fun loadInitialItems() {
        val sampleItems = listOf(
            CampusItem(
                id = "c1",
                title = "Scientific Calculator",
                description = "Casio fx-991EX ClassWiz in perfect condition. Ideal for engineering maths, stats, and exam calculations.",
                category = "TECH",
                priceLabel = "FREE",
                isFree = true,
                pickupSpot = CampusLocationHelper.formatZoneWithWalkTime(userLatitude, userLongitude, "Main Library"),
                ownerName = "Priya Patel",
                ownerUid = "u1",
                rating = "★ 5.0",
                drawableRes = R.drawable.item_calculator,
                department = "IMSc.IT",
                condition = "Like New",
                maxDuration = "Up to 3 days",
                securityDeposit = "None",
                completedLends = 18,
                ownerCourse = "IMSc.IT Sem 4"
            ),
            CampusItem(
                id = "c2",
                title = "Type-C Multiport Dongle",
                description = "HDMI, 2x USB 3.0, and SD Card adapter. Essential for laptop presentations and lab assignments.",
                category = "TECH",
                priceLabel = "₹20/day",
                isFree = false,
                pickupSpot = CampusLocationHelper.formatZoneWithWalkTime(userLatitude, userLongitude, "IMBA Block"),
                ownerName = "Rahul Sharma",
                ownerUid = "u2",
                rating = "★ 4.9",
                drawableRes = R.drawable.item_dongle,
                department = "IMBA",
                condition = "Pristine",
                maxDuration = "Up to 2 days",
                securityDeposit = "₹100 (Refundable)",
                completedLends = 24,
                ownerCourse = "IMBA Sem 3"
            ),
            CampusItem(
                id = "c3",
                title = "DSA in Java Notes",
                description = "Comprehensive handwritten Data Structures & Algorithms notes covering trees, graphs, and dynamic programming.",
                category = "BOOKS",
                priceLabel = "FREE",
                isFree = true,
                pickupSpot = CampusLocationHelper.formatZoneWithWalkTime(userLatitude, userLongitude, "Main Library"),
                ownerName = "Aman Verma",
                ownerUid = "u3",
                rating = "★ 5.0",
                drawableRes = R.drawable.item_notes,
                department = "IMSc.IT",
                condition = "Handwritten • Complete",
                maxDuration = "Up to 7 days",
                securityDeposit = "None",
                completedLends = 32,
                ownerCourse = "IMSc.IT Sem 6"
            ),
            CampusItem(
                id = "c4",
                title = "Arduino Uno Kit",
                description = "Complete robotics starter set with breadboard, sensors, jumper cables, and LEDs.",
                category = "LAB",
                priceLabel = "₹30/day",
                isFree = false,
                pickupSpot = CampusLocationHelper.formatZoneWithWalkTime(userLatitude, userLongitude, "IMSc.IT Lab"),
                ownerName = "Sneha Joshi",
                ownerUid = "u4",
                rating = "★ 4.8",
                drawableRes = R.drawable.item_arduino,
                department = "IMSc.IT",
                condition = "Fully Working",
                maxDuration = "Up to 5 days",
                securityDeposit = "College ID",
                completedLends = 12,
                ownerCourse = "IMSc.IT Sem 4"
            ),
            CampusItem(
                id = "c5",
                title = "White Lab Coat (M)",
                description = "Clean, freshly washed cotton lab coat compliant with campus laboratory guidelines.",
                category = "LAB",
                priceLabel = "FREE",
                isFree = true,
                pickupSpot = CampusLocationHelper.formatZoneWithWalkTime(userLatitude, userLongitude, "Science Block"),
                ownerName = "Karan Shah",
                ownerUid = "u5",
                rating = "★ 4.7",
                drawableRes = R.drawable.item_lab_coat,
                department = "IMBA",
                condition = "Freshly Washed",
                maxDuration = "Up to 1 day",
                securityDeposit = "None",
                completedLends = 15,
                ownerCourse = "IMBA Sem 2"
            ),
            CampusItem(
                id = "c6",
                title = "DSLR Tripod Stand",
                description = "Adjustable 50-inch camera tripod with 360-degree ball head and mobile phone clamp.",
                category = "EVENT",
                priceLabel = "₹50/day",
                isFree = false,
                pickupSpot = CampusLocationHelper.formatZoneWithWalkTime(userLatitude, userLongitude, "Campus Canteen"),
                ownerName = "Ananya Mehta",
                ownerUid = "u6",
                rating = "★ 4.9",
                drawableRes = R.drawable.item_tripod,
                department = "IMBA",
                condition = "Excellent",
                maxDuration = "Up to 2 days",
                securityDeposit = "₹200 (Refundable)",
                completedLends = 9,
                ownerCourse = "IMBA Sem 4"
            ),
            CampusItem(
                id = "c7",
                title = "Braided Charging Cables",
                description = "1.5m braided quick-charge cables (Type-C & Lightning) for emergency phone charging between classes.",
                category = "TECH",
                priceLabel = "FREE",
                isFree = true,
                pickupSpot = CampusLocationHelper.formatZoneWithWalkTime(userLatitude, userLongitude, "Campus Canteen"),
                ownerName = "Dev Patel",
                ownerUid = "u7",
                rating = "★ 5.0",
                drawableRes = R.drawable.item_cables,
                department = "IMSc.IT",
                condition = "Good",
                maxDuration = "Up to 4 hours",
                securityDeposit = "None",
                completedLends = 28,
                ownerCourse = "IMSc.IT Sem 4"
            ),
            CampusItem(
                id = "c8",
                title = "DBMS 7th Edition",
                description = "Database System Concepts reference textbook by Silberschatz and Korth.",
                category = "BOOKS",
                priceLabel = "FREE",
                isFree = true,
                pickupSpot = CampusLocationHelper.formatZoneWithWalkTime(userLatitude, userLongitude, "Main Library"),
                ownerName = "Meera Desai",
                ownerUid = "u8",
                rating = "★ 4.9",
                drawableRes = R.drawable.item_dbms_book,
                department = "IMSc.IT",
                condition = "Crisp Pages",
                maxDuration = "Up to 5 days",
                securityDeposit = "None",
                completedLends = 19,
                ownerCourse = "IMSc.IT Sem 4"
            ),
            CampusItem(
                id = "c9",
                title = "Yonex Badminton Set",
                description = "Pair of lightweight carbon badminton rackets and Mavis 350 shuttles for university sports ground.",
                category = "SPORTS",
                priceLabel = "₹15/day",
                isFree = false,
                pickupSpot = CampusLocationHelper.formatZoneWithWalkTime(userLatitude, userLongitude, "Sports Ground"),
                ownerName = "Rohan V.",
                ownerUid = "u9",
                rating = "★ 4.9",
                drawableRes = R.drawable.item_badminton,
                department = "IMBA",
                condition = "Great Tension",
                maxDuration = "Up to 1 day",
                securityDeposit = "None",
                completedLends = 11,
                ownerCourse = "IMBA Sem 1"
            ),
            CampusItem(
                id = "c10",
                title = "Wireless Presentation Clicker",
                description = "2.4GHz USB wireless slide presenter with red laser pointer for auditorium seminars.",
                category = "EVENT",
                priceLabel = "FREE",
                isFree = true,
                pickupSpot = CampusLocationHelper.formatZoneWithWalkTime(userLatitude, userLongitude, "Clock Tower"),
                ownerName = "Kavita R.",
                ownerUid = "u10",
                rating = "★ 5.0",
                drawableRes = R.drawable.item_clicker,
                department = "IMSc.IT",
                condition = "New Batteries",
                maxDuration = "Up to 1 day",
                securityDeposit = "None",
                completedLends = 14,
                ownerCourse = "IMSc.IT Sem 4"
            )
        )

        val userItems = com.borrowbuddy.app.model.StudentProfileRepository.getCurrentUserItems().map { item ->
            val zoneName = item.pickupSpot.split("•").first().trim()
            val formatted = CampusLocationHelper.formatZoneWithWalkTime(userLatitude, userLongitude, zoneName)
            item.copy(pickupSpot = formatted)
        }

        allItems.clear()
        allItems.addAll(userItems)
        val remainingSamples = sampleItems.filter { sample ->
            userItems.none { it.id == sample.id || (it.title == sample.title && it.ownerName == sample.ownerName) }
        }
        allItems.addAll(remainingSamples)
        applyFilters()
    }

    private fun listenForFirestoreItems() {
        firestoreListener = firestore.collection("items")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (_binding == null || snapshot == null) return@addSnapshotListener

                val remoteItems = mutableListOf<CampusItem>()
                for (doc in snapshot.documents) {
                    val spot = doc.getString("pickupSpot") ?: "Main Library"
                    val item = CampusItem(
                        id = doc.id,
                        title = doc.getString("title").orEmpty(),
                        description = doc.getString("description").orEmpty(),
                        category = doc.getString("category") ?: "TECH",
                        priceLabel = doc.getString("priceLabel") ?: "FREE",
                        isFree = doc.getBoolean("isFree") ?: true,
                        pickupSpot = CampusLocationHelper.formatZoneWithWalkTime(userLatitude, userLongitude, spot),
                        ownerName = doc.getString("ownerName") ?: "Student",
                        ownerUid = doc.getString("ownerUid").orEmpty(),
                        rating = doc.getString("rating") ?: "★ 5.0",
                        imageUri = doc.getString("imageUri").orEmpty(),
                        department = doc.getString("department") ?: "IMSc.IT",
                        createdAt = doc.getLong("createdAt") ?: 0L
                    )
                    if (item.title.isNotEmpty()) {
                        remoteItems.add(item)
                    }
                }

                if (remoteItems.isNotEmpty()) {
                    val sampleRemaining = allItems.filter { local ->
                        remoteItems.none { it.id == local.id || (it.title == local.title && it.ownerName == local.ownerName) }
                    }
                    allItems.clear()
                    allItems.addAll(remoteItems)
                    allItems.addAll(sampleRemaining)
                    applyFilters()
                }
            }
    }

    private fun applyFilters() {
        val query = binding.etSearch.text?.toString()?.trim().orEmpty().lowercase()

        val filtered = allItems.filter { item ->
            val matchesQuery = query.isEmpty() ||
                    item.title.lowercase().contains(query) ||
                    item.description.lowercase().contains(query) ||
                    item.pickupSpot.lowercase().contains(query) ||
                    item.ownerName.lowercase().contains(query)

            val matchesCategory = selectedCategory == null ||
                    item.category.equals(selectedCategory, ignoreCase = true)

            val matchesPrice = when (filterCriteria.price) {
                "FREE" -> item.isFree
                "PAID" -> !item.isFree
                else -> true
            }

            val matchesDept = when (filterCriteria.department) {
                "IMBA" -> item.department.equals("IMBA", ignoreCase = true)
                "IMSC" -> item.department.contains("IMSc", ignoreCase = true)
                else -> true
            }

            val matchesZone = when (filterCriteria.zone) {
                "LIB" -> item.pickupSpot.contains("Library", ignoreCase = true)
                "IMBA" -> item.pickupSpot.contains("IMBA", ignoreCase = true)
                "LAB" -> item.pickupSpot.contains("Lab", ignoreCase = true)
                "CANTEEN" -> item.pickupSpot.contains("Canteen", ignoreCase = true)
                else -> true
            }

            // Proximity rule for Home screen "Available near you":
            // Display ONLY items physically nearby (within 5 minutes walk), unless explicitly searched by text
            val isNearbyItem = if (query.isNotEmpty()) true else {
                CampusLocationHelper.isNearby(userLatitude, userLongitude, item.pickupSpot, maxMinutes = 5)
            }

            matchesQuery && matchesCategory && matchesPrice && matchesDept && matchesZone && isNearbyItem
        }

        // Proximity sort: closest items appear first
        val sorted = CampusLocationHelper.sortItemsByProximity(filtered, userLatitude, userLongitude)

        displayedItems.clear()
        displayedItems.addAll(sorted)
        itemAdapter.notifyDataSetChanged()

        if (displayedItems.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.rvCampusItems.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.rvCampusItems.visibility = View.VISIBLE
        }
    }

    fun addNewItem(item: CampusItem) {
        val formattedSpot = CampusLocationHelper.formatZoneWithWalkTime(userLatitude, userLongitude, item.pickupSpot)
        val prepared = item.copy(pickupSpot = formattedSpot)
        allItems.add(0, prepared)
        if (_binding != null) {
            applyFilters()
            binding.rvCampusItems.scrollToPosition(0)
        }
    }

    override fun onPause() {
        super.onPause()
        glanceHandler.removeCallbacks(glanceRunnable)
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            val activeRequests = glanceRequests.filter { it.state == "ACTIVE" }
            if (activeRequests.isNotEmpty()) {
                glanceHandler.removeCallbacks(glanceRunnable)
                glanceHandler.postDelayed(glanceRunnable, 5000)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        glanceHandler.removeCallbacks(glanceRunnable)
        firestoreListener?.remove()
        _binding = null
    }
}
