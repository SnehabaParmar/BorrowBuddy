package com.borrowbuddy.app.model

import android.content.Context
import android.content.SharedPreferences
import com.borrowbuddy.app.util.ActivityHelper

object NotificationRepository {

    private const val PREFS_NAME = "borrowbuddy_notifications_prefs"
    private const val KEY_READ_IDS = "read_notification_ids"
    private const val KEY_INITIALIZED = "has_initialized_notifications"

    private val listeners = mutableListOf<() -> Unit>()

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

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getReadIds(context: Context): MutableSet<String> {
        val prefs = getPrefs(context)
        val stored = prefs.getStringSet(KEY_READ_IDS, null)
        return stored?.toMutableSet() ?: mutableSetOf()
    }

    private fun saveReadIds(context: Context, ids: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_READ_IDS, ids).apply()
        notifyChanges()
    }

    /**
     * Clear user-specific notification preferences upon logout.
     */
    fun clearSession(context: Context) {
        getPrefs(context).edit().clear().apply()
        notifyChanges()
    }

    /**
     * Build notifications from current application activity records without duplicating entries.
     */
    fun getNotifications(context: Context): List<NotificationItem> {
        val prefs = getPrefs(context)
        val readIds = getReadIds(context)
        val isFirstRun = !prefs.getBoolean(KEY_INITIALIZED, false)

        val allExchanges = ExchangeRepository.getAll()
        val notifications = mutableListOf<NotificationItem>()

        for (exchange in allExchanges) {
            val dateStr = ActivityHelper.getActivityDate(exchange).ifBlank { exchange.requestDate }
            val timestamp = ActivityHelper.parseActivityTimestamp(dateStr)

            when (exchange.type) {
                ExchangeType.LEND -> {
                    when (exchange.status) {
                        ExchangeStatus.AWAITING_RESPONSE -> {
                            val id = "notif_lend_req_${exchange.id}"
                            notifications.add(
                                NotificationItem(
                                    id = id,
                                    type = NotificationType.BORROW_REQUEST_RECEIVED,
                                    title = "New Borrow Request",
                                    description = "${exchange.borrowerName} requested to borrow \"${exchange.title}\"",
                                    dateDisplay = dateStr,
                                    timestamp = timestamp,
                                    category = exchange.category,
                                    isRead = readIds.contains(id),
                                    targetTab = 3,
                                    targetCategory = "INCOMING",
                                    targetExchangeType = ExchangeType.LEND
                                )
                            )
                        }
                        ExchangeStatus.HANDOVER_PENDING -> {
                            val id = "notif_lend_handover_${exchange.id}"
                            notifications.add(
                                NotificationItem(
                                    id = id,
                                    type = NotificationType.EXCHANGE_UPDATE,
                                    title = "Handover Scheduled",
                                    description = "Meet ${exchange.borrowerName} at ${exchange.pickupSpot} to hand over \"${exchange.title}\"",
                                    dateDisplay = dateStr,
                                    timestamp = timestamp,
                                    category = exchange.category,
                                    isRead = readIds.contains(id),
                                    targetTab = 3,
                                    targetCategory = "ACTIVE",
                                    targetExchangeType = ExchangeType.LEND
                                )
                            )
                        }
                        ExchangeStatus.RETURN_PENDING -> {
                            val id = "notif_lend_ret_pending_${exchange.id}"
                            notifications.add(
                                NotificationItem(
                                    id = id,
                                    type = NotificationType.RETURN_UPDATE,
                                    title = "Return Verification Pending",
                                    description = "${exchange.borrowerName} returned \"${exchange.title}\". Please verify and complete.",
                                    dateDisplay = dateStr,
                                    timestamp = timestamp,
                                    category = exchange.category,
                                    isRead = readIds.contains(id),
                                    targetTab = 3,
                                    targetCategory = "ACTIVE",
                                    targetExchangeType = ExchangeType.LEND
                                )
                            )
                        }
                        ExchangeStatus.COMPLETED -> {
                            val id = "notif_lend_completed_${exchange.id}"
                            notifications.add(
                                NotificationItem(
                                    id = id,
                                    type = NotificationType.RETURN_UPDATE,
                                    title = "Lend Completed",
                                    description = "${exchange.borrowerName} completed borrow for \"${exchange.title}\". Rating updated!",
                                    dateDisplay = dateStr,
                                    timestamp = timestamp,
                                    category = exchange.category,
                                    isRead = readIds.contains(id),
                                    targetTab = 3,
                                    targetCategory = "HISTORY",
                                    targetExchangeType = ExchangeType.LEND
                                )
                            )
                        }
                        ExchangeStatus.CANCELLED -> {
                            val id = "notif_lend_cancelled_${exchange.id}"
                            notifications.add(
                                NotificationItem(
                                    id = id,
                                    type = NotificationType.EXCHANGE_UPDATE,
                                    title = "Lending Request Cancelled",
                                    description = "${exchange.borrowerName} cancelled request for \"${exchange.title}\"",
                                    dateDisplay = dateStr,
                                    timestamp = timestamp,
                                    category = exchange.category,
                                    isRead = readIds.contains(id),
                                    targetTab = 3,
                                    targetCategory = "HISTORY",
                                    targetExchangeType = ExchangeType.LEND
                                )
                            )
                        }
                        else -> {
                            // Other lend states
                        }
                    }
                }
                ExchangeType.BORROW -> {
                    when (exchange.status) {
                        ExchangeStatus.HANDOVER_PENDING,
                        ExchangeStatus.ACCEPTED -> {
                            val id = "notif_borrow_accepted_${exchange.id}"
                            notifications.add(
                                NotificationItem(
                                    id = id,
                                    type = NotificationType.REQUEST_ACCEPTED,
                                    title = "Borrow Request Accepted",
                                    description = "${exchange.ownerName} accepted your request for \"${exchange.title}\"",
                                    dateDisplay = dateStr,
                                    timestamp = timestamp,
                                    category = exchange.category,
                                    isRead = readIds.contains(id),
                                    targetTab = 3,
                                    targetCategory = "ACTIVE",
                                    targetExchangeType = ExchangeType.BORROW
                                )
                            )
                        }
                        ExchangeStatus.BORROWED -> {
                            val id = "notif_borrow_active_${exchange.id}"
                            notifications.add(
                                NotificationItem(
                                    id = id,
                                    type = NotificationType.EXCHANGE_UPDATE,
                                    title = "Item Collected",
                                    description = "You collected \"${exchange.title}\" from ${exchange.ownerName}. Due ${exchange.expectedReturnDate}",
                                    dateDisplay = dateStr,
                                    timestamp = timestamp,
                                    category = exchange.category,
                                    isRead = readIds.contains(id),
                                    targetTab = 3,
                                    targetCategory = "ACTIVE",
                                    targetExchangeType = ExchangeType.BORROW
                                )
                            )
                        }
                        ExchangeStatus.DECLINED -> {
                            val id = "notif_borrow_declined_${exchange.id}"
                            notifications.add(
                                NotificationItem(
                                    id = id,
                                    type = NotificationType.REQUEST_DECLINED,
                                    title = "Borrow Request Declined",
                                    description = "${exchange.ownerName} was unable to lend \"${exchange.title}\"",
                                    dateDisplay = dateStr,
                                    timestamp = timestamp,
                                    category = exchange.category,
                                    isRead = readIds.contains(id),
                                    targetTab = 3,
                                    targetCategory = "HISTORY",
                                    targetExchangeType = ExchangeType.BORROW
                                )
                            )
                        }
                        ExchangeStatus.COMPLETED,
                        ExchangeStatus.RETURNED -> {
                            val id = "notif_borrow_completed_${exchange.id}"
                            notifications.add(
                                NotificationItem(
                                    id = id,
                                    type = NotificationType.RETURN_UPDATE,
                                    title = "Item Returned Successfully",
                                    description = "You returned \"${exchange.title}\" to ${exchange.ownerName}",
                                    dateDisplay = dateStr,
                                    timestamp = timestamp,
                                    category = exchange.category,
                                    isRead = readIds.contains(id),
                                    targetTab = 3,
                                    targetCategory = "HISTORY",
                                    targetExchangeType = ExchangeType.BORROW
                                )
                            )
                        }
                        ExchangeStatus.CANCELLED -> {
                            val id = "notif_borrow_cancelled_${exchange.id}"
                            notifications.add(
                                NotificationItem(
                                    id = id,
                                    type = NotificationType.EXCHANGE_UPDATE,
                                    title = "Borrow Request Cancelled",
                                    description = "Your borrow request for \"${exchange.title}\" was cancelled",
                                    dateDisplay = dateStr,
                                    timestamp = timestamp,
                                    category = exchange.category,
                                    isRead = readIds.contains(id),
                                    targetTab = 3,
                                    targetCategory = "HISTORY",
                                    targetExchangeType = ExchangeType.BORROW
                                )
                            )
                        }
                        else -> {
                            // Other borrow states
                        }
                    }
                }
            }
        }

        // Handle initial first-run read status setup
        if (isFirstRun) {
            val initialReadIds = mutableSetOf<String>()
            // Initialize completed/cancelled history notifications as already read
            notifications.forEach { notif ->
                if (notif.type == NotificationType.RETURN_UPDATE && notif.title.contains("Completed", ignoreCase = true) ||
                    notif.type == NotificationType.REQUEST_DECLINED ||
                    notif.title.contains("Cancelled", ignoreCase = true)
                ) {
                    initialReadIds.add(notif.id)
                }
            }
            prefs.edit()
                .putBoolean(KEY_INITIALIZED, true)
                .putStringSet(KEY_READ_IDS, initialReadIds)
                .apply()

            // Return with initial read status mapped
            return notifications
                .map { it.copy(isRead = initialReadIds.contains(it.id)) }
                .sortedByDescending { it.timestamp }
        }

        return notifications.sortedByDescending { it.timestamp }
    }

    fun markAsRead(context: Context, notificationId: String) {
        val readIds = getReadIds(context)
        if (readIds.add(notificationId)) {
            saveReadIds(context, readIds)
        }
    }

    fun markAllAsRead(context: Context) {
        val allNotifs = getNotifications(context)
        val readIds = getReadIds(context)
        var changed = false
        allNotifs.forEach {
            if (readIds.add(it.id)) {
                changed = true
            }
        }
        if (changed) {
            saveReadIds(context, readIds)
        }
    }

    fun getUnreadCount(context: Context): Int {
        val notifs = getNotifications(context)
        return notifs.count { !it.isRead }
    }
}
