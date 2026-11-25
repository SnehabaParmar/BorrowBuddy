package com.borrowbuddy.app.util

import com.borrowbuddy.app.R
import com.borrowbuddy.app.model.ExchangeItem
import com.borrowbuddy.app.model.ExchangeStatus
import com.borrowbuddy.app.model.ExchangeType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object ActivityHelper {

    fun getActivityDescription(exchange: ExchangeItem): String {
        return when (exchange.type) {
            ExchangeType.BORROW -> when (exchange.status) {
                ExchangeStatus.COMPLETED,
                ExchangeStatus.RETURNED -> "You returned this item."
                ExchangeStatus.BORROWED -> "You borrowed this item."
                ExchangeStatus.HANDOVER_PENDING,
                ExchangeStatus.ACCEPTED -> "Borrowing request accepted."
                ExchangeStatus.AWAITING_RESPONSE -> "You requested to borrow this item."
                ExchangeStatus.RETURN_PENDING -> "Return in progress."
                ExchangeStatus.DECLINED -> "Borrow request declined."
                ExchangeStatus.CANCELLED -> "Borrow request cancelled."
                ExchangeStatus.OVERDUE -> "Return overdue."
            }
            ExchangeType.LEND -> when (exchange.status) {
                ExchangeStatus.COMPLETED -> "You lent this item."
                ExchangeStatus.AWAITING_RESPONSE -> "You received a borrowing request."
                ExchangeStatus.BORROWED,
                ExchangeStatus.HANDOVER_PENDING,
                ExchangeStatus.ACCEPTED -> "You lent this item."
                ExchangeStatus.RETURN_PENDING,
                ExchangeStatus.RETURNED -> "Borrower returned this item."
                ExchangeStatus.CANCELLED -> "Lending request cancelled."
                ExchangeStatus.DECLINED -> "Lending request declined."
                ExchangeStatus.OVERDUE -> "Item return overdue."
            }
        }
    }

    fun getActivityDate(exchange: ExchangeItem): String {
        return when {
            exchange.handoverDate.isNotBlank() && exchange.status in listOf(
                ExchangeStatus.BORROWED,
                ExchangeStatus.COMPLETED,
                ExchangeStatus.RETURNED,
                ExchangeStatus.RETURN_PENDING
            ) -> exchange.handoverDate
            exchange.requestDate.isNotBlank() -> exchange.requestDate
            else -> ""
        }
    }

    fun parseActivityTimestamp(dateStr: String): Long {
        val trimmed = dateStr.trim()
        if (trimmed.isEmpty()) return 0L
        val now = Calendar.getInstance()

        // 1. "Today..."
        if (trimmed.startsWith("Today", ignoreCase = true)) {
            val timePart = trimmed.substringAfter("Today").replace(",", "").trim()
            val cal = Calendar.getInstance()
            try {
                val time = SimpleDateFormat("h:mm a", Locale.US).parse(timePart)
                if (time != null) {
                    val tCal = Calendar.getInstance().apply { this.time = time }
                    cal.set(Calendar.HOUR_OF_DAY, tCal.get(Calendar.HOUR_OF_DAY))
                    cal.set(Calendar.MINUTE, tCal.get(Calendar.MINUTE))
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    return cal.timeInMillis
                }
            } catch (_: Exception) {}
            return now.timeInMillis
        }

        // 2. "Yesterday..."
        if (trimmed.startsWith("Yesterday", ignoreCase = true)) {
            val timePart = trimmed.substringAfter("Yesterday").replace(",", "").trim()
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            try {
                val time = SimpleDateFormat("h:mm a", Locale.US).parse(timePart)
                if (time != null) {
                    val tCal = Calendar.getInstance().apply { this.time = time }
                    cal.set(Calendar.HOUR_OF_DAY, tCal.get(Calendar.HOUR_OF_DAY))
                    cal.set(Calendar.MINUTE, tCal.get(Calendar.MINUTE))
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    return cal.timeInMillis
                }
            } catch (_: Exception) {}
            return cal.timeInMillis
        }

        // 3. "X days ago"
        if (trimmed.contains("days ago", ignoreCase = true)) {
            val days = trimmed.split(" ").firstOrNull()?.toIntOrNull() ?: 2
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }
            return cal.timeInMillis
        }

        // 4. "dd MMM yyyy" (e.g. "18 Nov 2026")
        try {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale.US)
            val d = sdf.parse(trimmed)
            if (d != null) return d.time
        } catch (_: Exception) {}

        return 0L
    }

    fun getCategoryIcon(category: String): Int {
        return when (category.uppercase()) {
            "TECH" -> R.drawable.ic_cat_tech
            "BOOKS" -> R.drawable.ic_cat_books
            "LAB" -> R.drawable.ic_cat_lab
            "EVENT" -> R.drawable.ic_cat_event
            "STATIONERY" -> R.drawable.ic_cat_stationery
            "SPORTS" -> R.drawable.ic_cat_sports
            "BUSINESS" -> R.drawable.ic_cat_business
            "CAMPUS" -> R.drawable.ic_cat_campus
            else -> R.drawable.ic_cat_tech
        }
    }
}
