package com.borrowbuddy.app.model

import com.borrowbuddy.app.R

object ExchangeRepository {

    private val listeners = mutableListOf<() -> Unit>()

    // Unseen Activity State Tracking
    private val unseenCategories = mutableSetOf<String>(
        "LEND_INCOMING",   // New incoming request waiting for review
        "BORROW_ACTIVE"    // New exchange update (handover pending)
    )

    fun hasUnseenActivity(tab: ExchangeType): Boolean {
        return when (tab) {
            ExchangeType.BORROW -> unseenCategories.any { it.startsWith("BORROW_") }
            ExchangeType.LEND -> unseenCategories.any { it.startsWith("LEND_") }
        }
    }

    fun isCategoryUnseen(categoryKey: String): Boolean {
        return unseenCategories.contains(categoryKey)
    }

    fun markCategorySeen(categoryKey: String) {
        if (unseenCategories.remove(categoryKey)) {
            notifyChanges()
        }
    }

    fun addUnseenActivity(categoryKey: String) {
        if (unseenCategories.add(categoryKey)) {
            notifyChanges()
        }
    }

    /**
     * Clear dynamic user-created exchanges, reset modified statuses, and restore pristine demo exchanges.
     * Prevents Account A's requests and status updates from leaking into Account B.
     */
    fun clearSession() {
        exchangeList.clear()
        exchangeList.addAll(createInitialSeedExchanges())
        unseenCategories.clear()
        unseenCategories.add("LEND_INCOMING")
        unseenCategories.add("BORROW_ACTIVE")
        notifyChanges()
    }

    private fun createInitialSeedExchanges(): List<ExchangeItem> = listOf(
        // ==================== BORROW ITEMS ====================
        // 1. Pending Request
        ExchangeItem(
            id = "ex_b_pending_1",
            itemId = "c1",
            exchangeCode = "#EX-9201",
            type = ExchangeType.BORROW,
            title = "Casio fx-991ES Plus Scientific Calculator",
            category = "TECH",
            drawableRes = R.drawable.item_calculator,
            ownerName = "Rahul Dave",
            ownerDept = "IMSc.IT Sem 4",
            ownerUid = "mock_rahul",
            borrowerName = "Harsh Mistry",
            borrowerDept = "IMSc.IT Sem 4",
            borrowerUid = "mock_user_harsh",
            pickupSpot = "Main Library • 2m",
            duration = "1 Day",
            priceLabel = "FREE",
            isFree = true,
            securityDeposit = "None",
            requestDate = "Today, 10:30 AM",
            expectedReturnDate = "Tomorrow, 10:30 AM",
            borrowerNote = "Need for Engineering Math examination today",
            status = ExchangeStatus.AWAITING_RESPONSE,
            currentStepIndex = 1,
            pin = "3821"
        ),

        // 2. Active Borrowing: Handover Pending
        ExchangeItem(
            id = "ex_b_active_1",
            itemId = "c3",
            exchangeCode = "#EX-8419",
            type = ExchangeType.BORROW,
            title = "DBMS & OS Reference Notes",
            category = "BOOKS",
            drawableRes = R.drawable.item_notes,
            ownerName = "Karan Patel",
            ownerDept = "IMSc.IT Sem 6",
            ownerUid = "mock_karan",
            borrowerName = "Harsh Mistry",
            borrowerDept = "IMSc.IT Sem 4",
            borrowerUid = "mock_user_harsh",
            pickupSpot = "IT Block Entrance • 2m",
            duration = "2 Days",
            priceLabel = "FREE",
            isFree = true,
            securityDeposit = "None",
            requestDate = "Yesterday, 4:15 PM",
            handoverDate = "Today, 12:00 PM",
            expectedReturnDate = "Today, 5:00 PM",
            borrowerNote = "Preparing for lab viva",
            status = ExchangeStatus.HANDOVER_PENDING,
            currentStepIndex = 3,
            pin = "4892"
        ),

        // 3. Active Borrowing: Currently Borrowed (In possession)
        ExchangeItem(
            id = "ex_b_active_2",
            itemId = "c2",
            exchangeCode = "#EX-7732",
            type = ExchangeType.BORROW,
            title = "USB-C Multiport Adapter (HDMI + USB 3.0)",
            category = "TECH",
            drawableRes = R.drawable.item_dongle,
            ownerName = "Sneha Joshi",
            ownerDept = "IMSc.IT Sem 4",
            ownerUid = "mock_sneha",
            borrowerName = "Harsh Mistry",
            borrowerDept = "IMSc.IT Sem 4",
            borrowerUid = "mock_user_harsh",
            pickupSpot = "Reading Hall B • 4m",
            duration = "3 Days",
            priceLabel = "₹15/day",
            isFree = false,
            securityDeposit = "₹50 (Refundable)",
            requestDate = "2 days ago",
            handoverDate = "2 days ago",
            expectedReturnDate = "Tomorrow, 6:00 PM",
            borrowerNote = "Needed for classroom presentation",
            status = ExchangeStatus.BORROWED,
            currentStepIndex = 4,
            pin = "7154"
        ),

        // 4. Borrow History: Completed
        ExchangeItem(
            id = "ex_b_hist_1",
            itemId = "c4",
            exchangeCode = "#EX-6120",
            type = ExchangeType.BORROW,
            title = "Arduino Uno Starter Kit + Sensors",
            category = "LAB",
            drawableRes = R.drawable.item_arduino,
            ownerName = "Amit Shah",
            ownerDept = "IMSc.IT Sem 6",
            ownerUid = "mock_amit",
            borrowerName = "Harsh Mistry",
            borrowerDept = "IMSc.IT Sem 4",
            borrowerUid = "mock_user_harsh",
            pickupSpot = "Electronics Lab • 5m",
            duration = "3 Days",
            priceLabel = "FREE",
            isFree = true,
            securityDeposit = "None",
            requestDate = "18 Nov 2026",
            handoverDate = "18 Nov 2026",
            expectedReturnDate = "21 Nov 2026",
            borrowerNote = "IoT course mini project",
            status = ExchangeStatus.COMPLETED,
            currentStepIndex = 7,
            pin = "9041"
        ),

        // 5. Borrow History: Declined
        ExchangeItem(
            id = "ex_b_hist_2",
            itemId = "c_mock_clicker",
            exchangeCode = "#EX-5541",
            type = ExchangeType.BORROW,
            title = "Logitech Wireless Presenter Clicker",
            category = "TECH",
            drawableRes = R.drawable.item_clicker,
            ownerName = "Pooja Mehta",
            ownerDept = "IMBA Sem 3",
            ownerUid = "mock_pooja",
            borrowerName = "Harsh Mistry",
            borrowerDept = "IMSc.IT Sem 4",
            borrowerUid = "mock_user_harsh",
            pickupSpot = "Management Block • 6m",
            duration = "1 Day",
            priceLabel = "₹20/day",
            isFree = false,
            securityDeposit = "None",
            requestDate = "15 Nov 2026",
            expectedReturnDate = "16 Nov 2026",
            borrowerNote = "Seminar demo",
            status = ExchangeStatus.DECLINED,
            currentStepIndex = 1,
            pin = "1124"
        ),

        // ==================== LEND ITEMS ====================
        // 1. Incoming Request: Awaiting your review
        ExchangeItem(
            id = "ex_l_in_1",
            itemId = "c9",
            exchangeCode = "#EX-9914",
            type = ExchangeType.LEND,
            title = "Yonex Carbon Badminton Racket Pair",
            category = "SPORTS",
            drawableRes = R.drawable.item_badminton,
            ownerName = "Harsh Mistry",
            ownerDept = "IMSc.IT Sem 4",
            ownerUid = "mock_user_harsh",
            borrowerName = "Meera Trivedi",
            borrowerDept = "IMSc.IT Sem 2",
            borrowerUid = "mock_meera",
            pickupSpot = "Sports Complex • 3m",
            duration = "2 Hours",
            priceLabel = "FREE",
            isFree = true,
            securityDeposit = "None",
            requestDate = "Today, 11:15 AM",
            expectedReturnDate = "Today, 2:00 PM",
            borrowerNote = "Friendly doubles match with batchmates at court 2",
            status = ExchangeStatus.AWAITING_RESPONSE,
            currentStepIndex = 1,
            pin = "5512"
        ),

        // 2. Incoming Request: Awaiting your review (2nd)
        ExchangeItem(
            id = "ex_l_in_2",
            itemId = "c5",
            exchangeCode = "#EX-9831",
            type = ExchangeType.LEND,
            title = "White Lab Coat (Size M) Cleaned",
            category = "LAB",
            drawableRes = R.drawable.item_lab_coat,
            ownerName = "Harsh Mistry",
            ownerDept = "IMSc.IT Sem 4",
            ownerUid = "mock_user_harsh",
            borrowerName = "Devansh Parikh",
            borrowerDept = "IMSc.IT Sem 1",
            borrowerUid = "mock_devansh",
            pickupSpot = "Chemistry Lab 3 • 4m",
            duration = "1 Day",
            priceLabel = "₹10/day",
            isFree = false,
            securityDeposit = "None",
            requestDate = "Today, 9:45 AM",
            expectedReturnDate = "Tomorrow, 10:00 AM",
            borrowerNote = "Forgot mine at hostel, practical exam starts at 11 AM",
            status = ExchangeStatus.AWAITING_RESPONSE,
            currentStepIndex = 1,
            pin = "6238"
        ),

        // 3. Currently Lent: Handover Pending
        ExchangeItem(
            id = "ex_l_act_1",
            itemId = "c6",
            exchangeCode = "#EX-8902",
            type = ExchangeType.LEND,
            title = "Camera Tripod with Mobile Mount",
            category = "TECH",
            drawableRes = R.drawable.item_tripod,
            ownerName = "Harsh Mistry",
            ownerDept = "IMSc.IT Sem 4",
            ownerUid = "mock_user_harsh",
            borrowerName = "Rohan Vora",
            borrowerDept = "IMBA Sem 4",
            borrowerUid = "mock_rohan",
            pickupSpot = "Campus Canteen • 3m",
            duration = "1 Day",
            priceLabel = "₹25/day",
            isFree = false,
            securityDeposit = "₹100 (Refundable)",
            requestDate = "Yesterday, 3:30 PM",
            handoverDate = "Today, 1:00 PM",
            expectedReturnDate = "Tomorrow, 1:00 PM",
            borrowerNote = "Recording management fest promotional teaser",
            status = ExchangeStatus.HANDOVER_PENDING,
            currentStepIndex = 3,
            pin = "8831"
        ),

        // 4. Currently Lent: Return Pending (Borrower returned, needs confirmation)
        ExchangeItem(
            id = "ex_l_act_2",
            itemId = "c7",
            exchangeCode = "#EX-8411",
            type = ExchangeType.LEND,
            title = "HDMI to VGA Converter Cable",
            category = "TECH",
            drawableRes = R.drawable.item_cables,
            ownerName = "Harsh Mistry",
            ownerDept = "IMSc.IT Sem 4",
            ownerUid = "mock_user_harsh",
            borrowerName = "Neha Shah",
            borrowerDept = "IMBA Sem 2",
            borrowerUid = "mock_neha",
            pickupSpot = "Auditorium Lobby • 4m",
            duration = "4 Hours",
            priceLabel = "FREE",
            isFree = true,
            securityDeposit = "None",
            requestDate = "Today, 8:00 AM",
            handoverDate = "Today, 8:30 AM",
            expectedReturnDate = "Today, 12:30 PM",
            borrowerNote = "Returned at IT desk counter. Ready for handover check.",
            status = ExchangeStatus.RETURN_PENDING,
            currentStepIndex = 5,
            pin = "2940"
        ),

        // 5. Lending History: Completed
        ExchangeItem(
            id = "ex_l_hist_1",
            itemId = "c8",
            exchangeCode = "#EX-6819",
            type = ExchangeType.LEND,
            title = "Database System Concepts (Korth 7th Ed)",
            category = "BOOKS",
            drawableRes = R.drawable.item_dbms_book,
            ownerName = "Harsh Mistry",
            ownerDept = "IMSc.IT Sem 4",
            ownerUid = "mock_user_harsh",
            borrowerName = "Ananya Desai",
            borrowerDept = "IMSc.IT Sem 4",
            borrowerUid = "mock_ananya",
            pickupSpot = "Main Library • 2m",
            duration = "4 Days",
            priceLabel = "FREE",
            isFree = true,
            securityDeposit = "None",
            requestDate = "14 Nov 2026",
            handoverDate = "14 Nov 2026",
            expectedReturnDate = "18 Nov 2026",
            borrowerNote = "Studying normalization chapter",
            status = ExchangeStatus.COMPLETED,
            currentStepIndex = 7,
            pin = "4419"
        ),

        // 6. Lending History: Cancelled
        ExchangeItem(
            id = "ex_l_hist_2",
            itemId = "c_user_1",
            exchangeCode = "#EX-5901",
            type = ExchangeType.LEND,
            title = "Presentation Clicker with Red Laser",
            category = "TECH",
            drawableRes = R.drawable.item_clicker,
            ownerName = "Harsh Mistry",
            ownerDept = "IMSc.IT Sem 4",
            ownerUid = "mock_user_harsh",
            borrowerName = "Jay Patel",
            borrowerDept = "IMSc.IT Sem 4",
            borrowerUid = "mock_jay",
            pickupSpot = "IT Block Entrance • 2m",
            duration = "1 Day",
            priceLabel = "FREE",
            isFree = true,
            securityDeposit = "None",
            requestDate = "12 Nov 2026",
            expectedReturnDate = "13 Nov 2026",
            borrowerNote = "Presentation got rescheduled",
            status = ExchangeStatus.CANCELLED,
            currentStepIndex = 1,
            pin = "3310"
        )
    )

    private val exchangeList = createInitialSeedExchanges().toMutableList()

    /**
     * Determine whether an item is available or currently committed to an active exchange.
     * An item in HANDOVER_PENDING, BORROWED, RETURN_PENDING, or ACCEPTED is unavailable.
     * Completing or cancelling the exchange restores availability.
     */
    fun isItemAvailable(itemId: String): Boolean {
        if (itemId.isBlank()) return true
        val activeStatuses = setOf(
            ExchangeStatus.ACCEPTED,
            ExchangeStatus.HANDOVER_PENDING,
            ExchangeStatus.BORROWED,
            ExchangeStatus.RETURN_PENDING,
            ExchangeStatus.OVERDUE
        )
        return exchangeList.none { it.itemId == itemId && it.status in activeStatuses }
    }

    fun registerListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanges() {
        listeners.forEach { it.invoke() }
    }

    fun getAll(): List<ExchangeItem> = exchangeList.toList()

    fun getBorrowItems(): List<ExchangeItem> =
        exchangeList.filter { it.type == ExchangeType.BORROW }

    fun getLendItems(): List<ExchangeItem> =
        exchangeList.filter { it.type == ExchangeType.LEND }

    fun findById(id: String): ExchangeItem? =
        exchangeList.find { it.id == id }

    fun addBorrowRequest(
        item: CampusItem,
        returnTime: String,
        borrowerNote: String,
        pin: String
    ): ExchangeItem {
        val currentProfile = StudentProfileRepository.getCurrentUserProfile()
        val currentAuth = try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        } catch (_: Exception) {
            null
        }
        val borrowerUid = currentAuth?.uid ?: currentProfile.id
        val borrowerName = currentAuth?.displayName?.ifBlank { currentProfile.fullName } ?: currentProfile.fullName

        val newExchange = ExchangeItem(
            id = "ex_b_" + System.currentTimeMillis(),
            itemId = item.id,
            exchangeCode = "#EX-" + (1000 + kotlin.random.Random.nextInt(9000)),
            type = ExchangeType.BORROW,
            title = item.title,
            category = item.category,
            drawableRes = item.drawableRes,
            imageUri = item.imageUri,
            ownerName = item.ownerName,
            ownerDept = item.department,
            ownerUid = item.ownerUid,
            borrowerName = borrowerName,
            borrowerDept = currentProfile.department,
            borrowerUid = borrowerUid,
            pickupSpot = item.pickupSpot,
            duration = item.maxDuration.ifBlank { "1 Day" },
            priceLabel = item.priceLabel,
            isFree = item.isFree,
            securityDeposit = item.securityDeposit,
            requestDate = "Just now",
            expectedReturnDate = returnTime,
            borrowerNote = borrowerNote,
            status = ExchangeStatus.AWAITING_RESPONSE,
            currentStepIndex = 1,
            pin = pin
        )
        exchangeList.add(0, newExchange)
        addUnseenActivity("BORROW_PENDING")
        notifyChanges()
        return newExchange
    }

    // State Transitions
    fun acceptRequest(id: String) {
        val item = findById(id) ?: return
        item.status = ExchangeStatus.HANDOVER_PENDING
        item.currentStepIndex = 3
        notifyChanges()
    }

    fun declineRequest(id: String) {
        val item = findById(id) ?: return
        item.status = ExchangeStatus.DECLINED
        item.currentStepIndex = 1
        notifyChanges()
    }

    fun cancelRequest(id: String) {
        val item = findById(id) ?: return
        item.status = ExchangeStatus.CANCELLED
        item.currentStepIndex = 1
        notifyChanges()
    }

    fun confirmHandover(id: String) {
        val item = findById(id) ?: return
        item.status = ExchangeStatus.BORROWED
        item.currentStepIndex = 4
        notifyChanges()
    }

    fun initiateReturn(id: String) {
        val item = findById(id) ?: return
        item.status = ExchangeStatus.RETURN_PENDING
        item.currentStepIndex = 5
        notifyChanges()
    }

    fun confirmReturn(id: String) {
        val item = findById(id) ?: return
        item.status = ExchangeStatus.COMPLETED
        item.currentStepIndex = 7
        notifyChanges()
    }
}
