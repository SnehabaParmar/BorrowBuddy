package com.borrowbuddy.app.model

enum class NotificationType {
    BORROW_REQUEST_RECEIVED,
    REQUEST_ACCEPTED,
    REQUEST_DECLINED,
    EXCHANGE_UPDATE,
    RETURN_UPDATE,
    GLANCE_RESPONSE
}

data class NotificationItem(
    val id: String,
    val type: NotificationType,
    val title: String,
    val description: String,
    val dateDisplay: String,
    val timestamp: Long,
    val category: String,
    val isRead: Boolean,
    val targetTab: Int = 3, // 3 = Exchanges, 0 = Home
    val targetCategory: String? = null,
    val targetExchangeType: ExchangeType? = null
)
