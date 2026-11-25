package com.borrowbuddy.app.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.borrowbuddy.app.R
import com.borrowbuddy.app.adapter.CampusItemAdapter
import com.borrowbuddy.app.adapter.TrendingItemAdapter
import com.borrowbuddy.app.databinding.FragmentExploreBinding
import com.borrowbuddy.app.model.CampusItem
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class ExploreFragment : Fragment() {

    private var _binding: FragmentExploreBinding? = null
    private val binding get() = _binding!!

    private val firestore by lazy { FirebaseFirestore.getInstance() }

    // Master dataset across entire campus marketplace
    private val allExploreItems = mutableListOf<CampusItem>()
    private val displayedItems = mutableListOf<CampusItem>()
    private val trendingItems = mutableListOf<CampusItem>()

    private lateinit var itemAdapter: CampusItemAdapter
    private lateinit var trendingAdapter: TrendingItemAdapter

    // Search and filter criteria state
    private var searchQuery: String = ""
    private var selectedCategory: String? = null // null means "ALL"
    private var filterCriteria = ExploreFilterCriteria()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExploreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerViews()
        setupSearch()
        setupCategoryTiles()
        setupQuickFilterChips()
        setupFilterSheet()
        loadExploreItems()
    }

    private fun setupRecyclerViews() {
        // Main 2-column marketplace grid
        itemAdapter = CampusItemAdapter(displayedItems) { clickedItem ->
            val detailSheet = ItemDetailBottomSheetFragment(clickedItem)
            detailSheet.show(childFragmentManager, "ItemDetail")
        }

        binding.rvExploreItems.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = itemAdapter
            isNestedScrollingEnabled = false
        }

        // Horizontal trending/popular items row
        trendingAdapter = TrendingItemAdapter(trendingItems) { clickedItem ->
            val detailSheet = ItemDetailBottomSheetFragment(clickedItem)
            detailSheet.show(childFragmentManager, "ItemDetailTrending")
        }

        binding.rvTrendingItems.apply {
            layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = trendingAdapter
        }

        // Empty state reset button
        binding.btnResetSearchAndFilter.setOnClickListener {
            resetAllFilters()
        }

        // Reset category view all button
        binding.btnResetCategoryFilter.setOnClickListener {
            selectedCategory = null
            updateCategoryTilesUI()
            applyFiltersAndSort()
        }
    }

    private fun setupSearch() {
        binding.etExploreSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim().orEmpty()
                binding.btnClearExploreSearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFiltersAndSort()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearExploreSearch.setOnClickListener {
            binding.etExploreSearch.setText("")
        }
    }

    private fun setupCategoryTiles() {
        val catMap = mapOf(
            binding.tileExploreCatAll to null,
            binding.tileExploreCatTech to "TECH",
            binding.tileExploreCatBooks to "BOOKS",
            binding.tileExploreCatLab to "LAB",
            binding.tileExploreCatEvent to "EVENT",
            binding.tileExploreCatSports to "SPORTS"
        )

        catMap.forEach { (tile, catKey) ->
            tile.setOnClickListener {
                selectedCategory = if (selectedCategory == catKey) null else catKey
                updateCategoryTilesUI()
                applyFiltersAndSort()
            }
        }
    }

    private fun updateCategoryTilesUI() {
        val context = context ?: return
        val tiles = listOf(
            Triple(binding.tileExploreCatAll, binding.tvCatAllTitle, null),
            Triple(binding.tileExploreCatTech, binding.tvCatTechTitle, "TECH"),
            Triple(binding.tileExploreCatBooks, binding.tvCatBooksTitle, "BOOKS"),
            Triple(binding.tileExploreCatLab, binding.tvCatLabTitle, "LAB"),
            Triple(binding.tileExploreCatEvent, binding.tvCatEventTitle, "EVENT"),
            Triple(binding.tileExploreCatSports, binding.tvCatSportsTitle, "SPORTS")
        )

        tiles.forEach { (tile, titleView, catKey) ->
            val isSelected = selectedCategory == catKey
            if (isSelected) {
                tile.setBackgroundResource(R.drawable.bg_category_tile_selected)
                titleView.setTextColor(ContextCompat.getColor(context, R.color.brand_blue))
            } else {
                tile.setBackgroundResource(R.drawable.bg_category_tile)
                titleView.setTextColor(ContextCompat.getColor(context, R.color.brand_navy))
            }
        }
    }

    private fun setupQuickFilterChips() {
        val context = requireContext()

        // 1. Free Only Quick Chip
        binding.chipQuickFree.setOnClickListener {
            filterCriteria = if (filterCriteria.priceType == "FREE") {
                filterCriteria.copy(priceType = "ALL")
            } else {
                filterCriteria.copy(priceType = "FREE")
            }
            syncQuickChipsUI()
            applyFiltersAndSort()
        }

        // 2. Paid Only Quick Chip
        binding.chipQuickPaid.setOnClickListener {
            filterCriteria = if (filterCriteria.priceType == "PAID") {
                filterCriteria.copy(priceType = "ALL")
            } else {
                filterCriteria.copy(priceType = "PAID")
            }
            syncQuickChipsUI()
            applyFiltersAndSort()
        }

        // 3. Top Rated Quick Chip
        binding.chipQuickTopRated.setOnClickListener {
            filterCriteria = if (filterCriteria.sortBy == ExploreSortOption.HIGHEST_RATED) {
                filterCriteria.copy(sortBy = ExploreSortOption.RECENTLY_ADDED)
            } else {
                filterCriteria.copy(sortBy = ExploreSortOption.HIGHEST_RATED)
            }
            syncQuickChipsUI()
            applyFiltersAndSort()
        }

        // 4. Nearest First Quick Chip
        binding.chipQuickNearest.setOnClickListener {
            filterCriteria = if (filterCriteria.sortBy == ExploreSortOption.NEAREST_FIRST) {
                filterCriteria.copy(sortBy = ExploreSortOption.RECENTLY_ADDED)
            } else {
                filterCriteria.copy(sortBy = ExploreSortOption.NEAREST_FIRST)
            }
            syncQuickChipsUI()
            applyFiltersAndSort()
        }
    }

    private fun syncQuickChipsUI() {
        val context = context ?: return

        // Free chip styling
        if (filterCriteria.priceType == "FREE") {
            binding.chipQuickFree.setBackgroundResource(R.drawable.bg_chip_selected)
            binding.chipQuickFree.setTextColor(ContextCompat.getColor(context, R.color.white))
        } else {
            binding.chipQuickFree.setBackgroundResource(R.drawable.bg_chip_unselected)
            binding.chipQuickFree.setTextColor(ContextCompat.getColor(context, R.color.brand_navy))
        }

        // Paid chip styling
        if (filterCriteria.priceType == "PAID") {
            binding.chipQuickPaid.setBackgroundResource(R.drawable.bg_chip_selected)
            binding.chipQuickPaid.setTextColor(ContextCompat.getColor(context, R.color.white))
        } else {
            binding.chipQuickPaid.setBackgroundResource(R.drawable.bg_chip_unselected)
            binding.chipQuickPaid.setTextColor(ContextCompat.getColor(context, R.color.brand_navy))
        }

        // Top Rated chip styling
        if (filterCriteria.sortBy == ExploreSortOption.HIGHEST_RATED) {
            binding.chipQuickTopRated.setBackgroundResource(R.drawable.bg_chip_selected)
            binding.chipQuickTopRated.setTextColor(ContextCompat.getColor(context, R.color.white))
        } else {
            binding.chipQuickTopRated.setBackgroundResource(R.drawable.bg_chip_unselected)
            binding.chipQuickTopRated.setTextColor(ContextCompat.getColor(context, R.color.brand_navy))
        }

        // Nearest chip styling
        if (filterCriteria.sortBy == ExploreSortOption.NEAREST_FIRST) {
            binding.chipQuickNearest.setBackgroundResource(R.drawable.bg_chip_selected)
            binding.chipQuickNearest.setTextColor(ContextCompat.getColor(context, R.color.white))
        } else {
            binding.chipQuickNearest.setBackgroundResource(R.drawable.bg_chip_unselected)
            binding.chipQuickNearest.setTextColor(ContextCompat.getColor(context, R.color.brand_navy))
        }

        // Update active sort pill text
        binding.tvActiveSortLabel.text = filterCriteria.sortBy.label

        // Filter active dot on filter icon
        val hasActiveFilters = filterCriteria.isFiltered() || selectedCategory != null
        binding.viewFilterActiveDot.visibility = if (hasActiveFilters) View.VISIBLE else View.GONE
    }

    private fun setupFilterSheet() {
        val openSheetListener = View.OnClickListener {
            val filterSheet = ExploreFilterBottomSheetFragment(filterCriteria) { updatedCriteria ->
                filterCriteria = updatedCriteria
                syncQuickChipsUI()
                applyFiltersAndSort()
            }
            filterSheet.show(childFragmentManager, "ExploreFilterSheet")
        }

        binding.btnExploreFilterToggle.setOnClickListener(openSheetListener)
        binding.chipFilterSheetTrigger.setOnClickListener(openSheetListener)
    }

    private fun applyFiltersAndSort() {
        var result = allExploreItems.filter { item ->
            // 1. Search Query Filter
            val matchesQuery = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.lowercase()
                item.title.lowercase().contains(q) ||
                        item.description.lowercase().contains(q) ||
                        item.category.lowercase().contains(q) ||
                        item.pickupSpot.lowercase().contains(q) ||
                        item.ownerName.lowercase().contains(q)
            }

            // 2. Category Filter
            val matchesCat = if (selectedCategory.isNullOrBlank()) {
                true
            } else {
                item.category.equals(selectedCategory, ignoreCase = true)
            }

            // 3. Price Type Filter
            val matchesPrice = when (filterCriteria.priceType) {
                "FREE" -> item.isFree
                "PAID" -> !item.isFree
                else -> true
            }

            // 4. Zone Filter
            val matchesZone = when (filterCriteria.zone) {
                "LIB" -> item.pickupSpot.contains("Library", ignoreCase = true) || item.pickupSpot.contains("Reading", ignoreCase = true)
                "IMBA" -> item.pickupSpot.contains("IMBA", ignoreCase = true)
                "LAB" -> item.pickupSpot.contains("Lab", ignoreCase = true) || item.pickupSpot.contains("Science", ignoreCase = true)
                "CANTEEN" -> item.pickupSpot.contains("Canteen", ignoreCase = true) || item.pickupSpot.contains("Clock", ignoreCase = true)
                else -> true
            }

            // 5. Condition Filter
            val matchesCondition = when (filterCriteria.condition) {
                "LIKE_NEW" -> item.condition.contains("Like New", ignoreCase = true) || item.condition.contains("Pristine", ignoreCase = true)
                else -> true
            }

            matchesQuery && matchesCat && matchesPrice && matchesZone && matchesCondition
        }

        // Apply Sorting
        result = when (filterCriteria.sortBy) {
            ExploreSortOption.NEAREST_FIRST -> {
                result.sortedBy { extractWalkMinutes(it.pickupSpot) }
            }
            ExploreSortOption.LOWEST_PRICE -> {
                result.sortedBy { extractPriceValue(it) }
            }
            ExploreSortOption.HIGHEST_PRICE -> {
                result.sortedByDescending { extractPriceValue(it) }
            }
            ExploreSortOption.HIGHEST_RATED -> {
                result.sortedByDescending { extractRatingValue(it.rating) }
            }
            ExploreSortOption.RECENTLY_ADDED -> {
                result // Natural recent order
            }
        }

        displayedItems.clear()
        displayedItems.addAll(result)
        itemAdapter.notifyDataSetChanged()

        // Update count badge & section title
        binding.tvItemsCountBadge.text = "${displayedItems.size} Available"
        if (searchQuery.isNotBlank()) {
            binding.tvAllListingsTitle.text = "Search Results (\"$searchQuery\")"
            binding.layoutTrendingSection.visibility = View.GONE
        } else if (selectedCategory != null) {
            binding.tvAllListingsTitle.text = "$selectedCategory Listings"
            binding.layoutTrendingSection.visibility = View.GONE
        } else {
            binding.tvAllListingsTitle.text = "All Campus Listings"
            binding.layoutTrendingSection.visibility = View.VISIBLE
        }

        // Empty state visibility
        if (displayedItems.isEmpty()) {
            binding.rvExploreItems.visibility = View.GONE
            binding.layoutEmptyState.visibility = View.VISIBLE
        } else {
            binding.rvExploreItems.visibility = View.VISIBLE
            binding.layoutEmptyState.visibility = View.GONE
        }
    }

    private fun extractWalkMinutes(spot: String): Int {
        val match = Regex("""(\d+)\s*m""").find(spot)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 10
    }

    private fun extractPriceValue(item: CampusItem): Int {
        if (item.isFree) return 0
        val match = Regex("""(\d+)""").find(item.priceLabel)
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 20
    }

    private fun extractRatingValue(rating: String): Float {
        val cleaned = rating.replace("★", "").trim()
        return cleaned.toFloatOrNull() ?: 4.5f
    }

    private fun resetAllFilters() {
        searchQuery = ""
        selectedCategory = null
        filterCriteria = ExploreFilterCriteria()

        binding.etExploreSearch.setText("")
        updateCategoryTilesUI()
        syncQuickChipsUI()
        applyFiltersAndSort()
    }

    private fun loadExploreItems() {
        val defaultItems = listOf(
            CampusItem(
                id = "e1",
                title = "Scientific Calculator",
                description = "CASIO fx-991EX for engineering maths and statistics exams.",
                category = "TECH",
                priceLabel = "FREE",
                isFree = true,
                pickupSpot = "Main Library • 2m",
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
                id = "e2",
                title = "Type-C Multiport Dongle",
                description = "USB-C to HDMI adapter for presentations & project demos.",
                category = "TECH",
                priceLabel = "₹20/day",
                isFree = false,
                pickupSpot = "IMBA Block • 5m",
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
                id = "e3",
                title = "DSA in Java Notes",
                description = "Comprehensive handwritten notes on trees, graphs, and DP.",
                category = "BOOKS",
                priceLabel = "FREE",
                isFree = true,
                pickupSpot = "Reading Hall • 10m",
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
                id = "e4",
                title = "Arduino Uno Kit",
                description = "Microcontroller robotics starter lab set with sensors and wires.",
                category = "LAB",
                priceLabel = "₹30/day",
                isFree = false,
                pickupSpot = "Tech Lab 2 • 8m",
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
                id = "e5",
                title = "White Lab Coat",
                description = "Clean white cotton lab coat size M for chemistry & biotech labs.",
                category = "LAB",
                priceLabel = "FREE",
                isFree = true,
                pickupSpot = "Science Block • 12m",
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
                id = "e6",
                title = "DSLR Tripod Stand",
                description = "Adjustable camera tripod with 360-degree ball mount.",
                category = "EVENT",
                priceLabel = "₹50/day",
                isFree = false,
                pickupSpot = "Campus Canteen • 4m",
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
                id = "e7",
                title = "Braided Charging Cables",
                description = "1.5m quick charge cables for fast charging between lectures.",
                category = "TECH",
                priceLabel = "FREE",
                isFree = true,
                pickupSpot = "Campus Canteen • 4m",
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
                id = "e8",
                title = "DBMS 7th Edition",
                description = "Silberschatz Database Concepts textbook for semester studies.",
                category = "BOOKS",
                priceLabel = "FREE",
                isFree = true,
                pickupSpot = "Main Library • 2m",
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
                id = "e9",
                title = "Yonex Badminton Set",
                description = "Rackets and shuttles ready for play on university badminton courts.",
                category = "SPORTS",
                priceLabel = "₹15/day",
                isFree = false,
                pickupSpot = "Sports Ground • 10m",
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
                id = "e10",
                title = "Wireless Presentation Clicker",
                description = "2.4GHz slide changer with red laser for department presentations.",
                category = "EVENT",
                priceLabel = "FREE",
                isFree = true,
                pickupSpot = "Clock Tower • 12m",
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

        val userItems = com.borrowbuddy.app.model.StudentProfileRepository.getCurrentUserItems()
        val combinedItems = mutableListOf<CampusItem>()
        combinedItems.addAll(userItems)
        val remainingDefaults = defaultItems.filter { defaultItem ->
            userItems.none { it.id == defaultItem.id || (it.title == defaultItem.title && it.ownerName == defaultItem.ownerName) }
        }
        combinedItems.addAll(remainingDefaults)

        allExploreItems.clear()
        allExploreItems.addAll(combinedItems)

        // Populate popular/trending items (items with rating 5.0 or 4.9 & high completed lends)
        trendingItems.clear()
        trendingItems.addAll(combinedItems.filter { it.rating.contains("5.0") || it.rating.contains("4.9") }.take(5))
        trendingAdapter.notifyDataSetChanged()

        syncQuickChipsUI()
        updateCategoryTilesUI()
        applyFiltersAndSort()

        // Fetch from Firestore (kept non-blocking so mock data always functions cleanly)
        firestore.collection("items")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                if (_binding != null && !snapshot.isEmpty) {
                    val remoteItems = mutableListOf<CampusItem>()
                    for (doc in snapshot.documents) {
                        val item = CampusItem(
                            id = doc.id,
                            title = doc.getString("title").orEmpty(),
                            description = doc.getString("description").orEmpty(),
                            category = doc.getString("category") ?: "TECH",
                            priceLabel = doc.getString("priceLabel") ?: "FREE",
                            isFree = doc.getBoolean("isFree") ?: true,
                            pickupSpot = doc.getString("pickupSpot") ?: "Campus Library",
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
                        allExploreItems.clear()
                        allExploreItems.addAll(remoteItems)
                        allExploreItems.addAll(defaultItems)
                        applyFiltersAndSort()
                    }
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
