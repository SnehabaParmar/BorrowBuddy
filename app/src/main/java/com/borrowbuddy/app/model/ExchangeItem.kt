package com.borrowbuddy.app.model

import com.borrowbuddy.app.R

enum class ExchangeType {
    BORROW,
    LEND
}

enum class ExchangeStatus(val displayName: String) {
    AWAITING_RESPONSE("Awaiting Response"),
    ACCEPTED("Accepted"),
    HANDOVER_PENDING("Handover Pending"),
    BORROWED("Borrowed"),
    RETURN_PENDING("Return Pending"),
    RETURNED("Returned"),
    COMPLETED("Completed"),
    DECLINED("Declined"),
    CANCELLED("Cancelled"),
    OVERDUE("Overdue")
}

data class ExchangeItem(
    val id: String,
    val itemId: String = "",
    val exchangeCode: String = "#EX-8421",
    val type: ExchangeType,
    val title: String,
    val category: String = "TECH",
    val drawableRes: Int = 0,
    val imageUri: String = "",
    val ownerName: String,
    val ownerDept: String = "IMSc.IT",
    val ownerUid: String = "",
    val borrowerName: String,
    val borrowerDept: String = "IMSc.IT",
    val borrowerUid: String = "",
    val pickupSpot: String,
    val duration: String,
    val priceLabel: String = "FREE",
    val isFree: Boolean = true,
    val securityDeposit: String = "None",
    val requestDate: String,
    val handoverDate: String = "",
    val expectedReturnDate: String = "",
    val borrowerNote: String = "",
    var status: ExchangeStatus,
    var currentStepIndex: Int = 1, // 1 to 7
    val pin: String = "4892"
)
