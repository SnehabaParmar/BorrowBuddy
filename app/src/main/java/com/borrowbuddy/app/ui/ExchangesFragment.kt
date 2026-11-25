package com.borrowbuddy.app.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.borrowbuddy.app.R
import com.borrowbuddy.app.databinding.FragmentExchangesBinding
import com.borrowbuddy.app.databinding.ItemExchangeCardBinding
import com.borrowbuddy.app.databinding.ItemExchangeEmptyStateBinding
import com.borrowbuddy.app.model.CampusItem
import com.borrowbuddy.app.model.ExchangeItem
import com.borrowbuddy.app.model.ExchangeRepository
import com.borrowbuddy.app.model.ExchangeStatus
import com.borrowbuddy.app.model.ExchangeType

class ExchangesFragment : Fragment() {

    companion object {
        private var savedTab: ExchangeType = ExchangeType.BORROW
        private var savedBorrowCategory: String = "PENDING"   // "PENDING", "ACTIVE", "HISTORY"
        private var savedLendCategory: String = "INCOMING"    // "INCOMING", "ACTIVE", "HISTORY"

        fun navigateTo(tab: ExchangeType, category: String) {
            savedTab = tab
            if (tab == ExchangeType.BORROW) {
                savedBorrowCategory = category
            } else {
                savedLendCategory = category
            }
        }
    }

    private var _binding: FragmentExchangesBinding? = null
    private val binding get() = _binding!!

    private val repoListener: () -> Unit = {
        activity?.runOnUiThread {
            if (isAdded && _binding != null) {
                populateAll()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExchangesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabs()
        setupCategorySelectors()
        ExchangeRepository.registerListener(repoListener)

        val initialCategory = "${savedTab.name}_${if (savedTab == ExchangeType.BORROW) savedBorrowCategory else savedLendCategory}"
        ExchangeRepository.markCategorySeen(initialCategory)

        switchTab(savedTab)
        populateAll()
    }

    private fun setupTabs() {
        binding.tabBorrow.setOnClickListener {
            switchTab(ExchangeType.BORROW)
        }

        binding.tabLend.setOnClickListener {
            switchTab(ExchangeType.LEND)
        }
    }

    private fun setupCategorySelectors() {
        // BORROW Categories
        binding.catBorrowPending.setOnClickListener {
            switchCategory("PENDING")
        }
        binding.catBorrowActive.setOnClickListener {
            switchCategory("ACTIVE")
        }
        binding.catBorrowHistory.setOnClickListener {
            switchCategory("HISTORY")
        }

        // LEND Categories
        binding.catLendIncoming.setOnClickListener {
            switchCategory("INCOMING")
        }
        binding.catLendActive.setOnClickListener {
            switchCategory("ACTIVE")
        }
        binding.catLendHistory.setOnClickListener {
            switchCategory("HISTORY")
        }
    }

    private fun switchTab(tab: ExchangeType) {
        savedTab = tab
        val context = requireContext()

        if (tab == ExchangeType.BORROW) {
            binding.tabBorrow.setBackgroundResource(R.drawable.bg_segmented_tab_selected)
            binding.tvTabBorrowLabel.setTextColor(ContextCompat.getColor(context, R.color.white))

            binding.tabLend.setBackgroundResource(android.R.color.transparent)
            binding.tvTabLendLabel.setTextColor(ContextCompat.getColor(context, R.color.brand_slate))

            binding.layoutBorrowCategorySelector.visibility = View.VISIBLE
            binding.layoutLendCategorySelector.visibility = View.GONE
        } else {
            binding.tabLend.setBackgroundResource(R.drawable.bg_segmented_tab_selected)
            binding.tvTabLendLabel.setTextColor(ContextCompat.getColor(context, R.color.white))

            binding.tabBorrow.setBackgroundResource(android.R.color.transparent)
            binding.tvTabBorrowLabel.setTextColor(ContextCompat.getColor(context, R.color.brand_slate))

            binding.layoutBorrowCategorySelector.visibility = View.GONE
            binding.layoutLendCategorySelector.visibility = View.VISIBLE
        }

        val viewedCategory = if (tab == ExchangeType.BORROW) savedBorrowCategory else savedLendCategory
        ExchangeRepository.markCategorySeen("${tab.name}_$viewedCategory")

        updateCategorySelectorUI()
        updateUnderlineIndicator(animate = false)
        populateActiveCards()
    }

    private fun switchCategory(category: String) {
        if (savedTab == ExchangeType.BORROW) {
            savedBorrowCategory = category
        } else {
            savedLendCategory = category
        }

        ExchangeRepository.markCategorySeen("${savedTab.name}_$category")

        updateCategorySelectorUI()
        updateUnderlineIndicator(animate = true)
        populateActiveCards()
        binding.scrollContainer.smoothScrollTo(0, 0)
    }

    private fun updateCategorySelectorUI() {
        if (_binding == null) return
        val context = requireContext()

        if (savedTab == ExchangeType.BORROW) {
            setCategoryTabStyle(binding.tvCatBorrowPendingLabel, savedBorrowCategory == "PENDING", context)
            setCategoryTabStyle(binding.tvCatBorrowActiveLabel, savedBorrowCategory == "ACTIVE", context)
            setCategoryTabStyle(binding.tvCatBorrowHistoryLabel, savedBorrowCategory == "HISTORY", context)
        } else {
            setCategoryTabStyle(binding.tvCatLendIncomingLabel, savedLendCategory == "INCOMING", context)
            setCategoryTabStyle(binding.tvCatLendActiveLabel, savedLendCategory == "ACTIVE", context)
            setCategoryTabStyle(binding.tvCatLendHistoryLabel, savedLendCategory == "HISTORY", context)
        }

        // Unseen Activity Indicator Dots
        binding.dotBorrowUnseen.visibility = if (ExchangeRepository.hasUnseenActivity(ExchangeType.BORROW)) View.VISIBLE else View.GONE
        binding.dotLendUnseen.visibility = if (ExchangeRepository.hasUnseenActivity(ExchangeType.LEND)) View.VISIBLE else View.GONE

        binding.dotCatBorrowPending.visibility = if (ExchangeRepository.isCategoryUnseen("BORROW_PENDING")) View.VISIBLE else View.GONE
        binding.dotCatBorrowActive.visibility = if (ExchangeRepository.isCategoryUnseen("BORROW_ACTIVE")) View.VISIBLE else View.GONE
        binding.dotCatBorrowHistory.visibility = if (ExchangeRepository.isCategoryUnseen("BORROW_HISTORY")) View.VISIBLE else View.GONE

        binding.dotCatLendIncoming.visibility = if (ExchangeRepository.isCategoryUnseen("LEND_INCOMING")) View.VISIBLE else View.GONE
        binding.dotCatLendActive.visibility = if (ExchangeRepository.isCategoryUnseen("LEND_ACTIVE")) View.VISIBLE else View.GONE
        binding.dotCatLendHistory.visibility = if (ExchangeRepository.isCategoryUnseen("LEND_HISTORY")) View.VISIBLE else View.GONE
    }

    private fun updateUnderlineIndicator(animate: Boolean) {
        if (_binding == null) return

        val isBorrow = savedTab == ExchangeType.BORROW
        val container = if (isBorrow) binding.layoutBorrowCategorySelector else binding.layoutLendCategorySelector
        val indicator = if (isBorrow) binding.indicatorBorrow else binding.indicatorLend
        val category = if (isBorrow) savedBorrowCategory else savedLendCategory

        val index = when (category) {
            "PENDING", "INCOMING" -> 0
            "ACTIVE" -> 1
            else -> 2
        }

        container.post {
            if (_binding == null) return@post
            val totalWidth = container.width
            if (totalWidth <= 0) return@post

            val tabWidth = totalWidth / 3
            if (indicator.layoutParams.width != tabWidth) {
                indicator.layoutParams.width = tabWidth
                indicator.requestLayout()
            }

            val targetX = index * tabWidth.toFloat()
            if (animate) {
                indicator.animate()
                    .translationX(targetX)
                    .setDuration(200)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            } else {
                indicator.translationX = targetX
            }
        }
    }

    private fun setCategoryTabStyle(label: TextView, isSelected: Boolean, context: android.content.Context) {
        if (isSelected) {
            label.setTextColor(ContextCompat.getColor(context, R.color.brand_blue))
        } else {
            label.setTextColor(ContextCompat.getColor(context, R.color.brand_slate))
        }
    }

    private fun populateAll() {
        if (_binding == null) return

        val allBorrow = ExchangeRepository.getBorrowItems()
        val allLend = ExchangeRepository.getLendItems()

        // Main Tab Labels (without numerical counts)
        binding.tvTabBorrowLabel.text = "BORROW"
        binding.tvTabLendLabel.text = "LEND"

        // Filter BORROW Categories
        val pendingBorrow = allBorrow.filter { it.status == ExchangeStatus.AWAITING_RESPONSE }
        val activeBorrow = allBorrow.filter {
            it.status in listOf(
                ExchangeStatus.ACCEPTED,
                ExchangeStatus.HANDOVER_PENDING,
                ExchangeStatus.BORROWED,
                ExchangeStatus.RETURN_PENDING,
                ExchangeStatus.OVERDUE
            )
        }
        val historyBorrow = allBorrow.filter {
            it.status in listOf(
                ExchangeStatus.COMPLETED,
                ExchangeStatus.DECLINED,
                ExchangeStatus.CANCELLED
            )
        }

        binding.tvCatBorrowPendingLabel.text = "Pending (${pendingBorrow.size})"
        binding.tvCatBorrowActiveLabel.text = "Active (${activeBorrow.size})"
        binding.tvCatBorrowHistoryLabel.text = "History (${historyBorrow.size})"

        // Filter LEND Categories
        val incomingLend = allLend.filter { it.status == ExchangeStatus.AWAITING_RESPONSE }
        val activeLend = allLend.filter {
            it.status in listOf(
                ExchangeStatus.ACCEPTED,
                ExchangeStatus.HANDOVER_PENDING,
                ExchangeStatus.BORROWED,
                ExchangeStatus.RETURN_PENDING,
                ExchangeStatus.OVERDUE
            )
        }
        val historyLend = allLend.filter {
            it.status in listOf(
                ExchangeStatus.COMPLETED,
                ExchangeStatus.DECLINED,
                ExchangeStatus.CANCELLED
            )
        }

        binding.tvCatLendIncomingLabel.text = "Incoming (${incomingLend.size})"
        binding.tvCatLendActiveLabel.text = "Active (${activeLend.size})"
        binding.tvCatLendHistoryLabel.text = "History (${historyLend.size})"

        updateCategorySelectorUI()
        updateUnderlineIndicator(animate = false)
        populateActiveCards()
    }

    private fun populateActiveCards() {
        if (_binding == null) return

        val container = binding.layoutExchangeCardsList
        container.removeAllViews()

        val allBorrow = ExchangeRepository.getBorrowItems()
        val allLend = ExchangeRepository.getLendItems()

        val (heading, items, emptyTitle, emptySubtitle) = if (savedTab == ExchangeType.BORROW) {
            when (savedBorrowCategory) {
                "PENDING" -> {
                    val list = allBorrow.filter { it.status == ExchangeStatus.AWAITING_RESPONSE }
                    Quadruple(
                        "Pending Requests",
                        list,
                        "No Pending Requests",
                        "You haven't requested any items recently."
                    )
                }
                "ACTIVE" -> {
                    val list = allBorrow.filter {
                        it.status in listOf(
                            ExchangeStatus.ACCEPTED,
                            ExchangeStatus.HANDOVER_PENDING,
                            ExchangeStatus.BORROWED,
                            ExchangeStatus.RETURN_PENDING,
                            ExchangeStatus.OVERDUE
                        )
                    }
                    Quadruple(
                        "Active Borrowings",
                        list,
                        "No Active Borrowings",
                        "Items you borrow and currently hold will appear here."
                    )
                }
                else -> {
                    val list = allBorrow.filter {
                        it.status in listOf(
                            ExchangeStatus.COMPLETED,
                            ExchangeStatus.DECLINED,
                            ExchangeStatus.CANCELLED
                        )
                    }
                    Quadruple(
                        "Borrow History",
                        list,
                        "No Borrow History",
                        "Past completed or cancelled borrowings will be logged here."
                    )
                }
            }
        } else {
            when (savedLendCategory) {
                "INCOMING" -> {
                    val list = allLend.filter { it.status == ExchangeStatus.AWAITING_RESPONSE }
                    Quadruple(
                        "Incoming Requests",
                        list,
                        "No Incoming Requests",
                        "When other students request your items, they will appear here."
                    )
                }
                "ACTIVE" -> {
                    val list = allLend.filter {
                        it.status in listOf(
                            ExchangeStatus.ACCEPTED,
                            ExchangeStatus.HANDOVER_PENDING,
                            ExchangeStatus.BORROWED,
                            ExchangeStatus.RETURN_PENDING,
                            ExchangeStatus.OVERDUE
                        )
                    }
                    Quadruple(
                        "Currently Lent",
                        list,
                        "No Currently Lent Items",
                        "Items you have shared that are currently with peers will appear here."
                    )
                }
                else -> {
                    val list = allLend.filter {
                        it.status in listOf(
                            ExchangeStatus.COMPLETED,
                            ExchangeStatus.DECLINED,
                            ExchangeStatus.CANCELLED
                        )
                    }
                    Quadruple(
                        "Lending History",
                        list,
                        "No Lending History",
                        "Past lent items and returned exchanges will be archived here."
                    )
                }
            }
        }

        binding.tvCategoryHeading.text = heading

        if (items.isEmpty()) {
            container.addView(createEmptyView(emptyTitle, emptySubtitle))
        } else {
            items.forEach { item ->
                container.addView(createCardView(item))
            }
        }
    }

    private fun createEmptyView(title: String, subtitle: String): View {
        val emptyBinding = ItemExchangeEmptyStateBinding.inflate(layoutInflater)
        emptyBinding.tvEmptyTitle.text = title
        emptyBinding.tvEmptySubtitle.text = subtitle
        return emptyBinding.root
    }

    private fun createCardView(item: ExchangeItem): View {
        val cardBinding = ItemExchangeCardBinding.inflate(layoutInflater)

        // Status Badge & Header Info
        bindStatusBadge(cardBinding, item)
        cardBinding.tvDurationPrice.text = "${item.priceLabel} • ${item.duration}"
        cardBinding.tvItemTitle.text = item.title

        // Image
        if (item.drawableRes != 0) {
            cardBinding.ivItemImage.setImageResource(item.drawableRes)
        } else {
            cardBinding.ivItemImage.setImageResource(R.drawable.bg_image_fallback)
        }

        // Person Info
        val otherStudentName = if (item.type == ExchangeType.BORROW) item.ownerName else item.borrowerName
        val otherStudentUid = if (item.type == ExchangeType.BORROW) item.ownerUid else item.borrowerUid
        if (item.type == ExchangeType.BORROW) {
            cardBinding.tvPersonInfo.text = "Owner: ${item.ownerName} (${item.ownerDept})"
            cardBinding.ivPersonRole.setImageResource(R.drawable.ic_user)
        } else {
            cardBinding.tvPersonInfo.text = "Borrower: ${item.borrowerName} (${item.borrowerDept})"
            cardBinding.ivPersonRole.setImageResource(R.drawable.ic_user)
        }

        // Direct contextual navigation to Public Student Profile
        cardBinding.layoutPersonInfo.setOnClickListener {
            val uid = if (otherStudentUid.isNotBlank()) otherStudentUid else null
            com.borrowbuddy.app.StudentProfileActivity.start(requireContext(), uid, otherStudentName)
        }

        // Location
        cardBinding.tvPickupSpot.text = item.pickupSpot

        // Borrower Note
        if (item.borrowerNote.isNotEmpty()) {
            cardBinding.layoutBorrowerNote.visibility = View.VISIBLE
            cardBinding.tvBorrowerNote.text = "\"${item.borrowerNote}\""
        } else {
            cardBinding.layoutBorrowerNote.visibility = View.GONE
        }

        // Entire Card Click Opens Details
        cardBinding.root.setOnClickListener {
            openDetailSheet(item.id)
        }

        // Contextual Buttons Configuration
        setupCardActions(cardBinding, item)

        return cardBinding.root
    }

    private fun bindStatusBadge(cardBinding: ItemExchangeCardBinding, item: ExchangeItem) {
        val context = requireContext()
        when (item.status) {
            ExchangeStatus.AWAITING_RESPONSE -> {
                cardBinding.tvStatusBadge.text = if (item.type == ExchangeType.BORROW) "Awaiting Response" else "Action Required"
                cardBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_pending)
                cardBinding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_pending_text))
            }
            ExchangeStatus.ACCEPTED,
            ExchangeStatus.HANDOVER_PENDING -> {
                cardBinding.tvStatusBadge.text = "Handover Pending"
                cardBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_active)
                cardBinding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_active_text))
            }
            ExchangeStatus.BORROWED -> {
                cardBinding.tvStatusBadge.text = "Borrowed"
                cardBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_borrowed)
                cardBinding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_borrowed_text))
            }
            ExchangeStatus.RETURN_PENDING -> {
                cardBinding.tvStatusBadge.text = "Return Pending"
                cardBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_return)
                cardBinding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_return_text))
            }
            ExchangeStatus.COMPLETED -> {
                cardBinding.tvStatusBadge.text = "Completed"
                cardBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_completed)
                cardBinding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_completed_text))
            }
            ExchangeStatus.DECLINED -> {
                cardBinding.tvStatusBadge.text = "Declined"
                cardBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_declined)
                cardBinding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_declined_text))
            }
            ExchangeStatus.CANCELLED -> {
                cardBinding.tvStatusBadge.text = "Cancelled"
                cardBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_completed)
                cardBinding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_completed_text))
            }
            ExchangeStatus.OVERDUE -> {
                cardBinding.tvStatusBadge.text = "Overdue"
                cardBinding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_declined)
                cardBinding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_declined_text))
            }
            else -> {
                cardBinding.tvStatusBadge.text = item.status.displayName
            }
        }
    }

    private fun setupCardActions(cardBinding: ItemExchangeCardBinding, item: ExchangeItem) {
        val context = requireContext()

        if (item.type == ExchangeType.BORROW) {
            when (item.status) {
                ExchangeStatus.AWAITING_RESPONSE -> {
                    cardBinding.btnActionSecondary.text = "Cancel"
                    cardBinding.btnActionSecondary.setTextColor(ContextCompat.getColor(context, R.color.status_declined_text))
                    cardBinding.btnActionSecondary.strokeColor = ContextCompat.getColorStateList(context, R.color.status_declined_text)
                    cardBinding.btnActionSecondary.setOnClickListener {
                        ExchangeRepository.cancelRequest(item.id)
                        Toast.makeText(context, "Request cancelled", Toast.LENGTH_SHORT).show()
                    }

                    cardBinding.btnActionPrimary.text = "View Details"
                    cardBinding.btnActionPrimary.setBackgroundColor(ContextCompat.getColor(context, R.color.brand_navy))
                    cardBinding.btnActionPrimary.setOnClickListener {
                        openDetailSheet(item.id)
                    }
                }
                ExchangeStatus.ACCEPTED,
                ExchangeStatus.HANDOVER_PENDING -> {
                    cardBinding.btnActionSecondary.text = "View Details"
                    cardBinding.btnActionSecondary.setTextColor(ContextCompat.getColor(context, R.color.brand_navy))
                    cardBinding.btnActionSecondary.strokeColor = ContextCompat.getColorStateList(context, R.color.border_light)
                    cardBinding.btnActionSecondary.setOnClickListener {
                        openDetailSheet(item.id)
                    }

                    cardBinding.btnActionPrimary.text = "Show QR Pass"
                    cardBinding.btnActionPrimary.setBackgroundColor(ContextCompat.getColor(context, R.color.brand_blue))
                    cardBinding.btnActionPrimary.setOnClickListener {
                        openPassSheet(item)
                    }
                }
                ExchangeStatus.BORROWED -> {
                    cardBinding.btnActionSecondary.text = "View Details"
                    cardBinding.btnActionSecondary.setTextColor(ContextCompat.getColor(context, R.color.brand_navy))
                    cardBinding.btnActionSecondary.strokeColor = ContextCompat.getColorStateList(context, R.color.border_light)
                    cardBinding.btnActionSecondary.setOnClickListener {
                        openDetailSheet(item.id)
                    }

                    cardBinding.btnActionPrimary.text = "Initiate Return"
                    cardBinding.btnActionPrimary.setBackgroundColor(ContextCompat.getColor(context, R.color.brand_turquoise))
                    cardBinding.btnActionPrimary.setOnClickListener {
                        ExchangeRepository.initiateReturn(item.id)
                        Toast.makeText(context, "Return initiated! Meet owner to hand back item.", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    cardBinding.btnActionSecondary.visibility = View.GONE
                    cardBinding.btnActionPrimary.text = "View Details"
                    cardBinding.btnActionPrimary.setBackgroundColor(ContextCompat.getColor(context, R.color.brand_navy))
                    cardBinding.btnActionPrimary.setOnClickListener {
                        openDetailSheet(item.id)
                    }
                }
            }
        } else {
            // LEND Tab
            when (item.status) {
                ExchangeStatus.AWAITING_RESPONSE -> {
                    cardBinding.btnActionSecondary.text = "Decline"
                    cardBinding.btnActionSecondary.setTextColor(ContextCompat.getColor(context, R.color.status_declined_text))
                    cardBinding.btnActionSecondary.strokeColor = ContextCompat.getColorStateList(context, R.color.status_declined_text)
                    cardBinding.btnActionSecondary.setOnClickListener {
                        ExchangeRepository.declineRequest(item.id)
                        Toast.makeText(context, "Request declined", Toast.LENGTH_SHORT).show()
                    }

                    cardBinding.btnActionPrimary.text = "Accept"
                    cardBinding.btnActionPrimary.setBackgroundColor(ContextCompat.getColor(context, R.color.brand_blue))
                    cardBinding.btnActionPrimary.setOnClickListener {
                        ExchangeRepository.acceptRequest(item.id)
                        Toast.makeText(context, "Request accepted! Scheduled for handover.", Toast.LENGTH_SHORT).show()
                    }
                }
                ExchangeStatus.ACCEPTED,
                ExchangeStatus.HANDOVER_PENDING -> {
                    cardBinding.btnActionSecondary.text = "View Details"
                    cardBinding.btnActionSecondary.setTextColor(ContextCompat.getColor(context, R.color.brand_navy))
                    cardBinding.btnActionSecondary.strokeColor = ContextCompat.getColorStateList(context, R.color.border_light)
                    cardBinding.btnActionSecondary.setOnClickListener {
                        openDetailSheet(item.id)
                    }

                    cardBinding.btnActionPrimary.text = "Confirm Handover"
                    cardBinding.btnActionPrimary.setBackgroundColor(ContextCompat.getColor(context, R.color.brand_blue))
                    cardBinding.btnActionPrimary.setOnClickListener {
                        ExchangeRepository.confirmHandover(item.id)
                        Toast.makeText(context, "Handover confirmed! Lending active.", Toast.LENGTH_SHORT).show()
                    }
                }
                ExchangeStatus.RETURN_PENDING -> {
                    cardBinding.btnActionSecondary.text = "View Details"
                    cardBinding.btnActionSecondary.setTextColor(ContextCompat.getColor(context, R.color.brand_navy))
                    cardBinding.btnActionSecondary.strokeColor = ContextCompat.getColorStateList(context, R.color.border_light)
                    cardBinding.btnActionSecondary.setOnClickListener {
                        openDetailSheet(item.id)
                    }

                    cardBinding.btnActionPrimary.text = "Confirm Return"
                    cardBinding.btnActionPrimary.setBackgroundColor(ContextCompat.getColor(context, R.color.brand_turquoise))
                    cardBinding.btnActionPrimary.setOnClickListener {
                        ExchangeRepository.confirmReturn(item.id)
                        Toast.makeText(context, "Return confirmed! Exchange completed.", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {
                    cardBinding.btnActionSecondary.visibility = View.GONE
                    cardBinding.btnActionPrimary.text = "View Details"
                    cardBinding.btnActionPrimary.setBackgroundColor(ContextCompat.getColor(context, R.color.brand_navy))
                    cardBinding.btnActionPrimary.setOnClickListener {
                        openDetailSheet(item.id)
                    }
                }
            }
        }
    }

    private fun openDetailSheet(id: String) {
        val sheet = ExchangeDetailBottomSheetFragment(id)
        sheet.show(childFragmentManager, "ExchangeDetailSheet")
    }

    private fun openPassSheet(item: ExchangeItem) {
        val campusItem = CampusItem(
            id = item.id,
            title = item.title,
            pickupSpot = item.pickupSpot,
            ownerName = item.ownerName,
            department = item.ownerDept,
            isFree = item.isFree,
            priceLabel = item.priceLabel
        )
        val passSheet = ExchangePassBottomSheetFragment(
            item = campusItem,
            returnTime = if (item.expectedReturnDate.isNotEmpty()) item.expectedReturnDate else "Today, 5:00 PM",
            borrowerNote = item.borrowerNote
        )
        passSheet.show(childFragmentManager, "ExchangePassSheet")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        ExchangeRepository.unregisterListener(repoListener)
        _binding = null
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
}
